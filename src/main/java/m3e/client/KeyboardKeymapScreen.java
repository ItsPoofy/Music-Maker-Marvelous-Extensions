package m3e.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

/** Focus-to-capture editor for the local absolute-note keyboard preset. */
final class KeyboardKeymapScreen extends Screen {
    private static final int PANEL_WIDTH = 390;
    private static final int ROW_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int LAST_OCTAVE_PAGE = 8;
    private final Screen parent;
    private InstrumentOptions.KeyboardLayout selectedPreset;
    private int octavePage = 4;
    private boolean rebuilding;

    KeyboardKeymapScreen(Screen parent) {
        super(Component.literal("Keyboard Keymaps"));
        this.parent = parent;
        this.selectedPreset = InstrumentOptions.keyboardLayout();
    }

    @Override
    protected void init() {
        if (rebuilding) return;
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(260, width - 16));
        int left = (width - panelWidth) / 2;
        int y = 30;
        int half = (panelWidth - GAP) / 2;

        addRenderableWidget(Button.builder(Component.literal(
                        "Preset: " + selectedPreset.label()), ignored -> cyclePreset())
                .bounds(left, y, panelWidth, ROW_HEIGHT).build());
        y += ROW_HEIGHT + GAP;
        addRenderableWidget(Button.builder(Component.literal("◀"), ignored -> {
            octavePage = Math.floorMod(octavePage - 1, 9);
            refreshWidgets();
        }).bounds(left, y, 34, ROW_HEIGHT).build());
        Button page = Button.builder(Component.literal(pageLabel()), ignored -> { })
                .bounds(left + 38, y, panelWidth - 76, ROW_HEIGHT).build();
        page.active = false;
        addRenderableWidget(page);
        addRenderableWidget(Button.builder(Component.literal("▶"), ignored -> {
            octavePage = (octavePage + 1) % 9;
            refreshWidgets();
        }).bounds(left + panelWidth - 34, y, 34, ROW_HEIGHT).build());
        y += ROW_HEIGHT + GAP;

        int[] range = pageRange(octavePage);
        Map<Integer, KeyboardChord> displayed = displayedBindings();
        int count = range[1] - range[0];
        int rows = (count + 1) / 2;
        for (int index = 0; index < count; index++) {
            int column = index / rows;
            int row = index % rows;
            int cellX = left + column * (half + GAP);
            int cellY = y + row * (ROW_HEIGHT + GAP);
            int noteId = range[0] + index;
            int labelWidth = 38;
            addRenderableWidget(new StringWidget(cellX, cellY + 5, labelWidth, 10,
                    Component.literal(KeyboardNoteLayout.noteName(
                            noteId, InstrumentOptions.noteSpelling())), font));
            KeyCaptureBox field = new KeyCaptureBox(cellX + labelWidth, cellY,
                    half - labelWidth, noteId, null);
            field.show(displayed.get(noteId));
            field.allowCapture(selectedPreset == InstrumentOptions.KeyboardLayout.CUSTOM);
            field.active = selectedPreset == InstrumentOptions.KeyboardLayout.CUSTOM;
            addRenderableWidget(field);
        }
        y += rows * (ROW_HEIGHT + GAP) + 2;

        boolean octaveControls = selectedPreset == InstrumentOptions.KeyboardLayout.CUSTOM
                && !InstrumentOptions.customKeyboardPreset().coversEveryOctave(
                PianoKeyboardGeometry.STANDARD_FIRST_NOTE_ID,
                PianoKeyboardGeometry.STANDARD_LAST_NOTE_ID + 1);
        KeyCaptureBox down = new KeyCaptureBox(left, y, half, -1, OctaveAction.DOWN);
        down.show(octaveBinding(false));
        down.setHint(Component.literal(octaveControls ? "Octave down" : "Octave down disabled"));
        down.allowCapture(octaveControls);
        down.active = octaveControls;
        addRenderableWidget(down);
        KeyCaptureBox up = new KeyCaptureBox(left + half + GAP, y,
                panelWidth - half - GAP, -1, OctaveAction.UP);
        up.show(octaveBinding(true));
        up.setHint(Component.literal(octaveControls ? "Octave up" : "Octave up disabled"));
        up.allowCapture(octaveControls);
        up.active = octaveControls;
        addRenderableWidget(up);
        y += ROW_HEIGHT + GAP;

