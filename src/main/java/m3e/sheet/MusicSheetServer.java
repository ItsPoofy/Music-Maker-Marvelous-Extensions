package m3e.sheet;

import m3e.network.MusicSheetDataPacket;
import m3e.network.MusicSheetImportPacket;
import m3e.network.MusicSheetEditPacket;
import m3e.network.MusicSheetSaveResultPacket;
import m3e.network.MusicSheetTempoUpdatePacket;
import m3e.network.SheetBatchResultPacket;
import m3e.network.SheetBatchResultAckPacket;
import m3e.network.SheetBatchUploadCancelPacket;
import m3e.network.SheetBatchUploadChunkPacket;
import m3e.network.SheetBatchUploadFinishPacket;
import m3e.network.SheetBatchUploadStartPacket;
import m3e.songbook.SongbookServer;
import m3e.songbook.NativeSheetSnapshot;
import m3e.M3E;
import m3e.debug.MusicDebugTelemetry;
import m3e.sound.PlayerRateLimiter;
import m3e.sound.MusicBoxLibraryServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import xerca.xercamusic.common.item.ItemMusicSheet;
import xerca.xercamusic.common.item.Items;
import xerca.xercamusic.common.packets.serverbound.MusicUpdatePacket;

import m3e.network.ChunkedTransfer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Atomic server-side import and authorization boundary. */
public final class MusicSheetServer {
    private static final long MAX_DURATION_MICROS = 2L * 60L * 60L * 1_000_000L;
    private static final int MAX_IMPORTS_PER_SECOND = 2;
    private static final int MAX_REQUESTS_PER_SECOND = 8;
    private static final int MAX_TEMPO_UPDATES_PER_SECOND = 8;
    private static final long BATCH_TIMEOUT_NANOS = 15_000_000_000L;
    private static final long REPLAY_WINDOW_NANOS = 30_000_000_000L;
    private static final long BATCH_BEGIN_COOLDOWN_NANOS = 500_000_000L;
    private static final PlayerRateLimiter IMPORT_RATE = new PlayerRateLimiter();
    private static final PlayerRateLimiter REQUEST_RATE = new PlayerRateLimiter();
    private static final PlayerRateLimiter TEMPO_RATE = new PlayerRateLimiter();
    private static final Map<SheetKey, Set<UUID>> WATCHERS = new HashMap<>();
    private static final Map<UUID, BatchUpload> ACTIVE_BATCHES = new HashMap<>();
    private static final Map<BatchKey, Long> RECENT_BATCHES = new HashMap<>();
    private static final Map<UUID, Long> LAST_BATCH_BEGIN = new HashMap<>();
    private static final Map<BatchKey, PendingBatchResult> PENDING_BATCH_RESULTS = new HashMap<>();

    private MusicSheetServer() { }

    public static ItemStack createSheet(ServerPlayer player, MusicScore song, String displayName) {
        MusicScore named = song.renamed(displayName);
        UUID id = UUID.randomUUID();
        int version = 1;
        ItemStack sheet = new ItemStack(Items.MUSIC_SHEET);
        applyToSheet(sheet, id, version, named);
        watch(player, id, version);
        ServerPlayNetworking.send(player, new MusicSheetDataPacket(id, version, named));
        return sheet;
    }

    public static boolean allowImport(ServerPlayer player) {
        return IMPORT_RATE.allow(player.getUUID(), MAX_IMPORTS_PER_SECOND);
    }

    public static boolean allowRequest(ServerPlayer player) {
        return REQUEST_RATE.allow(player.getUUID(), MAX_REQUESTS_PER_SECOND);
    }

