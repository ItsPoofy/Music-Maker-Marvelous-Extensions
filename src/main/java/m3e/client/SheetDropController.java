package m3e.client;

import m3e.M3E;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xerca.xercamusic.common.item.ItemMusicSheet;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Routes supported dropped files without blocking Minecraft's render thread. */
public final class SheetDropController {
    private SheetDropController() { }

    public static boolean handle(Screen sheetScreen, List<Path> paths) {
        Path supported = paths.stream().filter(SheetDropController::supported).findFirst().orElse(null);
        if (supported == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        String lower = supported.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean canImport = lower.endsWith(".sheet")
                ? hasWritableSheet(minecraft) : hasWritableSheet(minecraft, sheetScreen);
        if (!canImport) {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(
                        "Hold an editable Music Maker sheet in your main hand to import this file.")
                        .withStyle(ChatFormatting.RED));
            }
            return true;
        }
        long generation = AsyncMusicImport.begin();
        if (lower.endsWith(".sheet")) {
            LegacySheetDropImporter.importPath(supported, () -> {
                if (minecraft.gui.screen() != sheetScreen || !hasWritableSheet(minecraft)) return false;
                prepareIfBlank(sheetScreen);
                beginReplacementImport(sheetScreen);
                return true;
            });
            return true;
        }
        if (lower.endsWith(".abc")) {
            AbcImportController.start(supported, sheetScreen,
                    ScoreImportDestination.sheet(sheetScreen),
                    () -> hasWritableSheet(minecraft, sheetScreen), generation);
            return true;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return MidiSheetFile.read(supported);
            } catch (Exception exception) {
                throw new MidiReadException(exception);
            }
        }).whenComplete((analysis, error) -> minecraft.execute(() -> {
            if (!AsyncMusicImport.isCurrent(generation)) return;
            if (error != null) {
                Throwable cause = error.getCause() instanceof MidiReadException wrapped ? wrapped.getCause() : error;
                String message = cause.getMessage();
                if (message == null || message.isBlank()) message = cause.getClass().getSimpleName();
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal("MIDI import failed: "
                            + message.substring(0, Math.min(180, message.length()))).withStyle(ChatFormatting.RED));
                }
                M3E.LOGGER.warn("Could not inspect dropped MIDI {}", supported, cause);
                return;
            }
            if (minecraft.gui.screen() != sheetScreen || !hasWritableSheet(minecraft, sheetScreen)) return;
            prepareIfBlank(sheetScreen);
            minecraft.gui.setScreen(MidiChannelImportScreen.forSheet(sheetScreen, analysis));
        }));
        return true;
    }

    static void prepareIfBlank(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && ItemMusicSheet.isEmptySheet(minecraft.player.getMainHandItem())
                && screen instanceof SheetImportScreenAccess access) {
            access.m3e$prepareForReplacementImport();
        }
    }

    private static void beginReplacementImport(Screen screen) {
        if (screen instanceof SheetImportScreenAccess access) {
            access.m3e$beginReplacementImport();
        }
    }

    static boolean hasWritableSheet(Minecraft minecraft) {
        return minecraft.player != null
                && minecraft.player.getMainHandItem().getItem() instanceof ItemMusicSheet
                && (minecraft.player.isCreative()
                || ItemMusicSheet.isEmptySheet(minecraft.player.getMainHandItem()));
    }

    static boolean hasWritableSheet(Minecraft minecraft, Screen screen) {
        return screen instanceof PianoRollScreen pianoRoll
                ? pianoRoll.canAcceptImport() : hasWritableSheet(minecraft);
    }

    private static boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mid") || name.endsWith(".midi")
                || name.endsWith(".abc") || name.endsWith(".sheet");
    }

    private static final class MidiReadException extends RuntimeException {
        private MidiReadException(Throwable cause) { super(cause); }
    }
}
