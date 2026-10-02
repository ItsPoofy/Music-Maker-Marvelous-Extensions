package m3e.sound;

import m3e.network.InstrumentExpressionStatePacket;
import m3e.network.InstrumentExpressionUpdatePacket;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xerca.xercamusic.common.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Validation and bounded relay for live MIDI expression state. */
public final class InstrumentExpressionServer {
    private static final PlayerRateLimiter RATE = new PlayerRateLimiter();
    private static final Map<Key, State> STATES = new HashMap<>();

    private InstrumentExpressionServer() { }

    public static void update(MinecraftServer server, ServerPlayer player,
                              InstrumentExpressionUpdatePacket packet) {
        if (!HeldInstrumentAuthorization.holds(player, packet.instrumentId())
                || !Float.isFinite(packet.bendSemitones())
                || packet.bendSemitones() < -96.0F || packet.bendSemitones() > 96.0F
                || packet.modulation() < 0 || packet.modulation() > 127) return;
        if (!RATE.allow(player.getUUID(), 500)) return;
        Key key = new Key(player.getUUID(), packet.instrumentId());
        State next = new State(packet.bendSemitones(), packet.modulation());
        if (next.equals(STATES.get(key))) return;
        STATES.put(key, next);
        InstrumentExpressionStatePacket state = new InstrumentExpressionStatePacket(
                player.getId(), packet.instrumentId(), packet.bendSemitones(), packet.modulation());
        boolean sentToSource = false;
        for (ServerPlayer recipient : PlayerLookup.around(
                player.level(), player.position(), 24.0D)) {
            ServerPlayNetworking.send(recipient, state);
            sentToSource |= recipient == player;
        }
        if (!sentToSource) ServerPlayNetworking.send(player, state);
    }

    public static void remove(ServerPlayer player) {
        RATE.remove(player.getUUID());
        STATES.keySet().removeIf(key -> key.playerId().equals(player.getUUID()));
    }

    public static void clear() {
        RATE.clear();
        STATES.clear();
    }

    public static void sendSnapshot(ServerPlayer joining) {
        MinecraftServer server = joining.level().getServer();
        if (server == null) return;
        for (Map.Entry<Key, State> entry : STATES.entrySet()) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey().playerId());
            if (owner == null) continue;
            State state = entry.getValue();
            ServerPlayNetworking.send(joining, new InstrumentExpressionStatePacket(
                    owner.getId(), entry.getKey().instrumentId(),
                    state.bendSemitones(), state.modulation()));
        }
    }

    public static State state(UUID playerId, int instrumentId) {
        return STATES.getOrDefault(new Key(playerId, instrumentId), State.CENTERED);
    }

    private record Key(UUID playerId, int instrumentId) { }
    public record State(float bendSemitones, int modulation) {
        private static final State CENTERED = new State(0.0F, 0);
    }

}
