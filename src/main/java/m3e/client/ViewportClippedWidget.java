package m3e.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;

/**
 * Keeps a native control interactive while clipping its partially visible
 * pixels and hitbox to a scrolling viewport.
 */
public final class ViewportClippedWidget extends AbstractWidget {
    private final AbstractWidget child;
    private final int clipLeft;
    private final int clipTop;
    private final int clipRight;
    private final int clipBottom;

    private ViewportClippedWidget(AbstractWidget child, int clipLeft, int clipTop,
                                  int clipRight, int clipBottom) {
        super(Math.max(child.getX(), clipLeft), Math.max(child.getY(), clipTop),
                intersectionLength(child.getX(), child.getRight(), clipLeft, clipRight),
                intersectionLength(child.getY(), child.getBottom(), clipTop, clipBottom),
                child.getMessage());
        this.child = child;
        this.clipLeft = clipLeft;
        this.clipTop = clipTop;
        this.clipRight = clipRight;
        this.clipBottom = clipBottom;
    }

    static boolean intersects(AbstractWidget widget, int x, int y, int width, int height) {
        return widget.getRight() > x && widget.getX() < x + width
                && widget.getBottom() > y && widget.getY() < y + height;
    }

    static AbstractWidget clip(AbstractWidget widget, int x, int y, int width, int height) {
        int right = x + width;
        int bottom = y + height;
        if (!intersects(widget, x, y, width, height)) {
            throw new IllegalArgumentException("Widget does not intersect its viewport");
        }
        if (widget.getX() >= x && widget.getY() >= y
                && widget.getRight() <= right && widget.getBottom() <= bottom) {
            return widget;
        }
        return new ViewportClippedWidget(widget, x, y, right, bottom);
    }

    static int intersectionLength(int firstStart, int firstEnd,
                                  int secondStart, int secondEnd) {
        return Math.max(0, Math.min(firstEnd, secondEnd)
                - Math.max(firstStart, secondStart));
    }

    public static GuiEventListener unwrap(GuiEventListener listener) {
        GuiEventListener current = listener;
        while (current instanceof ViewportClippedWidget clipped) {
            current = clipped.child;
        }
        return current;
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                       int mouseX, int mouseY,
                                                       float partialTick) {
        boolean ownsPointer = containsClip(mouseX, mouseY)
                || OverlayDropdown.ownsPopup(child, mouseX, mouseY);
        int childMouseX = ownsPointer ? mouseX : Integer.MIN_VALUE;
        int childMouseY = ownsPointer ? mouseY : Integer.MIN_VALUE;
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        child.extractRenderState(graphics, childMouseX, childMouseY, partialTick);
        graphics.disableScissor();
    }

    @Override public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && ((containsClip(mouseX, mouseY) && child.isMouseOver(mouseX, mouseY))
                || OverlayDropdown.ownsPopup(child, mouseX, mouseY));
    }

    @Override public boolean isActive() { return visible && child.isActive(); }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return isMouseOver(event.x(), event.y()) && child.mouseClicked(event, doubled);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return child.mouseReleased(event);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event,
                                          double deltaX, double deltaY) {
        return child.mouseDragged(event, deltaX, deltaY);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY,
                                           double horizontal, double vertical) {
        return isMouseOver(mouseX, mouseY)
                && child.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public void mouseMoved(double mouseX, double mouseY) {
        if (containsClip(mouseX, mouseY)) child.mouseMoved(mouseX, mouseY);
    }

    @Override public boolean keyPressed(KeyEvent event) { return child.keyPressed(event); }

    @Override public boolean keyReleased(KeyEvent event) { return child.keyReleased(event); }

    @Override public boolean charTyped(CharacterEvent event) { return child.charTyped(event); }

    @Override public boolean preeditUpdated(PreeditEvent event) {
        return child.preeditUpdated(event);
    }

    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        child.setFocused(focused);
    }

    @Override public boolean shouldTakeFocusAfterInteraction() {
        return child.shouldTakeFocusAfterInteraction();
    }

    @Override public int getTabOrderGroup() { return child.getTabOrderGroup(); }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        child.updateNarration(output);
    }

    private boolean containsClip(double x, double y) {
        return x >= clipLeft && x < clipRight && y >= clipTop && y < clipBottom;
    }
}
