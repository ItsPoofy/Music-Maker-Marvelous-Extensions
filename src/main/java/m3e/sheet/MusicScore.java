package m3e.sheet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MusicScore {
    private final int ppq;
    private final boolean percussion;
    private final String sourceName;
    private final long lengthTick;
    private final float tuningHz;
    private final List<TempoChange> tempos;
    private final List<TimeSignature> timeSignatures;
    private final List<ExpressionEvent> expressions;
    private final List<MidiNote> notes;
    private final AutomationData automation;
    private final List<TrackInfo> tracks;
    private final String author;
    private final boolean signed;
    private final String instrumentId;
    private final boolean convertDynamics;
    private final boolean convertGlissando;
    private final TempoMap tempoMap;

    public int ppq() { return ppq; }
    public boolean percussion() { return percussion; }
    public String sourceName() { return sourceName; }
    public long lengthTick() { return lengthTick; }
    public float tuningHz() { return tuningHz; }
    public List<TempoChange> tempos() { return tempos; }
    public List<TimeSignature> timeSignatures() { return timeSignatures; }
    public List<ExpressionEvent> expressions() { return expressions; }
    public List<MidiNote> notes() { return notes; }
    public AutomationData automation() { return automation; }
    public List<TrackInfo> tracks() { return tracks; }
    public String author() { return author; }
    public boolean signed() { return signed; }
    public String instrumentId() { return instrumentId; }
    public boolean convertDynamics() { return convertDynamics; }
    public boolean convertGlissando() { return convertGlissando; }

    public static final int FORMAT_VERSION = 3;
    public static final int MIN_PITCH = 21;
    public static final int MAX_PITCH = 116;
    public static final int MAX_TRACKS = 256;
    public static final int MAX_NOTES = 16_384;
    public static final int MAX_TEMPO_EVENTS = 2_048;
    public static final int MAX_TIME_SIGNATURES = 256;
    public static final int MAX_EXPRESSION_EVENTS = 32_768;
    public static final long MAX_TICKS = 1L << 40;

    public static final Codec<TempoChange> TEMPO_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("tick").forGetter(TempoChange::tick),
            Codec.INT.fieldOf("micros_per_quarter").forGetter(TempoChange::microsPerQuarter)
    ).apply(instance, TempoChange::new));
    public static final Codec<TimeSignature> TIME_SIGNATURE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("tick").forGetter(TimeSignature::tick),
            Codec.INT.fieldOf("numerator").forGetter(TimeSignature::numerator),
            Codec.INT.fieldOf("denominator").forGetter(TimeSignature::denominator)
    ).apply(instance, TimeSignature::new));
    public static final Codec<MidiNote> NOTE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("track", 0).forGetter(MidiNote::track),
            Codec.INT.fieldOf("pitch").forGetter(MidiNote::pitch),
            Codec.INT.fieldOf("velocity").forGetter(MidiNote::velocity),
            Codec.LONG.fieldOf("start").forGetter(MidiNote::startTick),
            Codec.LONG.fieldOf("key_release").forGetter(MidiNote::keyReleaseTick),
            Codec.LONG.fieldOf("end").forGetter(MidiNote::endTick)
    ).apply(instance, MidiNote::new));
    public static final Codec<ExpressionEvent> EXPRESSION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("track", 0).forGetter(ExpressionEvent::track),
            Codec.LONG.fieldOf("tick").forGetter(ExpressionEvent::tick),
            Codec.FLOAT.fieldOf("bend_semitones").forGetter(ExpressionEvent::bendSemitones),
            Codec.INT.fieldOf("modulation").forGetter(ExpressionEvent::modulation),
            Codec.INT.optionalFieldOf("channel_volume", 127).forGetter(ExpressionEvent::channelVolume),
            Codec.INT.optionalFieldOf("expression", 127).forGetter(ExpressionEvent::expression),
            Codec.BOOL.optionalFieldOf("sustain", false).forGetter(ExpressionEvent::sustain)
    ).apply(instance, ExpressionEvent::new));
    public static final Codec<TrackInfo> TRACK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(TrackInfo::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(TrackInfo::name),
            Codec.BOOL.optionalFieldOf("percussion", false).forGetter(TrackInfo::percussion),
            Codec.INT.optionalFieldOf("octave", 0).forGetter(TrackInfo::octave),
            Codec.INT.listOf().optionalFieldOf("drum_map", List.of()).forGetter(TrackInfo::drumMap)
    ).apply(instance, TrackInfo::new));
    public static final Codec<VolumeRamp> VOLUME_RAMP_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("track", 0).forGetter(VolumeRamp::track),
            Codec.LONG.fieldOf("start").forGetter(VolumeRamp::startTick),
            Codec.LONG.fieldOf("end").forGetter(VolumeRamp::endTick),
            Codec.INT.fieldOf("start_velocity").forGetter(VolumeRamp::startVelocity),
            Codec.INT.fieldOf("end_velocity").forGetter(VolumeRamp::endVelocity),
            Codec.INT.optionalFieldOf("min_pitch", 21).forGetter(VolumeRamp::minPitch),
            Codec.INT.optionalFieldOf("max_pitch", 116).forGetter(VolumeRamp::maxPitch)
    ).apply(instance, VolumeRamp::new));
    public static final Codec<GlissandoPoint> GLISSANDO_POINT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("position").forGetter(GlissandoPoint::positionPercent),
            Codec.INT.fieldOf("semitones").forGetter(GlissandoPoint::semitones)
    ).apply(instance, GlissandoPoint::new));
    public static final Codec<Glissando> GLISSANDO_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("track", 0).forGetter(Glissando::track),
            Codec.LONG.fieldOf("note_start").forGetter(Glissando::noteStartTick),
            Codec.INT.fieldOf("pitch").forGetter(Glissando::pitch),
            GLISSANDO_POINT_CODEC.listOf().fieldOf("points").forGetter(Glissando::points)
    ).apply(instance, Glissando::new));
    public static final Codec<AutomationData> AUTOMATION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VOLUME_RAMP_CODEC.listOf().optionalFieldOf("volume_ramps", List.of()).forGetter(AutomationData::volumeRamps),
            GLISSANDO_CODEC.listOf().optionalFieldOf("glissandos", List.of()).forGetter(AutomationData::glissandos),
            Codec.INT.optionalFieldOf("base_tempo_bpm", 0).forGetter(AutomationData::baseTempoBpm)
    ).apply(instance, AutomationData::new));
    public static final Codec<MusicScore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("ppq").forGetter(MusicScore::ppq),
            Codec.BOOL.optionalFieldOf("percussion", false).forGetter(MusicScore::percussion),
            Codec.STRING.fieldOf("source_name").forGetter(MusicScore::sourceName),
            Codec.LONG.fieldOf("length_tick").forGetter(MusicScore::lengthTick),
            Codec.FLOAT.optionalFieldOf("tuning_hz", 440.0F).forGetter(MusicScore::tuningHz),
            TEMPO_CODEC.listOf().fieldOf("tempos").forGetter(MusicScore::tempos),
            TIME_SIGNATURE_CODEC.listOf().fieldOf("time_signatures").forGetter(MusicScore::timeSignatures),
            EXPRESSION_CODEC.listOf().optionalFieldOf("expressions", List.of())
                    .forGetter(MusicScore::expressions),
            NOTE_CODEC.listOf().fieldOf("notes").forGetter(MusicScore::notes),
            AUTOMATION_CODEC.optionalFieldOf("automation", AutomationData.EMPTY).forGetter(MusicScore::automation),
            TRACK_CODEC.listOf().optionalFieldOf("tracks", List.of()).forGetter(MusicScore::tracks),
            Codec.STRING.optionalFieldOf("author", "").forGetter(MusicScore::author),
            Codec.BOOL.optionalFieldOf("signed", false).forGetter(MusicScore::signed),
            Codec.STRING.optionalFieldOf("instrument", "").forGetter(MusicScore::instrumentId),
            Codec.BOOL.optionalFieldOf("convert_dynamics", true).forGetter(MusicScore::convertDynamics),
            Codec.BOOL.optionalFieldOf("convert_glissando", true).forGetter(MusicScore::convertGlissando)
    ).apply(instance, MusicScore::new));

    public MusicScore(int ppq, boolean percussion, String sourceName,
                            long lengthTick, float tuningHz,
                            List<TempoChange> tempos,
                            List<TimeSignature> timeSignatures,
                            List<MidiNote> notes) {
        this(ppq, percussion, sourceName, lengthTick, tuningHz,
                tempos, timeSignatures, List.of(), notes, AutomationData.EMPTY,
                List.of(), "", false, "", true, true);
    }

    public MusicScore(int ppq, boolean percussion, String sourceName,
                            long lengthTick, float tuningHz,
                            List<TempoChange> tempos,
                            List<TimeSignature> timeSignatures,
                            List<ExpressionEvent> expressions,
                            List<MidiNote> notes) {
        this(ppq, percussion, sourceName, lengthTick, tuningHz, tempos, timeSignatures,
                expressions, notes, AutomationData.EMPTY, List.of(), "", false, "", true, true);
    }

    public MusicScore(int ppq, boolean percussion, String sourceName,
                            long lengthTick, float tuningHz,
                            List<TempoChange> tempos, List<TimeSignature> timeSignatures,
                            List<ExpressionEvent> expressions, List<MidiNote> notes,
                            List<TrackInfo> tracks, String author, boolean signed,
                            String instrumentId, boolean convertDynamics,
                            boolean convertGlissando) {
        this(ppq, percussion, sourceName, lengthTick, tuningHz, tempos, timeSignatures,
                expressions, notes, AutomationData.EMPTY, tracks, author, signed,
                instrumentId, convertDynamics, convertGlissando);
    }

    public MusicScore(int ppq, boolean percussion, String sourceName, long lengthTick, float tuningHz, List<TempoChange> tempos, List<TimeSignature> timeSignatures, List<ExpressionEvent> expressions, List<MidiNote> notes, AutomationData automation, List<TrackInfo> tracks, String author, boolean signed, String instrumentId, boolean convertDynamics, boolean convertGlissando) {
        if (ppq < 1 || ppq > 0x7FFF) throw new IllegalArgumentException("PPQ is outside 1-32767");
        sourceName = sourceName == null ? "" : sourceName.strip();
        if (sourceName.length() > 128) sourceName = sourceName.substring(0, 128);
        if (lengthTick < 1 || lengthTick > MAX_TICKS) throw new IllegalArgumentException("invalid MIDI length");
        if (!Float.isFinite(tuningHz) || tuningHz < 100.0F || tuningHz > 1_000.0F) {
            throw new IllegalArgumentException("invalid MIDI tuning");
        }
        tempos = sortedTempos(tempos);
        timeSignatures = sortedTimeSignatures(timeSignatures);
        expressions = sortedExpressions(expressions, lengthTick);
        notes = sortedNotes(notes);
        automation = normalizedAutomation(automation, lengthTick);
        tracks = normalizedTracks(tracks, notes, expressions, percussion, sourceName);
        author = clean(author, 96);
        instrumentId = clean(instrumentId, 128);
        if (notes.size() > MAX_NOTES) throw new IllegalArgumentException("invalid MIDI note count");
        if (tempos.isEmpty() || tempos.size() > MAX_TEMPO_EVENTS) throw new IllegalArgumentException("invalid tempo map");
        if (timeSignatures.size() > MAX_TIME_SIGNATURES) throw new IllegalArgumentException("invalid time-signature map");
        if (expressions.size() > MAX_EXPRESSION_EVENTS) throw new IllegalArgumentException("invalid expression event count");
        if (notes.stream().anyMatch(note -> note.endTick() > lengthTick)) throw new IllegalArgumentException("note exceeds MIDI length");
        int maximumTrack = tracks.getLast().id();
        for (MidiNote note : notes) if (note.track() > maximumTrack) throw new IllegalArgumentException("note has no track");
        for (ExpressionEvent event : expressions) if (event.track() > maximumTrack) throw new IllegalArgumentException("expression has no track");
        for (VolumeRamp ramp : automation.volumeRamps()) if (ramp.track() > maximumTrack) throw new IllegalArgumentException("volume ramp has no track");
        for (Glissando glissando : automation.glissandos()) if (glissando.track() > maximumTrack) throw new IllegalArgumentException("glissando has no track");
        this.ppq = ppq;
        this.percussion = percussion;
        this.sourceName = sourceName;
        this.lengthTick = lengthTick;
        this.tuningHz = tuningHz;
        this.tempos = tempos;
        this.timeSignatures = timeSignatures;
        this.expressions = expressions;
        this.notes = notes;
        this.automation = automation;
        this.tracks = tracks;
        this.author = author;
        this.signed = signed;
        this.instrumentId = instrumentId;
        this.convertDynamics = convertDynamics;
        this.convertGlissando = convertGlissando;
        this.tempoMap = new TempoMap(ppq, tempos);
    }

    private static AutomationData normalizedAutomation(AutomationData source, long lengthTick) {
        AutomationData data = source == null ? AutomationData.EMPTY : source;
        for (VolumeRamp ramp : data.volumeRamps()) {
            if (ramp.startTick() < 0 || ramp.endTick() <= ramp.startTick() || ramp.endTick() > lengthTick) {
                throw new IllegalArgumentException("invalid volume ramp");
            }
        }
        for (Glissando glissando : data.glissandos()) {
            if (glissando.noteStartTick() < 0 || glissando.noteStartTick() > lengthTick) {
                throw new IllegalArgumentException("invalid glissando");
            }
        }
        return data;
    }

    private static String clean(String value, int maximum) {
        String result = value == null ? "" : value.strip();
        return result.substring(0, Math.min(maximum, result.length()));
    }

    private static List<TrackInfo> normalizedTracks(List<TrackInfo> source, List<MidiNote> notes,
                                                     List<ExpressionEvent> expressions,
                                                     boolean percussion, String name) {
        ArrayList<TrackInfo> result = new ArrayList<>(source == null ? List.of() : source);
        if (result.isEmpty()) {
            int maximum = 0;
            for (MidiNote note : notes) maximum = Math.max(maximum, note.track());
            for (ExpressionEvent event : expressions) maximum = Math.max(maximum, event.track());
            for (int id = 0; id <= maximum; id++) result.add(new TrackInfo(id, id == 0 ? name : "Track " + (id + 1), percussion, 0, List.of()));
        }
        if (result.size() > MAX_TRACKS) throw new IllegalArgumentException("too many tracks");
        result.sort(Comparator.comparingInt(TrackInfo::id));
        for (int index = 0; index < result.size(); index++) {
            if (result.get(index).id() != index) throw new IllegalArgumentException("track ids must be contiguous");
        }
        return List.copyOf(result);
    }

    private static List<TempoChange> sortedTempos(List<TempoChange> source) {
        if (source == null) throw new IllegalArgumentException("missing tempo map");
        ArrayList<TempoChange> result = new ArrayList<>(source);
        result.sort(Comparator.comparingLong(TempoChange::tick));
        if (result.isEmpty() || result.getFirst().tick() != 0) result.addFirst(new TempoChange(0, 500_000));
        long previous = -1;
        for (TempoChange tempo : result) {
            if (tempo.tick() < 0 || tempo.tick() > MAX_TICKS || tempo.tick() == previous
                    || tempo.microsPerQuarter() < 1 || tempo.microsPerQuarter() > 60_000_000) {
                throw new IllegalArgumentException("invalid or duplicate tempo event");
            }
            previous = tempo.tick();
        }
        return List.copyOf(result);
    }

    private static List<TimeSignature> sortedTimeSignatures(List<TimeSignature> source) {
        ArrayList<TimeSignature> result = new ArrayList<>(source == null ? List.of() : source);
        result.sort(Comparator.comparingLong(TimeSignature::tick));
        long previous = -1;
        for (TimeSignature signature : result) {
            if (signature.tick() < 0 || signature.tick() > MAX_TICKS || signature.tick() == previous
                    || signature.numerator() < 1 || signature.numerator() > 32
                    || signature.denominator() < 1 || signature.denominator() > 64
                    || Integer.bitCount(signature.denominator()) != 1) {
                throw new IllegalArgumentException("invalid or duplicate time signature");
            }
            previous = signature.tick();
        }
        return List.copyOf(result);
    }

    private static List<MidiNote> sortedNotes(List<MidiNote> source) {
        if (source == null) throw new IllegalArgumentException("missing notes");
        ArrayList<MidiNote> result = new ArrayList<>(source);
        result.sort(Comparator.comparingLong(MidiNote::startTick).thenComparingInt(MidiNote::track)
                .thenComparingInt(MidiNote::pitch)
                .thenComparingLong(MidiNote::endTick));
        for (MidiNote note : result) {
            if (note.track() < 0 || note.pitch() < MIN_PITCH || note.pitch() > MAX_PITCH || note.velocity() < 1 || note.velocity() > 127
                    || note.startTick() < 0 || note.keyReleaseTick() <= note.startTick()
                    || note.endTick() < note.keyReleaseTick() || note.endTick() > MAX_TICKS) {
                throw new IllegalArgumentException("invalid MIDI note");
            }
        }
        return List.copyOf(result);
    }

    private static List<ExpressionEvent> sortedExpressions(List<ExpressionEvent> source,
                                                            long lengthTick) {
        ArrayList<ExpressionEvent> result = new ArrayList<>(source == null ? List.of() : source);
        result.sort(Comparator.comparingLong(ExpressionEvent::tick).thenComparingInt(ExpressionEvent::track));
        long previousTick = -1L;
        int previousTrack = -1;
        for (ExpressionEvent event : result) {
            if (event.track() < 0 || event.tick() < 0 || event.tick() > lengthTick
                    || (event.tick() == previousTick && event.track() == previousTrack)
                    || !Float.isFinite(event.bendSemitones())
                    || event.bendSemitones() < -96.0F || event.bendSemitones() > 96.0F
                    || event.modulation() < 0 || event.modulation() > 127
                    || event.channelVolume() < 0 || event.channelVolume() > 127
                    || event.expression() < 0 || event.expression() > 127) {
                throw new IllegalArgumentException("invalid or duplicate expression event");
            }
            previousTick = event.tick();
            previousTrack = event.track();
        }
        return List.copyOf(result);
    }

    public int sustainedNoteCount() {
        int count = 0;
        for (MidiNote note : notes) if (note.sustained()) count++;
        return count;
    }

    public MusicScore transposeOctaves(int octaves) {
        if (octaves == 0) return this;
        if (octaves < -8 || octaves > 8) throw new IllegalArgumentException("octave shift is outside -8 to +8");
        int semitones = Math.multiplyExact(octaves, 12);
        ArrayList<MidiNote> shifted = new ArrayList<>(notes.size());
        for (MidiNote note : notes) {
            int pitch = percussionTrack(note.track()) ? note.pitch() : note.pitch() + semitones;
            if (pitch < 21 || pitch > 116) {
                throw new IllegalArgumentException("octave shift would move " + note.pitch()
                        + " outside Music Maker's C0-A#7 range");
            }
            shifted.add(new MidiNote(note.track(), pitch, note.velocity(), note.startTick(), note.keyReleaseTick(), note.endTick()));
        }
        ArrayList<VolumeRamp> shiftedRamps = new ArrayList<>(automation.volumeRamps().size());
        for (VolumeRamp ramp : automation.volumeRamps()) {
            int shift = percussionTrack(ramp.track()) ? 0 : semitones;
            int low = ramp.minPitch() + shift;
            int high = ramp.maxPitch() + shift;
            if (low < 21 || high > 116) throw new IllegalArgumentException("octave shift would move automation outside Music Maker's range");
            shiftedRamps.add(new VolumeRamp(ramp.track(), ramp.startTick(), ramp.endTick(),
                    ramp.startVelocity(), ramp.endVelocity(), low, high));
        }
        ArrayList<Glissando> shiftedGlissandos = new ArrayList<>(automation.glissandos().size());
        for (Glissando glissando : automation.glissandos()) {
            int pitch = glissando.pitch()
                    + (percussionTrack(glissando.track()) ? 0 : semitones);
            if (pitch < 21 || pitch > 116) throw new IllegalArgumentException("octave shift would move automation outside Music Maker's range");
            shiftedGlissandos.add(new Glissando(glissando.track(), glissando.noteStartTick(),
                    pitch, glissando.points()));
        }
        return new MusicScore(ppq, percussion, sourceName, lengthTick, tuningHz,
                tempos, timeSignatures, expressions, shifted,
                new AutomationData(shiftedRamps, shiftedGlissandos, automation.baseTempoBpm()), tracks, author, signed,
                instrumentId, convertDynamics, convertGlissando);
    }

    public MusicScore renamed(String name) {
        return new MusicScore(ppq, percussion, name, lengthTick, tuningHz,
                tempos, timeSignatures, expressions, notes, automation, tracks, author, signed,
                instrumentId, convertDynamics, convertGlissando);
    }

    public MusicScore withTuning(float hz) {
        return new MusicScore(ppq, percussion, sourceName, lengthTick, hz,
                tempos, timeSignatures, expressions, notes, automation, tracks, author, signed,
                instrumentId, convertDynamics, convertGlissando);
    }

    public MusicScore withTimeSignature(int numerator, int denominator) {
        return new MusicScore(ppq, percussion, sourceName, lengthTick, tuningHz,
                tempos, List.of(new TimeSignature(0L, numerator, denominator)),
                expressions, notes, automation, tracks, author, signed,
                instrumentId, convertDynamics, convertGlissando);
    }

    public MusicScore extendedTo(long tick) {
        long extended = Math.max(lengthTick, tick);
        if (extended == lengthTick) return this;
        return new MusicScore(ppq, percussion, sourceName, extended, tuningHz,
                tempos, timeSignatures, expressions, notes, automation, tracks, author, signed,
                instrumentId, convertDynamics, convertGlissando);
    }

    public MusicScore withInitialBpm(int bpm) {
        if (bpm < 1 || bpm > 3_000) throw new IllegalArgumentException("invalid MIDI BPM");
        int oldBpm = initialBpm();
        if (oldBpm == bpm) return this;

        ArrayList<TempoChange> scaled = new ArrayList<>(tempos.size());
        for (TempoChange tempo : tempos) {
            long numerator = (long) tempo.microsPerQuarter() * oldBpm;
            int micros = (int) Math.clamp(
                    (numerator + bpm / 2L) / bpm,
                    1L, 60_000_000L);
            scaled.add(new TempoChange(tempo.tick(), micros));
        }
        return new MusicScore(ppq, percussion, sourceName, lengthTick, tuningHz,
                scaled, timeSignatures, expressions, notes,
                new AutomationData(automation.volumeRamps(), automation.glissandos(), bpm),
                tracks, author, signed,
                instrumentId, convertDynamics, convertGlissando);
    }

    public MusicScore transposeTracks(List<Integer> octaves) {
        if (octaves == null || octaves.size() != tracks.size()) {
            throw new IllegalArgumentException("track octave count does not match song tracks");
        }
        if (octaves.stream().allMatch(value -> value == 0)) return this;
        ArrayList<MidiNote> shifted = new ArrayList<>(notes.size());
        for (MidiNote note : notes) {
            int octave = octaves.get(note.track());
            if (octave < -8 || octave > 8) throw new IllegalArgumentException("invalid track octave");
            int pitch = percussionTrack(note.track()) ? note.pitch() : note.pitch() + octave * 12;
            if (pitch < 21 || pitch > 116) {
                throw new IllegalArgumentException("octave shift would move " + note.pitch()
                        + " outside Music Maker's C0-A#7 range");
            }
            shifted.add(new MidiNote(note.track(), pitch, note.velocity(), note.startTick(),
                    note.keyReleaseTick(), note.endTick()));
        }
        ArrayList<VolumeRamp> ramps = new ArrayList<>(automation.volumeRamps().size());
        for (VolumeRamp ramp : automation.volumeRamps()) {
            int shift = percussionTrack(ramp.track()) ? 0 : octaves.get(ramp.track()) * 12;
            int low = ramp.minPitch() + shift;
            int high = ramp.maxPitch() + shift;
            if (low < 21 || high > 116) {
                throw new IllegalArgumentException("octave shift would move automation outside Music Maker's range");
            }
            ramps.add(new VolumeRamp(ramp.track(), ramp.startTick(), ramp.endTick(),
                    ramp.startVelocity(), ramp.endVelocity(), low, high));
        }
        ArrayList<Glissando> glissandos = new ArrayList<>(automation.glissandos().size());
        for (Glissando glissando : automation.glissandos()) {
            int pitch = glissando.pitch()
                    + (percussionTrack(glissando.track()) ? 0 : octaves.get(glissando.track()) * 12);
            if (pitch < 21 || pitch > 116) {
                throw new IllegalArgumentException("octave shift would move automation outside Music Maker's range");
            }
            glissandos.add(new Glissando(glissando.track(), glissando.noteStartTick(),
                    pitch, glissando.points()));
        }
        ArrayList<TrackInfo> shiftedTracks = new ArrayList<>(tracks.size());
        for (TrackInfo track : tracks) {
            shiftedTracks.add(new TrackInfo(track.id(), track.name(), track.percussion(),
                    octaves.get(track.id()), track.drumMap()));
        }
        return new MusicScore(ppq, percussion, sourceName, lengthTick, tuningHz,
                tempos, timeSignatures, expressions, shifted,
                new AutomationData(ramps, glissandos, automation.baseTempoBpm()), shiftedTracks,
                author, signed, instrumentId, convertDynamics, convertGlissando);
    }

    private boolean percussionTrack(int track) {
        return track >= 0 && track < tracks.size() && tracks.get(track).percussion();
    }

    public MusicScore withMetadata(String nextAuthor, boolean nextSigned, String nextInstrumentId) {
        return new MusicScore(ppq, percussion, sourceName, lengthTick, tuningHz,
                tempos, timeSignatures, expressions, notes, automation, tracks, nextAuthor, nextSigned,
                nextInstrumentId, convertDynamics, convertGlissando);
    }

    public MusicScore withConversion(boolean dynamics, boolean glissando) {
        return new MusicScore(ppq, percussion, sourceName, lengthTick, tuningHz,
                tempos, timeSignatures, expressions, notes, automation, tracks, author, signed,
                instrumentId, dynamics, glissando);
    }

    public MusicScore withDrumMap(List<Integer> requestedMap) {
        if (!percussion || requestedMap == null || requestedMap.isEmpty()) return this;
        List<Integer> map = m3e.session.DrumNoteMap.immutableList(
                m3e.session.DrumNoteMap.validatedArray(requestedMap));
        ArrayList<TrackInfo> mappedTracks = new ArrayList<>(tracks.size());
        for (TrackInfo track : tracks) {
            mappedTracks.add(track.percussion()
                    ? new TrackInfo(track.id(), track.name(), true, track.octave(), map)
                    : track);
        }
        if (mappedTracks.stream().noneMatch(TrackInfo::percussion)) {
            TrackInfo first = mappedTracks.getFirst();
            mappedTracks.set(0, new TrackInfo(first.id(), first.name(), true, first.octave(), map));
        }
        return new MusicScore(ppq, true, sourceName, lengthTick, tuningHz,
                tempos, timeSignatures, expressions, notes, automation, mappedTracks,
                author, signed, instrumentId, convertDynamics, convertGlissando);
    }

    public int initialBpm() {
        return automation.baseTempoBpm() > 0 ? automation.baseTempoBpm()
                : mapInitialBpm();
    }

    private int mapInitialBpm() {
        return Math.max(1, (int) Math.round(60_000_000.0 / tempos.getFirst().microsPerQuarter()));
    }

    public TempoMap tempoMap() {
        return tempoMap;
    }

    public int tempoBpmAtMicros(long micros) {
        return Math.max(1, (int) Math.round(tempoMap().bpmAtMicros(micros)
                * (initialBpm() / (double) mapInitialBpm())));
    }

    public Projection editorView() {
        return MusicMakerCompatibilityProjection.project(
                this, MusicMakerCompatibilityProjection.bestBps(this));
    }

    public Projection compatibilityExportView() {
        return compatibilityExportView(MusicMakerCompatibilityProjection.bestBps(this));
    }

    public Projection compatibilityExportView(int bps) {
        if (bps < 1 || bps > 50) throw new IllegalArgumentException("invalid Music Maker BPS");
        return MusicMakerCompatibilityProjection.project(this, bps);
    }

    public MusicScore withEditorProjection(List<NoteEvent> editedNotes,
                                                  List<VolumeMarker> editedMarkers,
                                                  int bps, int projectedLength) {
        return MusicMakerCompatibilityProjection.applyEdits(
                this, editedNotes, editedMarkers, bps, projectedLength);
    }

    public record TempoChange(long tick, int microsPerQuarter) { }
    public record TimeSignature(long tick, int numerator, int denominator) { }
    public record ExpressionEvent(int track, long tick, float bendSemitones, int modulation,
                                  int channelVolume, int expression, boolean sustain) {
        public ExpressionEvent(long tick, float bendSemitones, int modulation) {
            this(0, tick, bendSemitones, modulation, 127, 127, false);
        }
        public float gain() { return channelVolume / 127.0F * expression / 127.0F; }
    }

    public int outputPitch(MidiNote note) {
        if (note.track() < 0 || note.track() >= tracks.size()) return note.pitch();
        TrackInfo track = tracks.get(note.track());
        if (!track.percussion() || track.drumMap().isEmpty()) return note.pitch();
        int index = m3e.session.DrumNoteMap.index(note.pitch());
        return index < 0 ? -1 : track.drumMap().get(index);
    }
    public record MidiNote(int track, int pitch, int velocity, long startTick, long keyReleaseTick, long endTick) {
        public MidiNote(int pitch, int velocity, long startTick, long keyReleaseTick, long endTick) {
            this(0, pitch, velocity, startTick, keyReleaseTick, endTick);
        }
        public boolean sustained() { return endTick > keyReleaseTick; }
    }
    public record TrackInfo(int id, String name, boolean percussion, int octave, List<Integer> drumMap) {
        public TrackInfo {
            if (id < 0 || id > 255 || octave < -8 || octave > 8) throw new IllegalArgumentException("invalid track");
            name = clean(name, 96);
            drumMap = drumMap == null ? List.of() : List.copyOf(drumMap);
            if (!drumMap.isEmpty() && drumMap.size() != m3e.session.DrumNoteMap.SIZE) {
                throw new IllegalArgumentException("invalid drum map snapshot");
            }
        }
    }
    public record VolumeRamp(int track, long startTick, long endTick, int startVelocity,
                             int endVelocity, int minPitch, int maxPitch) {
        public VolumeRamp {
            if (track < 0 || startVelocity < 1 || startVelocity > 127
                    || endVelocity < 1 || endVelocity > 127 || minPitch < 21
                    || maxPitch > 116 || minPitch > maxPitch) {
                throw new IllegalArgumentException("invalid volume ramp");
            }
        }
    }
    public record GlissandoPoint(int positionPercent, int semitones) {
        public GlissandoPoint {
            if (positionPercent < 1 || positionPercent > 100 || semitones < -95 || semitones > 95) {
                throw new IllegalArgumentException("invalid glissando point");
            }
        }
    }
    public record Glissando(int track, long noteStartTick, int pitch, List<GlissandoPoint> points) {
        public Glissando {
            if (track < 0 || pitch < 21 || pitch > 116) throw new IllegalArgumentException("invalid glissando");
            points = points == null ? List.of() : List.copyOf(points);
            if (points.isEmpty() || points.size() > 8) throw new IllegalArgumentException("invalid glissando points");
            int previous = 0;
            for (GlissandoPoint point : points) {
                if (point.positionPercent() <= previous) throw new IllegalArgumentException("unordered glissando points");
                previous = point.positionPercent();
            }
        }
    }
    public record AutomationData(List<VolumeRamp> volumeRamps, List<Glissando> glissandos,
                                 int baseTempoBpm) {
        public static final AutomationData EMPTY = new AutomationData(List.of(), List.of(), 0);
        public AutomationData(List<VolumeRamp> volumeRamps, List<Glissando> glissandos) {
            this(volumeRamps, glissandos, 0);
        }
        public AutomationData {
            volumeRamps = volumeRamps == null ? List.of() : List.copyOf(volumeRamps);
            glissandos = glissandos == null ? List.of() : List.copyOf(glissandos);
            if (baseTempoBpm < 0 || baseTempoBpm > 3_000) throw new IllegalArgumentException("invalid base tempo");
        }
    }
    public record Projection(List<NoteEvent> notes, List<VolumeMarker> volumeMarkers,
                             int bps, int length) { }


    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MusicScore score)) return false;
        return ppq == score.ppq
                && percussion == score.percussion
                && sourceName.equals(score.sourceName)
                && lengthTick == score.lengthTick
                && Float.compare(tuningHz, score.tuningHz) == 0
                && tempos.equals(score.tempos)
                && timeSignatures.equals(score.timeSignatures)
                && expressions.equals(score.expressions)
                && notes.equals(score.notes)
                && automation.equals(score.automation)
                && tracks.equals(score.tracks)
                && author.equals(score.author)
                && signed == score.signed
                && instrumentId.equals(score.instrumentId)
                && convertDynamics == score.convertDynamics
                && convertGlissando == score.convertGlissando;
    }
    @Override public int hashCode() {
        return java.util.Objects.hash(ppq, percussion, sourceName, lengthTick, tuningHz, tempos, timeSignatures, expressions, notes, automation, tracks, author, signed, instrumentId, convertDynamics, convertGlissando);
    }

    public static final class TempoMap {
        private final int ppq;
        private final long[] ticks;
        private final int[] rates;
        private final long[] segmentMicros;

        private TempoMap(int ppq, List<TempoChange> tempos) {
            this.ppq = ppq;
            ticks = new long[tempos.size()];
            rates = new int[tempos.size()];
            segmentMicros = new long[tempos.size()];
            for (int index = 0; index < tempos.size(); index++) {
                TempoChange tempo = tempos.get(index);
                ticks[index] = tempo.tick();
                rates[index] = tempo.microsPerQuarter();
                if (index > 0) {
                    segmentMicros[index] = segmentMicros[index - 1]
                            + multiplyDivide(ticks[index] - ticks[index - 1], rates[index - 1], ppq);
                }
            }
        }

        public long microsAt(long tick) {
            int low = 0, high = ticks.length - 1;
            while (low < high) {
                int mid = (low + high + 1) >>> 1;
                if (ticks[mid] <= tick) low = mid;
                else high = mid - 1;
            }
            return segmentMicros[low] + multiplyDivide(tick - ticks[low], rates[low], ppq);
        }

        public long tickAtMicros(long micros, long maximumTick) {
            long low = 0L;
            long high = Math.max(0L, maximumTick);
            while (low < high) {
                long mid = (low + high + 1L) >>> 1;
                if (microsAt(mid) <= micros) low = mid;
                else high = mid - 1L;
            }
            return low;
        }

        public int bpmAtMicros(long micros) {
            long bounded = Math.max(0L, micros);
            int low = 0, high = segmentMicros.length - 1;
            while (low < high) {
                int mid = (low + high + 1) >>> 1;
                if (segmentMicros[mid] <= bounded) low = mid;
                else high = mid - 1;
            }
            return Math.max(1, (int) Math.round(60_000_000.0D / rates[low]));
        }

        private static long multiplyDivide(long value, int multiplier, int divisor) {
            long whole = value / divisor;
            long remainder = value % divisor;
            return Math.addExact(Math.multiplyExact(whole, multiplier), remainder * multiplier / divisor);
        }
    }
}
