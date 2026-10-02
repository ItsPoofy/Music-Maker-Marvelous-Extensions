package m3e.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.Items;
import m3e.sheet.MusicScore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

final class SongbookManagementScreen extends Screen {
    private static final int MAX_WIDTH = 640;
    private static final int MAX_HEIGHT = 310;
    private static final int FOOTER_HEIGHT = 34;
    private static final int INSET = InstrumentPanelGeometry.SECTION_INSET;
    private static final int GAP = InstrumentPanelGeometry.SECTION_GAP;
    private static final int ROW = 20;
    private static final int ROW_GAP = InstrumentPanelGeometry.GAP;
    private static final int ARROW_WIDTH = 22;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int SELECTED_BORDER = 0xFFFFFFFF;

    private final SongbookManagementModel model;
    private final InstrumentMidiScreenAccess previewOutput;
    private final InstrumentMidiPlayback previewPlayback = new InstrumentMidiPlayback(true);
    private final OverlayDropdown.Controller instrumentDropdown;
    private final List<AbstractWidget> previewControls = new ArrayList<>();
    private final PlaybackTransportPanel.Binding previewTransport = new PreviewBinding();
    private CommitEditBox bookName;
    private CommitEditBox songName;
    private PlaybackTransportPanel.TimelineSlider previewScrubber;
    private Button previewPlayPause;
    private MusicScore previewSong;
    private String previewInstrumentOverride;
    private int seenRevision = Integer.MIN_VALUE;
    private List<SongbookClientState.SongEntry> seenEntries = List.of();
    private UUID seenBook;
    private String seenBookLabel = "";
    private int seenSelected = Integer.MIN_VALUE;
    private int seenCapacity = Integer.MIN_VALUE;
    private int scroll;
    private int listViewportX;
    private int listViewportY;
    private int listViewportWidth;
    private int listViewportHeight;
    private int listContentHeight;
    private int scrollTrackX;
    private int scrollTrackY;
    private int scrollTrackWidth;
    private int scrollTrackHeight;
    private int scrollDragOffset;
    private boolean scrollbarDragging;
    private int shellX;
    private int shellY;
    private int shellWidth;
    private int shellHeight;
    private int footerY;
    private int listPanelX;
    private int listPanelY;
    private int listPanelWidth;
    private int listPanelHeight;
    private int optionsX;
    private int optionsY;
    private int optionsWidth;
    private int optionsHeight;
    private int namePanelX;
    private int namePanelY;
    private int namePanelWidth;
    private int namePanelHeight;
    private int controlsPanelX;
    private int controlsPanelY;
    private int controlsPanelWidth;
    private int controlsPanelHeight;
    private int instrumentPanelX;
    private int instrumentPanelY;
    private int instrumentPanelWidth;
    private int instrumentPanelHeight;
    private int actionPanelX;
    private int actionPanelY;
    private int actionPanelWidth;
    private int actionPanelHeight;
    private int octaveLabelY;
    private int selectedRowX;
    private int selectedRowY;
    private int selectedRowWidth;
    private int selectedRowHeight;
    private boolean rebuilding;

    private SongbookManagementScreen() {
        this(SongbookManagementModel.inventory(), null);
    }

    SongbookManagementScreen(SongbookManagementModel model,
                             InstrumentMidiScreenAccess previewOutput) {
        super(Component.literal("Songbook"));
        this.model = Objects.requireNonNull(model, "model");
        this.previewOutput = previewOutput == null
                ? new ScoreImportPreviewOutput(this::previewInstrument)
                : previewOutput;
        this.instrumentDropdown = new OverlayDropdown.Controller(this::refreshWidgets);
    }

    static void open(UUID bookId) {
        if (SongbookClientState.openManagedBook(bookId)) {
            SongbookClientState.endSelectedPlayback();
            SongbookClientState.selectBook(bookId);
            Minecraft.getInstance().gui.setScreen(new SongbookManagementScreen());
        }
    }

