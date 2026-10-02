package m3e.sound;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xerca.xercamusic.common.MusicManager;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;
import xerca.xercamusic.common.block.BlockMusicBox;
import xerca.xercamusic.common.item.ItemMusicSheet;
import xerca.xercamusic.common.item.Items;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.musicbox.MusicBoxLibraryAccess;
import m3e.musicbox.MusicBoxLibraryIdentity;
import m3e.musicbox.MusicBoxLoopMode;
import m3e.musicbox.MusicBoxPlaybackAccess;
import m3e.network.MusicBoxLibraryActionPacket;
import m3e.network.MusicBoxLibraryStatePacket;
import m3e.sheet.MusicSheetComponents;
import m3e.sheet.MusicSheetData;
import m3e.sheet.MusicScore;
import m3e.sheet.SheetTiming;
import m3e.songbook.SongbookContents;
import m3e.songbook.SongbookEntry;
import m3e.songbook.SongbookItems;
import m3e.songbook.SongbookServer;
import m3e.songbook.SongbookService;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

/** Stores, materializes, catalogs, and advances Songbooks loaded in music boxes. */
public final class MusicBoxLibraryServer {
    private static final PlayerRateLimiter RATE = new PlayerRateLimiter();
    private static final Map<BoxKey, ShuffleSequence> SHUFFLE = new HashMap<>();
    private static final Map<SheetKey, BoxKey> MATERIALIZED_SHEETS = new HashMap<>();

    private MusicBoxLibraryServer() { }

