package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;
import m3e.songbook.SongbookContents;

import java.util.UUID;

public record SongbookUploadStartPacket(UUID bookId, int revision, String displayName, int size,
                                        int storedSize, String sha256, Kind kind) implements CustomPacketPayload {
    public enum Kind { MIDI, SHEET }
    public static final Type<SongbookUploadStartPacket> TYPE = new Type<>(M3E.id("songbook_upload_start"));
    public static final StreamCodec<FriendlyByteBuf, SongbookUploadStartPacket> CODEC =
            StreamCodec.ofMember(SongbookUploadStartPacket::write, SongbookUploadStartPacket::read);
    private static SongbookUploadStartPacket read(FriendlyByteBuf b) {
        UUID book = b.readUUID();
        int revision = b.readVarInt();
        String displayName = b.readUtf(96);
        int size = b.readVarInt();
        int storedSize = b.readVarInt();
        String sha256 = b.readUtf(64);
        int kind = b.readUnsignedByte();
        if (kind >= Kind.values().length) throw new IllegalArgumentException("invalid Songbook upload kind");
        SongbookUploadStartPacket p = new SongbookUploadStartPacket(book, revision, displayName,
                size, storedSize, sha256, Kind.values()[kind]);
        if (p.revision < 0 || p.displayName.isBlank() || p.size <= 0
                || p.size > SongbookContents.MAX_ENCODED_BYTES || p.storedSize <= 0
                || p.storedSize > SongbookContents.MAX_ENCODED_BYTES
                || !p.sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid Songbook upload");
        return p;
    }
    private void write(FriendlyByteBuf b) { b.writeUUID(bookId); b.writeVarInt(revision); b.writeUtf(displayName, 96); b.writeVarInt(size); b.writeVarInt(storedSize); b.writeUtf(sha256, 64); b.writeByte(kind.ordinal()); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
