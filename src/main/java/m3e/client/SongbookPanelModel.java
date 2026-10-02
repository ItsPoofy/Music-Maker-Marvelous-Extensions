package m3e.client;

import java.util.UUID;

/** Behavior surface behind the shared Songbook-style playback composition. */
interface SongbookPanelModel {
    long playheadMicros();
    long durationMicros();
    int tempoBpm();
    default int displayTempoBpm() { return tempoBpm(); }
    void setTempoBpm(int bpm);
    double speed();
    void setSpeed(double speed);
    boolean playing();
    String stateIcon();
    void playPause();
    void stop();
    void seekMicros(long micros);
    int transpose();
    boolean canTranspose(int delta);
    void transpose(int delta);
    String loopLabel();
    void cycleLoopMode();
    boolean shuffle();
    void setShuffle(boolean value);
    default boolean canShuffle(int songCount) { return songCount > 1; }
    default boolean ensembleActive() { return false; }
    default boolean syncTempoWithEnsemble() { return false; }
    default void setSyncTempoWithEnsemble(boolean value) { }
    boolean selectBook(UUID bookId);
    void selectSong(int index);
    int selectedIndex();
    int capacity();
    boolean extractSelected();
    boolean deleteSelected();
    default boolean canExtract(boolean hasSongs) { return hasSongs && capacity() > 1; }
    default boolean canDelete(boolean hasSongs) { return hasSongs; }

    /** Non-null replaces the inventory Songbook selector label. */
    default String fixedLibraryLabel(int actualSongs, int totalSlots) { return null; }

    static SongbookPanelModel inventory() { return Inventory.INSTANCE; }

    final class Inventory implements SongbookPanelModel {
        private static final Inventory INSTANCE = new Inventory();
        private Inventory() { }

        @Override public long playheadMicros() { return SongbookClientState.playheadMicros(); }
        @Override public long durationMicros() { return SongbookClientState.selectedDurationMicros(); }
        @Override public int tempoBpm() { return SongbookClientState.selectedTempoBpm(); }
        @Override public int displayTempoBpm() { return SongbookClientState.currentTempoBpm(); }
        @Override public void setTempoBpm(int bpm) { SongbookClientState.setSelectedTempoBpm(bpm); }
        @Override public double speed() { return SongbookClientState.playbackSpeed(); }
        @Override public void setSpeed(double speed) { SongbookClientState.setPlaybackSpeed(speed); }
        @Override public boolean playing() { return SongbookClientState.playbackRunning(); }
        @Override public String stateIcon() { return SongbookClientState.stateIcon(); }
        @Override public void playPause() { SongbookClientState.playPause(); }
        @Override public void stop() { SongbookClientState.endSelectedPlayback(); }
        @Override public void seekMicros(long micros) { SongbookClientState.seekSelected(micros); }
        @Override public int transpose() { return SongbookClientState.playbackSemitoneShift(); }
        @Override public boolean canTranspose(int delta) { return SongbookClientState.canShiftPlayback(delta); }
        @Override public void transpose(int delta) { SongbookClientState.shiftPlaybackSemitones(delta); }
        @Override public String loopLabel() { return SongbookClientState.loopMode().label(); }
        @Override public void cycleLoopMode() { SongbookClientState.cycleLoopMode(); }
        @Override public boolean shuffle() { return SongbookClientState.shuffle(); }
        @Override public void setShuffle(boolean value) { SongbookClientState.setShuffle(value); }
        @Override public boolean ensembleActive() { return m3e.api.client.M3EClientIntegration.ensembleActive(); }
        @Override public boolean syncTempoWithEnsemble() { return SongbookClientState.syncTempoWithEnsemble(); }
        @Override public void setSyncTempoWithEnsemble(boolean value) {
            SongbookClientState.setSyncTempoWithEnsemble(value);
        }
        @Override public boolean selectBook(UUID bookId) { return SongbookClientState.selectBook(bookId); }
        @Override public void selectSong(int index) { SongbookClientState.select(index); }
        @Override public int selectedIndex() { return SongbookClientState.selectedIndex(); }
        @Override public int capacity() { return SongbookClientState.capacity(); }
        @Override public boolean extractSelected() { return SongbookClientState.extractSelected(); }
        @Override public boolean deleteSelected() { return SongbookClientState.deleteSelected(); }
    }
}
