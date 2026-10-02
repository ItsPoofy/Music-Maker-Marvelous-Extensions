package m3e.client;

/** Keeps rapid Songbook switches within the server's selected-song download limit. */
final class SongbookRequestThrottle {
    static final long INTERVAL_NANOS = 275_000_000L;

    private boolean pending;
    private long lastRequestNanos = Long.MIN_VALUE / 2;

    void queue() { pending = true; }

    void cancel() { pending = false; }

    boolean takeIfReady(long nowNanos) {
        if (!pending || nowNanos - lastRequestNanos < INTERVAL_NANOS) return false;
        pending = false;
        lastRequestNanos = nowNanos;
        return true;
    }

    void reset() {
        pending = false;
        lastRequestNanos = Long.MIN_VALUE / 2;
    }
}
