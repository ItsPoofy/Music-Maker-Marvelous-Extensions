package m3e.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class PianoInsetPanel extends AbstractWidget {
    static final int EDGE = 0xFF45484F;
    static final int FACE = 0xFF1B1D21;

    public PianoInsetPanel(int x, int y, int width, int height, String narration) {
        super(x, y, width, height, Component.literal(narration));
        active = false;
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                       int mouseX, int mouseY,
                                                       float partialTick) {
        draw(graphics, getX(), getY(), getWidth(), getHeight());
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y,
                            int width, int height) {
        graphics.fill(x, y, x + width, y + height, EDGE);
        if (width > 2 && height > 2) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, FACE);
        }
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
