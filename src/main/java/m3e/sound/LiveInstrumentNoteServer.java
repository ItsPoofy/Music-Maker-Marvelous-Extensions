package m3e.sound;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import xerca.xercamusic.common.item.Items;
import m3e.network.LiveInstrumentNotePacket;
import m3e.network.LiveInstrumentNoteStatePacket;
import m3e.network.DexedVoiceAutomationPacket;
import m3e.instrument.M3EInstruments;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Validates and relays ordinary live instrument events to remote listeners. */
public final class LiveInstrumentNoteServer {
    private static final int MAX_ACTIVE_EVENTS = 256;
    private static final int MAX_STARTS_PER_SECOND = 128;
    private static final int MAX_STOPS_PER_SECOND = 256;
    private static final int MAX_AUTOMATION_PER_SECOND = 4_096;
    private static final PlayerRateLimiter START_RATE = new PlayerRateLimiter();
    private static final PlayerRateLimiter STOP_RATE = new PlayerRateLimiter();
    private static final PlayerRateLimiter AUTOMATION_RATE = new PlayerRateLimiter();
    private static final Map<UUID, Map<Long, ActiveEvent>> ACTIVE = new HashMap<>();

    private LiveInstrumentNoteServer() { }

    public static boolean allow(ServerPlayer player, boolean stop) {
        PlayerRateLimiter rate = stop ? STOP_RATE : START_RATE;
        int maximum = stop ? MAX_STOPS_PER_SECOND : MAX_STARTS_PER_SECOND;
        return rate.allow(player.getUUID(), maximum);
    }

    public static boolean allowAutomation(ServerPlayer player) {
        return AUTOMATION_RATE.allow(player.getUUID(), MAX_AUTOMATION_PER_SECOND);
    }

    public static void receive(ServerPlayer player, LiveInstrumentNotePacket packet) {
        if (packet.instrumentId() >= Items.INSTRUMENTS.size()) return;
        Map<Long, ActiveEvent> active = ACTIVE.get(player.getUUID());
        List<ServerPlayer> recipients;
        ActiveEvent owner;
        if (packet.stop()) {
            if (active == null) return;
            owner = active.get(packet.eventId());
            if (owner == null || owner.note() != packet.note()
                    || owner.instrumentId() != packet.instrumentId()) return;
            active.remove(packet.eventId());
            recipients = recipients(player, owner.recipientIds());
        } else {
            if (!HeldInstrumentAuthorization.holds(player, packet.instrumentId())) return;
            if (active == null) {
                active = new HashMap<>();
                ACTIVE.put(player.getUUID(), active);
            }
            if (active.size() >= MAX_ACTIVE_EVENTS
                    || active.containsKey(packet.eventId())) return;
            Collection<ServerPlayer> nearby = PlayerLookup.around(
                    player.level(), player.position(), 24.0D);
            recipients = nearby.stream()
                    .filter(recipient -> !recipient.getUUID().equals(player.getUUID()))
                    .toList();
            owner = null;
        }
        if (!packet.stop()) {
            owner = new ActiveEvent(packet.note(), packet.instrumentId(),
                    recipients.stream().map(ServerPlayer::getUUID)
                            .collect(Collectors.toUnmodifiableSet()));
            active.put(packet.eventId(), owner);
        }
        if (active.isEmpty()) ACTIVE.remove(player.getUUID());
        InstrumentSoundProfile profile = InstrumentProfileServer.profile(
                player.getUUID(), packet.instrumentId());
        String variation = InstrumentProfileServer.variation(
                player.getUUID(), packet.instrumentId());
        InstrumentExpressionServer.State expression = InstrumentExpressionServer.state(
                player.getUUID(), packet.instrumentId());
        LiveInstrumentNoteStatePacket state = new LiveInstrumentNoteStatePacket(
                player.getId(), packet.eventId(), packet.note(), packet.velocity(), packet.stop(),
                packet.instrumentId(), profile, variation, expression.bendSemitones(),
                expression.modulation());
        for (ServerPlayer recipient : recipients) {
            ServerPlayNetworking.send(recipient, state);
        }
    }

    public static void receiveAutomation(ServerPlayer player,
                                         DexedVoiceAutomationPacket packet) {
        Map<Long, ActiveEvent> active = ACTIVE.get(player.getUUID());
        if (active == null) return;
        ActiveEvent owner = active.get(packet.eventId());
        if (owner == null || owner.instrumentId()
                != M3EInstruments.dexed().getInstrumentId()) return;
        DexedVoiceAutomationPacket state = new DexedVoiceAutomationPacket(player.getId(),
                packet.eventId(), packet.kind(), packet.bendSemitones(), packet.modulation(),
                packet.gain(), packet.waypoints(), packet.positions(), packet.durationMicros());
        for (ServerPlayer recipient : recipients(player, owner.recipientIds())) {
            ServerPlayNetworking.send(recipient, state);
        }
    }

    public static void remove(ServerPlayer player) {
        START_RATE.remove(player.getUUID());
        STOP_RATE.remove(player.getUUID());
        AUTOMATION_RATE.remove(player.getUUID());
        ACTIVE.remove(player.getUUID());
    }

    public static void clear() {
        START_RATE.clear();
        STOP_RATE.clear();
        AUTOMATION_RATE.clear();
        ACTIVE.clear();
    }

    private static List<ServerPlayer> recipients(ServerPlayer performer,
                                                  Set<UUID> recipientIds) {
        var server = performer.level().getServer();
        return recipientIds.stream()
                .map(server.getPlayerList()::getPlayer)
                .filter(Objects::nonNull)
                .toList();
    }

    private record ActiveEvent(int note, int instrumentId, Set<UUID> recipientIds) { }
}
