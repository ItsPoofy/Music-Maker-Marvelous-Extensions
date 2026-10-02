package m3e.client;

/** Explicit MIDI velocity-to-linear-volume conversion shared by every player path. */
public final class MidiNoteVolume {
    private MidiNoteVolume() { }

    /** Music Maker's live-note path keeps a note-on alive at a minimum audible velocity. */
    public static float normalizedLive(int velocity) {
        return Math.clamp(velocity, 1, 127) / 127.0F;
    }

    /** Scales a song/sheet gain by a MIDI note-on velocity without integer division. */
    public static float scale(float baseVolume, int velocity) {
        return baseVolume * Math.clamp(velocity, 0, 127) / 127.0F;
    }
}
