package m3e.client;

import m3e.sheet.MusicScore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

final class ScoreEditSession {
    record Note(long id, int track, int pitch, int velocity, long start, long release, long end) {
        long length() { return end - start; }
        MusicScore.MidiNote midi() {
            return new MusicScore.MidiNote(track, pitch, velocity, start, release, end);
        }
    }

    record ClipboardNote(UUID origin, Note note, MusicScore.Glissando glissando,
                         MusicScore.TrackInfo track) { }

    private record ClipboardTrack(UUID origin, int id) { }
    private record State(MusicScore source, List<Note> notes,
                         Map<Long, MusicScore.Glissando> glissandos,
                         Set<Long> selection, Map<ClipboardTrack, Integer> pastedTracks) { }

    private MusicScore source;
    private final UUID identity = UUID.randomUUID();
    private final Map<ClipboardTrack, Integer> pastedTracks = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Note> notes = new LinkedHashMap<>();
    private final LinkedHashSet<Long> selection = new LinkedHashSet<>();
    private final LinkedHashMap<Long, MusicScore.Glissando> glissandos = new LinkedHashMap<>();
    private final ArrayDeque<State> undo = new ArrayDeque<>();
    private final ArrayDeque<State> redo = new ArrayDeque<>();
    private long nextId;
    private boolean dirty;
    private MusicScore snapshot;
    private String error;
    private long revision;
    private long selectionRevision;
    private List<Note> noteView;
    private State editStart;
    private boolean editWasDirty;

    ScoreEditSession(MusicScore source) {
        this.source = source;
        snapshot = source;
        for (MusicScore.MidiNote note : source.notes()) {
            long id = nextId++;
            notes.put(id, new Note(id, note.track(), note.pitch(), note.velocity(),
                    note.startTick(), note.keyReleaseTick(), note.endTick()));
            for (MusicScore.Glissando glissando : source.automation().glissandos()) {
                if (glissando.track() == note.track() && glissando.noteStartTick() == note.startTick()
                        && glissando.pitch() == note.pitch()) {
                    glissandos.put(id, glissando);
                    break;
                }
            }
        }
    }

    List<Note> notes() {
        if (noteView == null) noteView = List.copyOf(notes.values());
        return noteView;
    }
    Note note(long id) { return notes.get(id); }
    int selectedCount() { return selection.size(); }
    long selectionRevision() { return selectionRevision; }
    boolean editing() { return editStart != null; }

    void beginEdit() {
        if (editStart != null) throw new IllegalStateException("an editor operation is already active");
        editStart = state();
        editWasDirty = dirty;
    }

    void commitEdit() {
        if (editStart == null) return;
        State before = editStart;
        editStart = null;
        State after = state();
        if (!before.source().equals(after.source()) || !before.notes().equals(after.notes())
                || !before.glissandos().equals(after.glissandos())) pushCheckpoint(before);
        else dirty = editWasDirty;
    }

    void cancelEdit() {
        if (editStart == null) return;
        State before = editStart;
        editStart = null;
        restore(before);
        changed();
        dirty = editWasDirty;
    }
    Set<Long> selection() { return Set.copyOf(selection); }
    boolean selected(long id) { return selection.contains(id); }
    boolean dirty() { return dirty; }
    int ppq() { return source.ppq(); }
    int initialBpm() { return source.initialBpm(); }
    String instrumentId() { return source.instrumentId(); }
    String takeError() { String result = error; error = null; return result; }
    long revision() { return revision; }
    void acknowledge(long savedRevision) { if (revision == savedRevision) dirty = false; }

    void setInitialBpm(int bpm) {
        if (bpm == source.initialBpm()) return;
        checkpoint();
        source = source.withInitialBpm(bpm);
        changed();
    }

