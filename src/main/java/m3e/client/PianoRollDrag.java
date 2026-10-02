package m3e.client;

import java.util.function.DoubleToLongFunction;

/** Musical displacement, independent of pointer grab phase and GUI row boundaries. */
final class PianoRollDrag {
    private double time;
    private double pitch;

    PianoRollDrag(double time, double pitch) {
        this(time, pitch, 0);
    }

    PianoRollDrag(double time, double pitch, long cellStep) {
        this.time = time - cellStep * 0.5;
        this.pitch = pitch;
    }

    long timeDelta(long noteAnchor, double pointerTime, boolean locked, DoubleToLongFunction snap) {
        if (locked) return 0L;
        long anchor = snap.applyAsLong(noteAnchor);
        return snap.applyAsLong(anchor + Math.rint(pointerTime - time)) - anchor;
    }

    int pitchDelta(double pointerPitch, boolean locked) {
        return locked ? 0 : (int) (pointerPitch - pitch);
    }

    void applied(long ticks, int semitones) {
        time += ticks;
        pitch += semitones;
    }
}
