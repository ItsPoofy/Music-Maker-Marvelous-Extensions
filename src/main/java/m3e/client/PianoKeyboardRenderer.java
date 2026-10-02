package m3e.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import xerca.xercamusic.common.item.IItemInstrument;

import java.util.Map;

/** Code-rendered black piano housing and full-size keybed. */
public final class PianoKeyboardRenderer {
    private static final int HOUSING_SHADOW = 0x90000000;
    private static final int HOUSING_EDGE = 0xFF08090C;
    static final int HOUSING_BEVEL = 0xFF35373B;
    private static final int HOUSING_FACE = 0xFF17181B;
    private static final int HOUSING_DECK = 0xFF1D1F23;
    static final int STATUS_FACE = 0xFF121316;
    static final int STATUS_DIVIDER = 0xFF35373B;
    private static final int KEY_GAP = 0xFF111215;
    private static final int WHITE_KEY = 0xFFF2F1EC;
    private static final int WHITE_KEY_DISABLED = 0xFF66686A;
    private static final int BLACK_KEY = 0xFF111216;
    private static final int BLACK_KEY_DISABLED = 0xFF303135;
    private static final int WHITE_LABEL = 0xFF000000;
    private static final int BLACK_LABEL = 0xFFE2E2E2;

    private PianoKeyboardRenderer() { }

    public static void render(GuiGraphicsExtractor graphics,
                              Font font,
                              PianoKeyboardGeometry.Layout layout,
                              boolean[] pressedNotes,
                              IItemInstrument instrument,
                              KeyboardNoteLayout.SelectionWindow selection,
                              Map<Integer, KeyboardChord> labels) {
        render(graphics, font, layout, pressedNotes, instrument, selection, labels,
                0, 0, null);
    }

    public static void render(GuiGraphicsExtractor graphics,
                              Font font,
                              PianoKeyboardGeometry.Layout layout,
                              boolean[] pressedNotes,
                              IItemInstrument instrument,
                              KeyboardNoteLayout.SelectionWindow selection,
                              Map<Integer, KeyboardChord> labels,
                              int screenWidth, int screenHeight,
                              PianoWaterfallState.Frame waterfall) {
        PianoKeyboardGeometry.Rect housing = layout.housing();
        PianoKeyboardGeometry.Rect visibleHousing = waterfall == null
                ? housing : waterfallHousing(layout);
        drawPanelShadow(graphics, visibleHousing.x(), visibleHousing.y() + 3,
                visibleHousing.width(), visibleHousing.height());
        drawFourPixelPanel(graphics, visibleHousing.x(), visibleHousing.y(),
                visibleHousing.width(), visibleHousing.height());
        if (waterfall == null) {
            graphics.fill(housing.x() + 6, housing.y() + 6,
                    housing.right() - 6, layout.keybed().y() - 2, HOUSING_DECK);
            PianoKeyboardGeometry.Rect status = layout.statusArea();
            graphics.fill(status.x(), status.y(), status.right(), status.bottom(), STATUS_DIVIDER);
            graphics.fill(status.x() + 1, status.y() + 1,
                    status.right() - 1, status.bottom() - 1, STATUS_FACE);
            for (int column = 1; column < 4; column++) {
                int dividerX = status.x() + status.width() * column / 4;
                graphics.fill(dividerX, status.y() + 2,
                        dividerX + 1, status.bottom() - 2, STATUS_DIVIDER);
            }
        } else {
            PianoWaterfallRenderer.render(graphics, layout, waterfall);
        }
        SmartPlaySettings.Scale guideScale = InstrumentOptions.smartPlay().scale();
        for (PianoKeyboardGeometry.Key key : layout.keys()) {
            if (!key.black()) drawWhiteKey(graphics, key,
                    isPressed(key.noteId(), pressedNotes), isAvailable(key.noteId(), instrument),
                    isSelected(key.noteId(), selection) || isGuideNote(key.noteId(), guideScale),
                    waterfall != null);
        }
        for (PianoKeyboardGeometry.Key key : layout.keys()) {
            if (key.black()) drawBlackKey(graphics, key,
                    isPressed(key.noteId(), pressedNotes), isAvailable(key.noteId(), instrument),
                    isSelected(key.noteId(), selection) || isGuideNote(key.noteId(), guideScale),
                    waterfall != null);
        }
        drawLabels(graphics, font, layout, labels);
    }

