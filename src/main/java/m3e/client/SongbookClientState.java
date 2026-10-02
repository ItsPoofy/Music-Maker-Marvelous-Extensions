package m3e.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import m3e.api.client.M3EClientIntegration;
import m3e.debug.MusicDebugTelemetry;
import m3e.network.SongbookActionPacket;
import m3e.network.SongbookCatalogPacket;
import m3e.network.SongbookCatalogRequestPacket;
import m3e.network.SongbookResultPacket;
import m3e.network.SongbookSongChunkPacket;
import m3e.network.SongbookSongFinishPacket;
import m3e.network.SongbookSongRequestPacket;
import m3e.network.SongbookSongStartPacket;
import m3e.network.SongbookUploadChunkPacket;
import m3e.network.SongbookUploadFinishPacket;
import m3e.network.SongbookUploadStartPacket;
import m3e.sheet.MusicScore;
import m3e.sheet.SheetFileIO;
import m3e.songbook.SongbookComponents;
import m3e.songbook.SongbookHash;
import m3e.songbook.SongbookHeader;
import m3e.songbook.SongbookItems;
import m3e.songbook.SongbookEntry;
import m3e.songbook.SongbookSongCodec;
import m3e.songbook.SongbookMetadata;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Connection-local UI/cache for server-owned Songbooks; no songs are stored in local config files. */
public final class SongbookClientState {
    private static final SongbookTransport TRANSPORT = new SongbookTransport(SongbookClientState::playbackCycleCompleted);
    private static final SongbookManagementState MANAGEMENT = new SongbookManagementState();
    private static final SongbookRequestThrottle SONG_REQUESTS = new SongbookRequestThrottle();
    private static final long BOOK_RENAME_GRACE_NANOS = 5_000_000_000L;
    private static final Map<UUID, PendingBookLabel> PENDING_BOOK_LABELS = new HashMap<>();
    private static List<BookOption> books = List.of();
    private static UUID activeBookId;
    private static SongbookCatalogPacket catalog;
    private static int selectedIndex = -1;
    private static MusicScore selectedSong;
    private static int selectedSongRevision = -1;
    private static UUID requestedCatalogId;
    private static int requestedCatalogRevision = -1;
    private static final SongbookDownload DOWNLOAD = new SongbookDownload();



    static MusicScore loadedSong() { return selectedSong; }
    static SongbookCatalogPacket activeCatalog() { return catalog; }
    private SongbookClientState() { }

    public static synchronized void tick() {
        scanInventory();
        if (DOWNLOAD.expire(System.nanoTime())) TRANSPORT.startPlaybackAfterLoad = false;
        flushSelectedSongRequest();
        TRANSPORT.playback.syncSingleplayerPause(Minecraft.getInstance().isPaused());
        TRANSPORT.playback.validateInstrumentBinding();
    }

    public static synchronized boolean hasBooks() { return !books.isEmpty(); }
    public static synchronized List<BookOption> books() { return books; }
    public static synchronized UUID activeBookId() { return activeBookId; }

    public static synchronized ImportTarget activeImportTarget() {
        BookOption active = activeBook();
        if (active == null || catalog == null || !catalog.bookId().equals(active.bookId())) return null;
        return new ImportTarget(active.bookId(), catalog.revision(), catalog.capacity(),
                catalog.entries().size(), catalog.encodedBytes());
    }

    public static synchronized boolean openManagedBook(UUID bookId) { return MANAGEMENT.openManagedBook(bookId); }

    public static synchronized void closeManagedBook() { MANAGEMENT.closeManagedBook(); }

    public static synchronized boolean hasManagedBook() { return MANAGEMENT.hasManagedBook(); }

