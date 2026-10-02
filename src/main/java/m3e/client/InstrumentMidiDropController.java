package m3e.client;

import m3e.M3E;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Non-blocking drag-and-drop entry point for Music Maker instrument screens. */
public final class InstrumentMidiDropController {
    private InstrumentMidiDropController() { }

    public static boolean handle(Screen instrumentScreen, List<Path> paths) {
        if (!(instrumentScreen instanceof InstrumentMidiScreenAccess access)) return false;
        Path score = paths.stream().filter(InstrumentMidiDropController::supported).findFirst().orElse(null);
        if (score == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (!SongbookClientState.hasBooks()) {
            if (minecraft.player != null) minecraft.player.sendSystemMessage(
                    Component.literal("Carry a Songbook before importing music.").withStyle(ChatFormatting.RED));
            return true;
        }
        SongbookClientState.ImportTarget target = SongbookClientState.activeImportTarget();
        if (target == null) {
            if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(
                    "Wait for the selected Songbook to finish loading before importing music.")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        if (SongbookFileImporter.isFull(target)) return true;
        long generation = AsyncMusicImport.begin();
        String lower = score.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".sheet")) {
            SongbookFileImporter.importPath(instrumentScreen, score);
            return true;
        }
        if (lower.endsWith(".abc")) {
            AbcImportController.start(score, instrumentScreen,
                    ScoreImportDestination.instrumentSongbook(instrumentScreen, access, target),
                    () -> minecraft.player != null
                            && target.equals(SongbookClientState.activeImportTarget()), generation);
            return true;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return MidiSheetFile.read(score);
            } catch (Exception exception) {
                throw new MidiReadException(exception);
            }
        }).whenComplete((analysis, error) -> minecraft.execute(() -> {
            if (!AsyncMusicImport.isCurrent(generation)) return;
            if (error != null) {
                Throwable cause = error.getCause() instanceof MidiReadException wrapped
                        ? wrapped.getCause() : error;
                reportFailure(score, cause);
                return;
            }
            if (minecraft.gui.screen() != instrumentScreen || minecraft.player == null
                    || !target.equals(SongbookClientState.activeImportTarget())) return;
            minecraft.gui.setScreen(MidiChannelImportScreen.forInstrumentSongbook(
                    instrumentScreen, analysis, access, target));
        }));
        return true;
    }

    private static boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mid") || name.endsWith(".midi")
                || name.endsWith(".abc") || name.endsWith(".sheet");
    }

    private static void reportFailure(Path path, Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        if (message.length() > 180) message = message.substring(0, 180);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("MIDI import failed: " + message)
                    .withStyle(ChatFormatting.RED));
        }
        M3E.LOGGER.warn("Could not inspect dropped instrument MIDI {}", path, failure);
    }

    private static final class MidiReadException extends RuntimeException {
        private MidiReadException(Throwable cause) {
            super(cause);
        }
    }
}
