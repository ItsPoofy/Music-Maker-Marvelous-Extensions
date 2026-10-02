package m3e.instrument;

import xerca.xercamusic.common.item.IItemInstrument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InstrumentVariations {
    public static final String DEFAULT_ID = "default";
    public static final int MAX_ID_LENGTH = 16;
    private static final List<Variation> DEFAULT_ONLY = List.of(
            new Variation(DEFAULT_ID, "Default"));
    private static final Map<Integer, List<Variation>> AVAILABLE = new HashMap<>();

    private InstrumentVariations() { }

    static void register(int instrumentId, String variationId, String label) {
        if (variationId == null || variationId.isBlank()
                || variationId.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid variation id: " + variationId);
        }
        List<Variation> variations = new ArrayList<>(
                AVAILABLE.getOrDefault(instrumentId, List.of()));
        if (variations.stream().anyMatch(variation -> variation.id().equals(variationId))) {
            throw new IllegalStateException("Duplicate variation " + variationId
                    + " for instrument " + instrumentId);
        }
        variations.add(new Variation(variationId, label));
        AVAILABLE.put(instrumentId, List.copyOf(variations));
    }

    public static List<Variation> available(int instrumentId) {
        return AVAILABLE.getOrDefault(instrumentId, DEFAULT_ONLY);
    }

    public static String defaultId(int instrumentId) {
        return available(instrumentId).getFirst().id();
    }

    public static String normalize(int instrumentId, String requested) {
        List<Variation> available = available(instrumentId);
        if (requested != null && requested.length() <= MAX_ID_LENGTH) {
            for (Variation variation : available) {
                if (variation.id().equals(requested)) return requested;
            }
        }
        return available.getFirst().id();
    }

    public static String label(int instrumentId, String variationId) {
        String normalized = normalize(instrumentId, variationId);
        for (Variation variation : available(instrumentId)) {
            if (variation.id().equals(normalized)) return variation.label();
        }
        return available(instrumentId).getFirst().label();
    }

    public static IItemInstrument.InsSound resolve(IItemInstrument instrument, int note,
                                                   String variationId) {
        String normalized = normalize(instrument.getInstrumentId(), variationId);
        return DEFAULT_ID.equals(normalized) ? instrument.getSound(note)
                : M3EInstruments.variationSound(instrument.getInstrumentId(), normalized, note);
    }

    public record Variation(String id, String label) { }
}
