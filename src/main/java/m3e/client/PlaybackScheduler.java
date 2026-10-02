package m3e.client;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Shared deadline scheduler. Callbacks dispatch work and never wait for music time. */
final class PlaybackScheduler {
    private static final ScheduledThreadPoolExecutor EXECUTOR = create();
    private PlaybackScheduler() { }

    private static ScheduledThreadPoolExecutor create() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "M3E playback deadlines");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    static ScheduledFuture<?> schedule(Runnable callback, long delayNanos) {
        return EXECUTOR.schedule(callback, Math.max(0L, delayNanos), TimeUnit.NANOSECONDS);
    }
}
