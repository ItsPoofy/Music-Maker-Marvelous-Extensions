package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

import java.util.UUID;

public record SongbookSongRequestPacket(UUID bookId, int revision, UUID entryId) implements CustomPacketPayload {
    public static final Type<SongbookSongRequestPacket> TYPE = new Type<>(M3E.id("songbook_song_request"));
    public static final StreamCodec<FriendlyByteBuf, SongbookSongRequestPacket> CODEC =
            StreamCodec.ofMember(SongbookSongRequestPacket::write, SongbookSongRequestPacket::read);
    private static SongbookSongRequestPacket read(FriendlyByteBuf b) { return new SongbookSongRequestPacket(b.readUUID(), b.readVarInt(), b.readUUID()); }
    private void write(FriendlyByteBuf b) { b.writeUUID(bookId); b.writeVarInt(revision); b.writeUUID(entryId); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
