package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

import java.util.UUID;

public record SheetBatchUploadFinishPacket(UUID transactionId) implements CustomPacketPayload {
    public static final Type<SheetBatchUploadFinishPacket> TYPE =
            new Type<>(M3E.id("sheet_batch_upload_finish"));
    public static final StreamCodec<FriendlyByteBuf, SheetBatchUploadFinishPacket> CODEC =
            StreamCodec.ofMember(SheetBatchUploadFinishPacket::write, SheetBatchUploadFinishPacket::read);

    private static SheetBatchUploadFinishPacket read(FriendlyByteBuf buffer) {
        return new SheetBatchUploadFinishPacket(buffer.readUUID());
    }

    private void write(FriendlyByteBuf buffer) { buffer.writeUUID(transactionId); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
