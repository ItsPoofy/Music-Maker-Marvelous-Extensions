package m3e.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import m3e.network.SheetBatchUploadStartPacket;
import m3e.sheet.MusicScore;
import m3e.sheet.BlankMusicSheet;
import m3e.sheet.SheetBatchCodec;
import m3e.songbook.SongbookContents;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Shared completion boundary for MIDI and ABC score imports. */
final class ScoreImportDestination {
    enum Kind { SHEET, SONGBOOK, INSTRUMENT_SONGBOOK }

    private final Screen parent;
    private final InstrumentMidiScreenAccess instrumentAccess;
    private final SongbookClientState.ImportTarget songbookTarget;
    private final Supplier<SongbookClientState.ImportTarget> currentTarget;
    private final Kind kind;

    private ScoreImportDestination(Screen parent, InstrumentMidiScreenAccess instrumentAccess,
                                   SongbookClientState.ImportTarget songbookTarget,
                                   Supplier<SongbookClientState.ImportTarget> currentTarget,
                                   Kind kind) {
        this.parent = parent;
        this.instrumentAccess = instrumentAccess;
        this.songbookTarget = songbookTarget;
        this.currentTarget = currentTarget;
        this.kind = kind;
    }

    static ScoreImportDestination sheet(Screen parent) {
        return new ScoreImportDestination(parent, null, null, null, Kind.SHEET);
    }

    static ScoreImportDestination instrumentSongbook(
            Screen parent, InstrumentMidiScreenAccess instrumentAccess,
            SongbookClientState.ImportTarget target) {
        return new ScoreImportDestination(parent, instrumentAccess, target,
                SongbookClientState::activeImportTarget, Kind.INSTRUMENT_SONGBOOK);
    }

    static ScoreImportDestination songbook(Screen parent,
                                            SongbookClientState.ImportTarget target,
                                            boolean managed) {
        return new ScoreImportDestination(parent, null, target,
                managed ? SongbookClientState::managedImportTarget
                        : SongbookClientState::activeImportTarget,
                Kind.SONGBOOK);
    }

    Screen parent() { return parent; }
    Kind kind() { return kind; }
    boolean songbook() { return kind != Kind.SHEET; }
    boolean supportsSeparate(boolean midi) { return supportsSeparate(kind, midi); }
    InstrumentMidiScreenAccess previewInstrument() { return instrumentAccess; }

    int separateCapacity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.isCreative()) {
            return Integer.MAX_VALUE;
        }
        if (kind == Kind.SHEET) {
            return minecraft.player == null ? 0 : BlankMusicSheet.importCapacity(minecraft.player);
        }
        SongbookClientState.ImportTarget current = currentTarget == null ? null : currentTarget.get();
        if (songbookTarget == null || !Objects.equals(songbookTarget, current)) return 0;
        return songbookBatchCapacity(current.capacity(), current.entryCount(), false);
    }

    boolean valid() {
        if (!songbook()) return SheetDropController.hasWritableSheet(Minecraft.getInstance(), parent);
        return songbookTarget != null && Objects.equals(songbookTarget, currentTarget.get());
    }

    boolean complete(MusicScore song, String displayName, List<Integer> drumMap) {
        if (!valid()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (songbook()) {
            if (SongbookClientState.importSong(songbookTarget, song, displayName)) {
                if (instrumentAccess != null) instrumentAccess.reopenInstrumentScreen();
                else minecraft.gui.setScreen(parent);
                return true;
            }
            return false;
        }
        if (parent instanceof PianoRollScreen pianoRoll) {
            return pianoRoll.importScore(song, drumMap);
        }
        if (!SheetEditController.importHeld(song, drumMap, result -> {
            if (result.success()) xerca.xercamusic.client.ModClient.showMusicGui();
        })) return false;
        minecraft.gui.setScreen(parent);
        return true;
    }

    UUID completeBatch(List<SheetBatchCodec.Entry> entries,
                       SheetBatchImportClient.Listener listener) {
        if (separateCapacity() < entries.size()) return null;
        if (kind == Kind.SHEET) {
            return SheetBatchImportClient.start(entries,
                    SheetBatchUploadStartPacket.Destination.SHEETS,
                    null, 0, listener);
        }
        if (!valid()) return null;
        return SheetBatchImportClient.start(entries,
                SheetBatchUploadStartPacket.Destination.SONGBOOK,
                songbookTarget.bookId(), songbookTarget.revision(), listener);
    }

    void openBatchResult() {
        Minecraft minecraft = Minecraft.getInstance();
        if (parent instanceof SheetImportScreenAccess access) {
            access.m3e$beginReplacementImport();
        }
        if (instrumentAccess != null) instrumentAccess.reopenInstrumentScreen();
        else minecraft.gui.setScreen(parent);
    }

    String separateCapacityError() {
        return songbook() ? "Not enough empty Songbook pages"
                : "Not enough completely blank Music Sheets";
    }

    static boolean supportsSeparate(Kind kind, boolean midi) {
        return kind == Kind.SHEET || midi;
    }

    static int remainingSongbookPages(int capacity, int entryCount) {
        return Math.max(0, capacity - entryCount);
    }

    static int songbookBatchCapacity(int capacity, int entryCount, boolean creative) {
        int available = remainingSongbookPages(creative
                ? SongbookContents.MAX_CAPACITY : capacity, entryCount);
        return Math.min(SheetBatchCodec.MAX_ENTRIES, available);
    }

    void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (instrumentAccess != null) {
            instrumentAccess.reopenInstrumentScreen();
        } else if (songbook()) {
            minecraft.gui.setScreen(parent);
        } else {
            xerca.xercamusic.client.ModClient.showMusicGui();
        }
    }
}
