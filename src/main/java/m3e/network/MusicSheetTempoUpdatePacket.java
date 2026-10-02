package m3e.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

import java.util.UUID;

/** Lossless tempo edit for one MIDI-backed Music Maker sheet. */
public record MusicSheetTempoUpdatePacket(UUID sheetId, int version, int bpm)
        implements CustomPacketPayload {
    public static final Type<MusicSheetTempoUpdatePacket> TYPE =
            new Type<>(M3E.id("music_sheet_tempo_update"));
    public static final StreamCodec<FriendlyByteBuf, MusicSheetTempoUpdatePacket> CODEC =
            StreamCodec.ofMember(MusicSheetTempoUpdatePacket::write,
                    MusicSheetTempoUpdatePacket::read);

    private static MusicSheetTempoUpdatePacket read(FriendlyByteBuf buf) {
        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
        int version = buf.readVarInt();
        int bpm = buf.readVarInt();
        if (version < 0 || bpm < 1 || bpm > 3_000) {
            throw new IllegalArgumentException("invalid music sheet tempo update");
        }
        return new MusicSheetTempoUpdatePacket(id, version, bpm);
    }

    private void write(FriendlyByteBuf buf) {
        UUIDUtil.STREAM_CODEC.encode(buf, sheetId);
        buf.writeVarInt(version);
        buf.writeVarInt(bpm);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