    public static void importSong(ServerPlayer player, MusicSheetImportPacket packet) {
        MusicScore song = null;
        try {
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof ItemMusicSheet)
                    || !java.util.Objects.equals(held.get(Items.SHEET_ID), packet.targetSheetId())
                    || held.getOrDefault(Items.SHEET_VERSION, -1) != packet.targetVersion()
                    || (!player.isCreative() && !ItemMusicSheet.isEmptySheet(held))) {
                rejectSave(player, packet.requestId(), null,
                        "The held sheet changed or cannot be replaced. Your draft was retained.");
                return;
            }
            MusicSheetImportPacket.Decoded decoded = packet.decode();
            song = decoded.song();
            if (song.signed()) {
                song = song.withMetadata(player.getName().getString(), true, song.instrumentId());
            }
            List<Integer> requestedDrumMap = decoded.drumMap();
            MusicDebugTelemetry.event("sheet_import", "received",
                    "player", player.getUUID(), "percussion", song.percussion(),
                    "notes", song.notes().size(), "drum_map_entries",
                    requestedDrumMap == null ? 0 : requestedDrumMap.size());
            if (song.percussion()) song = mapDrumsForImport(song, requestedDrumMap);
            long duration = song.tempoMap().microsAt(song.lengthTick());
            if (duration < 1 || duration > MAX_DURATION_MICROS) {
                rejectSave(player, packet.requestId(), null, "Music duration must be no longer than two hours.");
                return;
            }
            UUID id = UUID.randomUUID();
            int version = 1;
            applyToSheet(held, id, version, song);
            applyPreviewInstrument(held, decoded.previewInstrument(),
                    decoded.previewInstrumentLocked());
            held.set(Items.SHEET_HIGHLIGHT_INTERVAL,
                    (byte) Math.clamp(decoded.highlightInterval(), 1, 24));
            watch(player, id, version);
            ServerPlayNetworking.send(player, new MusicSheetDataPacket(id, version, song));
            ServerPlayNetworking.send(player, new MusicSheetSaveResultPacket(packet.requestId(), true, id, version, ""));
            MusicDebugTelemetry.event("sheet_import", "completed",
                    "player", player.getUUID(), "sheet", id, "percussion", song.percussion(),
                    "notes", song.notes().size());
        } catch (RuntimeException exception) {
            MusicDebugTelemetry.warning("sheet_import", "rejected", exception,
                    "player", player.getUUID(), "percussion", song != null && song.percussion(),
                    "notes", song == null ? 0 : song.notes().size());
            M3E.LOGGER.warn("Rejected MIDI sheet import from {}", player.getScoreboardName(), exception);
            rejectSave(player, packet.requestId(), null, "Music import failed: " + safeMessage(exception));
        }
    }

    public static void beginBatch(ServerPlayer player, SheetBatchUploadStartPacket packet) {
        cleanupBatchState();
        BatchKey key = new BatchKey(player.getUUID(), packet.transactionId());
        try {
            long now = System.nanoTime();
            if (now - LAST_BATCH_BEGIN.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2)
                    < BATCH_BEGIN_COOLDOWN_NANOS) {
                throw new IllegalArgumentException("Please wait before starting another sheet import");
            }
            if (RECENT_BATCHES.containsKey(key)) {
                throw new IllegalArgumentException("Separate import transaction was already used");
            }
            if (ACTIVE_BATCHES.containsKey(player.getUUID())) {
                throw new IllegalArgumentException("Another separate sheet import is active");
            }
            if (packet.destination() == SheetBatchUploadStartPacket.Destination.SHEETS) {
                if (BlankMusicSheet.importCapacity(player) < packet.entryCount()) {
                    throw new IllegalArgumentException("Not enough completely blank Music Sheets");
                }
            } else {
                SongbookServer.requireBatchCapacity(player, packet.songbookId(),
                        packet.songbookRevision(), packet.entryCount());
            }
            RECENT_BATCHES.put(key, now);
            LAST_BATCH_BEGIN.put(player.getUUID(), now);
            ACTIVE_BATCHES.put(player.getUUID(), new BatchUpload(player.level().getServer(), packet,
                    new ChunkedTransfer(packet.size(), packet.sha256(), SheetBatchCodec.MAX_BYTES, BATCH_TIMEOUT_NANOS, now)));
        } catch (RuntimeException exception) {
            batchResult(player, packet.transactionId(), false, safeMessage(exception));
        }
    }

    public static void batchChunk(ServerPlayer player, SheetBatchUploadChunkPacket packet) {
        BatchUpload upload = ACTIVE_BATCHES.get(player.getUUID());
        long now = System.nanoTime();
        if (upload == null || upload.server() != player.level().getServer()
                || !upload.start().transactionId().equals(packet.transactionId())) {
            ACTIVE_BATCHES.remove(player.getUUID());
            batchResult(player, packet.transactionId(), false,
                    "Separate import was missing, expired, or out of order.");
            return;
        }
        try {
            upload.transfer().append(packet.offset(), packet.bytes(), now);
        } catch (IllegalArgumentException invalid) {
            ACTIVE_BATCHES.remove(player.getUUID());
            batchResult(player, packet.transactionId(), false, invalid.getMessage());
        }
    }

    public static void finishBatch(ServerPlayer player, SheetBatchUploadFinishPacket packet) {
        BatchUpload upload = ACTIVE_BATCHES.remove(player.getUUID());
        try {
            if (upload == null || upload.server() != player.level().getServer()
                    || !upload.start().transactionId().equals(packet.transactionId())) {
                throw new IllegalArgumentException("Separate import is incomplete or expired");
            }
            byte[] bytes = upload.transfer().complete(System.nanoTime());
            List<SheetBatchCodec.Entry> entries = SheetBatchCodec.decode(bytes);
            if (entries.size() != upload.start().entryCount()) {
                throw new IllegalArgumentException("Separate import count did not match");
            }
            if (upload.start().destination() == SheetBatchUploadStartPacket.Destination.SONGBOOK) {
                SongbookServer.importBatch(player, upload.start().songbookId(),
                        upload.start().songbookRevision(), entries);
                batchResult(player, packet.transactionId(), true,
                        "Imported " + entries.size() + " Songbook songs.");
            } else {
                importBatch(player, packet.transactionId(), entries);
            }
        } catch (RuntimeException exception) {
            M3E.LOGGER.warn("Rejected separate import from {}",
                    player.getScoreboardName(), exception);
            batchResult(player, packet.transactionId(), false, safeMessage(exception));
        }
    }

    public static void cancelBatch(ServerPlayer player, SheetBatchUploadCancelPacket packet) {
        BatchUpload upload = ACTIVE_BATCHES.get(player.getUUID());
        if (upload != null && upload.start().transactionId().equals(packet.transactionId())) {
            ACTIVE_BATCHES.remove(player.getUUID());
            batchResult(player, packet.transactionId(), false, "Separate import canceled.");
        }
    }

    public static void acknowledgeBatchResult(ServerPlayer player, SheetBatchResultAckPacket packet) {
        PendingBatchResult pending = PENDING_BATCH_RESULTS.remove(
                new BatchKey(player.getUUID(), packet.transactionId()));
        if (pending == null || pending.server() != player.level().getServer()
                || System.nanoTime() - pending.createdNanos() > BATCH_TIMEOUT_NANOS) return;
        for (PreparedSheet sheet : pending.sheets()) {
            watch(player, sheet.id(), sheet.version());
            ServerPlayNetworking.send(player, new MusicSheetDataPacket(sheet.id(),
                    sheet.version(), sheet.song()));
        }
    }

    private static void importBatch(ServerPlayer player, UUID transactionId,
                                    List<SheetBatchCodec.Entry> entries) {
        if (!(player.getMainHandItem().getItem() instanceof ItemMusicSheet)
                || (!player.isCreative() && !BlankMusicSheet.isStrictlyBlank(player.getMainHandItem()))) {
            throw new IllegalArgumentException("Hold a completely blank Music Sheet in your main hand");
        }
        ArrayList<Integer> slots = new ArrayList<>(BlankMusicSheet.orderedSlots(player));
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (player.isCreative() && !slots.contains(selectedSlot)) slots.addFirst(selectedSlot);
        if (entries.isEmpty() || (!player.isCreative() && entries.size() > slots.size())) {
            throw new IllegalArgumentException("Not enough completely blank Music Sheets");
        }
        if (slots.getFirst() != selectedSlot) {
            throw new IllegalArgumentException("The held Music Sheet is not completely blank");
        }

        ArrayList<PreparedSheet> prepared = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            SheetBatchCodec.Entry entry = entries.get(index);
            MusicScore song = entry.song().renamed(entry.displayName());
            if (!song.percussion() && !entry.drumMap().isEmpty()) {
                throw new IllegalArgumentException("Drum remapping was attached to a melodic part");
            }
            if (song.percussion()) song = mapDrumsForImport(song, entry.drumMap());
            long duration = song.tempoMap().microsAt(song.lengthTick());
            if (duration < 1 || duration > MAX_DURATION_MICROS) {
                throw new IllegalArgumentException("Score duration must be no longer than two hours");
            }
            UUID id = UUID.randomUUID();
            int version = 1;
            ItemStack sheet = new ItemStack(Items.MUSIC_SHEET);
            applyToSheet(sheet, id, version, song);
            int slot = index < slots.size() ? slots.get(index) : -1;
            prepared.add(new PreparedSheet(slot, sheet, id, version, song));
        }

        Inventory inventory = player.getInventory();
        ArrayList<ItemStack> snapshots = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            snapshots.add(inventory.getItem(slot).copy());
        }
        ArrayList<ItemEntity> dropped = new ArrayList<>();
        try {
            for (PreparedSheet sheet : prepared) {
                if (sheet.slot() >= 0) {
                    inventory.setItem(sheet.slot(), sheet.stack());
                } else if (!inventory.add(sheet.stack().copy())) {
                    ItemEntity entity = player.drop(sheet.stack().copy(), false);
                    if (entity == null) throw new IllegalStateException("Could not create imported Music Sheet");
                    dropped.add(entity);
                }
            }
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
        } catch (RuntimeException exception) {
            for (int slot = 0; slot < snapshots.size(); slot++) inventory.setItem(slot, snapshots.get(slot));
            for (ItemEntity entity : dropped) entity.discard();
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            throw exception;
        }

        PENDING_BATCH_RESULTS.put(new BatchKey(player.getUUID(), transactionId),
                new PendingBatchResult(player.level().getServer(), List.copyOf(prepared), System.nanoTime()));
        batchResult(player, transactionId, true, "Imported " + prepared.size() + " Music Sheets.");
        MusicDebugTelemetry.event("sheet_batch_import", "completed",
                "player", player.getUUID(), "sheets", prepared.size(),
                "notes", prepared.stream().mapToInt(sheet -> sheet.song().notes().size()).sum());
    }

    /** Applies the requesting client's local preference to this import only. */
    public static MusicScore mapDrumsForImport(MusicScore source,
                                                      List<Integer> requestedMap) {
        MusicScore mapped = source.withDrumMap(requestedMap);
        if (mapped == source) return source;
        MusicDebugTelemetry.event("sheet_import", "drums_mapped",
                "source_notes", source.notes().size(), "mapped_notes", source.notes().size(),
                "custom_map", true);
        return mapped;
    }

    private static void applyToSheet(ItemStack sheet, UUID id, int version,
                                     MusicScore song) {
        sheet.set(Items.SHEET_ID, id);
        sheet.set(Items.SHEET_VERSION, version);
        sheet.set(Items.SHEET_GENERATION, song.signed() ? 1 : 0);
        sheet.set(Items.SHEET_VOLUME, 1.0F);
        sheet.set(MusicSheetComponents.SCORE, song);
        sheet.set(MusicSheetComponents.SOURCE_BPM, song.initialBpm());
        SheetTiming.clearLegacyComponents(sheet);
        sheet.set(Items.SHEET_HIGHLIGHT_INTERVAL, (byte) 12);
        applyPresentation(sheet, song);
        ItemMusicSheet.updateStackSize(sheet);
    }

    private static void applyPresentation(ItemStack sheet, MusicScore song) {
        sheet.set(Items.SHEET_TITLE, title(song.sourceName()));
        if (song.author().isBlank()) sheet.remove(Items.SHEET_AUTHOR);
        else sheet.set(Items.SHEET_AUTHOR, title(song.author()));
        int assigned = instrumentIndex(song.instrumentId());
        if (assigned >= 0 || song.percussion()) {
            int instrument = assigned >= 0 ? assigned : Items.INSTRUMENTS.indexOf(Items.DRUM_KIT);
            if (instrument >= 0) {
                sheet.set(Items.SHEET_PREV_INSTRUMENT, (byte) instrument);
                sheet.set(Items.SHEET_PREV_INSTRUMENT_LOCKED, true);
            }
        } else {
            sheet.remove(Items.SHEET_PREV_INSTRUMENT);
            sheet.remove(Items.SHEET_PREV_INSTRUMENT_LOCKED);
        }
    }

    public static void request(ServerPlayer player, UUID id, int version) {
        MusicScore song = canAccess(player, id, version)
                ? accessibleSong(player, id, version) : null;
        if (song != null) watch(player, id, version);
        ServerPlayNetworking.send(player, new MusicSheetDataPacket(id, version,
                song));
    }

    public static void updateTempo(ServerPlayer player,
                                   MusicSheetTempoUpdatePacket packet) {
        if (!TEMPO_RATE.allow(player.getUUID(), MAX_TEMPO_UPDATES_PER_SECOND)) return;
        var server = player.level().getServer();
        ItemStack held = player.getMainHandItem();
        UUID heldId = held.get(Items.SHEET_ID);
        int heldVersion = held.getOrDefault(Items.SHEET_VERSION, -1);
        MusicScore current = held.get(MusicSheetComponents.SCORE);
        if (current == null) {
            MusicSheetData.Entry legacy = MusicSheetData.get(
                    server, packet.sheetId(), packet.version());
            current = legacy == null ? null : legacy.song();
        }
        if (!(held.getItem() instanceof ItemMusicSheet) || !packet.sheetId().equals(heldId)
                || packet.version() != heldVersion || !canEdit(held, player) || current == null) {
            ServerPlayNetworking.send(player, new MusicSheetDataPacket(
                    packet.sheetId(), packet.version(), current));
            return;
        }
        if (current.initialBpm() == packet.bpm()) return;

        MusicScore updated = current.withInitialBpm(packet.bpm());
        if (updated.tempoMap().microsAt(updated.lengthTick()) > MAX_DURATION_MICROS) {
            ServerPlayNetworking.send(player, new MusicSheetDataPacket(
                    packet.sheetId(), packet.version(), current));
            return;
        }

        held.set(MusicSheetComponents.SCORE, updated);
        held.set(MusicSheetComponents.SOURCE_BPM, updated.initialBpm());
        watch(player, packet.sheetId(), packet.version());
        ItemMusicSheet.updateStackSize(held);

        MusicSheetDataPacket exactState = new MusicSheetDataPacket(
                packet.sheetId(), packet.version(), updated);
        for (ServerPlayer recipient : watchers(server, packet.sheetId(), packet.version())) {
            ServerPlayNetworking.send(recipient, exactState);
        }
    }

    private static void applyPreviewInstrument(ItemStack sheet, int instrument,
                                               boolean locked) {
        if (instrument < 0 || instrument >= Items.INSTRUMENTS.size()) return;
        sheet.set(Items.SHEET_PREV_INSTRUMENT, (byte) instrument);
        sheet.set(Items.SHEET_PREV_INSTRUMENT_LOCKED, locked);
    }

    public static void rejectSave(ServerPlayer player, UUID requestId, UUID sheetId, String message) {
        ServerPlayNetworking.send(player, new MusicSheetSaveResultPacket(requestId, false,
                sheetId == null ? new UUID(0L, 0L) : sheetId, 0, message));
    }

    public static void editSong(ServerPlayer player, MusicSheetEditPacket packet) {
        ItemStack held = player.getMainHandItem();
        UUID id = held.get(Items.SHEET_ID);
        int version = held.getOrDefault(Items.SHEET_VERSION, -1);
        MusicScore current = held.get(MusicSheetComponents.SCORE);
        if (!(held.getItem() instanceof ItemMusicSheet) || current == null || id == null
                || !id.equals(packet.sheetId()) || version != packet.version() || !canEdit(held, player)) {
            rejectSave(player, packet.requestId(), packet.sheetId(),
                    "The sheet changed or is no longer editable. Your draft was retained.");
            return;
        }
        try {
            MusicScore edited = packet.decode();
            boolean signing = packet.sign() && !current.signed();
            MusicScore updated = edited.withMetadata(
                    signing ? player.getName().getString() : current.author(),
                    signing || current.signed(), edited.instrumentId());
            if (updated.tempoMap().microsAt(updated.lengthTick()) > MAX_DURATION_MICROS) {
                throw new IllegalArgumentException("Music Sheet must be no longer than two hours");
            }
            int savedVersion = version + 1;
            List<ServerPlayer> recipients = new ArrayList<>(watchers(
                    player.level().getServer(), id, version));
            if (!recipients.contains(player)) recipients.add(player);
            held.set(Items.SHEET_VERSION, savedVersion);
            held.set(MusicSheetComponents.SCORE, updated);
            held.set(MusicSheetComponents.SOURCE_BPM, updated.initialBpm());
            SheetTiming.clearLegacyComponents(held);
            held.set(Items.SHEET_HIGHLIGHT_INTERVAL,
                    (byte) Math.clamp(packet.highlightInterval(), 1, 24));
            if (signing) {
                held.set(Items.SHEET_GENERATION, 1);
                held.set(Items.SHEET_AUTHOR, player.getName().getString());
            }
            applyPresentation(held, updated);
            applyPreviewInstrument(held, packet.previewInstrument(),
                    packet.previewInstrumentLocked());
            ItemMusicSheet.updateStackSize(held);
            MusicSheetDataPacket state = new MusicSheetDataPacket(id, savedVersion, updated);
            for (ServerPlayer recipient : recipients) {
                watch(recipient, id, savedVersion);
                ServerPlayNetworking.send(recipient, state);
            }
            ServerPlayNetworking.send(player, new MusicSheetSaveResultPacket(packet.requestId(), true, id, savedVersion, ""));
        } catch (RuntimeException exception) {
            M3E.LOGGER.warn("Rejected piano-roll edit from {}", player.getScoreboardName(), exception);
            rejectSave(player, packet.requestId(), id, "Music Sheet save failed: " + safeMessage(exception));
        }
    }

    public static boolean updateFromEditor(ServerPlayer player, MusicUpdatePacket packet) {
        ItemStack held = player.getMainHandItem();
        MusicScore current = held.get(MusicSheetComponents.SCORE);
        UUID id = held.get(Items.SHEET_ID);
        int version = held.getOrDefault(Items.SHEET_VERSION, -1);
        if (current == null || id == null || !id.equals(packet.id())
                || version != packet.version() || !canEdit(held, player)) return false;

        MusicUpdatePacket.FieldFlag fields = packet.availability();
        MusicScore updated = current;
        if (fields.hasNotes && packet.notes() != null) {
            updated = current.withEditorProjection(packet.notes(),
                    fields.hasVolumeMarkers && packet.volumeMarkers() != null
                            ? packet.volumeMarkers() : current.editorView().volumeMarkers(),
                    Byte.toUnsignedInt(packet.bps()), packet.lengthBeats());
        }
        if (fields.hasTitle && packet.title() != null) {
            updated = updated.renamed(packet.title().strip());
            held.set(Items.SHEET_TITLE, title(packet.title()));
        }
        if (fields.hasVolume) held.set(Items.SHEET_VOLUME,
                Math.clamp(packet.volume(), 0.0F, 1.0F));
        if (fields.hasPrevIns) held.set(Items.SHEET_PREV_INSTRUMENT, packet.prevInstrument());
        if (fields.hasPrevInsLocked) held.set(Items.SHEET_PREV_INSTRUMENT_LOCKED,
                packet.prevInsLocked());
        if (fields.hasHlInterval) held.set(Items.SHEET_HIGHLIGHT_INTERVAL,
                (byte) Math.clamp(Byte.toUnsignedInt(packet.highlightInterval()), 1, 24));
        if (fields.hasSigned && packet.signed()) {
            held.set(Items.SHEET_GENERATION, 1);
            held.set(Items.SHEET_AUTHOR, player.getName().getString());
            updated = updated.withMetadata(player.getName().getString(), true,
                    updated.instrumentId());
        }
        int savedVersion = version + (fields.hasNotes || fields.hasLength ? 1 : 0);
        held.set(Items.SHEET_VERSION, savedVersion);
        held.set(MusicSheetComponents.SCORE, updated);
        held.set(MusicSheetComponents.SOURCE_BPM, updated.initialBpm());
        SheetTiming.clearLegacyComponents(held);
        ItemMusicSheet.updateStackSize(held);
        watch(player, id, savedVersion);
        MusicSheetDataPacket state = new MusicSheetDataPacket(id, savedVersion, updated);
        for (ServerPlayer recipient : watchers(player.level().getServer(), id, savedVersion)) {
            ServerPlayNetworking.send(recipient, state);
        }
        return true;
    }

    public static void remove(ServerPlayer player) {
        IMPORT_RATE.remove(player.getUUID());
        REQUEST_RATE.remove(player.getUUID());
        TEMPO_RATE.remove(player.getUUID());
        ACTIVE_BATCHES.remove(player.getUUID());
        LAST_BATCH_BEGIN.remove(player.getUUID());
        RECENT_BATCHES.keySet().removeIf(key -> key.playerId().equals(player.getUUID()));
        PENDING_BATCH_RESULTS.keySet().removeIf(key -> key.playerId().equals(player.getUUID()));
        WATCHERS.values().forEach(watchers -> watchers.remove(player.getUUID()));
        WATCHERS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public static void clear() {
        IMPORT_RATE.clear();
        REQUEST_RATE.clear();
        TEMPO_RATE.clear();
        WATCHERS.clear();
        ACTIVE_BATCHES.clear();
        RECENT_BATCHES.clear();
        LAST_BATCH_BEGIN.clear();
        PENDING_BATCH_RESULTS.clear();
    }

    private static String instrumentIdentifier(int index) {
        if (index < 0 || index >= Items.INSTRUMENTS.size()
                || !(Items.INSTRUMENTS.get(index) instanceof net.minecraft.world.item.Item item)) return "";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static int instrumentIndex(String id) {
        if (id == null || id.isBlank()) return -1;
        for (int index = 0; index < Items.INSTRUMENTS.size(); index++) {
            if (Items.INSTRUMENTS.get(index) instanceof net.minecraft.world.item.Item item
                    && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean canAccess(ServerPlayer player, UUID id, int version) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (id.equals(stack.get(Items.SHEET_ID))
                    && version == stack.getOrDefault(Items.SHEET_VERSION, -1)) return true;
        }
        return MusicBoxLibraryServer.canAccessMaterializedSheet(player, id, version);
    }

    private static MusicScore accessibleSong(ServerPlayer player, UUID id, int version) {
        MusicSheetData.Entry legacy = MusicSheetData.get(
                player.level().getServer(), id, version);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (id.equals(stack.get(Items.SHEET_ID))
                    && version == stack.getOrDefault(Items.SHEET_VERSION, -1)) {
                MusicScore score = stack.get(MusicSheetComponents.SCORE);
                if (score != null) return score;
                score = legacy == null
                        ? NativeSheetSnapshot.capture(stack, player.level().getServer()).toPlaybackSong()
                        : legacy.song();
                migrateStack(player, stack, id, version, score);
                return score;
            }
        }
        MusicScore materialized = MusicBoxLibraryServer.scoreForMaterializedSheet(
                player, id, version);
        if (materialized != null) return materialized;
        return legacy == null ? null : legacy.song();
    }

    private static void migrateStack(ServerPlayer player, ItemStack stack, UUID id,
                                     int version, MusicScore score) {
        stack.set(MusicSheetComponents.SCORE, score);
        stack.set(Items.SHEET_VOLUME, 1.0F);
        stack.set(MusicSheetComponents.SOURCE_BPM, score.initialBpm());
        SheetTiming.clearLegacyComponents(stack);
        stack.set(Items.SHEET_HIGHLIGHT_INTERVAL, (byte) 12);
        ItemMusicSheet.updateStackSize(stack);
    }

    private static void watch(ServerPlayer player, UUID id, int version) {
        WATCHERS.computeIfAbsent(new SheetKey(id, version), ignored -> new HashSet<>())
                .add(player.getUUID());
    }

    private static List<ServerPlayer> watchers(net.minecraft.server.MinecraftServer server,
                                                UUID id, int version) {
        Set<UUID> ids = WATCHERS.getOrDefault(new SheetKey(id, version), Set.of());
        return ids.stream().map(server.getPlayerList()::getPlayer)
                .filter(java.util.Objects::nonNull).toList();
    }

    private static boolean canEdit(ItemStack sheet, ServerPlayer player) {
        int generation = sheet.getOrDefault(Items.SHEET_GENERATION, 0);
        return generation == 0 || generation > 1
                || sheet.getOrDefault(Items.SHEET_AUTHOR, "")
                .equals(player.getName().getString());
    }

    private record SheetKey(UUID id, int version) { }

    private record BatchKey(UUID playerId, UUID transactionId) { }

    private record PreparedSheet(int slot, ItemStack stack, UUID id, int version,
                                 MusicScore song) { }

    private record PendingBatchResult(net.minecraft.server.MinecraftServer server,
                                      List<PreparedSheet> sheets, long createdNanos) { }

    private record BatchUpload(net.minecraft.server.MinecraftServer server,
                               SheetBatchUploadStartPacket start, ChunkedTransfer transfer) { }

    private static void cleanupBatchState() {
        long now = System.nanoTime();
        ACTIVE_BATCHES.entrySet().removeIf(entry ->
                entry.getValue().transfer().expired(now));
        RECENT_BATCHES.entrySet().removeIf(entry -> now - entry.getValue() > REPLAY_WINDOW_NANOS);
        PENDING_BATCH_RESULTS.entrySet().removeIf(entry ->
                now - entry.getValue().createdNanos() > BATCH_TIMEOUT_NANOS);
    }

    private static void batchResult(ServerPlayer player, UUID transactionId,
                                    boolean success, String message) {
        ServerPlayNetworking.send(player, new SheetBatchResultPacket(transactionId, success, message));
    }

    private static String title(String sourceName) {
        String name = sourceName == null ? "Imported MIDI" : sourceName.strip();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".midi")) name = name.substring(0, name.length() - 5);
        else if (lower.endsWith(".mid")) name = name.substring(0, name.length() - 4);
        if (name.isBlank()) name = "Imported MIDI";
        return truncate(name, 16);
    }

    private static String truncate(String value, int maximum) {
        return value.substring(0, Math.min(maximum, value.length()));
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName()
                : truncate(message.replace('\n', ' '), 160);
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
