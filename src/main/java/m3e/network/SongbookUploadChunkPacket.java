package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

public record SongbookUploadChunkPacket(int offset, byte[] bytes) implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 32 * 1024;
    public static final Type<SongbookUploadChunkPacket> TYPE = new Type<>(M3E.id("songbook_upload_chunk"));
    public static final StreamCodec<FriendlyByteBuf, SongbookUploadChunkPacket> CODEC = StreamCodec.ofMember(SongbookUploadChunkPacket::write, SongbookUploadChunkPacket::read);
    public SongbookUploadChunkPacket { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    private static SongbookUploadChunkPacket read(FriendlyByteBuf b) { int o=b.readVarInt(); byte[] v=b.readByteArray(MAX_CHUNK_BYTES); if(o<0||v.length==0) throw new IllegalArgumentException("invalid upload chunk"); return new SongbookUploadChunkPacket(o,v); }
    private void write(FriendlyByteBuf b) { b.writeVarInt(offset); b.writeByteArray(bytes); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
