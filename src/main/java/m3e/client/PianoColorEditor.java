package m3e.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

final class PianoColorEditor {
    private PianoColorEditor() { }

    static int add(Screen screen, Font font, int x, int y, int width,
                   int rowHeight, int gap, Consumer<AbstractWidget> add,
                   String label, String narration, IntSupplier color,
                   IntConsumer update, Runnable reset, Runnable rebuild) {
        int resetWidth = 42;
        int swatchWidth = 26;
        int hexWidth = Math.min(72, Math.max(54,
                width - resetWidth - swatchWidth - gap * 3 - 82));
        int labelWidth = Math.max(56,
                width - resetWidth - swatchWidth - hexWidth - gap * 3);
        add.accept(new StringWidget(x, y + Math.max(0, (rowHeight - 10) / 2),
                labelWidth, 10, Component.literal(label), font));
        add.accept(new HexBox(screen, font, x + labelWidth + gap, y,
                hexWidth, rowHeight, narration, color, update));
        int swatchX = x + labelWidth + gap + hexWidth + gap;
        add.accept(new ColorSwatch(swatchX, y, swatchWidth, rowHeight,
                narration, color));
        add.accept(Button.builder(Component.literal("Reset"), ignored -> {
            reset.run();
            rebuild.run();
        }).bounds(x + width - resetWidth, y, resetWidth, rowHeight).build());
        return y + rowHeight + gap;
    }

    static String format(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    static Integer parse(String text) {
        if (text == null) return null;
        String value = text.strip();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("(?i)[0-9a-f]{6}")) return null;
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class HexBox extends M3EEditBox {
        private HexBox(Screen screen, Font font, int x, int y, int width, int height,
                       String narration, IntSupplier color, IntConsumer update) {
            super(screen, font, x, y, width, height, Component.literal(narration));
            setMaxLength(7);
            setCentered(true);
            setValue(format(color.getAsInt()));
            setResponder(text -> {
                Integer parsed = parse(text);
                setTextColor(parsed == null ? 0xFFFF5555 : 0xFFE0E0E0);
                if (parsed != null) update.accept(parsed);
            });
        }

    }

    private static final class ColorSwatch extends AbstractWidget {
        private final IntSupplier color;

        private ColorSwatch(int x, int y, int width, int height,
                            String narration, IntSupplier color) {
            super(x, y, width, height, Component.literal(narration + " preview"));
            this.color = color;
            active = false;
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                           int mouseX, int mouseY,
                                                           float partialTick) {
            graphics.fill(getX(), getY(), getRight(), getBottom(),
                    PianoKeyboardRenderer.HOUSING_BEVEL);
            graphics.fill(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2,
                    0xFF000000 | color.getAsInt());
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }
}
