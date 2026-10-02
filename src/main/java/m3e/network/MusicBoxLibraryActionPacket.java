package m3e.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

public record MusicBoxLibraryActionPacket(BlockPos pos, byte action,
                                          int index, int revision, int value)
        implements CustomPacketPayload {
    public static final byte SELECT = 0;
    public static final byte DELETE = 1;
    public static final byte EXTRACT = 2;
    public static final byte SET_TEMPO = 3;
    public static final Type<MusicBoxLibraryActionPacket> TYPE =
            new Type<>(M3E.id("music_box_library_action"));
    public static final StreamCodec<FriendlyByteBuf, MusicBoxLibraryActionPacket> CODEC =
            StreamCodec.ofMember(MusicBoxLibraryActionPacket::write,
                    MusicBoxLibraryActionPacket::read);

    private static MusicBoxLibraryActionPacket read(FriendlyByteBuf buffer) {
        return new MusicBoxLibraryActionPacket(buffer.readBlockPos(),
                buffer.readByte(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeByte(action);
        buffer.writeVarInt(index);
        buffer.writeVarInt(revision);
        buffer.writeVarInt(value);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