    public static synchronized UUID managedBookId() { return MANAGEMENT.managedBookId(); }
    public static synchronized int managedRevision() { return MANAGEMENT.managedRevision(); }
    public static synchronized int managedCapacity() { return MANAGEMENT.managedCapacity(); }
    public static synchronized int managedEncodedBytes() { return MANAGEMENT.managedEncodedBytes(); }
    public static synchronized int managedSelectedIndex() { return MANAGEMENT.managedSelectedIndex(); }
    public static synchronized String managedBookLabel() { return MANAGEMENT.managedBookLabel(); }
    public static synchronized List<SongEntry> managedSnapshots() { return MANAGEMENT.managedSnapshots(); }
    public static synchronized void selectManaged(int index) { MANAGEMENT.selectManaged(index); }
    public static synchronized ImportTarget managedImportTarget() { return MANAGEMENT.managedImportTarget(); }
    public static synchronized boolean renameManagedBook(String name) { return MANAGEMENT.renameManagedBook(name); }
    public static synchronized boolean renameManagedSelected(String name) { return MANAGEMENT.renameManagedSelected(name); }
    public static synchronized boolean deleteManagedSelected() { return MANAGEMENT.deleteManagedSelected(); }
    public static synchronized boolean extractManagedSelected() { return MANAGEMENT.extractManagedSelected(); }
    public static synchronized boolean moveManagedSelected(int targetIndex) { return MANAGEMENT.moveManagedSelected(targetIndex); }
    public static synchronized int managedSemitoneShift() { return MANAGEMENT.managedSemitoneShift(); }
    public static synchronized boolean canShiftManaged(int delta) { return MANAGEMENT.canShiftManaged(delta); }
    public static synchronized boolean shiftManagedSemitones(int delta) { return MANAGEMENT.shiftManagedSemitones(delta); }

    public static synchronized boolean setManagedTempoBpm(int bpm) { return MANAGEMENT.setManagedTempoBpm(bpm); }

    static synchronized MusicScore managedPreviewSong() { return MANAGEMENT.managedPreviewSong(); }

    private static boolean managedSelectionMatchesPlayback() { return MANAGEMENT.managedSelectionMatchesPlayback(); }

    public static synchronized boolean selectBook(UUID bookId) {
        if (bookId != null && books.stream().noneMatch(book -> book.bookId().equals(bookId))) {
            scanInventory();
        }
        if (bookId == null || books.stream().noneMatch(book -> book.bookId().equals(bookId))) {
            return false;
        }
        if (bookId.equals(activeBookId)) {
            requestCatalog(true);
            return true;
        }
        TRANSPORT.playback.stop();
        publishEnsembleTransport();
        TRANSPORT.queue.selectionChanged();
        activeBookId = bookId;
        catalog = null;
        selectedIndex = -1;
        selectedSong = null;
        selectedSongRevision = -1;
        TRANSPORT.pendingSeekMicros = -1L;
        TRANSPORT.playbackSemitoneShift = 0;
        TRANSPORT.startPlaybackAfterLoad = false;
        SONG_REQUESTS.cancel();
        DOWNLOAD.clear();
        requestedCatalogId = null;
        requestedCatalogRevision = -1;
        MusicDebugTelemetry.event("songbook_transport", "book_selected", "book", bookId);
        requestCatalog(true);
        return true;
    }

    public static synchronized List<SongEntry> snapshots() {
        if (catalog == null || !catalog.bookId().equals(activeBookId)) return List.of();
        return catalog.entries().stream().map(entry -> new SongEntry(entry.entryId(), entry.displayName(),
                entry.tempoBpm(), entry.durationMicros(), entry.timeSignatureNumerator(),
                entry.timeSignatureDenominator(), entry.metadata())).toList();
    }

    public static synchronized int selectedIndex() { return selectedIndex; }
    public static synchronized int capacity() { return catalog == null ? 0 : catalog.capacity(); }
    public static synchronized int encodedBytes() { return catalog == null ? 0 : catalog.encodedBytes(); }
    public static synchronized int revision() { return catalog == null ? -1 : catalog.revision(); }
    public static synchronized String activeBookLabel() {
        BookOption book = activeBook();
        return book == null ? "Songbook" : book.label();
    }

    public static synchronized String selectedTimeSignature() {
        SongEntry entry = selectedEntry();
        return entry == null ? "4/4" : entry.timeSignatureNumerator() + "/" + entry.timeSignatureDenominator();
    }

    public static synchronized void select(int index) {
        select(index, false);
    }

    private static void select(int index, boolean playAfterLoad) {
        List<SongEntry> entries = snapshots();
        if (index < 0 || index >= entries.size() || index == selectedIndex) return;
        TRANSPORT.playback.stop();
        publishEnsembleTransport();
        selectedIndex = index;
        if (!playAfterLoad) TRANSPORT.queue.selectionChanged();
        selectedSong = null;
        selectedSongRevision = -1;
        TRANSPORT.pendingSeekMicros = -1L;
        restorePlaybackPitchShift();
        TRANSPORT.startPlaybackAfterLoad = playAfterLoad;
        SONG_REQUESTS.cancel();
        DOWNLOAD.clear();
        requestSelectedSong();
    }

