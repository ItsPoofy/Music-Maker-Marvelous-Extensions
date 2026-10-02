package m3e.client;

/** Immutable, validated state for Smart Play scale and chord assistance. */
public record SmartPlaySettings(Scale scale, Chord chord) {
    public static final int AUTO_POSITION = 99;
    public static final SmartPlaySettings DEFAULT = new SmartPlaySettings(
            Scale.DEFAULT, Chord.DEFAULT);

    public SmartPlaySettings {
        scale = scale == null ? Scale.DEFAULT : scale;
        chord = chord == null ? Chord.DEFAULT : chord;
    }

    public SmartPlaySettings withScale(Scale value) {
        return new SmartPlaySettings(value, chord);
    }

    public SmartPlaySettings withChord(Chord value) {
        return new SmartPlaySettings(scale, value);
    }

    public boolean routesNotes() {
        // Chord is a page of the Scale engine, not an independently active engine.
        return scale.enabled();
    }

    public record Scale(boolean enabled, int root, ScaleBank bank, int type,
                        KeyMode keyMode) {
        public static final Scale DEFAULT = new Scale(false, 0, ScaleBank.MAIN, 1,
                KeyMode.MAPPED);

        public Scale {
            root = Math.floorMod(root, 12);
            bank = bank == null ? ScaleBank.MAIN : bank;
            type = Math.clamp(type, 0, 14);
            keyMode = keyMode == null ? KeyMode.MAPPED : keyMode;
        }

        public Scale withEnabled(boolean value) {
            return new Scale(value, root, bank, type, keyMode);
        }
        public Scale withRoot(int value) {
            return new Scale(enabled, value, bank, type, keyMode);
        }
        public Scale withBank(ScaleBank value) {
            return new Scale(enabled, root, value, type, keyMode);
        }
        public Scale withType(int value) {
            return new Scale(enabled, root, bank, value, keyMode);
        }
        public Scale withKeyMode(KeyMode value) {
            return new Scale(enabled, root, bank, type, value);
        }
    }

    public record Chord(ChordMode mode, DiatonicHarmony diatonicHarmony,
                        ChromaticHarmony chromaticHarmony, ChordSet chordSet,
                        int position) {
        public static final Chord DEFAULT = new Chord(ChordMode.OFF,
                DiatonicHarmony.ONE_THREE_FIVE, ChromaticHarmony.MAJOR,
                ChordSet.MAJ_1, 0);

        public Chord {
            mode = mode == null ? ChordMode.OFF : mode;
            diatonicHarmony = diatonicHarmony == null
                    ? DiatonicHarmony.ONE_THREE_FIVE : diatonicHarmony;
            chromaticHarmony = chromaticHarmony == null
                    ? ChromaticHarmony.MAJOR : chromaticHarmony;
            chordSet = chordSet == null ? ChordSet.MAJ_1 : chordSet;
            position = position == AUTO_POSITION ? AUTO_POSITION
                    : Math.clamp(position, -8, 8);
        }

        public Chord withMode(ChordMode value) {
            return new Chord(value, diatonicHarmony, chromaticHarmony, chordSet, position);
        }
        public Chord withDiatonic(DiatonicHarmony value) {
            return new Chord(mode, value, chromaticHarmony, chordSet, position);
        }
        public Chord withChromatic(ChromaticHarmony value) {
            return new Chord(mode, diatonicHarmony, value, chordSet, position);
        }
        public Chord withChordSet(ChordSet value) {
            return new Chord(mode, diatonicHarmony, chromaticHarmony, value, position);
        }
        public Chord withPosition(int value) {
            return new Chord(mode, diatonicHarmony, chromaticHarmony, chordSet, value);
        }
    }

