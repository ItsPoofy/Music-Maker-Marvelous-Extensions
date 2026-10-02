package m3e.session;

/** A stable chord snapshot keeps harmonic root and the player’s actual bass independent. */
public record DetectedChord(int root, ChordQuality quality, int bassNote, int confidence) {
    public DetectedChord { root = Math.floorMod(root, 12); bassNote = Math.clamp(bassNote, 21, 117); }
    public String display() { return NoteNames.chord(this, NoteNames.Spelling.SHARPS); }
    public String display(NoteNames.Spelling spelling) { return NoteNames.chord(this, spelling); }
}
