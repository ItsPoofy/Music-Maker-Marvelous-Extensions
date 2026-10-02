package m3e.client;

import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure physical-key mapping for every M3E instrument keyboard layout.
 * The GUI mixin owns press/release routing only; pitch and consumed-key rules
 * live here so they can be exhaustively verified without opening a screen.
 */
public final class KeyboardKeyMap {
    private static final int[] MAJOR_SCALE = {0, 2, 4, 5, 7, 9, 11};

    private KeyboardKeyMap() { }

    public static int octaveShift(InstrumentOptions.KeyboardLayout layout, int key) {
        return octaveShift(layout, key, 0,
                PianoKeyboardGeometry.STANDARD_FIRST_NOTE_ID,
                PianoKeyboardGeometry.STANDARD_LAST_NOTE_ID + 1);
    }

    public static int octaveShift(InstrumentOptions.KeyboardLayout layout, int key,
                                  int modifiers, int firstNoteId, int noteLimit) {
        KeyboardChord chord = KeyboardChord.of(key, modifiers);
        return switch (layout == null ? InstrumentOptions.KeyboardLayout.NORMAL : layout) {
            case NORMAL -> key == GLFW.GLFW_KEY_A ? -1 : key == GLFW.GLFW_KEY_S ? 1 : 0;
            case ALTERNATIVE, GENSHIN -> key == GLFW.GLFW_KEY_O ? -1
                    : key == GLFW.GLFW_KEY_P ? 1 : 0;
            case FULL -> key == GLFW.GLFW_KEY_Z ? -1 : key == GLFW.GLFW_KEY_X ? 1 : 0;
            case VISUAL_PIANO -> 0;
            case CUSTOM -> customOctaveShift(InstrumentOptions.customKeyboardPreset(),
                    chord, firstNoteId, noteLimit);
        };
    }

    static int customOctaveShift(KeyboardCustomPreset custom, KeyboardChord chord,
                                 int firstNoteId, int noteLimit) {
        if (custom == null || custom.coversEveryOctave(firstNoteId, noteLimit)) return 0;
        return chord != null && chord.equals(custom.octaveDown()) ? -1
                : chord != null && chord.equals(custom.octaveUp()) ? 1 : 0;
    }

    /** One source of truth for live note/control routing in every layout. */
    public static ResolvedBinding resolve(InstrumentOptions.KeyboardLayout layout,
                                          int key, int modifiers,
                                          int highlightedOctave, int originPitchClass,
                                          int firstNoteId, int noteLimit,
                                          boolean leftSplit) {
        InstrumentOptions.KeyboardLayout selected = layout == null
                ? InstrumentOptions.KeyboardLayout.NORMAL : layout;
        int noteId;
        switch (selected) {
            case VISUAL_PIANO -> {
                Integer fixed = inverse(visualPianoBindings(firstNoteId, noteLimit))
                        .get(KeyboardChord.of(key, modifiers));
                return fixed == null ? null : new ResolvedBinding(fixed, false);
            }
            case CUSTOM -> {
                return resolveCustom(InstrumentOptions.customKeyboardPreset(),
                        KeyboardChord.of(key, modifiers), highlightedOctave,
                        firstNoteId, noteLimit);
            }
            case GENSHIN -> {
                int control = leftSplit ? genshinControlNote(key) : -1;
                if (control >= 0) return new ResolvedBinding(control, true);
                noteId = genshinMelodyNote(key, highlightedOctave,
                        originPitchClass, noteLimit);
            }
            case ALTERNATIVE -> {
                int control = leftSplit ? alternativeControlNote(key) : -1;
                if (control >= 0) return new ResolvedBinding(control, true);
                noteId = alternativeMelodyNote(key, modifiers, highlightedOctave,
                        originPitchClass, noteLimit);
            }
            case FULL -> {
                int controlDegree = leftSplit ? fullKeyboardBottomDegree(key) : -1;
                if (controlDegree >= 0) return new ResolvedBinding(36 + controlDegree, true);
                noteId = fullKeyboardNoteId(key, highlightedOctave, noteLimit);
            }
            case NORMAL -> {
                int degree = numberRowDegree(key);
                if (degree >= 0) {
                    if (leftSplit) return new ResolvedBinding(36 + degree, true);
                    noteId = originNoteId(highlightedOctave, originPitchClass, noteLimit)
                            + 12 + degree;
                } else {
                    degree = normalRowDegree(key);
                    if (degree < 0) return null;
                    noteId = originNoteId(highlightedOctave, originPitchClass, noteLimit) + degree;
                }
            }
            default -> throw new IllegalStateException("Unhandled keyboard layout " + selected);
        }
        return noteId >= firstNoteId && noteId < noteLimit
                ? new ResolvedBinding(noteId, false) : null;
    }

