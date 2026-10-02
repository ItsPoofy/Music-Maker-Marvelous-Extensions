package m3e.sheet;

import net.minecraft.nbt.CompoundTag;
import m3e.songbook.SongbookContents;
import m3e.songbook.SongbookSongCodec;

public final class PortableSheetSidecar {
    public static final String EXACT_SONG_KEY = "m3e:exact_song_v1";

    private PortableSheetSidecar() { }

    public static void write(CompoundTag tag, MusicScore song) {
        byte[] bytes = SongbookSongCodec.encode(song);
        if (bytes.length <= SongbookContents.MAX_ENCODED_BYTES) tag.putByteArray(EXACT_SONG_KEY, bytes);
    }

    public static MusicScore read(CompoundTag tag) {
        byte[] bytes = tag.getByteArray(EXACT_SONG_KEY).orElse(null);
        return bytes == null || bytes.length == 0 ? null : SongbookSongCodec.decode(bytes);
    }
}
