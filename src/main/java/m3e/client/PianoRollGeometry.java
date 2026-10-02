package m3e.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One visible geometry list for drawing, picking, and swept editing. */
final class PianoRollGeometry {
    enum Part { BODY, START, END }
    record Rect(ScoreEditSession.Note note, int left, int right, int top, int bottom) { }
    record Hit(ScoreEditSession.Note note, Part part) { }
    private record View(long revision, long selection, double time, double scale, double pitch,
                        int row, int x, int y, int width, int height) { }
    private View view;
    private List<Rect> visible = List.of();

    List<Rect> visible(ScoreEditSession session, double time, double scale, double pitch,
                       int row, int x, int y, int width, int height) {
        View next = new View(session.revision(), session.selectionRevision(), time, scale, pitch,
                row, x, y, width, height);
        if (next.equals(view)) return visible;
        view = next;
        int origin = y + (int) Math.round(pitch * row);
        double end = time + width / scale;
        ArrayList<Rect> result = new ArrayList<>();
        for (ScoreEditSession.Note note : session.notes()) {
            int top = origin - note.pitch() * row;
            if (note.end() < time - 4 / scale || note.start() > end + 4 / scale
                    || top + row <= y || top >= y + height) continue;
            // Bound very long imported notes before converting to GUI coordinates.
            int left = x + (int) Math.clamp(Math.round((note.start() - time) * scale), -1_000_000L, 1_000_000L);
            int right = Math.max(left + 3, x + (int) Math.clamp(
                    Math.round((note.end() - time) * scale), -1_000_000L, 1_000_000L));
            result.add(new Rect(note, left, right, top, top + row));
        }
        result.sort(Comparator.comparing((Rect r) -> session.selected(r.note().id()))
                .thenComparingLong(r -> r.note().id()));
        visible = List.copyOf(result);
        return visible;
    }

    Hit hit(double x, double y, boolean leftResize, boolean resizing) {
        Rect best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = visible.size() - 1; i >= 0; i--) {
            Rect rect = visible.get(i);
            if (y < rect.top() || y >= rect.bottom()) continue;
            double outside = Math.max(0, Math.max(rect.left() - x, x - (rect.right() - 1)));
            if (outside > 2 || outside >= distance) continue;
            best = rect;
            distance = outside;
            if (outside == 0) break;
        }
        if (best == null) return null;
        int edge = resizing ? Math.min(4, Math.max(0,
                (best.right() - best.left() - 7) / (leftResize ? 2 : 1))) : 0;
        Part part = edge > 0 && x >= best.right() - edge ? Part.END
                : edge > 0 && leftResize && x < best.left() + edge ? Part.START : Part.BODY;
        return new Hit(best.note(), part);
    }

    ScoreEditSession.Note nearest(double x, double y) {
        return visible.stream().min(Comparator.comparingDouble(rect -> {
            double dx = Math.max(0, Math.max(rect.left() - x, x - rect.right()));
            double dy = Math.max(0, Math.max(rect.top() - y, y - rect.bottom()));
            return dx * dx + dy * dy;
        })).map(Rect::note).orElse(null);
    }

    List<ScoreEditSession.Note> swept(double x1, double y1, double x2, double y2) {
        return visible.stream().filter(r -> intersects(r, x1, y1, x2, y2)).map(Rect::note).toList();
    }

    static boolean intersects(Rect r, double x1, double y1, double x2, double y2) {
        double low = 0, high = 1;
        double[] p = {x1 - x2, x2 - x1, y1 - y2, y2 - y1};
        double[] q = {x1 - r.left(), r.right() - 1 - x1, y1 - r.top(), r.bottom() - 1 - y1};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) { if (q[i] < 0) return false; }
            else {
                double t = q[i] / p[i];
                if (p[i] < 0) low = Math.max(low, t); else high = Math.min(high, t);
                if (low > high) return false;
            }
        }
        return true;
    }
}
