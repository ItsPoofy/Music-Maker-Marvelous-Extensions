package m3e.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/** Pure scale, harmony, chord-set, inversion, and range mapping. */
public final class SmartPlayMapper {
    private static final int[] WHITE_DEGREES = {0, -1, 1, -1, 2, 3, -1, 4, -1, 5, -1, 6};

    private SmartPlayMapper() { }

    public static int mapNote(int midiNote, SmartPlaySettings.Scale scale,
                              int minimumNote, int maximumNote,
                              IntPredicate playable) {
        int minimum = Math.min(minimumNote, maximumNote);
        int maximum = Math.max(minimumNote, maximumNote);
        if (!scale.enabled() || scale.keyMode() == SmartPlaySettings.KeyMode.GUIDE) {
            return midiNote >= minimum && midiNote <= maximum && playable.test(midiNote)
                    ? midiNote : -1;
        }
        SmartPlayScaleCatalog.ScaleDefinition definition =
                SmartPlayScaleCatalog.scale(scale);
        if (scale.keyMode() == SmartPlaySettings.KeyMode.EASY
                && !definition.chromatic()) {
            int degree = WHITE_DEGREES[Math.floorMod(midiNote, 12)];
            if (degree < 0) return -1;
            int whiteIndex = Math.floorDiv(midiNote, 12) * 7 + degree;
            int middleCIndex = Math.floorDiv(60, 12) * 7;
            int scaleIndex = whiteIndex - middleCIndex;
            int[] intervals = definition.intervals();
            int target = 60 + scale.root()
                    + Math.floorDiv(scaleIndex, intervals.length) * 12
                    + intervals[Math.floorMod(scaleIndex, intervals.length)];
            return fitPitchClass(target, minimum, maximum, playable);
        }
        int bounded = Math.clamp(midiNote, minimum, maximum);
        if (allowed(bounded, scale.root(), definition) && playable.test(bounded)) return bounded;
        for (int distance = 1; distance <= maximum - minimum; distance++) {
            int upper = bounded + distance;
            if (upper <= maximum && allowed(upper, scale.root(), definition)
                    && playable.test(upper)) return upper;
            int lower = bounded - distance;
            if (lower >= minimum && allowed(lower, scale.root(), definition)
                    && playable.test(lower)) return lower;
        }
        return -1;
    }

    public static List<Integer> transform(int midiNote, SmartPlaySettings settings,
                                          int minimumNote, int maximumNote,
                                          IntPredicate playable,
                                          List<Integer> previousVoicing) {
        SmartPlaySettings.Chord chord = settings.scale().enabled()
                ? settings.chord() : SmartPlaySettings.Chord.DEFAULT;
        int minimum = Math.min(minimumNote, maximumNote);
        int maximum = Math.max(minimumNote, maximumNote);
        // Chord Set disables Scale Type and Key Mode. Its trigger note therefore
        // must not be quantized by whichever scale settings remain stored.
        int mapped = chord.mode() == SmartPlaySettings.ChordMode.CHORD_SET
                ? midiNote >= minimum && midiNote <= maximum ? midiNote : -1
                : mapNote(midiNote, settings.scale(), minimum, maximum, playable);
        if (mapped < 0) return List.of();
        List<Integer> notes = switch (chord.mode()) {
            case OFF -> List.of(mapped);
            case HARMONIZER -> harmonize(mapped, settings.scale(), chord);
            case CHORD_SET -> SmartPlayChordSetCatalog.voicing(mapped,
                    settings.scale().root(), chord.chordSet());
        };
        if (chord.mode() == SmartPlaySettings.ChordMode.OFF) {
            return fitVoicing(notes, minimum, maximum, playable);
        }
        if (chord.position() != SmartPlaySettings.AUTO_POSITION) {
            return fitVoicing(position(notes, chord.position(), List.of()),
                    minimum, maximum, playable);
        }
        List<Integer> rootPosition = fitVoicing(position(notes, 0, List.of()),
                minimum, maximum, playable);
        if (previousVoicing == null || previousVoicing.isEmpty()) return rootPosition;
        List<Integer> best = rootPosition;
        long bestCost = voicingCost(best, previousVoicing);
        for (int shift = -8; shift <= 8; shift++) {
            List<Integer> candidate = fitVoicing(position(notes, shift, List.of()),
                    minimum, maximum, playable);
            long cost = voicingCost(candidate, previousVoicing);
            if (cost < bestCost) {
                best = candidate;
                bestCost = cost;
            }
        }
        return best;
    }

    public static boolean allowed(int midiNote, int root,
                                  SmartPlayScaleCatalog.ScaleDefinition definition) {
        int relative = Math.floorMod(midiNote - root, 12);
        for (int interval : definition.intervals()) if (relative == interval) return true;
        return false;
    }

