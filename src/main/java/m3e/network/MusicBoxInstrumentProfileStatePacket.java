package m3e.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;
import m3e.sound.InstrumentSoundProfile;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;

public record MusicBoxInstrumentProfileStatePacket(BlockPos pos, int instrumentId,
                                                   InstrumentSoundProfile profile,
                                                   String variation,
                                                   DexedPatch dexedPatch,
                                                   DexedPerformance dexedPerformance)
        implements CustomPacketPayload {
    public static final Type<MusicBoxInstrumentProfileStatePacket> TYPE =
            new Type<>(M3E.id("music_box_instrument_profile_state"));
    public static final StreamCodec<FriendlyByteBuf, MusicBoxInstrumentProfileStatePacket> CODEC =
            StreamCodec.ofMember(MusicBoxInstrumentProfileStatePacket::write,
                    MusicBoxInstrumentProfileStatePacket::read);

    private static MusicBoxInstrumentProfileStatePacket read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int instrumentId = buf.readVarInt();
        InstrumentSoundProfile profile = InstrumentProfileUpdatePacket.readProfile(buf);
        String variation = buf.readUtf(16);
        byte[] patch = new byte[DexedPatch.SIZE];
        buf.readBytes(patch);
        return new MusicBoxInstrumentProfileStatePacket(
                pos, instrumentId, profile, variation, new DexedPatch(patch),
                DexedPerformance.STREAM_CODEC.decode(buf));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(instrumentId);
        InstrumentProfileUpdatePacket.writeProfile(buf, profile);
        buf.writeUtf(variation, 16);
        buf.writeBytes(dexedPatch.bytes());
        DexedPerformance.STREAM_CODEC.encode(buf, dexedPerformance);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
