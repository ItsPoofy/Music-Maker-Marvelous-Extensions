package m3e.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

/** Carries the sheet title omitted by Music Maker's music-box update packet. */
public record MusicBoxLabelPacket(BlockPos pos, String sheetTitle) implements CustomPacketPayload {
    public static final Type<MusicBoxLabelPacket> TYPE =
            new Type<>(M3E.id("music_box_label"));
    public static final StreamCodec<FriendlyByteBuf, MusicBoxLabelPacket> CODEC =
            StreamCodec.ofMember(MusicBoxLabelPacket::write, MusicBoxLabelPacket::read);

    private static MusicBoxLabelPacket read(FriendlyByteBuf buf) {
        return new MusicBoxLabelPacket(buf.readBlockPos(), buf.readUtf(256));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(sheetTitle, 256);
    }

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
