package m3e.sound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player fixed-window limiter shared by independent server update channels. */
public final class PlayerRateLimiter {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private final Map<UUID, Window> windows = new HashMap<>();

    public synchronized boolean allow(UUID playerId, int maximum) {
        long now = System.nanoTime();
        Window window = windows.computeIfAbsent(playerId, ignored -> new Window(now));
        if (now - window.startedNanos >= WINDOW_NANOS) {
            window.startedNanos = now;
            window.updates = 0;
        }
        if (window.updates >= maximum) return false;
        window.updates++;
        return true;
    }

    public synchronized void remove(UUID playerId) { windows.remove(playerId); }

    public synchronized void clear() { windows.clear(); }

    private static final class Window {
        private long startedNanos;
        private int updates;

        private Window(long now) { startedNanos = now; }
    }
}