    public enum ScaleBank {
        MAIN("Main"), MODES("Modes"), JAZZ("Jazz"), WORLD("World"),
        FIVE_TONE("5-Tone"), MODERN("Modern"), MAJOR("Major"), MINOR("Minor");
        private final String label;
        ScaleBank(String label) { this.label = label; }
        public String label() { return label; }
        public ScaleBank next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum KeyMode {
        GUIDE("Guide"), MAPPED("Mapped"), EASY("Easy");
        private final String label;
        KeyMode(String label) { this.label = label; }
        public String label() { return label; }
        public KeyMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum ChordMode {
        OFF("Off"), HARMONIZER("Harmonizer"), CHORD_SET("Chord Set");
        private final String label;
        ChordMode(String label) { this.label = label; }
        public String label() { return label; }
        public ChordMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum DiatonicHarmony {
        OCTAVE("Octave", 0, 7), ONE_THREE("1-3", 0, 2),
        ONE_FIVE("1-5", 0, 4), ONE_THREE_FIVE("1-3-5", 0, 2, 4),
        ONE_FOUR_FIVE("1-4-5", 0, 3, 4),
        ONE_THREE_FIVE_SEVEN("1-3-5-7", 0, 2, 4, 6),
        ONE_FOUR_SEVEN("1-4-7", 0, 3, 6);
        private final String label;
        private final int[] degrees;
        DiatonicHarmony(String label, int... degrees) {
            this.label = label;
            this.degrees = degrees;
        }
        public String label() { return label; }
        public int[] degrees() { return degrees.clone(); }
        public DiatonicHarmony next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum ChromaticHarmony {
        OCTAVE("Octave", 0, 12), PERFECT_FOURTH("Perfect 4th", 0, 5),
        PERFECT_FIFTH("Perfect 5th", 0, 7), MAJOR("Major", 0, 4, 7),
        MINOR("Minor", 0, 3, 7), SUS_FOUR("Sus 4", 0, 5, 7),
        MAJOR_SEVEN("Major 7", 0, 4, 7, 11), MINOR_SEVEN("Minor 7", 0, 3, 7, 10),
        DOMINANT_SEVEN("Dominant 7", 0, 4, 7, 10),
        DOMINANT_NINE("Dominant 9", 0, 4, 7, 10, 14),
        MINOR_SEVEN_FLAT_FIVE("Minor 7 b5", 0, 3, 6, 10),
        DIMINISHED_SEVEN("Diminished 7", 0, 3, 6, 9),
        AUGMENTED("Augmented", 0, 4, 8), QUARTAL("Quartal", 0, 5, 10, 15),
        TRICHORD("Trichord", 0, 5, 11);
        private final String label;
        private final int[] semitones;
        ChromaticHarmony(String label, int... semitones) {
            this.label = label;
            this.semitones = semitones;
        }
        public String label() { return label; }
        public int[] semitones() { return semitones.clone(); }
        public ChromaticHarmony next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum ChordSet {
        MAJ_1("Maj 1", true, 1), MAJ_2("Maj 2", true, 2),
        MAJ_3("Maj 3", true, 3), MAJ_4("Maj 4", true, 4),
        MAJ_5("Maj 5", true, 5), MAJ_6("Maj 6", true, 6),
        MAJ_7("Maj 7", true, 7), MAJ_8("Maj 8", true, 8),
        MIN_1("Min 1", false, 1), MIN_2("Min 2", false, 2),
        MIN_3("Min 3", false, 3), MIN_4("Min 4", false, 4),
        MIN_5("Min 5", false, 5), MIN_6("Min 6", false, 6),
        MIN_7("Min 7", false, 7), MIN_8("Min 8", false, 8);
        private final String label;
        private final boolean major;
        private final int variant;
        ChordSet(String label, boolean major, int variant) {
            this.label = label;
            this.major = major;
            this.variant = variant;
        }
        public String label() { return label; }
        public boolean major() { return major; }
        public int variant() { return variant; }
        public ChordSet next() { return values()[(ordinal() + 1) % values().length]; }
    }
}
