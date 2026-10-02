package m3e.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

public record MusicBoxPlaybackControlPacket(BlockPos pos, byte action, long value)
        implements CustomPacketPayload {
    public static final byte REQUEST = 0;
    public static final byte START = 1;
    public static final byte STOP = 2;
    public static final byte TOGGLE_LOOP = 3;
    public static final byte PAUSE = 4;
    public static final byte SEEK_MICROS = 5;
    public static final byte SET_SPEED_HUNDREDTHS = 6;
    public static final byte SET_TRANSPOSE = 7;
    public static final byte SET_TEMPO = 8;
    public static final byte TOGGLE_SHUFFLE = 9;
    public static final Type<MusicBoxPlaybackControlPacket> TYPE =
            new Type<>(M3E.id("music_box_playback_control"));
    public static final StreamCodec<FriendlyByteBuf, MusicBoxPlaybackControlPacket> CODEC =
            StreamCodec.ofMember(MusicBoxPlaybackControlPacket::write,
                    MusicBoxPlaybackControlPacket::read);

    private static MusicBoxPlaybackControlPacket read(FriendlyByteBuf buf) {
        return new MusicBoxPlaybackControlPacket(buf.readBlockPos(), buf.readByte(),
                buf.readLong());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByte(action);
        buf.writeLong(value);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
