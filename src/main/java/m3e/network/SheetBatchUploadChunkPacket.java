package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

import java.util.UUID;

public record SheetBatchUploadChunkPacket(UUID transactionId, int offset, byte[] bytes)
        implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 32 * 1024;
    public static final Type<SheetBatchUploadChunkPacket> TYPE =
            new Type<>(M3E.id("sheet_batch_upload_chunk"));
    public static final StreamCodec<FriendlyByteBuf, SheetBatchUploadChunkPacket> CODEC =
            StreamCodec.ofMember(SheetBatchUploadChunkPacket::write, SheetBatchUploadChunkPacket::read);

    public SheetBatchUploadChunkPacket {
        bytes = bytes == null ? new byte[0] : bytes.clone();
        if (offset < 0 || bytes.length < 1 || bytes.length > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("invalid separate sheet upload chunk");
        }
    }

    @Override public byte[] bytes() { return bytes.clone(); }

    private static SheetBatchUploadChunkPacket read(FriendlyByteBuf buffer) {
        return new SheetBatchUploadChunkPacket(buffer.readUUID(), buffer.readVarInt(),
                buffer.readByteArray(MAX_CHUNK_BYTES));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(transactionId);
        buffer.writeVarInt(offset);
        buffer.writeByteArray(bytes);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
