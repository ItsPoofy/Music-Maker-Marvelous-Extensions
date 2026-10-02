package m3e.sound;

import m3e.network.InstrumentProfileStatePacket;
import m3e.network.InstrumentProfileUpdatePacket;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import m3e.instrument.InstrumentVariations;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned, connection-lifetime instrument profiles. */
public final class InstrumentProfileServer {
    private static final int MAX_UPDATES_PER_SECOND = 40;
    private static final Map<Key, State> PROFILES = new HashMap<>();
    private static final PlayerRateLimiter RATE = new PlayerRateLimiter();

    private InstrumentProfileServer() { }

    public static boolean allow(ServerPlayer player) {
        return RATE.allow(player.getUUID(), MAX_UPDATES_PER_SECOND);
    }

    public static void update(MinecraftServer server, ServerPlayer player,
                              InstrumentProfileUpdatePacket packet) {
        if (!HeldInstrumentAuthorization.holds(player, packet.instrumentId())) return;
        Key key = new Key(player.getUUID(), packet.instrumentId());
        String variation = InstrumentVariations.normalize(packet.instrumentId(), packet.variation());
        State approved = new State(packet.profile(), variation);
        if (approved.equals(PROFILES.get(key))) return;
        PROFILES.put(key, approved);
        InstrumentProfileStatePacket state =
                new InstrumentProfileStatePacket(player.getId(), packet.instrumentId(),
                        approved.profile(), approved.variation());
        boolean sentToSource = false;
        for (ServerPlayer recipient : PlayerLookup.around(
                player.level(), player.position(), 24.0D)) {
            ServerPlayNetworking.send(recipient, state);
            sentToSource |= recipient == player;
        }
        if (!sentToSource) ServerPlayNetworking.send(player, state);
    }

    public static void sendSnapshot(ServerPlayer joining) {
        MinecraftServer server = joining.level().getServer();
        if (server == null) return;
        for (Map.Entry<Key, State> entry : PROFILES.entrySet()) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey().playerId());
            if (owner != null) {
                ServerPlayNetworking.send(joining, new InstrumentProfileStatePacket(
                        owner.getId(), entry.getKey().instrumentId(),
                        entry.getValue().profile(), entry.getValue().variation()));
            }
        }
    }

    public static void remove(ServerPlayer player) {
        PROFILES.keySet().removeIf(key -> key.playerId().equals(player.getUUID()));
        RATE.remove(player.getUUID());
    }

    public static void clear() {
        PROFILES.clear();
        RATE.clear();
    }

    /** Profile selected by an owner for their own and extension-held copies. */
    public static InstrumentSoundProfile profile(UUID playerId, int instrumentId) {
        State state = PROFILES.get(new Key(playerId, instrumentId));
        return state == null ? InstrumentSoundProfile.DEFAULT : state.profile();
    }

    public static String variation(UUID playerId, int instrumentId) {
        State state = PROFILES.get(new Key(playerId, instrumentId));
        return state == null ? InstrumentVariations.defaultId(instrumentId) : state.variation();
    }

    private record Key(UUID playerId, int instrumentId) { }
    private record State(InstrumentSoundProfile profile, String variation) { }

}
