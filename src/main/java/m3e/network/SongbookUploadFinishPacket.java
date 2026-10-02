package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

public record SongbookUploadFinishPacket() implements CustomPacketPayload {
    public static final Type<SongbookUploadFinishPacket> TYPE = new Type<>(M3E.id("songbook_upload_finish"));
    public static final StreamCodec<FriendlyByteBuf, SongbookUploadFinishPacket> CODEC = StreamCodec.unit(new SongbookUploadFinishPacket());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
