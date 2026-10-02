package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;

public record DexedPatchUpdatePacket(boolean offHand, DexedPatch patch,
                                     DexedPerformance performance) implements CustomPacketPayload {
    public static final Type<DexedPatchUpdatePacket> TYPE =
            new Type<>(M3E.id("dexed_patch_update"));
    public static final StreamCodec<FriendlyByteBuf, DexedPatchUpdatePacket> CODEC =
            StreamCodec.ofMember(DexedPatchUpdatePacket::write, DexedPatchUpdatePacket::read);

    private static DexedPatchUpdatePacket read(FriendlyByteBuf buffer) {
        boolean offHand = buffer.readBoolean();
        byte[] bytes = new byte[DexedPatch.SIZE];
        buffer.readBytes(bytes);
        return new DexedPatchUpdatePacket(offHand, new DexedPatch(bytes),
                DexedPerformance.STREAM_CODEC.decode(buffer));
    }
    private void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(offHand);
        buffer.writeBytes(patch.bytes());
        DexedPerformance.STREAM_CODEC.encode(buffer, performance);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
