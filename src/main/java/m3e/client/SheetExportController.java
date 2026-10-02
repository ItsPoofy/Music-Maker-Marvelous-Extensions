package m3e.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import xerca.xercamusic.common.item.Items;
import m3e.sheet.MusicSheetComponents;
import m3e.sheet.MusicScore;
import m3e.sheet.SheetFileIO;
import java.nio.file.Path;
import java.io.IOException;

import java.util.List;
import java.util.UUID;

/** Waits for the editor's normal server save, then invokes Music Maker's own exporter. */
public final class SheetExportController {
    private static final int TIMEOUT_TICKS = 200;
    private static Request pending;

    private SheetExportController() { }

    public static void queue(UUID id, int minimumVersion, float volume,
                             int generation, String title, int previousInstrument,
                             boolean previousInstrumentLocked, int highlightInterval,
                             String filename, boolean compatibility) {
        pending = new Request(id, minimumVersion, volume, generation,
                title == null ? "" : title, previousInstrument, previousInstrumentLocked,
                highlightInterval, safeFilename(filename), compatibility, 0);
    }

    public static void tick(Minecraft client) {
        Request request = pending;
        LocalPlayer player = client.player;
        if (request == null || player == null) return;

        ItemStack sheet = matchingSheet(player, request.id());
        if (sheet != null && matchesSavedState(sheet, request)) {
            pending = null;
            boolean accepted = export(sheet, request.filename(), request.compatibility());
            Component message = accepted
                    ? Component.translatable("xercamusic.export.success", request.filename())
                    .withStyle(ChatFormatting.GREEN)
                    : Component.translatable("xercamusic.export.fail").withStyle(ChatFormatting.RED);
            player.sendSystemMessage(message);
            return;
        }

        if (request.waitedTicks() >= TIMEOUT_TICKS) {
            pending = null;
            player.sendSystemMessage(Component.literal(
                    "Sheet export timed out while waiting for the editor save.")
                    .withStyle(ChatFormatting.RED));
        } else {
            pending = request.waitedOneTick();
        }
    }

    public static void clear() { pending = null; }

    private static boolean export(ItemStack sheet, String filename, boolean compatibility) {
        MusicScore score = sheet.get(MusicSheetComponents.SCORE);
        if (score == null) {
            UUID id = sheet.get(Items.SHEET_ID);
            score = id == null ? null : MusicSheetClientCache.get(id);
        }
        if (score == null) return false;
        try {
            Path path = Path.of("music_sheets").resolve(filename + ".sheet");
            if (compatibility) SheetFileIO.writeCompatibility(path, sheet, score);
            else SheetFileIO.writeScore(path, score);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static ItemStack matchingSheet(LocalPlayer player, UUID id) {
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            UUID stackId = stack.get(Items.SHEET_ID);
            if (id == null ? stackId != null : id.equals(stackId)) return stack;
        }
        return null;
    }

    private static boolean matchesSavedState(ItemStack sheet, Request request) {
        Byte previous = sheet.get(Items.SHEET_PREV_INSTRUMENT);
        int previousInstrument = previous == null ? -1 : previous;
        return sheet.getOrDefault(Items.SHEET_VERSION, -1) >= request.minimumVersion()
                && Math.abs(sheet.getOrDefault(Items.SHEET_VOLUME, 1.0F) - request.volume()) < 0.0001F
                && sheet.getOrDefault(Items.SHEET_GENERATION, 0) == request.generation()
                && sheet.getOrDefault(Items.SHEET_TITLE, "").equals(request.title())
                && previousInstrument == request.previousInstrument()
                && sheet.getOrDefault(Items.SHEET_PREV_INSTRUMENT_LOCKED, false)
                == request.previousInstrumentLocked()
                && (sheet.getOrDefault(Items.SHEET_HIGHLIGHT_INTERVAL, (byte) 12) & 0xFF)
                == request.highlightInterval();
    }

    private static String safeFilename(String requested) {
        String safe = requested == null ? "" : requested.strip()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("^\\.+|\\.+$", "");
        if (safe.isBlank()) return "music-sheet";
        if (safe.matches("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) return "_" + safe;
        return safe;
    }

    private record Request(UUID id, int minimumVersion, float volume,
                           int generation, String title, int previousInstrument,
                           boolean previousInstrumentLocked, int highlightInterval,
                           String filename, boolean compatibility, int waitedTicks) {
        private Request waitedOneTick() {
            return new Request(id, minimumVersion, volume, generation, title,
                    previousInstrument, previousInstrumentLocked, highlightInterval,
                    filename, compatibility, waitedTicks + 1);
        }
    }
}