    /** Note labels use the exact same resolver as live input. */
    public static Map<Integer, KeyboardChord> displayBindings(
            InstrumentOptions.KeyboardLayout layout, int highlightedOctave,
            int originPitchClass, int firstNoteId, int noteLimit) {
        InstrumentOptions.KeyboardLayout selected = layout == null
                ? InstrumentOptions.KeyboardLayout.NORMAL : layout;
        if (selected == InstrumentOptions.KeyboardLayout.VISUAL_PIANO) {
            return visualPianoBindings(firstNoteId, noteLimit);
        }
        if (selected == InstrumentOptions.KeyboardLayout.CUSTOM) {
            return displayCustomBindings(InstrumentOptions.customKeyboardPreset(),
                    highlightedOctave, firstNoteId, noteLimit);
        }
        LinkedHashMap<Integer, KeyboardChord> labels = new LinkedHashMap<>();
        int[] modifiers = {0, GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_MOD_CONTROL,
                GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL};
        for (int modifier : modifiers) {
            for (int key : PERFORMANCE_KEYS) {
                ResolvedBinding resolved = resolve(selected, key, modifier,
                        highlightedOctave, originPitchClass, firstNoteId, noteLimit, false);
                if (resolved != null && !resolved.control()) {
                    labels.putIfAbsent(resolved.noteId(), KeyboardChord.of(key, modifier));
                }
            }
        }
        return Map.copyOf(labels);
    }

    static ResolvedBinding resolveCustom(KeyboardCustomPreset custom,
                                         KeyboardChord chord,
                                         int highlightedOctave,
                                         int firstNoteId, int noteLimit) {
        if (custom == null || chord == null) return null;
        Integer fixed = custom.noteFor(chord);
        if (fixed == null || fixed < firstNoteId || fixed >= noteLimit) return null;
        if (!custom.coversEveryOctave(firstNoteId, noteLimit)
                && KeyboardCustomPreset.octavePage(fixed) != highlightedOctave + 1) return null;
        return new ResolvedBinding(fixed, false);
    }

    static Map<Integer, KeyboardChord> displayCustomBindings(
            KeyboardCustomPreset custom, int highlightedOctave,
            int firstNoteId, int noteLimit) {
        if (custom == null) return Map.of();
        boolean fixedRange = custom.coversEveryOctave(firstNoteId, noteLimit);
        LinkedHashMap<Integer, KeyboardChord> visible = new LinkedHashMap<>();
        custom.notes().forEach((note, chord) -> {
            if (note >= firstNoteId && note < noteLimit
                    && (fixedRange || KeyboardCustomPreset.octavePage(note)
                    == highlightedOctave + 1)) {
                visible.put(note, chord);
            }
        });
        return Map.copyOf(visible);
    }

