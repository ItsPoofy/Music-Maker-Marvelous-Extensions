package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

import java.util.UUID;

public record SongbookOpenPacket(UUID bookId) implements CustomPacketPayload {
    public static final Type<SongbookOpenPacket> TYPE = new Type<>(M3E.id("songbook_open"));
    public static final StreamCodec<FriendlyByteBuf, SongbookOpenPacket> CODEC =
            StreamCodec.ofMember(SongbookOpenPacket::write, SongbookOpenPacket::read);
    private static SongbookOpenPacket read(FriendlyByteBuf buffer) {
        return new SongbookOpenPacket(buffer.readUUID());
    }
    private void write(FriendlyByteBuf buffer) { buffer.writeUUID(bookId); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
