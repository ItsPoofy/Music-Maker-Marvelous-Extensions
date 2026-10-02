package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;

public record DexedPatchStatePacket(int entityId, DexedPatch patch,
                                    DexedPerformance performance) implements CustomPacketPayload {
    public static final Type<DexedPatchStatePacket> TYPE =
            new Type<>(M3E.id("dexed_patch_state"));
    public static final StreamCodec<FriendlyByteBuf, DexedPatchStatePacket> CODEC =
            StreamCodec.ofMember(DexedPatchStatePacket::write, DexedPatchStatePacket::read);

    private static DexedPatchStatePacket read(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        byte[] bytes = new byte[DexedPatch.SIZE];
        buffer.readBytes(bytes);
        return new DexedPatchStatePacket(entityId, new DexedPatch(bytes),
                DexedPerformance.STREAM_CODEC.decode(buffer));
    }
    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeBytes(patch.bytes());
        DexedPerformance.STREAM_CODEC.encode(buffer, performance);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
