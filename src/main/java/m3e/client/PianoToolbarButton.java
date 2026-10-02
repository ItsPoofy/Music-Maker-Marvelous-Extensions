package m3e.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

final class PianoToolbarButton extends Button {
    private static final int HOVER_FACE = 0xFF292C31;
    private static final int OPEN_FACE = 0xFF34383E;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int DISABLED_TEXT = 0xFF77797D;

    private final Font font;
    private final BooleanSupplier open;

    PianoToolbarButton(Font font, int x, int y, int width, int height,
                       Component message, OnPress onPress,
                       BooleanSupplier open) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.font = font;
        this.open = open;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY, float partialTick) {
        int face = open.getAsBoolean() ? OPEN_FACE
                : isHoveredOrFocused() ? HOVER_FACE
                : PianoKeyboardRenderer.STATUS_FACE;
        graphics.fill(getX(), getY(), getRight(), getBottom(),
                PianoKeyboardRenderer.HOUSING_BEVEL);
        graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1,
                face);
        graphics.centeredText(font, getMessage(), getX() + getWidth() / 2,
                getY() + Math.max(0, (getHeight() - 9) / 2),
                active ? TEXT : DISABLED_TEXT);
    }
}
