package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

import java.util.UUID;

public record SongbookSongFinishPacket(UUID entryId) implements CustomPacketPayload {
    public static final Type<SongbookSongFinishPacket> TYPE = new Type<>(M3E.id("songbook_song_finish"));
    public static final StreamCodec<FriendlyByteBuf, SongbookSongFinishPacket> CODEC =
            StreamCodec.ofMember(SongbookSongFinishPacket::write, SongbookSongFinishPacket::read);
    private static SongbookSongFinishPacket read(FriendlyByteBuf b) { return new SongbookSongFinishPacket(b.readUUID()); }
    private void write(FriendlyByteBuf b) { b.writeUUID(entryId); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
