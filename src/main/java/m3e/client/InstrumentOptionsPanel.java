package m3e.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.Objects;
import m3e.instrument.M3EInstruments;
import m3e.instrument.InstrumentVariations;

/** Reusable keyboard and sound-profile options for instrument screens. */
final class InstrumentOptionsPanel {
    private static final int HEIGHT = InstrumentPanelGeometry.BUTTON_HEIGHT;
    private static final int GAP = InstrumentPanelGeometry.GAP;
    private final Minecraft client;
    private final Screen screen;
    private final Consumer<AbstractWidget> add;
    private final Runnable rerender;
    private final Runnable pianoLayoutChanged;
    private final InstrumentControlTarget target;
    private final DexedSimplePanel dexedPanel;
    private final OverlayDropdown.Controller variationDropdown;

    InstrumentOptionsPanel(Minecraft client, Screen screen,
                           Consumer<AbstractWidget> add, Runnable rerender,
                           Runnable pianoLayoutChanged) {
        this(client, screen, add, rerender, pianoLayoutChanged,
                new HeldInstrumentControlTarget(() ->
                        screen instanceof InstrumentMidiScreenAccess access
                                ? access.instrumentId() : -1,
                        () -> screen instanceof InstrumentMidiScreenAccess access
                                ? access.heldInstrumentEditSession() : null));
    }

    InstrumentOptionsPanel(Minecraft client, Screen screen,
                           Consumer<AbstractWidget> add, Runnable rerender,
                           Runnable pianoLayoutChanged, InstrumentControlTarget target) {
        this.client = client;
        this.screen = screen;
        this.add = add;
        this.rerender = rerender;
        this.pianoLayoutChanged = pianoLayoutChanged;
        this.target = Objects.requireNonNull(target, "instrument target");
        this.dexedPanel = new DexedSimplePanel(client, screen, add, rerender,
                this.target::dexed);
        this.variationDropdown = new OverlayDropdown.Controller(rerender);
    }

    void render(int x, int y, int width, int id) {
        y = addVelocity(x, y, width);
        if (InstrumentRangeOverride.supportsExtension(id)) {
            y = addFullKeyboardRange(x, y, width);
        }
        y = addKeyboardLayout(x, y, width);
        y = addPianoRange(x, y, width);
        y = addNoteNames(x, y, width);
        y = addOctaveOrigin(x, y, width);
        y = addPitchBendRange(x, y, width);
        y = addSmartPlay(x, y, width);
        if (id != M3EInstruments.dexed().getInstrumentId()) {
            addInstrumentProfile(x, y, width, id);
        }
    }

    void renderKeyboard(int x, int y, int width, int bottom) {
        FormFrame frame = formFrame(x, y, width, bottom, "Keyboard controls");
        int row = addVelocity(frame.x(), frame.y(), frame.width());
        if (InstrumentRangeOverride.supportsExtension(target.instrumentId())) {
            row = addFullKeyboardRange(frame.x(), row, frame.width());
        }
        row = addKeyboardLayout(frame.x(), row, frame.width());
        row = addOctaveOrigin(frame.x(), row, frame.width());
        addPitchBendRange(frame.x(), row, frame.width());
    }

    void renderDisplay(int x, int y, int width, int bottom) {
        FormFrame frame = formFrame(x, y, width, bottom, "Display controls");
        int row = addNoteNames(frame.x(), frame.y(), frame.width());
        row = addPianoRange(frame.x(), row, frame.width());
        row = PianoColorEditor.add(screen, client.font, frame.x(), row, frame.width(),
                HEIGHT, GAP, add, "Pressed Notes", "Pressed-note color",
                InstrumentOptions::pressedKeyColor, InstrumentOptions::setPressedKeyColor,
                InstrumentOptions::resetPressedKeyColor, rerender);
        row = PianoColorEditor.add(screen, client.font, frame.x(), row, frame.width(),
                HEIGHT, GAP, add, "Playable Notes", "Playable-note color",
                InstrumentOptions::playableKeyColor, InstrumentOptions::setPlayableKeyColor,
                InstrumentOptions::resetPlayableKeyColor, rerender);
        row = addPianoWaterfall(frame.x(), row, frame.width());
        row = PianoColorEditor.add(screen, client.font, frame.x(), row, frame.width(),
                HEIGHT, GAP, add, "Waterfall", "Waterfall-note color",
                InstrumentOptions::waterfallColor, InstrumentOptions::setWaterfallColor,
                InstrumentOptions::resetWaterfallColor, rerender);
        PianoColorEditor.add(screen, client.font, frame.x(), row, frame.width(),
                HEIGHT, GAP, add, "Outline", "Waterfall-outline color",
                InstrumentOptions::waterfallOutlineColor,
                InstrumentOptions::setWaterfallOutlineColor,
                InstrumentOptions::resetWaterfallOutlineColor, rerender);
    }

