package m3e.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;
import m3e.songbook.SongbookContents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record MusicBoxLibraryStatePacket(BlockPos pos, byte kind, UUID libraryId,
                                         String label, int revision, int capacity,
                                         int selectedIndex, List<Entry> entries)
        implements CustomPacketPayload {
    public static final byte EMPTY = 0;
    public static final byte LOOSE_SHEET = 1;
    public static final byte SONGBOOK = 2;
    public static final UUID NO_LIBRARY_ID = new UUID(0L, 0L);
    public static final Type<MusicBoxLibraryStatePacket> TYPE =
            new Type<>(M3E.id("music_box_library_state"));
    public static final StreamCodec<FriendlyByteBuf, MusicBoxLibraryStatePacket> CODEC =
            StreamCodec.ofMember(MusicBoxLibraryStatePacket::write,
                    MusicBoxLibraryStatePacket::read);

    public MusicBoxLibraryStatePacket {
        pos = pos.immutable();
        libraryId = libraryId == null ? NO_LIBRARY_ID : libraryId;
        label = label == null ? "" : label.strip();
        if (label.length() > 96) label = label.substring(0, 96);
        entries = List.copyOf(entries == null ? List.of() : entries);
        boolean invalidSelection = entries.isEmpty() ? selectedIndex != -1
                : selectedIndex < 0 || selectedIndex >= entries.size();
        boolean invalidShape = switch (kind) {
            case EMPTY -> capacity != 1 || !entries.isEmpty()
                    || !NO_LIBRARY_ID.equals(libraryId);
            case LOOSE_SHEET -> capacity != 1 || entries.size() != 1
                    || !NO_LIBRARY_ID.equals(libraryId);
            case SONGBOOK -> NO_LIBRARY_ID.equals(libraryId);
            default -> true;
        };
        if (invalidShape || invalidSelection || revision < 0
                || capacity < 1 || capacity > SongbookContents.MAX_CAPACITY
                || entries.size() > capacity) {
            throw new IllegalArgumentException("invalid music-box library state");
        }
    }

    private static MusicBoxLibraryStatePacket read(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        byte kind = buffer.readByte();
        UUID libraryId = buffer.readUUID();
        String label = buffer.readUtf(96);
        int revision = buffer.readVarInt();
        int capacity = buffer.readUnsignedByte();
        int selected = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > SongbookContents.MAX_CAPACITY) {
            throw new IllegalArgumentException("invalid music-box song count");
        }
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readUUID(), buffer.readUtf(96),
                    buffer.readVarInt(), buffer.readVarLong(),
                    buffer.readUnsignedByte(), buffer.readUnsignedByte()));
        }
        return new MusicBoxLibraryStatePacket(pos, kind, libraryId, label,
                revision, capacity, selected, entries);
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeByte(kind);
        buffer.writeUUID(libraryId);
        buffer.writeUtf(label, 96);
        buffer.writeVarInt(revision);
        buffer.writeByte(capacity);
        buffer.writeVarInt(selectedIndex);
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.entryId());
            buffer.writeUtf(entry.displayName(), 96);
            buffer.writeVarInt(entry.tempoBpm());
            buffer.writeVarLong(entry.durationMicros());
            buffer.writeByte(entry.timeSignatureNumerator());
            buffer.writeByte(entry.timeSignatureDenominator());
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(UUID entryId, String displayName, int tempoBpm,
                        long durationMicros, int timeSignatureNumerator,
                        int timeSignatureDenominator) {
        public Entry {
            if (entryId == null) throw new IllegalArgumentException("missing song id");
            displayName = displayName == null ? "" : displayName.strip();
            if (displayName.isEmpty()) displayName = "Music Sheet";
            if (displayName.length() > 96) displayName = displayName.substring(0, 96);
            if (tempoBpm < 1 || tempoBpm > 3_000 || durationMicros < 0
                    || durationMicros > 7_200_000_000L
                    || timeSignatureNumerator < 1 || timeSignatureNumerator > 32
                    || timeSignatureDenominator < 1 || timeSignatureDenominator > 64) {
                throw new IllegalArgumentException("invalid music-box song summary");
            }
        }
    }
}
