package m3e.client;

import m3e.sheet.MusicScore;
import m3e.session.DrumNoteMap;
import m3e.debug.MusicDebugTelemetry;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiFileFormat;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Client-only Standard MIDI File inspection used by the drag-and-drop screen. */
final class MidiSheetFile {
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_RAW_EVENTS = 500_000;

    private MidiSheetFile() { }

    static Analysis read(Path file) throws IOException, InvalidMidiDataException {
        long size = Files.size(file);
        if (size < 1 || size > MAX_FILE_BYTES) throw new IOException("MIDI file must be between 1 byte and 16 MiB");
        MidiFileFormat format = MidiSystem.getMidiFileFormat(file.toFile());
        if (format.getType() != 0 && format.getType() != 1) {
            throw new InvalidMidiDataException("Only Standard MIDI type 0 and type 1 files are supported");
        }
        Sequence sequence = MidiSystem.getSequence(file.toFile());
        if (sequence.getDivisionType() != Sequence.PPQ) {
            throw new InvalidMidiDataException("SMPTE MIDI timing is not supported; export the file using PPQ timing");
        }
        int ppq = sequence.getResolution();
        if (ppq < 1 || ppq > 0x7FFF) throw new InvalidMidiDataException("Invalid MIDI PPQ resolution");

        Track[] tracks = sequence.getTracks();
        int rawCount = 0;
        ArrayList<OrderedEvent> events = new ArrayList<>();
        String[] trackNames = new String[tracks.length];
        String[] instrumentNames = new String[tracks.length];
        for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
            Track track = tracks[trackIndex];
            rawCount += track.size();
            if (rawCount > MAX_RAW_EVENTS) throw new InvalidMidiDataException("MIDI contains too many events");
            for (int eventIndex = 0; eventIndex < track.size(); eventIndex++) {
                MidiEvent event = track.get(eventIndex);
                events.add(new OrderedEvent(event.getTick(), trackIndex, eventIndex, event.getMessage()));
                if (event.getMessage() instanceof MetaMessage meta && meta.getType() == 0x03) {
                    trackNames[trackIndex] = cleanTrackName(meta.getData(), trackIndex);
                } else if (event.getMessage() instanceof MetaMessage meta && meta.getType() == 0x04) {
                    instrumentNames[trackIndex] = cleanMetaName(meta.getData());
                }
            }
            if (trackNames[trackIndex] == null) trackNames[trackIndex] = "Unnamed " + (trackIndex + 1);
        }
        events.sort(Comparator.comparingLong(OrderedEvent::tick)
                .thenComparingInt(OrderedEvent::track).thenComparingInt(OrderedEvent::order));

        ChannelState[][] states = new ChannelState[tracks.length][16];
        for (int track = 0; track < tracks.length; track++) {
            for (int channel = 0; channel < 16; channel++) states[track][channel] = new ChannelState();
        }
        ChannelExpressionState[] expressionStates = new ChannelExpressionState[16];
        for (int channel = 0; channel < 16; channel++) {
            expressionStates[channel] = new ChannelExpressionState();
        }
        @SuppressWarnings("unchecked")
        TreeMap<Long, Integer>[] channelPrograms = new TreeMap[16];
        for (int channel = 0; channel < 16; channel++) channelPrograms[channel] = new TreeMap<>();
        for (OrderedEvent ordered : events) {
            if (ordered.message() instanceof ShortMessage shortMessage
                    && shortMessage.getCommand() == ShortMessage.PROGRAM_CHANGE) {
                channelPrograms[shortMessage.getChannel()].put(
                        ordered.tick(), shortMessage.getData1());
            }
        }
        TreeMap<Long, Integer> tempoMap = new TreeMap<>();
        TreeMap<Long, MusicScore.TimeSignature> signatures = new TreeMap<>();
        int ignoredOrphanOffs = 0;
        float globalTuningHz = 440.0F;
        int globalFineTuning = 8192;
        int globalCoarseTuning = 0;