    @Override protected void init() {
        if (rebuilding) return;
        previewControls.clear();
        previewScrubber = null;
        previewPlayPause = null;
        selectedRowWidth = 0;
        previewPlayback.attach(previewOutput);

        shellWidth = Math.max(1, Math.min(MAX_WIDTH, width - 12));
        shellHeight = Math.max(1, Math.min(MAX_HEIGHT, height - 12));
        shellX = (width - shellWidth) / 2;
        shellY = (height - shellHeight) / 2;
        footerY = shellY + shellHeight - FOOTER_HEIGHT;

        int contentX = shellX + 10;
        int contentY = shellY + 10;
        int contentWidth = Math.max(1, shellWidth - 20);
        int contentBottom = footerY - INSET;
        int bookLabelWidth = 82;
        addRenderableWidget(new StringWidget(contentX, contentY + 5,
                bookLabelWidth, 10, Component.literal("Songbook Name"), font));
        bookName = new CommitEditBox(contentX + bookLabelWidth, contentY,
                contentWidth - bookLabelWidth, Component.literal("Songbook name"),
                model::renameBook);
        bookName.setMaxLength(48);
        bookName.setInitialValue(model.bookLabel());
        addRenderableWidget(bookName);

        int bodyY = contentY + ROW + GAP;
        int bodyHeight = Math.max(1, contentBottom - bodyY);
        optionsWidth = Math.clamp(contentWidth * 34 / 100, 184, 218);
        listPanelX = contentX;
        listPanelY = bodyY;
        listPanelWidth = Math.max(1, contentWidth - optionsWidth - GAP);
        listPanelHeight = bodyHeight;
        optionsX = listPanelX + listPanelWidth + GAP;
        optionsY = bodyY;
        optionsHeight = bodyHeight;

        addSongRows();
        instrumentDropdown.beginFrame(optionsY + INSET, optionsY + optionsHeight - INSET);
        addOptions();
        addFooter();
        instrumentDropdown.addOverlay(this::addRenderableWidget);
        syncPreviewSong();
        refreshPreviewControls();
        rememberState();
    }

    private void addSongRows() {
        List<SongbookClientState.SongEntry> entries = model.entries();
        int capacity = Math.max(0, model.capacity());
        int rowCount = Math.max(1, capacity);
        listViewportX = listPanelX + INSET;
        listViewportY = listPanelY + INSET;
        listViewportWidth = Math.max(1, listPanelWidth - INSET * 2);
        listViewportHeight = Math.max(1, listPanelHeight - INSET * 2);
        listContentHeight = rowCount * (ROW + ROW_GAP) - ROW_GAP;
        int maximumScroll = maximumScroll(listContentHeight, listViewportHeight);
        scroll = Math.clamp(scroll, 0, maximumScroll);
        boolean scrollable = maximumScroll > 0;
        int rowWidth = Math.max(1, listViewportWidth - (scrollable ? 9 : 0));
        int selected = model.selectedIndex();

        for (int slot = 0; slot < rowCount; slot++) {
            int rowY = listViewportY + slot * (ROW + ROW_GAP) - scroll;
            boolean hasSong = slot < entries.size();
            boolean selectedSong = hasSong && slot == selected;
            int mainWidth = selectedSong ? selectedMainWidth(rowWidth) : rowWidth;
            SongbookClientState.SongEntry entry = hasSong ? entries.get(slot) : null;
            String metadata = entry == null ? "" : rowMetadata(entry, mainWidth - 80);
            int nameWidth = Math.max(1, mainWidth - font.width(metadata) - (metadata.isEmpty() ? 0 : 8));
            String label = hasSong ? rowLabel(slot, entry.displayName(), nameWidth)
                    : capacity == 0 ? "Loading Songbook…" : (slot + 1) + "  [EMPTY]";
            int index = slot;
            Button song = new LeftAlignedButton(font, listViewportX, rowY,
                    mainWidth, ROW, Component.literal(label), Component.literal(metadata),
                    ignored -> selectSong(index));
            song.active = hasSong;
            addClipped(song);
            if (!selectedSong) continue;

            int upX = listViewportX + mainWidth + ROW_GAP;
            Button up = Button.builder(Component.literal("↑"), ignored -> moveSelected(-1))
                    .bounds(upX, rowY, ARROW_WIDTH, ROW).build();
            up.active = slot > 0;
            addClipped(up);
            int downX = upX + ARROW_WIDTH + ROW_GAP;
            Button down = Button.builder(Component.literal("↓"), ignored -> moveSelected(1))
                    .bounds(downX, rowY,
                            Math.max(1, listViewportX + rowWidth - downX), ROW).build();
            down.active = slot + 1 < entries.size();
            addClipped(down);
            selectedRowX = listViewportX;
            selectedRowY = rowY;
            selectedRowWidth = mainWidth;
            selectedRowHeight = ROW;
        }
        scrollTrackWidth = 5;
        scrollTrackX = listViewportX + listViewportWidth - scrollTrackWidth - 1;
        scrollTrackY = listViewportY;
        scrollTrackHeight = listViewportHeight;
        if (!scrollable) scrollbarDragging = false;
    }

