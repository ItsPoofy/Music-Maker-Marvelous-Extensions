package m3e.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import m3e.sheet.MusicScore;

import java.util.List;

/** Synthesia-style note waterfall aligned to the existing piano key geometry. */
public final class PianoWaterfallRenderer {
    static final long VISIBLE_MICROS = 4_000_000L;
    private static MusicScore cachedSong;
    private static int cachedBpm;
    private static int cachedPitchShift;
    private static PianoWaterfallTimeline timeline;

    private PianoWaterfallRenderer() { }

    public static void render(GuiGraphicsExtractor graphics,
                              PianoKeyboardGeometry.Layout layout,
                              PianoWaterfallState.Frame frame) {
        if (frame == null || layout.keys().isEmpty()) return;
        PianoKeyboardGeometry.Rect keybed = layout.keybed();
        int top = 2;
        int bottom = keybed.y();
        if (bottom - top < 8) return;

        graphics.enableScissor(keybed.x(), top, keybed.right(), bottom);
        drawNotes(graphics, layout, top, bottom, frame);
        graphics.disableScissor();
        graphics.fill(keybed.x(), bottom - 1, keybed.right(), bottom,
                0xFF9E5559);
    }

    private static void drawNotes(GuiGraphicsExtractor graphics,
                                  PianoKeyboardGeometry.Layout layout,
                                  int top, int bottom,
                                  PianoWaterfallState.Frame frame) {
        PianoWaterfallTimeline activeTimeline = timeline(frame);
        List<PianoWaterfallTimeline.RenderNote> notes = activeTimeline.visible(
                frame.playheadMicros(), VISIBLE_MICROS);
        for (boolean black : new boolean[]{false, true}) {
            for (PianoWaterfallTimeline.RenderNote note : notes) {
                PianoKeyboardGeometry.Key key = layout.key(note.noteId());
                if (key == null || key.black() != black) continue;
                drawNote(graphics, key.bounds(), top, bottom,
                        frame.playheadMicros(), note);
            }
        }
    }

    private static PianoWaterfallTimeline timeline(PianoWaterfallState.Frame frame) {
        if (timeline == null || cachedSong != frame.song()
                || cachedBpm != frame.bpm() || cachedPitchShift != frame.pitchShift()) {
            cachedSong = frame.song();
            cachedBpm = frame.bpm();
            cachedPitchShift = frame.pitchShift();
            timeline = new PianoWaterfallTimeline(
                    frame.song(), frame.bpm(), frame.pitchShift());
        }
        return timeline;
    }

    private static void drawNote(GuiGraphicsExtractor graphics,
                                 PianoKeyboardGeometry.Rect key,
                                 int top, int bottom, long playhead,
                                 PianoWaterfallTimeline.RenderNote note) {
        int height = bottom - top;
        int front = projectedY(note.startMicros(), playhead, top, bottom);
        int tail = projectedY(note.releaseMicros(), playhead, top, bottom);
        int y0 = Math.min(tail, front);
        int y1 = Math.max(tail, front);
        if (y1 - y0 < 3) y0 = Math.max(top, y1 - 3);

        // Match the exact rendered key column. At GUI scale 3 the previous
        // one-pixel inset on each side made a 24px black key's note only 18px.
        int x0 = key.x();
        int x1 = key.right();
        int face = noteColor(note.velocity());
        graphics.fill(x0, y0, x1, y1,
                0xFF000000 | InstrumentOptions.waterfallOutlineColor());
        if (x1 - x0 > 2 && y1 - y0 > 2) {
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, face);
            if (shouldDrawShine(x1 - x0, y1 - y0,
                    note.releaseMicros() - note.startMicros(), height)) {
                graphics.fill(x0 + 2, y0 + 2, x1 - 2, y0 + 3,
                        PianoKeyboardRenderer.topColor(face));
            }
        }
        if (note.startMicros() <= playhead && note.releaseMicros() >= playhead) {
            int glow = 0x80000000 | (InstrumentOptions.waterfallColor() & 0xFFFFFF);
            graphics.fill(Math.max(key.x(), x0 - 2), bottom - 3,
                    Math.min(key.right(), x1 + 2), bottom, glow);
        }
    }

    static int noteColor(int velocity) {
        int rgb = InstrumentOptions.waterfallColor();
        float strength = 0.72F + Math.clamp(velocity, 1, 127) / 127.0F * 0.28F;
        int red = Math.clamp(Math.round((rgb >>> 16 & 0xFF) * strength), 0, 255);
        int green = Math.clamp(Math.round((rgb >>> 8 & 0xFF) * strength), 0, 255);
        int blue = Math.clamp(Math.round((rgb & 0xFF) * strength), 0, 255);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static int projectedY(long noteMicros, long playhead,
                          int top, int bottom) {
        int height = bottom - top;
        double pixelsPerMicro = height / (double) VISIBLE_MICROS;
        long notePixel = Math.round(noteMicros * pixelsPerMicro);
        long playheadPixel = Math.round(playhead * pixelsPerMicro);
        long y = (long) bottom - notePixel + playheadPixel;
        return (int) Math.max(top, Math.min(bottom, y));
    }

    /**
     * Uses duration rather than independently rounded endpoints so a short
     * moving note cannot gain and lose its shine on alternating frames.
     */
    static boolean shouldDrawShine(int noteWidth, int renderedHeight,
                                   long durationMicros, int waterfallHeight) {
        double stableHeight = durationMicros * waterfallHeight
                / (double) VISIBLE_MICROS;
        return noteWidth >= 6 && renderedHeight >= 5 && stableHeight >= 5.0D;
    }

}