        for (OrderedEvent ordered : events) {
            MidiMessage message = ordered.message();
            if (message instanceof SysexMessage sysex) {
                MasterTuning tuning = masterTuning(sysex);
                if (tuning != null) {
                    if (tuning.fineValue() != null) globalFineTuning = tuning.fineValue();
                    if (tuning.coarseSemitones() != null)
                        globalCoarseTuning = tuning.coarseSemitones();
                    globalTuningHz = tuningFromFineValue(globalFineTuning,
                            globalCoarseTuning);
                }
                continue;
            }
            if (message instanceof MetaMessage meta) {
                byte[] data = meta.getData();
                if (meta.getType() == 0x51) {
                    if (data.length != 3) throw new InvalidMidiDataException("Malformed tempo event");
                    int micros = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                    if (micros < 1) throw new InvalidMidiDataException("Invalid MIDI tempo");
                    tempoMap.put(ordered.tick(), micros);
                } else if (meta.getType() == 0x58) {
                    if (data.length < 2) throw new InvalidMidiDataException("Malformed time-signature event");
                    int numerator = data[0] & 0xFF;
                    int exponent = data[1] & 0xFF;
                    if (numerator < 1 || exponent > 6) throw new InvalidMidiDataException("Unsupported time signature");
                    signatures.put(ordered.tick(), new MusicScore.TimeSignature(
                            ordered.tick(), numerator, 1 << exponent));
                }
                continue;
            }
            if (!(message instanceof ShortMessage shortMessage)) continue;
            int channel = shortMessage.getChannel();
            ChannelState state = states[ordered.track()][channel];
            int command = shortMessage.getCommand();
            if (command == ShortMessage.PROGRAM_CHANGE) {
                continue;
            }
            if (command == ShortMessage.PITCH_BEND) {
                int bend14 = (shortMessage.getData2() << 7) | shortMessage.getData1();
                expressionStates[channel].pitchBend(bend14, ordered.tick());
                continue;
            }
            if (command == ShortMessage.CONTROL_CHANGE) {
                int controller = shortMessage.getData1();
                int value = shortMessage.getData2();
                expressionStates[channel].controlChange(controller, value, ordered.tick());
                // Controllers are channel messages even when a type-1 MIDI stores
                // them on a dedicated automation track. Apply them to every
                // track-local note state without combining those tracks' notes.
                for (ChannelState[] trackStates : states) {
                    ChannelState channelState = trackStates[channel];
                    channelState.controlChange(controller, value);
                    if (controller == 64) {
                        boolean down = value >= 64;
                        if (channelState.sustainDown && !down) {
                            channelState.releaseSustained(ordered.tick());
                        }
                        channelState.sustainDown = down;
                    } else if (controller == 121) {
                        if (channelState.sustainDown) {
                            channelState.releaseSustained(ordered.tick());
                        }
                        channelState.sustainDown = false;
                    }
                }
                continue;
            }
            if (command != ShortMessage.NOTE_ON && command != ShortMessage.NOTE_OFF) continue;
            int pitch = shortMessage.getData1();
            int velocity = shortMessage.getData2();
            boolean noteOn = command == ShortMessage.NOTE_ON && velocity > 0;
            if (noteOn) {
                if (pitch < 21 || pitch > 116) {
                    state.outOfRangeNotes++;
                    pitch = foldIntoMusicMakerRange(pitch);
                }
                Map.Entry<Long, Integer> programEvent = channelPrograms[channel].floorEntry(ordered.tick());
                int program = channel == 9 ? -1
                        : programEvent == null ? -1 : programEvent.getValue();
                state.active.computeIfAbsent(pitch, ignored -> new ArrayDeque<>())
                        .addLast(new NoteStart(pitch, velocity, ordered.tick(), program));
            } else {
                if (pitch < 21 || pitch > 116) pitch = foldIntoMusicMakerRange(pitch);
                Deque<NoteStart> active = state.active.get(pitch);
                if (active == null || active.isEmpty()) {
                    ignoredOrphanOffs++;
                    continue;
                }
                NoteStart start = active.removeFirst();
                if (ordered.tick() <= start.startTick()) {
                    state.zeroLengthNotes++;
                    continue;
                }
                PendingRelease release = new PendingRelease(start, ordered.tick());
                if (state.sustainDown) state.sustained.add(release);
                else state.finish(release, ordered.tick());
            }
        }

