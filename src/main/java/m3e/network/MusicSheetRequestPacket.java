package m3e.network;

import m3e.M3E;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record MusicSheetRequestPacket(UUID sheetId, int version) implements CustomPacketPayload {
    public static final Type<MusicSheetRequestPacket> TYPE =
            new Type<>(M3E.id("music_sheet_request"));
    public static final StreamCodec<FriendlyByteBuf, MusicSheetRequestPacket> CODEC =
            StreamCodec.ofMember(MusicSheetRequestPacket::write, MusicSheetRequestPacket::read);

    private static MusicSheetRequestPacket read(FriendlyByteBuf buf) {
        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
        int version = buf.readVarInt();
        if (version < 0) throw new IllegalArgumentException("invalid sheet version");
        return new MusicSheetRequestPacket(id, version);
    }

    private void write(FriendlyByteBuf buf) {
        UUIDUtil.STREAM_CODEC.encode(buf, sheetId);
        buf.writeVarInt(version);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
