package m3e.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SmartPlayScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private final Screen parent;
    private SmartPlayPanel panel;

    SmartPlayScreen(Screen parent) {
        super(Component.literal("Smart Play"));
        this.parent = parent;
    }

    @Override protected void init() {
        if (panel == null) panel = new SmartPlayPanel(font,
                this::addRenderableWidget, this::rebuild);
        rebuild();
    }

    @Override public boolean isPauseScreen() { return false; }

    private void rebuild() {
        clearWidgets();
        int contentWidth = Math.min(520, Math.max(260, width - 20));
        int x = (width - contentWidth) / 2;
        panel.render(x, 12, contentWidth, height - 34);
        int footerY = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> onClose())
                .bounds(x, footerY, contentWidth, BUTTON_HEIGHT).build());
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

}
