package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

import java.util.UUID;

public record SongbookCatalogRequestPacket(UUID bookId) implements CustomPacketPayload {
    public static final Type<SongbookCatalogRequestPacket> TYPE = new Type<>(M3E.id("songbook_catalog_request"));
    public static final StreamCodec<FriendlyByteBuf, SongbookCatalogRequestPacket> CODEC =
            StreamCodec.ofMember(SongbookCatalogRequestPacket::write, SongbookCatalogRequestPacket::read);
    private static SongbookCatalogRequestPacket read(FriendlyByteBuf buffer) { return new SongbookCatalogRequestPacket(buffer.readUUID()); }
    private void write(FriendlyByteBuf buffer) { buffer.writeUUID(bookId); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
