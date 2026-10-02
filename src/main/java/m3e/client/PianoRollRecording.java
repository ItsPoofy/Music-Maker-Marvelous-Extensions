package m3e.client;

import m3e.sheet.MusicScore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Recording input and musical click boundaries, independent of screen widgets. */
final class PianoRollRecording {
    record Note(int pitch, int velocity, long startTick) { }
    private final Map<Integer, Note> notes = new LinkedHashMap<>();
    private final BiConsumer<Note, Long> finish;
    private final Consumer<Boolean> click;
    private boolean counting;
    private boolean active;
    private long startTick;
    private long countInNanos;
    private int countInBeat = -1;
    private long metronomeBoundary = Long.MIN_VALUE;

    PianoRollRecording(BiConsumer<Note, Long> finish, Consumer<Boolean> click) {
        this.finish = finish;
        this.click = click;
    }
    boolean counting() { return counting; }
    boolean active() { return active; }
    long startTick() { return startTick; }
    List<Note> notes() { return List.copyOf(notes.values()); }

    void begin(long tick, long nowNanos) {
        notes.clear();
        startTick = tick;
        counting = true;
        active = false;
        countInNanos = nowNanos;
        countInBeat = -1;
        resetMetronome();
    }

    boolean countIn(MusicScore.TimeSignature signature, int bpm, long nowNanos) {
        if (!counting) return false;
        long beatMicros = Math.max(1L, Math.round(60_000_000.0D * 4
                / (Math.max(1, bpm) * (double) signature.denominator())));
        long beat = Math.max(0L, (nowNanos - countInNanos) / 1_000L) / beatMicros;
        if (beat >= signature.numerator()) return true;
        if (beat != countInBeat) {
            countInBeat = (int) beat;
            click.accept(beat == 0);
        }
        return false;
    }

    void start() { counting = false; active = true; }
    void noteOn(int pitch, int velocity, long tick) {
        if (!active) return;
        Note previous = notes.put(pitch, new Note(pitch, velocity, tick));
        if (previous != null) finish.accept(previous, tick);
    }
    void noteOff(int pitch, long tick) {
        Note note = notes.remove(pitch);
        if (note != null) finish.accept(note, tick);
    }
    void stop(long tick) {
        for (Note note : notes()) finish.accept(note, tick);
        notes.clear();
        counting = false;
        active = false;
        resetMetronome();
    }
    void resetMetronome() { metronomeBoundary = Long.MIN_VALUE; }

    void metronome(MusicScore song, long tick) {
        MusicScore.TimeSignature signature = new MusicScore.TimeSignature(0, 4, 4);
        for (MusicScore.TimeSignature candidate : song.timeSignatures()) {
            if (candidate.tick() > tick) break;
            signature = candidate;
        }
        long unit = Math.max(1L, Math.round(song.ppq() * 4.0D / signature.denominator()));
        long beat = Math.max(0L, Math.floorDiv(tick - signature.tick(), unit));
        long boundary = signature.tick() + beat * unit;
        if (boundary == metronomeBoundary) return;
        metronomeBoundary = boundary;
        click.accept(Math.floorMod(beat, signature.numerator()) == 0);
    }
}
