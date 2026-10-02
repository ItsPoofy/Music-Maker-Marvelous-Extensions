package m3e.session;

import java.util.ArrayList;
import java.util.List;

/** Validated GM-percussion to Music Maker Drum Kit note map. */
public final class DrumNoteMap {
    public static final int MIN_GM_NOTE = 27;
    public static final int MAX_GM_NOTE = 87;
    public static final int MIN_MUSIC_MAKER_NOTE = 21;
    public static final int MAX_MUSIC_MAKER_NOTE = 116;
    public static final int SIZE = MAX_GM_NOTE - MIN_GM_NOTE + 1;
    private static final String[] NAMES = {
            "High Q", "Slap", "Scratch Push", "Scratch Pull", "Sticks", "Square Click",
            "Metronome Click", "Metronome Bell",
            "Acoustic Bass Drum", "Bass Drum 1", "Side Stick", "Acoustic Snare", "Hand Clap", "Electric Snare",
            "Low Floor Tom", "Closed Hi-Hat", "High Floor Tom", "Pedal Hi-Hat", "Low Tom", "Open Hi-Hat",
            "Low-Mid Tom", "Hi-Mid Tom", "Crash Cymbal 1", "High Tom", "Ride Cymbal 1", "Chinese Cymbal",
            "Ride Bell", "Tambourine", "Splash Cymbal", "Cowbell", "Crash Cymbal 2", "Vibraslap",
            "Ride Cymbal 2", "High Bongo", "Low Bongo", "Mute High Conga", "Open High Conga", "Low Conga",
            "High Timbale", "Low Timbale", "High Agogo", "Low Agogo", "Cabasa", "Maracas",
            "Short Whistle", "Long Whistle", "Short Guiro", "Long Guiro", "Claves", "High Wood Block",
            "Low Wood Block", "Mute Cuica", "Open Cuica", "Mute Triangle", "Open Triangle",
            "Shaker", "Jingle Bell", "Belltree", "Castanets", "Mute Surdo", "Open Surdo"
    };
    private static final int[] DEFAULT_MAP = {
            106, 63, 91, 92, 107, 106, 107, 115, 34, 41, 107, 59, 67, 65,
            45, 70, 47, 72, 49, 74, 51, 53, 88, 55, 87, 89, 81, 103,
            85, 114, 90, 105, 84, 56, 52, 54, 109, 46, 68, 64, 115, 114,
            93, 94, 96, 97, 95, 98, 106, 115, 114, 99, 100, 102, 104, 91,
            102, 92, 106, 39, 33
    };

    private DrumNoteMap() { }

    public static int[] identity() {
        return DEFAULT_MAP.clone();
    }

    public static int index(int gmNote) {
        return gmNote >= MIN_GM_NOTE && gmNote <= MAX_GM_NOTE ? gmNote - MIN_GM_NOTE : -1;
    }

    public static boolean validTarget(int note) {
        return note >= MIN_MUSIC_MAKER_NOTE && note <= MAX_MUSIC_MAKER_NOTE;
    }

    public static String name(int gmNote) {
        int index = index(gmNote);
        if (index < 0) throw new IllegalArgumentException("Not a GM percussion note: " + gmNote);
        return NAMES[index];
    }

    public static List<Integer> immutableList(int[] map) {
        if (map == null || map.length != SIZE) throw new IllegalArgumentException("Drum map must contain " + SIZE + " notes");
        List<Integer> result = new ArrayList<>(SIZE);
        for (int target : map) {
            if (!validTarget(target)) throw new IllegalArgumentException("Invalid Music Maker Drum Kit note " + target);
            result.add(target);
        }
        return List.copyOf(result);
    }

    public static int[] validatedArray(List<Integer> values) {
        if (values == null || values.size() != SIZE) throw new IllegalArgumentException("Drum map must contain " + SIZE + " notes");
        int[] result = new int[SIZE];
        for (int index = 0; index < SIZE; index++) {
            int target = values.get(index);
            if (!validTarget(target)) throw new IllegalArgumentException("Invalid Music Maker Drum Kit note " + target);
            result[index] = target;
        }
        return result;
    }

    public static String encode(int[] map) {
        List<Integer> values = immutableList(map);
        return String.join(",", values.stream().map(String::valueOf).toList());
    }

    public static int[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return identity();
        String[] parts = encoded.split(",", -1);
        if (parts.length != SIZE) return identity();
        List<Integer> values = new ArrayList<>(SIZE);
        try {
            for (String part : parts) values.add(Integer.parseInt(part));
            return validatedArray(values);
        } catch (IllegalArgumentException ignored) {
            return identity();
        }
    }
}
