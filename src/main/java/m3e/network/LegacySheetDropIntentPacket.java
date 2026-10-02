package m3e.network;

import m3e.M3E;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import m3e.sheet.MusicScore;

/** Marks the following native Music Maker import as an in-place file drop. */
public record LegacySheetDropIntentPacket(UUID sheetId, @Nullable MusicScore exactSong)
        implements CustomPacketPayload {
    public static final Type<LegacySheetDropIntentPacket> TYPE =
            new Type<>(M3E.id("legacy_sheet_drop_intent"));
    public static final StreamCodec<FriendlyByteBuf, LegacySheetDropIntentPacket> CODEC =
            StreamCodec.ofMember(LegacySheetDropIntentPacket::write, LegacySheetDropIntentPacket::read);

    private static LegacySheetDropIntentPacket read(FriendlyByteBuf buf) {
        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
        MusicScore song = buf.readBoolean() ? MusicScoreNetwork.read(buf) : null;
        return new LegacySheetDropIntentPacket(id, song);
    }

    private void write(FriendlyByteBuf buf) {
        UUIDUtil.STREAM_CODEC.encode(buf, sheetId);
        buf.writeBoolean(exactSong != null);
        if (exactSong != null) MusicScoreNetwork.write(buf, exactSong);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
