package m3e.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import m3e.M3E;

import java.nio.file.Files;
import java.nio.file.Path;

/** Local import/export for the versioned, human-readable keyboard preset. */
final class KeyboardKeymapFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private KeyboardKeymapFiles() { }

    static void chooseImport(Screen parent) {
        Thread.ofVirtual().name("Keyboard keymap import").start(() -> {
            String selected;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(2);
                filters.put(stack.UTF8("*.json"));
                filters.put(stack.UTF8("*.txt"));
                filters.flip();
                selected = TinyFileDialogs.tinyfd_openFileDialog(
                        "Import Music Maker keymap", null, filters,
                        "Keymap JSON or text", false);
            }
            if (selected == null || selected.isBlank()) return;
            Path path = Path.of(selected);
            try {
                KeyboardCustomPreset preset = KeyboardCustomPreset.fromJson(
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject());
                Minecraft.getInstance().execute(() -> {
                    InstrumentOptions.setCustomKeyboardPreset(preset);
                    InstrumentOptions.setKeyboardLayout(InstrumentOptions.KeyboardLayout.CUSTOM);
                    report("Imported Custom keyboard preset from " + path.getFileName(), false);
                    Minecraft.getInstance().gui.setScreen(new KeyboardKeymapScreen(parent));
                });
            } catch (Exception exception) {
                M3E.LOGGER.warn("Could not import keyboard keymap {}", path, exception);
                Minecraft.getInstance().execute(() -> report(
                        "Keymap import failed: " + concise(exception), true));
            }
        });
    }

    static void chooseExport() {
        Thread.ofVirtual().name("Keyboard keymap export").start(() -> {
            String selected;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(2);
                filters.put(stack.UTF8("*.json"));
                filters.put(stack.UTF8("*.txt"));
                filters.flip();
                selected = TinyFileDialogs.tinyfd_saveFileDialog(
                        "Export Music Maker keymap", "music-maker-keymap.json",
                        filters, "Keymap JSON or text");
            }
            if (selected == null || selected.isBlank()) return;
            Path path = Path.of(selected);
            if (!hasSupportedExtension(path)) {
                path = path.resolveSibling(path.getFileName() + ".json");
            }
            try {
                Files.writeString(path, GSON.toJson(
                        InstrumentOptions.customKeyboardPreset().toJson()));
                Path saved = path;
                Minecraft.getInstance().execute(() -> report(
                        "Exported Custom keyboard preset to " + saved.getFileName(), false));
            } catch (Exception exception) {
                M3E.LOGGER.warn("Could not export keyboard keymap {}", path, exception);
                Minecraft.getInstance().execute(() -> report(
                        "Keymap export failed: " + concise(exception), true));
            }
        });
    }

    private static boolean hasSupportedExtension(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) return false;
        String name = fileName.toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".json") || name.endsWith(".txt");
    }

    private static String concise(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    private static void report(String message, boolean error) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message)
                    .withStyle(error ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
    }
}
