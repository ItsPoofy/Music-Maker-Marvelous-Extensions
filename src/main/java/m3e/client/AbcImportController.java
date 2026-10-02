package m3e.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import m3e.M3E;
import m3e.sheet.MusicScore;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Asynchronous, last-action-wins coordinator shared by every ABC import destination. */
final class AbcImportController {
    private AbcImportController() { }

    static void start(Path path, Screen parent, ScoreImportDestination destination,
                      BooleanSupplier destinationGuard, long generation) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return AbcFile.read(path);
            } catch (Exception exception) {
                throw new ReadFailure(exception);
            }
        }).whenComplete((document, failure) -> Minecraft.getInstance().execute(() -> {
            if (!current(parent, destination, destinationGuard, generation)) return;
            if (failure != null) {
                reportFailure("ABC import failed", path, failure);
                return;
            }
            if (destination.kind() == ScoreImportDestination.Kind.SHEET) {
                SheetDropController.prepareIfBlank(parent);
            }
            if (document.tunes().size() == 1) {
                parseTune(path, parent, document, document.tunes().getFirst(), destination,
                        destinationGuard, generation);
            } else {
                Minecraft.getInstance().gui.setScreen(new AbcTuneImportScreen(
                        path, document, destination, destinationGuard, generation));
            }
        }));
    }

    static void parseTune(Path path, Screen expectedScreen, AbcFile.AbcDocument document,
                          AbcFile.AbcTuneSummary tune, ScoreImportDestination destination,
                          BooleanSupplier destinationGuard, long generation) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return document.parse(tune);
            } catch (Exception exception) {
                throw new ReadFailure(exception);
            }
        }).whenComplete((analysis, failure) -> Minecraft.getInstance().execute(() -> {
            if (!current(expectedScreen, destination, destinationGuard, generation)) return;
            if (failure != null) {
                reportFailure("ABC tune could not be loaded", path, failure);
                if (expectedScreen instanceof AbcTuneImportScreen selector) selector.parseFailed();
                return;
            }
            routeAnalysis(analysis, destination);
        }));
    }

    private static void routeAnalysis(AbcFile.AbcTuneAnalysis analysis,
                                      ScoreImportDestination destination) {
        AbcScoreImportAnalysis adapted = new AbcScoreImportAnalysis(analysis);
        if (adapted.partCount() > 1) {
            Minecraft.getInstance().gui.setScreen(new ScorePartImportScreen(adapted, destination));
            return;
        }
        try {
            String name = adapted.defaultName(List.of(0), false, false, false);
            MusicScore song = adapted.song(List.of(0), List.of())
                    .transposeOctaves(InstrumentOptions.importOctaveOffset()).renamed(name);
            if (!destination.complete(song, name, List.of())) {
                report("The import target is no longer available.");
            }
        } catch (IllegalArgumentException exception) {
            report("ABC tune could not be imported: " + boundedMessage(exception));
        }
    }

    private static boolean current(Screen expectedScreen, ScoreImportDestination destination,
                                   BooleanSupplier destinationGuard, long generation) {
        return AsyncMusicImport.isCurrent(generation)
                && Minecraft.getInstance().gui.screen() == expectedScreen
                && destination.valid() && destinationGuard.getAsBoolean();
    }

    private static void reportFailure(String prefix, Path path, Throwable failure) {
        Throwable cause = failure.getCause() instanceof ReadFailure wrapped
                ? wrapped.getCause() : failure;
        report(prefix + ": " + boundedMessage(cause));
        M3E.LOGGER.warn("Could not import ABC file {}", path, cause);
    }

    private static String boundedMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        return message.substring(0, Math.min(180, message.length()));
    }

    private static void report(String text) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal(text).withStyle(ChatFormatting.RED));
        }
    }

    private static final class ReadFailure extends RuntimeException {
        private ReadFailure(Throwable cause) { super(cause); }
    }
}
