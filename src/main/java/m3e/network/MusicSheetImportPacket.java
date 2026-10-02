package m3e.network;

import io.netty.buffer.Unpooled;
import m3e.M3E;
import m3e.session.DrumNoteMap;
import m3e.sheet.MusicScore;
import m3e.sheet.SheetFileIO;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MusicSheetImportPacket implements CustomPacketPayload {
    public static final Type<MusicSheetImportPacket> TYPE =
            new Type<>(M3E.id("music_sheet_import"));
    public static final StreamCodec<FriendlyByteBuf, MusicSheetImportPacket> CODEC =
            StreamCodec.ofMember(MusicSheetImportPacket::write,
                    MusicSheetImportPacket::read);

    private final UUID requestId;
    private final UUID targetSheetId;
    private final int targetVersion;
    private final MusicScore song;
    private final List<Integer> drumMap;
    private final int highlightInterval;
    private final int previewInstrument;
    private final boolean previewInstrumentLocked;
    private final byte[] encoded;

    public MusicSheetImportPacket(UUID requestId, UUID targetSheetId, int targetVersion,
                                  MusicScore song, List<Integer> drumMap, int highlightInterval,
                                  int previewInstrument, boolean previewInstrumentLocked) {
        this.requestId = requestId;
        this.targetSheetId = targetSheetId;
        this.targetVersion = targetVersion;
        if (song == null) throw new IllegalArgumentException("missing music score");
        List<Integer> safeMap = drumMap == null ? List.of() : List.copyOf(drumMap);
        if (safeMap.size() > DrumNoteMap.SIZE) {
            throw new IllegalArgumentException("invalid drum map size");
        }
        this.song = song;
        this.drumMap = safeMap;
        this.highlightInterval = highlightInterval;
        this.previewInstrument = previewInstrument;
        this.previewInstrumentLocked = previewInstrumentLocked;
        encoded = null;
    }

    private MusicSheetImportPacket(UUID requestId, UUID targetSheetId, int targetVersion, byte[] encoded) {
        this.requestId = requestId;
        this.targetSheetId = targetSheetId;
        this.targetVersion = targetVersion;
        song = null;
        drumMap = null;
        highlightInterval = 0;
        previewInstrument = -1;
        previewInstrumentLocked = false;
        this.encoded = encoded;
    }

    private static MusicSheetImportPacket read(FriendlyByteBuf buffer) {
        UUID requestId = buffer.readUUID();
        UUID targetSheetId = buffer.readBoolean() ? buffer.readUUID() : null;
        int targetVersion = buffer.readVarInt();
        int size = buffer.readableBytes();
        if (size < 1 || size > SheetFileIO.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("invalid music score import size");
        }
        byte[] encoded = new byte[size];
        buffer.readBytes(encoded);
        return new MusicSheetImportPacket(requestId, targetSheetId, targetVersion, encoded);
    }

    public UUID requestId() { return requestId; }
    public UUID targetSheetId() { return targetSheetId; }
    public int targetVersion() { return targetVersion; }

    public Decoded decode() {
        if (encoded == null) return new Decoded(song, drumMap, highlightInterval,
                previewInstrument, previewInstrumentLocked);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
        try {
            MusicScore decodedSong = MusicScoreNetwork.read(buffer);
            int size = buffer.readVarInt();
            if (size < 0 || size > DrumNoteMap.SIZE) {
                throw new IllegalArgumentException("invalid drum map size");
            }
            ArrayList<Integer> decodedMap = new ArrayList<>(size);
            for (int index = 0; index < size; index++) decodedMap.add(buffer.readVarInt());
            int decodedHighlightInterval = buffer.readUnsignedByte();
            int decodedPreviewInstrument = buffer.readByte();
            boolean decodedPreviewInstrumentLocked = buffer.readBoolean();
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("trailing music score import data");
            }
            return new Decoded(decodedSong, List.copyOf(decodedMap), decodedHighlightInterval,
                    decodedPreviewInstrument, decodedPreviewInstrumentLocked);
        } finally {
            buffer.release();
        }
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(requestId);
        buffer.writeBoolean(targetSheetId != null);
        if (targetSheetId != null) buffer.writeUUID(targetSheetId);
        buffer.writeVarInt(targetVersion);
        if (encoded != null) {
            buffer.writeBytes(encoded);
            return;
        }
        MusicScoreNetwork.write(buffer, song);
        buffer.writeVarInt(drumMap.size());
        drumMap.forEach(buffer::writeVarInt);
        buffer.writeByte(highlightInterval);
        buffer.writeByte(previewInstrument);
        buffer.writeBoolean(previewInstrumentLocked);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Decoded(MusicScore song, List<Integer> drumMap,
                          int highlightInterval, int previewInstrument,
                          boolean previewInstrumentLocked) { }
}
