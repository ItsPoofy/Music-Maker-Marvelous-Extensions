package m3e.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import m3e.debug.MusicDebugTelemetry;
import m3e.sound.InstrumentSoundProfile;
import m3e.session.DrumNoteMap;
import m3e.session.NoteNames;
import m3e.instrument.InstrumentRangeState;
import m3e.instrument.InstrumentVariations;
import m3e.M3E;
import net.fabricmc.loader.api.FabricLoader;
import xerca.xercamusic.client.NoteSound;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent player preferences and instrument defaults; connection state lives separately. */
public final class InstrumentOptions {
    public static final int DEFAULT_PRESSED_KEY_COLOR = 0x3C91CF;
    public static final int DEFAULT_PLAYABLE_KEY_COLOR = 0xD8C66F;
    public static final int DEFAULT_WATERFALL_COLOR = 0x3C91CF;
    public static final int DEFAULT_WATERFALL_OUTLINE_COLOR = 0x000000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE = FabricLoader.getInstance().getConfigDir()
            .resolve("m3e").resolve("instrument-options.json");
    private static final Map<Integer, InstrumentSoundProfile> LOCAL = new HashMap<>();
    private static final Map<Integer, String> LOCAL_VARIATIONS = new HashMap<>();
    private static KeyboardLayout keyboardLayout = KeyboardLayout.VISUAL_PIANO;
    private static NoteNames.Spelling noteSpelling = NoteNames.Spelling.SHARPS;
    private static boolean compactInstrumentUi;
    private static boolean fullKeyboardRange;
    private static boolean includeMidiName;
    private static boolean includeMidiInstrumentName;
    private static boolean includeMidiChannelName;
    private static boolean useDrumRemapping = true;
    private static boolean debugTelemetry;
    private static int importOctaveOffset;
    private static int pitchBendRangeSemitones = 2;
    private static int pressedKeyColor = DEFAULT_PRESSED_KEY_COLOR;
    private static int playableKeyColorRgb = DEFAULT_PLAYABLE_KEY_COLOR;
    private static boolean pianoWaterfallEnabled;
    private static int waterfallColorRgb = DEFAULT_WATERFALL_COLOR;
    private static int waterfallOutlineColorRgb = DEFAULT_WATERFALL_OUTLINE_COLOR;
    private static int[] drumNoteMap = DrumNoteMap.identity();
    private static KeyboardCustomPreset customPresetState = KeyboardCustomPreset.empty();
    private static final Map<String, Integer> SONGBOOK_PITCH_SHIFTS = new HashMap<>();
    private static SmartPlaySettings smartPlay = SmartPlaySettings.DEFAULT;
    /** Pitch class that begins a highlighted/typing octave. C fixes upstream's A grouping. */
    private static int octaveOrigin;
    private static long lastSaveNanos;
    private static boolean dirty;

    private InstrumentOptions() { }

