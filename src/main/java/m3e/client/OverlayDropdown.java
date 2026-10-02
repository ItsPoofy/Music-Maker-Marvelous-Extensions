package m3e.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Shared, Dexed-style overlay dropdown used by M3E and extension screens.
 * Options deliberately use the trigger's exact bounds and the vanilla button
 * sprite supplies the visual separation between adjacent rows.
 */
public final class OverlayDropdown {
    public static final String ARROW = " \u25be";
    public static final String SELECTED_MARKER = "\u25b6 ";

    private OverlayDropdown() { }

    static boolean ownsPopup(AbstractWidget widget, double mouseX, double mouseY) {
        return widget instanceof DropdownButton<?> button
                && button.popup != null && button.popup.contains(mouseX, mouseY);
    }

    public record Choice<T>(T value, String label) {
        public Choice {
            if (label == null) label = "";
        }
    }

    public static final class Controller {
        private final Runnable rerender;
        private String openKey;
        private int start;
        private int top;
        private int bottom;
        private Popup<?> popup;
        private DropdownButton<?> openButton;

        public Controller(Runnable rerender) {
            this.rerender = rerender;
        }

        /** Starts one render pass and defines the vertical popup bounds. */
        public void beginFrame(int top, int bottom) {
            this.top = top;
            this.bottom = Math.max(top + 1, bottom);
            popup = null;
            openButton = null;
        }

        public <T> Button button(String key, int x, int y, int width, int height,
                                 String mainLabel, List<Choice<T>> choices,
                                 int selected, int maximumRows,
                                 Consumer<T> select) {
            if (choices.isEmpty()) throw new IllegalArgumentException(
                    "A dropdown must have at least one choice");
            int boundedSelected = Math.clamp(selected, 0, choices.size() - 1);
            Popup<T> currentPopup = null;
            if (key.equals(openKey)) {
                currentPopup = popup(key, x, y, width, height, choices,
                        boundedSelected, maximumRows, select);
                popup = currentPopup;
            }
            DropdownButton<T> result = new DropdownButton<>(this, key, x, y,
                    width, height, mainLabel, choices, boundedSelected,
                    maximumRows, select, currentPopup);
            if (currentPopup != null) openButton = result;
            return result;
        }

        public boolean isOpen() {
            return openKey != null;
        }

        public boolean isOpenButton(AbstractWidget widget) {
            return widget == openButton;
        }

        public void close() {
            openKey = null;
            popup = null;
            openButton = null;
        }

        /** Adds the popup painter after ordinary controls so it stays on top. */
        public void addOverlay(Consumer<AbstractWidget> add) {
            if (popup != null) add.accept(new Overlay(popup));
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
            if (popup == null || !popup.contains(mouseX, mouseY)
                    || popup.choices().size() <= popup.rows()) return false;
            int direction = vertical < 0.0D ? 1 : vertical > 0.0D ? -1 : 0;
            if (direction == 0) return true;
            int next = Math.clamp(start + direction, 0,
                    popup.choices().size() - popup.rows());
            if (next != start) {
                start = next;
                rerender.run();
            }
            return true;
        }

        /** Closes an open menu when its modal area does not own the click. */
        public boolean closeFromOutsideClick(MouseButtonEvent event) {
            if (openKey == null || event.button() != 0) return false;
            if ((openButton != null && openButton.containsTrigger(event.x(), event.y()))
                    || (popup != null && popup.contains(event.x(), event.y()))) return false;
            close();
            rerender.run();
            return true;
        }

        /** Lets modal screens route a popup choice before controls underneath it. */
        public boolean mouseClicked(MouseButtonEvent event) {
            if (popup == null || event.button() != 0
                    || !popup.contains(event.x(), event.y())) return false;
            int row = (int) ((event.y() - popup.y()) / popup.rowHeight());
            selectPopup(popup, popup.start() + Math.clamp(
                    row, 0, popup.rows() - 1));
            return true;
        }