    static List<Integer> position(List<Integer> source, int position,
                                  List<Integer> previousVoicing) {
        if (source.isEmpty()) return source;
        List<Integer> notes = new ArrayList<>(source);
        notes.sort(Integer::compareTo);
        if (position == SmartPlaySettings.AUTO_POSITION) {
            if (previousVoicing == null || previousVoicing.isEmpty()) return List.copyOf(notes);
            List<Integer> best = List.copyOf(notes);
            long bestCost = voicingCost(best, previousVoicing);
            for (int shift = -8; shift <= 8; shift++) {
                List<Integer> candidate = position(notes, shift, List.of());
                long cost = voicingCost(candidate, previousVoicing);
                if (cost < bestCost) {
                    best = candidate;
                    bestCost = cost;
                }
            }
            return best;
        }
        if (position < 0) {
            for (int count = 0; count < -position; count++) {
                int highest = notes.removeLast();
                insertSorted(notes, highest - 12);
            }
        } else {
            for (int count = 0; count < position; count++) {
                int lowest = notes.removeFirst();
                insertSorted(notes, lowest + 12);
            }
        }
        return List.copyOf(notes);
    }

    private static List<Integer> harmonize(int rootNote, SmartPlaySettings.Scale scale,
                                           SmartPlaySettings.Chord chord) {
        SmartPlayScaleCatalog.ScaleDefinition definition = SmartPlayScaleCatalog.scale(scale);
        if (definition.chromatic()) {
            return Arrays.stream(chord.chromaticHarmony().semitones())
                    .map(offset -> rootNote + offset).boxed().toList();
        }
        int[] intervals = definition.intervals();
        int relative = rootNote - scale.root();
        int octave = Math.floorDiv(relative, 12);
        int pitchClass = Math.floorMod(relative, 12);
        int rootDegree = nearestDegree(intervals, pitchClass);
        boolean rootInScale = intervals[rootDegree] == pitchClass;
        List<Integer> notes = new ArrayList<>();
        for (int degreeOffset : chord.diatonicHarmony().degrees()) {
            if (degreeOffset == 0 && !rootInScale) {
                notes.add(rootNote);
                continue;
            }
            int degree = rootDegree + degreeOffset;
            notes.add(scale.root() + (octave + Math.floorDiv(degree, intervals.length)) * 12
                    + intervals[Math.floorMod(degree, intervals.length)]);
        }
        return notes;
    }

    private static int nearestDegree(int[] intervals, int pitchClass) {
        int best = 0;
        int distance = 13;
        for (int index = 0; index < intervals.length; index++) {
            int candidate = Math.abs(intervals[index] - pitchClass);
            if (candidate < distance) { best = index; distance = candidate; }
        }
        return best;
    }

    private static int fitPitchClass(int note, int minimum, int maximum,
                                     IntPredicate playable) {
        if (note >= minimum && note <= maximum && playable.test(note)) return note;
        for (int octave = 1; octave <= 10; octave++) {
            int down = note - octave * 12;
            if (down >= minimum && down <= maximum && playable.test(down)) return down;
            int up = note + octave * 12;
            if (up >= minimum && up <= maximum && playable.test(up)) return up;
        }
        return -1;
    }

    private static List<Integer> fitVoicing(List<Integer> source, int minimum,
                                            int maximum, IntPredicate playable) {
        if (source.isEmpty()) return List.of();
        List<Integer> ordered = new ArrayList<>(source);
        ordered.sort(Integer::compareTo);
        for (int distance = 0; distance <= 10; distance++) {
            int[] shifts = distance == 0 ? new int[]{0} : new int[]{-distance, distance};
            for (int shift : shifts) {
                int semitones = shift * 12;
                boolean fits = true;
                for (int note : ordered) {
                    int candidate = note + semitones;
                    if (candidate < minimum || candidate > maximum
                            || !playable.test(candidate)) {
                        fits = false;
                        break;
                    }
                }
                if (fits) return ordered.stream().map(note -> note + semitones).toList();
            }
        }

        Set<Integer> fitted = new LinkedHashSet<>();
        for (int note : ordered) {
            int usable = fitPitchClass(note, minimum, maximum, playable);
            if (usable >= 0) fitted.add(usable);
        }
        return fitted.stream().sorted().toList();
    }

    private static void insertSorted(List<Integer> notes, int value) {
        int index = 0;
        while (index < notes.size() && notes.get(index) <= value) index++;
        notes.add(index, value);
    }

    private static long voicingCost(List<Integer> candidate, List<Integer> previous) {
        if (candidate.isEmpty()) return Long.MAX_VALUE;
        List<Integer> current = candidate.stream().sorted().toList();
        List<Integer> old = previous.stream().sorted().toList();
        long cost = Math.abs(current.size() - old.size()) * 24L;
        if (current.size() == old.size()) {
            for (int index = 0; index < current.size(); index++) {
                cost += Math.abs(current.get(index) - old.get(index));
            }
        } else {
            for (int note : current) {
                int nearest = Integer.MAX_VALUE;
                for (int previousNote : old) {
                    nearest = Math.min(nearest, Math.abs(note - previousNote));
                }
                cost += nearest;
            }
        }
        cost += Math.abs(current.getFirst() - old.getFirst());
        return cost;
    }
}
