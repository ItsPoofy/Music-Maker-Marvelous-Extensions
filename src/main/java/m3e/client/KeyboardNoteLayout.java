package m3e.client;

import m3e.session.NoteNames;

/** Pure note mapping shared by the typing-keyboard implementation and tests. */
public final class KeyboardNoteLayout {
    private static final int C_NOTE_ID_OFFSET = 3;
    private static final int NOTES_PER_OCTAVE = 12;
    private static final int FIRST_MIDI_NOTE = 21; // A0

    private static final int[][] C_OCTAVE_COLORS = {
            {0xFF331A00, 0xFF442300, 0xFF562C00, 0xFF683600, 0xFF7A3F00, 0xFF8C4800,
                    0xFF9E5100, 0xFFAF5E07, 0xFFA8621C, 0xFFAD7130, 0xFFAA7C4B, 0xFFA88059},
            {0xFF300000, 0xFF520000, 0xFF700000, 0xFF8F0000, 0xFFAD0000, 0xFFC00000,
                    0xFFEB0000, 0xFFFF0A0A, 0xFFFF2929, 0xFFFF4747, 0xFFFF7070, 0xFFFF8585},
            {0xFF003000, 0xFF005200, 0xFF007000, 0xFF008F00, 0xFF00AD00, 0xFF00C000,
                    0xFF00EB00, 0xFF0AFF0A, 0xFF29FF29, 0xFF47FF47, 0xFF66FF66, 0xFF85FF85},
            {0xFF000030, 0xFF000052, 0xFF000070, 0xFF00008F, 0xFF0000AD, 0xFF0000C0,
                    0xFF0000EB, 0xFF0A0AFF, 0xFF1429FF, 0xFF2847FF, 0xFF4666FF, 0xFF6585FF},
            {0xFF190033, 0xFF290052, 0xFF380070, 0xFF47008F, 0xFF5700AD, 0xFF6600CC,
                    0xFF7500EB, 0xFF850AFF, 0xFF9429FF, 0xFFA347FF, 0xFFB266FF, 0xFFC285FF},
            {0xFF2D0023, 0xFF51003F, 0xFF6D0055, 0xFF8E006D, 0xFFAA0082, 0xFFBF0092,
                    0xFFEA00B3, 0xFFFF0AC5, 0xFFFF2BCD, 0xFFFF47D4, 0xFFFF70DD, 0xFFFF87E3},
            {0xFF002D29, 0xFF005149, 0xFF006D62, 0xFF008E82, 0xFF00AA9C, 0xFF00BFB2,
                    0xFF00EADA, 0xFF0AFFF6, 0xFF35FFF8, 0xFF5BFFF9, 0xFF8CFFFB, 0xFFA0FFFD},
            {0xFF2D2800, 0xFF514800, 0xFF6D6200, 0xFF8E8000, 0xFFAA9900, 0xFFBFAC00,
                    0xFFEAD300, 0xFFFFEA0A, 0xFFFFED2B, 0xFFFFF247, 0xFFFFF570, 0xFFFFF787}
    };

    private KeyboardNoteLayout() { }

    /**
     * Converts Music Maker's original MIDI-controller octave into the octave
     * displayed by a keyboard whose first visible note may be cropped upward.
     * A compact C2-C7 keyboard starts at note id 15, so MIDI octave zero is
     * still untransposed while its first complete visible octave is one.
     */
    public static int displayOctave(int midiOctave, int firstVisibleNoteId) {
        return midiOctave + Math.floorDiv(Math.max(0, firstVisibleNoteId), NOTES_PER_OCTAVE);
    }

    public static int midiOctave(int displayOctave, int firstVisibleNoteId) {
        return displayOctave - Math.floorDiv(Math.max(0, firstVisibleNoteId), NOTES_PER_OCTAVE);
    }

    /**
     * Maps one of Full Keyboard's three C-based rows. The octave-origin color
     * option deliberately does not affect this layout.
     */
    public static int fullKeyboardNoteId(int highlightedOctave, int rowOffset,
                                         int chromaticDegree, int totalNotes) {
        if (chromaticDegree < 0 || chromaticDegree >= NOTES_PER_OCTAVE) return -1;
        int noteId = C_NOTE_ID_OFFSET
                + NOTES_PER_OCTAVE * (highlightedOctave + rowOffset)
                + chromaticDegree;
        return noteId >= 0 && noteId < totalNotes ? noteId : -1;
    }

    /**
     * C-anchored offset used only by the M3E's new keyboard UI. Physical
     * note id zero remains A0, so C is id 3 and B is id 14. This intentionally
     * lets G# -> A advance normally and wraps only from B -> C.
     */
    public static int originOffset(int originPitchClass) {
        return C_NOTE_ID_OFFSET + Math.floorMod(originPitchClass, NOTES_PER_OCTAVE);
    }

    /** Unvalidated origin used while a typing-key degree still needs to be added. */
    public static int originBaseNoteId(int highlightedOctave, int originPitchClass) {
        return NOTES_PER_OCTAVE * highlightedOctave + originOffset(originPitchClass);
    }

