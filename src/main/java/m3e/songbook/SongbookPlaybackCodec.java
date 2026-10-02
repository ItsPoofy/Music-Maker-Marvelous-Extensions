package m3e.songbook;

import m3e.sheet.MusicScore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
public final class SongbookPlaybackCodec {
    private static final String SONG = "song";

    private SongbookPlaybackCodec() { }

    public static byte[] encode(SongbookEntry entry) {
        CompoundTag root = new CompoundTag();
        root.putByteArray(SONG, entry.playback());
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(entry.encodedBytes());
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(root, output);
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length <= 0 || encoded.length > SongbookContents.MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException("Songbook playback exceeds 1 MiB");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not encode Songbook playback", exception);
        }
    }

    public static SongbookPlayback decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > SongbookContents.MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("invalid Songbook playback payload size");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            CompoundTag root = NbtIo.read(input,
                    NbtAccounter.create(SongbookContents.MAX_ENCODED_BYTES * 2L));
            if (input.available() != 0) throw new IllegalArgumentException("trailing Songbook playback data");
            byte[] songBytes = root.getByteArray(SONG).orElseThrow(() ->
                    new IllegalArgumentException("Songbook playback has no song"));
            return new SongbookPlayback(SongbookSongCodec.decode(songBytes));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not decode Songbook playback", exception);
        }
    }
}
