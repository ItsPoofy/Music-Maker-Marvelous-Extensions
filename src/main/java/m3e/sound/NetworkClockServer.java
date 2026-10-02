package m3e.sound;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import m3e.network.NetworkClockRequestPacket;
import m3e.network.NetworkClockResponsePacket;
import m3e.sync.NetworkTiming;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Connection-lifetime route and nano-clock estimates shared by music systems. */
public final class NetworkClockServer {
    private static final int MAX_REQUESTS_PER_SECOND = 8;
    private static final Map<UUID, NetworkTiming> TIMINGS = new HashMap<>();
    private static final PlayerRateLimiter RATE = new PlayerRateLimiter();

    private NetworkClockServer() { }

    public static boolean allow(ServerPlayer player) {
        return RATE.allow(player.getUUID(), MAX_REQUESTS_PER_SECOND);
    }

    public static void receive(ServerPlayer player, NetworkClockRequestPacket packet) {
        long received = System.nanoTime();
        if (packet.measuredRttNanos() > 0L) {
            long serverMinusClient = received - packet.clientSentNanos()
                    - packet.measuredRttNanos() / 2L;
            NetworkTiming prior = TIMINGS.get(player.getUUID());
            if (prior != null) {
                serverMinusClient = (prior.serverMinusClientNanos() * 3L
                        + serverMinusClient) / 4L;
            }
            TIMINGS.put(player.getUUID(), new NetworkTiming(
                    packet.measuredRttNanos(), packet.measuredJitterNanos(),
                    serverMinusClient, received));
        }
        ServerPlayNetworking.send(player, new NetworkClockResponsePacket(
                packet.clientSentNanos(), received, System.nanoTime()));
    }

    public static NetworkTiming timing(UUID playerId) {
        return TIMINGS.get(playerId);
    }

    public static void remove(ServerPlayer player) {
        TIMINGS.remove(player.getUUID());
        RATE.remove(player.getUUID());
    }

    public static void clear() {
        TIMINGS.clear();
        RATE.clear();
    }
}
