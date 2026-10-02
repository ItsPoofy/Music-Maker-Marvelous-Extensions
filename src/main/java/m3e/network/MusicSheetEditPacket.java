package m3e.network;

import io.netty.buffer.Unpooled;
import m3e.M3E;
import m3e.sheet.MusicScore;
import m3e.sheet.SheetFileIO;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public final class MusicSheetEditPacket implements CustomPacketPayload {
    public static final Type<MusicSheetEditPacket> TYPE = new Type<>(M3E.id("music_sheet_edit"));
    public static final StreamCodec<FriendlyByteBuf, MusicSheetEditPacket> CODEC =
            StreamCodec.ofMember(MusicSheetEditPacket::write, MusicSheetEditPacket::read);

    private final UUID requestId;
    private final UUID sheetId;
    private final int version;
    private final int highlightInterval;
    private final boolean sign;
    private final int previewInstrument;
    private final boolean previewInstrumentLocked;
    private final MusicScore song;
    private final byte[] encoded;

    public MusicSheetEditPacket(UUID sheetId, int version, MusicScore song,
                                       int highlightInterval) {
        this(sheetId, version, song, highlightInterval, false);
    }

    public MusicSheetEditPacket(UUID sheetId, int version, MusicScore song,
                                int highlightInterval, boolean sign) {
        this(sheetId, version, song, highlightInterval, sign, -1, false);
    }

    public MusicSheetEditPacket(UUID sheetId, int version, MusicScore song,
                                int highlightInterval, boolean sign,
                                int previewInstrument, boolean previewInstrumentLocked) {
        this(UUID.randomUUID(), sheetId, version, song, highlightInterval, sign,
                previewInstrument, previewInstrumentLocked);
    }

    public MusicSheetEditPacket(UUID requestId, UUID sheetId, int version, MusicScore song,
                                int highlightInterval, boolean sign, int previewInstrument,
                                boolean previewInstrumentLocked) {
        this.requestId = requestId;
        this.sheetId = sheetId;
        this.version = version;
        this.highlightInterval = highlightInterval;
        this.sign = sign;
        this.previewInstrument = previewInstrument;
        this.previewInstrumentLocked = previewInstrumentLocked;
        this.song = song;
        this.encoded = null;
    }

    private MusicSheetEditPacket(UUID requestId, UUID sheetId, int version, int highlightInterval,
                                 boolean sign, int previewInstrument,
                                 boolean previewInstrumentLocked, byte[] encoded) {
        this.requestId = requestId;
        this.sheetId = sheetId;
        this.version = version;
        this.highlightInterval = highlightInterval;
        this.sign = sign;
        this.previewInstrument = previewInstrument;
        this.previewInstrumentLocked = previewInstrumentLocked;
        this.song = null;
        this.encoded = encoded;
    }

    private static MusicSheetEditPacket read(FriendlyByteBuf buffer) {
        UUID requestId = buffer.readUUID();
        UUID id = buffer.readUUID();
        int version = buffer.readVarInt();
        int highlightInterval = buffer.readUnsignedByte();
        boolean sign = buffer.readBoolean();
        int previewInstrument = buffer.readByte();
        boolean previewInstrumentLocked = buffer.readBoolean();
        int size = buffer.readableBytes();
        if (size < 1 || size > SheetFileIO.MAX_FILE_BYTES) throw new IllegalArgumentException("invalid sheet edit size");
        byte[] bytes = new byte[size];
        buffer.readBytes(bytes);
        return new MusicSheetEditPacket(requestId, id, version, highlightInterval, sign,
                previewInstrument, previewInstrumentLocked, bytes);
    }

    public UUID requestId() { return requestId; }
    public UUID sheetId() { return sheetId; }
    public int version() { return version; }
    public int highlightInterval() { return highlightInterval; }
    public boolean sign() { return sign; }
    public int previewInstrument() { return previewInstrument; }
    public boolean previewInstrumentLocked() { return previewInstrumentLocked; }
    public MusicScore decode() {
        if (song != null) return song;
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
        try {
            MusicScore result = MusicScoreNetwork.read(buffer);
            if (buffer.isReadable()) throw new IllegalArgumentException("trailing sheet edit data");
            return result;
        } finally { buffer.release(); }
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(requestId);
        buffer.writeUUID(sheetId);
        buffer.writeVarInt(version);
        buffer.writeByte(highlightInterval);
        buffer.writeBoolean(sign);
        buffer.writeByte(previewInstrument);
        buffer.writeBoolean(previewInstrumentLocked);
        if (encoded != null) buffer.writeBytes(encoded);
        else MusicScoreNetwork.write(buffer, song);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