    public static synchronized boolean importSong(MusicScore song, String displayName) {
        return importSong(activeImportTarget(), song, displayName);
    }

    public static synchronized boolean importSong(ImportTarget target, MusicScore song,
                                                   String displayName) {
        if (target == null || song == null) return false;
        if (target.entryCount() >= target.capacity()) {
            message("Songbook has no empty page.");
            return false;
        }
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty()) name = song.sourceName().isBlank() ? "Imported MIDI" : song.sourceName();
        if (name.length() > 96) name = name.substring(0, 96);
        byte[] bytes = SongbookSongCodec.encode(song);
        int storedSize = SongbookEntry.estimatedBytes(name, bytes.length);
        if (target.encodedBytes() + storedSize > 1024 * 1024) {
            message("Songbook does not have enough data capacity.");
            return false;
        }
        upload(target, name, bytes, storedSize, SongbookUploadStartPacket.Kind.MIDI);
        return true;
    }

    public static synchronized boolean importSheet(byte[] bytes, String fileName) {
        return importSheet(activeImportTarget(), bytes, fileName);
    }

    public static synchronized boolean importSheet(ImportTarget target, byte[] bytes, String fileName) {
        if (target == null) return false;
        if (target.entryCount() >= target.capacity()) {
            message("Songbook has no empty page.");
            return false;
        }
        if (bytes == null || bytes.length == 0 || bytes.length > SheetFileIO.MAX_FILE_BYTES) {
            message("The .sheet file is empty or exceeds 1 MiB.");
            return false;
        }
        String name = stripSheetExtension(fileName == null ? "Imported Sheet" : fileName);
        if (name.isBlank()) name = "Imported Sheet";
        if (name.length() > 96) name = name.substring(0, 96);
        int decodedEstimate;
        try {
            decodedEstimate = SheetFileIO.read(bytes).toString()
                    .getBytes(StandardCharsets.UTF_8).length;
        } catch (java.io.IOException exception) {
            message("The .sheet file is not valid.");
            return false;
        }
        int storedSize = Math.addExact(SongbookEntry.estimatedBytes(
                name, bytes.length), decodedEstimate);
        if (target.encodedBytes() + storedSize > 1024 * 1024) {
            message("Songbook does not have enough data capacity.");
            return false;
        }
        upload(target, name, bytes, storedSize, SongbookUploadStartPacket.Kind.SHEET);
        return true;
    }

    private static void upload(ImportTarget target, String name, byte[] bytes, int storedSize,
                               SongbookUploadStartPacket.Kind kind) {
        ClientPlayNetworking.send(new SongbookUploadStartPacket(target.bookId(), target.revision(), name,
                bytes.length, storedSize, SongbookHash.sha256(bytes), kind));
        for (int offset = 0; offset < bytes.length; offset += SongbookUploadChunkPacket.MAX_CHUNK_BYTES) {
            int length = Math.min(SongbookUploadChunkPacket.MAX_CHUNK_BYTES, bytes.length - offset);
            ClientPlayNetworking.send(new SongbookUploadChunkPacket(offset,
                    Arrays.copyOfRange(bytes, offset, offset + length)));
        }
        ClientPlayNetworking.send(new SongbookUploadFinishPacket());
    }

    public static synchronized void playPause() {
        SongEntry entry = selectedEntry();
        if (entry == null) return;
        if (selectedSong == null || selectedSongRevision != catalog.revision()) {
            TRANSPORT.startPlaybackAfterLoad = true;
            requestSelectedSong();
            return;
        }
        TRANSPORT.playback.playPause(selectedSong, effectiveTempoBpm(entry), TRANSPORT.playbackSemitoneShift);
        publishEnsembleTransport();
    }

    public static synchronized void endSelectedPlayback() {
        TRANSPORT.startPlaybackAfterLoad = false;
        TRANSPORT.playback.stop();
        publishEnsembleTransport();
    }

    public static synchronized boolean renameSelected(String name) {
        SongEntry entry = selectedEntry();
        String cleaned = name == null ? "" : name.strip();
        if (entry == null || entry.metadata().signed() || cleaned.isEmpty()) return false;
        sendAction(entry.entryId(), SongbookActionPacket.Action.RENAME, cleaned, 0);
        return true;
    }

    public static synchronized boolean deleteSelected() {
        SongEntry entry = selectedEntry();
        if (entry == null) return false;
        TRANSPORT.startPlaybackAfterLoad = false;
        TRANSPORT.playback.stop();
        publishEnsembleTransport();
        sendAction(entry.entryId(), SongbookActionPacket.Action.DELETE, "", 0);
        return true;
    }

    public static synchronized boolean extractSelected() {
        SongEntry entry = selectedEntry();
        if (entry == null || capacity() <= 1) return false;
        TRANSPORT.startPlaybackAfterLoad = false;
        TRANSPORT.playback.stop();
        publishEnsembleTransport();
        sendAction(entry.entryId(), SongbookActionPacket.Action.EXTRACT_SHEET, "", 0);
        return true;
    }

    public static synchronized boolean renameBook(String name) {
        if (catalog == null) return false;
        String cleaned = cleanBookName(name);
        sendAction(new UUID(0L, 0L), SongbookActionPacket.Action.RENAME_BOOK,
                cleaned, 0);
        cacheBookRename(catalog.bookId(), cleaned);
        return true;
    }

    public static synchronized boolean moveSelected(int targetIndex) {
        SongEntry entry = selectedEntry();
        if (entry == null || targetIndex < 0 || targetIndex >= snapshots().size()
                || targetIndex == selectedIndex) return false;
        sendAction(entry.entryId(), SongbookActionPacket.Action.MOVE, "", targetIndex);
        return true;
    }

    public static synchronized SongbookPlaybackQueue.LoopMode loopMode() {
        return TRANSPORT.queue.loopMode();
    }

    public static synchronized boolean setSelectedInstrument(String instrumentId) {
        SongEntry entry = selectedEntry();
        if (entry == null) return false;
        sendAction(entry.entryId(), SongbookActionPacket.Action.SET_INSTRUMENT,
                instrumentId == null ? "" : instrumentId, 0);
        return true;
    }

    public static synchronized void cycleLoopMode() {
        setLoopMode(TRANSPORT.queue.cycleLoopMode());
    }

    static synchronized void setLoopMode(SongbookPlaybackQueue.LoopMode mode) {
        TRANSPORT.queue.loopMode(mode);
        TRANSPORT.playback.setLooping(TRANSPORT.queue.loopMode()
                == SongbookPlaybackQueue.LoopMode.LOOP_SONG);
        MusicDebugTelemetry.event("songbook_transport", "loop_mode_changed",
                "mode", TRANSPORT.queue.loopMode(), "shuffle", TRANSPORT.queue.shuffle());
    }

    public static synchronized boolean shuffle() { return TRANSPORT.queue.shuffle(); }

    public static synchronized void setShuffle(boolean value) {
        TRANSPORT.queue.shuffle(value);
        MusicDebugTelemetry.event("songbook_transport", "shuffle_changed", "enabled", value);
    }

    public static synchronized void seekSelected(long micros) {
        SongEntry entry = selectedEntry();
        if (entry == null) return;
        long target = Math.clamp(micros, 0L, entry.durationMicros());
        if (selectedSong == null) {
            TRANSPORT.pendingSeekMicros = target;
            requestSelectedSong();
            return;
        }
        TRANSPORT.pendingSeekMicros = -1L;
        TRANSPORT.playback.seek(selectedSong, toPlaybackMicros(entry, target),
                effectiveTempoBpm(entry), TRANSPORT.playbackSemitoneShift);
        publishEnsembleTransport();
    }

    public static synchronized int playbackSemitoneShift() {
        return TRANSPORT.playbackSemitoneShift;
    }

    public static synchronized boolean canShiftPlayback(int delta) {
        if (selectedEntry() == null || delta == 0) return false;
        int next = TRANSPORT.playbackSemitoneShift + delta;
        return next >= -48 && next <= 48;
    }

    public static synchronized boolean shiftPlaybackSemitones(int delta) {
        if (!canShiftPlayback(delta)) return false;
        TRANSPORT.playbackSemitoneShift += delta;
        SongEntry entry = selectedEntry();
        InstrumentOptions.setSongbookPitchShift(activeBookId, entry.entryId(),
                TRANSPORT.playbackSemitoneShift);
        if (selectedSong != null) TRANSPORT.playback.updatePitchShift(selectedSong,
                effectiveTempoBpm(entry), TRANSPORT.playbackSemitoneShift);
        return true;
    }

    public static synchronized void skipSelectedSeconds(int seconds) {
        seekSelected(Math.addExact(playheadMicros(), seconds * 1_000_000L));
    }

    public static synchronized long playheadMicros() {
        SongEntry entry = selectedEntry();
        if (entry == null) return 0L;
        if (TRANSPORT.pendingSeekMicros >= 0L) {
            return Math.clamp(TRANSPORT.pendingSeekMicros, 0L, entry.durationMicros());
        }
        return Math.clamp(
                fromPlaybackMicros(entry, TRANSPORT.playback.playheadMicros()),
                0L, entry.durationMicros());
    }
    public static synchronized long selectedDurationMicros() {
        SongEntry entry = selectedEntry();
        return entry == null ? 0L : Math.max(0L, entry.durationMicros());
    }
    public static synchronized int selectedTempoBpm() { SongEntry e=selectedEntry(); return e == null ? 120 : e.tempoBpm(); }
    public static synchronized int currentTempoBpm() {
        SongEntry entry = selectedEntry();
        return entry == null ? 120 : TRANSPORT.playback.transportState(effectiveTempoBpm(entry)).bpm();
    }

    public static synchronized double playbackSpeed() { return TRANSPORT.playbackSpeed; }

    public static synchronized void setPlaybackSpeed(double speed) {
        double next = PlaybackSpeedSlider.clamp(speed);
        if (Math.abs(next - TRANSPORT.playbackSpeed) < 0.0001D) return;
        SongEntry entry = selectedEntry();
        int oldEffectiveTempo = entry == null ? 120 : effectiveTempoBpm(entry);
        TRANSPORT.playbackSpeed = next;
        if (entry != null && selectedSong != null) {
            TRANSPORT.playback.updateTempo(selectedSong, oldEffectiveTempo,
                    effectiveTempoBpm(entry));
            publishEnsembleTransport();
        }
    }

    public static synchronized void setSelectedTempoBpm(int bpm) {
        SongEntry entry = selectedEntry();
        if (entry == null) return;
        int value = Math.clamp(bpm, 1, 3_000);
        if (value == entry.tempoBpm()) return;
        TRANSPORT.startPlaybackAfterLoad = false;
        TRANSPORT.playback.stop();
        publishEnsembleTransport();
        sendAction(entry.entryId(), SongbookActionPacket.Action.SET_TEMPO, "", value);
    }

    public static synchronized boolean syncTempoWithEnsemble() { return TRANSPORT.syncTempoWithEnsemble; }
    public static synchronized void setSyncTempoWithEnsemble(boolean value) {
        if (TRANSPORT.syncTempoWithEnsemble == value) return;
        if (!value) publishEnsembleTransport(true);
        TRANSPORT.syncTempoWithEnsemble = value;
        if (value) publishEnsembleTransport();
    }
    public static synchronized String stateLabel() { return selectedSong == null && selectedEntry() != null ? "Load" : TRANSPORT.playback.stateLabel(); }
    public static synchronized String stateIcon() { return TRANSPORT.playback.stateIcon(); }
    /** True while the transport is actively driving notes after the instrument UI closes. */
    public static synchronized boolean playbackRunning() { return TRANSPORT.playback.playingSong() != null; }
    static synchronized InstrumentMidiPlayback.VisualizationSnapshot visualizationSnapshot() {
        return TRANSPORT.playback.visualizationSnapshot();
    }
    public static synchronized void attach(InstrumentMidiScreenAccess screen) { TRANSPORT.playback.attach(screen); }
    public static synchronized void detach(InstrumentMidiScreenAccess screen) { TRANSPORT.playback.detach(screen); }

    public static synchronized void clear() {
        TRANSPORT.playback.stop(); books=List.of(); activeBookId=null; catalog=null; selectedIndex=-1;
        selectedSong=null; selectedSongRevision=-1;
        TRANSPORT.playbackSemitoneShift=0; TRANSPORT.playbackSpeed=1.0D; TRANSPORT.pendingSeekMicros=-1L;
        TRANSPORT.startPlaybackAfterLoad=false; TRANSPORT.queue.reset(); TRANSPORT.playback.setLooping(false);
        TRANSPORT.syncTempoWithEnsemble=false;
        SONG_REQUESTS.reset();
        SongbookFileImporter.cancelPending();
        requestedCatalogId=null; requestedCatalogRevision=-1; DOWNLOAD.clear();
        PENDING_BOOK_LABELS.clear();
        closeManagedBook();
    }

    public static synchronized void receiveCatalog(SongbookCatalogPacket packet) {
        MANAGEMENT.receiveCatalog(packet);
        if (!packet.bookId().equals(activeBookId)) return;
        SongEntry previousSelection = selectedEntry();
        UUID selectedId = previousSelection == null ? null : previousSelection.entryId();
        SongbookCatalogPacket.Entry replacement = packet.entries().stream()
                .filter(entry -> entry.entryId().equals(selectedId))
                .findFirst().orElse(null);
        boolean revisionChanged = catalog == null || catalog.revision() != packet.revision();
        boolean preserveLoadedSong = selectedSong != null
                && sameMusicalEntry(previousSelection, replacement);
        boolean playbackInvalidated = selectedSong != null && !preserveLoadedSong;
        if (playbackInvalidated) {
            TRANSPORT.playback.stop();
            publishEnsembleTransport();
        }
        if (revisionChanged && DOWNLOAD.active()) {
            DOWNLOAD.clear();
            TRANSPORT.startPlaybackAfterLoad = false;
            SONG_REQUESTS.cancel();
        }
        catalog = packet;
        requestedCatalogId = null;
        requestedCatalogRevision = -1;
        selectedIndex = -1;
        if (selectedId != null) {
            for (int index=0; index<packet.entries().size(); index++) if(packet.entries().get(index).entryId().equals(selectedId)) { selectedIndex=index; break; }
        }
        if (selectedIndex < 0 && !packet.entries().isEmpty()) selectedIndex = 0;
        if (preserveLoadedSong) {
            selectedSongRevision = packet.revision();
        } else if (playbackInvalidated) {
            selectedSong=null; selectedSongRevision=-1;
            TRANSPORT.startPlaybackAfterLoad=false;
        }
        restorePlaybackPitchShift();
        if (selectedIndex >= 0 && selectedSong == null) requestSelectedSong();
        if (packet.bookId().equals(MANAGEMENT.managedBookId) && MANAGEMENT.managedSelectedIndex >= 0
                && selectedIndex != MANAGEMENT.managedSelectedIndex) {
            select(MANAGEMENT.managedSelectedIndex);
        }
    }

    public static synchronized void beginSong(SongbookSongStartPacket packet) {
        SongEntry selected = selectedEntry();
        if (catalog == null || selected == null || !packet.bookId().equals(activeBookId) || packet.revision() != catalog.revision()
                || !packet.entryId().equals(selected.entryId())) return;
        DOWNLOAD.begin(packet);
    }

    public static synchronized void receiveSongChunk(SongbookSongChunkPacket packet) {
        if (!DOWNLOAD.append(packet)) TRANSPORT.startPlaybackAfterLoad = false;
    }

    public static synchronized void finishSong(SongbookSongFinishPacket packet) {
        SongbookDownload.Completed completed = DOWNLOAD.finish(packet);
        if (completed == null) { TRANSPORT.startPlaybackAfterLoad = false; return; }
        SongEntry selected = selectedEntry();
        if (catalog == null || selected == null
                || !completed.start().bookId().equals(activeBookId)
                || completed.start().revision() != catalog.revision()
                || !completed.start().entryId().equals(selected.entryId())) {
            TRANSPORT.startPlaybackAfterLoad = false;
            return;
        }
        try {
            selectedSong = completed.song();
            selectedSongRevision = completed.start().revision();
            if (TRANSPORT.pendingSeekMicros >= 0L) {
                long target = TRANSPORT.pendingSeekMicros;
                TRANSPORT.pendingSeekMicros = -1L;
                TRANSPORT.playback.seek(selectedSong, toPlaybackMicros(selected, target),
                        effectiveTempoBpm(selected), TRANSPORT.playbackSemitoneShift);
            }
            if (TRANSPORT.startPlaybackAfterLoad) {
                TRANSPORT.startPlaybackAfterLoad = false;
                startSelectedPlayback();
            }
        } catch (RuntimeException invalid) {
            selectedSong = null;
            selectedSongRevision = -1;
            TRANSPORT.startPlaybackAfterLoad = false;
        }
    }

    public static synchronized void receiveResult(SongbookResultPacket packet) {
        if (!packet.success()) message(packet.message());
    }

    static void scanInventory() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) { clear(); return; }
        ArrayList<BookOption> found = new ArrayList<>();
        int ordinal = 0;
        long now = System.nanoTime();
        for (int slot=0; slot<minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (!stack.is(SongbookItems.SONGBOOK)) continue;
            SongbookHeader header = stack.get(SongbookComponents.HEADER);
            if (header == null) continue;
            ordinal++;
            Component custom = stack.get(DataComponents.CUSTOM_NAME);
            String label = custom == null ? "Songbook #" + ordinal : custom.getString();
            PendingBookLabel pending = PENDING_BOOK_LABELS.get(header.bookId());
            if (pending != null) {
                String serverName = custom == null ? "" : custom.getString();
                if (pending.name().equals(serverName)
                        || now - pending.createdNanos() >= BOOK_RENAME_GRACE_NANOS) {
                    PENDING_BOOK_LABELS.remove(header.bookId());
                } else {
                    label = pending.name().isEmpty() ? "Songbook" : pending.name();
                }
            }
            found.add(new BookOption(header.bookId(), label, header, slot));
        }
        books = List.copyOf(found);
        BookOption active = activeBook();
        if (active == null) {
            TRANSPORT.playback.stop(); publishEnsembleTransport();
            catalog=null; selectedIndex=-1; selectedSong=null;
            selectedSongRevision=-1; DOWNLOAD.clear();
            TRANSPORT.startPlaybackAfterLoad=false; SONG_REQUESTS.cancel();
            TRANSPORT.playbackSemitoneShift=0;
            activeBookId = books.isEmpty() ? null : books.getFirst().bookId();
            requestCatalog(true);
            return;
        }
        if (catalog == null || catalog.revision() != active.header().revision()) requestCatalog(false);
    }

    private static void requestCatalog(boolean force) {
        BookOption active = activeBook();
        if (active == null) return;
        if (!force && active.bookId().equals(requestedCatalogId) && active.header().revision() == requestedCatalogRevision) return;
        requestedCatalogId=active.bookId(); requestedCatalogRevision=active.header().revision();
        ClientPlayNetworking.send(new SongbookCatalogRequestPacket(active.bookId()));
    }

    private static void requestSelectedSong() {
        SongEntry entry = selectedEntry();
        if (entry == null || catalog == null) {
            SONG_REQUESTS.cancel();
            return;
        }
        SONG_REQUESTS.queue();
        flushSelectedSongRequest();
    }

    private static void flushSelectedSongRequest() {
        SongEntry entry = selectedEntry();
        if (entry == null || catalog == null) {
            SONG_REQUESTS.cancel();
            return;
        }
        if (!SONG_REQUESTS.takeIfReady(System.nanoTime())) return;
        ClientPlayNetworking.send(new SongbookSongRequestPacket(catalog.bookId(), catalog.revision(), entry.entryId()));
    }

    private static void sendAction(UUID entryId, SongbookActionPacket.Action action, String text, int value) {
        if (catalog == null) return;
        ClientPlayNetworking.send(new SongbookActionPacket(catalog.bookId(), catalog.revision(), entryId, action, text, value));
    }



    static String cleanBookName(String name) {
        String cleaned = name == null ? "" : name.strip();
        return cleaned.length() > 48 ? cleaned.substring(0, 48) : cleaned;
    }

    static void cacheBookRename(UUID bookId, String name) {
        PENDING_BOOK_LABELS.put(bookId, new PendingBookLabel(name, System.nanoTime()));
        String label = name.isEmpty() ? "Songbook" : name;
        books = books.stream().map(book -> book.bookId().equals(bookId)
                ? new BookOption(book.bookId(), label, book.header(), book.inventorySlot())
                : book).toList();
    }

    private static BookOption activeBook() {
        if (activeBookId == null) return null;
        return books.stream().filter(book -> book.bookId().equals(activeBookId)).findFirst().orElse(null);
    }

    static SongEntry selectedEntry() {
        List<SongEntry> entries = snapshots();
        return selectedIndex < 0 || selectedIndex >= entries.size() ? null : entries.get(selectedIndex);
    }



    private static void restorePlaybackPitchShift() {
        SongEntry entry = selectedEntry();
        TRANSPORT.playbackSemitoneShift = entry == null ? 0
                : InstrumentOptions.songbookPitchShift(activeBookId, entry.entryId());
    }

    private static void playbackCycleCompleted() {
        synchronized (SongbookClientState.class) {
            if (TRANSPORT.queue.loopMode() == SongbookPlaybackQueue.LoopMode.LOOP_SONG) {
                publishEnsembleTransport();
                return;
            }
            int songCount = snapshots().size();
            int randomChoice = songCount <= 1 ? 0
                    : ThreadLocalRandom.current().nextInt(songCount - 1);
            int nextIndex = TRANSPORT.queue.nextIndex(selectedIndex, songCount, randomChoice);
            if (nextIndex < 0) {
                publishEnsembleTransport();
                return;
            }
            if (nextIndex == selectedIndex && selectedSong != null) {
                startSelectedPlayback();
            } else {
                select(nextIndex, true);
            }
        }
    }

    private static void startSelectedPlayback() {
        SongEntry entry = selectedEntry();
        if (entry == null || selectedSong == null) return;
        TRANSPORT.playback.playPause(selectedSong, effectiveTempoBpm(entry), TRANSPORT.playbackSemitoneShift);
        publishEnsembleTransport();
    }

    private static void publishEnsembleTransport() {
        publishEnsembleTransport(false);
    }

    private static void publishEnsembleTransport(boolean forceStopped) {
        SongEntry entry = selectedEntry();
        if ((!TRANSPORT.syncTempoWithEnsemble && !forceStopped) || entry == null || selectedSong == null) return;
        InstrumentMidiPlayback.TransportState state = TRANSPORT.playback.transportState(
                effectiveTempoBpm(entry));
        MusicScore.TimeSignature signature = selectedSong.timeSignatures().isEmpty()
                ? new MusicScore.TimeSignature(0,4,4) : selectedSong.timeSignatures().getFirst();
        M3EClientIntegration.observeTransport(state.bpm(), signature.numerator(), signature.denominator(),
                state.playheadMicros(), !forceStopped && state.playing(), state.clientSampleNanos());
    }

    static boolean sameMusicalEntry(SongEntry previous, SongbookCatalogPacket.Entry replacement) {
        return previous != null && replacement != null
                && previous.entryId().equals(replacement.entryId())
                && previous.tempoBpm() == replacement.tempoBpm()
                && previous.durationMicros() == replacement.durationMicros()
                && previous.timeSignatureNumerator() == replacement.timeSignatureNumerator()
                && previous.timeSignatureDenominator() == replacement.timeSignatureDenominator();
    }

    private static int effectiveTempoBpm(SongEntry entry) { return TRANSPORT.effectiveTempoBpm(entry); }

    private static long fromPlaybackMicros(SongEntry entry, long playbackMicros) {
        return Math.max(0L, Math.round(playbackMicros * playbackRate(entry)));
    }

    private static long toPlaybackMicros(SongEntry entry, long sourceMicros) {
        return Math.max(0L, Math.round(Math.max(0L, sourceMicros) / playbackRate(entry)));
    }

    private static double playbackRate(SongEntry entry) { return TRANSPORT.playbackRate(entry); }

    private static void message(String text) {
        Minecraft minecraft=Minecraft.getInstance();
        if(minecraft.player!=null) minecraft.player.sendSystemMessage(Component.literal(text)
                .withStyle(ChatFormatting.RED));
    }

    private static String stripSheetExtension(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".sheet")
                ? name.substring(0, name.length() - 6) : name;
    }

    public record BookOption(UUID bookId, String label, SongbookHeader header, int inventorySlot) { }
    private record PendingBookLabel(String name, long createdNanos) { }
    public record ImportTarget(UUID bookId, int revision, int capacity, int entryCount, int encodedBytes) { }
    public record SongEntry(UUID entryId, String displayName, int tempoBpm, long durationMicros,
                            int timeSignatureNumerator, int timeSignatureDenominator,
                            SongbookMetadata metadata) {
        public SongEntry(UUID entryId, String displayName, int tempoBpm, long durationMicros,
                         int timeSignatureNumerator, int timeSignatureDenominator) {
            this(entryId, displayName, tempoBpm, durationMicros, timeSignatureNumerator,
                    timeSignatureDenominator, SongbookMetadata.EMPTY);
        }

        public SongEntry {
            metadata = metadata == null ? SongbookMetadata.EMPTY : metadata;
        }
    }
}
