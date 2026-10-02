package m3e.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-instrument balanced note routing for Smart Play scale and chord assistance. */
public final class SmartPlayRuntime {
    private final Output output;
    private final int minimumNote;
    private final int maximumNote;
    private final Map<Integer, ActiveSource> directSources = new HashMap<>();
    private final Map<Integer, Float> soundingVolumes = new HashMap<>();
    private SmartPlaySettings activeSettings = SmartPlaySettings.DEFAULT;
    private List<Integer> previousVoicing = List.of();

    public SmartPlayRuntime(Output output, int minimumNote, int maximumNote) {
        this.output = output;
        this.minimumNote = Math.min(minimumNote, maximumNote);
        this.maximumNote = Math.max(minimumNote, maximumNote);
    }

    public boolean noteOn(int sourceMidi, float volume, SmartPlaySettings settings) {
        SmartPlaySettings normalized = syncSettings(settings);
        if (!normalized.routesNotes()) return false;
        float boundedVolume = Math.clamp(volume, 0.0F, 1.0F);
        ActiveSource source = directSources.get(sourceMidi);
        if (source == null) {
            List<Integer> transformed = SmartPlayMapper.transform(sourceMidi, normalized,
                    minimumNote, maximumNote, output::playable, previousVoicing);
            source = new ActiveSource(transformed);
            directSources.put(sourceMidi, source);
            if (!transformed.isEmpty()) previousVoicing = transformed;
        }
        source.velocities.add(boundedVolume);
        for (int note : source.notes) refresh(note, true);
        return true;
    }

    public boolean noteOff(int sourceMidi, SmartPlaySettings settings) {
        syncSettings(settings);
        ActiveSource source = directSources.get(sourceMidi);
        if (source == null || source.velocities.isEmpty()) return false;
        source.velocities.removeLast();
        if (source.velocities.isEmpty()) directSources.remove(sourceMidi);
        for (int note : source.notes) refresh(note, false);
        return true;
    }

    public void reset() {
        for (int note : soundingVolumes.keySet()) output.stop(note);
        directSources.clear();
        soundingVolumes.clear();
        previousVoicing = List.of();
    }

    private void refresh(int note, boolean retrigger) {
        Float next = maximumVolume(note);
        Float current = soundingVolumes.get(note);
        if (next == null) {
            if (current != null) output.stop(note);
            soundingVolumes.remove(note);
            return;
        }
        if (current == null) {
            output.play(note, next);
        } else if (retrigger || Float.compare(current, next) != 0) {
            output.stop(note);
            output.play(note, next);
        }
        soundingVolumes.put(note, next);
    }

    private Float maximumVolume(int note) {
        Float maximum = null;
        for (ActiveSource source : directSources.values()) {
            if (!source.notes.contains(note) || source.velocities.isEmpty()) continue;
            for (float velocity : source.velocities) {
                if (maximum == null || velocity > maximum) maximum = velocity;
            }
        }
        return maximum;
    }

    private SmartPlaySettings syncSettings(SmartPlaySettings settings) {
        SmartPlaySettings normalized = settings == null ? SmartPlaySettings.DEFAULT : settings;
        if (!normalized.equals(activeSettings)) {
            reset();
            activeSettings = normalized;
        }
        return normalized;
    }

    public interface Output {
        void play(int midiNote, float volume);
        void stop(int midiNote);
        boolean playable(int midiNote);
    }

    private static final class ActiveSource {
        private final List<Integer> notes;
        private final List<Float> velocities = new ArrayList<>();

        private ActiveSource(List<Integer> notes) {
            this.notes = List.copyOf(notes);
        }
    }
}
