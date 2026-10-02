package m3e.songbook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import m3e.sheet.MusicScore;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

public record SongbookEntry(UUID entryId, String displayName, int tempoBpm, byte[] playback,
                            SongbookMetadata metadata) {
    private static final Codec<byte[]> BYTES = Codec.BYTE_BUFFER.xmap(buffer -> {
        ByteBuffer copy = buffer.slice();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }, bytes -> ByteBuffer.wrap(bytes.clone()));
    public static final Codec<SongbookEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(SongbookEntry::entryId),
            Codec.STRING.fieldOf("name").forGetter(SongbookEntry::displayName),
            Codec.INT.fieldOf("tempo_bpm").forGetter(SongbookEntry::tempoBpm),
            BYTES.fieldOf("playback").forGetter(SongbookEntry::playback),
            SongbookMetadata.CODEC.optionalFieldOf("metadata", SongbookMetadata.EMPTY)
                    .forGetter(SongbookEntry::metadata)
    ).apply(instance, SongbookEntry::new));

    public SongbookEntry(UUID entryId, String displayName, int tempoBpm, byte[] playback) {
        this(entryId, displayName, tempoBpm, playback, SongbookMetadata.EMPTY);
    }

    public SongbookEntry {
        if (entryId == null) throw new IllegalArgumentException("missing Songbook entry id");
        displayName = displayName == null ? "" : displayName.strip();
        if (displayName.isEmpty()) displayName = "Untitled Song";
        if (displayName.length() > 96) displayName = displayName.substring(0, 96);
        tempoBpm = Math.clamp(tempoBpm, 1, 3_000);
        playback = playback == null ? new byte[0] : playback.clone();
        metadata = metadata == null ? SongbookMetadata.EMPTY : metadata;
        SongbookSongCodec.decode(playback);
    }

    @Override public byte[] playback() { return playback.clone(); }

    public MusicScore song() { return SongbookSongCodec.decode(playback); }

    public int encodedBytes() {
        return estimatedBytes(displayName, playback.length, metadata);
    }

    public static int estimatedBytes(String displayName, int playbackBytes) {
        return estimatedBytes(displayName, playbackBytes, SongbookMetadata.EMPTY);
    }

    public static int estimatedBytes(String displayName, int playbackBytes,
                                     SongbookMetadata metadata) {
        String name = displayName == null ? "" : displayName;
        int metadataBytes = metadata == null ? 0 : metadata.estimatedBytes();
        return Math.addExact(Math.addExact(Math.max(0, playbackBytes), name.length() * 3 + 64), metadataBytes);
    }

    public SongbookEntry renamed(String name) {
        return new SongbookEntry(entryId, name, tempoBpm, playback, metadata);
    }

    public SongbookEntry withTempo(int bpm) {
        MusicScore changed = song().withInitialBpm(bpm);
        return new SongbookEntry(entryId, displayName, changed.initialBpm(),
                SongbookSongCodec.encode(changed), metadata);
    }

    public SongbookEntry withInstrument(String id, String name) {
        MusicScore current = song();
        MusicScore changed = current.withMetadata(
                current.author(), current.signed(), id);
        return new SongbookEntry(entryId, displayName, tempoBpm,
                SongbookSongCodec.encode(changed), metadata.withInstrument(id, name));
    }

    @Override public boolean equals(Object value) {
        return value instanceof SongbookEntry other && entryId.equals(other.entryId)
                && displayName.equals(other.displayName) && tempoBpm == other.tempoBpm
                && Arrays.equals(playback, other.playback) && metadata.equals(other.metadata);
    }

    @Override public int hashCode() {
        return 31 * (31 * (31 * (31 * entryId.hashCode() + displayName.hashCode()) + tempoBpm)
                + Arrays.hashCode(playback)) + metadata.hashCode();
    }
}