    void renderInstrument(int x, int y, int width, int bottom) {
        int id = target.instrumentId();
        if (id == M3EInstruments.dexed().getInstrumentId()) {
            dexedPanel.render(x, y, width, bottom);
            return;
        }
        FormFrame frame = formFrame(x, y, width, bottom, "Instrument controls");
        variationDropdown.beginFrame(frame.y(), bottom);
        x = frame.x();
        y = frame.y();
        width = frame.width();
        y = addVariation(x, y, width, id);
        addInstrumentProfile(x, y, width, id);
        variationDropdown.addOverlay(add);
    }

    void mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (variationDropdown.mouseScrolled(mouseX, mouseY, vertical)) return;
        dexedPanel.mouseScrolled(mouseX, mouseY, vertical);
    }

    void mouseClicked(MouseButtonEvent event) { dexedPanel.mouseClicked(event); }
    void mouseDragged(MouseButtonEvent event) { dexedPanel.mouseDragged(event); }
    void mouseReleased(MouseButtonEvent event) { dexedPanel.mouseReleased(event); }

    boolean needsRerender() {
        return dexedPanel.needsRerender();
    }

    private FormFrame formFrame(int x, int y, int width, int bottom,
                                String narration) {
        int sectionWidth = width;
        int sectionX = x;
        add.accept(new PianoInsetPanel(sectionX, y, sectionWidth,
                Math.max(1, bottom - y), narration));
        int available = Math.max(1,
                sectionWidth - InstrumentPanelGeometry.SECTION_INSET * 2);
        int controlsWidth = InstrumentPanelGeometry.boundedWidth(available,
                InstrumentPanelGeometry.MAX_BUTTON_WIDTH);
        int controlsX = InstrumentPanelGeometry.centeredX(
                sectionX + InstrumentPanelGeometry.SECTION_INSET,
                available, controlsWidth);
        return new FormFrame(controlsX,
                y + InstrumentPanelGeometry.SECTION_INSET, controlsWidth);
    }

    private record FormFrame(int x, int y, int width) { }

    private int addVelocity(int x, int y, int width) {
        int velocityFieldWidth = 44;
        add.accept(new StringWidget(x, y + 4, width - velocityFieldWidth - GAP, 10,
                Component.literal("Keyboard Velocity"), client.font));
        M3EEditBox velocity = new M3EEditBox(screen, client.font,
                x + width - velocityFieldWidth, y, velocityFieldWidth, HEIGHT,
                Component.literal("Keyboard Velocity"));
        velocity.setMaxLength(3);
        velocity.setCentered(true);
        velocity.setValue(Integer.toString(InstrumentPerformanceControls.keyboardVelocity()));
        velocity.setResponder(text -> velocity.setTextColor(
                InstrumentPerformanceControls.acceptVelocityText(text)
                        ? 0xFFE0E0E0 : 0xFFFF5555));
        add.accept(velocity);
        return y + HEIGHT + GAP;
    }

    private int addKeyboardLayout(int x, int y, int width) {
        add.accept(Button.builder(Component.literal("Keyboard Layout: "
                        + InstrumentOptions.keyboardLayout().label()), ignored -> {
                    InstrumentOptions.setKeyboardLayout(
                            InstrumentOptions.nextKeyboardLayout());
                    rerender.run();
                })
                .bounds(x, y, width, HEIGHT).build());
        return y + HEIGHT + GAP;
    }

    private int addPianoRange(int x, int y, int width) {
        add.accept(Button.builder(Component.literal(InstrumentOptions.compactInstrumentUi()
                        ? "Piano Range: 61 Keys (C2-C7)"
                        : "Piano Range: 88 Keys (A0-C8)"), ignored -> {
                    InstrumentOptions.setCompactInstrumentUi(
                            !InstrumentOptions.compactInstrumentUi());
                    pianoLayoutChanged.run();
                }).bounds(x, y, width, HEIGHT).build());
        return y + HEIGHT + GAP;
    }

    private int addFullKeyboardRange(int x, int y, int width) {
        add.accept(Button.builder(Component.literal("Full Keyboard Range: "
                        + (InstrumentOptions.fullKeyboardRange() ? "On" : "Off")), ignored -> {
                    InstrumentOptions.setFullKeyboardRange(!InstrumentOptions.fullKeyboardRange());
                    pianoLayoutChanged.run();
                })
                .bounds(x, y, width, HEIGHT).build());
        return y + HEIGHT + GAP;
    }

    private int addNoteNames(int x, int y, int width) {
        add.accept(Button.builder(Component.literal("Note Names: "
                        + InstrumentOptions.noteSpelling().label()), ignored -> {
                    InstrumentOptions.setNoteSpelling(InstrumentOptions.noteSpelling().next());
                    rerender.run();
                }).bounds(x, y, width, HEIGHT).build());
        return y + HEIGHT + GAP;
    }

    private int addPianoWaterfall(int x, int y, int width) {
        add.accept(Button.builder(Component.literal("Piano Waterfall: "
                        + (InstrumentOptions.pianoWaterfallEnabled() ? "On" : "Off")), ignored -> {
                    InstrumentOptions.setPianoWaterfallEnabled(
                            !InstrumentOptions.pianoWaterfallEnabled());
                    rerender.run();
                }).bounds(x, y, width, HEIGHT).build());
        return y + HEIGHT + GAP;
    }

    private int addOctaveOrigin(int x, int y, int width) {
        OctaveOriginSlider octaveOrigin = new OctaveOriginSlider(x, y, width);
        octaveOrigin.active = InstrumentOptions.keyboardLayout()
                != InstrumentOptions.KeyboardLayout.VISUAL_PIANO;
        add.accept(octaveOrigin);
        return y + HEIGHT + GAP;
    }

    private int addPitchBendRange(int x, int y, int width) {
        add.accept(new PitchBendRangeSlider(x, y, width));
        return y + HEIGHT + GAP;
    }

    private int addSmartPlay(int x, int y, int width) {
        SmartPlaySettings settings = InstrumentOptions.smartPlay();
        String state = settings.chord().mode() != SmartPlaySettings.ChordMode.OFF
                ? settings.chord().mode().label()
                : settings.scale().enabled() ? SmartPlayScaleCatalog.scale(
                        settings.scale()).label() : "Off";
        add.accept(Button.builder(Component.literal("Smart Play: " + state + " ▸"), ignored ->
                        client.gui.setScreen(new SmartPlayScreen(screen)))
                .bounds(x, y, width, HEIGHT).build());
        return y + HEIGHT + GAP;
    }

    private int addVariation(int x, int y, int width, int instrumentId) {
        String selected = target.variation();
        java.util.List<InstrumentVariations.Variation> available =
                InstrumentVariations.available(instrumentId);
        java.util.List<OverlayDropdown.Choice<String>> choices = available.stream()
                .map(variation -> new OverlayDropdown.Choice<>(
                        variation.id(), variation.label())).toList();
        int selectedIndex = 0;
        for (int index = 0; index < available.size(); index++) {
            if (available.get(index).id().equals(selected)) selectedIndex = index;
        }
        add.accept(variationDropdown.button("variation:" + instrumentId,
                x, y, width, HEIGHT,
                "Variation: " + InstrumentVariations.label(instrumentId, selected),
                choices, selectedIndex, 7, id -> {
                    target.setVariation(id);
                    rerender.run();
                }));
        return y + HEIGHT + GAP;
    }

    private void addInstrumentProfile(int x, int y, int width, int id) {
        InstrumentProfileEditor.add(screen, client.font, x, y, width, HEIGHT, GAP,
                    48, 42, add,
                InstrumentProfileEditor.binding(
                        target::profile, target::setProfile),
                    rerender);
    }

    private final class OctaveOriginSlider extends AbstractSliderButton {
        private OctaveOriginSlider(int x, int y, int width) {
            super(x, y, width, HEIGHT, Component.empty(),
                    InstrumentOptions.octaveOrigin() / 11.0D);
            updateMessage();
        }

        @Override protected void updateMessage() {
            int pitchClass = (int) Math.round(value * 11.0D);
            setMessage(Component.literal("Octave Start: "
                    + new String[]{"C", "C#", "D", "D#", "E", "F",
                    "F#", "G", "G#", "A", "A#", "B"}[pitchClass]));
        }

        @Override protected void applyValue() {
            int pitchClass = (int) Math.round(value * 11.0D);
            value = pitchClass / 11.0D;
            InstrumentOptions.setOctaveOrigin(pitchClass);
            updateMessage();
        }
    }

    private static final class PitchBendRangeSlider extends AbstractSliderButton {
        private PitchBendRangeSlider(int x, int y, int width) {
            super(x, y, width, HEIGHT, Component.empty(),
                    (InstrumentOptions.pitchBendRangeSemitones() - 1) / 11.0D);
            updateMessage();
        }

        @Override protected void updateMessage() {
            int range = 1 + (int) Math.round(value * 11.0D);
            setMessage(Component.literal("Pitch Bend Range: +/-" + range + " st"));
        }

        @Override protected void applyValue() {
            int range = 1 + (int) Math.round(value * 11.0D);
            value = (range - 1) / 11.0D;
            InstrumentOptions.setPitchBendRangeSemitones(range);
            updateMessage();
        }
    }

}
