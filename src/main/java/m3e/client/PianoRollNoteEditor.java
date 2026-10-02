package m3e.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Modal note properties within the editor, preserving its playback and viewport. */
final class PianoRollNoteEditor {
    record Values(int pitch, int velocity, long start, long length) { }
    final long noteId;
    private final ScoreEditSession.Note note;
    private final Predicate<Values> apply;
    private final Runnable close;
    private int x, y, width, height;
    private EditBox pitch, velocity, start, length;
    private String error = "";

    PianoRollNoteEditor(ScoreEditSession.Note note, Predicate<Values> apply, Runnable close) {
        this.note = note; noteId = note.id(); this.apply = apply; this.close = close;
    }

    EditBox addWidgets(Screen owner, Font font, Consumer<AbstractWidget> add, int screenWidth, int screenHeight) {
        width = 230; height = 159; x = (screenWidth - width) / 2; y = (screenHeight - height) / 2;
        pitch = field(owner, font, add, y + 23, Integer.toString(note.pitch()), "Pitch");
        velocity = field(owner, font, add, y + 46, Integer.toString(note.velocity()), "Velocity");
        start = field(owner, font, add, y + 69, Long.toString(note.start()), "Start tick");
        length = field(owner, font, add, y + 92, Long.toString(note.length()), "Length ticks");
        add.accept(Button.builder(Component.literal("Apply"), ignored -> submit()).bounds(x + 80, y + 131, 65, 20).build());
        add.accept(Button.builder(Component.literal("Cancel"), ignored -> close.run()).bounds(x + 151, y + 131, 65, 20).build());
        return pitch;
    }

    private EditBox field(Screen owner, Font font, Consumer<AbstractWidget> add, int top, String value, String label) {
        EditBox box = new M3EEditBox(owner, font, x + 80, top, width - 94, 20, Component.literal(label));
        box.setMaxLength(18); box.setValue(value); add.accept(box); return box;
    }

    void submit() {
        try {
            Values values = new Values(Integer.parseInt(pitch.getValue().strip()), Integer.parseInt(velocity.getValue().strip()),
                    Long.parseLong(start.getValue().strip()), Long.parseLong(length.getValue().strip()));
            if (apply.test(values)) close.run(); else error = "Value outside supported range.";
        } catch (NumberFormatException ignored) { error = "Enter whole numbers."; }
    }

    boolean contains(double mouseX, double mouseY) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }

    void draw(GuiGraphicsExtractor graphics, Font font, int screenWidth, int screenHeight) {
        graphics.fill(0, 0, screenWidth, screenHeight, 0xA0000000);
        PianoKeyboardRenderer.drawFourPixelPanel(graphics, x, y, width, height);
        graphics.text(font, Component.literal("Note properties"), x + 12, y + 9, 0xFFE6E6E6, false);
        String[] labels = {"Pitch", "Velocity", "Start tick", "Length ticks"};
        for (int i = 0; i < labels.length; i++) graphics.text(font, Component.literal(labels[i]), x + 12, y + 29 + i * 23, 0xFFE6E6E6, false);
        if (!error.isEmpty()) graphics.text(font, Component.literal(error), x + 8, y + 117, 0xFFFFAA88, false);
    }
}
