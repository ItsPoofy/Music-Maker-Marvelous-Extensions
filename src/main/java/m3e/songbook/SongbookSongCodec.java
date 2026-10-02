package m3e.songbook;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import m3e.network.MusicScoreNetwork;
import m3e.sheet.MusicScore;

/** Compact, bounded representation shared by Songbook storage and transfers. */
public final class SongbookSongCodec {
    private SongbookSongCodec() { }

    public static byte[] encode(MusicScore song) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MusicScoreNetwork.write(buffer, song);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    public static MusicScore decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > SongbookContents.MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("invalid Songbook song payload size");
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            MusicScore song = MusicScoreNetwork.read(buffer);
            if (buffer.isReadable()) throw new IllegalArgumentException("trailing Songbook song data");
            return song;
        } finally {
            buffer.release();
        }
    }
}
