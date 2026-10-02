package m3e.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable local-only absolute piano mapping used by the Custom preset. */
public record KeyboardCustomPreset(Map<Integer, KeyboardChord> notes,
                                   KeyboardChord octaveDown,
                                   KeyboardChord octaveUp) {
    public static final int FORMAT_VERSION = 1;
    private static final String BINDINGS = "bindings";

    public KeyboardCustomPreset {
        LinkedHashMap<Integer, KeyboardChord> sanitized = new LinkedHashMap<>();
        if (notes != null) {
            notes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (entry.getKey() >= PianoKeyboardGeometry.STANDARD_FIRST_NOTE_ID
                        && entry.getKey() <= PianoKeyboardGeometry.STANDARD_LAST_NOTE_ID
                        && entry.getValue() != null) {
                    sanitized.put(entry.getKey(), entry.getValue());
                }
            });
        }
        notes = Map.copyOf(sanitized);
    }

    public static KeyboardCustomPreset empty() {
        return new KeyboardCustomPreset(Map.of(),
                KeyboardChord.of(GLFW.GLFW_KEY_Z, 0),
                KeyboardChord.of(GLFW.GLFW_KEY_X, 0));
    }

    public boolean hasNoteBindings() {
        return !notes.isEmpty();
    }

    public KeyboardChord note(int noteId) {
        return notes.get(noteId);
    }

    public Integer noteFor(KeyboardChord chord) {
        if (chord == null) return null;
        for (Map.Entry<Integer, KeyboardChord> entry : notes.entrySet()) {
            if (entry.getValue().equals(chord)) return entry.getKey();
        }
        return null;
    }

    public KeyboardCustomPreset withNote(int noteId, KeyboardChord chord) {
        LinkedHashMap<Integer, KeyboardChord> changed = new LinkedHashMap<>(notes);
        changed.entrySet().removeIf(entry -> chord != null && chord.equals(entry.getValue()));
        if (chord == null) changed.remove(noteId);
        else changed.put(noteId, chord);
        KeyboardChord down = chord != null && chord.equals(octaveDown) ? null : octaveDown;
        KeyboardChord up = chord != null && chord.equals(octaveUp) ? null : octaveUp;
        return new KeyboardCustomPreset(changed, down, up);
    }

    public KeyboardCustomPreset withOctaveDown(KeyboardChord chord) {
        LinkedHashMap<Integer, KeyboardChord> changed = withoutChord(chord);
        KeyboardChord up = chord != null && chord.equals(octaveUp) ? null : octaveUp;
        return new KeyboardCustomPreset(changed, chord, up);
    }

    public KeyboardCustomPreset withOctaveUp(KeyboardChord chord) {
        LinkedHashMap<Integer, KeyboardChord> changed = withoutChord(chord);
        KeyboardChord down = chord != null && chord.equals(octaveDown) ? null : octaveDown;
        return new KeyboardCustomPreset(changed, down, chord);
    }

    public boolean coversEveryOctave(int firstNoteId, int noteLimit) {
        if (noteLimit <= firstNoteId) return false;
        int firstPage = octavePage(firstNoteId);
        int lastPage = octavePage(noteLimit - 1);
        for (int page = firstPage; page <= lastPage; page++) {
            final int expected = page;
            if (notes.keySet().stream().noneMatch(note -> note >= firstNoteId
                    && note < noteLimit && octavePage(note) == expected)) return false;
        }
        return true;
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", "m3e-keymap");
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("name", "Custom");
        root.add("octave_down", chordToJson(octaveDown));
        root.add("octave_up", chordToJson(octaveUp));
        JsonArray bindings = new JsonArray();
        for (Map.Entry<Integer, KeyboardChord> entry : notes.entrySet()) {
            JsonObject binding = chordToJson(entry.getValue());
            binding.addProperty("note_id", entry.getKey());
            bindings.add(binding);
        }
        root.add(BINDINGS, bindings);
        return root;
    }

    public static KeyboardCustomPreset fromJson(JsonObject root) {
        if (root == null || !"m3e-keymap".equals(string(root, "format"))
                || integer(root, "version") != FORMAT_VERSION
                || !root.has(BINDINGS) || !root.get(BINDINGS).isJsonArray()) {
            throw new IllegalArgumentException("Not a supported Music Maker’s Marvelous Extensions keymap");
        }
        LinkedHashMap<Integer, KeyboardChord> notes = new LinkedHashMap<>();
        for (JsonElement element : root.getAsJsonArray(BINDINGS)) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid keymap binding");
            JsonObject binding = element.getAsJsonObject();
            int noteId = integer(binding, "note_id");
            KeyboardChord chord = chordFromJson(binding);
            if (noteId < PianoKeyboardGeometry.STANDARD_FIRST_NOTE_ID
                    || noteId > PianoKeyboardGeometry.STANDARD_LAST_NOTE_ID
                    || notes.put(noteId, chord) != null) {
                throw new IllegalArgumentException("Invalid or duplicate note in keymap");
            }
        }
        KeyboardChord down = optionalChord(root.get("octave_down"));
        KeyboardChord up = optionalChord(root.get("octave_up"));
        KeyboardCustomPreset parsed = new KeyboardCustomPreset(notes, down, up);
        if (hasDuplicateChords(parsed)) {
            throw new IllegalArgumentException("A key combination may only have one action");
        }
        return parsed;
    }

    private LinkedHashMap<Integer, KeyboardChord> withoutChord(KeyboardChord chord) {
        LinkedHashMap<Integer, KeyboardChord> changed = new LinkedHashMap<>(notes);
        changed.entrySet().removeIf(entry -> chord != null && chord.equals(entry.getValue()));
        return changed;
    }

    static int octavePage(int noteId) {
        return noteId < 3 ? 0 : 1 + (noteId - 3) / 12;
    }

    private static boolean hasDuplicateChords(KeyboardCustomPreset preset) {
        java.util.HashSet<KeyboardChord> seen = new java.util.HashSet<>();
        for (KeyboardChord chord : preset.notes.values()) if (!seen.add(chord)) return true;
        return preset.octaveDown != null && !seen.add(preset.octaveDown)
                || preset.octaveUp != null && !seen.add(preset.octaveUp);
    }

    private static JsonObject chordToJson(KeyboardChord chord) {
        JsonObject object = new JsonObject();
        if (chord != null) {
            object.addProperty("key", chord.key());
            object.addProperty("modifiers", chord.modifiers());
        }
        return object;
    }

    private static KeyboardChord optionalChord(JsonElement element) {
        return element == null || element.isJsonNull() || !element.isJsonObject()
                || !element.getAsJsonObject().has("key")
                ? null : chordFromJson(element.getAsJsonObject());
    }

    private static KeyboardChord chordFromJson(JsonObject object) {
        int key = integer(object, "key");
        int modifiers = integer(object, "modifiers");
        if (key < GLFW.GLFW_KEY_SPACE || key > GLFW.GLFW_KEY_LAST
                || KeyboardChord.isModifierKey(key)
                || (modifiers & ~KeyboardChord.SUPPORTED_MODIFIERS) != 0) {
            throw new IllegalArgumentException("Invalid key combination");
        }
        return KeyboardChord.of(key, modifiers);
    }

    private static String string(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonPrimitive()
                ? object.get(name).getAsString() : "";
    }

    private static int integer(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()
                || !object.getAsJsonPrimitive(name).isNumber()) {
            throw new IllegalArgumentException("Missing numeric keymap field " + name);
        }
        return object.get(name).getAsInt();
    }
}
