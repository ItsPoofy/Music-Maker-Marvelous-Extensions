package m3e.network;

import java.io.ByteArrayOutputStream;
import m3e.songbook.SongbookHash;

/** Bounded, ordered, expiring assembly shared by sheet and Songbook transfers. */
public final class ChunkedTransfer {
    private final int size;
    private final String hash;
    private final long timeoutNanos;
    private final ByteArrayOutputStream bytes;
    private long updatedNanos;

    public ChunkedTransfer(int size, String hash, int maximumSize, long timeoutNanos, long now) {
        if (size < 1 || size > maximumSize || timeoutNanos < 1 || hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid music transfer");
        }
        this.size = size;
        this.hash = hash;
        this.timeoutNanos = timeoutNanos;
        this.updatedNanos = now;
        bytes = new ByteArrayOutputStream(size);
    }

    public boolean expired(long now) { return now - updatedNanos > timeoutNanos; }

    public void append(int offset, byte[] chunk, long now) {
        if (expired(now) || offset != bytes.size() || chunk.length == 0
                || chunk.length > size - bytes.size()) {
            throw new IllegalArgumentException("Music transfer expired or was out of order");
        }
        bytes.writeBytes(chunk);
        updatedNanos = now;
    }

    public byte[] complete(long now) {
        if (expired(now) || bytes.size() != size) throw new IllegalArgumentException("Music transfer is incomplete or expired");
        byte[] result = bytes.toByteArray();
        if (!SongbookHash.sha256(result).equals(hash)) throw new IllegalArgumentException("Music transfer hash did not match");
        return result;
    }
}