    /**
     * Maps origin-relative typing rows without clamping the selected octave.
     * A negative endpoint octave is intentional: it exposes only the real
     * A/B notes hidden below a C-based first visual block, while still
     * rejecting pitches outside Music Maker's actual 96-note range.
     */
    public static int originBasedNoteId(int highlightedOctave,
                                        int originPitchClass,
                                        int chromaticDegree,
                                        int totalNotes) {
        int noteId = originBaseNoteId(highlightedOctave, originPitchClass) + chromaticDegree;
        return noteId >= 0 && noteId < totalNotes ? noteId : -1;
    }

    /** The on-screen keyboard is always the physical A0-up Music Maker range. */
    public static int visualSlotToNoteId(int visualSlot, int totalNotes) {
        return visualSlot >= 0 && visualSlot < totalNotes ? visualSlot : -1;
    }

    /** Reverse identity mapping used for pressed-note overlays. */
    public static int noteIdToVisualSlot(int noteId, int totalNotes) {
        return noteId >= 0 && noteId < totalNotes ? noteId : -1;
    }

    /**
     * Clips an origin-relative twelve-note selection to the physical keyboard.
     * Index -1 is meaningful only when the chosen origin leaves a lower A0-B0
     * fragment; index 7 similarly exposes the available high fragment.
     */
    public static SelectionWindow selectionWindow(int highlightedOctave,
                                                   int originPitchClass,
                                                   int totalNotes) {
        int rawStart = NOTES_PER_OCTAVE * highlightedOctave
                + originOffset(originPitchClass);
        int from = Math.max(0, rawStart);
        int to = Math.min(totalNotes, rawStart + NOTES_PER_OCTAVE);
        return from < to ? new SelectionWindow(from, to)
                : SelectionWindow.EMPTY;
    }

    /** Clips a selected octave to the instrument's actual non-gray octave band. */
    public static SelectionWindow playableSelectionWindow(int highlightedOctave,
                                                           int originPitchClass,
                                                           int totalNotes,
                                                           int minimumOctave,
                                                           int maximumOctave) {
        SelectionWindow selected = selectionWindow(highlightedOctave,
                originPitchClass, totalNotes);
        if (selected.isEmpty() || minimumOctave > maximumOctave) return SelectionWindow.EMPTY;
        int playableFrom = Math.clamp(minimumOctave * NOTES_PER_OCTAVE, 0, totalNotes);
        int playableTo = Math.clamp((maximumOctave + 1) * NOTES_PER_OCTAVE, 0, totalNotes);
        int from = Math.max(selected.fromInclusive(), playableFrom);
        int to = Math.min(selected.toExclusive(), playableTo);
        return from < to ? new SelectionWindow(from, to) : SelectionWindow.EMPTY;
    }

    /**
     * Position of a visible edge fragment within its logical twelve-note
     * selection. This lets rendering crop the original highlight in note
     * units instead of scaling and visibly squashing its artwork.
     */
    public static int selectionOffset(int highlightedOctave, int originPitchClass,
                                      int visibleFromNoteId) {
        int rawStart = NOTES_PER_OCTAVE * highlightedOctave
                + originOffset(originPitchClass);
        return Math.clamp(visibleFromNoteId - rawStart, 0, NOTES_PER_OCTAVE - 1);
    }

    /**
     * Returns the permanent C-anchored color for a physical note slot. A0,
     * A#0 and B0 use a half-bright continuation of the final three shades in
     * the first normal octave gradient.
     */
    public static int visualColor(int noteId, int totalNotes) {
        if (noteId < 0 || noteId >= totalNotes) return 0;
        if (noteId < C_NOTE_ID_OFFSET) {
            return darken(C_OCTAVE_COLORS[0][9 + noteId]);
        }
        int cRelative = noteId - C_NOTE_ID_OFFSET;
        int octave = Math.floorDiv(cRelative, NOTES_PER_OCTAVE);
        int degree = Math.floorMod(cRelative, NOTES_PER_OCTAVE);
        octave = Math.clamp(octave, 0, C_OCTAVE_COLORS.length - 1);
        return C_OCTAVE_COLORS[octave][degree];
    }

    public static String noteName(int noteId, NoteNames.Spelling spelling) {
        int midiNote = FIRST_MIDI_NOTE + noteId;
        return NoteNames.pitchClass(midiNote, spelling)
                + (Math.floorDiv(midiNote, NOTES_PER_OCTAVE) - 1);
    }

    private static int darken(int color) {
        int red = (color >>> 16 & 0xFF) / 2;
        int green = (color >>> 8 & 0xFF) / 2;
        int blue = (color & 0xFF) / 2;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    public record SelectionWindow(int fromInclusive, int toExclusive) {
        private static final SelectionWindow EMPTY = new SelectionWindow(-1, -1);

        public boolean isEmpty() {
            return fromInclusive < 0 || toExclusive <= fromInclusive;
        }
    }
}
