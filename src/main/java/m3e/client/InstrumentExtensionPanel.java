package m3e.client;

import m3e.api.client.M3EClientIntegration;
import m3e.api.client.InstrumentMenuExtension;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import xerca.xercamusic.client.GuiInstrument;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Music Maker velocity/chord HUD, Songbook, and per-instrument controls. */
final class InstrumentExtensionPanel {
    private static Panel panel;

    private InstrumentExtensionPanel() { }

    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof GuiInstrument && M3EClientIntegration.instrumentUiEnabled()) {
                panel = new Panel(client, screen, width, height);
                panel.sync();
                Panel created = panel;
                ScreenMouseEvents.beforeMouseScroll(screen).register(
                        (activeScreen, mouseX, mouseY, horizontal, vertical) -> {
                            // AFTER_INIT may run again on resize. Only the newest
                            // panel instance may consume the screen's wheel event.
                            if (panel == created && created.screen == activeScreen) {
                                created.mouseScrolled(mouseX, mouseY, vertical);
                            }
                        });
                ScreenMouseEvents.beforeMouseClick(screen).register(
                        (activeScreen, event) -> {
                            if (panel == created && created.screen == activeScreen) {
                                created.mouseClicked(event);
                            }
                        });
                ScreenMouseEvents.beforeMouseDrag(screen).register(
                        (activeScreen, event, deltaX, deltaY) -> {
                            if (panel == created && created.screen == activeScreen) {
                                created.mouseDragged(event);
                            }
                        });
                ScreenMouseEvents.beforeMouseRelease(screen).register(
                        (activeScreen, event) -> {
                            if (panel == created && created.screen == activeScreen) {
                                created.mouseReleased(event);
                            }
                        });
            } else if (client.gui.screen() == screen) {
                panel = null;
                PianoWaterfallState.screenClosed();
            }
        });
    }

    static void tick(Minecraft client) {
        Screen current = client.gui.screen();
        if (!(current instanceof GuiInstrument) || !M3EClientIntegration.instrumentUiEnabled()) {
            panel = null;
            PianoWaterfallState.screenClosed();
            return;
        }
        if (panel == null || panel.screen != current) {
            panel = new Panel(client, current, current.width, current.height);
        }
        panel.sync();
    }

    private static final class Panel {
        private static final int HEIGHT = InstrumentPanelGeometry.BUTTON_HEIGHT;
        private static final int GAP = InstrumentPanelGeometry.GAP;
        private final Minecraft client;
        private final Screen screen;
        private int screenWidth;
        private int screenHeight;
        private final List<AbstractWidget> performanceOwned = new ArrayList<>();
        private final List<AbstractWidget> panelOwned = new ArrayList<>();
        private final SongbookInstrumentPanel songbookPanel;
        private final InstrumentOptionsPanel optionsPanel;
        private final SmartPlayPanel smartPlayPanel;
        private boolean pianoMenuExpanded;
        private PianoMenuCategory pianoMenuCategory = PianoMenuCategory.KEYBOARD;
        private String selectedExtensionId;
        private Object extensionStateKey;
        private StringWidget pitchWidget;
        private StringWidget chordWidget;
        private StringWidget octaveWidget;
        private StringWidget vibratoWidget;
        private Button octaveDownButton;
        private Button octaveUpButton;
        private boolean renderRequested;
        private boolean performanceRebuildRequested;
        private boolean waterfallActive;

        private Panel(Minecraft client, Screen screen, int screenWidth, int screenHeight) {
            this.client = client;
            this.screen = screen;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.songbookPanel = new SongbookInstrumentPanel(
                    client, screen, this::addPanel, this::requestRender);
            this.optionsPanel = new InstrumentOptionsPanel(
                    client, screen, this::addPanel, this::requestRender,
                    this::pianoLayoutChanged);
            this.smartPlayPanel = new SmartPlayPanel(
                    client.font, this::addPanel, this::requestRender);
        }

        private void sync() {
            if (screen.width != screenWidth || screen.height != screenHeight) {
                screenWidth = screen.width;
                screenHeight = screen.height;
                removePerformanceOwned();
                removePanelOwned();
            }
            boolean nextWaterfall = PianoWaterfallState.sync();
            if (nextWaterfall != waterfallActive) {
                waterfallActive = nextWaterfall;
                removePerformanceOwned();
                removePanelOwned();
            }
            if (waterfallActive) {
                if (performanceOwned.isEmpty()) addWaterfallExit();
                return;
            }
            if (performanceRebuildRequested) {
                performanceRebuildRequested = false;
                removePerformanceOwned();
            }
            if (performanceOwned.isEmpty()) addPerformanceControls();
            refreshPerformanceControls();
            List<SongbookClientState.SongEntry> imported = SongbookClientState.snapshots();
            List<SongbookClientState.BookOption> carriedBooks = SongbookClientState.books();
            int selected = SongbookClientState.selectedIndex();
            java.util.UUID active = SongbookClientState.activeBookId();
            InstrumentMenuExtension selectedExtension = selectedMenuExtension();
            if (selectedExtensionId != null && selectedExtension == null) {
                selectedExtensionId = null;
            }
            Object updatedExtensionState = selectedExtension == null
                    ? null : selectedExtension.stateKey();
            boolean extensionStateChanged = !Objects.equals(
                    updatedExtensionState, extensionStateKey);
            extensionStateKey = updatedExtensionState;
            boolean requested = renderRequested;
            renderRequested = false;
            if (songbookPanel.update(imported, carriedBooks, active, selected,
                    SongbookClientState.capacity())
                    || panelOwned.isEmpty()
                    || optionsPanel.needsRerender()
                    || requested
                    || extensionStateChanged) {
                if (!songbookPanel.hasBooks()
                        && pianoMenuCategory == PianoMenuCategory.SONGBOOK) {
                    pianoMenuCategory = PianoMenuCategory.KEYBOARD;
                }
                renderPanel();
            } else songbookPanel.refreshPlayback();
        }

        private int instrumentId() {
            return screen instanceof InstrumentMidiScreenAccess access ? access.instrumentId() : 0;
        }

        private void addPerformanceControls() {
            PianoKeyboardGeometry.Layout layout = PianoKeyboardGeometry.layout(
                    screenWidth, screenHeight, pianoMode());
            PianoKeyboardGeometry.Rect status = layout.statusArea();
            int y = status.y() + Math.max(0, (status.height() - 10) / 2);
            chordWidget = statusWidget(status, 0, y);
            octaveWidget = statusWidget(status, 1, y);
            pitchWidget = statusWidget(status, 2, y);
            vibratoWidget = statusWidget(status, 3, y);
            addPerformance(chordWidget);
            addPerformance(octaveWidget);
            addPerformance(pitchWidget);
            addPerformance(vibratoWidget);
            addPianoExpressionControls(layout);
        }

        private void addWaterfallExit() {
            PianoKeyboardGeometry.Layout layout = PianoKeyboardGeometry.layout(
                    screenWidth, screenHeight, pianoMode());
            PianoKeyboardGeometry.Rect buttons = layout.octaveControlsArea();
            addPerformance(new PianoToolbarButton(client.font,
                    buttons.x(), layout.keybed().y(), buttons.width(), buttons.height(),
                    Component.literal("Exit"), ignored -> {
                PianoWaterfallState.dismiss();
                waterfallActive = false;
                removePerformanceOwned();
                removePanelOwned();
                performanceRebuildRequested = true;
                requestRender();
            }, () -> false));
        }

        private void addPianoExpressionControls(PianoKeyboardGeometry.Layout layout) {
            PianoKeyboardGeometry.Rect buttons = layout.octaveControlsArea();
            PianoKeyboardGeometry.Rect wheels = layout.expressionWheelsArea();
            int controlGap = 2;
            int leftWidth = Math.max(1, (buttons.width() - controlGap) / 2);
            int rightX = buttons.x() + leftWidth + controlGap;
            int rightWidth = Math.max(1, buttons.right() - rightX);
            octaveDownButton = new PianoToolbarButton(client.font,
                    buttons.x(), buttons.y(), leftWidth, buttons.height(),
                    Component.literal("−"), ignored -> changeOctave(false), () -> false);
            octaveUpButton = new PianoToolbarButton(client.font,
                    rightX, buttons.y(), rightWidth, buttons.height(),
                    Component.literal("+"), ignored -> changeOctave(true), () -> false);
            addPerformance(octaveDownButton);
            addPerformance(octaveUpButton);

            int leftWheelWidth = Math.max(1, (wheels.width() - controlGap) / 2);
            int rightWheelX = wheels.x() + leftWheelWidth + controlGap;
            int rightWheelWidth = Math.max(1, wheels.right() - rightWheelX);
            addPerformance(new PianoExpressionWheel(wheels.x(), wheels.y(),
                    leftWheelWidth, wheels.height(), PianoExpressionWheel.Kind.PITCH,
                    InstrumentExpressionController::displayedBend14,
                    InstrumentExpressionController::acceptPitchBend));
            addPerformance(new PianoExpressionWheel(rightWheelX, wheels.y(),
                    rightWheelWidth, wheels.height(), PianoExpressionWheel.Kind.VIBRATO,
                    InstrumentExpressionController::displayedModulation,
                    InstrumentExpressionController::acceptModulation));
        }

        private void changeOctave(boolean increase) {
            if (!(screen instanceof InstrumentOctaveAccess access)) return;
            if (increase) access.m3e$increaseKeyboardOctave();
            else access.m3e$decreaseKeyboardOctave();
            refreshPerformanceControls();
        }

        private StringWidget statusWidget(PianoKeyboardGeometry.Rect status,
                                          int column, int y) {
            int left = status.x() + status.width() * column / 4 + 3;
            int right = status.x() + status.width() * (column + 1) / 4 - 3;
            return new CenteredStringWidget(left, y, Math.max(1, right - left), 10,
                    client.font);
        }

        private void refreshPerformanceControls() {
            if (chordWidget == null) return;
            setStatus(pitchWidget, String.format(Locale.ROOT, "Pitch: %+.2f st",
                    InstrumentExpressionController.displayedBendSemitones()));
            setStatus(chordWidget, InstrumentPerformanceControls.chordLabel());
            setStatus(octaveWidget, "Octave: " + selectedRange());
            int vibrato = Math.round(InstrumentExpressionController.displayedModulation()
                    * 100.0F / 127.0F);
            setStatus(vibratoWidget, "Vibrato: " + vibrato + "%");
            if (screen instanceof InstrumentOctaveAccess access) {
                if (octaveDownButton != null) {
                    octaveDownButton.active = access.m3e$canDecreaseOctave();
                }
                if (octaveUpButton != null) {
                    octaveUpButton.active = access.m3e$canIncreaseOctave();
                }
            }
        }

        private void setStatus(StringWidget widget, String text) {
            if (widget == null) return;
            int width = widget instanceof CenteredStringWidget centered
                    ? centered.layoutWidth() : widget.getWidth();
            widget.setMessage(Component.literal(fit(text, width)));
        }

        private String fit(String text, int width) {
            if (client.font.width(text) <= width) return text;
            int end = text.length();
            while (end > 1 && client.font.width(text.substring(0, end) + "…") > width) end--;
            return text.substring(0, Math.max(1, end)) + "…";
        }

        private PianoKeyboardGeometry.Mode pianoMode() {
            return InstrumentOptions.compactInstrumentUi()
                    ? PianoKeyboardGeometry.Mode.COMPACT : PianoKeyboardGeometry.Mode.STANDARD;
        }

        private String selectedRange() {
            return screen instanceof InstrumentOctaveAccess access
                    ? access.m3e$selectedKeyboardRange() : "—";
        }

        private void pianoLayoutChanged() {
            if (screen instanceof InstrumentOctaveAccess access) {
                access.m3e$pianoLayoutChanged();
            }
            performanceRebuildRequested = true;
            requestRender();
        }

        private void requestRender() {
            renderRequested = true;
        }

        private void renderPanel() {
            renderRequested = false;
            removePanelOwned();
            renderPianoMenu();
        }

        private void renderPianoMenu() {
            PianoKeyboardGeometry.Layout layout = PianoKeyboardGeometry.layout(
                    screenWidth, screenHeight, pianoMode());
            PianoExpandedMenu.Geometry menu = PianoExpandedMenu.geometry(
                    layout, screenWidth, screenHeight, pianoMenuExpanded);
            if (pianoMenuExpanded) {
                addPanel(new PianoExpandedMenu.Background(menu));
                renderPianoMenuNavigation(menu.navigation());
                renderPianoMenuContent(menu.content());
            }
            PianoKeyboardGeometry.Rect tab = menu.tab();
            addPanel(new PianoToolbarButton(client.font, tab.x(), tab.y(),
                    tab.width(), tab.height(),
                    Component.literal(pianoMenuExpanded ? "▼" : "▲"), ignored -> {
                pianoMenuExpanded = !pianoMenuExpanded;
                requestRender();
            }, () -> pianoMenuExpanded));
        }

        private void renderPianoMenuNavigation(PianoKeyboardGeometry.Rect area) {
            int x = area.x() + 6;
            int y = area.y() + 6;
            int width = Math.max(1, area.width() - 12);
            for (PianoMenuCategory category : PianoMenuCategory.values()) {
                if (category == PianoMenuCategory.SONGBOOK && !songbookPanel.hasBooks()) continue;
                PianoMenuCategory selected = category;
                addPanel(new PianoToolbarButton(client.font, x, y, width, HEIGHT,
                        Component.literal(category.label), ignored -> {
                    pianoMenuCategory = selected;
                    selectedExtensionId = null;
                    songbookPanel.collapseBooks();
                    requestRender();
                }, () -> selectedExtensionId == null && pianoMenuCategory == selected));
                y += HEIGHT + 4;
            }
            for (InstrumentMenuExtension extension : M3EClientIntegration.menuExtensions()) {
                if (!extension.available()) continue;
                addPanel(new PianoToolbarButton(client.font, x, y, width, HEIGHT,
                        extension.label(), ignored -> {
                    if (extension.opensScreen()) {
                        extension.open(screen);
                        return;
                    }
                    selectedExtensionId = extension.id();
                    extensionStateKey = extension.stateKey();
                    songbookPanel.collapseBooks();
                    requestRender();
                }, () -> extension.id().equals(selectedExtensionId)));
                y += HEIGHT + 4;
            }
        }

        private void renderPianoMenuContent(PianoKeyboardGeometry.Rect area) {
            int inset = InstrumentPanelGeometry.SECTION_INSET;
            int contentX = area.x() + inset;
            int contentWidth = Math.max(1, area.width() - inset * 2);
            int y = area.y() + inset;
            int controlsWidth = InstrumentPanelGeometry.boundedWidth(contentWidth,
                    InstrumentPanelGeometry.MAX_WORKSPACE_WIDTH);
            int x = contentX + Math.max(0, (contentWidth - controlsWidth) / 2);
            InstrumentMenuExtension extension = selectedMenuExtension();
            if (extension != null) {
                extension.render(screen, client.font, x, y, controlsWidth,
                        area.bottom() - inset, this::addPanel, this::requestRender);
                return;
            }
            switch (pianoMenuCategory) {
                case SONGBOOK -> songbookPanel.renderWide(x, y, controlsWidth,
                        area.bottom() - inset);
                case KEYBOARD -> optionsPanel.renderKeyboard(x, y, controlsWidth,
                        area.bottom() - inset);
                case PLAY_ASSIST -> smartPlayPanel.render(x, y, controlsWidth,
                        area.bottom() - inset);
                case DISPLAY -> optionsPanel.renderDisplay(x, y, controlsWidth,
                        area.bottom() - inset);
                case INSTRUMENT -> optionsPanel.renderInstrument(
                        x, y, controlsWidth, area.bottom() - inset);
            }
        }

        private InstrumentMenuExtension selectedMenuExtension() {
            if (selectedExtensionId == null) return null;
            return M3EClientIntegration.menuExtensions().stream()
                    .filter(extension -> extension.id().equals(selectedExtensionId))
                    .filter(InstrumentMenuExtension::available)
                    .findFirst()
                    .orElse(null);
        }

        private void mouseScrolled(double mouseX, double mouseY, double vertical) {
            if (!pianoMenuExpanded || pianoMenuCategory != PianoMenuCategory.INSTRUMENT) return;
            optionsPanel.mouseScrolled(mouseX, mouseY, vertical);
        }

        private void mouseClicked(MouseButtonEvent event) {
            if (!pianoMenuExpanded || pianoMenuCategory != PianoMenuCategory.INSTRUMENT) return;
            optionsPanel.mouseClicked(event);
        }

        private void mouseDragged(MouseButtonEvent event) {
            if (!pianoMenuExpanded || pianoMenuCategory != PianoMenuCategory.INSTRUMENT) return;
            optionsPanel.mouseDragged(event);
        }

        private void mouseReleased(MouseButtonEvent event) {
            if (!pianoMenuExpanded || pianoMenuCategory != PianoMenuCategory.INSTRUMENT) return;
            optionsPanel.mouseReleased(event);
        }

        private void addPerformance(AbstractWidget widget) {
            performanceOwned.add(widget);
            Screens.getWidgets(screen).add(widget);
        }

        private void addPanel(AbstractWidget widget) {
            addPanelRoot(widget);
        }

        private void addPanelRoot(AbstractWidget widget) {
            panelOwned.add(widget);
            Screens.getWidgets(screen).add(widget);
        }

        private void removePanelOwned() {
            Screens.getWidgets(screen).removeAll(panelOwned);
            panelOwned.clear();
            songbookPanel.detached();
        }

        private void removePerformanceOwned() {
            Screens.getWidgets(screen).removeAll(performanceOwned);
            performanceOwned.clear();
            pitchWidget = null;
            chordWidget = null;
            octaveWidget = null;
            vibratoWidget = null;
            octaveDownButton = null;
            octaveUpButton = null;
        }

    }

    private enum PianoMenuCategory {
        SONGBOOK("Songbook"), KEYBOARD("Keyboard"), PLAY_ASSIST("Play Assist"),
        DISPLAY("Display"), INSTRUMENT("Instrument");

        private final String label;

        PianoMenuCategory(String label) { this.label = label; }
    }

}
