package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

public record SongbookSongChunkPacket(int offset, byte[] bytes) implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 32 * 1024;
    public static final Type<SongbookSongChunkPacket> TYPE = new Type<>(M3E.id("songbook_song_chunk"));
    public static final StreamCodec<FriendlyByteBuf, SongbookSongChunkPacket> CODEC =
            StreamCodec.ofMember(SongbookSongChunkPacket::write, SongbookSongChunkPacket::read);
    public SongbookSongChunkPacket { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    private static SongbookSongChunkPacket read(FriendlyByteBuf b) {
        int offset = b.readVarInt(); byte[] bytes = b.readByteArray(MAX_CHUNK_BYTES);
        if (offset < 0 || bytes.length == 0) throw new IllegalArgumentException("invalid song chunk");
        return new SongbookSongChunkPacket(offset, bytes);
    }
    private void write(FriendlyByteBuf b) { b.writeVarInt(offset); b.writeByteArray(bytes); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
