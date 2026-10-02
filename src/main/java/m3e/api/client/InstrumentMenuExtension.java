package m3e.api.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A menu entry contributed to M3E's expanded instrument interface. */
public final class InstrumentMenuExtension {
    private final String id;
    private final Component label;
    private final BooleanSupplier available;
    private final Consumer<Screen> opener;
    private final Supplier<?> stateKey;
    private final Renderer renderer;

    private InstrumentMenuExtension(String id, Component label,
                                    BooleanSupplier available,
                                    Consumer<Screen> opener,
                                    Supplier<?> stateKey,
                                    Renderer renderer) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.available = Objects.requireNonNull(available, "available");
        this.opener = opener;
        this.stateKey = stateKey;
        this.renderer = renderer;
    }

    public static InstrumentMenuExtension action(String id, Component label,
                                                 BooleanSupplier available,
                                                 Consumer<Screen> opener) {
        return new InstrumentMenuExtension(id, label, available,
                Objects.requireNonNull(opener, "opener"), null, null);
    }

    public static InstrumentMenuExtension panel(String id, Component label,
                                                BooleanSupplier available,
                                                Supplier<?> stateKey,
                                                Renderer renderer) {
        return new InstrumentMenuExtension(id, label, available, null,
                Objects.requireNonNull(stateKey, "stateKey"),
                Objects.requireNonNull(renderer, "renderer"));
    }

    public String id() { return id; }
    public Component label() { return label; }
    public boolean available() { return available.getAsBoolean(); }
    public boolean opensScreen() { return opener != null; }
    public void open(Screen returnScreen) {
        if (opener != null) opener.accept(returnScreen);
    }
    public Object stateKey() { return stateKey == null ? this : stateKey.get(); }
    public void render(Screen screen, Font font, int x, int y, int width, int contentBottom,
                       Consumer<AbstractWidget> add, Runnable rerender) {
        if (renderer != null) {
            renderer.render(screen, font, x, y, width, contentBottom, add, rerender);
        }
    }

    @FunctionalInterface
    public interface Renderer {
        void render(Screen screen, Font font, int x, int y, int width, int contentBottom,
                    Consumer<AbstractWidget> add, Runnable rerender);
    }
}
