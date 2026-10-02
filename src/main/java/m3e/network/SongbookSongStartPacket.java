package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;
import m3e.songbook.SongbookContents;

import java.util.UUID;

public record SongbookSongStartPacket(UUID bookId, int revision, UUID entryId, int size,
                                      String sha256) implements CustomPacketPayload {
    public static final Type<SongbookSongStartPacket> TYPE = new Type<>(M3E.id("songbook_song_start"));
    public static final StreamCodec<FriendlyByteBuf, SongbookSongStartPacket> CODEC =
            StreamCodec.ofMember(SongbookSongStartPacket::write, SongbookSongStartPacket::read);
    private static SongbookSongStartPacket read(FriendlyByteBuf b) {
        SongbookSongStartPacket p = new SongbookSongStartPacket(b.readUUID(), b.readVarInt(), b.readUUID(), b.readVarInt(), b.readUtf(64));
        if (p.size <= 0 || p.size > SongbookContents.MAX_ENCODED_BYTES || !p.sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid song transfer");
        return p;
    }
    private void write(FriendlyByteBuf b) { b.writeUUID(bookId); b.writeVarInt(revision); b.writeUUID(entryId); b.writeVarInt(size); b.writeUtf(sha256, 64); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