    void rename(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.equals(source.sourceName())) return;
        checkpoint();
        source = source.renamed(clean);
        changed();
    }

    void setTuning(float hz) {
        if (!Float.isFinite(hz) || hz < 100.0F || hz > 1_000.0F
                || Float.compare(hz, source.tuningHz()) == 0) return;
        checkpoint();
        source = source.withTuning(hz);
        changed();
    }

    MusicScore.TimeSignature timeSignature() {
        return source.timeSignatures().isEmpty()
                ? new MusicScore.TimeSignature(0L, 4, 4)
                : source.timeSignatures().getFirst();
    }

    void setTimeSignature(int numerator, int denominator) {
        if (numerator < 1 || numerator > 32 || denominator < 1 || denominator > 64
                || Integer.bitCount(denominator) != 1) {
            error = "The time signature is outside the supported range.";
            return;
        }
        MusicScore.TimeSignature current = timeSignature();
        if (current.tick() == 0L && current.numerator() == numerator
                && current.denominator() == denominator
                && source.timeSignatures().size() == 1) return;
        checkpoint();
        source = source.withTimeSignature(numerator, denominator);
        changed();
    }

    Note at(long tick, int pitch) {
        Note found = null;
        for (Note note : notes.values()) {
            if (note.pitch() == pitch && tick >= note.start() && tick < note.end()) found = note;
        }
        return found;
    }

    void selectOnly(long id) { selection.clear(); if (notes.containsKey(id)) selection.add(id); selectionRevision++; }
    void toggle(long id) { if (notes.containsKey(id) && !selection.remove(id)) selection.add(id); selectionRevision++; }
    void clearSelection() { selection.clear(); selectionRevision++; }
    void selectAll() { selection.clear(); selection.addAll(notes.keySet()); selectionRevision++; }
    List<Note> selectedNotes() {
        return selection.stream().map(notes::get).filter(java.util.Objects::nonNull).toList();
    }

    List<ClipboardNote> copySelection() {
        return copyNotes(selectedNotes());
    }
    List<ClipboardNote> copyNotes(Collection<Note> targets) {
        return targets.stream()
                .map(note -> new ClipboardNote(identity, note, glissandos.get(note.id()),
                        source.tracks().get(note.track())))
                .toList();
    }

    List<ClipboardNote> cutSelection() {
        List<ClipboardNote> copied = copySelection();
        if (!copied.isEmpty()) deleteSelection();
        return copied;
    }
    void selectIn(long start, long end, int lowPitch, int highPitch, boolean additive) {
        selectionRevision++;
        if (!additive) selection.clear();
        for (Note note : notes.values()) {
            if (note.end() > start && note.start() < end
                    && note.pitch() >= lowPitch && note.pitch() <= highPitch) selection.add(note.id());
        }
    }

    void toggleIn(long start, long end, int lowPitch, int highPitch) {
        for (Note note : notes.values()) {
            if (note.end() > start && note.start() < end
                    && note.pitch() >= lowPitch && note.pitch() <= highPitch) toggle(note.id());
        }
    }

    long add(int track, int pitch, int velocity, long start, long length) {
        if (!capacity(1)) return -1L;
        if (track < 0 || track >= source.tracks().size()
                || pitch < MusicScore.MIN_PITCH || pitch > MusicScore.MAX_PITCH
                || velocity < 1 || velocity > 127 || start < 0
                || length < 1 || start > MusicScore.MAX_TICKS - length) {
            error = "The note is outside the score's supported range.";
            return -1L;
        }
        checkpoint();
        long id = nextId++;
        Note note = new Note(id, track, pitch, velocity, start, start + length, start + length);
        notes.put(id, note);
        selectOnly(id);
        changed();
        return id;
    }

    void addRecorded(int track, int pitch, int velocity, long start, long length) {
        Set<Long> selected = Set.copyOf(selection);
        long id = add(track, pitch, velocity, start, length);
        if (id < 0) return;
        selection.clear(); selection.addAll(selected); selectionRevision++;
        if (editStart != null) {
            // Recording stays independently undoable without taking ownership of a live drag.
            pushCheckpoint(editStart);
            List<Note> recordedBase = new ArrayList<>(editStart.notes());
            recordedBase.add(notes.get(id));
            editStart = new State(editStart.source(), recordedBase, editStart.glissandos(),
                    editStart.selection(), editStart.pastedTracks());
            editWasDirty = true;
        }
    }

    void deleteSelection() {
        if (selection.isEmpty()) return;
        checkpoint();
        selection.forEach(notes::remove);
        selection.forEach(glissandos::remove);
        selection.clear();
        changed();
    }

    void delete(long id) {
        if (!notes.containsKey(id)) return;
        checkpoint();
        notes.remove(id);
        glissandos.remove(id);
        selection.remove(id);
        changed();
    }

    void deleteAll(Collection<Long> ids) {
        if (ids == null || ids.stream().noneMatch(notes::containsKey)) return;
        checkpoint();
        ids.forEach(notes::remove);
        ids.forEach(glissandos::remove);
        selection.removeAll(ids);
        changed();
    }

    void moveSelection(long tickDelta, int pitchDelta, boolean copy) {
        if (selection.isEmpty() || (tickDelta == 0 && pitchDelta == 0)) return;
        if (copy && !capacity(selectedNotes().size())) return;
        long safeTickDelta = boundedTickDelta(tickDelta);
        int safePitchDelta = boundedPitchDelta(pitchDelta);
        if (safeTickDelta == 0 && safePitchDelta == 0) return;
        checkpoint();
        List<Note> moved = selectedNotes();
        if (copy) selection.clear();
        for (Note note : moved) {
            long start = note.start() + safeTickDelta;
            int pitch = note.pitch() + safePitchDelta;
            Note result = new Note(copy ? nextId++ : note.id(), note.track(), pitch, note.velocity(),
                    start, start + Math.max(1, note.release() - note.start()), start + note.length());
            notes.put(result.id(), result);
            MusicScore.Glissando glissando = glissandos.get(note.id());
            if (glissando != null) {
                MusicScore.Glissando movedGlissando = new MusicScore.Glissando(result.track(), result.start(),
                        result.pitch(), glissando.points());
                if (copy) glissandos.put(result.id(), movedGlissando);
                else glissandos.put(note.id(), movedGlissando);
            }
            if (copy) selection.add(result.id());
        }
        changed();
    }

    long boundedTickDelta(long tickDelta) {
        List<Note> targets = selectedNotes();
        return Math.clamp(tickDelta,
                -targets.stream().mapToLong(Note::start).min().orElse(0L),
                MusicScore.MAX_TICKS - targets.stream().mapToLong(Note::end).max().orElse(0L));
    }

    int boundedPitchDelta(int pitchDelta) {
        return Math.clamp(pitchDelta,
                MusicScore.MIN_PITCH - selection.stream().map(notes::get)
                        .filter(java.util.Objects::nonNull).mapToInt(Note::pitch)
                        .min().orElse(MusicScore.MIN_PITCH),
                MusicScore.MAX_PITCH - selection.stream().map(notes::get)
                        .filter(java.util.Objects::nonNull).mapToInt(Note::pitch)
                        .max().orElse(MusicScore.MAX_PITCH));
    }

    long boundedResizeStartDelta(long anchorId, long requestedStart, long minimumLength) {
        Note anchor = notes.get(anchorId);
        if (anchor == null) return 0L;
        minimumLength = Math.clamp(minimumLength, 1L, MusicScore.MAX_TICKS);
        final long minimum = minimumLength;
        List<Note> targets = resizeTargets(anchorId);
        long minimumDelta = -targets.stream().mapToLong(Note::start).min().orElse(0L);
        long maximumDelta = targets.stream().mapToLong(note -> note.length() - minimum)
                .min().orElse(0L);
        if (minimumDelta > maximumDelta) return 0L;
        return Math.clamp(Math.clamp(requestedStart, 0L, MusicScore.MAX_TICKS) - anchor.start(), minimumDelta, maximumDelta);
    }

    long boundedResizeEndDelta(long anchorId, long requestedEnd, long minimumLength) {
        Note anchor = notes.get(anchorId);
        if (anchor == null) return 0L;
        final long minimum = Math.clamp(minimumLength, 1L, MusicScore.MAX_TICKS);
        long minimumDelta = resizeTargets(anchorId).stream()
                .mapToLong(note -> minimum - note.length()).max().orElse(0L);
        long maximumDelta = MusicScore.MAX_TICKS - resizeTargets(anchorId).stream()
                .mapToLong(Note::end).max().orElse(0L);
        if (minimumDelta > maximumDelta) return 0L;
        return Math.clamp(Math.clamp(requestedEnd, 0L, MusicScore.MAX_TICKS) - anchor.end(), minimumDelta, maximumDelta);
    }

    void resizeSelectionStart(long anchorId, long requestedStart, long minimumLength) {
        long delta = boundedResizeStartDelta(anchorId, requestedStart, minimumLength);
        if (delta == 0L) return;
        List<Note> targets = resizeTargets(anchorId);
        checkpoint();
        for (Note note : targets) {
            long start = note.start() + delta;
            long release = Math.clamp(note.release(), start + 1L, note.end());
            Note resized = new Note(note.id(), note.track(), note.pitch(), note.velocity(), start,
                    release, note.end());
            notes.put(note.id(), resized);
            MusicScore.Glissando glissando = glissandos.get(note.id());
            if (glissando != null) glissandos.put(note.id(), new MusicScore.Glissando(
                    resized.track(), resized.start(), resized.pitch(), glissando.points()));
        }
        changed();
    }

    void resizeSelectionEnd(long anchorId, long requestedEnd, long minimumLength) {
        long delta = boundedResizeEndDelta(anchorId, requestedEnd, minimumLength);
        if (delta == 0L) return;
        List<Note> targets = resizeTargets(anchorId);
        checkpoint();
        for (Note note : targets) {
            long end = note.end() + delta;
            long release = Math.clamp(note.release() + delta, note.start() + 1, end);
            notes.put(note.id(), new Note(note.id(), note.track(), note.pitch(), note.velocity(),
                    note.start(), release, end));
        }
        changed();
    }

    void adjustSelectionVelocity(int delta) {
        if (selection.isEmpty() || delta == 0) return;
        boolean willChange = selectedNotes().stream().anyMatch(note ->
                Math.clamp((long) note.velocity() + delta, 1, 127) != note.velocity());
        if (!willChange) return;
        checkpoint();
        for (Note note : selectedNotes()) {
            int velocity = Math.clamp((long) note.velocity() + delta, 1, 127);
            notes.put(note.id(), new Note(note.id(), note.track(), note.pitch(), velocity,
                    note.start(), note.release(), note.end()));
        }
        changed();
    }

    private List<Note> resizeTargets(long anchorId) {
        Note anchor = notes.get(anchorId);
        if (anchor == null) return List.of();
        return selection.contains(anchorId) ? selectedNotes() : List.of(anchor);
    }

    void paste(List<ClipboardNote> copied, long tickOffset) {
        if (copied == null || copied.isEmpty()) return;
        if (!capacity(copied.size())) return;
        long first = copied.stream().mapToLong(entry -> entry.note().start()).min().orElse(0L);
        long span = copied.stream().mapToLong(entry -> entry.note().end()).max().orElse(first) - first;
        if (span > MusicScore.MAX_TICKS) {
            error = "The copied notes exceed the score's supported duration.";
            return;
        }
        long offset = Math.clamp(tickOffset, 0L, MusicScore.MAX_TICKS - span);
        ArrayList<MusicScore.TrackInfo> tracks = new ArrayList<>(source.tracks());
        Map<ClipboardTrack, Integer> mapping = new LinkedHashMap<>();
        for (ClipboardNote entry : copied) {
            MusicScore.TrackInfo track = entry.track();
            ClipboardTrack key = new ClipboardTrack(entry.origin(), track.id());
            if (mapping.containsKey(key)) continue;
            int target = entry.origin().equals(identity) ? track.id() : pastedTracks.getOrDefault(key, -1);
            if (target >= tracks.size() || target >= 0 && !sameTrack(tracks.get(target), track)) target = -1;
            if (target < 0) {
                if (tracks.size() >= MusicScore.MAX_TRACKS) {
                    error = "A score can contain at most 256 tracks.";
                    return;
                }
                target = tracks.size();
                tracks.add(new MusicScore.TrackInfo(target, track.name(), track.percussion(),
                        track.octave(), track.drumMap()));
            }
            mapping.put(key, target);
        }
        checkpoint();
        pastedTracks.putAll(mapping);
        source = new MusicScore(source.ppq(), source.percussion(), source.sourceName(),
                source.lengthTick(), source.tuningHz(), source.tempos(), source.timeSignatures(),
                source.expressions(), source.notes(), source.automation(), tracks, source.author(),
                source.signed(), source.instrumentId(), source.convertDynamics(), source.convertGlissando());
        selection.clear();
        for (ClipboardNote entry : copied) {
            Note note = entry.note();
            long start = note.start() - first + offset;
            long id = nextId++;
            Note pasted = new Note(id, mapping.get(new ClipboardTrack(entry.origin(), entry.track().id())), note.pitch(), note.velocity(), start,
                    start + Math.max(1, note.release() - note.start()), start + note.length());
            notes.put(id, pasted);
            MusicScore.Glissando glissando = entry.glissando();
            if (glissando != null) glissandos.put(id, new MusicScore.Glissando(
                    pasted.track(), pasted.start(), pasted.pitch(), glissando.points()));
            selection.add(id);
        }
        changed();
    }

    boolean editNote(long id, int pitch, int velocity, long start, long length) {
        Note prior = notes.get(id);
        if (prior == null || pitch < MusicScore.MIN_PITCH || pitch > MusicScore.MAX_PITCH
                || velocity < 1 || velocity > 127 || start < 0 || length < 1
                || length > MusicScore.MAX_TICKS || start > MusicScore.MAX_TICKS - length) return false;
        long end = start + length;
        long release = Math.clamp(end - (prior.end() - prior.release()), start + 1, end);
        Note next = new Note(id, prior.track(), pitch, velocity, start, release, end);
        if (prior.equals(next)) return true;
        checkpoint(); notes.put(id, next);
        MusicScore.Glissando glissando = glissandos.get(id);
        if (glissando != null) glissandos.put(id, new MusicScore.Glissando(prior.track(), start, pitch, glissando.points()));
        changed(); return true;
    }

    void setVelocities(Map<Long, Integer> values) {
        boolean any = values.entrySet().stream().anyMatch(entry -> {
            Note note = notes.get(entry.getKey());
            return note != null && note.velocity() != Math.clamp(entry.getValue(), 1, 127);
        });
        if (!any) return;
        checkpoint();
        values.forEach((id, value) -> {
            Note note = notes.get(id);
            if (note != null) notes.put(id, new Note(id, note.track(), note.pitch(),
                    Math.clamp(value, 1, 127), note.start(), note.release(), note.end()));
        });
        changed();
    }

    long paint(int track, int pitch, int velocity, long start, long length) {
        if (start < 0 || start >= MusicScore.MAX_TICKS) return -1;
        long end = Math.min(MusicScore.MAX_TICKS, start + Math.min(length, MusicScore.MAX_TICKS - start));
        for (Note note : notes.values()) {
            if (note.track() != track || note.pitch() != pitch) continue;
            if (note.start() <= start && start < note.end()) return -1;
            if (start < note.start() && note.start() < end) end = note.start();
        }
        return add(track, pitch, velocity, start, Math.max(1, end - start));
    }

    void slice(long id, long tick) {
        Note note = notes.get(id);
        if (note == null || tick <= note.start() || tick >= note.end() || !capacity(1)) return;
        checkpoint();
        notes.put(id, new Note(id, note.track(), note.pitch(), note.velocity(), note.start(),
                Math.min(note.release(), tick), tick));
        long next = nextId++;
        notes.put(next, new Note(next, note.track(), note.pitch(), note.velocity(), tick,
                Math.clamp(note.release(), tick + 1, note.end()), note.end()));
        MusicScore.Glissando glissando = glissandos.get(id);
        if (glissando != null) glissandos.put(next, new MusicScore.Glissando(
                note.track(), tick, note.pitch(), glissando.points()));
        if (selection.contains(id)) selection.add(next);
        changed();
    }

    void chopSelection(long step) {
        if (step < 1) return;
        List<Note> targets = selection.isEmpty() ? notes() : selectedNotes();
        long additions = targets.stream().mapToLong(note -> (note.length() - 1) / step).sum();
        if (additions == 0 || !capacity((int) Math.min(Integer.MAX_VALUE, additions))) return;
        beginEdit();
        for (Note note : targets) {
            // Slice from the right so the original identity always owns the next cut.
            for (long tick = note.start() + ((note.length() - 1) / step) * step;
                    tick > note.start(); tick -= step) slice(note.id(), tick);
        }
        commitEdit();
    }

    void glueSelection() {
        List<Note> targets = new ArrayList<>(selection.isEmpty() ? notes() : selectedNotes());
        targets.sort(java.util.Comparator.comparingInt(Note::track).thenComparingInt(Note::pitch)
                .thenComparingLong(Note::start));
        beginEdit();
        Note prior = null;
        for (Note note : targets) {
            if (prior != null && prior.track() == note.track() && prior.pitch() == note.pitch()
                    && note.start() <= prior.end()) {
                checkpoint();
                prior = new Note(prior.id(), prior.track(), prior.pitch(), prior.velocity(), prior.start(),
                        Math.max(prior.release(), note.release()), Math.max(prior.end(), note.end()));
                notes.put(prior.id(), prior);
                notes.remove(note.id());
                glissandos.remove(note.id());
                selection.remove(note.id());
                changed();
            } else prior = note;
        }
        commitEdit();
    }

    void undo() {
        commitEdit();
        if (undo.isEmpty()) return;
        redo.push(state());
        restore(undo.pop());
        changed();
    }

    void redo() {
        commitEdit();
        if (redo.isEmpty()) return;
        undo.push(state());
        restore(redo.pop());
        changed();
    }

    MusicScore song() {
        if (snapshot != null) return snapshot;
        List<MusicScore.MidiNote> result = notes.values().stream().map(Note::midi).toList();
        long length = Math.max(source.lengthTick(), result.stream()
                .mapToLong(MusicScore.MidiNote::endTick).max().orElse(source.ppq()));
        MusicScore.AutomationData automation = new MusicScore.AutomationData(
                source.automation().volumeRamps(), List.copyOf(glissandos.values()),
                source.automation().baseTempoBpm());
        snapshot = new MusicScore(source.ppq(), source.percussion(), source.sourceName(), length,
                source.tuningHz(), source.tempos(), source.timeSignatures(), source.expressions(), result,
                automation, source.tracks(), source.author(), source.signed(), source.instrumentId(),
                source.convertDynamics(), source.convertGlissando());
        return snapshot;
    }

    private void checkpoint() {
        if (editStart == null) pushCheckpoint(state());
    }

    private void pushCheckpoint(State before) {
        undo.push(before);
        while (undo.size() > 64) undo.removeLast();
        redo.clear();
    }

    private State state() { return new State(source, new ArrayList<>(notes.values()), new LinkedHashMap<>(glissandos),
            new LinkedHashSet<>(selection), new LinkedHashMap<>(pastedTracks)); }
    private void restore(State state) {
        source = state.source();
        pastedTracks.clear();
        pastedTracks.putAll(state.pastedTracks());
        notes.clear();
        for (Note note : state.notes()) notes.put(note.id(), note);
        glissandos.clear();
        glissandos.putAll(state.glissandos());
        selection.clear();
        selection.addAll(state.selection());
    }
    private boolean capacity(int additions) {
        if (additions <= MusicScore.MAX_NOTES - notes.size()) return true;
        error = "A score can contain at most " + MusicScore.MAX_NOTES + " notes.";
        return false;
    }

    private static boolean sameTrack(MusicScore.TrackInfo left, MusicScore.TrackInfo right) {
        return left.name().equals(right.name()) && left.percussion() == right.percussion()
                && left.octave() == right.octave() && left.drumMap().equals(right.drumMap());
    }

    private void changed() { dirty = true; snapshot = null; noteView = null; revision++; selectionRevision++; }
}
