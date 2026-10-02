package m3e.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.AvoidLiteralsInIfCondition"})
final class SmartPlayChordSetCatalog {
    private static final String[][] SYMBOLS = {
            {"C", "Em", "F", "G", "Am", "Esus4", "Gadd9", "Dm", "Fadd9", "F6", "Gsus4", "G"},
            {"C", "C/B", "C/A", "C/G", "Fadd9", "Fm", "Csus4", "Am7", "Em", "F", "Gsus4", "G6"},
            {"C", "Am", "FMaj7", "Gsus4", "Amadd9", "F6", "Csus2", "G", "Dsus2", "Bbadd9", "Gsus4", "Fadd9"},
            {"C", "G", "Am", "F6", "F", "Am/E", "Dm7", "Am/C", "Gadd9", "Am7", "G/B", "Dm/G"},
            {"C", "D", "F", "C", "Ab", "Eb", "Bb", "F", "G7sus4", "Bbadd9", "F6", "Cadd9"},
            {"C", "G", "Dm", "Am", "F", "G/F", "C/E", "Am7", "Dm7", "Em7", "C/F", "Gsus4"},
            {"C", "G", "Am", "Em", "F", "C/E", "Dm", "Dm/C", "G7/B", "G", "F/A", "G7/B"},
            {"CMaj9", "C#dim", "Dm9", "D#dim7", "Em9", "C9#5", "FMaj7add13", "Bb9", "Em7", "A9", "Dm11", "G7(b9b13)"},
            {"Cm", "Cm/Eb", "Fm", "G", "AbMaj7", "Eb", "Gm", "Bb", "F", "Fm/Ab", "Cm/G", "G"},
            {"Cm", "G+/B", "Cm/Bb", "Cm/A", "AbMaj7", "EbMaj7", "Fm", "Bb7", "Cm", "Bbadd9", "Abadd9", "G7sus4"},
            {"Cm", "Ab", "Eb", "Bb", "F", "Fm", "Cm/G", "Gsus4", "Cm", "Cm#5", "Cm6", "Cm7"},
            {"Cm", "Eb", "Bb", "F", "Ab", "AbMaj7", "Abm7", "EbMaj7", "Dsus4", "D", "Fm/G", "G+"},
            {"Cm", "Bb", "Ab6", "Bbadd9", "Cm", "Dm7", "Cm/Eb", "Fm", "G", "AbMaj7", "B6/9", "Csus4"},
            {"Cm", "Fm", "Bb", "Eb", "Ab", "Dm7b5", "Gaddb9", "G", "Ab/G", "G7", "Cm/G", "G7b9"},
            {"Cm9", "Ab9", "Cm11", "C7(#9,b13)", "Fm9", "EbMaj7/F", "C11", "Am11", "Ab7#11", "G7#9", "Cmadd9", "G7(b9,b13)"},
            {"Cm6/9", "Dm7b5", "Cm11/G", "Cm9", "Fm9", "Abm7", "Ebmi7", "Bbm7b5", "Am11", "AbMaj7#5", "G7(b9,b13)", "Cm9Maj7"}
    };

    private SmartPlayChordSetCatalog() { }

    static String symbol(SmartPlaySettings.ChordSet set, int triggerPitchClass) {
        return SYMBOLS[set.ordinal()][Math.floorMod(triggerPitchClass, 12)];
    }

    static List<Integer> voicing(int input, int selectedRoot,
                                 SmartPlaySettings.ChordSet set) {
        int triggerClass = Math.floorMod(input - selectedRoot, 12);
        ChordSymbol chord = parse(symbol(set, triggerClass));
        int rootBase = input - triggerClass;
        int chordRoot = center(rootBase + chord.rootPitchClass(), input);
        if (chord.bassPitchClass() < 0) {
            return Arrays.stream(chord.intervals()).map(value -> chordRoot + value)
                    .boxed().toList();
        }

        int bass = center(rootBase + chord.bassPitchClass(), input);
        List<Integer> notes = new ArrayList<>();
        notes.add(bass);
        for (int interval : chord.intervals()) {
            int note = chordRoot + interval;
            while (note < bass) note += 12;
            if (Math.floorMod(note - bass, 12) == 0) continue;
            while (notes.contains(note)) note += 12;
            notes.add(note);
        }
        notes.sort(Integer::compareTo);
        return List.copyOf(notes);
    }

