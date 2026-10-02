package m3e.client;

import m3e.network.ChunkedTransfer;
import m3e.network.SongbookSongStartPacket;
import m3e.network.SongbookSongChunkPacket;
import m3e.network.SongbookSongFinishPacket;
import m3e.songbook.SongbookContents;
import m3e.songbook.SongbookPlaybackCodec;
import m3e.sheet.MusicScore;

/** Connection-owned bounded song download; catalog and playback selection stay with their models. */
final class SongbookDownload {
    private static final long TIMEOUT_NANOS = 15_000_000_000L;
    record Completed(SongbookSongStartPacket start, MusicScore song) { }
    private SongbookSongStartPacket start;
    private ChunkedTransfer transfer;

    boolean active() { return transfer != null; }
    void clear() { start = null; transfer = null; }
    boolean expire(long now) {
        if (transfer == null || !transfer.expired(now)) return false;
        clear();
        return true;
    }
    void begin(SongbookSongStartPacket packet) {
        clear();
        transfer = new ChunkedTransfer(packet.size(), packet.sha256(),
                SongbookContents.MAX_ENCODED_BYTES, TIMEOUT_NANOS, System.nanoTime());
        start = packet;
    }
    boolean append(SongbookSongChunkPacket packet) {
        if (transfer == null) return false;
        try {
            transfer.append(packet.offset(), packet.bytes(), System.nanoTime());
            return true;
        } catch (IllegalArgumentException invalid) {
            clear();
            return false;
        }
    }
    Completed finish(SongbookSongFinishPacket packet) {
        SongbookSongStartPacket pending = start;
        ChunkedTransfer completed = transfer;
        clear();
        if (pending == null || !packet.entryId().equals(pending.entryId())) return null;
        try {
            return new Completed(pending, SongbookPlaybackCodec.decode(completed.complete(System.nanoTime())).song());
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