    static PianoKeyboardGeometry.Rect waterfallHousing(
            PianoKeyboardGeometry.Layout layout) {
        PianoKeyboardGeometry.Rect housing = layout.housing();
        PianoKeyboardGeometry.Rect keybed = layout.keybed();
        int margin = Math.max(1, housing.bottom() - keybed.bottom());
        int top = keybed.y() - margin;
        return new PianoKeyboardGeometry.Rect(housing.x(), top, housing.width(),
                housing.bottom() - top);
    }

    private static void drawWhiteKey(GuiGraphicsExtractor graphics,
                                     PianoKeyboardGeometry.Key key,
                                     boolean pressed, boolean available,
                                     boolean selected, boolean waterfall) {
        PianoKeyboardGeometry.Rect bounds = key.bounds();
        int outerBottom = bounds.bottom() + (pressed ? 2 : 0);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), outerBottom, KEY_GAP);
        // A white key owns its left edge and leaves exactly one separator pixel
        // at its right edge. Insetting both sides doubled every internal gap.
        int left = bounds.x();
        int right = Math.max(left + 1, bounds.right() - 1);
        int top = bounds.y() + 1;
        int bottom = pressed ? outerBottom - 1 : bounds.bottom() - 1;
        int face = !available ? WHITE_KEY_DISABLED
                : pressed ? pressedColor(false, waterfall)
                : selected ? playableColor(false) : WHITE_KEY;
        graphics.fill(left, top, right, bottom, face);
        if (available && !pressed && bottom - top > 4 && right - left > 3) {
            graphics.fill(left, top, right, top + 3, topColor(face));
        }
    }

    private static void drawBlackKey(GuiGraphicsExtractor graphics,
                                     PianoKeyboardGeometry.Key key,
                                     boolean pressed, boolean available,
                                     boolean selected, boolean waterfall) {
        PianoKeyboardGeometry.Rect bounds = key.bounds();
        int outerBottom = bounds.bottom() + (pressed ? 2 : 0);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), outerBottom, KEY_GAP);
        int inset = 1;
        int top = bounds.y() + 1;
        int bottom = pressed ? outerBottom - 1 : bounds.bottom() - 2;
        int face = !available ? BLACK_KEY_DISABLED
                : pressed ? pressedColor(true, waterfall)
                : selected ? playableColor(true) : BLACK_KEY;
        graphics.fill(bounds.x() + inset, top, bounds.right() - inset, bottom, face);
        if (!pressed && bottom - top > 4 && bounds.width() > 4) {
            graphics.fill(bounds.x() + inset, top, bounds.right() - inset, top + 3,
                    topColor(face));
        }
        if (!pressed && bottom - top > 4 && bounds.width() > 2) {
            // Keep the underside inside the black-key face. A cool charcoal rim
            // remains visible on normal keys, while tinted keys retain a darker
            // shadow of their own hue instead of borrowing a hard black strip.
            graphics.fill(bounds.x() + 1, bottom - 2, bounds.right() - 1, bottom,
                    bottomColor(face));
        }
    }

    private static int pressedColor(boolean black, boolean waterfall) {
        int rgb = waterfall ? InstrumentOptions.waterfallColor()
                : InstrumentOptions.pressedKeyColor();
        if (!black) return 0xFF000000 | rgb;
        if (!waterfall && rgb == InstrumentOptions.DEFAULT_PRESSED_KEY_COLOR) return 0xFF216FA9;
        int red = Math.round(((rgb >> 16) & 0xFF) * 0.62F);
        int green = Math.round(((rgb >> 8) & 0xFF) * 0.78F);
        int blue = Math.round((rgb & 0xFF) * 0.82F);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static boolean isSelected(int noteId, KeyboardNoteLayout.SelectionWindow selection) {
        return selection != null && !selection.isEmpty()
                && noteId >= selection.fromInclusive() && noteId < selection.toExclusive();
    }

    static boolean isGuideNote(int noteId, SmartPlaySettings.Scale scale) {
        return scale.enabled() && scale.keyMode() == SmartPlaySettings.KeyMode.GUIDE
                && SmartPlayMapper.allowed(IItemInstrument.idToNote(noteId), scale.root(),
                SmartPlayScaleCatalog.scale(scale));
    }

    static int playableColor(boolean black) {
        return playableColor(black, InstrumentOptions.playableKeyColor());
    }

    static int playableColor(boolean black, int rgb) {
        if (!black) return 0xFF000000 | rgb;
        int red = Math.round(((rgb >> 16) & 0xFF) * 0.48F);
        int green = Math.round(((rgb >> 8) & 0xFF) * 0.46F);
        int blue = Math.round((rgb & 0xFF) * 0.32F);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static int topColor(int face) {
        int red = face >>> 16 & 0xFF;
        int green = face >>> 8 & 0xFF;
        int blue = face & 0xFF;
        red += Math.round((255 - red) * 0.28F);
        green += Math.round((255 - green) * 0.28F);
        blue += Math.round((255 - blue) * 0.28F);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static int bottomColor(int face) {
        if (face == BLACK_KEY) return 0xFF292C35;
        if (face == BLACK_KEY_DISABLED) return 0xFF1D2026;
        int red = Math.round((face >>> 16 & 0xFF) * 0.42F);
        int green = Math.round((face >>> 8 & 0xFF) * 0.40F);
        int blue = Math.round((face & 0xFF) * 0.38F);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void drawLabels(GuiGraphicsExtractor graphics, Font font,
                                   PianoKeyboardGeometry.Layout layout,
                                   Map<Integer, KeyboardChord> labels) {
        if (labels == null || labels.isEmpty()) return;
        for (PianoKeyboardGeometry.Key key : layout.keys()) {
            KeyboardChord chord = labels.get(key.noteId());
            if (chord == null) continue;
            PianoKeyboardGeometry.Rect bounds = key.bounds();
            var text = chord.shortComponent();
            int textWidth = font.width(text);
            if (textWidth > bounds.width() + 2) continue;
            int x = bounds.x() + (bounds.width() - textWidth) / 2;
            int y = key.black()
                    ? bounds.bottom() - 11
                    : layout.keybed().bottom() - 12;
            graphics.text(font, text, x, y,
                    key.black() ? BLACK_LABEL : WHITE_LABEL, false);
        }
    }

    private static boolean isPressed(int noteId, boolean[] pressedNotes) {
        return pressedNotes != null && noteId >= 0
                && noteId < pressedNotes.length && pressedNotes[noteId];
    }

    private static boolean isAvailable(int noteId, IItemInstrument instrument) {
        return instrument.getSound(IItemInstrument.idToNote(noteId)) != null;
    }

    static void drawPanelShadow(GuiGraphicsExtractor graphics,
                                int x, int y, int width, int height) {
        drawClippedPanel(graphics, x, y, width, height, HOUSING_SHADOW);
    }

    /** Four-pixel container edge matching the shared inventory edge ratio. */
    static void drawFourPixelPanel(GuiGraphicsExtractor graphics,
                                   int x, int y, int width, int height) {
        if (width < 8 || height < 8) return;
        // One-pixel clipped outer corners, then a two-pixel bevel and face.
        drawClippedPanel(graphics, x, y, width, height, HOUSING_EDGE);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2,
                HOUSING_BEVEL);
        graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4,
                HOUSING_FACE);
    }

    /** Same clipped-corner silhouette for the housing and its shadow. */
    private static void drawClippedPanel(GuiGraphicsExtractor graphics,
                                         int x, int y, int width, int height,
                                         int color) {
        if (width < 4 || height < 4) return;
        graphics.fill(x + 2, y, x + width - 2, y + 1, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1,
                color);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height,
                color);
    }
}
