package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

import java.util.UUID;

public record SheetBatchUploadCancelPacket(UUID transactionId) implements CustomPacketPayload {
    public static final Type<SheetBatchUploadCancelPacket> TYPE =
            new Type<>(M3E.id("sheet_batch_upload_cancel"));
    public static final StreamCodec<FriendlyByteBuf, SheetBatchUploadCancelPacket> CODEC =
            StreamCodec.ofMember(SheetBatchUploadCancelPacket::write, SheetBatchUploadCancelPacket::read);

    private static SheetBatchUploadCancelPacket read(FriendlyByteBuf buffer) {
        return new SheetBatchUploadCancelPacket(buffer.readUUID());
    }

    private void write(FriendlyByteBuf buffer) { buffer.writeUUID(transactionId); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