        private <T> Popup<T> popup(String key, int x, int y, int width, int height,
                                   List<Choice<T>> choices, int selected,
                                   int maximumRows, Consumer<T> select) {
            int desired = Math.min(Math.max(1, maximumRows), choices.size());
            int belowY = y + height + 1;
            int belowRows = Math.max(0, (bottom - belowY) / Math.max(1, height));
            int aboveRows = Math.max(0, (y - 1 - top) / Math.max(1, height));
            boolean below = belowRows >= desired || belowRows >= aboveRows;
            int available = below ? belowRows : aboveRows;
            int rows = Math.min(desired, Math.max(1, available));
            start = Math.clamp(start, 0, Math.max(0, choices.size() - rows));
            int popupY = below ? belowY : Math.max(top, y - rows * height - 1);
            return new Popup<>(key, x, popupY, width, height, rows,
                    choices, selected, start, select);
        }

        private <T> void selectPopup(Popup<T> selectedPopup, int index) {
            Choice<T> choice = selectedPopup.choices().get(index);
            close();
            selectedPopup.select().accept(choice.value());
        }

        private <T> void toggle(String key, int selected, int maximumRows,
                                int choiceCount) {
            if (key.equals(openKey)) {
                close();
            } else {
                openKey = key;
                int rows = Math.min(Math.max(1, maximumRows), choiceCount);
                start = Math.clamp(selected - rows / 2, 0,
                        Math.max(0, choiceCount - rows));
            }
            rerender.run();
        }
    }

    private static final class DropdownButton<T> extends Button {
        private final Controller owner;
        private final String key;
        private final List<Choice<T>> choices;
        private final int selected;
        private final int maximumRows;
        private final Consumer<T> select;
        private final Popup<T> popup;

        private DropdownButton(Controller owner, String key, int x, int y,
                               int width, int height, String mainLabel,
                               List<Choice<T>> choices, int selected,
                               int maximumRows, Consumer<T> select,
                               Popup<T> popup) {
            super(x, y, width, height, Component.literal(mainLabel + ARROW),
                    ignored -> { }, DEFAULT_NARRATION);
            this.owner = owner;
            this.key = key;
            this.choices = choices;
            this.selected = selected;
            this.maximumRows = maximumRows;
            this.select = select;
            this.popup = popup;
        }

        private boolean containsTrigger(double mouseX, double mouseY) {
            return mouseX >= getX() && mouseX < getRight()
                    && mouseY >= getY() && mouseY < getBottom();
        }

        @Override public boolean isMouseOver(double mouseX, double mouseY) {
            return active && (containsTrigger(mouseX, mouseY)
                    || (popup != null && popup.contains(mouseX, mouseY)));
        }

        @Override protected void extractContents(GuiGraphicsExtractor graphics,
                                                 int mouseX, int mouseY,
                                                 float partialTick) {
            extractDefaultSprite(graphics);
            extractDefaultLabel(graphics.textRendererForWidget(this,
                    GuiGraphicsExtractor.HoveredTextEffects.NONE));
        }

        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (popup != null && popup.contains(event.x(), event.y())) {
                int row = (int) ((event.y() - popup.y()) / popup.rowHeight());
                int index = popup.start() + Math.clamp(row, 0, popup.rows() - 1);
                owner.close();
                select.accept(choices.get(index).value());
                return;
            }
            owner.toggle(key, selected, maximumRows, choices.size());
        }

        @Override public boolean mouseScrolled(double mouseX, double mouseY,
                                               double horizontal, double vertical) {
            return owner.mouseScrolled(mouseX, mouseY, vertical);
        }
    }

    private record Popup<T>(String key, int x, int y, int width, int rowHeight,
                            int rows, List<Choice<T>> choices, int selected,
                            int start, Consumer<T> select) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + rows * rowHeight;
        }
    }

    private static final class Overlay extends AbstractWidget {
        private final Popup<?> popup;

        private Overlay(Popup<?> popup) {
            super(popup.x(), popup.y(), popup.width(),
                    popup.rows() * popup.rowHeight(),
                    Component.literal("Dropdown choices"));
            this.popup = popup;
            active = false;
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                           int mouseX, int mouseY,
                                                           float partialTick) {
            for (int row = 0; row < popup.rows(); row++) {
                int index = popup.start() + row;
                Button option = Button.builder(Component.literal(
                                (index == popup.selected() ? SELECTED_MARKER : "")
                                        + popup.choices().get(index).label()),
                                ignored -> { })
                        .bounds(popup.x(), popup.y() + row * popup.rowHeight(),
                                popup.width(), popup.rowHeight()).build();
                option.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }
}
