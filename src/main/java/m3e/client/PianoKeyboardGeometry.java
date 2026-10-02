package m3e.client;

import xerca.xercamusic.common.item.IItemInstrument;

import java.util.ArrayList;
import java.util.List;

/** Responsive, renderer-independent geometry for the instrument piano. */
public final class PianoKeyboardGeometry {
    public static final int STANDARD_FIRST_NOTE_ID = 0;   // A0
    public static final int STANDARD_NOTE_COUNT = 88;
    public static final int STANDARD_LAST_NOTE_ID = 87;  // C8
    public static final int COMPACT_FIRST_NOTE_ID = 15;  // C2
    public static final int COMPACT_NOTE_COUNT = 61;
    public static final int COMPACT_LAST_NOTE_ID = 75;   // C7

    /** Compatibility aliases for callers that mean the complete piano. */
    public static final int FIRST_NOTE_ID = STANDARD_FIRST_NOTE_ID;
    public static final int NOTE_COUNT = STANDARD_NOTE_COUNT;
    public static final int LAST_NOTE_ID = STANDARD_LAST_NOTE_ID;
    private static final int MIN_SIDE_MARGIN = 10;
    private static final int MAX_SIDE_MARGIN = 40;
    private static final int VERTICAL_LIFT = 8;
    private static final int DECK_INSET = 6;
    private static final int STATUS_SURROUND = 2;
    private static final int EXPRESSION_BAY_WIDTH = 44;
    private static final int EXPRESSION_BAY_GAP = 6;
    private static final int COMPACT_HOUSING_WIDTH =
            InstrumentPanelGeometry.FIXED_DRAWER_CANVAS_WIDTH;
    private static final int REFERENCE_HOUSING_HEIGHT = 95;
    private static final int EXPANDED_DRAWER_ANCHOR =
            InstrumentPanelGeometry.FIXED_DRAWER_CANVAS_HEIGHT + 18;

    private PianoKeyboardGeometry() { }

    public static Layout layout(int screenWidth, int screenHeight) {
        return layout(screenWidth, screenHeight, STANDARD_FIRST_NOTE_ID,
                STANDARD_NOTE_COUNT, Mode.STANDARD);
    }

    public static Layout layout(int screenWidth, int screenHeight, Mode mode) {
        Mode selected = mode == null ? Mode.STANDARD : mode;
        return selected == Mode.COMPACT
                ? layout(screenWidth, screenHeight, COMPACT_FIRST_NOTE_ID,
                COMPACT_NOTE_COUNT, selected)
                : layout(screenWidth, screenHeight, STANDARD_FIRST_NOTE_ID,
                STANDARD_NOTE_COUNT, selected);
    }

    public static Layout layout(int screenWidth, int screenHeight, int noteCount) {
        return layout(screenWidth, screenHeight, STANDARD_FIRST_NOTE_ID,
                noteCount, Mode.STANDARD);
    }

