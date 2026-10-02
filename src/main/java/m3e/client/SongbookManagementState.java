package m3e.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import m3e.network.SongbookActionPacket;
import m3e.network.SongbookCatalogPacket;
import m3e.network.SongbookCatalogRequestPacket;
import m3e.sheet.MusicScore;
import m3e.client.SongbookClientState.SongEntry;
import m3e.client.SongbookClientState.BookOption;
import m3e.client.SongbookClientState.ImportTarget;
import java.util.List;
import java.util.UUID;

/** Catalog selection and actions owned by the Songbook management UI. */
final class SongbookManagementState {
    UUID managedBookId;
    SongbookCatalogPacket managedCatalog;
    int managedSelectedIndex = -1;
    public boolean openManagedBook(UUID bookId) {
        if (bookId == null || SongbookClientState.books().stream().noneMatch(book -> book.bookId().equals(bookId))) {
            SongbookClientState.scanInventory();
        }
        if (bookId == null || SongbookClientState.books().stream().noneMatch(book -> book.bookId().equals(bookId))) {
            return false;
        }
        managedBookId = bookId;
        managedCatalog = null;
        managedSelectedIndex = -1;
        ClientPlayNetworking.send(new SongbookCatalogRequestPacket(bookId));
        return true;
    }

    public void closeManagedBook() {
        managedBookId = null;
        managedCatalog = null;
        managedSelectedIndex = -1;
    }

    public boolean hasManagedBook() {
        return managedBookId != null
                && SongbookClientState.books().stream().anyMatch(book -> book.bookId().equals(managedBookId));
    }

    public UUID managedBookId() { return managedBookId; }

    public int managedRevision() {
        return managedCatalog == null ? -1 : managedCatalog.revision();
    }

    public int managedCapacity() {
        return managedCatalog == null ? 0 : managedCatalog.capacity();
    }

    public int managedEncodedBytes() {
        return managedCatalog == null ? 0 : managedCatalog.encodedBytes();
    }

    public int managedSelectedIndex() { return managedSelectedIndex; }

    public String managedBookLabel() {
        return SongbookClientState.books().stream().filter(book -> book.bookId().equals(managedBookId))
                .map(BookOption::label).findFirst().orElse("Songbook");
    }

    public List<SongEntry> managedSnapshots() {
        if (managedCatalog == null || !managedCatalog.bookId().equals(managedBookId)) return List.of();
        return managedCatalog.entries().stream().map(entry -> new SongEntry(entry.entryId(),
                entry.displayName(), entry.tempoBpm(), entry.durationMicros(),
                entry.timeSignatureNumerator(), entry.timeSignatureDenominator(),
                entry.metadata())).toList();
    }

    public void selectManaged(int index) {
        managedSelectedIndex = index >= 0 && index < managedSnapshots().size() ? index : -1;
        if (managedSelectedIndex >= 0 && managedBookId != null) {
            if (!managedBookId.equals(SongbookClientState.activeBookId())) SongbookClientState.selectBook(managedBookId);
            if (SongbookClientState.activeCatalog() != null && managedBookId.equals(SongbookClientState.activeCatalog().bookId())) SongbookClientState.select(managedSelectedIndex);
        }
    }

    public ImportTarget managedImportTarget() {
        if (managedCatalog == null || !managedCatalog.bookId().equals(managedBookId)) return null;
        return new ImportTarget(managedBookId, managedCatalog.revision(), managedCatalog.capacity(),
                managedCatalog.entries().size(), managedCatalog.encodedBytes());
    }

    public boolean renameManagedBook(String name) {
        if (managedCatalog == null) return false;
        String cleaned = SongbookClientState.cleanBookName(name);
        sendManagedAction(new UUID(0L, 0L), SongbookActionPacket.Action.RENAME_BOOK,
                cleaned, 0);
        SongbookClientState.cacheBookRename(managedCatalog.bookId(), cleaned);
        return true;
    }

    public boolean renameManagedSelected(String name) {
        SongEntry entry = managedSelectedEntry();
        String cleaned = name == null ? "" : name.strip();
        if (entry == null || entry.metadata().signed() || cleaned.isEmpty()) return false;
        sendManagedAction(entry.entryId(), SongbookActionPacket.Action.RENAME, cleaned, 0);
        return true;
    }

    public boolean deleteManagedSelected() {
        SongEntry entry = managedSelectedEntry();
        if (entry == null) return false;
        sendManagedAction(entry.entryId(), SongbookActionPacket.Action.DELETE, "", 0);
        return true;
    }

