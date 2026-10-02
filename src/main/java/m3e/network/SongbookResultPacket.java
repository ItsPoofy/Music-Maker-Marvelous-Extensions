package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

public record SongbookResultPacket(boolean success, String message) implements CustomPacketPayload {
    public static final Type<SongbookResultPacket> TYPE = new Type<>(M3E.id("songbook_result"));
    public static final StreamCodec<FriendlyByteBuf, SongbookResultPacket> CODEC = StreamCodec.ofMember(SongbookResultPacket::write, SongbookResultPacket::read);
    private static SongbookResultPacket read(FriendlyByteBuf b) { return new SongbookResultPacket(b.readBoolean(), b.readUtf(180)); }
    private void write(FriendlyByteBuf b) { b.writeBoolean(success); b.writeUtf(message,180); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