    private static Layout layout(int screenWidth, int screenHeight,
                                 int firstNoteId, int noteCount,
                                 Mode requestedMode) {
        Mode mode = requestedMode == null ? Mode.STANDARD : requestedMode;
        int safeWidth = Math.max(1, screenWidth);
        int safeHeight = Math.max(1, screenHeight);
        int boundedNotes = Math.max(0, noteCount);
        int whiteCount = countWhiteKeys(firstNoteId, boundedNotes);
        int sideMargin = Math.clamp(safeWidth / 24, MIN_SIDE_MARGIN, MAX_SIDE_MARGIN);
        int preferredWidth = Math.max(360, safeWidth * 4 / 5);
        int standardHousingWidth = Math.max(1,
                Math.min(safeWidth - sideMargin * 2, preferredWidth));
        int horizontalPadding = Math.clamp(standardHousingWidth / 48, 8, 18);
        int keyHousingWidth = standardHousingWidth;
        if (mode == Mode.COMPACT && whiteCount > 0) {
            int standardWhiteCount = countWhiteKeys(
                    STANDARD_FIRST_NOTE_ID, STANDARD_NOTE_COUNT);
            int standardKeybedWidth = Math.max(1,
                    standardHousingWidth - horizontalPadding * 2);
            int compactKeybedWidth = Math.max(1, Math.round(
                    standardKeybedWidth * (float) whiteCount / standardWhiteCount));
            keyHousingWidth = Math.min(standardHousingWidth,
                    compactKeybedWidth + horizontalPadding * 2);
        }
        int maximumHousingWidth = Math.max(1, safeWidth - sideMargin * 2);
        int housingWidth = Math.min(maximumHousingWidth,
                keyHousingWidth + EXPRESSION_BAY_WIDTH + EXPRESSION_BAY_GAP);
        if (mode == Mode.COMPACT) {
            // The snug piano is the fixed drawer's width at every normal
            // viewport. It neither stretches on large screens nor leaves the
            // drawer wider than its keyboard on small screens.
            housingWidth = Math.min(maximumHousingWidth, COMPACT_HOUSING_WIDTH);
        }
        int bottomMargin = Math.clamp(safeHeight / 11, 30, 60);
        int baseHousingHeight = Math.min(REFERENCE_HOUSING_HEIGHT,
                Math.max(1, safeHeight - bottomMargin - 12));
        int housingX = (safeWidth - housingWidth) / 2;
        int baseHousingY = Math.max(2,
                safeHeight - bottomMargin - baseHousingHeight - VERTICAL_LIFT);
        // Reserve the native 220-pixel drawer on common 16:9 viewports without
        // changing the keyboard's vertical dimensions.
        if (safeHeight >= EXPANDED_DRAWER_ANCHOR + baseHousingHeight + 4
                && baseHousingY < EXPANDED_DRAWER_ANCHOR) {
            baseHousingY = EXPANDED_DRAWER_ANCHOR;
        }
        int housingY = baseHousingY;
        // Preserve every key dimension. The extra bottom breathing room grows
        // the casing by two pixels instead of shortening the keybed.
        int housingHeight = baseHousingHeight + 2;
        Rect housing = new Rect(housingX, housingY, housingWidth, housingHeight);

        int topDeck = Math.clamp(baseHousingHeight / 4, 24, 34);
        topDeck = Math.min(topDeck, Math.max(0, baseHousingHeight - 2));
        int bottomPadding = Math.clamp(baseHousingHeight / 18, 5, 8);
        bottomPadding = Math.min(bottomPadding,
                Math.max(0, baseHousingHeight - topDeck - 1));
        int availableInterior = Math.max(1, housing.width() - horizontalPadding * 2);
        int expressionWidth = Math.min(Math.max(1, availableInterior - 3),
                Math.min(EXPRESSION_BAY_WIDTH, Math.max(24, availableInterior / 8)));
        int expressionGap = Math.min(EXPRESSION_BAY_GAP,
                Math.max(1, availableInterior - expressionWidth - 1));
        Rect expressionBay = new Rect(housing.x() + horizontalPadding,
                baseHousingY + DECK_INSET, expressionWidth,
                Math.max(1, baseHousingHeight - DECK_INSET - bottomPadding));
        Rect rawKeybed = new Rect(expressionBay.right() + expressionGap,
                baseHousingY + topDeck,
                Math.max(1, availableInterior - expressionWidth - expressionGap),
                Math.max(1, baseHousingHeight - topDeck - bottomPadding));
        Rect keybed = uniformKeybed(rawKeybed, whiteCount);
        if (mode == Mode.STANDARD && keybed.width() < rawKeybed.width()) {
            // Trim only the indivisible keybed remainder, then recenter the
            // completed whole-pixel piano as one unit. Moving every interior
            // rectangle by the same integer offset preserves the exact casing,
            // expression-bay and keybed margins.
            int finalHousingWidth = housing.width()
                    - (rawKeybed.width() - keybed.width());
            int finalHousingX = (safeWidth - finalHousingWidth) / 2;
            int shiftX = finalHousingX - housing.x();
            housing = new Rect(finalHousingX, housing.y(), finalHousingWidth,
                    housing.height());
            expressionBay = translateX(expressionBay, shiftX);
            keybed = translateX(keybed, shiftX);
        }
        // Kept as a zero-height compatibility anchor for callers compiled
        // against the pre-drawer geometry. The removed toolbar no longer
        // reserves any visible casing space.
        Rect toolbarArea = new Rect(keybed.x(), housing.y(), keybed.width(), 0);
        Rect statusDeckArea = new Rect(keybed.x(), baseHousingY + DECK_INSET,
                keybed.width(), Math.max(1, keybed.y() - baseHousingY - DECK_INSET));
        Rect statusArea = new Rect(statusDeckArea.x(),
                statusDeckArea.y() + STATUS_SURROUND, statusDeckArea.width(),
                Math.max(1, statusDeckArea.height() - STATUS_SURROUND * 2));
        Rect octaveControlsArea = new Rect(expressionBay.x(), statusArea.y(),
                expressionBay.width(), statusArea.height());
        Rect expressionWheelsArea = new Rect(expressionBay.x(), keybed.y(),
                expressionBay.width(), keybed.height());

        if (whiteCount == 0) {
            return new Layout(mode, firstNoteId, housing, toolbarArea,
                    statusDeckArea, statusArea, octaveControlsArea,
                    expressionWheelsArea, keybed, List.of());
        }

        int averageWhiteWidth = Math.max(1, keybed.width() / whiteCount);
        int requestedBlackWidth = Math.max(4, averageWhiteWidth * 2 / 3);
        if ((requestedBlackWidth & 1) != 0) requestedBlackWidth++;
        int maximumBlackWidth = Math.max(2,
                Math.max(2, averageWhiteWidth - 1) / 2 * 2);
        int blackWidth = Math.min(requestedBlackWidth, maximumBlackWidth);
        int blackHeight = Math.max(1, keybed.height() * 5 / 8);
        ArrayList<Key> keys = new ArrayList<>(boundedNotes);
        int whitesBefore = 0;
        for (int noteId = firstNoteId;
             noteId < firstNoteId + boundedNotes; noteId++) {
            boolean black = isBlack(noteId);
            if (black) {
                int boundary = keybed.x() + whitesBefore * keybed.width() / whiteCount;
                keys.add(new Key(noteId,
                        new Rect(boundary - blackWidth / 2, keybed.y(),
                                blackWidth, blackHeight), true));
            } else {
                int x0 = keybed.x() + whitesBefore * keybed.width() / whiteCount;
                int x1 = keybed.x() + (whitesBefore + 1) * keybed.width() / whiteCount;
                keys.add(new Key(noteId,
                        new Rect(x0, keybed.y(), Math.max(1, x1 - x0), keybed.height()),
                        false));
                whitesBefore++;
            }
        }
        return new Layout(mode, firstNoteId, housing, toolbarArea,
                statusDeckArea, statusArea, octaveControlsArea,
                expressionWheelsArea, keybed, keys);
    }