    private void addClipped(AbstractWidget widget) {
        if (!ViewportClippedWidget.intersects(widget, listViewportX, listViewportY,
                listViewportWidth, listViewportHeight)) return;
        addRenderableWidget(ViewportClippedWidget.clip(widget,
                listViewportX, listViewportY, listViewportWidth, listViewportHeight));
    }

    private void addOptions() {
        int innerX = optionsX + INSET;
        int innerWidth = Math.max(1, optionsWidth - INSET * 2);
        int nextY = optionsY + INSET;
        SongbookClientState.SongEntry selectedEntry = selectedEntry();
        boolean hasSong = selectedEntry != null;

        namePanelX = innerX;
        namePanelY = nextY;
        namePanelWidth = innerWidth;
        namePanelHeight = 32;
        songName = new CommitEditBox(namePanelX + 6, namePanelY + 6,
                Math.max(1, namePanelWidth - 12), Component.literal("Song name"),
                model::renameSelected);
        songName.setMaxLength(96);
        if (hasSong) songName.setInitialValue(selectedEntry.displayName());
        songName.setHint(Component.literal("Song name"));
        boolean titleEditable = hasSong && !selectedEntry.metadata().signed();
        songName.setEditable(titleEditable);
        songName.active = titleEditable;
        addRenderableWidget(songName);
        nextY += namePanelHeight + GAP;

        instrumentPanelX = innerX;
        instrumentPanelY = nextY;
        instrumentPanelWidth = innerWidth;
        instrumentPanelHeight = 32;
        addInstrumentDropdown(selectedEntry, hasSong);
        nextY += instrumentPanelHeight + GAP;

        controlsPanelX = innerX;
        controlsPanelY = nextY;
        controlsPanelWidth = innerWidth;
        controlsPanelHeight = 106;
        int controlsX = controlsPanelX + 6;
        int controlsWidth = Math.max(1, controlsPanelWidth - 12);
        int y = controlsPanelY + 6;
        addPreviewControls(controlsX, y, controlsWidth);
        y += ROW * 2 + 4 + GAP;
        addPitchRow(controlsX, y, controlsWidth, true);
        octaveLabelY = y + 6;
        y += ROW + ROW_GAP;
        PlaybackTransportPanel.addTempo(this::addRenderableWidget, this, font,
                controlsX, y, controlsWidth, ROW, ROW_GAP, previewTransport);
        nextY += controlsPanelHeight + GAP;

        actionPanelX = innerX;
        actionPanelY = nextY;
        actionPanelWidth = innerWidth;
        actionPanelHeight = 32;
        int actionX = actionPanelX + 6;
        int actionWidth = Math.max(1, actionPanelWidth - 12);
        int half = Math.max(1, (actionWidth - ROW_GAP) / 2);
        Button takeOut = Button.builder(Component.literal("Extract Song"), ignored -> {
                    previewPlayback.stop();
                    model.extractSelected();
                }).bounds(actionX, actionPanelY + 6, half, ROW).build();
        takeOut.active = hasSong && model.capacity() > 1;
        addRenderableWidget(takeOut);
        Button clear = Button.builder(Component.literal("Clear Song"), ignored -> {
                    previewPlayback.stop();
                    model.deleteSelected();
                }).bounds(actionX + half + ROW_GAP, actionPanelY + 6,
                        actionWidth - half - ROW_GAP, ROW).build();
        clear.active = hasSong;
        addRenderableWidget(clear);
    }

