package m3e.client;

import net.minecraft.client.Minecraft;

import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * Monotonic musical clock which excludes time spent in a genuinely paused
 * integrated world. Multiplayer menus do not make {@link Minecraft#isPaused()}
 * true, so remote playback continues normally there.
 */
final class PauseAwarePlaybackClock {
    private static final long MAX_PARK_NANOS = 1_000_000L;

    private final Minecraft minecraft;
    private final long startedNanos;
    private final double speed;
    private long pausedNanos;

    PauseAwarePlaybackClock(Minecraft minecraft) {
        this(minecraft, 0L, 1.0D);
    }

    PauseAwarePlaybackClock(Minecraft minecraft, long initialMicros) {
        this(minecraft, initialMicros, 1.0D);
    }

    PauseAwarePlaybackClock(Minecraft minecraft, long initialMicros, double speed) {
        this.minecraft = minecraft;
        this.speed = Math.clamp(speed, 0.01D, 16.0D);
        this.startedNanos = System.nanoTime()
                - Math.round(Math.max(0L, initialMicros) * 1_000.0D / this.speed);
    }

    boolean awaitMicros(long elapsedMicros, BooleanSupplier stopped) {
        return awaitNanos(Math.max(0L, elapsedMicros) * 1_000L, stopped);
    }

    boolean awaitNanos(long elapsedNanos, BooleanSupplier stopped) {
        while (!stopped.getAsBoolean() && minecraft.level != null) {
            if (minecraft.isPaused()) {
                long pauseStarted = System.nanoTime();
                do {
                    LockSupport.parkNanos(MAX_PARK_NANOS);
                } while (minecraft.isPaused() && minecraft.level != null && !stopped.getAsBoolean());
                pausedNanos += System.nanoTime() - pauseStarted;
                continue;
            }

            long remaining = startedNanos + Math.round(elapsedNanos / speed)
                    + pausedNanos - System.nanoTime();
            if (remaining <= 0L) return true;
            LockSupport.parkNanos(Math.min(remaining, MAX_PARK_NANOS));
        }
        return false;
    }
}