    private static ChordSymbol parse(String symbol) {
        String chord = symbol;
        int bassPitchClass = -1;
        int slash = chord.lastIndexOf('/');
        if (slash > 0 && isNoteName(chord.substring(slash + 1))) {
            bassPitchClass = pitchClass(chord.substring(slash + 1));
            chord = chord.substring(0, slash);
        }

        int rootLength = chord.length() > 1
                && (chord.charAt(1) == '#' || chord.charAt(1) == 'b') ? 2 : 1;
        int rootPitchClass = pitchClass(chord.substring(0, rootLength));
        String quality = chord.substring(rootLength);
        int[] intervals = switch (quality) {
            case "" -> ints(0, 4, 7);
            case "m" -> ints(0, 3, 7);
            case "+" -> ints(0, 4, 8);
            case "dim" -> ints(0, 3, 6);
            case "dim7" -> ints(0, 3, 6, 9);
            case "sus2" -> ints(0, 2, 7);
            case "sus4" -> ints(0, 5, 7);
            case "6" -> ints(0, 4, 7, 9);
            case "m6" -> ints(0, 3, 7, 9);
            case "7" -> ints(0, 4, 7, 10);
            case "m7", "mi7" -> ints(0, 3, 7, 10);
            case "Maj7" -> ints(0, 4, 7, 11);
            case "9" -> ints(0, 4, 7, 10, 14);
            case "m9" -> ints(0, 3, 7, 10, 14);
            case "Maj9" -> ints(0, 4, 7, 11, 14);
            case "11" -> ints(0, 4, 7, 10, 14, 17);
            case "m11" -> ints(0, 3, 7, 10, 14, 17);
            case "add9" -> ints(0, 4, 7, 14);
            case "madd9" -> ints(0, 3, 7, 14);
            case "addb9" -> ints(0, 4, 7, 13);
            case "7sus4" -> ints(0, 5, 7, 10);
            case "6/9" -> ints(0, 4, 7, 9, 14);
            case "m6/9" -> ints(0, 3, 7, 9, 14);
            case "m7b5" -> ints(0, 3, 6, 10);
            case "m#5" -> ints(0, 3, 8);
            case "9#5" -> ints(0, 4, 8, 10, 14);
            case "Maj7add13" -> ints(0, 4, 7, 11, 21);
            case "7b9" -> ints(0, 4, 7, 10, 13);
            case "7#9" -> ints(0, 4, 7, 10, 15);
            case "7#11" -> ints(0, 4, 7, 10, 18);
            case "7(#9,b13)" -> ints(0, 4, 7, 10, 15, 20);
            case "7(b9b13)", "7(b9,b13)" -> ints(0, 4, 7, 10, 13, 20);
            case "Maj7#5" -> ints(0, 4, 8, 11);
            case "m9Maj7" -> ints(0, 3, 7, 11, 14);
            default -> throw new IllegalArgumentException("Unsupported chord-set symbol: " + symbol);
        };
        return new ChordSymbol(rootPitchClass, bassPitchClass, intervals);
    }

    private static int center(int note, int input) {
        while (note - input > 6) note -= 12;
        while (input - note > 6) note += 12;
        return note;
    }

    private static boolean isNoteName(String value) {
        if (value.length() < 1 || value.length() > 2) return false;
        char letter = Character.toUpperCase(value.charAt(0));
        return letter >= 'A' && letter <= 'G'
                && (value.length() == 1 || value.charAt(1) == '#'
                || value.charAt(1) == 'b');
    }

    private static int pitchClass(String name) {
        int value = switch (Character.toUpperCase(name.charAt(0))) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            case 'B' -> 11;
            default -> throw new IllegalArgumentException("Invalid chord note: " + name);
        };
        if (name.length() > 1) value += name.charAt(1) == '#' ? 1 : -1;
        return Math.floorMod(value, 12);
    }

    private static int[] ints(int... values) { return values; }

    private record ChordSymbol(int rootPitchClass, int bassPitchClass,
                               int[] intervals) { }
}
