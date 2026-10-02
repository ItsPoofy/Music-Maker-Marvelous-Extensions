package m3e.sheet;

/** Conversions used when exact MIDI data must become an ordinary Music Maker sheet. */
public final class MusicMakerSheetCompatibility {
    private static final float VELOCITY_MIDPOINT = 0.7F;
    private static final float VELOCITY_CURVE_BASE =
            (1.0F / VELOCITY_MIDPOINT - 1.0F) * (1.0F / VELOCITY_MIDPOINT - 1.0F);

    private MusicMakerSheetCompatibility() { }

    /** Matches Music Maker's live-MIDI recording curve before storing note volume. */
    public static int noteVolume(int midiVelocity) {
        float normalized = Math.clamp(midiVelocity, 0, 127) / 128.0F;
        float curved = (float) (Math.pow(VELOCITY_CURVE_BASE, normalized)
                / (VELOCITY_CURVE_BASE - 1.0F)
                - 1.0F / (VELOCITY_CURVE_BASE - 1.0F));
        return Math.clamp(Math.round(curved * 128.0F), 0, 127);
    }

    /** Music Maker's editable tempo is an integer beats-per-second value. */
    public static int editableBps(int bpm) {
        return Math.clamp(Math.round(bpm / 60.0F), 1, 50);
    }

    /** Fine tempo steps for lossless MIDI-backed sheets. */
    public static int adjustedMidiBpm(int bpm, boolean increase,
                                      boolean shiftDown, boolean controlDown) {
        int step = shiftDown ? (controlDown ? 50 : 10) : 1;
        return Math.clamp(bpm + (increase ? step : -step), 1, 3_000);
    }

    public static int midiVelocity(int noteVolume) {
        int target = Math.clamp(noteVolume, 0, 127);
        int closestVelocity = 1;
        int closestDistance = Integer.MAX_VALUE;
        for (int velocity = 1; velocity <= 127; velocity++) {
            int distance = Math.abs(noteVolume(velocity) - target);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestVelocity = velocity;
            }
        }
        return closestVelocity;
    }
}
