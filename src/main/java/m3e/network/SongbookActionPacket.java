package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

import java.util.UUID;

public record SongbookActionPacket(UUID bookId, int revision, UUID entryId, Action action,
                                   String text, int value) implements CustomPacketPayload {
    public enum Action { RENAME, DELETE, SET_TEMPO, EXTRACT_SHEET, RENAME_BOOK, MOVE, SET_INSTRUMENT }
    public static final Type<SongbookActionPacket> TYPE = new Type<>(M3E.id("songbook_action"));
    public static final StreamCodec<FriendlyByteBuf, SongbookActionPacket> CODEC = StreamCodec.ofMember(SongbookActionPacket::write, SongbookActionPacket::read);
    private static SongbookActionPacket read(FriendlyByteBuf b) {
        UUID book=b.readUUID(); int revision=b.readVarInt(); UUID entry=b.readUUID(); int ordinal=b.readUnsignedByte(); String text=b.readUtf(128); int value=b.readVarInt();
        if(revision<0||ordinal>=Action.values().length) throw new IllegalArgumentException("invalid Songbook action");
        return new SongbookActionPacket(book,revision,entry,Action.values()[ordinal],text,value);
    }
    private void write(FriendlyByteBuf b) { b.writeUUID(bookId); b.writeVarInt(revision); b.writeUUID(entryId); b.writeByte(action.ordinal()); b.writeUtf(text == null ? "" : text,128); b.writeVarInt(value); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
