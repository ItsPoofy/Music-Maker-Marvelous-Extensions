package m3e.songbook;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import m3e.M3E;
import m3e.network.SongbookActionPacket;
import m3e.network.SongbookCatalogPacket;
import m3e.network.SongbookResultPacket;
import m3e.network.SongbookSongChunkPacket;
import m3e.network.SongbookSongFinishPacket;
import m3e.network.SongbookSongStartPacket;
import m3e.network.SongbookUploadChunkPacket;
import m3e.network.SongbookUploadStartPacket;
import m3e.sheet.MusicSheetServer;
import m3e.sheet.SheetFileIO;
import m3e.sheet.MusicScore;
import m3e.sheet.SheetBatchCodec;

import m3e.network.ChunkedTransfer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Possession-checked Songbook catalog, transfer, and mutation boundary. */
public final class SongbookServer {
    private static final long MAX_DURATION_MICROS = 7_200_000_000L;
    private static final long UPLOAD_TIMEOUT_NANOS = 15_000_000_000L;
    private static final long BEGIN_COOLDOWN_NANOS = 500_000_000L;
    private static final Map<UUID, Upload> ACTIVE_UPLOADS = new HashMap<>();
    private static final Map<UUID, Long> LAST_BEGIN = new HashMap<>();
    private static final Map<UUID, Long> LAST_DOWNLOAD = new HashMap<>();

    private SongbookServer() { }

    public static void sendCatalog(ServerPlayer player, UUID bookId) {
        try {
            LocatedBook located = requireBook(player, bookId);
            sendCatalog(player, located.contents());
        } catch (RuntimeException exception) {
            result(player, false, safeMessage(exception));
        }
    }

    public static void sendSong(ServerPlayer player, UUID bookId, int revision, UUID entryId) {
        try {
            long now = System.nanoTime();
            long previous = LAST_DOWNLOAD.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
            if (now - previous < 250_000_000L) throw new IllegalArgumentException("Please wait before loading another song");
            LocatedBook located = requireBook(player, bookId, revision);
            SongbookEntry entry = findEntry(located.contents(), entryId);
            LAST_DOWNLOAD.put(player.getUUID(), now);
            byte[] bytes = SongbookPlaybackCodec.encode(entry);
            ServerPlayNetworking.send(player, new SongbookSongStartPacket(bookId, revision, entryId,
                    bytes.length, SongbookHash.sha256(bytes)));
            for (int offset = 0; offset < bytes.length; offset += SongbookSongChunkPacket.MAX_CHUNK_BYTES) {
                int length = Math.min(SongbookSongChunkPacket.MAX_CHUNK_BYTES, bytes.length - offset);
                ServerPlayNetworking.send(player, new SongbookSongChunkPacket(offset,
                        Arrays.copyOfRange(bytes, offset, offset + length)));
            }
            ServerPlayNetworking.send(player, new SongbookSongFinishPacket(entryId));
        } catch (RuntimeException exception) {
            result(player, false, safeMessage(exception));
        }
    }

    public static void beginUpload(ServerPlayer player, SongbookUploadStartPacket packet) {
        long now = System.nanoTime();
        long previous = LAST_BEGIN.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        if (now - previous < BEGIN_COOLDOWN_NANOS) {
            result(player, false, "Please wait before starting another Songbook import.");
            return;
        }
        try {
            LocatedBook located = requireBook(player, packet.bookId(), packet.revision());
            if (located.contents().entries().size() >= located.contents().capacity()) {
                throw new IllegalArgumentException("Songbook has no empty page");
            }
            if (located.contents().encodedBytes() + packet.storedSize()
                    > SongbookContents.MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException("Songbook does not have enough data capacity");
            }
            LAST_BEGIN.put(player.getUUID(), now);
            ACTIVE_UPLOADS.put(player.getUUID(), new Upload(player.level().getServer(), packet,
                    new ChunkedTransfer(packet.size(), packet.sha256(), SongbookContents.MAX_ENCODED_BYTES, UPLOAD_TIMEOUT_NANOS, now)));
        } catch (RuntimeException exception) {
            result(player, false, safeMessage(exception));
        }
    }

