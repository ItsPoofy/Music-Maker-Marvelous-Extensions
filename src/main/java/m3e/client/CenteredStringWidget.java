package m3e.client;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;

/** Fixed-width centered label; 26.2 StringWidget otherwise shrinks on updates. */
final class CenteredStringWidget extends StringWidget {
    private final int layoutWidth;

    CenteredStringWidget(int x, int y, int width, int height, Font font) {
        this(x, y, width, height, font, Component.empty());
    }

    CenteredStringWidget(int x, int y, int width, int height,
                         Font font, Component message) {
        super(x, y, width, height, message, font);
        layoutWidth = width;
        setWidth(width);
    }

    @Override public void setMessage(Component message) {
        super.setMessage(message);
        setWidth(layoutWidth);
    }

    int layoutWidth() { return layoutWidth; }

    @Override public void visitLines(ActiveTextCollector output) {
        int textWidth = getFont().width(getMessage());
        int textX = getX() + Math.max(0, (layoutWidth - textWidth) / 2);
        int textY = getY() + Math.max(0, (getHeight() - 9) / 2);
        output.accept(textX, textY, getMessage().getVisualOrderText());
    }
}
