package m3e.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class SmartPlayPanel {
    private static final int HEIGHT = InstrumentPanelGeometry.BUTTON_HEIGHT;
    private static final int GAP = InstrumentPanelGeometry.GAP;
    private static final int HEADING_HEIGHT = 10;

    private final Font font;
    private final Consumer<AbstractWidget> add;
    private final Runnable rerender;

    SmartPlayPanel(Font font, Consumer<AbstractWidget> add, Runnable rerender) {
        this.font = font;
        this.add = add;
        this.rerender = rerender;
    }

    void render(int x, int y, int width, int contentBottom) {
        List<ButtonSpec> scale = scaleButtons();
        List<ButtonSpec> chord = chordButtons();
        int sectionGap = InstrumentPanelGeometry.SECTION_GAP;
        int sectionInset = InstrumentPanelGeometry.SECTION_INSET;
        int leftWidth = Math.max(1, (width - sectionGap) / 2);
        int rightX = x + leftWidth + sectionGap;
        int rightWidth = Math.max(1, x + width - rightX);
        int sectionHeight = Math.max(1, contentBottom - y);
        add.accept(new PianoInsetPanel(x, y, leftWidth, sectionHeight,
                "Scale assist"));
        add.accept(new PianoInsetPanel(rightX, y, rightWidth, sectionHeight,
                "Chord assist"));
        addButtonColumn(x + sectionInset, y + sectionInset,
                Math.max(1, leftWidth - sectionInset * 2), "Scale Assist", scale);
        addButtonColumn(rightX + sectionInset, y + sectionInset,
                Math.max(1, rightWidth - sectionInset * 2), "Chord Assist", chord);
    }

    private List<ButtonSpec> scaleButtons() {
        SmartPlaySettings settings = InstrumentOptions.smartPlay();
        SmartPlaySettings.Scale value = settings.scale();
        boolean chordSet = settings.chord().mode() == SmartPlaySettings.ChordMode.CHORD_SET;
        SmartPlayScaleCatalog.ScaleDefinition definition = SmartPlayScaleCatalog.scale(value);
        List<ButtonSpec> buttons = new ArrayList<>();
        buttons.add(new ButtonSpec("Scale: " + onOff(value.enabled()), true,
                () -> updateScale(value.withEnabled(!value.enabled()))));
        buttons.add(new ButtonSpec("Root: " + noteName(value.root()), true,
                () -> updateScale(value.withRoot(value.root() + 1))));
        buttons.add(new ButtonSpec("Bank: " + value.bank().label(), !chordSet,
                () -> updateScale(value.withBank(value.bank().next()).withType(0))));
        buttons.add(new ButtonSpec("Type: " + definition.label(), !chordSet,
                () -> updateScale(value.withType((value.type() + 1) % 15))));
        buttons.add(new ButtonSpec("Keys: " + value.keyMode().label(), !chordSet,
                () -> updateScale(value.withKeyMode(value.keyMode().next()))));
        buttons.add(new ButtonSpec("Reset Scale", true,
                () -> updateScale(SmartPlaySettings.Scale.DEFAULT)));
        return buttons;
    }

    private List<ButtonSpec> chordButtons() {
        SmartPlaySettings settings = InstrumentOptions.smartPlay();
        SmartPlaySettings.Chord value = settings.chord();
        List<ButtonSpec> buttons = new ArrayList<>();
        buttons.add(new ButtonSpec("Mode: " + value.mode().label(), true,
                () -> updateChord(value.withMode(value.mode().next()))));
        if (value.mode() == SmartPlaySettings.ChordMode.HARMONIZER) {
            boolean chromatic = SmartPlayScaleCatalog.scale(settings.scale()).chromatic();
            buttons.add(chromatic
                    ? new ButtonSpec("Type: " + value.chromaticHarmony().label(), true,
                    () -> updateChord(value.withChromatic(value.chromaticHarmony().next())))
                    : new ButtonSpec("Type: " + value.diatonicHarmony().label(), true,
                    () -> updateChord(value.withDiatonic(value.diatonicHarmony().next()))));
        } else if (value.mode() == SmartPlaySettings.ChordMode.CHORD_SET) {
            buttons.add(new ButtonSpec("Set: " + value.chordSet().label(), true,
                    () -> updateChord(value.withChordSet(value.chordSet().next()))));
        } else {
            buttons.add(new ButtonSpec("Type: —", false, () -> { }));
        }
        buttons.add(new ButtonSpec("Position: " + positionLabel(value.position()),
                value.mode() != SmartPlaySettings.ChordMode.OFF,
                () -> updateChord(value.withPosition(nextPosition(value.position())))));
        buttons.add(new ButtonSpec("Reset Chord", true,
                () -> updateChord(SmartPlaySettings.Chord.DEFAULT)));
        return buttons;
    }

    private void addButtonColumn(int x, int y, int width, String heading,
                                 List<ButtonSpec> buttons) {
        add.accept(new CenteredStringWidget(x, y, width, HEADING_HEIGHT,
                font, Component.literal(heading)));
        int buttonY = y + HEADING_HEIGHT + 2;
        for (ButtonSpec spec : buttons) {
            Button button = Button.builder(Component.literal(spec.label()),
                            ignored -> spec.action().run())
                    .bounds(x, buttonY, width, HEIGHT).build();
            button.active = spec.active();
            add.accept(button);
            buttonY += HEIGHT + GAP;
        }
    }

    private void updateScale(SmartPlaySettings.Scale value) {
        InstrumentOptions.setSmartPlay(InstrumentOptions.smartPlay().withScale(value));
        rerender.run();
    }

    private void updateChord(SmartPlaySettings.Chord value) {
        InstrumentOptions.setSmartPlay(InstrumentOptions.smartPlay().withChord(value));
        rerender.run();
    }

    private String noteName(int pitchClass) {
        return m3e.session.NoteNames.pitchClass(
                pitchClass, InstrumentOptions.noteSpelling());
    }

    private static String onOff(boolean value) { return value ? "On" : "Off"; }

    private static String positionLabel(int value) {
        if (value == SmartPlaySettings.AUTO_POSITION) return "Auto";
        if (value == 0) return "Root";
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static int nextPosition(int value) {
        if (value == SmartPlaySettings.AUTO_POSITION) return -8;
        return value == 8 ? SmartPlaySettings.AUTO_POSITION : value + 1;
    }

    private record ButtonSpec(String label, boolean active, Runnable action) { }
}