        long sequenceEnd = Math.max(1, sequence.getTickLength());
        ArrayList<ChannelSummary> channels = new ArrayList<>();
        for (int trackIndex = 0; trackIndex < states.length; trackIndex++) {
            for (int channel = 0; channel < 16; channel++) {
                ChannelState state = states[trackIndex][channel];
                state.releaseSustained(sequenceEnd);
                for (Deque<NoteStart> active : state.active.values()) {
                    while (!active.isEmpty()) {
                        NoteStart start = active.removeFirst();
                        if (sequenceEnd > start.startTick()) {
                            state.finish(new PendingRelease(start, sequenceEnd), sequenceEnd);
                        }
                    }
                }
                for (Map.Entry<Integer, List<MusicScore.MidiNote>> programEntry
                        : state.notesByProgram.entrySet()) {
                    List<MusicScore.MidiNote> programNotes = programEntry.getValue();
                    if (programNotes.isEmpty()) continue;
                    programNotes.sort(Comparator.comparingLong(MusicScore.MidiNote::startTick)
                            .thenComparingInt(MusicScore.MidiNote::pitch));
                    if (programNotes.size() > MusicScore.MAX_NOTES) {
                        throw new InvalidMidiDataException("Track " + (trackIndex + 1) + ", channel "
                                + (channel + 1) + " exceeds " + MusicScore.MAX_NOTES + " notes");
                    }
                    int low = 127, high = 0, sustained = 0;
                    long length = 1;
                    for (MusicScore.MidiNote note : programNotes) {
                        low = Math.min(low, note.pitch());
                        high = Math.max(high, note.pitch());
                        length = Math.max(length, note.endTick());
                        if (note.sustained()) sustained++;
                    }
                    if (!expressionStates[channel].expressions.isEmpty()) {
                        length = Math.max(length, expressionStates[channel].expressions.lastKey());
                    }
                    channels.add(new ChannelSummary(trackIndex, channel, trackNames[trackIndex],
                            instrumentNames[trackIndex], programEntry.getKey(), low, high,
                            programNotes.size(), sustained, state.outOfRangeNotes, state.zeroLengthNotes,
                            length, state.tuningExplicit ? state.tuningHz : globalTuningHz,
                            List.copyOf(expressionStates[channel].expressions.values()),
                            List.copyOf(programNotes)));
                }
            }
        }
        if (channels.isEmpty()) throw new InvalidMidiDataException("MIDI contains no playable notes");
        if (tempoMap.isEmpty() || tempoMap.firstKey() != 0) tempoMap.put(0L, 500_000);
        List<MusicScore.TempoChange> tempos = tempoMap.entrySet().stream()
                .map(entry -> new MusicScore.TempoChange(entry.getKey(), entry.getValue())).toList();
        List<MusicScore.TimeSignature> timeSignatures = List.copyOf(signatures.values());
        String sourceName = file.getFileName().toString();
        int noteCount = channels.stream().mapToInt(ChannelSummary::noteCount).sum();
        MusicDebugTelemetry.event("midi_import", "parsed",
                "file", sourceName, "bytes", size, "type", format.getType(),
                "tracks", tracks.length, "parts", channels.size(), "notes", noteCount,
                "ppq", ppq, "tempos", tempos.size(), "signatures", timeSignatures.size(),
                "orphan_offs", ignoredOrphanOffs, "tuning_hz", globalTuningHz);
        return new Analysis(file, sourceName, ppq, tempos, timeSignatures,
                List.copyOf(channels), ignoredOrphanOffs);
    }

    /**
     * Universal real-time/non-real-time Master Fine Tuning:
     * F0 7E/7F device 04 03 ll mm F7. The 14-bit value is centered at
     * 8192 and spans one semitone down/up.
     */
    private static MasterTuning masterTuning(SysexMessage message) {
        byte[] data = message.getData();
        if (data.length < 5 || ((data[0] & 0xFF) != 0x7E && (data[0] & 0xFF) != 0x7F)
                || (data[2] & 0xFF) != 0x04) {
            return null;
        }
        if ((data[3] & 0xFF) == 0x03 && data.length >= 6) {
            int value = ((data[5] & 0x7F) << 7) | (data[4] & 0x7F);
            return new MasterTuning(value, null);
        }
        if ((data[3] & 0xFF) == 0x04) {
            return new MasterTuning(null, (data[4] & 0x7F) - 64);
        }
        return null;
    }

    private static float tuningFromFineValue(int fineValue, int coarseSemitones) {
        double fineSemitones = (Math.clamp(fineValue, 0, 16_383) - 8192) / 8192.0D;
        return (float) (440.0D * Math.pow(2.0D,
                (coarseSemitones + fineSemitones) / 12.0D));
    }

    private static String cleanTrackName(byte[] data, int trackIndex) {
        String name = new String(data, StandardCharsets.UTF_8).strip();
        if (name.regionMatches(true, 0, "Track ", 0, 6)) {
            name = name.substring(6).strip();
        }
        return name.isEmpty() ? "Unnamed " + (trackIndex + 1)
                : name.substring(0, Math.min(64, name.length()));
    }

    private static String cleanMetaName(byte[] data) {
        String name = new String(data, StandardCharsets.UTF_8).strip();
        return name.substring(0, Math.min(96, name.length()));
    }

    private static int foldIntoMusicMakerRange(int pitch) {
        int result = pitch;
        while (result < 21) result += 12;
        while (result > 116) result -= 12;
        return result;
    }

    record Analysis(Path path, String sourceName, int ppq,
                    List<MusicScore.TempoChange> tempos,
                    List<MusicScore.TimeSignature> timeSignatures,
                    List<ChannelSummary> channels, int ignoredOrphanOffs) {
        /** Prefers a melodic part while retaining a useful fallback for drum-only files. */
        int preferredInitialPartIndex() {
            for (int index = 0; index < channels.size(); index++) {
                if (channels.get(index).channel() != 9) return index;
            }
            return 0;
        }

        MusicScore song(ChannelSummary selected) {
            if (selected == null) throw new IllegalArgumentException("MIDI track/channel part is not present");
            return song(List.of(selected), List.of());
        }

        MusicScore song(List<ChannelSummary> selectedParts) {
            return song(selectedParts, List.of());
        }

        /** Combines selected track/channel parts while retaining every note and its timing. */
        MusicScore song(List<ChannelSummary> selectedParts, List<Integer> drumMap) {
            if (selectedParts == null || selectedParts.isEmpty()) {
                throw new IllegalArgumentException("Select at least one MIDI part");
            }
            Set<ChannelSummary> unique = new LinkedHashSet<>(selectedParts);
            if (unique.size() != selectedParts.size() || !channels.containsAll(unique)) {
                throw new IllegalArgumentException("MIDI track/channel part is not present");
            }
            boolean anyDrums = selectedParts.stream().anyMatch(part -> part.channel() == 9);
            boolean anyMelodic = selectedParts.stream().anyMatch(part -> part.channel() != 9);
            if (anyDrums && anyMelodic) {
                throw new IllegalArgumentException("Drum and melodic MIDI parts cannot be combined");
            }

            int[] mappedDrums = drumMap == null || drumMap.isEmpty()
                    ? null : DrumNoteMap.validatedArray(drumMap);
            ArrayList<MusicScore.MidiNote> merged = new ArrayList<>();
            ArrayList<MusicScore.ExpressionEvent> expressions = new ArrayList<>();
            ArrayList<MusicScore.TrackInfo> tracks = new ArrayList<>();
            float tuning = selectedParts.getFirst().tuningHz();
            for (int trackId = 0; trackId < selectedParts.size(); trackId++) {
                ChannelSummary selected = selectedParts.get(trackId);
                List<Integer> trackDrumMap = selected.channel() == 9 && mappedDrums != null
                        ? DrumNoteMap.immutableList(mappedDrums) : List.of();
                tracks.add(new MusicScore.TrackInfo(trackId, selected.channelName(),
                        selected.channel() == 9, 0, trackDrumMap));
                for (MusicScore.MidiNote note : selected.notes()) {
                    merged.add(new MusicScore.MidiNote(trackId, note.pitch(), note.velocity(),
                            note.startTick(), note.keyReleaseTick(), note.endTick()));
                }
                for (MusicScore.ExpressionEvent event : selected.expressions()) {
                    expressions.add(new MusicScore.ExpressionEvent(trackId, event.tick(),
                            event.bendSemitones(), event.modulation(), event.channelVolume(),
                            event.expression(), event.sustain()));
                }
            }
            if (merged.isEmpty()) throw new IllegalArgumentException("Selected MIDI parts contain no playable notes");
            if (merged.size() > MusicScore.MAX_NOTES) {
                throw new IllegalArgumentException("Selected MIDI parts exceed "
                        + MusicScore.MAX_NOTES + " notes when combined");
            }
            merged.sort(Comparator.comparingLong(MusicScore.MidiNote::startTick)
                    .thenComparingInt(MusicScore.MidiNote::pitch));
            long length = merged.stream().mapToLong(MusicScore.MidiNote::endTick).max().orElse(1);
            for (ChannelSummary selected : selectedParts) length = Math.max(length, selected.lengthTick());
            return new MusicScore(ppq, anyDrums, sourceName, length,
                    tuning, tempos, timeSignatures,
                    expressions, merged, tracks, "", false, "", true, true);
        }

    }

    record ChannelSummary(int trackIndex, int channel, String trackName,
                          String authoredInstrumentName, int program, int lowNote, int highNote,
                          int noteCount, int sustainedCount, int outOfRangeCount, int zeroLengthCount,
                          long lengthTick, float tuningHz,
                          List<MusicScore.ExpressionEvent> expressions,
                          List<MusicScore.MidiNote> notes) {
        String displayName() {
            return (trackIndex + 1) + " · " + channelName() + " · " + instrumentName();
        }

        /** A short channel title that remains useful even for unnamed MIDI tracks. */
        String channelName() {
            String channelName = trackName == null ? "" : trackName.strip();
            if (channelName.isEmpty() || channelName.equals("Unnamed " + (trackIndex + 1))) {
                channelName = "Track " + (trackIndex + 1);
            }
            return channelName;
        }

        String midiMapInstrument() {
            if (channel == 9) return "Std. Drums";
            return program < 0 ? "Unspec." : GM_PROGRAMS[program];
        }

        String instrumentName() {
            String authored = authoredInstrumentName == null ? "" : authoredInstrumentName.strip();
            return authored.isEmpty() ? midiMapInstrument() : authored;
        }
    }

    private static final String[] GM_PROGRAMS = {
            "Aco. Gr. Pno.", "Bright Aco. Pno.", "Elec. Gr. Pno.", "Honky Pno.", "Elec. Pno. 1", "Elec. Pno. 2", "Harpsi.", "Clav.",
            "Celesta", "Glock.", "Music Box", "Vibes", "Marimba", "Xylo.", "Tub. Bells", "Dulcimer",
            "Drawbar Org.", "Perc. Org.", "Rock Org.", "Church Org.", "Reed Org.", "Accordion", "Harmonica", "Tango Acc.",
            "Nylon Gtr.", "Steel Gtr.", "Jazz Elec. Gtr.", "Clean Elec. Gtr.", "Muted Elec. Gtr.", "Overdrive Gtr.", "Distortion Gtr.", "Gtr. Harm.",
            "Aco. Bass", "Finger E. Bass", "Pick E. Bass", "Fretless Bass", "Slap Bass 1", "Slap Bass 2", "Syn. Bass 1", "Syn. Bass 2",
            "Violin", "Viola", "Cello", "Contra.", "Trem. Str.", "Pizz. Str.", "Orch. Harp", "Timpani",
            "Str. Ens. 1", "Str. Ens. 2", "Syn. Str. 1", "Syn. Str. 2", "Choir Aahs", "Voice Oohs", "Syn. Voice", "Orch. Hit",
            "Trumpet", "Trombone", "Tuba", "Muted Tpt.", "French Horn", "Brass Sect.", "Syn. Brass 1", "Syn. Brass 2",
            "Sop. Sax", "Alto Sax", "Tenor Sax", "Bari. Sax", "Oboe", "English Horn", "Bassoon", "Clarinet",
            "Piccolo", "Flute", "Recorder", "Pan Flute", "Blown Bottle", "Shakuhachi", "Whistle", "Ocarina",
            "Lead 1 Sq.", "Lead 2 Saw", "Lead 3 Call.", "Lead 4 Chiff", "Lead 5 Char.", "Lead 6 Voice", "Lead 7 Fifths", "Lead 8 Bs+Ld",
            "Pad 1 New Age", "Pad 2 Warm", "Pad 3 Poly", "Pad 4 Choir", "Pad 5 Bowed", "Pad 6 Metal", "Pad 7 Halo", "Pad 8 Sweep",
            "FX 1 Rain", "FX 2 Soundtrk.", "FX 3 Crystal", "FX 4 Atmos.", "FX 5 Bright", "FX 6 Goblins", "FX 7 Echoes", "FX 8 Sci-Fi",
            "Sitar", "Banjo", "Shamisen", "Koto", "Kalimba", "Bagpipe", "Fiddle", "Shanai",
            "Tinkle Bell", "Agogo", "Steel Drums", "Woodblock", "Taiko", "Melodic Tom", "Syn. Drum", "Rev. Cymbal",
            "Gtr. Fret Noise", "Breath Noise", "Seashore", "Bird Tweet", "Phone Ring", "Helicopter", "Applause", "Gunshot"
    };

    private record OrderedEvent(long tick, int track, int order, MidiMessage message) { }
    private record NoteStart(int pitch, int velocity, long startTick, int program) { }
    private record PendingRelease(NoteStart start, long keyReleaseTick) { }
    private record MasterTuning(Integer fineValue, Integer coarseSemitones) { }

    private static final class ChannelState {
        private final Map<Integer, Deque<NoteStart>> active = new HashMap<>();
        private final List<PendingRelease> sustained = new ArrayList<>();
        private final Map<Integer, List<MusicScore.MidiNote>> notesByProgram = new TreeMap<>();
        private boolean sustainDown;
        private int outOfRangeNotes;
        private int zeroLengthNotes;
        private int rpnMsb = 127;
        private int rpnLsb = 127;
        private int fineTuning = 8192;
        private int coarseTuning;
        private float tuningHz = 440.0F;
        private boolean tuningExplicit;

        private void controlChange(int controller, int value) {
            switch (controller) {
                case 101 -> rpnMsb = value;
                case 100 -> rpnLsb = value;
                // Selecting an NRPN invalidates the preceding RPN selection;
                // subsequent Data Entry belongs to that NRPN, not to tuning.
                case 99, 98 -> {
                    rpnMsb = 127;
                    rpnLsb = 127;
                }
                case 6 -> applyDataEntryMsb(value);
                case 38 -> applyDataEntryLsb(value);
                default -> { }
            }
        }

        private void applyDataEntryMsb(int value) {
            if (rpnMsb != 0) return;
            if (rpnLsb == 1) {
                fineTuning = (value << 7) | (fineTuning & 0x7F);
                tuningExplicit = true;
            } else if (rpnLsb == 2) {
                coarseTuning = value - 64;
                tuningExplicit = true;
            } else {
                return;
            }
            tuningHz = tuningFromFineValue(fineTuning, coarseTuning);
        }

        private void applyDataEntryLsb(int value) {
            if (rpnMsb != 0) return;
            if (rpnLsb == 1) {
                fineTuning = (fineTuning & 0x3F80) | value;
                tuningExplicit = true;
                tuningHz = tuningFromFineValue(fineTuning, coarseTuning);
            }
        }

        private void releaseSustained(long tick) {
            for (PendingRelease release : sustained) finish(release, Math.max(tick, release.keyReleaseTick()));
            sustained.clear();
        }

        private void finish(PendingRelease release, long endTick) {
            NoteStart start = release.start();
            if (endTick <= start.startTick()) return;
            notesByProgram.computeIfAbsent(start.program(), ignored -> new ArrayList<>())
                    .add(new MusicScore.MidiNote(start.pitch(), start.velocity(), start.startTick(),
                            release.keyReleaseTick(), endTick));
        }
    }

    /** One channel-wide timeline shared by independent track/channel parts. */
    private static final class ChannelExpressionState {
        private final TreeMap<Long, MusicScore.ExpressionEvent> expressions = new TreeMap<>();
        private int rpnMsb = 127;
        private int rpnLsb = 127;
        private int pitchBend14 = 8192;
        private int bendRangeSemitones = 2;
        private int bendRangeCents;
        private int modulation;
        private int channelVolume = 127;
        private int expression = 127;
        private boolean sustain;

        private void controlChange(int controller, int value, long tick) {
            switch (controller) {
                case 101 -> rpnMsb = value;
                case 100 -> rpnLsb = value;
                case 99, 98 -> {
                    rpnMsb = 127;
                    rpnLsb = 127;
                }
                case 6 -> {
                    if (rpnMsb == 0 && rpnLsb == 0) {
                        bendRangeSemitones = value;
                        recordExpression(tick);
                    }
                }
                case 38 -> {
                    if (rpnMsb == 0 && rpnLsb == 0) {
                        bendRangeCents = Math.clamp(value, 0, 99);
                        recordExpression(tick);
                    }
                }
                case 1 -> {
                    modulation = value;
                    recordExpression(tick);
                }
                case 7 -> {
                    channelVolume = value;
                    recordExpression(tick);
                }
                case 11 -> {
                    expression = value;
                    recordExpression(tick);
                }
                case 64 -> {
                    sustain = value >= 64;
                    recordExpression(tick);
                }
                case 121 -> {
                    modulation = 0;
                    channelVolume = 127;
                    expression = 127;
                    sustain = false;
                    recordExpression(tick);
                }
                default -> { }
            }
        }

        private void pitchBend(int value14, long tick) {
            pitchBend14 = Math.clamp(value14, 0, 16_383);
            recordExpression(tick);
        }

        private void recordExpression(long tick) {
            float range = bendRangeSemitones + bendRangeCents / 100.0F;
            float normalized = (pitchBend14 - 8192) / 8192.0F;
            MusicScore.ExpressionEvent event = new MusicScore.ExpressionEvent(
                    0, tick, normalized * range, modulation,
                    channelVolume, expression, sustain);
            Map.Entry<Long, MusicScore.ExpressionEvent> previous = expressions.floorEntry(tick);
            if (previous != null && previous.getKey() != tick
                    && sameExpression(previous.getValue(), event)) return;
            expressions.put(tick, event);
        }

        private static boolean sameExpression(MusicScore.ExpressionEvent left,
                                              MusicScore.ExpressionEvent right) {
            return Float.compare(left.bendSemitones(), right.bendSemitones()) == 0
                    && left.modulation() == right.modulation()
                    && left.channelVolume() == right.channelVolume()
                    && left.expression() == right.expression()
                    && left.sustain() == right.sustain();
        }

    }
}
