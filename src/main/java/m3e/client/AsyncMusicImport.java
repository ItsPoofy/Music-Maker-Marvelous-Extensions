package m3e.client;

import java.util.concurrent.atomic.AtomicLong;

/** Shared last-action-wins token for every asynchronous score and sheet import route. */
final class AsyncMusicImport {
    private static final AtomicLong GENERATION = new AtomicLong();

    private AsyncMusicImport() { }

    static long begin() {
        return GENERATION.incrementAndGet();
    }

    static boolean isCurrent(long generation) {
        return generation == GENERATION.get();
    }

    static void cancel() {
        GENERATION.incrementAndGet();
    }
}
