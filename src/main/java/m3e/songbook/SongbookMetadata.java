package m3e.songbook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;

public record SongbookMetadata(String instrument, String instrumentId, String author, boolean signed) {
    public static final int MAX_LENGTH = 96;
    public static final SongbookMetadata EMPTY = new SongbookMetadata("", "", "", false);
    public static final Codec<SongbookMetadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("instrument", "").forGetter(SongbookMetadata::instrument),
            Codec.STRING.optionalFieldOf("instrument_id", "").forGetter(SongbookMetadata::instrumentId),
            Codec.STRING.optionalFieldOf("author", "").forGetter(SongbookMetadata::author),
            Codec.BOOL.optionalFieldOf("signed", false).forGetter(SongbookMetadata::signed)
    ).apply(instance, SongbookMetadata::new));

    public SongbookMetadata(String instrument, String author) {
        this(instrument, "", author, false);
    }

    public SongbookMetadata(String instrument, String author, boolean signed) {
        this(instrument, "", author, signed);
    }

    public SongbookMetadata {
        instrument = clean(instrument);
        instrumentId = cleanId(instrumentId);
        author = clean(author);
    }

    public boolean hasInstrument() { return !instrument.isEmpty(); }
    public boolean hasAuthor() { return !author.isEmpty(); }

    public int estimatedBytes() {
        return instrument.length() * 3 + instrumentId.length() * 3 + author.length() * 3 + 21;
    }

    public static SongbookMetadata read(FriendlyByteBuf buffer) {
        return new SongbookMetadata(buffer.readUtf(MAX_LENGTH), buffer.readUtf(128),
                buffer.readUtf(MAX_LENGTH), buffer.readBoolean());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(instrument, MAX_LENGTH);
        buffer.writeUtf(instrumentId, 128);
        buffer.writeUtf(author, MAX_LENGTH);
        buffer.writeBoolean(signed);
    }

    public SongbookMetadata withSigned(boolean value) {
        return value == signed ? this : new SongbookMetadata(instrument, instrumentId, author, value);
    }

    public SongbookMetadata withInstrument(String id, String name) {
        return new SongbookMetadata(name, id, author, signed);
    }

    private static String clean(String value) {
        String cleaned = value == null ? "" : value.strip();
        return cleaned.substring(0, Math.min(MAX_LENGTH, cleaned.length()));
    }

    private static String cleanId(String value) {
        String cleaned = value == null ? "" : value.strip();
        return cleaned.substring(0, Math.min(128, cleaned.length()));
    }
}
