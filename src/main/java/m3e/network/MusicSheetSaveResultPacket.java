package m3e.network;

import m3e.M3E;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

/** Acknowledges one mutation; general score broadcasts are never save acknowledgements. */
public record MusicSheetSaveResultPacket(UUID requestId, boolean success, UUID sheetId,
                                         int version, String message) implements CustomPacketPayload {
    public MusicSheetSaveResultPacket {
        if (requestId == null || sheetId == null || version < 0 || message == null || message.length() > 256
                || success && (version < 1 || sheetId.equals(new UUID(0, 0)))) {
            throw new IllegalArgumentException("Invalid sheet save result");
        }
    }
    public static final Type<MusicSheetSaveResultPacket> TYPE = new Type<>(M3E.id("music_sheet_save_result"));
    public static final StreamCodec<FriendlyByteBuf, MusicSheetSaveResultPacket> CODEC =
            StreamCodec.ofMember(MusicSheetSaveResultPacket::write, MusicSheetSaveResultPacket::read);

    private static MusicSheetSaveResultPacket read(FriendlyByteBuf buffer) {
        return new MusicSheetSaveResultPacket(buffer.readUUID(), buffer.readBoolean(),
                buffer.readUUID(), buffer.readVarInt(), buffer.readUtf(256));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(requestId); buffer.writeBoolean(success); buffer.writeUUID(sheetId);
        buffer.writeVarInt(version); buffer.writeUtf(message, 256);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
