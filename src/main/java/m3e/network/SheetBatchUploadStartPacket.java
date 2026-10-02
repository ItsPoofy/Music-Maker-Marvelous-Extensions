package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;
import m3e.sheet.SheetBatchCodec;

import java.util.UUID;

public record SheetBatchUploadStartPacket(UUID transactionId, Destination destination,
                                          UUID songbookId, int songbookRevision,
                                          int entryCount, int size,
                                          String sha256) implements CustomPacketPayload {
    public enum Destination { SHEETS, SONGBOOK }

    private static final UUID NO_SONGBOOK = new UUID(0L, 0L);
    public static final Type<SheetBatchUploadStartPacket> TYPE =
            new Type<>(M3E.id("sheet_batch_upload_start"));
    public static final StreamCodec<FriendlyByteBuf, SheetBatchUploadStartPacket> CODEC =
            StreamCodec.ofMember(SheetBatchUploadStartPacket::write, SheetBatchUploadStartPacket::read);

    private static SheetBatchUploadStartPacket read(FriendlyByteBuf buffer) {
        UUID transactionId = buffer.readUUID();
        int destinationIndex = buffer.readUnsignedByte();
        if (destinationIndex >= Destination.values().length) {
            throw new IllegalArgumentException("invalid separate import destination");
        }
        SheetBatchUploadStartPacket packet = new SheetBatchUploadStartPacket(
                transactionId, Destination.values()[destinationIndex], buffer.readUUID(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(64));
        if (packet.entryCount < 1 || packet.entryCount > SheetBatchCodec.MAX_ENTRIES
                || packet.size < 1 || packet.size > SheetBatchCodec.MAX_BYTES
                || packet.songbookRevision < 0
                || (packet.destination == Destination.SONGBOOK
                && packet.songbookId.equals(NO_SONGBOOK))
                || !packet.sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid separate import");
        }
        return packet;
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(transactionId);
        buffer.writeByte(destination.ordinal());
        buffer.writeUUID(songbookId);
        buffer.writeVarInt(songbookRevision);
        buffer.writeVarInt(entryCount);
        buffer.writeVarInt(size);
        buffer.writeUtf(sha256, 64);
    }

    public static SheetBatchUploadStartPacket sheets(UUID transactionId, int entryCount,
                                                       int size, String sha256) {
        return new SheetBatchUploadStartPacket(transactionId, Destination.SHEETS,
                NO_SONGBOOK, 0, entryCount, size, sha256);
    }

    public static SheetBatchUploadStartPacket songbook(UUID transactionId, UUID bookId,
                                                         int revision, int entryCount,
                                                         int size, String sha256) {
        return new SheetBatchUploadStartPacket(transactionId, Destination.SONGBOOK,
                bookId, revision, entryCount, size, sha256);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
