package m3e.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Mouse-controlled pitch/modulation wheel embedded in the Visual Piano casing. */
final class PianoExpressionWheel extends AbstractWidget {
    private static final int FRAME = 0xFF08090C;
    private static final int SLOT = 0xFF101114;
    private static final int WHEEL = 0xFF34373C;
    private static final int WHEEL_EDGE = 0xFF55595F;
    private static final int INDICATOR = 0xFF3C91CF;

    private final Kind kind;
    private final IntSupplier value;
    private final IntConsumer update;

    PianoExpressionWheel(int x, int y, int width, int height, Kind kind,
                         IntSupplier value, IntConsumer update) {
        super(x, y, width, height, Component.literal(kind.narration));
        this.kind = kind;
        this.value = value;
        this.update = update;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                            int mouseX, int mouseY,
                                            float partialTick) {
        // A fullscreen/windowed transition can render one frame between the
        // screen resize and the panel rebuild. Tiny fallback layouts may also
        // legitimately leave no room for an expression wheel. Do not derive
        // clamp bounds from geometry that cannot contain the seven-pixel thumb.
        if (getWidth() < 8 || getHeight() < 12) return;
        graphics.fill(getX(), getY(), getRight(), getBottom(), FRAME);
        graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1,
                PianoKeyboardRenderer.STATUS_FACE);
        int slotLeft = getX() + Math.max(2, getWidth() / 3);
        int slotRight = getRight() - Math.max(2, getWidth() / 3);
        if (slotRight <= slotLeft) slotRight = slotLeft + 1;
        graphics.fill(slotLeft, getY() + 3, slotRight, getBottom() - 3, SLOT);

        int travel = Math.max(1, getHeight() - 12);
        int centerY = getY() + 4 + Math.round((1.0F - normalized()) * travel);
        int thumbTop = Math.clamp(centerY - 3, getY() + 2, getBottom() - 8);
        graphics.fill(getX() + 2, thumbTop, getRight() - 2, thumbTop + 7,
                WHEEL_EDGE);
        graphics.fill(getX() + 3, thumbTop + 1, getRight() - 3, thumbTop + 6,
                isHoveredOrFocused() ? WHEEL_EDGE : WHEEL);
        graphics.fill(getX() + 4, thumbTop + 3, getRight() - 4, thumbTop + 4,
                INDICATOR);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (doubled) {
            update.accept(kind.neutral);
            return;
        }
        update(event.y());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        update(event.y());
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        if (kind.springLoaded) update.accept(kind.neutral);
    }

    private void update(double mouseY) {
        double position = 1.0D - Math.clamp(
                (mouseY - getY() - 4.0D) / Math.max(1.0D, getHeight() - 12.0D),
                0.0D, 1.0D);
        update.accept(Math.clamp((int) Math.round(position * kind.maximum),
                0, kind.maximum));
    }

    private float normalized() {
        return Math.clamp(value.getAsInt(), 0, kind.maximum)
                / (float) kind.maximum;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(
                kind.narration + " " + value.getAsInt()));
    }

    enum Kind {
        PITCH("Pitch bend", 16_383, 8_192, true),
        VIBRATO("Vibrato", 127, 0, false);

        private final String narration;
        private final int maximum;
        private final int neutral;
        private final boolean springLoaded;

        Kind(String narration, int maximum, int neutral, boolean springLoaded) {
            this.narration = narration;
            this.maximum = maximum;
            this.neutral = neutral;
            this.springLoaded = springLoaded;
        }

        boolean resetsOnRelease() { return springLoaded; }
    }
}
