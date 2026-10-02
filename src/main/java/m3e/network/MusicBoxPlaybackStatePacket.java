package m3e.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

public record MusicBoxPlaybackStatePacket(BlockPos pos, boolean playing,
                                           int loopMode, boolean shuffle, boolean powered,
                                           long playheadMicros, int speedHundredths,
                                           int tempoBpm, int transpose,
                                           int transportRevision, long serverSampleNanos)
        implements CustomPacketPayload {
    public static final Type<MusicBoxPlaybackStatePacket> TYPE =
            new Type<>(M3E.id("music_box_playback_state"));
    public static final StreamCodec<FriendlyByteBuf, MusicBoxPlaybackStatePacket> CODEC =
            StreamCodec.ofMember(MusicBoxPlaybackStatePacket::write,
                    MusicBoxPlaybackStatePacket::read);

    private static MusicBoxPlaybackStatePacket read(FriendlyByteBuf buf) {
        return new MusicBoxPlaybackStatePacket(buf.readBlockPos(), buf.readBoolean(),
                buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                buf.readLong(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readLong());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(playing);
        buf.writeVarInt(loopMode);
        buf.writeBoolean(shuffle);
        buf.writeBoolean(powered);
        buf.writeLong(playheadMicros);
        buf.writeVarInt(speedHundredths);
        buf.writeVarInt(tempoBpm);
        buf.writeVarInt(transpose);
        buf.writeVarInt(transportRevision);
        buf.writeLong(serverSampleNanos);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
