package m3e.sheet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Pure score compilation; voice ownership and output remain backend responsibilities. */
public final class ScorePlaybackEvents {
    private ScorePlaybackEvents() { }

    public enum Type { OFF, EXPRESSION, ON, VOLUME }

    public record Event(long micros, Type type, long eventId, MusicScore.MidiNote note,
                        int pitch, int velocity, int track, long sourceStartTick,
                        float gain, float bendSemitones, int modulation,
                        MusicScore.Glissando glissando, long durationMicros) {
        public int lengthTicks() {
            return Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                    Math.round(durationMicros / 50_000.0D)));
        }
        public byte[] byteWaypoints() {
            if (glissando == null) return null;
            byte[] values = new byte[glissando.points().size()];
            for (int i = 0; i < values.length; i++) values[i] = (byte) glissando.points().get(i).semitones();
            return values;
        }
        public byte[] bytePositions() {
            if (glissando == null) return null;
            byte[] values = new byte[glissando.points().size()];
            for (int i = 0; i < values.length; i++) values[i] = (byte) glissando.points().get(i).positionPercent();
            return values;
        }
        public float[] waypoints() {
            if (glissando == null) return null;
            float[] values = new float[glissando.points().size()];
            for (int i = 0; i < values.length; i++) values[i] = glissando.points().get(i).semitones();
            return values;
        }
        public float[] positions() {
            if (glissando == null) return null;
            float[] values = new float[glissando.points().size()];
            for (int i = 0; i < values.length; i++) values[i] = glissando.points().get(i).positionPercent() / 100.0F;
            return values;
        }
    }

    public static List<Event> compile(MusicScore song, long fromMicros, int tempoBpm,
                                      int transpose, boolean resume, long eventIdBase,
                                      long percussionTailMicros) {
        ArrayList<Event> result = new ArrayList<>();
        MusicScore.TempoMap map = song.tempoMap();
        AutomationIndex automation = new AutomationIndex(song);
        for (MusicScore.ExpressionEvent expression : song.expressions()) {
            long at = micros(song, map.microsAt(expression.tick()), tempoBpm);
            if (at >= fromMicros) result.add(expression(at, expression));
        }
        if (fromMicros > 0) for (MusicScore.TrackInfo track : song.tracks()) {
            result.add(expression(fromMicros, automation.expressionAt(track.id(),
                    tick(song, fromMicros, tempoBpm))));
        }
        long id = eventIdBase;
        for (MusicScore.MidiNote note : song.notes()) {
            long eventId = ++id;
            int pitch = song.outputPitch(note) + transpose;
            if (pitch < MusicScore.MIN_PITCH || pitch > MusicScore.MAX_PITCH) continue;
            long start = micros(song, map.microsAt(note.startTick()), tempoBpm);
            long end = micros(song, map.microsAt(note.endTick()), tempoBpm);
            long duration = Math.max(1, end - start);
            if (song.tracks().get(note.track()).percussion()) end = Math.max(end, start + percussionTailMicros);
            if (end <= fromMicros || start < fromMicros && !resume) continue;
            boolean resumed = start < fromMicros;
            long atTick = resumed ? tick(song, fromMicros, tempoBpm) : note.startTick();
            MusicScore.ExpressionEvent expression = automation.expressionAt(note.track(), atTick);
            MusicScore.VolumeRamp ramp = automation.volumeRamp(note);
            MusicScore.Glissando glissando = resumed ? null : automation.glissando(note);
            // Seeking retriggers a sustained voice with its current expression, without restarting its curve.
            float gain = ramp == null ? expression.gain() : rampVolume(ramp, atTick);
            result.add(new Event(Math.max(start, fromMicros), Type.ON, eventId, note,
                    pitch, ramp == null ? note.velocity() : 127, note.track(), note.startTick(),
                    gain, expression.bendSemitones(), expression.modulation(), glissando,
                    resumed ? Math.max(1, end - fromMicros) : duration));
            result.add(new Event(end, Type.OFF, eventId, note, pitch, 0, note.track(),
                    note.startTick(), 0, 0, 0, null, 0));
            if (ramp != null) {
                long span = note.endTick() - note.startTick();
                int samples = (int) Math.min(120L, span);
                for (int sample = 1; sample < samples; sample++) {
                    long at = note.startTick() + Math.round(span * sample / (double) samples);
                    long time = micros(song, map.microsAt(at), tempoBpm);
                    if (time >= fromMicros) result.add(new Event(time, Type.VOLUME, eventId,
                            note, pitch, 0, note.track(), note.startTick(), rampVolume(ramp, at),
                            0, 0, null, 0));
                }
            }
        }
        result.sort(Comparator.comparingLong(Event::micros).thenComparing(Event::type)
                .thenComparingLong(Event::eventId));
        return List.copyOf(result);
    }

    private static Event expression(long at, MusicScore.ExpressionEvent expression) {
        return new Event(at, Type.EXPRESSION, -1, null, -1, 0, expression.track(),
                expression.tick(), expression.gain(), expression.bendSemitones(),
                expression.modulation(), null, 0);
    }

    public static MusicScore.ExpressionEvent expressionAt(MusicScore song, int track, long tick) {
        MusicScore.ExpressionEvent result = new MusicScore.ExpressionEvent(track, 0, 0, 0, 127, 127, false);
        for (MusicScore.ExpressionEvent candidate : song.expressions()) {
            if (candidate.tick() > tick) break;
            if (candidate.track() == track) result = candidate;
        }
        return result;
    }

    private record NoteKey(int track, long start, int pitch) { }

    /** Index once per compilation instead of scanning every expression and curve for each note. */
    private static final class AutomationIndex {
        private final List<List<MusicScore.ExpressionEvent>> expressions = new ArrayList<>();
        private final List<List<MusicScore.VolumeRamp>> ramps = new ArrayList<>();
        private final Map<NoteKey, MusicScore.Glissando> glissandos = new HashMap<>();

        AutomationIndex(MusicScore song) {
            for (int track = 0; track < song.tracks().size(); track++) {
                expressions.add(new ArrayList<>());
                ramps.add(new ArrayList<>());
            }
            for (var event : song.expressions()) expressions.get(event.track()).add(event);
            for (var ramp : song.automation().volumeRamps()) ramps.get(ramp.track()).add(ramp);
            for (var glissando : song.automation().glissandos()) glissandos.putIfAbsent(
                    new NoteKey(glissando.track(), glissando.noteStartTick(), glissando.pitch()), glissando);
        }

        MusicScore.ExpressionEvent expressionAt(int track, long tick) {
            List<MusicScore.ExpressionEvent> events = expressions.get(track);
            int low = 0, high = events.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (events.get(middle).tick() <= tick) low = middle + 1;
                else high = middle;
            }
            return low == 0 ? new MusicScore.ExpressionEvent(track, 0, 0, 0, 127, 127, false)
                    : events.get(low - 1);
        }

        MusicScore.VolumeRamp volumeRamp(MusicScore.MidiNote note) {
            for (var ramp : ramps.get(note.track())) {
                if (note.startTick() >= ramp.startTick() && note.endTick() <= ramp.endTick()
                        && note.pitch() >= ramp.minPitch() && note.pitch() <= ramp.maxPitch()) return ramp;
            }
            return null;
        }

        MusicScore.Glissando glissando(MusicScore.MidiNote note) {
            return glissandos.get(new NoteKey(note.track(), note.startTick(), note.pitch()));
        }
    }

    private static float rampVolume(MusicScore.VolumeRamp ramp, long tick) {
        double progress = Math.clamp((tick - ramp.startTick())
                / (double) Math.max(1L, ramp.endTick() - ramp.startTick() - 1L), 0, 1);
        return (float) ((ramp.startVelocity() + progress * (ramp.endVelocity() - ramp.startVelocity())) / 127.0D);
    }

    public static long micros(MusicScore song, long sourceMicros, int tempoBpm) {
        return Math.max(0, Math.round(sourceMicros * (song.initialBpm() / (double) Math.clamp(tempoBpm, 1, 3_000))));
    }

    public static long tick(MusicScore song, long playbackMicros, int tempoBpm) {
        long source = Math.max(0, Math.round(playbackMicros * (Math.clamp(tempoBpm, 1, 3_000) / (double) song.initialBpm())));
        return song.tempoMap().tickAtMicros(source, song.lengthTick());
    }
}
