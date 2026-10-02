package m3e.network;

import m3e.M3E;
import m3e.sheet.MusicScore;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record MusicSheetDataPacket(UUID sheetId, int version,
                                          @Nullable MusicScore song) implements CustomPacketPayload {
    public static final Type<MusicSheetDataPacket> TYPE =
            new Type<>(M3E.id("music_sheet_data"));
    public static final StreamCodec<FriendlyByteBuf, MusicSheetDataPacket> CODEC =
            StreamCodec.ofMember(MusicSheetDataPacket::write, MusicSheetDataPacket::read);

    private static MusicSheetDataPacket read(FriendlyByteBuf buf) {
        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
        int version = buf.readVarInt();
        if (version < 0) throw new IllegalArgumentException("invalid sheet version");
        return new MusicSheetDataPacket(id, version,
                buf.readBoolean() ? MusicScoreNetwork.read(buf) : null);
    }

    private void write(FriendlyByteBuf buf) {
        UUIDUtil.STREAM_CODEC.encode(buf, sheetId);
        buf.writeVarInt(version);
        buf.writeBoolean(song != null);
        if (song != null) MusicScoreNetwork.write(buf, song);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