        addRenderableWidget(Button.builder(Component.literal("Reset Custom"), ignored -> {
            InstrumentOptions.setCustomKeyboardPreset(KeyboardCustomPreset.empty());
            selectedPreset = InstrumentOptions.KeyboardLayout.CUSTOM;
            InstrumentOptions.setKeyboardLayout(selectedPreset);
            refreshWidgets();
        }).bounds(left, y, half, ROW_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> onClose())
                .bounds(left + half + GAP, y, panelWidth - half - GAP, ROW_HEIGHT).build());
    }

    private void cyclePreset() {
        selectedPreset = selectedPreset.next();
        InstrumentOptions.setKeyboardLayout(selectedPreset);
        refreshWidgets();
    }

    private Map<Integer, KeyboardChord> displayedBindings() {
        if (selectedPreset == InstrumentOptions.KeyboardLayout.CUSTOM) {
            return InstrumentOptions.customKeyboardPreset().notes();
        }
        return KeyboardKeyMap.displayBindings(selectedPreset, octavePage - 1,
                InstrumentOptions.octaveOrigin(),
                PianoKeyboardGeometry.STANDARD_FIRST_NOTE_ID,
                PianoKeyboardGeometry.STANDARD_LAST_NOTE_ID + 1);
    }

    private KeyboardChord octaveBinding(boolean up) {
        return switch (selectedPreset) {
            case NORMAL -> KeyboardChord.of(up ? GLFW.GLFW_KEY_S : GLFW.GLFW_KEY_A, 0);
            case ALTERNATIVE, GENSHIN -> KeyboardChord.of(
                    up ? GLFW.GLFW_KEY_P : GLFW.GLFW_KEY_O, 0);
            case FULL -> KeyboardChord.of(up ? GLFW.GLFW_KEY_X : GLFW.GLFW_KEY_Z, 0);
            case VISUAL_PIANO -> null;
            case CUSTOM -> up ? InstrumentOptions.customKeyboardPreset().octaveUp()
                    : InstrumentOptions.customKeyboardPreset().octaveDown();
        };
    }

    private void refreshWidgets() {
        if (rebuilding) return;
        rebuilding = true;
        clearWidgets();
        rebuilding = false;
        init();
    }

    private String pageLabel() {
        int[] range = pageRange(octavePage);
        String first = KeyboardNoteLayout.noteName(range[0], InstrumentOptions.noteSpelling());
        String last = KeyboardNoteLayout.noteName(range[1] - 1, InstrumentOptions.noteSpelling());
        return "Octave " + first + (first.equals(last) ? "" : "–" + last);
    }

    private static int[] pageRange(int page) {
        if (page <= 0) return new int[]{0, 3};
        if (page >= LAST_OCTAVE_PAGE) return new int[]{87, 88};
        int first = 3 + (page - 1) * 12;
        return new int[]{first, first + 12};
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick) {
        graphics.fill(0, 0, width, height, 0xD0101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTicks) {
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(
                        "Focus a field, then press one key combination. Backspace clears it."),
                width / 2, height - 14, 0xFFB8B8B8);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    private enum OctaveAction { DOWN, UP }

    private final class KeyCaptureBox extends M3EEditBox {
        private final int noteId;
        private final OctaveAction octaveAction;
        private boolean captureEnabled;

        private KeyCaptureBox(int x, int y, int width, int noteId,
                              OctaveAction octaveAction) {
            super(KeyboardKeymapScreen.this, font, x, y, width, ROW_HEIGHT,
                    Component.literal("Key combination"));
            this.noteId = noteId;
            this.octaveAction = octaveAction;
            setCentered(true);
            setMaxLength(40);
        }

        private void show(KeyboardChord chord) {
            super.setValue(chord == null ? "" : chord.displayName());
        }

        private void allowCapture(boolean enabled) {
            captureEnabled = enabled;
            setEditable(enabled);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (M3EEditBox.isDismissKey(event.key())) return super.keyPressed(event);
            if (!captureEnabled) return false;
            if (KeyboardChord.isModifierKey(event.key())) return true;
            KeyboardChord chord = event.key() == GLFW.GLFW_KEY_BACKSPACE
                    || event.key() == GLFW.GLFW_KEY_DELETE
                    ? null : KeyboardChord.of(event.key(), event.modifiers());
            KeyboardCustomPreset preset = InstrumentOptions.customKeyboardPreset();
            if (octaveAction == OctaveAction.DOWN) preset = preset.withOctaveDown(chord);
            else if (octaveAction == OctaveAction.UP) preset = preset.withOctaveUp(chord);
            else preset = preset.withNote(noteId, chord);
            InstrumentOptions.setCustomKeyboardPreset(preset);
            InstrumentOptions.setKeyboardLayout(InstrumentOptions.KeyboardLayout.CUSTOM);
            selectedPreset = InstrumentOptions.KeyboardLayout.CUSTOM;
            refreshWidgets();
            return true;
        }
    }
}
