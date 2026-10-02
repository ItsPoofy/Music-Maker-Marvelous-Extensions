package m3e.network;

import m3e.sheet.MusicScore;
import m3e.session.DrumNoteMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public final class MusicScoreNetwork {
    private static final int MAGIC = 0x4D334553;
    public static final StreamCodec<FriendlyByteBuf, MusicScore> STREAM_CODEC =
            StreamCodec.of(MusicScoreNetwork::write, MusicScoreNetwork::read);

    private MusicScoreNetwork() { }

    public static MusicScore read(FriendlyByteBuf buf) {
        if (buf.readInt() != MAGIC) throw new IllegalArgumentException("Invalid M3E score header");
        int version = buf.readVarInt();
        if (version != MusicScore.FORMAT_VERSION) throw new IllegalArgumentException("Unsupported M3E score version " + version);
        return readCurrent(buf);
    }

    private static MusicScore readCurrent(FriendlyByteBuf buf) {
        Base base = readBase(buf);
        String author = buf.readUtf(96);
        boolean signed = buf.readBoolean();
        String instrumentId = buf.readUtf(128);
        boolean convertDynamics = buf.readBoolean();
        boolean convertGlissando = buf.readBoolean();

        int trackCount = readCount(buf, 256, "track");
        if (trackCount < 1) throw new IllegalArgumentException("score has no tracks");
        List<MusicScore.TrackInfo> tracks = new ArrayList<>(trackCount);
        for (int index = 0; index < trackCount; index++) {
            int id = buf.readVarInt();
            String name = buf.readUtf(96);
            boolean percussion = buf.readBoolean();
            int octave = buf.readByte();
            int mapSize = readCount(buf, DrumNoteMap.SIZE, "drum map");
            List<Integer> map = new ArrayList<>(mapSize);
            for (int mapIndex = 0; mapIndex < mapSize; mapIndex++) map.add(buf.readVarInt());
            tracks.add(new MusicScore.TrackInfo(id, name, percussion, octave, map));
        }

        int expressionCount = readCount(buf, MusicScore.MAX_EXPRESSION_EVENTS, "expression");
        List<MusicScore.ExpressionEvent> expressions = new ArrayList<>(expressionCount);
        long previousTick = 0L;
        for (int index = 0; index < expressionCount; index++) {
            long tick = Math.addExact(previousTick,
                    readBoundedVarLong(buf, MusicScore.MAX_TICKS, "expression tick delta"));
            expressions.add(new MusicScore.ExpressionEvent(buf.readVarInt(), tick,
                    buf.readFloat(), buf.readUnsignedByte(), buf.readUnsignedByte(),
                    buf.readUnsignedByte(), buf.readBoolean()));
            previousTick = tick;
        }

        int noteCount = readCount(buf, MusicScore.MAX_NOTES, "note");
        List<MusicScore.MidiNote> notes = new ArrayList<>(noteCount);
        long previousStart = 0L;
        for (int index = 0; index < noteCount; index++) {
            int track = buf.readVarInt();
            int pitch = buf.readUnsignedByte();
            int velocity = buf.readUnsignedByte();
            long start = Math.addExact(previousStart,
                    readBoundedVarLong(buf, MusicScore.MAX_TICKS, "note start delta"));
            long release = Math.addExact(start,
                    readBoundedVarLong(buf, MusicScore.MAX_TICKS, "note release delta"));
            long end = Math.addExact(release,
                    readBoundedVarLong(buf, MusicScore.MAX_TICKS, "note sustain delta"));
            if (end > MusicScore.MAX_TICKS) throw new IllegalArgumentException("note time is too large");
            notes.add(new MusicScore.MidiNote(track, pitch, velocity, start, release, end));
            previousStart = start;
        }
        MusicScore.AutomationData automation = readAutomation(buf);
        return new MusicScore(base.ppq, base.percussion, base.sourceName, base.lengthTick,
                base.tuningHz, base.tempos, base.signatures, expressions, notes, automation, tracks,
                author, signed, instrumentId, convertDynamics, convertGlissando);
    }

    private static MusicScore.AutomationData readAutomation(FriendlyByteBuf buf) {
        int rampCount = readCount(buf, 4_096, "volume ramp");
        List<MusicScore.VolumeRamp> ramps = new ArrayList<>(rampCount);
        for (int index = 0; index < rampCount; index++) {
            ramps.add(new MusicScore.VolumeRamp(buf.readVarInt(), buf.readVarLong(),
                    buf.readVarLong(), buf.readUnsignedByte(), buf.readUnsignedByte(),
                    buf.readUnsignedByte(), buf.readUnsignedByte()));
        }
        int glissandoCount = readCount(buf, MusicScore.MAX_NOTES, "glissando");
        List<MusicScore.Glissando> glissandos = new ArrayList<>(glissandoCount);
        for (int index = 0; index < glissandoCount; index++) {
            int track = buf.readVarInt();
            long start = buf.readVarLong();
            int pitch = buf.readUnsignedByte();
            int pointCount = readCount(buf, 8, "glissando point");
            List<MusicScore.GlissandoPoint> points = new ArrayList<>(pointCount);
            for (int point = 0; point < pointCount; point++) {
                points.add(new MusicScore.GlissandoPoint(
                        buf.readUnsignedByte(), buf.readByte()));
            }
            glissandos.add(new MusicScore.Glissando(track, start, pitch, points));
        }
        return new MusicScore.AutomationData(ramps, glissandos, buf.readVarInt());
    }

    private static Base readBase(FriendlyByteBuf buf) {
        int ppq = buf.readUnsignedShort();
        boolean percussion = buf.readBoolean();
        String sourceName = buf.readUtf(128);
        long lengthTick = readBoundedVarLong(buf, MusicScore.MAX_TICKS, "length");
        float tuningHz = buf.readFloat();
        int tempoCount = readCount(buf, MusicScore.MAX_TEMPO_EVENTS, "tempo");
        List<MusicScore.TempoChange> tempos = new ArrayList<>(tempoCount);
        for (int index = 0; index < tempoCount; index++) {
            tempos.add(new MusicScore.TempoChange(
                    readBoundedVarLong(buf, MusicScore.MAX_TICKS, "tempo tick"), buf.readVarInt()));
        }
        int signatureCount = readCount(buf, MusicScore.MAX_TIME_SIGNATURES, "time signature");
        List<MusicScore.TimeSignature> signatures = new ArrayList<>(signatureCount);
        for (int index = 0; index < signatureCount; index++) {
            signatures.add(new MusicScore.TimeSignature(
                    readBoundedVarLong(buf, MusicScore.MAX_TICKS, "time-signature tick"),
                    buf.readUnsignedByte(), buf.readUnsignedByte()));
        }
        return new Base(ppq, percussion, sourceName, lengthTick, tuningHz, tempos, signatures);
    }

    public static void write(FriendlyByteBuf buf, MusicScore song) {
        buf.writeInt(MAGIC);
        buf.writeVarInt(MusicScore.FORMAT_VERSION);
        writeBase(buf, song);
        buf.writeUtf(song.author(), 96);
        buf.writeBoolean(song.signed());
        buf.writeUtf(song.instrumentId(), 128);
        buf.writeBoolean(song.convertDynamics());
        buf.writeBoolean(song.convertGlissando());
        buf.writeVarInt(song.tracks().size());
        for (MusicScore.TrackInfo track : song.tracks()) {
            buf.writeVarInt(track.id());
            buf.writeUtf(track.name(), 96);
            buf.writeBoolean(track.percussion());
            buf.writeByte(track.octave());
            buf.writeVarInt(track.drumMap().size());
            track.drumMap().forEach(buf::writeVarInt);
        }
        buf.writeVarInt(song.expressions().size());
        long previousExpressionTick = 0L;
        for (MusicScore.ExpressionEvent expression : song.expressions()) {
            buf.writeVarLong(expression.tick() - previousExpressionTick);
            buf.writeVarInt(expression.track());
            buf.writeFloat(expression.bendSemitones());
            buf.writeByte(expression.modulation());
            buf.writeByte(expression.channelVolume());
            buf.writeByte(expression.expression());
            buf.writeBoolean(expression.sustain());
            previousExpressionTick = expression.tick();
        }
        buf.writeVarInt(song.notes().size());
        long previousStart = 0L;
        for (MusicScore.MidiNote note : song.notes()) {
            buf.writeVarInt(note.track());
            buf.writeByte(note.pitch());
            buf.writeByte(note.velocity());
            buf.writeVarLong(note.startTick() - previousStart);
            buf.writeVarLong(note.keyReleaseTick() - note.startTick());
            buf.writeVarLong(note.endTick() - note.keyReleaseTick());
            previousStart = note.startTick();
        }
        writeAutomation(buf, song.automation());
    }

    private static void writeAutomation(FriendlyByteBuf buf,
                                        MusicScore.AutomationData automation) {
        buf.writeVarInt(automation.volumeRamps().size());
        for (MusicScore.VolumeRamp ramp : automation.volumeRamps()) {
            buf.writeVarInt(ramp.track());
            buf.writeVarLong(ramp.startTick());
            buf.writeVarLong(ramp.endTick());
            buf.writeByte(ramp.startVelocity());
            buf.writeByte(ramp.endVelocity());
            buf.writeByte(ramp.minPitch());
            buf.writeByte(ramp.maxPitch());
        }
        buf.writeVarInt(automation.glissandos().size());
        for (MusicScore.Glissando glissando : automation.glissandos()) {
            buf.writeVarInt(glissando.track());
            buf.writeVarLong(glissando.noteStartTick());
            buf.writeByte(glissando.pitch());
            buf.writeVarInt(glissando.points().size());
            for (MusicScore.GlissandoPoint point : glissando.points()) {
                buf.writeByte(point.positionPercent());
                buf.writeByte(point.semitones());
            }
        }
        buf.writeVarInt(automation.baseTempoBpm());
    }

    private static void writeBase(FriendlyByteBuf buf, MusicScore song) {
        buf.writeShort(song.ppq());
        buf.writeBoolean(song.percussion());
        buf.writeUtf(song.sourceName(), 128);
        buf.writeVarLong(song.lengthTick());
        buf.writeFloat(song.tuningHz());
        buf.writeVarInt(song.tempos().size());
        for (MusicScore.TempoChange tempo : song.tempos()) {
            buf.writeVarLong(tempo.tick());
            buf.writeVarInt(tempo.microsPerQuarter());
        }
        buf.writeVarInt(song.timeSignatures().size());
        for (MusicScore.TimeSignature signature : song.timeSignatures()) {
            buf.writeVarLong(signature.tick());
            buf.writeByte(signature.numerator());
            buf.writeByte(signature.denominator());
        }
    }

    private static int readCount(FriendlyByteBuf buf, int maximum, String name) {
        int value = buf.readVarInt();
        if (value < 0 || value > maximum) throw new IllegalArgumentException("invalid " + name + " count");
        return value;
    }

    private static long readBoundedVarLong(FriendlyByteBuf buf, long maximum, String name) {
        long value = buf.readVarLong();
        if (value < 0 || value > maximum) throw new IllegalArgumentException("invalid " + name);
        return value;
    }

    private record Base(int ppq, boolean percussion, String sourceName, long lengthTick,
                        float tuningHz, List<MusicScore.TempoChange> tempos,
                        List<MusicScore.TimeSignature> signatures) { }
}
