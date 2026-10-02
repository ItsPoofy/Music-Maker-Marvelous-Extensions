package m3e.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Compact drag-adjustable FM parameter control shared by every editor page. */
final class DexedKnob extends AbstractWidget {
    private final String label;
    private final int maximum;
    private final IntSupplier getter;
    private final IntConsumer setter;

    DexedKnob(int x, int y, int width, int height, String label, int maximum,
              IntSupplier getter, IntConsumer setter) {
        super(x, y, width, height, Component.literal(label));
        this.label = label;
        this.maximum = maximum;
        this.getter = getter;
        this.setter = setter;
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                        int mouseX, int mouseY, float partialTick) {
        int border = isHovered() ? 0xff6c84a0 : 0xff3d4650;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xff11151a);
        int value = getter.getAsInt();
        int centerX = getX() + width / 2;
        int centerY = getY() + 14;
        graphics.fill(centerX - 7, centerY - 7, centerX + 8, centerY + 8, 0xff292f36);
        int marker = Math.round(value * 13.0F / Math.max(1, maximum));
        graphics.fill(centerX - 6 + marker, centerY - 6, centerX - 4 + marker, centerY - 3, 0xff45a4e6);
        graphics.centeredText(net.minecraft.client.Minecraft.getInstance().font,
                label, centerX, getY() + height - 18, 0xffb8c0c8);
        graphics.centeredText(net.minecraft.client.Minecraft.getInstance().font,
                Component.literal(Integer.toString(value)), centerX, getY() + height - 9, 0xffffffff);
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubled) {
        setFromX(event.x());
    }

    @Override protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        setFromX(event.x());
    }

    private void setFromX(double mouseX) {
        double fraction = Math.clamp((mouseX - getX() - 2) / Math.max(1.0, width - 4.0), 0.0, 1.0);
        setter.accept((int) Math.round(fraction * maximum));
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { }
}
