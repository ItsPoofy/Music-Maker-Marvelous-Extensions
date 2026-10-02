package m3e.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import m3e.debug.MusicDebugTelemetry;
import m3e.network.NetworkClockRequestPacket;
import m3e.network.NetworkClockResponsePacket;
import m3e.sync.ClockOffsetEstimator;

/** Maps server presentation timestamps into this client's audio clock. */
public final class NetworkClockClient {
    private static final long INITIAL_INTERVAL_NANOS = 500_000_000L;
    private static final long STEADY_INTERVAL_NANOS = 2_000_000_000L;
    private static final long MAX_RTT_NANOS = 5_000_000_000L;
    private static final ClockOffsetEstimator ESTIMATOR = new ClockOffsetEstimator();
    private static long lastRequestNanos;
    private static long estimatedRttNanos;
    private static long estimatedJitterNanos;
    private static long previousRttNanos;

    private NetworkClockClient() { }

    public static void tick() {
        if (Minecraft.getInstance().getConnection() == null) {
            clear();
            return;
        }
        long now = System.nanoTime();
        long interval = isSynchronized() ? STEADY_INTERVAL_NANOS
                : INITIAL_INTERVAL_NANOS;
        if (now - lastRequestNanos < interval
                || !ClientPlayNetworking.canSend(NetworkClockRequestPacket.TYPE)) return;
        lastRequestNanos = now;
        ClientPlayNetworking.send(new NetworkClockRequestPacket(
                now, estimatedRttNanos, estimatedJitterNanos));
    }

    public static void receive(NetworkClockResponsePacket response) {
        long received = System.nanoTime();
        long rtt = received - response.clientSentNanos();
        if (rtt <= 0L || rtt > MAX_RTT_NANOS) return;
        long jitterSample = previousRttNanos == 0L ? 0L
                : Math.abs(rtt - previousRttNanos);
        previousRttNanos = rtt;
        estimatedRttNanos = estimatedRttNanos == 0L ? rtt
                : (estimatedRttNanos * 3L + rtt) / 4L;
        estimatedJitterNanos = estimatedJitterNanos == 0L ? jitterSample
                : (estimatedJitterNanos * 3L + jitterSample) / 4L;
        ESTIMATOR.add(response.clientSentNanos(), response.serverReceivedNanos(),
                response.serverSentNanos(), received);
        MusicDebugTelemetry.sampled("network_clock", 1_000_000_000L,
                "network_clock", "sample", "rtt_ms", rtt / 1_000_000.0D,
                "estimated_rtt_ms", estimatedRttNanos / 1_000_000.0D,
                "jitter_ms", estimatedJitterNanos / 1_000_000.0D,
                "offset_ms", clientMinusServerNanos() / 1_000_000.0D,
                "ready", ESTIMATOR.ready());
    }

    public static boolean isSynchronized() { return ESTIMATOR.ready(); }

    public static long clientMinusServerNanos() {
        return -ESTIMATOR.serverMinusClient(System.nanoTime());
    }

    public static long serverToClientNanos(long serverNanos) {
        return serverNanos + clientMinusServerNanos();
    }

    public static void requestSoon() { lastRequestNanos = 0L; }

    public static void clear() {
        lastRequestNanos = 0L;
        ESTIMATOR.clear();
        estimatedRttNanos = 0L;
        estimatedJitterNanos = 0L;
        previousRttNanos = 0L;
    }
}