    private void addInstrumentDropdown(SongbookClientState.SongEntry entry,
                                       boolean hasSong) {
        ArrayList<OverlayDropdown.Choice<String>> choices = new ArrayList<>();
        choices.add(new OverlayDropdown.Choice<>("", "None"));
        for (InstrumentChoiceCatalog.Entry option : InstrumentChoiceCatalog.entries()) {
            if (option.id().isEmpty()) continue;
            choices.add(new OverlayDropdown.Choice<>(option.id(), option.name()));
        }
        String selectedId = entry == null ? "" : entry.metadata().instrumentId();
        int selected = 0;
        for (int index = 1; index < choices.size(); index++) {
            if (choices.get(index).value().equals(selectedId)) {
                selected = index;
                break;
            }
        }
        String selectedName = choices.get(selected).label();
        Button selector = instrumentDropdown.button("songbook-instrument",
                instrumentPanelX + 6, instrumentPanelY + 6,
                Math.max(1, instrumentPanelWidth - 12), ROW,
                "Instrument: " + selectedName, choices, selected, 7,
                this::switchPreviewInstrument);
        selector.active = hasSong;
        addRenderableWidget(selector);
    }

    private void addPreviewControls(int x, int y, int width) {
        java.util.function.Consumer<AbstractWidget> add = widget -> {
            addRenderableWidget(widget);
            previewControls.add(widget);
        };
        previewScrubber = PlaybackTransportPanel.addScrubber(add,
                x, y, width, ROW, previewTransport, true);
        previewPlayPause = PlaybackTransportPanel.addTransport(add,
                x, y + ROW + 4, width, ROW, ROW_GAP, previewTransport);
    }

    private void addPitchRow(int x, int y, int width, boolean octave) {
        int labelWidth = 50;
        int buttonWidth = 28;
        int valueX = x + labelWidth + ROW_GAP;
        int valueWidth = Math.max(1, width - labelWidth - ROW_GAP
                - buttonWidth * 2 - ROW_GAP * 2);
        Button down = Button.builder(Component.literal("−"), ignored -> changePitch(octave ? -12 : -1))
                .bounds(valueX, y, buttonWidth, ROW).build();
        down.active = model.canTranspose(octave ? -12 : -1);
        addRenderableWidget(down);
        Button value = Button.builder(Component.literal(octave
                        ? octaveLabel(model.transpose()) : semitoneLabel(model.transpose())), ignored -> { })
                .bounds(valueX + buttonWidth + ROW_GAP, y, valueWidth, ROW).build();
        value.active = false;
        addRenderableWidget(value);
        int upX = valueX + buttonWidth + ROW_GAP + valueWidth + ROW_GAP;
        Button up = Button.builder(Component.literal("+"), ignored -> changePitch(octave ? 12 : 1))
                .bounds(upX, y, Math.max(1, x + width - upX), ROW).build();
        up.active = model.canTranspose(octave ? 12 : 1);
        addRenderableWidget(up);
    }