    public static void uploadChunk(ServerPlayer player, SongbookUploadChunkPacket packet) {
        Upload upload = ACTIVE_UPLOADS.get(player.getUUID());
        long now = System.nanoTime();
        if (upload == null || upload.server() != player.level().getServer()
) {
            ACTIVE_UPLOADS.remove(player.getUUID());
            result(player, false, "Songbook import was missing, expired, or out of order.");
            return;
        }
        try {
            upload.transfer().append(packet.offset(), packet.bytes(), now);
        } catch (IllegalArgumentException invalid) {
            ACTIVE_UPLOADS.remove(player.getUUID());
            result(player, false, invalid.getMessage());
        }
    }

    public static void finishUpload(ServerPlayer player) {
        Upload upload = ACTIVE_UPLOADS.remove(player.getUUID());
        try {
            if (upload == null || upload.server() != player.level().getServer()
) {
                throw new IllegalArgumentException("Songbook import is incomplete or expired");
            }
            byte[] bytes = upload.transfer().complete(System.nanoTime());
            MusicScore song = decodeUpload(upload.start(), bytes, player);
            long duration = song.tempoMap().microsAt(song.lengthTick());
            if (duration < 1 || duration > MAX_DURATION_MICROS) {
                throw new IllegalArgumentException("Song duration must be no longer than two hours");
            }
            LocatedBook located = requireBook(player, upload.start().bookId(), upload.start().revision());
            SongbookEntry entry = new SongbookEntry(UUID.randomUUID(), upload.start().displayName(),
                    song.initialBpm(), SongbookSongCodec.encode(song), SongbookService.sheetMetadata(song));
            SongbookContents updated = located.contents().add(entry, false);
            SongbookService.write(located.stack(), updated);
            sendCatalog(player, updated);
        } catch (RuntimeException exception) {
            M3E.LOGGER.warn("Rejected Songbook music upload from {}",
                    player.getScoreboardName(), exception);
            result(player, false, safeMessage(exception));
        }
    }

    private static MusicScore decodeUpload(SongbookUploadStartPacket start, byte[] bytes,
                                                  ServerPlayer player) {
        if (start.kind() == SongbookUploadStartPacket.Kind.MIDI) {
            return SongbookSongCodec.decode(bytes);
        }
        CompoundTag file = readSheetFile(bytes);
        MusicScore score = SheetFileIO.score(file);
        return score != null ? score : NativeSheetSnapshot.fromSheetFile(file,
                player.level().getServer()).toPlaybackSong();
    }