    public static boolean insert(ServerPlayer player, ServerLevel level, BlockPos pos,
                                 ItemStack held) {
        if (!held.is(SongbookItems.SONGBOOK)
                || !(level.getBlockEntity(pos) instanceof TileEntityMusicBox musicBox)
                || musicBox.getBlockState().getValue(BlockMusicBox.HAS_MUSIC)) return false;
        MusicBoxLibraryAccess library = MusicBoxLibraryAccess.of(musicBox);
        ItemStack previousBook = library.m3e$musicBoxSongbook().copy();
        int previousIndex = library.m3e$musicBoxSongIndex();
        ItemStack previousSheet = musicBox.getSheetStack().copy();
        BlockState previousState = level.getBlockState(pos);
        try {
            ItemStack stored = held.copyWithCount(1);
            SongbookContents contents = SongbookService.requireContents(stored);
            library.m3e$setMusicBoxSongbook(stored);
            library.m3e$setMusicBoxSongIndex(0);
            applySelection(musicBox, contents);
            level.setBlock(pos, musicBox.getBlockState().setValue(
                    BlockMusicBox.HAS_MUSIC, true), 3);
            musicBox.setChanged();
            broadcast(musicBox, player);
            return true;
        } catch (RuntimeException exception) {
            rollbackInsert(level, pos, musicBox, library, previousBook,
                    previousIndex, previousSheet, previousState);
            if (player.connection != null) {
                player.sendSystemMessage(Component.literal(safeMessage(exception))
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
    }

    public static void action(ServerPlayer player, MusicBoxLibraryActionPacket packet) {
        ServerLevel level = player.level();
        if (!level.hasChunkAt(packet.pos())
                || !player.mayInteract(level, packet.pos())
                || player.distanceToSqr(packet.pos().getX() + 0.5D,
                packet.pos().getY() + 0.5D, packet.pos().getZ() + 0.5D) > 64.0D
                || !(level.getBlockEntity(packet.pos())
                instanceof TileEntityMusicBox musicBox)) return;
        MusicBoxLibraryAccess library = MusicBoxLibraryAccess.of(musicBox);
        if (library.m3e$musicBoxSongbook().isEmpty()) return;
        if (packet.action() < MusicBoxLibraryActionPacket.SELECT
                || packet.action() > MusicBoxLibraryActionPacket.SET_TEMPO
                || !RATE.allow(player.getUUID(), 20)) return;
        try {
            ItemStack book = library.m3e$musicBoxSongbook();
            SongbookContents contents = SongbookService.requireContents(book);
            if (contents.revision() != packet.revision()) {
                send(player, musicBox);
                return;
            }
            int index = packet.index();
            if (index < 0 || index >= contents.entries().size()) return;
            if (packet.action() == MusicBoxLibraryActionPacket.SELECT) {
                select(musicBox, contents, index, false);
            } else if (packet.action() == MusicBoxLibraryActionPacket.DELETE) {
                SongbookContents updated = contents.remove(
                        contents.entries().get(index).entryId(), false);
                SongbookService.write(book, updated);
                library.m3e$setMusicBoxSongbook(book);
                library.m3e$setMusicBoxSongIndex(
                        Math.min(index, Math.max(0, updated.entries().size() - 1)));
                replaceSelection(musicBox, updated, false);
            } else if (packet.action() == MusicBoxLibraryActionPacket.EXTRACT) {
                if (contents.capacity() <= 1) {
                    throw new IllegalArgumentException(
                            "A Songbook must keep one page; delete the song instead");
                }
                if (player.getInventory().getFreeSlot() < 0) {
                    throw new IllegalArgumentException(
                            "Make room in your inventory before extracting a sheet");
                }
                SongbookEntry entry = contents.entries().get(index);
                ItemStack sheet = SongbookServer.createExtractedSheet(player, entry);
                SongbookContents updated = contents.remove(entry.entryId(), true);
                SongbookService.write(book, updated);
                if (!player.getInventory().add(sheet)) {
                    SongbookService.write(book, contents);
                    throw new IllegalArgumentException(
                            "The extracted sheet could not be placed in your inventory");
                }
                library.m3e$setMusicBoxSongbook(book);
                library.m3e$setMusicBoxSongIndex(
                        Math.min(index, Math.max(0, updated.entries().size() - 1)));
                replaceSelection(musicBox, updated, false);
            } else {
                SongbookContents updated = contents.replace(
                        contents.entries().get(index).withTempo(packet.value()));
                SongbookService.write(book, updated);
                library.m3e$setMusicBoxSongbook(book);
                replaceSelection(musicBox, updated, false);
            }
            resetShuffle(musicBox);
            musicBox.setChanged();
            broadcast(musicBox, player);
        } catch (RuntimeException exception) {
            if (player.connection != null) {
                player.sendSystemMessage(Component.literal(safeMessage(exception))
                        .withStyle(ChatFormatting.RED), true);
            }
            send(player, musicBox);
        }
    }

    public static boolean hasSongbook(TileEntityMusicBox musicBox) {
        return MusicBoxLibraryAccess.of(musicBox).m3e$musicBoxSongbook()
                .is(SongbookItems.SONGBOOK);
    }

    public static void cycleLoopMode(TileEntityMusicBox musicBox) {
        MusicBoxPlaybackAccess playback = MusicBoxPlaybackAccess.of(musicBox);
        MusicBoxLoopMode current = MusicBoxLoopMode.byId(
                playback.m3e$musicBoxLoopMode());
        MusicBoxLoopMode next = current.next(hasSongbook(musicBox));
        playback.m3e$setMusicBoxLoopMode(next.ordinal());
        if (next == MusicBoxLoopMode.NO_LOOP || next == MusicBoxLoopMode.LOOP_SONG) {
            resetShuffle(musicBox);
        }
    }

    public static void toggleShuffle(TileEntityMusicBox musicBox) {
        MusicBoxPlaybackAccess playback = MusicBoxPlaybackAccess.of(musicBox);
        if (songCount(musicBox) <= 1) {
            playback.m3e$setMusicBoxShuffle(false);
            return;
        }
        playback.m3e$setMusicBoxShuffle(
                !playback.m3e$isMusicBoxShuffle());
        resetShuffle(musicBox);
    }

    /** Returns true when the natural end was handled and vanilla must be cancelled. */
    public static boolean advanceAtEnd(TileEntityMusicBox musicBox) {
        MusicBoxPlaybackAccess playback = MusicBoxPlaybackAccess.of(musicBox);
        MusicBoxLoopMode mode = MusicBoxLoopMode.byId(
                playback.m3e$musicBoxLoopMode());
        if (mode == MusicBoxLoopMode.LOOP_SONG) {
            playback.m3e$restartMusicBoxLoop();
            return true;
        }
        if (!hasSongbook(musicBox)
                || (mode != MusicBoxLoopMode.FINISH_SONGBOOK
                && mode != MusicBoxLoopMode.LOOP_SONGBOOK)) return false;
        MusicBoxLibraryAccess library = MusicBoxLibraryAccess.of(musicBox);
        SongbookContents contents;
        try {
            contents = SongbookService.requireContents(
                    library.m3e$musicBoxSongbook());
        } catch (RuntimeException invalid) {
            return false;
        }
        int next = nextIndex(musicBox, mode, playback.m3e$isMusicBoxShuffle(),
                library.m3e$musicBoxSongIndex(), contents.entries().size());
        if (next < 0) return false;
        select(musicBox, contents, next, true);
        broadcast(musicBox, null);
        return true;
    }

    public static void send(ServerPlayer player, TileEntityMusicBox musicBox) {
        if (player.connection != null) {
            ServerPlayNetworking.send(player, state(musicBox));
        }
    }

    public static void broadcast(TileEntityMusicBox musicBox,
                                 @Nullable ServerPlayer source) {
        MusicBoxLibraryStatePacket packet = state(musicBox);
        boolean sentToSource = false;
        for (ServerPlayer recipient : PlayerLookup.tracking(musicBox)) {
            if (recipient.connection == null) continue;
            ServerPlayNetworking.send(recipient, packet);
            sentToSource |= recipient == source;
        }
        if (source != null && source.connection != null && !sentToSource) {
            ServerPlayNetworking.send(source, packet);
        }
    }

    public static boolean canAccessMaterializedSheet(ServerPlayer player,
                                                     UUID id, int version) {
        BoxKey box = MATERIALIZED_SHEETS.get(new SheetKey(
                player.level().getServer(), id, version));
        if (box == null || !box.dimension().equals(player.level().dimension())
                || player.distanceToSqr(box.pos().getX() + 0.5D,
                box.pos().getY() + 0.5D, box.pos().getZ() + 0.5D) > 65_536.0D
                || !player.level().hasChunkAt(box.pos())
                || !(player.level().getBlockEntity(box.pos())
                instanceof TileEntityMusicBox musicBox)) return false;
        ItemStack sheet = musicBox.getSheetStack();
        return id.equals(sheet.get(Items.SHEET_ID))
                && version == sheet.getOrDefault(Items.SHEET_VERSION, -1);
    }

    public static MusicScore scoreForMaterializedSheet(ServerPlayer player,
                                                              UUID id, int version) {
        if (!canAccessMaterializedSheet(player, id, version)) return null;
        BoxKey box = MATERIALIZED_SHEETS.get(new SheetKey(
                player.level().getServer(), id, version));
        if (box == null || !(player.level().getBlockEntity(box.pos())
                instanceof TileEntityMusicBox musicBox)) return null;
        return musicBox.getSheetStack().get(MusicSheetComponents.SCORE);
    }

    public static void remove(ServerPlayer player) { RATE.remove(player.getUUID()); }

    public static void clear() {
        RATE.clear();
        SHUFFLE.clear();
        MATERIALIZED_SHEETS.clear();
    }

    public static void forget(TileEntityMusicBox musicBox) {
        BoxKey box = key(musicBox);
        SHUFFLE.remove(box);
        MATERIALIZED_SHEETS.entrySet().removeIf(entry -> entry.getValue().equals(box));
    }

    private static void select(TileEntityMusicBox musicBox, SongbookContents contents,
                               int index, boolean play) {
        MusicBoxLibraryAccess library = MusicBoxLibraryAccess.of(musicBox);
        library.m3e$setMusicBoxSongIndex(index);
        replaceSelection(musicBox, contents, play);
    }

    private static void replaceSelection(TileEntityMusicBox musicBox,
                                         SongbookContents contents, boolean play) {
        MusicBoxPlaybackAccess playback = MusicBoxPlaybackAccess.of(musicBox);
        playback.m3e$setMusicBoxPlaying(false);
        applySelection(musicBox, contents);
        MusicBoxPlaybackServer.replaceSheet(musicBox, play);
    }

    private static void applySelection(TileEntityMusicBox musicBox,
                                       SongbookContents contents) {
        MusicBoxLibraryAccess library = MusicBoxLibraryAccess.of(musicBox);
        int selected = contents.entries().isEmpty() ? -1 : Math.clamp(
                library.m3e$musicBoxSongIndex(), 0,
                contents.entries().size() - 1);
        library.m3e$setMusicBoxSongIndex(Math.max(0, selected));
        forgetMaterialized(musicBox);
        ItemStack sheet = selected < 0 ? new ItemStack(Items.MUSIC_SHEET)
                : materialize(musicBox, contents, contents.entries().get(selected));
        musicBox.setSheetStack(sheet, true);
        MusicBoxPlaybackAccess playback = MusicBoxPlaybackAccess.of(musicBox);
        int tempo = selected < 0 ? SheetTiming.DEFAULT_TEMPO_BPM
                : contents.entries().get(selected).tempoBpm();
        playback.m3e$setMusicBoxPlaybackTempoBpm(tempo);
    }

    private static ItemStack materialize(TileEntityMusicBox musicBox,
                                         SongbookContents contents,
                                         SongbookEntry entry) {
        MinecraftServer server = musicBox.getLevel().getServer();
        MusicScore song = entry.song();
        UUID id = MusicBoxLibraryIdentity.sheetId(contents.bookId(), entry.entryId());
        int version = Math.max(1, contents.revision() + 1);
        ItemStack sheet = new ItemStack(Items.MUSIC_SHEET);
        sheet.set(Items.SHEET_GENERATION, 0);
        sheet.set(MusicSheetComponents.SCORE, song);
        sheet.set(MusicSheetComponents.SOURCE_BPM, song.initialBpm());
        sheet.set(Items.SHEET_HIGHLIGHT_INTERVAL, (byte) 12);
        sheet.set(Items.SHEET_ID, id);
        sheet.set(Items.SHEET_VERSION, version);
        sheet.set(Items.SHEET_TITLE, entry.displayName());
        ItemMusicSheet.updateStackSize(sheet);
        BoxKey box = key(musicBox);
        MATERIALIZED_SHEETS.put(new SheetKey(server, id, version), box);
        return sheet;
    }

    private static void forgetMaterialized(TileEntityMusicBox musicBox) {
        BoxKey box = key(musicBox);
        MATERIALIZED_SHEETS.entrySet().removeIf(entry -> entry.getValue().equals(box));
    }

    private static MusicBoxLibraryStatePacket state(TileEntityMusicBox musicBox) {
        MusicBoxLibraryAccess library = MusicBoxLibraryAccess.of(musicBox);
        ItemStack book = library.m3e$musicBoxSongbook();
        if (book.is(SongbookItems.SONGBOOK)) {
            try {
                SongbookContents contents = SongbookService.requireContents(book);
                ArrayList<MusicBoxLibraryStatePacket.Entry> summaries =
                        new ArrayList<>(contents.entries().size());
                for (SongbookEntry entry : contents.entries()) {
                    summaries.add(summary(entry));
                }
                Component custom = book.get(DataComponents.CUSTOM_NAME);
                String label = custom == null ? "Songbook #1" : custom.getString();
                int selected = summaries.isEmpty() ? -1 : Math.clamp(
                        library.m3e$musicBoxSongIndex(), 0,
                        summaries.size() - 1);
                return new MusicBoxLibraryStatePacket(musicBox.getBlockPos(),
                        MusicBoxLibraryStatePacket.SONGBOOK, contents.bookId(), label,
                        contents.revision(), contents.capacity(), selected, summaries);
            } catch (RuntimeException ignored) {
                return emptyState(musicBox);
            }
        }
        ItemStack sheet = musicBox.getSheetStack();
        if (sheet.isEmpty() || ItemMusicSheet.isEmptySheet(sheet)) return emptyState(musicBox);
        UUID id = sheet.get(Items.SHEET_ID);
        String title = sheet.getOrDefault(Items.SHEET_TITLE, "Music Sheet");
        SheetTiming timing = SheetTiming.from(sheet);
        int tempo = timing.musicalTempoBpm();
        long duration = timing.durationMicros();
        int numerator = 4;
        int denominator = 4;
        if (id != null && musicBox.getLevel() != null
                && musicBox.getLevel().getServer() != null) {
            int version = sheet.getOrDefault(Items.SHEET_VERSION, -1);
            MusicScore song = sheet.get(MusicSheetComponents.SCORE);
            if (song == null) {
                MusicSheetData.Entry legacy = MusicSheetData.get(
                        musicBox.getLevel().getServer(), id, version);
                song = legacy == null ? null : legacy.song();
            }
            if (song != null && !song.timeSignatures().isEmpty()) {
                MusicScore.TimeSignature signature =
                        song.timeSignatures().getFirst();
                numerator = signature.numerator();
                denominator = signature.denominator();
                tempo = song.initialBpm();
                duration = song.tempoMap().microsAt(song.lengthTick());
            }
        }
        MusicBoxLibraryStatePacket.Entry entry = new MusicBoxLibraryStatePacket.Entry(
                id == null ? MusicBoxLibraryStatePacket.NO_LIBRARY_ID : id,
                title, tempo, duration, numerator, denominator);
        return new MusicBoxLibraryStatePacket(musicBox.getBlockPos(),
                MusicBoxLibraryStatePacket.LOOSE_SHEET,
                MusicBoxLibraryStatePacket.NO_LIBRARY_ID, "None", 0, 1, 0,
                List.of(entry));
    }

    private static MusicBoxLibraryStatePacket emptyState(TileEntityMusicBox musicBox) {
        return new MusicBoxLibraryStatePacket(musicBox.getBlockPos(),
                MusicBoxLibraryStatePacket.EMPTY,
                MusicBoxLibraryStatePacket.NO_LIBRARY_ID, "None", 0, 1, -1,
                List.of());
    }

    private static MusicBoxLibraryStatePacket.Entry summary(SongbookEntry entry) {
        MusicScore song = entry.song();
        MusicScore.TimeSignature signature = song.timeSignatures().isEmpty()
                ? new MusicScore.TimeSignature(0, 4, 4)
                : song.timeSignatures().getFirst();
        return new MusicBoxLibraryStatePacket.Entry(entry.entryId(),
                entry.displayName(), entry.tempoBpm(),
                song.tempoMap().microsAt(song.lengthTick()),
                signature.numerator(), signature.denominator());
    }

    private static int nextIndex(TileEntityMusicBox musicBox, MusicBoxLoopMode mode,
                                 boolean shuffle, int currentIndex, int songCount) {
        if (songCount <= 0) return -1;
        int current = Math.clamp(currentIndex, 0, songCount - 1);
        if (songCount == 1) return mode == MusicBoxLoopMode.LOOP_SONGBOOK ? 0 : -1;
        if (!shuffle) {
            int next = current + 1;
            return next < songCount ? next
                    : mode == MusicBoxLoopMode.LOOP_SONGBOOK ? 0 : -1;
        }
        if (mode == MusicBoxLoopMode.FINISH_SONGBOOK) {
            ShuffleSequence sequence = SHUFFLE.computeIfAbsent(key(musicBox),
                    ignored -> new ShuffleSequence(songCount));
            sequence.resize(songCount);
            sequence.completed.set(current);
            int remaining = songCount - sequence.completed.cardinality();
            if (remaining <= 0) return -1;
            int choice = ThreadLocalRandom.current().nextInt(remaining);
            for (int index = sequence.completed.nextClearBit(0);
                 index >= 0 && index < songCount;
                 index = sequence.completed.nextClearBit(index + 1)) {
                if (choice-- == 0) return index;
            }
            return -1;
        }
        int offset = ThreadLocalRandom.current().nextInt(songCount - 1) + 1;
        return (current + offset) % songCount;
    }

    private static void resetShuffle(TileEntityMusicBox musicBox) {
        SHUFFLE.remove(key(musicBox));
    }

    private static int songCount(TileEntityMusicBox musicBox) {
        try {
            return SongbookService.requireContents(MusicBoxLibraryAccess.of(musicBox)
                    .m3e$musicBoxSongbook()).entries().size();
        } catch (RuntimeException invalid) {
            return 0;
        }
    }

    private static void rollbackInsert(ServerLevel level, BlockPos pos,
                                       TileEntityMusicBox musicBox,
                                       MusicBoxLibraryAccess library,
                                       ItemStack previousBook, int previousIndex,
                                       ItemStack previousSheet, BlockState previousState) {
        forgetMaterialized(musicBox);
        library.m3e$setMusicBoxSongbook(previousBook);
        library.m3e$setMusicBoxSongIndex(previousIndex);
        if (previousSheet.isEmpty()) {
            musicBox.removeSheetStack();
        } else {
            musicBox.setSheetStack(previousSheet, true);
        }
        if (!level.getBlockState(pos).equals(previousState)) {
            level.setBlock(pos, previousState, 3);
        }
        musicBox.setChanged();
    }

    private static BoxKey key(TileEntityMusicBox musicBox) {
        return new BoxKey(musicBox.getLevel().dimension(),
                musicBox.getBlockPos().immutable());
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "The Songbook could not be used.";
        return message.length() <= 160 ? message : message.substring(0, 160);
    }

    private record BoxKey(ResourceKey<Level> dimension, BlockPos pos) { }
    private record SheetKey(MinecraftServer server, UUID id, int version) { }

    private static final class ShuffleSequence {
        private final BitSet completed = new BitSet();
        private int songCount;

        private ShuffleSequence(int songCount) { this.songCount = songCount; }

        private void resize(int value) {
            if (songCount == value) return;
            completed.clear();
            songCount = value;
        }
    }
}