    private void addFooter() {
        int footerTop = footerY + 1;
        int footerBottom = shellY + shellHeight - 4;
        int y = footerTop + Math.max(0, footerBottom - footerTop - ROW) / 2;
        addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> onClose())
                .bounds(optionsX, y, optionsWidth, ROW).build());
    }

    private void selectSong(int index) {
        if (index == model.selectedIndex()) return;
        previewPlayback.stop();
        previewSong = null;
        previewInstrumentOverride = null;
        model.select(index);
        refreshWidgets();
    }

    private void moveSelected(int delta) {
        int target = model.selectedIndex() + delta;
        if (model.moveSelected(target)) refreshWidgets();
    }

    private void changePitch(int delta) {
        if (!model.transpose(delta)) return;
        if (previewSong != null) {
            previewPlayback.updatePitchShift(previewSong,
                    model.tempoBpm(), model.transpose());
        }
        refreshWidgets();
    }

    private SongbookClientState.SongEntry selectedEntry() {
        List<SongbookClientState.SongEntry> entries = model.entries();
        int selected = model.selectedIndex();
        return selected < 0 || selected >= entries.size() ? null : entries.get(selected);
    }

    private IItemInstrument previewInstrument() {
        if (previewSong != null && previewSong.percussion()) return (IItemInstrument) Items.DRUM_KIT;
        if (previewInstrumentOverride != null) {
            IItemInstrument override = InstrumentChoiceCatalog.resolve(previewInstrumentOverride);
            return override == null ? (IItemInstrument) Items.HARP_MC : override;
        }
        SongbookClientState.SongEntry entry = selectedEntry();
        IItemInstrument assigned = entry == null ? null
                : InstrumentChoiceCatalog.resolve(entry.metadata().instrumentId());
        if (assigned != null) return assigned;
        return (IItemInstrument) Items.HARP_MC;
    }

    private void switchPreviewInstrument(String instrumentId) {
        InstrumentMidiPlayback.TransportState previous =
                previewPlayback.transportState(model.tempoBpm());
        previewPlayback.stop();
        previewInstrumentOverride = instrumentId == null ? "" : instrumentId;
        model.setInstrument(previewInstrumentOverride);
        if (previewSong == null) return;
        long duration = previewPlayback.durationMicros(previewSong, model.tempoBpm());
        long playhead = Math.clamp(previous.playheadMicros(), 0L, duration);
        previewPlayback.attach(previewOutput);
        previewPlayback.seek(previewSong, playhead, model.tempoBpm(), model.transpose());
        if (previous.playing() && playhead < duration) {
            previewPlayback.playPause(previewSong, model.tempoBpm(), model.transpose());
        }
    }

    private void syncPreviewSong() {
        MusicScore nextSong = model.previewSong();
        if (nextSong == previewSong) return;
        InstrumentMidiPlayback.TransportState previous =
                previewPlayback.transportState(model.tempoBpm());
        previewPlayback.stop();
        previewSong = nextSong;
        if (previewSong != null) {
            long duration = previewPlayback.durationMicros(previewSong, model.tempoBpm());
            long playhead = Math.clamp(previous.playheadMicros(), 0L, duration);
            previewPlayback.seek(previewSong, playhead, model.tempoBpm(), model.transpose());
            if (previous.playing() && playhead < duration) {
                previewPlayback.playPause(previewSong, model.tempoBpm(), model.transpose());
            }
        }
    }

    private void refreshPreviewControls() {
        boolean available = previewTransport.available();
        for (AbstractWidget control : previewControls) control.active = available;
        if (previewScrubber != null) previewScrubber.refresh();
        if (previewPlayPause != null) previewPlayPause.setMessage(Component.literal(
                previewTransport.playing() ? "II" : "▶"));
    }

    private void rememberState() {
        seenRevision = model.revision();
        seenEntries = model.entries();
        seenBook = model.bookId();
        seenBookLabel = model.bookLabel();
        seenSelected = model.selectedIndex();
        seenCapacity = model.capacity();
    }

    @Override public void tick() {
        super.tick();
        if (!model.available()) {
            onClose();
            return;
        }
        syncPreviewSong();
        previewPlayback.validateInstrumentBinding();
        if (seenRevision != model.revision()
                || !seenEntries.equals(model.entries())
                || !seenBookLabel.equals(model.bookLabel())
                || !Objects.equals(seenBook, model.bookId())
                || seenSelected != model.selectedIndex()
                || seenCapacity != model.capacity()) {
            refreshWidgets();
            return;
        }
        refreshPreviewControls();
    }

    private void refreshWidgets() {
        if (rebuilding) return;
        rebuilding = true;
        clearWidgets();
        rebuilding = false;
        init();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY,
                                           double horizontal, double vertical) {
        if (instrumentDropdown.mouseScrolled(mouseX, mouseY, vertical)) return true;
        if (vertical != 0.0D && containsListViewport(mouseX, mouseY)) {
            int maximum = maximumScroll(listContentHeight, listViewportHeight);
            int next = Math.clamp(scroll - (int) Math.round(vertical * 22.0D), 0, maximum);
            if (next != scroll) {
                scroll = next;
                refreshWidgets();
            }
            return maximum > 0;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (instrumentDropdown.mouseClicked(event)) return true;
        if (instrumentDropdown.closeFromOutsideClick(event)) return true;
        if (event.button() == 0 && listContentHeight > listViewportHeight
                && event.x() >= scrollTrackX && event.x() < scrollTrackX + scrollTrackWidth
                && event.y() >= scrollTrackY && event.y() < scrollTrackY + scrollTrackHeight) {
            int thumbHeight = ScrollbarGeometry.scrollThumbHeight(
                    scrollTrackHeight, listViewportHeight, listContentHeight);
            int thumbY = ScrollbarGeometry.scrollThumbY(scrollTrackY, scrollTrackHeight,
                    thumbHeight, listViewportHeight, listContentHeight, scroll);
            scrollDragOffset = event.y() >= thumbY && event.y() < thumbY + thumbHeight
                    ? (int) Math.round(event.y() - thumbY) : thumbHeight / 2;
            scrollbarDragging = true;
            updateScrollFromMouse(event.y());
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (scrollbarDragging && event.button() == 0) {
            updateScrollFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean containsListViewport(double x, double y) {
        return x >= listViewportX && x < listViewportX + listViewportWidth
                && y >= listViewportY && y < listViewportY + listViewportHeight;
    }

    private void updateScrollFromMouse(double mouseY) {
        int maximum = maximumScroll(listContentHeight, listViewportHeight);
        int thumbHeight = ScrollbarGeometry.scrollThumbHeight(
                scrollTrackHeight, listViewportHeight, listContentHeight);
        int travel = Math.max(1, scrollTrackHeight - thumbHeight);
        int thumbTop = Math.clamp((int) Math.round(mouseY) - scrollDragOffset,
                scrollTrackY, scrollTrackY + travel);
        int next = Math.clamp(Math.round((thumbTop - scrollTrackY)
                * maximum / (float) travel), 0, maximum);
        if (next == scroll) return;
        scroll = next;
        refreshWidgets();
    }

    @Override public void onFilesDrop(List<Path> paths) {
        if (!model.handleDrop(this, paths)) super.onFilesDrop(paths);
    }

    @Override public void onClose() {
        previewPlayback.stop();
        model.close();
        minecraft.gui.setScreen(null);
    }

    @Override public void removed() {
        previewPlayback.stop();
        super.removed();
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        graphics.fill(0, 0, width, height, 0xD0101010);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                             float partialTicks) {
        PianoKeyboardRenderer.drawPanelShadow(graphics, shellX, shellY + 3, shellWidth, shellHeight);
        PianoKeyboardRenderer.drawFourPixelPanel(graphics, shellX, shellY, shellWidth, shellHeight);
        graphics.fill(shellX + 4, footerY, shellX + shellWidth - 4, footerY + 1,
                PianoKeyboardRenderer.STATUS_DIVIDER);
        PianoInsetPanel.draw(graphics, listPanelX, listPanelY, listPanelWidth, listPanelHeight);
        if (listContentHeight > listViewportHeight) {
            graphics.fill(scrollTrackX, scrollTrackY,
                    scrollTrackX + scrollTrackWidth, scrollTrackY + scrollTrackHeight, 0xFF252D34);
            int thumbHeight = ScrollbarGeometry.scrollThumbHeight(
                    scrollTrackHeight, listViewportHeight, listContentHeight);
            int thumbY = ScrollbarGeometry.scrollThumbY(scrollTrackY, scrollTrackHeight,
                    thumbHeight, listViewportHeight, listContentHeight, scroll);
            graphics.fill(scrollTrackX, thumbY,
                    scrollTrackX + scrollTrackWidth, thumbY + thumbHeight, 0xFF758895);
        }
        PianoInsetPanel.draw(graphics, optionsX, optionsY, optionsWidth, optionsHeight);
        PianoInsetPanel.draw(graphics, namePanelX, namePanelY, namePanelWidth, namePanelHeight);
        PianoInsetPanel.draw(graphics, controlsPanelX, controlsPanelY,
                controlsPanelWidth, controlsPanelHeight);
        PianoInsetPanel.draw(graphics, instrumentPanelX, instrumentPanelY,
                instrumentPanelWidth, instrumentPanelHeight);
        PianoInsetPanel.draw(graphics, actionPanelX, actionPanelY,
                actionPanelWidth, actionPanelHeight);
        graphics.text(font, Component.literal("Octave"),
                controlsPanelX + 8, octaveLabelY, TEXT);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        drawSelectedBorder(graphics);
    }

    private void drawSelectedBorder(GuiGraphicsExtractor graphics) {
        if (selectedRowWidth <= 0) return;
        int left = Math.max(selectedRowX, listViewportX);
        int top = Math.max(selectedRowY, listViewportY);
        int right = Math.min(selectedRowX + selectedRowWidth, listViewportX + listViewportWidth);
        int bottom = Math.min(selectedRowY + selectedRowHeight, listViewportY + listViewportHeight);
        if (right <= left || bottom <= top) return;
        graphics.fill(left, top, right, Math.min(bottom, top + 1), SELECTED_BORDER);
        graphics.fill(left, Math.max(top, bottom - 1), right, bottom, SELECTED_BORDER);
        graphics.fill(left, top, Math.min(right, left + 1), bottom, SELECTED_BORDER);
        graphics.fill(Math.max(left, right - 1), top, right, bottom, SELECTED_BORDER);
    }

    private String rowLabel(int index, String name, int width) {
        String prefix = (index + 1) + "  ";
        String value = name == null || name.isBlank() ? "Untitled Song" : name;
        int maximum = Math.max(1, width - 12);
        String result = prefix + value;
        while (font.width(result) > maximum && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
            result = prefix + value + "…";
        }
        return result;
    }

    private String rowMetadata(SongbookClientState.SongEntry entry, int maximumWidth) {
        String instrument = entry.metadata().instrument();
        String author = entry.metadata().author();
        String result = metadataLabel(entry.tempoBpm(), instrument, author);
        while (font.width(result) > Math.max(1, maximumWidth)
                && (instrument.length() > 1 || author.length() > 1)) {
            if (instrument.length() >= author.length() && instrument.length() > 1) {
                instrument = instrument.substring(0, instrument.length() - 1);
            } else if (author.length() > 1) {
                author = author.substring(0, author.length() - 1);
            }
            result = metadataLabel(entry.tempoBpm(), instrument, author);
        }
        return result;
    }

    private static String metadataLabel(int tempoBpm, String instrument, String author) {
        StringBuilder label = new StringBuilder().append(tempoBpm).append(" BPM");
        if (!instrument.isEmpty()) label.append(" · ").append(instrument);
        if (!author.isEmpty()) label.append(" · by ").append(author);
        return label.toString();
    }

    static int selectedMainWidth(int rowWidth) {
        return Math.max(1, rowWidth - ARROW_WIDTH * 2 - ROW_GAP * 2);
    }

    static int maximumScroll(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - viewportHeight);
    }

    static String octaveLabel(int semitones) {
        int octaves = semitones / 12;
        return octaves == 0 ? "Original" : (octaves > 0 ? "+" : "") + octaves;
    }

    static String semitoneLabel(int semitones) {
        int remainder = semitones % 12;
        return (remainder > 0 ? "+" : "") + remainder + " st";
    }

    private final class PreviewBinding implements PlaybackTransportPanel.Binding {
        @Override public boolean available() {
            return selectedEntry() != null && previewOutput.playbackInstrumentPresent();
        }
        @Override public long playheadMicros() { return previewPlayback.playheadMicros(); }
        @Override public long durationMicros() {
            if (previewSong != null) {
                return previewPlayback.durationMicros(previewSong, model.tempoBpm());
            }
            SongbookClientState.SongEntry entry = selectedEntry();
            return entry == null ? 0L : entry.durationMicros();
        }
        @Override public int tempoBpm() { return model.tempoBpm(); }
        @Override public int displayTempoBpm() {
            return previewPlayback.transportState(model.tempoBpm()).bpm();
        }
        @Override public void setTempoBpm(int bpm) {
            previewPlayback.stop();
            model.setTempoBpm(bpm);
            if (previewSong != null) previewPlayback.resetToStart(previewSong, model.tempoBpm());
        }
        @Override public double speed() { return 1.0D; }
        @Override public void setSpeed(double speed) { }
        @Override public boolean playing() {
            return previewPlayback.transportState(model.tempoBpm()).playing();
        }
        @Override public void playPause() {
            if (!available() || previewSong == null) return;
            previewPlayback.playPause(previewSong, model.tempoBpm(), model.transpose());
            refreshPreviewControls();
        }
        @Override public void stop() {
            previewPlayback.stop();
            refreshPreviewControls();
        }
        @Override public void seekMicros(long micros) {
            if (!available() || previewSong == null) return;
            previewPlayback.seek(previewSong, micros, model.tempoBpm(), model.transpose());
            refreshPreviewControls();
        }
        @Override public int transpose() { return model.transpose(); }
        @Override public boolean canTranspose(int delta) { return model.canTranspose(delta); }
        @Override public void transpose(int delta) { changePitch(delta); }
    }

    private final class CommitEditBox extends M3EEditBox {
        private final Function<String, Boolean> commitAction;
        private boolean committing;
        private String committedValue = "";

        private CommitEditBox(int x, int y, int width, Component label,
                              Function<String, Boolean> commitAction) {
            super(SongbookManagementScreen.this, font, x, y, width, ROW, label);
            this.commitAction = commitAction;
        }

        private void setInitialValue(String value) {
            super.setValue(value == null ? "" : value);
            committedValue = getValue();
        }

        @Override protected void onFocusLost() { commit(); }

        private void commit() {
            if (committing || !active || getValue().equals(committedValue)) return;
            committing = true;
            try {
                if (commitAction.apply(getValue())) {
                    committedValue = getValue();
                    setTextColor(0xFFE0E0E0);
                } else {
                    setTextColor(0xFFFF5555);
                }
            } finally {
                committing = false;
            }
        }
    }

    private static final class LeftAlignedButton extends Button {
        private final Font font;
        private final Component rightMessage;

        private LeftAlignedButton(Font font, int x, int y, int width, int height,
                                  Component message, Component rightMessage, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.font = font;
            this.rightMessage = rightMessage;
        }

        @Override protected void extractContents(GuiGraphicsExtractor graphics,
                                                 int mouseX, int mouseY,
                                                 float partialTick) {
            extractDefaultSprite(graphics);
            ActiveTextCollector text = graphics.textRendererForWidget(
                    this, GuiGraphicsExtractor.HoveredTextEffects.NONE);
            int textY = getY() + Math.max(0, getHeight() - font.lineHeight) / 2 + 1;
            text.accept(TextAlignment.LEFT, getX() + 6, textY, getMessage());
            if (!rightMessage.getString().isEmpty()) {
                text.accept(TextAlignment.RIGHT, getX() + getWidth() - 6, textY, rightMessage);
            }
        }
    }
}