    public static synchronized void load() {
        resetDefaults();
        if (!Files.isRegularFile(STORAGE)) {
            MusicDebugTelemetry.setClientEnabled(false);
            return;
        }
        try (Reader reader = Files.newBufferedReader(STORAGE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("keyboard_layout")) {
                try {
                    keyboardLayout = KeyboardLayout.valueOf(root.get("keyboard_layout").getAsString());
                } catch (IllegalArgumentException ignored) { }
            }
            if (root.has("octave_origin")) {
                octaveOrigin = Math.floorMod(root.get("octave_origin").getAsInt(), 12);
            }
            if (root.has("note_spelling")) {
                try {
                    noteSpelling = NoteNames.Spelling.valueOf(root.get("note_spelling").getAsString());
                } catch (IllegalArgumentException ignored) { }
            }
            compactInstrumentUi = booleanValue(root, "compact_instrument_ui", false);
            fullKeyboardRange = booleanValue(root, "full_keyboard_range", false);
            InstrumentRangeState.setFullKeyboardRange(fullKeyboardRange);
            includeMidiName = booleanValue(root, "include_midi_name", false);
            includeMidiInstrumentName = booleanValue(root, "include_midi_instrument_name", false);
            includeMidiChannelName = booleanValue(root, "include_midi_channel_name", false);
            useDrumRemapping = booleanValue(root, "use_drum_remapping", true);
            debugTelemetry = booleanValue(root, "debug_telemetry", false);
            importOctaveOffset = Math.clamp(integerValue(root,
                    "import_octave_offset", 0), -8, 8);
            pitchBendRangeSemitones = Math.clamp(integerValue(root,
                    "pitch_bend_range", 2), 1, 12);
            pressedKeyColor = integerValue(root, "pressed_key_color",
                    DEFAULT_PRESSED_KEY_COLOR) & 0xFFFFFF;
            playableKeyColorRgb = integerValue(root, "playable_key_color",
                    DEFAULT_PLAYABLE_KEY_COLOR) & 0xFFFFFF;
            pianoWaterfallEnabled = booleanValue(root, "piano_waterfall_enabled", false);
            waterfallColorRgb = integerValue(root, "waterfall_color",
                    DEFAULT_WATERFALL_COLOR) & 0xFFFFFF;
            waterfallOutlineColorRgb = integerValue(root, "waterfall_outline_color",
                    DEFAULT_WATERFALL_OUTLINE_COLOR) & 0xFFFFFF;
            drumNoteMap = DrumNoteMap.decode(root.has("drum_note_map")
                    ? root.get("drum_note_map").getAsString() : "");
            if (root.has("custom_keymap") && root.get("custom_keymap").isJsonObject()) {
                try {
                    customPresetState = KeyboardCustomPreset.fromJson(
                            root.getAsJsonObject("custom_keymap"));
                } catch (IllegalArgumentException invalidKeymap) {
                    M3E.LOGGER.warn("Ignored invalid Custom keymap", invalidKeymap);
                }
            }
            if (keyboardLayout == KeyboardLayout.CUSTOM
                    && !customPresetState.hasNoteBindings()) {
                keyboardLayout = KeyboardLayout.VISUAL_PIANO;
            }
            if (root.has("songbook_pitch_shifts")
                    && root.get("songbook_pitch_shifts").isJsonObject()) {
                JsonObject shifts = root.getAsJsonObject("songbook_pitch_shifts");
                for (String key : shifts.keySet()) {
                    if (!validSongbookKey(key)) continue;
                    if (!shifts.get(key).isJsonPrimitive()
                            || !shifts.get(key).getAsJsonPrimitive().isNumber()) continue;
                    int shift = Math.clamp(shifts.get(key).getAsInt(), -48, 48);
                    if (shift != 0) SONGBOOK_PITCH_SHIFTS.put(key, shift);
                }
            }
            smartPlay = loadSmartPlay(root);
            JsonObject variations = root.has("variations") && root.get("variations").isJsonObject()
                    ? root.getAsJsonObject("variations") : new JsonObject();
            for (String key : variations.keySet()) {
                try {
                    int instrumentId = Integer.parseInt(key);
                    LOCAL_VARIATIONS.put(instrumentId, InstrumentVariations.normalize(
                            instrumentId, variations.get(key).getAsString()));
                } catch (RuntimeException ignored) { }
            }
            JsonObject profiles = root.has("profiles") ? root.getAsJsonObject("profiles") : new JsonObject();
            for (String key : profiles.keySet()) {
                int instrumentId;
                try {
                    instrumentId = Integer.parseInt(key);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                JsonObject profile = profiles.getAsJsonObject(key);
                LOCAL.put(instrumentId, new InstrumentSoundProfile(
                        value(profile, "volume", 1.0F),
                        value(profile, "attack", InstrumentSoundProfile.DEFAULT_SLIDER),
                        value(profile, "decay", InstrumentSoundProfile.DEFAULT_SLIDER),
                        value(profile, "sustain", InstrumentSoundProfile.DEFAULT_SLIDER),
                        value(profile, "release", InstrumentSoundProfile.DEFAULT_SLIDER),
                        value(profile, "tuning_hz", InstrumentSoundProfile.DEFAULT_TUNING_HZ)));
            }
        } catch (RuntimeException | java.io.IOException exception) {
            M3E.LOGGER.warn("Could not load instrument options", exception);
            resetDefaults();
        } finally {
            MusicDebugTelemetry.setClientEnabled(debugTelemetry);
        }
    }

    private static void resetDefaults() {
        LOCAL.clear();
        LOCAL_VARIATIONS.clear();
        keyboardLayout = KeyboardLayout.VISUAL_PIANO;
        noteSpelling = NoteNames.Spelling.SHARPS;
        compactInstrumentUi = false;
        fullKeyboardRange = false;
        InstrumentRangeState.setFullKeyboardRange(false);
        octaveOrigin = 0;
        includeMidiName = false;
        includeMidiInstrumentName = false;
        includeMidiChannelName = false;
        useDrumRemapping = true;
        debugTelemetry = false;
        importOctaveOffset = 0;
        pitchBendRangeSemitones = 2;
        pressedKeyColor = DEFAULT_PRESSED_KEY_COLOR;
        playableKeyColorRgb = DEFAULT_PLAYABLE_KEY_COLOR;
        pianoWaterfallEnabled = false;
        waterfallColorRgb = DEFAULT_WATERFALL_COLOR;
        waterfallOutlineColorRgb = DEFAULT_WATERFALL_OUTLINE_COLOR;
        drumNoteMap = DrumNoteMap.identity();
        customPresetState = KeyboardCustomPreset.empty();
        SONGBOOK_PITCH_SHIFTS.clear();
        smartPlay = SmartPlaySettings.DEFAULT;
        lastSaveNanos = 0L;
        dirty = false;
    }

    public static synchronized KeyboardLayout keyboardLayout() { return keyboardLayout; }

    public static synchronized void setKeyboardLayout(KeyboardLayout value) {
        KeyboardLayout requested = value == null ? KeyboardLayout.NORMAL : value;
        keyboardLayout = requested == KeyboardLayout.CUSTOM
                && !customPresetState.hasNoteBindings()
                ? KeyboardLayout.VISUAL_PIANO : requested;
        save();
    }

    public static synchronized KeyboardLayout nextKeyboardLayout() {
        KeyboardLayout next = keyboardLayout.next();
        while (next == KeyboardLayout.CUSTOM && !customPresetState.hasNoteBindings()) {
            next = next.next();
        }
        return next;
    }

    public static synchronized int octaveOrigin() { return octaveOrigin; }

    public static synchronized NoteNames.Spelling noteSpelling() { return noteSpelling; }

    public static synchronized KeyboardCustomPreset customKeyboardPreset() {
        return customPresetState;
    }

    public static synchronized void setCustomKeyboardPreset(KeyboardCustomPreset preset) {
        customPresetState = preset == null ? KeyboardCustomPreset.empty() : preset;
        if (keyboardLayout == KeyboardLayout.CUSTOM
                && !customPresetState.hasNoteBindings()) {
            keyboardLayout = KeyboardLayout.VISUAL_PIANO;
        }
        save();
    }

    public static synchronized boolean compactInstrumentUi() { return compactInstrumentUi; }

    public static synchronized void setCompactInstrumentUi(boolean value) {
        compactInstrumentUi = value;
        save();
    }

    public static synchronized boolean fullKeyboardRange() { return fullKeyboardRange; }

    public static synchronized void setFullKeyboardRange(boolean value) {
        fullKeyboardRange = value;
        InstrumentRangeState.setFullKeyboardRange(value);
        save();
    }

    public static synchronized void setNoteSpelling(NoteNames.Spelling value) {
        noteSpelling = value == null ? NoteNames.Spelling.SHARPS : value;
        save();
    }

    public static synchronized void setOctaveOrigin(int pitchClass) {
        octaveOrigin = Math.floorMod(pitchClass, 12);
        save();
    }

    public static synchronized boolean includeMidiName() { return includeMidiName; }

    public static synchronized void setIncludeMidiName(boolean value) {
        includeMidiName = value;
        save();
    }

    public static synchronized boolean includeMidiInstrumentName() { return includeMidiInstrumentName; }

    public static synchronized void setIncludeMidiInstrumentName(boolean value) {
        includeMidiInstrumentName = value;
        save();
    }

    public static synchronized boolean includeMidiChannelName() { return includeMidiChannelName; }

    public static synchronized void setIncludeMidiChannelName(boolean value) {
        includeMidiChannelName = value;
        save();
    }

    public static synchronized boolean useDrumRemapping() { return useDrumRemapping; }

    public static synchronized void setUseDrumRemapping(boolean value) {
        useDrumRemapping = value;
        save();
    }

    public static synchronized boolean debugTelemetry() { return debugTelemetry; }

    public static synchronized void setDebugTelemetry(boolean value) {
        debugTelemetry = value;
        MusicDebugTelemetry.setClientEnabled(value);
        save();
    }

    public static synchronized int importOctaveOffset() { return importOctaveOffset; }

    public static synchronized void setImportOctaveOffset(int value) {
        importOctaveOffset = Math.clamp(value, -8, 8);
        save();
    }

    public static synchronized int pitchBendRangeSemitones() {
        return pitchBendRangeSemitones;
    }

    public static synchronized int pressedKeyColor() { return pressedKeyColor; }

    public static synchronized void setPressedKeyColor(int rgb) {
        pressedKeyColor = rgb & 0xFFFFFF;
        dirty = true;
        tick();
    }

    public static synchronized void resetPressedKeyColor() {
        setPressedKeyColor(DEFAULT_PRESSED_KEY_COLOR);
    }

    public static synchronized int playableKeyColor() { return playableKeyColorRgb; }

    public static synchronized void setPlayableKeyColor(int rgb) {
        playableKeyColorRgb = rgb & 0xFFFFFF;
        dirty = true;
        tick();
    }

    public static synchronized void resetPlayableKeyColor() {
        setPlayableKeyColor(DEFAULT_PLAYABLE_KEY_COLOR);
    }

    public static synchronized boolean pianoWaterfallEnabled() {
        return pianoWaterfallEnabled;
    }

    public static synchronized void setPianoWaterfallEnabled(boolean value) {
        pianoWaterfallEnabled = value;
        save();
    }

    public static synchronized int waterfallColor() { return waterfallColorRgb; }

    public static synchronized void setWaterfallColor(int rgb) {
        waterfallColorRgb = rgb & 0xFFFFFF;
        dirty = true;
        tick();
    }

    public static synchronized void resetWaterfallColor() {
        setWaterfallColor(DEFAULT_WATERFALL_COLOR);
    }

    public static synchronized int waterfallOutlineColor() {
        return waterfallOutlineColorRgb;
    }

    public static synchronized void setWaterfallOutlineColor(int rgb) {
        waterfallOutlineColorRgb = rgb & 0xFFFFFF;
        dirty = true;
        tick();
    }

    public static synchronized void resetWaterfallOutlineColor() {
        setWaterfallOutlineColor(DEFAULT_WATERFALL_OUTLINE_COLOR);
    }

    /** Local import preference; never published as player or remote state. */
    public static synchronized List<Integer> drumNoteMap() {
        return DrumNoteMap.immutableList(drumNoteMap);
    }

    public static synchronized void setDrumNoteMap(List<Integer> mapping) {
        drumNoteMap = DrumNoteMap.validatedArray(mapping);
        save();
    }

    public static synchronized void resetDrumNoteMap() {
        drumNoteMap = DrumNoteMap.identity();
        save();
    }

    /** Local playback preference scoped to one stored track in one Songbook. */
    public static synchronized int songbookPitchShift(UUID bookId, UUID entryId) {
        if (bookId == null || entryId == null) return 0;
        return SONGBOOK_PITCH_SHIFTS.getOrDefault(songbookKey(bookId, entryId), 0);
    }

    public static synchronized void setSongbookPitchShift(UUID bookId, UUID entryId,
                                                           int semitones) {
        if (bookId == null || entryId == null) return;
        String key = songbookKey(bookId, entryId);
        int bounded = Math.clamp(semitones, -48, 48);
        if (bounded == 0) SONGBOOK_PITCH_SHIFTS.remove(key);
        else SONGBOOK_PITCH_SHIFTS.put(key, bounded);
        dirty = true;
        tick();
    }

    public static synchronized void setPitchBendRangeSemitones(int value) {
        pitchBendRangeSemitones = Math.clamp(value, 1, 12);
        save();
        InstrumentExpressionController.refreshRange();
    }

    public static synchronized SmartPlaySettings smartPlay() { return smartPlay; }

    public static synchronized void setSmartPlay(SmartPlaySettings value) {
        smartPlay = value == null ? SmartPlaySettings.DEFAULT : value;
        save();
    }

    /** C-anchored note-id offset used by the M3E's new keyboard UI. */
    public static synchronized int octaveOriginOffset() {
        return 3 + Math.floorMod(octaveOrigin, 12);
    }

    public static synchronized String octaveOriginName() {
        return NoteNames.pitchClass(octaveOrigin, noteSpelling);
    }

    public static synchronized InstrumentSoundProfile playerDefaultProfile(int instrumentId) {
        return LOCAL.getOrDefault(instrumentId, InstrumentSoundProfile.DEFAULT);
    }

    public static synchronized String playerDefaultVariation(int instrumentId) {
        return InstrumentVariations.normalize(instrumentId, LOCAL_VARIATIONS.get(instrumentId));
    }

    public static synchronized void setPlayerDefaultVariation(int instrumentId, String variation) {
        String normalized = InstrumentVariations.normalize(instrumentId, variation);
        if (normalized.equals(InstrumentVariations.defaultId(instrumentId))) {
            LOCAL_VARIATIONS.remove(instrumentId);
        } else {
            LOCAL_VARIATIONS.put(instrumentId, normalized);
        }
        InstrumentProfileClientState.queue(instrumentId, playerDefaultProfile(instrumentId));
        dirty = true;
        tick();
    }

    public static synchronized void setPlayerDefaultProfile(int instrumentId, InstrumentSoundProfile profile) {
        LOCAL.put(instrumentId, profile);
        InstrumentProfileClientState.queue(instrumentId, profile);
        dirty = true;
        tick();
    }

    public static synchronized void tick() {
        long now = System.nanoTime();
        if (dirty && now - lastSaveNanos >= 250_000_000L) {
            save();
            dirty = false;
            lastSaveNanos = now;
        }
    }

    public static synchronized void shutdown() {
        if (dirty) save();
        dirty = false;
    }

    public static void apply(NoteSound sound, InstrumentSoundProfile profile) {
        if (sound instanceof InstrumentEnvelopeAccess access) {
            access.m3e$configureEnvelope(profile);
        }
    }

    private static String songbookKey(UUID bookId, UUID entryId) {
        return bookId + "/" + entryId;
    }

    private static boolean validSongbookKey(String key) {
        String[] ids = key.split("/", -1);
        if (ids.length != 2) return false;
        try {
            UUID.fromString(ids[0]);
            UUID.fromString(ids[1]);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static float value(JsonObject object, String name, float fallback) {
        return object.has(name) ? object.get(name).getAsFloat() : fallback;
    }

    private static boolean booleanValue(JsonObject object, String name, boolean fallback) {
        return object.has(name) ? object.get(name).getAsBoolean() : fallback;
    }

    private static int integerValue(JsonObject object, String name, int fallback) {
        return object.has(name) ? object.get(name).getAsInt() : fallback;
    }

    private static SmartPlaySettings loadSmartPlay(JsonObject root) {
        if (!root.has("smart_play") || !root.get("smart_play").isJsonObject()) return SmartPlaySettings.DEFAULT;

        JsonObject stored = root.getAsJsonObject("smart_play");
        JsonObject scaleJson = objectValue(stored, "scale");
        JsonObject chordJson = objectValue(stored, "chord");
        SmartPlaySettings.Scale scale = new SmartPlaySettings.Scale(
                booleanValue(scaleJson, "enabled", false),
                integerValue(scaleJson, "root", 0),
                enumValue(scaleJson, "bank", SmartPlaySettings.ScaleBank.class,
                        SmartPlaySettings.ScaleBank.MAIN),
                integerValue(scaleJson, "type", 1),
                enumValue(scaleJson, "key_mode", SmartPlaySettings.KeyMode.class,
                        SmartPlaySettings.KeyMode.MAPPED));
        SmartPlaySettings.Chord chord = new SmartPlaySettings.Chord(
                enumValue(chordJson, "mode", SmartPlaySettings.ChordMode.class,
                        SmartPlaySettings.ChordMode.OFF),
                enumValue(chordJson, "diatonic_harmony",
                        SmartPlaySettings.DiatonicHarmony.class,
                        SmartPlaySettings.DiatonicHarmony.ONE_THREE_FIVE),
                enumValue(chordJson, "chromatic_harmony",
                        SmartPlaySettings.ChromaticHarmony.class,
                        SmartPlaySettings.ChromaticHarmony.MAJOR),
                enumValue(chordJson, "chord_set", SmartPlaySettings.ChordSet.class,
                        SmartPlaySettings.ChordSet.MAJ_1),
                integerValue(chordJson, "position", 0));
        return new SmartPlaySettings(scale, chord);
    }

    private static JsonObject smartPlayJson(SmartPlaySettings settings) {
        JsonObject root = new JsonObject();
        JsonObject scale = new JsonObject();
        scale.addProperty("enabled", settings.scale().enabled());
        scale.addProperty("root", settings.scale().root());
        scale.addProperty("bank", settings.scale().bank().name());
        scale.addProperty("type", settings.scale().type());
        scale.addProperty("key_mode", settings.scale().keyMode().name());
        root.add("scale", scale);
        JsonObject chord = new JsonObject();
        chord.addProperty("mode", settings.chord().mode().name());
        chord.addProperty("diatonic_harmony", settings.chord().diatonicHarmony().name());
        chord.addProperty("chromatic_harmony", settings.chord().chromaticHarmony().name());
        chord.addProperty("chord_set", settings.chord().chordSet().name());
        chord.addProperty("position", settings.chord().position());
        root.add("chord", chord);
        return root;
    }

    private static JsonObject objectValue(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonObject()
                ? object.getAsJsonObject(name) : new JsonObject();
    }

    private static <E extends Enum<E>> E enumValue(JsonObject object, String name,
                                                   Class<E> type, E fallback) {
        if (!object.has(name)) return fallback;
        try {
            return Enum.valueOf(type, object.get(name).getAsString());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(STORAGE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 14);
            root.addProperty("keyboard_layout", keyboardLayout.name());
            root.addProperty("octave_origin", octaveOrigin);
            root.addProperty("note_spelling", noteSpelling.name());
            root.addProperty("compact_instrument_ui", compactInstrumentUi);
            root.addProperty("full_keyboard_range", fullKeyboardRange);
            root.addProperty("include_midi_name", includeMidiName);
            root.addProperty("include_midi_instrument_name", includeMidiInstrumentName);
            root.addProperty("include_midi_channel_name", includeMidiChannelName);
            root.addProperty("use_drum_remapping", useDrumRemapping);
            root.addProperty("debug_telemetry", debugTelemetry);
            root.addProperty("import_octave_offset", importOctaveOffset);
            root.addProperty("pitch_bend_range", pitchBendRangeSemitones);
            root.addProperty("pressed_key_color", pressedKeyColor);
            root.addProperty("playable_key_color", playableKeyColorRgb);
            root.addProperty("piano_waterfall_enabled", pianoWaterfallEnabled);
            root.addProperty("waterfall_color", waterfallColorRgb);
            root.addProperty("waterfall_outline_color", waterfallOutlineColorRgb);
            root.addProperty("drum_note_map", DrumNoteMap.encode(drumNoteMap));
            root.add("custom_keymap", customPresetState.toJson());
            JsonObject songbookShifts = new JsonObject();
            SONGBOOK_PITCH_SHIFTS.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> songbookShifts.addProperty(entry.getKey(), entry.getValue()));
            root.add("songbook_pitch_shifts", songbookShifts);
            root.add("smart_play", smartPlayJson(smartPlay));
            JsonObject variations = new JsonObject();
            LOCAL_VARIATIONS.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> variations.addProperty(
                            Integer.toString(entry.getKey()), entry.getValue()));
            root.add("variations", variations);
            JsonObject profiles = new JsonObject();
            for (Map.Entry<Integer, InstrumentSoundProfile> entry : LOCAL.entrySet()) {
                InstrumentSoundProfile profile = entry.getValue();
                JsonObject stored = new JsonObject();
                stored.addProperty("volume", profile.volume());
                stored.addProperty("attack", profile.attack());
                stored.addProperty("decay", profile.decay());
                stored.addProperty("sustain", profile.sustain());
                stored.addProperty("release", profile.release());
                stored.addProperty("tuning_hz", profile.tuningHz());
                profiles.add(Integer.toString(entry.getKey()), stored);
            }
            root.add("profiles", profiles);
            Path temporary = STORAGE.resolveSibling(STORAGE.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) { GSON.toJson(root, writer); }
            try {
                Files.move(temporary, STORAGE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, STORAGE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (RuntimeException | java.io.IOException exception) {
            M3E.LOGGER.warn("Could not save instrument options", exception);
        }
    }

    public enum KeyboardLayout {
        NORMAL("Normal"), ALTERNATIVE("Alternative"), FULL("Full Keyboard"),
        GENSHIN("Genshin"), VISUAL_PIANO("Visual Piano"), CUSTOM("Custom");
        private final String label;
        KeyboardLayout(String label) { this.label = label; }
        public String label() { return label; }
        public KeyboardLayout next() {
            KeyboardLayout[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

}
