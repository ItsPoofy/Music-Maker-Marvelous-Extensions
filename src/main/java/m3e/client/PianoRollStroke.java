package m3e.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Interpolates swept edits rather than depending on mouse-event frequency. */
final class PianoRollStroke {
    private PianoRollStroke() { }

    static void paint(double fromTime, double fromPitch, double toTime, double toPitch,
                      long step, BiConsumer<Long, Integer> insert) {
        long first = Math.max(0, (long) Math.floor(fromTime / step));
        long last = Math.max(0, (long) Math.floor(toTime / step));
        int samples = (int) Math.min(m3e.sheet.MusicScore.MAX_NOTES,
                Math.max(Math.abs(last - first), Math.ceil(Math.abs(toPitch - fromPitch))));
        for (int i = 0; i <= samples; i++) {
            double amount = samples == 0 ? 1 : i / (double) samples;
            long cell = Math.round(first + (last - first) * amount);
            int pitch = (int) Math.ceil(fromPitch + (toPitch - fromPitch) * amount);
            insert.accept(cell * step, pitch);
        }
    }

    static double sliceTime(int pitch, double fromTime, double fromPitch, double toTime, double toPitch) {
        if (Math.max(fromPitch, toPitch) <= pitch - 1 || Math.min(fromPitch, toPitch) > pitch) return -1;
        double amount = fromPitch == toPitch ? 1 : Math.clamp((pitch - 0.5 - fromPitch) / (toPitch - fromPitch), 0, 1);
        return fromTime + (toTime - fromTime) * amount;
    }

    static Map<Long, Integer> velocities(List<ScoreEditSession.Note> notes,
                                         double fromTime, int fromValue, double toTime, int toValue) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        ScoreEditSession.Note nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (ScoreEditSession.Note note : notes) {
            double nextDistance = Math.abs(note.start() - toTime);
            if (nextDistance < distance) { nearest = note; distance = nextDistance; }
            if (note.start() < Math.min(fromTime, toTime) || note.start() > Math.max(fromTime, toTime)) continue;
            double amount = toTime == fromTime ? 1 : (note.start() - fromTime) / (toTime - fromTime);
            result.put(note.id(), Math.clamp((int) Math.round(fromValue + (toValue - fromValue) * amount), 1, 127));
        }
        if (result.isEmpty() && nearest != null) result.put(nearest.id(), toValue);
        return result;
    }
}
