package m3e.client;

/** Shared whole-pixel geometry for instrument-screen extension panels. */
public final class InstrumentPanelGeometry {
    public static final int WIDTH = 160;
    public static final int BUTTON_HEIGHT = 18;
    public static final int GAP = 2;
    public static final int SECTION_GAP = 6;
    public static final int SECTION_INSET = 6;
    public static final int MAX_WORKSPACE_WIDTH = 520;
    public static final int MAX_BUTTON_WIDTH = 260;
    /** Roomy canonical drawer canvas, rendered only at whole physical-pixel tiers. */
    public static final int FIXED_DRAWER_CANVAS_WIDTH = 538;
    public static final int FIXED_DRAWER_CANVAS_HEIGHT = 220;
    public static final int KEYBOARD_WIDTH = 401;
    public static final int KEYBOARD_HEIGHT = 201;

    private InstrumentPanelGeometry() { }

    public static int boundedWidth(int available, int maximum) {
        return Math.max(1, Math.min(Math.max(1, available), maximum));
    }

    public static int centeredX(int x, int available, int width) {
        return x + Math.max(0, (available - width) / 2);
    }

    public static int columnX(int x, int width, int gap, int columns, int index) {
        int contentWidth = Math.max(0, width - Math.max(0, gap) * (columns - 1));
        return x + contentWidth * index / columns + Math.max(0, gap) * index;
    }

    public static int columnWidth(int width, int gap, int columns, int index) {
        int contentWidth = Math.max(0, width - Math.max(0, gap) * (columns - 1));
        return Math.max(1, contentWidth * (index + 1) / columns
                - contentWidth * index / columns);
    }

    /** Distributes fixed-height rows through an available vertical interval. */
    public static int distributedGap(int top, int bottom, int rowHeight,
                                     int rows, int minimumGap) {
        int intervals = Math.max(0, rows - 1);
        if (intervals == 0) return Math.max(0, minimumGap);
        int free = Math.max(0, bottom - top - Math.max(0, rowHeight) * rows);
        return Math.max(Math.max(0, minimumGap), free / intervals);
    }

    public static int top(int screenHeight) {
        return Math.max(8, (screenHeight - KEYBOARD_HEIGHT) / 2);
    }

    public static int keyboardLeft(int screenWidth) {
        return (screenWidth - KEYBOARD_WIDTH) / 2;
    }

    public static int keyboardRight(int screenWidth) {
        return keyboardLeft(screenWidth) + KEYBOARD_WIDTH;
    }

    /** Centers a side panel inside the free area to the left of the keyboard. */
    public static int leftPanelX(int screenWidth, int panelWidth) {
        int freeWidth = Math.max(0, keyboardLeft(screenWidth));
        return Math.max(4, (freeWidth - panelWidth) / 2);
    }

    /** Centers a side panel inside the free area to the right of the keyboard. */
    public static int rightPanelX(int screenWidth, int panelWidth) {
        int keyboardRight = keyboardRight(screenWidth);
        int freeWidth = Math.max(0, screenWidth - keyboardRight);
        return Math.min(Math.max(4, screenWidth - panelWidth - 4),
                keyboardRight + Math.max(0, (freeWidth - panelWidth) / 2));
    }
}
