package m3e.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Vanilla-style M3E text field: Enter, Escape, or an outside click dismisses focus. */
class M3EEditBox extends EditBox {
    private static final Set<Screen> REGISTERED_SCREENS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private final Screen screen;

    M3EEditBox(Screen screen, Font font, int x, int y, int width, int height,
                   Component narration) {
        super(font, x, y, width, height, narration);
        this.screen = screen;
    }

    static void registerFocusDismissal() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!REGISTERED_SCREENS.add(screen)) return;
            ScreenMouseEvents.beforeMouseClick(screen).register((activeScreen, event) -> {
                if (ViewportClippedWidget.unwrap(activeScreen.getFocused())
                        instanceof M3EEditBox field
                        && !field.isMouseOver(event.x(), event.y())) {
                    activeScreen.setFocused(null);
                }
            });
        });
    }

    static boolean isDismissKey(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                || key == GLFW.GLFW_KEY_ESCAPE;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (isDismissKey(event.key())) {
            screen.setFocused(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public void setFocused(boolean focused) {
        boolean wasFocused = isFocused();
        super.setFocused(focused);
        if (wasFocused && !focused) onFocusLost();
    }

    protected void onFocusLost() { }
}
