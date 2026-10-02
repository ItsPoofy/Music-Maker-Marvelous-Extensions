package m3e.session;

/** Enharmonic spelling shared by local and server-authoritative chord displays. */
public final class NoteNames {
    private static final String[] SHARPS =
            {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] FLATS =
            {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    private NoteNames() { }

    public static String pitchClass(int pitchClass, Spelling spelling) {
        return (spelling == Spelling.FLATS ? FLATS : SHARPS)[Math.floorMod(pitchClass, 12)];
    }

    public static String chord(DetectedChord chord, Spelling spelling) {
        if (chord == null) return "—";
        String result = pitchClass(chord.root(), spelling) + " " + chord.quality().suffix();
        int bass = Math.floorMod(chord.bassNote(), 12);
        return bass == chord.root() ? result : result + "/" + pitchClass(bass, spelling);
    }

    public enum Spelling {
        SHARPS("Sharps"), FLATS("Flats");

        private final String label;
        Spelling(String label) { this.label = label; }
        public String label() { return label; }
        public Spelling next() { return this == SHARPS ? FLATS : SHARPS; }
    }
}
