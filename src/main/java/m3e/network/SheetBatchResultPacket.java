package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

import java.util.UUID;

public record SheetBatchResultPacket(UUID transactionId, boolean success, String message)
        implements CustomPacketPayload {
    public static final Type<SheetBatchResultPacket> TYPE =
            new Type<>(M3E.id("sheet_batch_result"));
    public static final StreamCodec<FriendlyByteBuf, SheetBatchResultPacket> CODEC =
            StreamCodec.ofMember(SheetBatchResultPacket::write, SheetBatchResultPacket::read);

    public SheetBatchResultPacket {
        message = message == null ? "" : message.substring(0, Math.min(160, message.length()));
    }

    private static SheetBatchResultPacket read(FriendlyByteBuf buffer) {
        return new SheetBatchResultPacket(buffer.readUUID(), buffer.readBoolean(), buffer.readUtf(160));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(transactionId);
        buffer.writeBoolean(success);
        buffer.writeUtf(message, 160);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