    /** Canonical Visual Piano: 61 center keys, plus Ctrl edge extensions for 88 keys. */
    public static Map<Integer, KeyboardChord> visualPianoBindings(int firstNoteId, int noteLimit) {
        LinkedHashMap<Integer, KeyboardChord> compact = new LinkedHashMap<>();
        int white = 0;
        int black = 0;
        for (int note = PianoKeyboardGeometry.COMPACT_FIRST_NOTE_ID;
             note <= PianoKeyboardGeometry.COMPACT_LAST_NOTE_ID; note++) {
            compact.put(note, PianoKeyboardGeometry.isBlack(note)
                    ? KeyboardChord.of(VISUAL_BLACK_KEYS[black++], GLFW.GLFW_MOD_SHIFT)
                    : KeyboardChord.of(VISUAL_WHITE_KEYS[white++], 0));
        }
        LinkedHashMap<Integer, KeyboardChord> full = new LinkedHashMap<>(compact);
        KeyboardChord[] pattern = compact.values().toArray(KeyboardChord[]::new);
        for (int note = PianoKeyboardGeometry.STANDARD_FIRST_NOTE_ID;
             note < PianoKeyboardGeometry.COMPACT_FIRST_NOTE_ID; note++) {
            KeyboardChord source = pattern[note - PianoKeyboardGeometry.STANDARD_FIRST_NOTE_ID];
            full.put(note, KeyboardChord.of(source.key(),
                    source.modifiers() | GLFW.GLFW_MOD_CONTROL));
        }
        for (int note = PianoKeyboardGeometry.COMPACT_LAST_NOTE_ID + 1;
             note <= PianoKeyboardGeometry.STANDARD_LAST_NOTE_ID; note++) {
            int patternIndex = 15 + note - PianoKeyboardGeometry.COMPACT_LAST_NOTE_ID - 1;
            KeyboardChord source = pattern[patternIndex];
            full.put(note, KeyboardChord.of(source.key(),
                    source.modifiers() | GLFW.GLFW_MOD_CONTROL));
        }
        LinkedHashMap<Integer, KeyboardChord> result = new LinkedHashMap<>();
        full.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getKey() >= firstNoteId && entry.getKey() < noteLimit) {
                result.put(entry.getKey(), entry.getValue());
            }
        });
        return Map.copyOf(result);
    }

    /** Z-M plus A/S/F/G/H supplies the complete muted C2-B2 control octave. */
    public static int alternativeControlNote(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_Z -> 36; case GLFW.GLFW_KEY_A -> 37;
            case GLFW.GLFW_KEY_X -> 38; case GLFW.GLFW_KEY_S -> 39;
            case GLFW.GLFW_KEY_C -> 40; case GLFW.GLFW_KEY_V -> 41;
            case GLFW.GLFW_KEY_F -> 42; case GLFW.GLFW_KEY_B -> 43;
            case GLFW.GLFW_KEY_G -> 44; case GLFW.GLFW_KEY_N -> 45;
            case GLFW.GLFW_KEY_H -> 46; case GLFW.GLFW_KEY_M -> 47;
            default -> -1;
        };
    }

    public static int alternativeMelodyNote(int key, int modifiers,
                                            int highlightedOctave,
                                            int originPitchClass,
                                            int totalNotes) {
        int degree = switch (key) {
            case GLFW.GLFW_KEY_Q -> 0; case GLFW.GLFW_KEY_2 -> 1;
            case GLFW.GLFW_KEY_W -> 2; case GLFW.GLFW_KEY_3 -> 3;
            case GLFW.GLFW_KEY_E -> 4; case GLFW.GLFW_KEY_R -> 5;
            case GLFW.GLFW_KEY_5 -> 6; case GLFW.GLFW_KEY_T -> 7;
            case GLFW.GLFW_KEY_6 -> 8; case GLFW.GLFW_KEY_Y -> 9;
            case GLFW.GLFW_KEY_7 -> 10; case GLFW.GLFW_KEY_U -> 11;
            case GLFW.GLFW_KEY_I -> 12;
            default -> -1;
        };
        if (degree < 0) return -1;
        boolean up = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean down = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        int octaveOffset = up == down ? 0 : up ? 12 : -12;
        int noteId = originNoteId(highlightedOctave, originPitchClass, totalNotes)
                + degree + octaveOffset;
        return validNoteId(noteId, totalNotes) ? noteId : -1;
    }

    public static int fullKeyboardNoteId(int key, int highlightedOctave, int totalNotes) {
        FullKey position = fullKey(key);
        return position == null ? -1 : KeyboardNoteLayout.fullKeyboardNoteId(
                highlightedOctave, position.octaveOffset(), position.degree(), totalNotes);
    }

    public static int fullKeyboardBottomDegree(int key) {
        FullKey position = fullKey(key);
        return position != null && position.octaveOffset() == -1 ? position.degree() : -1;
    }

    public static int numberRowDegree(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_1 -> 0; case GLFW.GLFW_KEY_2 -> 1;
            case GLFW.GLFW_KEY_3 -> 2; case GLFW.GLFW_KEY_4 -> 3;
            case GLFW.GLFW_KEY_5 -> 4; case GLFW.GLFW_KEY_6 -> 5;
            case GLFW.GLFW_KEY_7 -> 6; case GLFW.GLFW_KEY_8 -> 7;
            case GLFW.GLFW_KEY_9 -> 8; case GLFW.GLFW_KEY_0 -> 9;
            case GLFW.GLFW_KEY_MINUS -> 10; case GLFW.GLFW_KEY_EQUAL -> 11;
            default -> -1;
        };
    }

    public static int normalRowDegree(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_Q -> 0; case GLFW.GLFW_KEY_W -> 1;
            case GLFW.GLFW_KEY_E -> 2; case GLFW.GLFW_KEY_R -> 3;
            case GLFW.GLFW_KEY_T -> 4; case GLFW.GLFW_KEY_Y -> 5;
            case GLFW.GLFW_KEY_U -> 6; case GLFW.GLFW_KEY_I -> 7;
            case GLFW.GLFW_KEY_O -> 8; case GLFW.GLFW_KEY_P -> 9;
            case GLFW.GLFW_KEY_LEFT_BRACKET -> 10;
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> 11;
            default -> -1;
        };
    }

    /** Exact three-row, 21-natural-note Windsong Lyre PC grid. */
    public static int genshinMelodyNote(int key, int highlightedOctave,
                                       int originPitchClass, int totalNotes) {
        DiatonicKey position = genshinKey(key);
        if (position == null) return -1;
        int noteId = originNoteId(highlightedOctave, originPitchClass, totalNotes)
                + position.octave() * 12 + MAJOR_SCALE[position.degree()];
        return validNoteId(noteId, totalNotes) ? noteId : -1;
    }

    /** In Left Split, Genshin's low natural row becomes muted chord data. */
    public static int genshinControlNote(int key) {
        DiatonicKey position = genshinKey(key);
        return position == null || position.octave() != 0
                ? -1 : 36 + MAJOR_SCALE[position.degree()];
    }

    public static boolean isPerformanceKey(InstrumentOptions.KeyboardLayout layout, int key) {
        return isPerformanceKey(layout, key,
                PianoKeyboardGeometry.STANDARD_FIRST_NOTE_ID,
                PianoKeyboardGeometry.STANDARD_LAST_NOTE_ID + 1);
    }

    public static boolean isPerformanceKey(InstrumentOptions.KeyboardLayout layout, int key,
                                           int firstNoteId, int noteLimit) {
        return switch (layout == null ? InstrumentOptions.KeyboardLayout.NORMAL : layout) {
            case NORMAL -> numberRowDegree(key) >= 0 || normalRowDegree(key) >= 0
                    || octaveShift(layout, key) != 0;
            case ALTERNATIVE -> alternativeControlNote(key) >= 0
                    || alternativeMelodyKey(key) || octaveShift(layout, key) != 0;
            case FULL -> fullKey(key) != null || octaveShift(layout, key) != 0;
            case GENSHIN -> genshinKey(key) != null || octaveShift(layout, key) != 0;
            case VISUAL_PIANO -> visualPianoBindings(
                    PianoKeyboardGeometry.STANDARD_FIRST_NOTE_ID,
                    PianoKeyboardGeometry.STANDARD_LAST_NOTE_ID + 1)
                    .values().stream().anyMatch(chord -> chord.key() == key);
            case CUSTOM -> {
                KeyboardCustomPreset custom = InstrumentOptions.customKeyboardPreset();
                boolean noteKey = custom.notes().values().stream()
                        .anyMatch(chord -> chord.key() == key);
                boolean octaveKey = !custom.coversEveryOctave(firstNoteId, noteLimit)
                        && (custom.octaveDown() != null && custom.octaveDown().key() == key
                        || custom.octaveUp() != null && custom.octaveUp().key() == key);
                yield noteKey || octaveKey;
            }
        };
    }

    /**
     * Music Maker's original instrument screen plays physical scan codes 16-27
     * after mod key handling. M3E must consume that complete legacy row when
     * a selected layout leaves one of those keys unbound, or keys such as [ and ]
     * leak through as unrelated notes.
     */
    public static boolean isLegacyMusicMakerRow(int scanCode) {
        return scanCode >= 16 && scanCode <= 27;
    }

    public static boolean consumesUnresolvedKey(InstrumentOptions.KeyboardLayout layout,
                                                int key, int scanCode,
                                                int firstNoteId, int noteLimit) {
        return isLegacyMusicMakerRow(scanCode)
                || isPerformanceKey(layout, key, firstNoteId, noteLimit);
    }

    private static boolean alternativeMelodyKey(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_W,
                    GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R,
                    GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_6,
                    GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_U,
                    GLFW.GLFW_KEY_I -> true;
            default -> false;
        };
    }

    private static FullKey fullKey(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_1 -> new FullKey(1, 0);
            case GLFW.GLFW_KEY_2 -> new FullKey(1, 1);
            case GLFW.GLFW_KEY_3 -> new FullKey(1, 2);
            case GLFW.GLFW_KEY_4 -> new FullKey(1, 3);
            case GLFW.GLFW_KEY_5 -> new FullKey(1, 4);
            case GLFW.GLFW_KEY_6 -> new FullKey(1, 5);
            case GLFW.GLFW_KEY_7 -> new FullKey(1, 6);
            case GLFW.GLFW_KEY_8 -> new FullKey(1, 7);
            case GLFW.GLFW_KEY_9 -> new FullKey(1, 8);
            case GLFW.GLFW_KEY_0 -> new FullKey(1, 9);
            case GLFW.GLFW_KEY_MINUS -> new FullKey(1, 10);
            case GLFW.GLFW_KEY_EQUAL -> new FullKey(1, 11);
            case GLFW.GLFW_KEY_Q -> new FullKey(0, 0);
            case GLFW.GLFW_KEY_W -> new FullKey(0, 1);
            case GLFW.GLFW_KEY_E -> new FullKey(0, 2);
            case GLFW.GLFW_KEY_R -> new FullKey(0, 3);
            case GLFW.GLFW_KEY_T -> new FullKey(0, 4);
            case GLFW.GLFW_KEY_Y -> new FullKey(0, 5);
            case GLFW.GLFW_KEY_U -> new FullKey(0, 6);
            case GLFW.GLFW_KEY_I -> new FullKey(0, 7);
            case GLFW.GLFW_KEY_O -> new FullKey(0, 8);
            case GLFW.GLFW_KEY_P -> new FullKey(0, 9);
            case GLFW.GLFW_KEY_LEFT_BRACKET -> new FullKey(0, 10);
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> new FullKey(0, 11);
            case GLFW.GLFW_KEY_A -> new FullKey(-1, 0);
            case GLFW.GLFW_KEY_S -> new FullKey(-1, 1);
            case GLFW.GLFW_KEY_D -> new FullKey(-1, 2);
            case GLFW.GLFW_KEY_F -> new FullKey(-1, 3);
            case GLFW.GLFW_KEY_G -> new FullKey(-1, 4);
            case GLFW.GLFW_KEY_H -> new FullKey(-1, 5);
            case GLFW.GLFW_KEY_J -> new FullKey(-1, 6);
            case GLFW.GLFW_KEY_K -> new FullKey(-1, 7);
            case GLFW.GLFW_KEY_L -> new FullKey(-1, 8);
            case GLFW.GLFW_KEY_SEMICOLON -> new FullKey(-1, 9);
            case GLFW.GLFW_KEY_APOSTROPHE -> new FullKey(-1, 10);
            case GLFW.GLFW_KEY_ENTER -> new FullKey(-1, 11);
            default -> null;
        };
    }

    private static DiatonicKey genshinKey(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_Q -> new DiatonicKey(2, 0);
            case GLFW.GLFW_KEY_W -> new DiatonicKey(2, 1);
            case GLFW.GLFW_KEY_E -> new DiatonicKey(2, 2);
            case GLFW.GLFW_KEY_R -> new DiatonicKey(2, 3);
            case GLFW.GLFW_KEY_T -> new DiatonicKey(2, 4);
            case GLFW.GLFW_KEY_Y -> new DiatonicKey(2, 5);
            case GLFW.GLFW_KEY_U -> new DiatonicKey(2, 6);
            case GLFW.GLFW_KEY_A -> new DiatonicKey(1, 0);
            case GLFW.GLFW_KEY_S -> new DiatonicKey(1, 1);
            case GLFW.GLFW_KEY_D -> new DiatonicKey(1, 2);
            case GLFW.GLFW_KEY_F -> new DiatonicKey(1, 3);
            case GLFW.GLFW_KEY_G -> new DiatonicKey(1, 4);
            case GLFW.GLFW_KEY_H -> new DiatonicKey(1, 5);
            case GLFW.GLFW_KEY_J -> new DiatonicKey(1, 6);
            case GLFW.GLFW_KEY_Z -> new DiatonicKey(0, 0);
            case GLFW.GLFW_KEY_X -> new DiatonicKey(0, 1);
            case GLFW.GLFW_KEY_C -> new DiatonicKey(0, 2);
            case GLFW.GLFW_KEY_V -> new DiatonicKey(0, 3);
            case GLFW.GLFW_KEY_B -> new DiatonicKey(0, 4);
            case GLFW.GLFW_KEY_N -> new DiatonicKey(0, 5);
            case GLFW.GLFW_KEY_M -> new DiatonicKey(0, 6);
            default -> null;
        };
    }

    private static int originNoteId(int highlightedOctave, int originPitchClass,
                                    int totalNotes) {
        // Validate only after the actual key degree is added. At the partial
        // A0/B0 endpoint the C-relative origin is intentionally negative.
        return KeyboardNoteLayout.originBaseNoteId(highlightedOctave, originPitchClass);
    }

    private static boolean validNoteId(int noteId, int totalNotes) {
        return noteId >= 0 && noteId < totalNotes;
    }

    private static Map<KeyboardChord, Integer> inverse(Map<Integer, KeyboardChord> source) {
        LinkedHashMap<KeyboardChord, Integer> inverse = new LinkedHashMap<>();
        source.forEach((note, chord) -> inverse.put(chord, note));
        return inverse;
    }

    private static final int[] PERFORMANCE_KEYS = {
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4,
            GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8,
            GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL,
            GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R,
            GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I,
            GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_LEFT_BRACKET,
            GLFW.GLFW_KEY_RIGHT_BRACKET, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S,
            GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H,
            GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_SEMICOLON,
            GLFW.GLFW_KEY_APOSTROPHE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_Z,
            GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B,
            GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_COMMA,
            GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH
    };

    private static final int[] VISUAL_WHITE_KEYS = {
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4,
            GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8,
            GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_W,
            GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_Y,
            GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P,
            GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F,
            GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K,
            GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C,
            GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M
    };

    private static final int[] VISUAL_BLACK_KEYS = {
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5,
            GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_Q,
            GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_Y,
            GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_S,
            GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_J,
            GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V,
            GLFW.GLFW_KEY_B
    };

    public record ResolvedBinding(int noteId, boolean control) { }

    private record FullKey(int octaveOffset, int degree) { }
    private record DiatonicKey(int octave, int degree) { }
}
