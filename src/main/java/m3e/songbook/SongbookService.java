package m3e.songbook;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import xerca.xercamusic.common.item.ItemMusicSheet;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.Items;
import m3e.M3E;
import m3e.sheet.MusicSheetData;
import m3e.sheet.MusicSheetComponents;
import m3e.sheet.MusicScore;
import m3e.sheet.BlankMusicSheet;

import java.util.UUID;

/** Server-authoritative Songbook mutations shared by item interaction and networking. */
public final class SongbookService {
    private SongbookService() { }

    public static void write(ItemStack stack, SongbookContents contents) {
        stack.set(SongbookComponents.CONTENTS, contents);
        stack.set(SongbookComponents.HEADER, contents.header());
    }

    public static boolean isCompletelyBlank(ItemStack stack) {
        return BlankMusicSheet.isStrictlyBlank(stack);
    }

    public static void insertHeldSheet(Player player, InteractionHand bookHand, InteractionHand sheetHand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ItemStack book = player.getItemInHand(bookHand);
        ItemStack sheet = player.getItemInHand(sheetHand);
        if (!book.is(SongbookItems.SONGBOOK) || !(sheet.getItem() instanceof ItemMusicSheet)) return;
        if (ItemMusicSheet.isEmptySheet(sheet)) {
            if (!isCompletelyBlank(sheet)) {
                fail(serverPlayer, "Only a completely blank Music Sheet can become an empty Songbook page.");
                return;
            }
            try {
                SongbookContents contents = requireContents(book);
                if (contents.capacity() >= SongbookContents.MAX_CAPACITY) {
                    throw new IllegalArgumentException("Songbook is already at maximum capacity");
                }
                write(book, contents.withCapacity(contents.capacity() + 1));
                if (!player.getAbilities().instabuild) sheet.shrink(1);
            } catch (RuntimeException exception) {
                M3E.LOGGER.warn("Could not add a blank page to {}'s Songbook",
                        serverPlayer.getScoreboardName(), exception);
                fail(serverPlayer, safeMessage(exception));
            }
            return;
        }
        try {
            SongbookContents contents = requireContents(book);
            MusicScore song = exactSong(sheet, serverPlayer.level().getServer());
            String name;
            if (song == null) {
                NativeSheetSnapshot imported = NativeSheetSnapshot.capture(
                        sheet, serverPlayer.level().getServer());
                song = imported.toPlaybackSong().withMetadata(
                        imported.author(), imported.signed(), imported.instrumentId());
                name = imported.title();
            } else {
                name = sheet.getOrDefault(Items.SHEET_TITLE, "Music Sheet");
                String author = sheet.getOrDefault(Items.SHEET_AUTHOR, "").strip();
                boolean signed = sheet.getOrDefault(Items.SHEET_GENERATION, 0) > 0;
                song = song.withMetadata(author, signed, song.instrumentId());
            }
            int displayTempo = song.initialBpm();
            if (name.isBlank()) name = "Music Sheet";
            SongbookMetadata metadata = sheetMetadata(song);
            SongbookEntry entry = new SongbookEntry(UUID.randomUUID(), name, displayTempo,
                    SongbookSongCodec.encode(song), metadata);
            // A physical Music Sheet supplies the physical page it is written
            // on, regardless of whether it originated from this Songbook.
            // Only file imports need a pre-existing empty page.
            SongbookContents updated = contents.add(entry, true);
            write(book, updated);
            if (!player.getAbilities().instabuild) {
                sheet.shrink(1);
            }
        } catch (RuntimeException exception) {
            M3E.LOGGER.warn("Could not insert a held sheet into {}'s Songbook",
                    serverPlayer.getScoreboardName(), exception);
            fail(serverPlayer, safeMessage(exception));
        }
    }

    static SongbookMetadata sheetMetadata(MusicScore song) {
        String instrument = "";
        if (!song.instrumentId().isEmpty()) {
            for (IItemInstrument value : Items.INSTRUMENTS) {
                if (value instanceof net.minecraft.world.item.Item item
                        && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)
                        .toString().equals(song.instrumentId())) {
                    instrument = new ItemStack(item).getHoverName().getString();
                    break;
                }
            }
        }
        return new SongbookMetadata(instrument, song.instrumentId(), song.author(), song.signed());
    }

    public static SongbookContents requireContents(ItemStack stack) {
        SongbookContents contents = stack.get(SongbookComponents.CONTENTS);
        SongbookHeader header = stack.get(SongbookComponents.HEADER);
        if (contents == null && header != null && header.revision() == 0 && header.songCount() == 0) {
            contents = new SongbookContents(SongbookContents.FORMAT_VERSION, header.bookId(), 0,
                    header.capacity(), java.util.List.of());
            write(stack, contents);
            return contents;
        }
        if (contents == null || header == null || !contents.header().equals(header)) {
            throw new IllegalArgumentException("Songbook data is missing or inconsistent");
        }
        return contents;
    }

    private static MusicScore exactSong(ItemStack sheet, MinecraftServer server) {
        MusicScore embedded = sheet.get(MusicSheetComponents.SCORE);
        if (embedded != null) return embedded;
        UUID id = sheet.get(Items.SHEET_ID);
        int version = sheet.getOrDefault(Items.SHEET_VERSION, -1);
        if (id == null || version < 0) return null;
        MusicSheetData.Entry entry = MusicSheetData.get(server, id, version);
        return entry == null ? null : entry.song();
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "The sheet could not be inserted.";
        return message.length() <= 160 ? message : message.substring(0, 160);
    }

    private static void fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
    }
}
