package m3e.client;

/** Integer GUI geometry shared by scrolling panels. */
final class ScrollbarGeometry {
    private ScrollbarGeometry() { }
    static int scrollThumbHeight(int trackHeight, int viewportHeight,
                                 int contentHeight) {
        if (contentHeight <= viewportHeight) return trackHeight;
        return Math.clamp(trackHeight * viewportHeight / contentHeight,
                Math.min(16, trackHeight), trackHeight);
    }

    static int scrollThumbY(int trackY, int trackHeight, int thumbHeight,
                            int viewportHeight, int contentHeight, int current) {
        int maximum = Math.max(1, contentHeight - viewportHeight);
        int travel = Math.max(0, trackHeight - thumbHeight);
        return trackY + travel * Math.clamp(current, 0, maximum) / maximum;
    }

}
