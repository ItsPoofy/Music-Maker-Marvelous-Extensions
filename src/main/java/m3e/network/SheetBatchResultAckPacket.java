package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

import java.util.UUID;

public record SheetBatchResultAckPacket(UUID transactionId) implements CustomPacketPayload {
    public static final Type<SheetBatchResultAckPacket> TYPE =
            new Type<>(M3E.id("sheet_batch_result_ack"));
    public static final StreamCodec<FriendlyByteBuf, SheetBatchResultAckPacket> CODEC =
            StreamCodec.ofMember(SheetBatchResultAckPacket::write, SheetBatchResultAckPacket::read);

    private static SheetBatchResultAckPacket read(FriendlyByteBuf buffer) {
        return new SheetBatchResultAckPacket(buffer.readUUID());
    }

    private void write(FriendlyByteBuf buffer) { buffer.writeUUID(transactionId); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
