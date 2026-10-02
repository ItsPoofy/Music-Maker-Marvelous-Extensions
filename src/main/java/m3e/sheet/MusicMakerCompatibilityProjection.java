package m3e.sheet;

import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MusicMakerCompatibilityProjection {
    private static final int DYNAMICS_THRESHOLD = 12;

    private MusicMakerCompatibilityProjection() { }

    static int bestBps(MusicScore song) {
        if (song.notes().isEmpty()) return 4;
        MusicScore.TempoMap tempo = song.tempoMap();
        double bestScore = Double.POSITIVE_INFINITY;
        int best = 1;
        for (int bps = 1; bps <= 50; bps++) {
            long error = 0L;
            int merges = 0;
            Map<Long, Boolean> onsets = new HashMap<>();
            for (MusicScore.MidiNote note : song.notes()) {
                long micros = tempo.microsAt(note.startTick());
                long beat = Math.round(micros * bps / 1_000_000.0D);
                error += Math.abs(micros - Math.round(beat * 1_000_000.0D / bps));
                long key = (beat << 8) | Math.max(0, song.outputPitch(note));
                if (onsets.put(key, Boolean.TRUE) != null) merges++;
            }
            double averageError = error / (double) song.notes().size();
            double score = averageError + merges * 250_000.0D + bps * 180.0D;
            if (score < bestScore) {
                bestScore = score;
                best = bps;
            }
        }
        return best;
    }

    static MusicScore.Projection project(MusicScore song, int bps) {
        MusicScore.TempoMap map = song.tempoMap();
        List<Projected> projected = projected(song, bps);
        ArrayList<NoteEvent> nativeNotes = new ArrayList<>(projected.size());
        for (Projected value : projected) {
            int length = Math.clamp(value.end() - value.start(), 1, 120);
            NoteEvent nativeNote = new NoteEvent((byte) value.pitch(),
                    (short) Math.min(31_999, value.start()),
                    (byte) MusicMakerSheetCompatibility.noteVolume(value.velocity()),
                    (byte) length);
            applyGlissando(song, value, nativeNote);
            nativeNotes.add(nativeNote);
        }
        NoteEvent.sortNotes(nativeNotes);
        NoteEvent.removeDuplicates(nativeNotes);
        ArrayList<VolumeMarker> markers = explicitVolumeRamps(song, bps);
        if (song.convertDynamics()) markers.addAll(dynamics(song, projected));
        long durationMicros = Math.max(1L, map.microsAt(song.lengthTick()));
        int length = Math.min(32_000, Math.max(1, beat(durationMicros, bps)));
        return new MusicScore.Projection(List.copyOf(nativeNotes), List.copyOf(markers), bps, length);
    }

    private static List<Projected> projected(MusicScore song, int bps) {
        MusicScore.TempoMap map = song.tempoMap();
        List<Projected> projected = new ArrayList<>(song.notes().size());
        for (MusicScore.MidiNote note : song.notes()) {
            int pitch = song.outputPitch(note);
            if (pitch < 21 || pitch > 116) continue;
            int start = beat(map.microsAt(note.startTick()), bps);
            int release = Math.max(start + 1, beat(map.microsAt(note.keyReleaseTick()), bps));
            int end = Math.max(release, beat(map.microsAt(note.endTick()), bps));
            projected.add(new Projected(note, pitch, start, end,
                    effectiveVelocity(song, note.track(), note.startTick(), note.velocity())));
        }
        projected.sort(Comparator.comparingInt(Projected::start)
                .thenComparingInt(Projected::pitch).thenComparingInt(value -> value.note().track()));
        clipSustainOverlaps(projected);
        return projected;
    }

    static MusicScore applyEdits(MusicScore song, List<NoteEvent> editedNotes,
                                       List<VolumeMarker> editedMarkers, int bps,
                                       int projectedLength) {
        if (editedNotes == null || editedNotes.isEmpty()) {
            throw new IllegalArgumentException("sheet contains no playable notes");
        }
        int boundedBps = Math.clamp(bps, 1, 50);
        MusicScore.TempoMap tempo = song.tempoMap();
        ArrayList<MusicScore.MidiNote> notes = new ArrayList<>(editedNotes.size());
        ArrayList<MusicScore.Glissando> glissandos = new ArrayList<>();
        Map<SourceKey, ArrayDeque<MusicScore.MidiNote>> sources = new HashMap<>();
        for (Projected value : projected(song, boundedBps)) {
            int visibleEnd = Math.min(value.end(), value.start() + 120);
            sources.computeIfAbsent(new SourceKey(value.pitch(), value.start(), visibleEnd),
                    ignored -> new ArrayDeque<>()).add(value.note());
        }
        long maximumTick = Math.max(song.lengthTick(), tempo.tickAtMicros(
                Math.max(1L, projectedLength) * 1_000_000L / boundedBps,
                MusicScore.MAX_TICKS));

        for (NoteEvent edited : editedNotes) {
            int startBeat = Math.max(0, edited.time);
            int endBeat = startBeat + Math.max(1, Byte.toUnsignedInt(edited.length));
            int pitch = Byte.toUnsignedInt(edited.note);
            ArrayDeque<MusicScore.MidiNote> matches = sources.get(
                    new SourceKey(pitch, startBeat, endBeat));
            MusicScore.MidiNote source = matches == null ? null : matches.pollFirst();
            int track = source == null ? nearestTrack(song, pitch, startBeat, boundedBps) : source.track();
            long startTick = source == null ? tickAtBeat(tempo, startBeat, boundedBps, maximumTick)
                    : source.startTick();
            long endTick = source == null ? tickAtBeat(tempo, endBeat, boundedBps, maximumTick)
                    : source.endTick();
            endTick = Math.max(startTick + 1L, endTick);
            int effectiveVelocity = MusicMakerSheetCompatibility.midiVelocity(
                    Byte.toUnsignedInt(edited.volume));
            int velocity = source != null && effectiveVelocity == effectiveVelocity(song, source.track(),
                    source.startTick(), source.velocity()) ? source.velocity()
                    : baseVelocity(song, track, startTick, effectiveVelocity);
            long releaseTick = source == null ? endTick : source.keyReleaseTick();
            int storedPitch = source == null ? storedPitch(song, track, pitch) : source.pitch();
            MusicScore.MidiNote note = new MusicScore.MidiNote(
                    track, storedPitch, velocity, startTick, Math.min(releaseTick, endTick), endTick);
            notes.add(note);
            addEditedGlissando(edited, note, glissandos);
        }

        long requestedLength = tickAtBeat(tempo, Math.max(1, projectedLength), boundedBps,
                MusicScore.MAX_TICKS);
        long noteLength = notes.stream().mapToLong(MusicScore.MidiNote::endTick).max().orElse(1L);
        long lengthTick = Math.max(noteLength, requestedLength);
        List<MusicScore.VolumeRamp> ramps = sameMarkers(
                project(song, boundedBps).volumeMarkers(), editedMarkers)
                ? song.automation().volumeRamps()
                : editedRamps(song, editedMarkers, boundedBps, lengthTick);
        return new MusicScore(song.ppq(), song.percussion(), song.sourceName(),
                lengthTick, song.tuningHz(), song.tempos(), song.timeSignatures(),
                song.expressions(), notes,
                new MusicScore.AutomationData(ramps, glissandos,
                        song.automation().baseTempoBpm()),
                song.tracks(), song.author(), song.signed(), song.instrumentId(),
                song.convertDynamics(), song.convertGlissando());
    }

    private static int nearestTrack(MusicScore song, int pitch, int start, int bps) {
        int track = 0;
        long distance = Long.MAX_VALUE;
        for (MusicScore.MidiNote note : song.notes()) {
            long candidate = Math.abs((long) song.outputPitch(note) - pitch) * 32_000L
                    + Math.abs((long) beat(song.tempoMap().microsAt(note.startTick()), bps) - start);
            if (candidate < distance) {
                distance = candidate;
                track = note.track();
            }
        }
        return track;
    }

    private static int storedPitch(MusicScore song, int trackId, int displayedPitch) {
        MusicScore.TrackInfo track = song.tracks().get(trackId);
        if (!track.percussion() || track.drumMap().isEmpty()) return displayedPitch;
        for (int index = 0; index < track.drumMap().size(); index++) {
            if (track.drumMap().get(index) == displayedPitch) {
                return m3e.session.DrumNoteMap.MIN_GM_NOTE + index;
            }
        }
        return displayedPitch;
    }

    private static long tickAtBeat(MusicScore.TempoMap tempo, int beat, int bps,
                                   long maximumTick) {
        return tempo.tickAtMicros(Math.max(0L, beat) * 1_000_000L / bps, maximumTick);
    }

    private static int baseVelocity(MusicScore song, int track, long tick,
                                    int effectiveVelocity) {
        float gain = expressionAt(song, track, tick).gain();
        return gain <= 0.0F ? effectiveVelocity
                : Math.clamp(Math.round(effectiveVelocity / gain), 1, 127);
    }

    private static void addEditedGlissando(NoteEvent edited,
                                            MusicScore.MidiNote note,
                                            List<MusicScore.Glissando> result) {
        byte[] waypoints = edited.getEffectiveWaypoints();
        if (waypoints == null || waypoints.length == 0) return;
        byte[] positions = edited.getEffectivePositions();
        ArrayList<MusicScore.GlissandoPoint> points = new ArrayList<>(waypoints.length);
        for (int index = 0; index < waypoints.length; index++) {
            int position = positions != null && positions.length == waypoints.length
                    ? Byte.toUnsignedInt(positions[index])
                    : Math.max(1, Math.round((index + 1) * 100.0F / waypoints.length));
            points.add(new MusicScore.GlissandoPoint(position, waypoints[index]));
        }
        result.add(new MusicScore.Glissando(note.track(), note.startTick(),
                note.pitch(), points));
    }

    private static List<MusicScore.VolumeRamp> editedRamps(MusicScore song,
                                                                 List<VolumeMarker> markers,
                                                                 int bps, long lengthTick) {
        if (markers == null || markers.isEmpty()) return List.of();
        ArrayList<MusicScore.VolumeRamp> result = new ArrayList<>(markers.size());
        for (VolumeMarker marker : markers) {
            int low = Byte.toUnsignedInt(marker.lowNote);
            int high = Byte.toUnsignedInt(marker.highNote);
            int track = nearestTrack(song, low, marker.startTime, bps);
            long start = tickAtBeat(song.tempoMap(), marker.startTime, bps, lengthTick);
            long end = Math.max(start + 1L,
                    tickAtBeat(song.tempoMap(), marker.endTime, bps, lengthTick));
            result.add(new MusicScore.VolumeRamp(track, start, end,
                    Math.clamp(Byte.toUnsignedInt(marker.startVolume), 1, 127),
                    Math.clamp(Byte.toUnsignedInt(marker.endVolume), 1, 127), low, high));
        }
        return List.copyOf(result);
    }

    private static boolean sameMarkers(List<VolumeMarker> first, List<VolumeMarker> second) {
        if (second == null || first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            VolumeMarker a = first.get(index);
            VolumeMarker b = second.get(index);
            if (a.startTime != b.startTime || a.endTime != b.endTime
                    || a.startVolume != b.startVolume || a.endVolume != b.endVolume
                    || a.lowNote != b.lowNote || a.highNote != b.highNote) return false;
        }
        return true;
    }

    private record SourceKey(int pitch, int start, int end) { }

    private static void clipSustainOverlaps(List<Projected> notes) {
        Map<Integer, Projected> previous = new HashMap<>();
        for (Projected note : notes) {
            Projected older = previous.put(note.pitch(), note);
            if (older != null && older.end() > note.start()) {
                older.end(Math.max(older.start() + 1, note.start()));
            }
        }
    }

    private static void applyGlissando(MusicScore song, Projected projected,
                                       NoteEvent nativeNote) {
        MusicScore.Glissando explicit = song.automation().glissandos().stream()
                .filter(value -> value.track() == projected.note().track()
                        && value.noteStartTick() == projected.note().startTick()
                        && value.pitch() == projected.note().pitch())
                .findFirst().orElse(null);
        if (explicit != null) {
            byte[] waypoints = new byte[explicit.points().size()];
            byte[] positions = new byte[explicit.points().size()];
            for (int index = 0; index < explicit.points().size(); index++) {
                MusicScore.GlissandoPoint point = explicit.points().get(index);
                waypoints[index] = (byte) point.semitones();
                positions[index] = (byte) point.positionPercent();
            }
            nativeNote.setGlissandoWaypoints(waypoints, positions);
            return;
        }
        if (!song.convertGlissando()) return;
        long start = projected.note().startTick();
        long end = projected.note().endTick();
        if (end <= start) return;
        ArrayList<Byte> waypoints = new ArrayList<>(8);
        ArrayList<Byte> positions = new ArrayList<>(8);
        int previous = 0;
        for (int sample = 1; sample <= 8; sample++) {
            long tick = start + Math.round((end - start) * sample / 8.0D);
            MusicScore.ExpressionEvent expression = expressionAt(song,
                    projected.note().track(), tick);
            int semitone = Math.clamp(Math.round(expression.bendSemitones()), -95, 95);
            if (semitone == previous && sample < 8) continue;
            previous = semitone;
            waypoints.add((byte) semitone);
            positions.add((byte) Math.clamp(Math.round(sample * 100.0F / 8.0F), 1, 100));
        }
        if (waypoints.stream().allMatch(value -> value == 0)) return;
        byte[] waypointArray = new byte[waypoints.size()];
        byte[] positionArray = new byte[positions.size()];
        for (int index = 0; index < waypoints.size(); index++) {
            waypointArray[index] = waypoints.get(index);
            positionArray[index] = positions.get(index);
        }
        nativeNote.setGlissandoWaypoints(waypointArray, positionArray);
    }

    private static ArrayList<VolumeMarker> explicitVolumeRamps(MusicScore song, int bps) {
        ArrayList<VolumeMarker> markers = new ArrayList<>();
        MusicScore.TempoMap tempo = song.tempoMap();
        for (MusicScore.VolumeRamp ramp : song.automation().volumeRamps()) {
            int start = beat(tempo.microsAt(ramp.startTick()), bps);
            int end = Math.max(start + 2, beat(tempo.microsAt(ramp.endTick()), bps));
            if (start > 31_999 || end > 31_999) continue;
            markers.add(new VolumeMarker((short) start, (short) end,
                    (byte) ramp.startVelocity(), (byte) ramp.endVelocity(),
                    (byte) ramp.minPitch(), (byte) ramp.maxPitch()));
        }
        return markers;
    }

    private static List<VolumeMarker> dynamics(MusicScore song, List<Projected> notes) {
        ArrayList<VolumeMarker> markers = new ArrayList<>();
        for (MusicScore.TrackInfo track : song.tracks()) {
            List<Projected> trackNotes = notes.stream()
                    .filter(note -> note.note().track() == track.id())
                    .sorted(Comparator.comparingInt(Projected::start)).toList();
            int runStart = 0;
            int direction = 0;
            for (int index = 1; index <= trackNotes.size(); index++) {
                int nextDirection = index == trackNotes.size() ? 0
                        : Integer.compare(trackNotes.get(index).velocity(),
                        trackNotes.get(index - 1).velocity());
                if (direction == 0) direction = nextDirection;
                if (nextDirection == direction && direction != 0) continue;
                addRunMarker(trackNotes, runStart, index, markers);
                runStart = Math.max(0, index - 1);
                direction = nextDirection;
            }
            addHeldAutomationMarkers(song, track.id(), trackNotes, markers);
        }
        return List.copyOf(markers);
    }

    private static void addRunMarker(List<Projected> notes, int from, int to,
                                     List<VolumeMarker> markers) {
        if (to - from < 4) return;
        Projected first = notes.get(from);
        Projected last = notes.get(to - 1);
        if (Math.abs(last.velocity() - first.velocity()) < DYNAMICS_THRESHOLD) return;
        int low = 127;
        int high = 0;
        for (int index = from; index < to; index++) {
            low = Math.min(low, notes.get(index).pitch());
            high = Math.max(high, notes.get(index).pitch());
        }
        int end = Math.max(first.start() + 2, last.end());
        if (end > 31_999) return;
        markers.add(new VolumeMarker((short) first.start(), (short) end,
                (byte) first.velocity(), (byte) last.velocity(), (byte) low, (byte) high));
    }

    private static void addHeldAutomationMarkers(MusicScore song, int track,
                                                  List<Projected> notes,
                                                  List<VolumeMarker> markers) {
        for (Projected note : notes) {
            boolean changesInside = song.expressions().stream().anyMatch(event ->
                    event.track() == track
                            && event.tick() > note.note().startTick()
                            && event.tick() <= note.note().endTick());
            if (!changesInside) continue;
            int start = Math.round(note.note().velocity()
                    * expressionAt(song, track, note.note().startTick()).gain());
            int end = Math.round(note.note().velocity()
                    * expressionAt(song, track, note.note().endTick()).gain());
            if (Math.abs(end - start) < DYNAMICS_THRESHOLD || note.end() - note.start() < 2) continue;
            markers.add(new VolumeMarker((short) note.start(), (short) note.end(),
                    (byte) Math.clamp(start, 1, 127), (byte) Math.clamp(end, 1, 127),
                    (byte) note.pitch(), (byte) note.pitch()));
        }
    }

    private static int effectiveVelocity(MusicScore song, int track, long tick,
                                         int velocity) {
        return Math.clamp(Math.round(velocity * expressionAt(song, track, tick).gain()), 1, 127);
    }

    private static MusicScore.ExpressionEvent expressionAt(MusicScore song,
                                                                  int track, long tick) {
        MusicScore.ExpressionEvent result = new MusicScore.ExpressionEvent(
                track, 0L, 0.0F, 0, 127, 127, false);
        for (MusicScore.ExpressionEvent event : song.expressions()) {
            if (event.tick() > tick) break;
            if (event.track() == track) result = event;
        }
        return result;
    }

    private static int beat(long micros, int bps) {
        return (int) Math.min(32_000, Math.max(0, Math.round(micros * bps / 1_000_000.0D)));
    }

    private static final class Projected {
        private final MusicScore.MidiNote note;
        private final int pitch;
        private final int start;
        private int end;
        private final int velocity;

        private Projected(MusicScore.MidiNote note, int pitch, int start,
                          int end, int velocity) {
            this.note = note;
            this.pitch = pitch;
            this.start = start;
            this.end = end;
            this.velocity = velocity;
        }

        private MusicScore.MidiNote note() { return note; }
        private int pitch() { return pitch; }
        private int start() { return start; }
        private int end() { return end; }
        private void end(int value) { end = value; }
        private int velocity() { return velocity; }
    }
}
