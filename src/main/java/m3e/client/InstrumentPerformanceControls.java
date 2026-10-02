package m3e.client;

import m3e.session.ChordMatcher;
import m3e.session.DetectedChord;
import m3e.session.NoteNames;
import m3e.api.client.M3EClientIntegration;

import java.util.HashMap;
import java.util.Map;

/** Shared state for the small controls mounted above Music Maker's keyboard. */
public final class InstrumentPerformanceControls {
    private static volatile int keyboardVelocity = 102;
    private static volatile DetectedChord localChord;
    private static final Map<Integer, Integer> ACTIVE_NOTES = new HashMap<>();
    private static final Map<Integer, Integer> BUILDING_NOTES = new HashMap<>();
    private static long detectAfterNanos;
    private static long clearAfterNanos;
    private static DetectedChord previous;
    private static volatile DetectedChord authoritativeChord;

    private InstrumentPerformanceControls() { }

    public static int keyboardVelocity() { return keyboardVelocity; }

    static boolean acceptVelocityText(String text) {
        if (text == null || !text.matches("\\d{1,3}")) return false;
        int parsed;
        try {
            parsed = Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return false;
        }
        if (parsed < 1 || parsed > 127) return false;
        keyboardVelocity = parsed;
        return true;
    }

    static String chordLabel() {
        DetectedChord shown = M3EClientIntegration.ensembleActive() && authoritativeChord != null
                ? authoritativeChord : localChord;
        return "Chord: " + (shown == null ? "—"
                : NoteNames.chord(shown, InstrumentOptions.noteSpelling()));
    }

    public static void setAuthoritativeChord(DetectedChord detected) { authoritativeChord = detected; }

    public static void clearAuthoritativeChord() { authoritativeChord = null; }

    public static synchronized void noteOn(int note, int velocity) {
        ACTIVE_NOTES.put(note, Math.clamp(velocity, 1, 127));
        BUILDING_NOTES.put(note, Math.clamp(velocity, 1, 127));
        if (detectAfterNanos == 0L) {
            detectAfterNanos = System.nanoTime() + 100_000_000L;
        }
        clearAfterNanos = 0L;
    }

    public static synchronized void noteOff(int note) {
        ACTIVE_NOTES.remove(note);
        if (ACTIVE_NOTES.isEmpty()) {
            if (detectAfterNanos == 0L) clearAfterNanos = System.nanoTime() + 750_000_000L;
        }
    }

    static synchronized void tick() {
        tick(System.nanoTime());
    }

    static synchronized void tick(long now) {
        if (clearAfterNanos != 0L && now >= clearAfterNanos && ACTIVE_NOTES.isEmpty()) {
            clearChord();
            previous = null;
            clearAfterNanos = 0L;
        }
        if (detectAfterNanos == 0L || now < detectAfterNanos) return;
        detectAfterNanos = 0L;
        Map<Integer, Integer> evidence = new HashMap<>(BUILDING_NOTES);
        evidence.putAll(ACTIVE_NOTES);
        DetectedChord detected = ChordMatcher.detect(evidence.entrySet().stream()
                .map(entry -> new ChordMatcher.PlayedNote(entry.getKey(), entry.getValue())).toList(), true, previous);
        BUILDING_NOTES.clear();
        if (detected == null) {
            // An incomplete transition does not invalidate the last chord;
            // the same rule is used by the authoritative arranger. The normal
            // release grace below still clears it once input actually ends.
            if (previous == null) localChord = null;
            return;
        }
        previous = detected;
        localChord = detected;
        if (ACTIVE_NOTES.isEmpty()) clearAfterNanos = now + 750_000_000L;
    }

    public static synchronized void clearInput() {
        ACTIVE_NOTES.clear();
        BUILDING_NOTES.clear();
        previous = null;
        detectAfterNanos = 0L;
        clearAfterNanos = 0L;
        clearChord();
        clearAuthoritativeChord();
    }

    static synchronized DetectedChord detectedChord() { return localChord; }

    static void clearChord() { localChord = null; }
}