    private static int countWhiteKeys(int firstNoteId, int noteCount) {
        int count = 0;
        for (int noteId = firstNoteId; noteId < firstNoteId + noteCount; noteId++) {
            if (!isBlack(noteId)) count++;
        }
        return count;
    }

    /**
     * Keeps the expression/control bay and the keybed's left edge untouched.
     * Only the keybed's right edge absorbs the indivisible remainder, so all
     * static key coordinates remain integer-aligned without widening controls.
     */
    private static Rect uniformKeybed(Rect raw, int whiteCount) {
        if (whiteCount <= 0 || raw.width() < whiteCount) return raw;
        int whiteWidth = Math.max(1, raw.width() / whiteCount);
        return new Rect(raw.x(), raw.y(), whiteWidth * whiteCount, raw.height());
    }

    private static Rect translateX(Rect rect, int amount) {
        return amount == 0 ? rect
                : new Rect(rect.x() + amount, rect.y(), rect.width(), rect.height());
    }

    public static boolean isBlack(int noteId) {
        int pitchClass = Math.floorMod(IItemInstrument.idToNote(noteId), 12);
        return pitchClass == 1 || pitchClass == 3 || pitchClass == 6
                || pitchClass == 8 || pitchClass == 10;
    }

    public enum Mode { STANDARD, COMPACT }

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    public record Key(int noteId, Rect bounds, boolean black) { }

    public record Layout(Mode mode, int firstNoteId, Rect housing, Rect toolbarArea,
                         Rect statusDeckArea, Rect statusArea,
                         Rect octaveControlsArea, Rect expressionWheelsArea,
                         Rect keybed,
                         List<Key> keys) {
        public Layout {
            keys = List.copyOf(keys);
        }

        /** Black keys overlap white keys and therefore always win hit testing. */
        public int hitTest(double x, double y) {
            for (Key key : keys) {
                if (key.black() && key.bounds().contains(x, y)) return key.noteId();
            }
            for (Key key : keys) {
                if (!key.black() && key.bounds().contains(x, y)) return key.noteId();
            }
            return -1;
        }

        public Key key(int noteId) {
            int index = noteId - firstNoteId;
            return index >= 0 && index < keys.size() ? keys.get(index) : null;
        }

        public int lastNoteId() {
            return keys.isEmpty() ? firstNoteId - 1 : keys.getLast().noteId();
        }
    }
}
