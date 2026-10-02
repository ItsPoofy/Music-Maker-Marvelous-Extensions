package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;
import m3e.songbook.SongbookContents;
import m3e.songbook.SongbookMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SongbookCatalogPacket(UUID bookId, int revision, int capacity, int encodedBytes,
                                    List<Entry> entries) implements CustomPacketPayload {
    public static final Type<SongbookCatalogPacket> TYPE = new Type<>(M3E.id("songbook_catalog"));
    public static final StreamCodec<FriendlyByteBuf, SongbookCatalogPacket> CODEC =
            StreamCodec.ofMember(SongbookCatalogPacket::write, SongbookCatalogPacket::read);

    public SongbookCatalogPacket { entries = List.copyOf(entries); }

    private static SongbookCatalogPacket read(FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        int revision = buffer.readVarInt();
        int capacity = buffer.readUnsignedByte();
        int bytes = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (revision < 0 || capacity > SongbookContents.MAX_CAPACITY || bytes < 0
                || bytes > SongbookContents.MAX_ENCODED_BYTES || count < 0 || count > capacity) {
            throw new IllegalArgumentException("invalid Songbook catalog");
        }
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readUUID(), buffer.readUtf(96), buffer.readVarInt(),
                    buffer.readVarLong(), buffer.readUnsignedByte(), buffer.readUnsignedByte(),
                    SongbookMetadata.read(buffer)));
        }
        return new SongbookCatalogPacket(id, revision, capacity, bytes, entries);
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(bookId);
        buffer.writeVarInt(revision);
        buffer.writeByte(capacity);
        buffer.writeVarInt(encodedBytes);
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.entryId());
            buffer.writeUtf(entry.displayName(), 96);
            buffer.writeVarInt(entry.tempoBpm());
            buffer.writeVarLong(entry.durationMicros());
            buffer.writeByte(entry.timeSignatureNumerator());
            buffer.writeByte(entry.timeSignatureDenominator());
            entry.metadata().write(buffer);
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(UUID entryId, String displayName, int tempoBpm, long durationMicros,
                        int timeSignatureNumerator, int timeSignatureDenominator,
                        SongbookMetadata metadata) {
        public Entry(UUID entryId, String displayName, int tempoBpm, long durationMicros,
                     int timeSignatureNumerator, int timeSignatureDenominator) {
            this(entryId, displayName, tempoBpm, durationMicros, timeSignatureNumerator,
                    timeSignatureDenominator, SongbookMetadata.EMPTY);
        }

        public Entry {
            displayName = displayName == null ? "" : displayName.strip();
            if (displayName.length() > 96) displayName = displayName.substring(0, 96);
            if (tempoBpm < 1 || tempoBpm > 3_000 || durationMicros < 0 || durationMicros > 7_200_000_000L
                    || timeSignatureNumerator < 1 || timeSignatureNumerator > 32
                    || timeSignatureDenominator < 1 || timeSignatureDenominator > 64) {
                throw new IllegalArgumentException("invalid Songbook catalog entry");
            }
            metadata = metadata == null ? SongbookMetadata.EMPTY : metadata;
        }
    }
}