    public boolean extractManagedSelected() {
        SongEntry entry = managedSelectedEntry();
        if (entry == null || managedCapacity() <= 1) return false;
        sendManagedAction(entry.entryId(), SongbookActionPacket.Action.EXTRACT_SHEET, "", 0);
        return true;
    }

    public boolean moveManagedSelected(int targetIndex) {
        SongEntry entry = managedSelectedEntry();
        if (entry == null || targetIndex < 0 || targetIndex >= managedSnapshots().size()
                || targetIndex == managedSelectedIndex) return false;
        sendManagedAction(entry.entryId(), SongbookActionPacket.Action.MOVE, "", targetIndex);
        return true;
    }

    public int managedSemitoneShift() {
        SongEntry entry = managedSelectedEntry();
        return entry == null ? 0 : InstrumentOptions.songbookPitchShift(managedBookId, entry.entryId());
    }

    public boolean canShiftManaged(int delta) {
        int next = managedSemitoneShift() + delta;
        return managedSelectedEntry() != null && delta != 0 && next >= -48 && next <= 48;
    }

    public boolean shiftManagedSemitones(int delta) {
        if (!canShiftManaged(delta)) return false;
        SongEntry entry = managedSelectedEntry();
        InstrumentOptions.setSongbookPitchShift(managedBookId, entry.entryId(),
                managedSemitoneShift() + delta);
        return true;
    }

    public boolean setManagedTempoBpm(int bpm) {
        SongEntry entry = managedSelectedEntry();
        if (entry == null) return false;
        int value = Math.clamp(bpm, 1, 3_000);
        if (value == entry.tempoBpm()) return true;
        sendManagedAction(entry.entryId(), SongbookActionPacket.Action.SET_TEMPO, "", value);
        List<SongbookCatalogPacket.Entry> entries = managedCatalog.entries().stream()
                .map(candidate -> candidate.entryId().equals(entry.entryId())
                        ? new SongbookCatalogPacket.Entry(candidate.entryId(), candidate.displayName(),
                                value, candidate.durationMicros(), candidate.timeSignatureNumerator(),
                                candidate.timeSignatureDenominator(), candidate.metadata())
                        : candidate)
                .toList();
        managedCatalog = new SongbookCatalogPacket(managedCatalog.bookId(), managedCatalog.revision(),
                managedCatalog.capacity(), managedCatalog.encodedBytes(), entries);
        return true;
    }

    MusicScore managedPreviewSong() {
        return managedSelectionMatchesPlayback() ? SongbookClientState.loadedSong() : null;
    }

    boolean managedSelectionMatchesPlayback() {
        SongEntry managed = managedSelectedEntry();
        SongEntry active = SongbookClientState.selectedEntry();
        return managed != null && active != null && managedBookId != null
                && managedBookId.equals(SongbookClientState.activeBookId())
                && managed.entryId().equals(active.entryId());
    }

    private void sendManagedAction(UUID entryId, SongbookActionPacket.Action action,
                                          String text, int value) {
        if (managedCatalog == null) return;
        ClientPlayNetworking.send(new SongbookActionPacket(managedCatalog.bookId(),
                managedCatalog.revision(), entryId, action, text, value));
    }

    private SongEntry managedSelectedEntry() {
        List<SongEntry> entries = managedSnapshots();
        return managedSelectedIndex < 0 || managedSelectedIndex >= entries.size()
                ? null : entries.get(managedSelectedIndex);
    }

    void receiveCatalog(SongbookCatalogPacket packet) {
        if (packet.bookId().equals(managedBookId)) {
            UUID selectedId = managedSelectedEntry() == null
                    ? null : managedSelectedEntry().entryId();
            int previousIndex = managedSelectedIndex;
            if (selectedId == null && packet.bookId().equals(SongbookClientState.activeBookId())) {
                SongEntry active = SongbookClientState.selectedEntry();
                if (active != null) selectedId = active.entryId();
            }
            managedCatalog = packet;
            managedSelectedIndex = -1;
            if (selectedId != null) {
                for (int index = 0; index < packet.entries().size(); index++) {
                    if (packet.entries().get(index).entryId().equals(selectedId)) {
                        managedSelectedIndex = index;
                        break;
                    }
                }
            }
            if (managedSelectedIndex < 0 && !packet.entries().isEmpty()) {
                managedSelectedIndex = Math.clamp(previousIndex, 0,
                        packet.entries().size() - 1);
            }
        }
    }
}
