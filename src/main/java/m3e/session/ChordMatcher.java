package m3e.session;

import xerca.xercamusic.common.item.IItemInstrument;

import java.util.Collection;

/**
 * Deterministic 12-bit pitch-class matcher. It favors exact canonical masks,
 * accepts only explicitly sanctioned omissions, tolerates at most one added
 * color/passing pitch, and uses bass/previous harmony solely as tie-breakers.
 */
public final class ChordMatcher {
    private static final int TWELVE_BITS = 0xFFF;

    private ChordMatcher() { }

    public static DetectedChord detect(Collection<PlayedNote> notes, boolean allowPower, DetectedChord previous) {
        return detect(notes, allowPower, previous, true);
    }

    /**
     * Yamaha Fingered recognition uses only documented complete/omitted-note
     * shapes. Other live-performance callers may explicitly retain the
     * M3E's one-passing-note tolerance.
     */
    public static DetectedChord detect(Collection<PlayedNote> notes, boolean allowPower,
                                       DetectedChord previous, boolean allowAddedPitch) {
        if (notes == null || notes.isEmpty()) return null;
        int heldMask = 0;
        int[] weights = new int[12];
        int bass = IItemInstrument.MAX_NOTE;
        for (PlayedNote played : notes) {
            if (played.note < IItemInstrument.MIN_NOTE || played.note > IItemInstrument.MAX_NOTE) return null;
            int pitchClass = Math.floorMod(played.note, 12);
            heldMask |= 1 << pitchClass;
            weights[pitchClass] += Math.max(1, Math.clamp(played.velocity, 1, 127) / 16);
            bass = Math.min(bass, played.note);
        }
        int presentCount = Integer.bitCount(heldMask);
        if (presentCount < 2 || (presentCount < 3 && !allowPower)) return null;
        if (isDenseChromaticCluster(heldMask)) return null;

        Match best = null;
        Match second = null;
        int bassClass = Math.floorMod(bass, 12);
        for (int root = 0; root < 12; root++) {
            for (ChordQuality quality : ChordQuality.values()) {
                if (quality.isRecognitionOnly()) continue;
                if (quality.isPower() && (!allowPower || presentCount != 2)) continue;
                if (!quality.isPower() && presentCount < 3) continue;

                int canonical = rotateLeft(quality.mask(), root);
                int essential = matchingRecognitionMask(quality, root, heldMask);
                if (essential < 0) continue;

                int extras = Integer.bitCount(heldMask & ~canonical & TWELVE_BITS);
                if (extras > (allowAddedPitch ? 1 : 0)) continue;
                int missing = Integer.bitCount(canonical & ~heldMask);
                int matched = Integer.bitCount(heldMask & canonical);
                boolean exact = extras == 0 && missing == 0;
                boolean exactRecognitionShape = heldMask == essential;
                boolean rootlessRecognitionShape = exactRecognitionShape
                        && (essential & 1 << root) == 0;
                boolean completeShell = extras == 0 && missing > 0;

                int weightedMatches = 0;
                int weightedExtras = 0;
                for (int pitchClass = 0; pitchClass < 12; pitchClass++) {
                    if ((heldMask & 1 << pitchClass) == 0) continue;
                    if ((canonical & 1 << pitchClass) != 0) weightedMatches += weights[pitchClass];
                    else weightedExtras += weights[pitchClass];
                }

                // Exact matches with the same absolute mask must start equal:
                // C6/Am7 and Cm6/Am7b5 are genuinely ambiguous pitch-class
                // sets, so bass and harmonic history—not enum metadata—decide.
                int score = (exact ? 500
                        : rootlessRecognitionShape ? 480
                        : exactRecognitionShape ? 350
                        : completeShell ? 300 : 0)
                        + (exact ? 0 : Integer.bitCount(essential) * 42)
                        + matched * 24
                        + quality.pitchClassCount() * 4
                        + weightedMatches * 2
                        - missing * 15
                        - extras * 30
                        - weightedExtras * 3;
                /*
                 * Pitch-class shells are intentionally rootless and therefore
                 * ambiguous with several ordinary triads. A candidate whose
                 * root is actually the physical bass must win that ambiguity;
                 * only use the rootless interpretation when no rooted shape
                 * explains what the performer played.
                 */
                if (root == bassClass) score += 160;
                if (previous != null) {
                    if (root == previous.root() && quality == previous.quality()) score += 12;
                    else if (root == previous.root()) score += 4;
                }

                Match candidate = new Match(root, quality, score, exact,
                        exact || exactRecognitionShape, matched, missing, extras);
                if (better(candidate, best, bassClass)) {
                    second = best;
                    best = candidate;
                } else if (better(candidate, second, bassClass)) {
                    second = candidate;
                }
            }
        }
        if (best == null) return null;
        int margin = second == null ? 20 : Math.max(0, best.score - second.score);
        int confidence = Math.clamp(48 + (best.recognitionExact ? 28 : 0) + best.matched * 4
                - best.missing * 5 - best.extras * 9 + Math.min(16, margin), 1, 100);
        return new DetectedChord(best.root, best.quality, bass, confidence);
    }

    /** Root-relative masks become absolute pitch-class masks by circular rotation. */
    public static int rotateLeft(int mask, int shift) {
        int normalized = Math.floorMod(shift, 12);
        return ((mask << normalized) | (mask >>> (12 - normalized))) & TWELVE_BITS;
    }

    /**
     * Returns the most informative sanctioned voicing contained in the held
     * notes.  This supports documented omitted-tone and rootless shells
     * without weakening every chord to a generic subset match.
     */
    public static int matchingRecognitionMask(ChordQuality quality, int root, int heldMask) {
        int best = -1;
        for (int relative : quality.recognitionMasks()) {
            int required = rotateLeft(relative, root);
            if ((heldMask & required) == required
                    && (best < 0 || Integer.bitCount(required) > Integer.bitCount(best))) {
                best = required;
            }
        }
        return best;
    }

    private static boolean isDenseChromaticCluster(int heldMask) {
        int present = Integer.bitCount(heldMask);
        if (present < 4) return false;
        int adjacentPairs = 0;
        for (int pitchClass = 0; pitchClass < 12; pitchClass++) {
            if ((heldMask & 1 << pitchClass) != 0
                    && (heldMask & 1 << Math.floorMod(pitchClass + 1, 12)) != 0) {
                adjacentPairs++;
            }
        }
        return adjacentPairs >= present - 1;
    }

    private static boolean better(Match candidate, Match current, int bassClass) {
        if (current == null) return true;
        if (candidate.root == current.root && candidate.canonicalExact != current.canonicalExact) {
            return candidate.canonicalExact;
        }
        if (candidate.score != current.score) return candidate.score > current.score;
        if ((candidate.root == bassClass) != (current.root == bassClass)) return candidate.root == bassClass;
        if (candidate.recognitionExact != current.recognitionExact) return candidate.recognitionExact;
        if (candidate.quality.pitchClassCount() != current.quality.pitchClassCount()) {
            return candidate.quality.pitchClassCount() > current.quality.pitchClassCount();
        }
        if (candidate.quality.ordinal() != current.quality.ordinal()) return candidate.quality.ordinal() < current.quality.ordinal();
        return candidate.root < current.root;
    }

    public record PlayedNote(int note, int velocity) { }
    private record Match(int root, ChordQuality quality, int score,
                         boolean canonicalExact, boolean recognitionExact,
                         int matched, int missing, int extras) { }
}
