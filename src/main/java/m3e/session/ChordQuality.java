package m3e.session;

import java.util.Arrays;

/**
 * Arranger chord vocabulary. Canonical and essential masks are separate so a
 * defining shell such as C-E-Bb is recognized as C7 without inventing a
 * duplicate "no fifth" chord quality.
 */
public enum ChordQuality {
    // Keep the original seven ordinals stable for existing packets and saves.
    MAJOR("Major", ints(0, 4, 7), ints(0, 4)),
    MINOR("Minor", ints(0, 3, 7), ints(0, 3)),
    DIMINISHED("dim", ints(0, 3, 6), ints(0, 3, 6)),
    AUGMENTED("aug", ints(0, 4, 8), ints(0, 4, 8)),
    SUS2("sus2", ints(0, 2, 7), ints(0, 2, 7)),
    SUS4("sus4", ints(0, 5, 7), ints(0, 5, 7)),
    POWER("5", ints(0, 7), ints(0, 7)),

    DOMINANT_7("7", ints(0, 4, 7, 10), ints(0, 4, 10), ints(0, 7, 10)),
    MAJOR_7("Maj7", ints(0, 4, 7, 11), ints(0, 4, 11)),
    MINOR_7("m7", ints(0, 3, 7, 10), ints(0, 3, 10)),
    HALF_DIMINISHED("m7b5", ints(0, 3, 6, 10), ints(0, 3, 6, 10)),
    DIMINISHED_7("dim7", ints(0, 3, 6, 9), ints(0, 3, 6, 9)),
    MINOR_MAJOR_7("mMaj7", ints(0, 3, 7, 11), ints(0, 3, 11)),
    DOMINANT_7_SUS4("7sus4", ints(0, 5, 7, 10), ints(0, 5, 10)),

    MAJOR_6("6", ints(0, 4, 7, 9), ints(0, 4, 9)),
    MINOR_6("m6", ints(0, 3, 7, 9), ints(0, 3, 9)),
    ADD9("add9", ints(0, 2, 4, 7), ints(0, 2, 4)),
    MINOR_ADD9("m(add9)", ints(0, 2, 3, 7), ints(0, 2, 3)),
    DOMINANT_9("9", ints(0, 2, 4, 7, 10), ints(0, 2, 4, 10), ints(2, 4, 10)),
    MAJOR_9("Maj9", ints(0, 2, 4, 7, 11), ints(0, 2, 4, 11), ints(2, 4, 11)),
    MINOR_9("m9", ints(0, 2, 3, 7, 10), ints(0, 2, 3, 10), ints(2, 3, 10)),

    // Additional Yamaha Fingered vocabulary.  These remain data, rather than
    // special cases in the detector, so every root and inversion is handled
    // by the same rotational matcher.
    MAJOR_6_9("6(9)", ints(0, 2, 4, 7, 9), ints(0, 2, 4, 9), ints(2, 4, 9)),
    MAJOR_7_SHARP_11("Maj7(#11)", ints(0, 4, 6, 7, 11), ints(0, 4, 6, 11)),
    FLAT_5("b5", ints(0, 4, 6), ints(0, 4, 6)),
    MAJOR_7_FLAT_5("Maj7(b5)", ints(0, 4, 6, 11), ints(0, 4, 6, 11)),
    MAJOR_7_AUGMENTED("Maj7aug", ints(0, 4, 8, 11), ints(0, 8, 11)),
    MINOR_7_11("m7(11)", ints(0, 3, 5, 7, 10), ints(0, 3, 5, 10)),
    MINOR_MAJOR_9("mMaj7(9)", ints(0, 2, 3, 7, 11), ints(0, 2, 3, 11), ints(2, 3, 11)),
    MINOR_MAJOR_7_FLAT_5("mMaj7(b5)", ints(0, 3, 6, 11), ints(0, 3, 6, 11)),
    DOMINANT_7_FLAT_9("7(b9)", ints(0, 1, 4, 7, 10), ints(0, 1, 4, 10)),
    DOMINANT_7_FLAT_13("7(b13)", ints(0, 4, 7, 8, 10), ints(0, 4, 8, 10)),
    DOMINANT_7_SHARP_11("7(#11)", ints(0, 4, 6, 7, 10), ints(0, 4, 6, 10)),
    DOMINANT_13("7(13)", ints(0, 4, 7, 9, 10), ints(0, 4, 9, 10), ints(4, 9, 10)),
    DOMINANT_7_SHARP_9("7(#9)", ints(0, 3, 4, 7, 10), ints(0, 3, 4, 10)),
    DOMINANT_7_FLAT_5("7(b5)", ints(0, 4, 6, 10), ints(0, 4, 6, 10)),
    DOMINANT_7_AUGMENTED("7aug", ints(0, 4, 8, 10), ints(0, 4, 8, 10)),

