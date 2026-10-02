package m3e.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class SmartPlayScaleCatalog {
    private static final Map<SmartPlaySettings.ScaleBank, List<ScaleDefinition>> BANKS = build();

    private SmartPlayScaleCatalog() { }

    public static List<ScaleDefinition> bank(SmartPlaySettings.ScaleBank bank) {
        return BANKS.get(bank == null ? SmartPlaySettings.ScaleBank.MAIN : bank);
    }

    public static ScaleDefinition scale(SmartPlaySettings.Scale settings) {
        List<ScaleDefinition> choices = bank(settings.bank());
        return choices.get(Math.clamp(settings.type(), 0, choices.size() - 1));
    }

    public static int bankCount() { return BANKS.size(); }

    public record ScaleDefinition(String label, int[] intervals) {
        public ScaleDefinition {
            label = label == null || label.isBlank() ? "Scale" : label;
            intervals = intervals == null ? new int[]{0} : intervals.clone();
        }

        @Override public int[] intervals() { return intervals.clone(); }

        public boolean chromatic() {
            if (intervals.length != 12) return false;
            for (int index = 0; index < 12; index++) if (intervals[index] != index) return false;
            return true;
        }
    }

    private static Map<SmartPlaySettings.ScaleBank, List<ScaleDefinition>> build() {
        EnumMap<SmartPlaySettings.ScaleBank, List<ScaleDefinition>> banks =
                new EnumMap<>(SmartPlaySettings.ScaleBank.class);
        banks.put(SmartPlaySettings.ScaleBank.MAIN, List.of(
                s("Chromatic", 0,1,2,3,4,5,6,7,8,9,10,11),
                s("Major", 0,2,4,5,7,9,11), s("Minor", 0,2,3,5,7,8,10),
                s("Harm Min", 0,2,3,5,7,8,11), s("Maj Pent", 0,2,4,7,9),
                s("Min Pent", 0,3,5,7,10), s("Blues", 0,3,5,6,7,10),
                s("Japanese", 0,2,3,7,8), s("Freygish", 0,1,4,5,7,8,10),
                s("Hungarian Min", 0,2,3,6,7,8,11), s("Arabic", 0,1,4,5,7,8,11),
                s("Altered", 0,1,3,4,6,8,10), s("Whole Tone", 0,2,4,6,8,10),
                s("H-W Dim", 0,1,3,4,6,7,9,10), s("W-H Dim", 0,2,3,5,6,8,9,11)));
        banks.put(SmartPlaySettings.ScaleBank.MODES, List.of(
                s("Ionian", 0,2,4,5,7,9,11), s("Dorian", 0,2,3,5,7,9,10),
                s("Phrygian", 0,1,3,5,7,8,10), s("Lydian", 0,2,4,6,7,9,11),
                s("Mixolydian", 0,2,4,5,7,9,10), s("Aeolian", 0,2,3,5,7,8,10),
                s("Locrian", 0,1,3,5,6,8,10), s("Ionian b2", 0,1,4,5,7,9,11),
                s("Dorian b5", 0,2,3,5,6,9,10), s("Harm Phryg", 0,1,3,5,7,8,11),
                s("Phryg Major", 0,1,3,5,7,9,11), s("Lydian b3", 0,2,3,6,7,9,11),
                s("Major Locrian", 0,2,4,5,6,8,10), s("Minor Locrian", 0,2,3,5,6,8,10),
                s("Super Locrian", 0,1,3,4,6,8,10)));
        banks.put(SmartPlaySettings.ScaleBank.JAZZ, List.of(
                s("Lydian b7", 0,2,4,6,7,9,10), s("Altered", 0,1,3,4,6,8,10),
                s("Diminished", 0,1,3,4,6,7,9,10), s("Mixo b13", 0,2,4,5,7,8,10),
                s("Mixo b9 b13", 0,1,4,5,7,8,10), s("Lydian b7 b2", 0,1,4,6,7,9,10),
                s("Bebop", 0,2,4,5,7,9,10,11), s("Whole Tone", 0,2,4,6,8,10),
                s("Blues Maj", 0,2,3,4,7,9), s("Blues Min", 0,3,5,6,7,10),
                s("Blues Combined", 0,2,3,4,5,6,7,9,10), s("Lydian #5", 0,2,4,6,8,9,11),
                s("Jazz Minor", 0,2,3,5,7,9,11), s("Half Dim", 0,2,3,5,6,8,10),
                s("Augmented", 0,3,4,7,8,11)));
        banks.put(SmartPlaySettings.ScaleBank.WORLD, List.of(
                s("Hung Min", 0,2,3,6,7,8,11), s("Hung Maj", 0,3,4,6,7,9,10),
                s("Neapolitan", 0,1,3,5,7,8,11), s("Spanish", 0,1,3,4,5,7,8,10),
                s("Greek", 0,2,3,4,7,8,10), s("Jewish 1", 0,1,4,5,7,8,10),
                s("Jewish 2", 0,2,3,6,7,9,10), s("Indian 1", 0,1,3,6,7,8,11),
                s("Indian 2", 0,2,3,6,7,9,11), s("Indian 3", 0,1,2,5,7,8,9),
                s("Indian 4", 0,3,4,5,7,10,11), s("Mid East 1", 0,1,4,5,7,8,11),
                s("Mid East 2", 0,1,4,5,6,8,11), s("Mid East 3", 0,1,3,5,6,9,10),
                s("Mid East 4", 0,1,4,5,6,9,10)));
        banks.put(SmartPlaySettings.ScaleBank.FIVE_TONE, List.of(
                s("Penta I", 0,2,4,7,9), s("Penta II", 0,2,5,7,10),
                s("Penta III", 0,3,5,8,10), s("Penta IV", 0,2,5,7,9),
                s("Penta V", 0,3,5,7,10), s("Hirajoshi", 0,2,3,7,8),
                s("Insen", 0,1,5,7,10), s("Kokin Joshi", 0,2,5,7,8),
                s("Akebono", 0,2,3,7,9), s("Ryukuan", 0,4,5,7,11),
                s("Abhogi", 0,2,3,5,9), s("Bhupkali", 0,2,4,7,8),
                s("Hindolam", 0,3,5,8,10), s("Bhupalam", 0,1,3,7,8),
                s("Amritavarshini", 0,4,6,7,11)));
        banks.put(SmartPlaySettings.ScaleBank.MODERN, List.of(
                s("Octatonic", 0,2,3,5,6,8,9,11), s("Acoustic", 0,2,4,6,7,9,10),
                s("Augmented", 0,3,4,7,8,11), s("Tritone", 0,1,4,6,7,10),
                s("Leading Wh Tone", 0,2,4,6,8,10,11), s("Enigmatic", 0,1,4,6,8,10,11),
                s("Scriabin", 0,2,4,6,9,10), s("Tcherepnin", 0,1,3,4,5,7,8,9,11),
                s("Messiaen I", 0,2,4,6,8,10), s("Messiaen II", 0,1,3,4,6,7,9,10),
                s("Messiaen III", 0,2,3,4,6,7,8,10,11), s("Messiaen IV", 0,1,2,5,6,7,8,11),
                s("Messiaen V", 0,1,5,6,7,11), s("Messiaen VI", 0,2,4,5,6,8,10,11),
                s("Messiaen VII", 0,1,2,3,5,6,7,8,9,11)));
        banks.put(SmartPlaySettings.ScaleBank.MAJOR, List.of(
                s("Natural", 0,2,4,5,7,9,11), s("Lydian", 0,2,4,6,7,9,11),
                s("Mixolydian", 0,2,4,5,7,9,10), s("Major Minor", 0,2,4,5,7,8,10),
                s("Harmonic Major", 0,2,4,5,7,8,11), s("Dbl Har Major", 0,1,4,5,7,8,11),
                s("Neapolitan Maj", 0,1,4,5,7,9,11), s("Major Locrian", 0,2,4,5,6,8,10),
                s("Blues Major", 0,2,3,4,7,9), s("Bebop Major", 0,2,4,5,7,8,9,11),
                s("Hexa 1", 0,2,4,7,9,11), s("Hexa 2", 0,2,4,5,7,9),
                s("Penta 1", 0,2,4,7,9), s("Penta 2", 0,4,5,7,11),
                s("Penta 3", 0,4,7,9,11)));
        banks.put(SmartPlaySettings.ScaleBank.MINOR, List.of(
                s("Natural", 0,2,3,5,7,8,10), s("Dorian", 0,2,3,5,7,9,10),
                s("Phrygian", 0,1,3,5,7,8,10), s("Minor Major", 0,2,3,5,7,9,11),
                s("Harmonic Minor", 0,2,3,5,7,8,11), s("Dbl Har Minor", 0,2,3,6,7,8,11),
                s("Neapolitan Min", 0,1,3,5,7,8,11), s("Minor Locrian", 0,2,3,5,6,8,10),
                s("Blues Minor", 0,3,5,6,7,10), s("Bebop Minor", 0,2,3,5,7,8,10,11),
                s("Hexa 1", 0,2,3,7,8,10), s("Hexa 2", 0,2,3,5,7,8),
                s("Penta 1", 0,2,3,7,8), s("Penta 2", 0,3,5,7,10),
                s("Penta 3", 0,3,7,8,10)));
        return Map.copyOf(banks);
    }

    private static ScaleDefinition s(String label, int... intervals) {
        return new ScaleDefinition(label, intervals);
    }
}