    private static CompoundTag readSheetFile(byte[] bytes) {
        try {
            return SheetFileIO.read(bytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("file is not a valid Music Maker .sheet", exception);
        }
    }

    public static void requireBatchCapacity(ServerPlayer player, UUID bookId,
                                            int revision, int entryCount) {
        if (entryCount < 1 || entryCount > SheetBatchCodec.MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid Songbook import count");
        }
        LocatedBook located = requireBook(player, bookId, revision);
        int capacity = player.isCreative() ? SongbookContents.MAX_CAPACITY
                : located.contents().capacity();
        if (entryCount > capacity - located.contents().entries().size()) {
            throw new IllegalArgumentException("Songbook does not have enough empty pages");
        }
    }

    public static void importBatch(ServerPlayer player, UUID bookId, int revision,
                                   List<SheetBatchCodec.Entry> entries) {
        requireBatchCapacity(player, bookId, revision, entries.size());
        LocatedBook located = requireBook(player, bookId, revision);
        ArrayList<SongbookEntry> additions = new ArrayList<>(entries.size());
        for (SheetBatchCodec.Entry batchEntry : entries) {
            MusicScore song = batchEntry.song().renamed(batchEntry.displayName());
            if (!song.percussion() && !batchEntry.drumMap().isEmpty()) {
                throw new IllegalArgumentException("Drum remapping was attached to a melodic part");
            }
            if (song.percussion()) {
                song = MusicSheetServer.mapDrumsForImport(song, batchEntry.drumMap());
            }
            long duration = song.tempoMap().microsAt(song.lengthTick());
            if (duration < 1 || duration > MAX_DURATION_MICROS) {
                throw new IllegalArgumentException("Song duration must be no longer than two hours");
            }
            additions.add(new SongbookEntry(UUID.randomUUID(), batchEntry.displayName(),
                    song.initialBpm(), SongbookSongCodec.encode(song)));
        }
        SongbookContents contents = located.contents();
        int requiredCapacity = contents.entries().size() + additions.size();
        if (player.isCreative() && requiredCapacity > contents.capacity()) {
            contents = contents.withCapacity(requiredCapacity);
        }
        SongbookContents updated = contents.addAll(additions);
        ItemStack snapshot = located.stack().copy();
        try {
            SongbookService.write(located.stack(), updated);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            sendCatalog(player, updated);
        } catch (RuntimeException exception) {
            player.getInventory().setItem(located.slot(), snapshot);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            throw exception;
        }
        M3E.LOGGER.info("Imported {} separate songs into Songbook {} for {}",
                additions.size(), bookId, player.getScoreboardName());
    }

    public static void action(ServerPlayer player, SongbookActionPacket packet) {
        try {
            LocatedBook located = requireBook(player, packet.bookId(), packet.revision());
            if (packet.action() == SongbookActionPacket.Action.RENAME_BOOK) {
                String name = packet.text() == null ? "" : packet.text().strip();
                if (name.length() > 48) name = name.substring(0, 48);
                if (name.isEmpty()) located.stack().remove(DataComponents.CUSTOM_NAME);
                else located.stack().set(DataComponents.CUSTOM_NAME, Component.literal(name));
                sendCatalog(player, located.contents());
                return;
            }
            SongbookEntry entry = findEntry(located.contents(), packet.entryId());
            SongbookContents updated;
            switch (packet.action()) {
                case RENAME -> {
                    if (entry.metadata().signed()) {
                        throw new IllegalArgumentException("Signed sheet titles cannot be changed");
                    }
                    String name = packet.text() == null ? "" : packet.text().strip();
                    if (name.isEmpty()) throw new IllegalArgumentException("Song name cannot be empty");
                    updated = located.contents().replace(entry.renamed(name));
                }
                case SET_TEMPO -> updated = located.contents().replace(entry.withTempo(packet.value()));
                case SET_INSTRUMENT -> {
                    String id = packet.text() == null ? "" : packet.text().strip();
                    String name = "";
                    if (!id.isEmpty()) {
                        for (xerca.xercamusic.common.item.IItemInstrument instrument :
                                xerca.xercamusic.common.item.Items.INSTRUMENTS) {
                            if (instrument instanceof net.minecraft.world.item.Item item
                                    && net.minecraft.core.registries.BuiltInRegistries.ITEM
                                    .getKey(item).toString().equals(id)) {
                                name = new ItemStack(item).getHoverName().getString();
                                break;
                            }
                        }
                        if (name.isEmpty()) throw new IllegalArgumentException("Unknown instrument");
                    }
                    updated = located.contents().replace(entry.withInstrument(id, name));
                }
                case DELETE -> updated = located.contents().remove(entry.entryId(), false);
                case MOVE -> updated = located.contents().move(entry.entryId(), packet.value());
                case EXTRACT_SHEET -> {
                    if (located.contents().capacity() <= 1) {
                        throw new IllegalArgumentException("A Songbook must keep one page; delete the song instead");
                    }
                    if (player.getInventory().getFreeSlot() < 0) {
                        throw new IllegalArgumentException("Make room in your inventory before extracting a sheet");
                    }
                    ItemStack sheet = createExtractedSheet(player, entry);
                    updated = located.contents().remove(entry.entryId(), true);
                    SongbookService.write(located.stack(), updated);
                    if (!player.getInventory().add(sheet)) {
                        SongbookService.write(located.stack(), located.contents());
                        throw new IllegalArgumentException("The extracted sheet could not be placed in your inventory");
                    }
                    sendCatalog(player, updated);
                    return;
                }
                default -> throw new IllegalArgumentException("unsupported Songbook action");
            }
            SongbookService.write(located.stack(), updated);
            sendCatalog(player, updated);
        } catch (RuntimeException exception) {
            result(player, false, safeMessage(exception));
        }
    }

    public static void remove(ServerPlayer player) {
        ACTIVE_UPLOADS.remove(player.getUUID());
        LAST_BEGIN.remove(player.getUUID());
        LAST_DOWNLOAD.remove(player.getUUID());
    }

    public static void tick() {
        long now = System.nanoTime();
        ACTIVE_UPLOADS.entrySet().removeIf(entry ->
                entry.getValue().transfer().expired(now));
        LAST_BEGIN.entrySet().removeIf(entry -> now - entry.getValue() > UPLOAD_TIMEOUT_NANOS);
        LAST_DOWNLOAD.entrySet().removeIf(entry -> now - entry.getValue() > UPLOAD_TIMEOUT_NANOS);
    }

    public static void clear() {
        ACTIVE_UPLOADS.clear();
        LAST_BEGIN.clear();
        LAST_DOWNLOAD.clear();
    }

    public static ItemStack createExtractedSheet(ServerPlayer player, SongbookEntry entry) {
        ItemStack sheet = MusicSheetServer.createSheet(player, entry.song(), entry.displayName());
        sheet.remove(xerca.xercamusic.common.item.Items.SHEET_AUTHOR);
        if (!entry.metadata().signed()) {
            sheet.set(xerca.xercamusic.common.item.Items.SHEET_TITLE, entry.displayName());
        }
        if (entry.metadata().hasAuthor()) {
            String author = entry.metadata().author();
            sheet.set(xerca.xercamusic.common.item.Items.SHEET_AUTHOR,
                    author.substring(0, Math.min(author.length(), 16)));
        }
        sheet.set(SongbookComponents.EXTRACTED_PAGE, true);
        return sheet;
    }

    private static void sendCatalog(ServerPlayer player, SongbookContents contents) {
        ArrayList<SongbookCatalogPacket.Entry> summaries = new ArrayList<>(contents.entries().size());
        for (SongbookEntry entry : contents.entries()) {
            MusicScore song = entry.song();
            List<MusicScore.TimeSignature> signatures = song.timeSignatures();
            MusicScore.TimeSignature signature = signatures.isEmpty()
                    ? new MusicScore.TimeSignature(0, 4, 4) : signatures.getFirst();
            long duration = Math.clamp(song.tempoMap().microsAt(song.lengthTick()), 0L, MAX_DURATION_MICROS);
            summaries.add(new SongbookCatalogPacket.Entry(entry.entryId(), entry.displayName(),
                    entry.tempoBpm(), duration, signature.numerator(), signature.denominator(),
                    entry.metadata()));
        }
        ServerPlayNetworking.send(player, new SongbookCatalogPacket(contents.bookId(), contents.revision(),
                contents.capacity(), contents.encodedBytes(), summaries));
    }

    private static LocatedBook requireBook(ServerPlayer player, UUID bookId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(SongbookItems.SONGBOOK)) continue;
            SongbookHeader header = stack.get(SongbookComponents.HEADER);
            if (header != null && header.bookId().equals(bookId)) {
                return new LocatedBook(slot, stack, SongbookService.requireContents(stack));
            }
        }
        throw new IllegalArgumentException("That Songbook is no longer in your inventory");
    }

    private static LocatedBook requireBook(ServerPlayer player, UUID bookId, int revision) {
        LocatedBook located = requireBook(player, bookId);
        if (located.contents().revision() != revision) throw new IllegalArgumentException("Songbook changed; refresh and try again");
        return located;
    }

    private static SongbookEntry findEntry(SongbookContents contents, UUID entryId) {
        return contents.entries().stream().filter(entry -> entry.entryId().equals(entryId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That song is no longer in the Songbook"));
    }

    private static void result(ServerPlayer player, boolean success, String message) {
        ServerPlayNetworking.send(player, new SongbookResultPacket(success, message));
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() <= 180 ? message : message.substring(0, 180);
    }

    private record LocatedBook(int slot, ItemStack stack, SongbookContents contents) { }
    private record Upload(net.minecraft.server.MinecraftServer server,
                          SongbookUploadStartPacket start, ChunkedTransfer transfer) { }
}
