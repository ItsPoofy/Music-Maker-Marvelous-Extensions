package m3e.client;

import net.minecraft.client.gui.screens.Screen;
import m3e.sheet.MusicScore;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

interface SongbookManagementModel {
    boolean available();
    UUID bookId();
    String bookLabel();
    int revision();
    List<SongbookClientState.SongEntry> entries();
    int capacity();
    int encodedBytes();
    int selectedIndex();
    void select(int index);
    boolean renameBook(String name);
    boolean renameSelected(String name);
    boolean moveSelected(int targetIndex);
    boolean extractSelected();
    boolean deleteSelected();
    int transpose();
    boolean canTranspose(int delta);
    boolean transpose(int delta);
    int tempoBpm();
    boolean setTempoBpm(int bpm);
    boolean setInstrument(String instrumentId);
    MusicScore previewSong();
    boolean handleDrop(Screen screen, List<Path> paths);
    void close();

    static SongbookManagementModel inventory() { return Inventory.INSTANCE; }

    final class Inventory implements SongbookManagementModel {
        private static final Inventory INSTANCE = new Inventory();
        private Inventory() { }

        @Override public boolean available() { return SongbookClientState.hasManagedBook(); }
        @Override public UUID bookId() { return SongbookClientState.managedBookId(); }
        @Override public String bookLabel() { return SongbookClientState.managedBookLabel(); }
        @Override public int revision() { return SongbookClientState.managedRevision(); }
        @Override public List<SongbookClientState.SongEntry> entries() {
            return SongbookClientState.managedSnapshots();
        }
        @Override public int capacity() { return SongbookClientState.managedCapacity(); }
        @Override public int encodedBytes() { return SongbookClientState.managedEncodedBytes(); }
        @Override public int selectedIndex() { return SongbookClientState.managedSelectedIndex(); }
        @Override public void select(int index) { SongbookClientState.selectManaged(index); }
        @Override public boolean renameBook(String name) {
            return SongbookClientState.renameManagedBook(name);
        }
        @Override public boolean renameSelected(String name) {
            return SongbookClientState.renameManagedSelected(name);
        }
        @Override public boolean moveSelected(int targetIndex) {
            return SongbookClientState.moveManagedSelected(targetIndex);
        }
        @Override public boolean extractSelected() {
            return SongbookClientState.extractManagedSelected();
        }
        @Override public boolean deleteSelected() {
            return SongbookClientState.deleteManagedSelected();
        }
        @Override public int transpose() { return SongbookClientState.managedSemitoneShift(); }
        @Override public boolean canTranspose(int delta) {
            return SongbookClientState.canShiftManaged(delta);
        }
        @Override public boolean transpose(int delta) {
            return SongbookClientState.shiftManagedSemitones(delta);
        }
        @Override public int tempoBpm() {
            int selected = selectedIndex();
            List<SongbookClientState.SongEntry> songs = entries();
            return selected < 0 || selected >= songs.size() ? 120 : songs.get(selected).tempoBpm();
        }
        @Override public boolean setTempoBpm(int bpm) {
            return SongbookClientState.setManagedTempoBpm(bpm);
        }
        @Override public boolean setInstrument(String instrumentId) {
            return SongbookClientState.setSelectedInstrument(instrumentId);
        }
        @Override public MusicScore previewSong() {
            return SongbookClientState.managedPreviewSong();
        }
        @Override public boolean handleDrop(Screen screen, List<Path> paths) {
            return SongbookFileImporter.handle(screen, paths, bookId());
        }
        @Override public void close() { SongbookClientState.closeManagedBook(); }
    }
}
