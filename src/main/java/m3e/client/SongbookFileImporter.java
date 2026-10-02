package m3e.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import m3e.M3E;
import m3e.sheet.SheetFileIO;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** File-picker and drag/drop imports owned by a carried server-backed Songbook. */
final class SongbookFileImporter {
    private static final AtomicBoolean PICKER_OPEN = new AtomicBoolean();

    private SongbookFileImporter() { }

    static boolean handle(Screen parent, List<Path> paths) {
        return handle(parent, paths, null);
    }

    static boolean handle(Screen parent, List<Path> paths, UUID managedBookId) {
        Path supported = paths.stream().filter(SongbookFileImporter::supported).findFirst().orElse(null);
        if (supported == null) return false;
        if (!SongbookClientState.hasBooks()) {
            report("Carry a Songbook before importing music.");
            return true;
        }
        importPath(parent, supported, managedBookId);
        return true;
    }

    static void chooseFile(Screen parent) {
        chooseFile(parent, null);
    }

    static void chooseFile(Screen parent, UUID managedBookId) {
        SongbookClientState.ImportTarget target = importTarget(managedBookId);
        if (target == null) {
            report("Wait for the selected Songbook to finish loading before importing music.");
            return;
        }
        if (isFull(target)) return;
        if (!PICKER_OPEN.compareAndSet(false, true)) return;
        try {
            Thread.ofPlatform().daemon().name("Songbook file chooser").start(() -> {
                try {
                    String selected;
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        PointerBuffer filters = stack.mallocPointer(4);
                        filters.put(stack.UTF8("*.mid"));
                        filters.put(stack.UTF8("*.midi"));
                        filters.put(stack.UTF8("*.abc"));
                        filters.put(stack.UTF8("*.sheet"));
                        filters.flip();
                        selected = TinyFileDialogs.tinyfd_openFileDialog(
                                "Import into Songbook", null, filters,
                                "ABC, MIDI, or Music Maker sheet", false);
                    }
                    if (selected != null && !selected.isBlank()) {
                        Minecraft.getInstance().execute(() ->
                                importPath(parent, Path.of(selected), managedBookId));
                    }
                } catch (Throwable failure) {
                    Minecraft.getInstance().execute(() -> reportPickerFailure(failure));
                } finally {
                    PICKER_OPEN.set(false);
                }
            });
        } catch (Throwable failure) {
            PICKER_OPEN.set(false);
            reportPickerFailure(failure);
        }
    }

    static void importPath(Screen parent, Path path) {
        importPath(parent, path, null);
    }

    static void importPath(Screen parent, Path path, UUID managedBookId) {
        SongbookClientState.ImportTarget target = importTarget(managedBookId);
        if (target == null) {
            report("Wait for the selected Songbook to finish loading before importing music.");
            return;
        }
        if (isFull(target)) return;
        long generation = AsyncMusicImport.begin();
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".sheet")) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    return SheetFileIO.readBytes(path);
                } catch (Exception exception) {
                    throw new ReadFailure(exception);
                }
            }).whenComplete((bytes, error) -> Minecraft.getInstance().execute(() -> {
                if (!stillCurrent(parent, managedBookId, target, generation)) return;
                if (error != null) {
                    reportFailure("Sheet import failed", path, error);
                    return;
                }
                SongbookClientState.importSheet(target, bytes, path.getFileName().toString());
            }));
            return;
        }
        if (lower.endsWith(".abc")) {
            ScoreImportDestination destination = ScoreImportDestination.songbook(
                    parent, target, managedBookId != null);
            AbcImportController.start(path, parent, destination,
                    () -> target.equals(importTarget(managedBookId)), generation);
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return MidiSheetFile.read(path);
            } catch (Exception exception) {
                throw new ReadFailure(exception);
            }
        }).whenComplete((analysis, error) -> Minecraft.getInstance().execute(() -> {
            if (!stillCurrent(parent, managedBookId, target, generation)) return;
            if (error != null) {
                reportFailure("MIDI import failed", path, error);
                return;
            }
            Minecraft.getInstance().gui.setScreen(
                    MidiChannelImportScreen.forSongbook(parent, analysis, target,
                            managedBookId != null));
        }));
    }

    static void cancelPending() {
        AsyncMusicImport.cancel();
    }

    private static boolean stillCurrent(Screen parent, UUID managedBookId,
                                        SongbookClientState.ImportTarget target,
                                        long generation) {
        if (!AsyncMusicImport.isCurrent(generation)
                || Minecraft.getInstance().gui.screen() != parent) return false;
        SongbookClientState.ImportTarget current = managedBookId == null
                ? SongbookClientState.activeImportTarget()
                : SongbookClientState.managedImportTarget();
        return target.equals(current);
    }

    private static SongbookClientState.ImportTarget importTarget(UUID managedBookId) {
        return managedBookId == null
                ? SongbookClientState.activeImportTarget()
                : SongbookClientState.managedImportTarget();
    }

    static boolean isFull(SongbookClientState.ImportTarget target) {
        return target != null && target.entryCount() >= target.capacity();
    }

    private static boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mid") || name.endsWith(".midi")
                || name.endsWith(".abc") || name.endsWith(".sheet");
    }

    private static void reportFailure(String prefix, Path path, Throwable failure) {
        Throwable cause = failure.getCause() instanceof ReadFailure wrapped
                ? wrapped.getCause() : failure;
        String message = cause.getMessage();
        if (message == null || message.isBlank()) message = cause.getClass().getSimpleName();
        if (message.length() > 180) message = message.substring(0, 180);
        report(prefix + ": " + message);
        M3E.LOGGER.warn("Could not import Songbook file {}", path, cause);
    }

    private static void reportPickerFailure(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        if (message.length() > 180) message = message.substring(0, 180);
        report("Could not open music file picker: " + message);
        M3E.LOGGER.warn("Could not open Songbook file picker", failure);
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