    // Directly recognized Yamaha control shapes.  They never participate in
    // ordinary best-fit matching.
    ROOT_ONLY("1+8", ints(0), ints(0)),
    CANCEL("Cancel", ints(), ints());

    private final String suffix;
    private final int[] intervals;
    private final int mask;
    private final int essentialMask;
    private final int[] recognitionMasks;

    ChordQuality(String suffix, int[] intervals, int[] essentialIntervals, int[]... alternateEssentialIntervals) {
        this.suffix = suffix;
        this.intervals = intervals;
        this.mask = mask(intervals);
        this.essentialMask = mask(essentialIntervals);
        this.recognitionMasks = new int[1 + alternateEssentialIntervals.length];
        this.recognitionMasks[0] = this.essentialMask;
        for (int index = 0; index < alternateEssentialIntervals.length; index++) {
            this.recognitionMasks[index + 1] = mask(alternateEssentialIntervals[index]);
        }
    }

    public String suffix() { return suffix; }
    public int[] intervals() { return intervals.clone(); }
    public int mask() { return mask; }
    public int essentialMask() { return essentialMask; }
    public int[] recognitionMasks() { return recognitionMasks.clone(); }
    public int pitchClassCount() { return Integer.bitCount(mask); }
    public boolean contains(int interval) { return (mask & (1 << Math.floorMod(interval, 12))) != 0; }
    public boolean isSuspended() { return this == SUS2 || this == SUS4 || this == DOMINANT_7_SUS4; }
    public boolean isPower() { return this == POWER; }
    public boolean isRecognitionOnly() { return this == ROOT_ONLY || this == CANCEL; }

    public int third() {
        if (isSuspended()) return this == SUS2 ? 2 : 5;
        if (this == POWER || isRecognitionOnly()) return -1;
        return switch (this) {
            case MINOR, DIMINISHED, MINOR_7, HALF_DIMINISHED, DIMINISHED_7,
                    MINOR_MAJOR_7, MINOR_6, MINOR_ADD9, MINOR_9, MINOR_7_11,
                    MINOR_MAJOR_9, MINOR_MAJOR_7_FLAT_5 -> 3;
            default -> 4;
        };
    }

    public int fifth() {
        return switch (this) {
            case DIMINISHED, HALF_DIMINISHED, DIMINISHED_7, FLAT_5,
                    MAJOR_7_FLAT_5, MINOR_MAJOR_7_FLAT_5,
                    DOMINANT_7_FLAT_5 -> 6;
            case AUGMENTED, MAJOR_7_AUGMENTED, DOMINANT_7_AUGMENTED -> 8;
            case CANCEL -> -1;
            default -> 7;
        };
    }

    public int seventh() {
        return switch (this) {
            case MAJOR_7, MINOR_MAJOR_7, MAJOR_9, MAJOR_7_SHARP_11,
                    MAJOR_7_FLAT_5, MAJOR_7_AUGMENTED, MINOR_MAJOR_9,
                    MINOR_MAJOR_7_FLAT_5 -> 11;
            case DOMINANT_7, MINOR_7, HALF_DIMINISHED, DOMINANT_7_SUS4,
                    DOMINANT_9, MINOR_9, MINOR_7_11, DOMINANT_7_FLAT_9,
                    DOMINANT_7_FLAT_13, DOMINANT_7_SHARP_11, DOMINANT_13,
                    DOMINANT_7_SHARP_9, DOMINANT_7_FLAT_5,
                    DOMINANT_7_AUGMENTED -> 10;
            case DIMINISHED_7 -> 9;
            default -> -1;
        };
    }

    public int ninth() {
        return switch (this) {
            case ADD9, MINOR_ADD9, DOMINANT_9, MAJOR_9, MINOR_9, SUS2,
                    MAJOR_6_9, MINOR_MAJOR_9 -> 2;
            default -> -1;
        };
    }

    public int eleventh() {
        return switch (this) {
            case SUS4, DOMINANT_7_SUS4, MINOR_7_11 -> 5;
            case MAJOR_7_SHARP_11, DOMINANT_7_SHARP_11 -> 6;
            default -> -1;
        };
    }

    public int thirteenth() {
        return switch (this) {
            case MAJOR_6, MINOR_6, MAJOR_6_9, DOMINANT_13 -> 9;
            case DOMINANT_7_FLAT_13 -> 8;
            default -> -1;
        };
    }

    private static int[] ints(int... values) { return values; }
    private static int mask(int[] values) {
        return Arrays.stream(values).reduce(0, (result, interval) -> result | 1 << Math.floorMod(interval, 12));
    }
}
