package m3e.songbook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** The only Songbook state sent during ordinary ItemStack synchronization. */
public record SongbookHeader(UUID bookId, int revision, int capacity, int songCount, int encodedBytes) {
    public static final Codec<SongbookHeader> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(SongbookHeader::bookId),
            Codec.INT.fieldOf("revision").forGetter(SongbookHeader::revision),
            Codec.INT.fieldOf("capacity").forGetter(SongbookHeader::capacity),
            Codec.INT.fieldOf("songs").forGetter(SongbookHeader::songCount),
            Codec.INT.fieldOf("bytes").forGetter(SongbookHeader::encodedBytes)
    ).apply(instance, SongbookHeader::new));
    public static final StreamCodec<FriendlyByteBuf, SongbookHeader> STREAM_CODEC =
            StreamCodec.ofMember(SongbookHeader::write, SongbookHeader::read);

    public SongbookHeader {
        capacity = Math.max(1, capacity);
        if (bookId == null || revision < 0 || capacity > SongbookContents.MAX_CAPACITY
                || songCount < 0 || songCount > capacity
                || encodedBytes < 0 || encodedBytes > SongbookContents.MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("invalid Songbook header");
        }
    }

    private static SongbookHeader read(FriendlyByteBuf buffer) {
        return new SongbookHeader(buffer.readUUID(), buffer.readVarInt(), buffer.readUnsignedByte(),
                buffer.readUnsignedByte(), buffer.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(bookId);
        buffer.writeVarInt(revision);
        buffer.writeByte(capacity);
        buffer.writeByte(songCount);
        buffer.writeVarInt(encodedBytes);
    }
}
