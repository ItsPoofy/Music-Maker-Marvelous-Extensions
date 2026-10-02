package m3e.client;

import java.util.BitSet;

final class SongbookPlaybackQueue {
    private static final int SINGLE_SONG = 1;

    enum LoopMode {
        NO_LOOP("No Loop"),
        FINISH_SONGBOOK("Finish Songbook"),
        LOOP_SONG("Loop Song"),
        LOOP_SONGBOOK("Loop Songbook");

        private final String displayLabel;

        LoopMode(String label) { this.displayLabel = label; }

        String label() { return displayLabel; }

        LoopMode next() {
            return switch (this) {
                case NO_LOOP -> FINISH_SONGBOOK;
                case FINISH_SONGBOOK -> LOOP_SONG;
                case LOOP_SONG -> LOOP_SONGBOOK;
                case LOOP_SONGBOOK -> NO_LOOP;
            };
        }
    }

    private LoopMode activeLoopMode = LoopMode.NO_LOOP;
    private boolean shuffleEnabled;
    private final BitSet completedShuffleSongs = new BitSet();
    private int shuffleSongCount;

    LoopMode loopMode() { return activeLoopMode; }

    void loopMode(LoopMode value) {
        activeLoopMode = value == null ? LoopMode.NO_LOOP : value;
        clearSequence();
    }

    LoopMode cycleLoopMode() {
        activeLoopMode = activeLoopMode.next();
        clearSequence();
        return activeLoopMode;
    }

    boolean shuffle() { return shuffleEnabled; }

    void shuffle(boolean value) {
        shuffleEnabled = value;
        clearSequence();
    }

    void selectionChanged() { clearSequence(); }

    void reset() {
        activeLoopMode = LoopMode.NO_LOOP;
        shuffleEnabled = false;
        clearSequence();
    }

    /** Returns the next stored-song index, or -1 when playback should stop. */
    int nextIndex(int currentIndex, int songCount, int shuffleChoice) {
        if (songCount <= 0 || activeLoopMode == LoopMode.NO_LOOP
                || activeLoopMode == LoopMode.LOOP_SONG) return -1;
        if (songCount == SINGLE_SONG) {
            return activeLoopMode == LoopMode.LOOP_SONGBOOK ? 0 : -1;
        }
        int current = Math.clamp(currentIndex, 0, songCount - 1);
        if (!shuffleEnabled) {
            int next = current + 1;
            return next < songCount ? next
                    : activeLoopMode == LoopMode.LOOP_SONGBOOK ? 0 : -1;
        }
        if (activeLoopMode == LoopMode.FINISH_SONGBOOK) {
            if (shuffleSongCount != songCount) clearSequence(songCount);
            completedShuffleSongs.set(current);
            int remaining = songCount - completedShuffleSongs.cardinality();
            if (remaining <= 0) return -1;
            int choice = Math.floorMod(shuffleChoice, remaining);
            for (int index = completedShuffleSongs.nextClearBit(0);
                 index >= 0 && index < songCount;
                 index = completedShuffleSongs.nextClearBit(index + 1)) {
                if (choice-- == 0) return index;
            }
            return -1;
        }
        int offset = Math.floorMod(shuffleChoice, songCount - 1) + 1;
        return (current + offset) % songCount;
    }

    private void clearSequence() { clearSequence(0); }

    private void clearSequence(int songCount) {
        completedShuffleSongs.clear();
        shuffleSongCount = songCount;
    }
}
