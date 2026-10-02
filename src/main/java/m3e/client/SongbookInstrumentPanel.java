package m3e.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

final class SongbookInstrumentPanel {
    private static final int HEIGHT = InstrumentPanelGeometry.BUTTON_HEIGHT;
    private static final int GAP = InstrumentPanelGeometry.GAP;
    private static final int SINGLE_PAGE = 1;

    private final Minecraft client;
    private final Screen screen;
    private final Consumer<AbstractWidget> add;
    private final Runnable rerender;
    private final SongbookPanelModel model;
    private List<SongbookClientState.SongEntry> midiEntries = List.of();
    private List<SongbookClientState.BookOption> songbooks = List.of();
    private UUID activeBookId;
    private final OverlayDropdown.Controller bookDropdown;
    private int midiPage;
    private int midiSelected = Integer.MIN_VALUE;
    private int songbookCapacity = Integer.MIN_VALUE;
    private Button midiPlayPause;
    private PlaybackTransportPanel.TimelineSlider midiScrubber;
    private final PlaybackTransportPanel.Binding transport;

    SongbookInstrumentPanel(Minecraft client, Screen screen,
                            Consumer<AbstractWidget> add, Runnable rerender) {
        this(client, screen, add, rerender, SongbookPanelModel.inventory());
    }

    SongbookInstrumentPanel(Minecraft client, Screen screen,
                            Consumer<AbstractWidget> add, Runnable rerender,
                            SongbookPanelModel model) {
        this.client = client;
        this.screen = screen;
        this.add = add;
        this.rerender = rerender;
        this.model = model;
        this.bookDropdown = new OverlayDropdown.Controller(rerender);
        this.transport = new PlaybackTransportPanel.Binding() {
            @Override public boolean available() { return !midiEntries.isEmpty(); }
            @Override public long playheadMicros() { return model.playheadMicros(); }
            @Override public long durationMicros() { return model.durationMicros(); }
            @Override public int tempoBpm() { return model.tempoBpm(); }
            @Override public int displayTempoBpm() { return model.displayTempoBpm(); }
            @Override public void setTempoBpm(int bpm) { model.setTempoBpm(bpm); }
            @Override public double speed() { return model.speed(); }
            @Override public void setSpeed(double speed) { model.setSpeed(speed); }
            @Override public boolean playing() { return model.playing(); }
            @Override public void playPause() {
                model.playPause();
                refreshPlayback();
            }
            @Override public void stop() {
                model.stop();
                refreshPlayback();
            }
            @Override public void seekMicros(long micros) { model.seekMicros(micros); }
            @Override public int transpose() { return model.transpose(); }
            @Override public boolean canTranspose(int delta) { return model.canTranspose(delta); }
            @Override public void transpose(int delta) { model.transpose(delta); }
        };
    }

    boolean update(List<SongbookClientState.SongEntry> entries,
                   List<SongbookClientState.BookOption> books,
                   UUID activeBook, int selected, int capacity) {
        boolean changed = !entries.equals(midiEntries) || !books.equals(songbooks)
                || !Objects.equals(activeBook, activeBookId) || selected != midiSelected
                || capacity != songbookCapacity;
        midiEntries = entries;
        songbooks = books;
        activeBookId = activeBook;
        midiSelected = selected;
        songbookCapacity = capacity;
        return changed;
    }

    boolean hasBooks() { return !songbooks.isEmpty(); }

    void collapseBooks() { bookDropdown.close(); }

    void detached() {
        midiPlayPause = null;
        midiScrubber = null;
    }

    void refreshPlayback() {
        if (midiPlayPause != null) {
            midiPlayPause.setMessage(Component.literal(model.stateIcon()));
        }
        if (midiScrubber != null) midiScrubber.refresh();
    }

    void render(int x, int y, int width) {
        render(x, y, width, screen.height - 4);
    }

    void render(int x, int y, int width, int contentBottom) {
        bookDropdown.beginFrame(2, screen.height - 2);
        addBookSelector(x, y, width, 5);
        y += HEIGHT + GAP;

        boolean ensembleActive = model.ensembleActive();
        boolean hasSongs = !midiEntries.isEmpty();
        int slotCount = songbookCapacity > 0 ? Math.max(songbookCapacity, midiEntries.size())
                : Math.max(1, midiEntries.size());
        // Progress, tempo, transport, loop/shuffle, transpose, pager, actions,
        // and the optional band-sync row stay visible.
        int reservedRows = ensembleActive ? 8 : 7;
        int visibleRows = Math.clamp(
                (contentBottom - y - reservedRows * (HEIGHT + GAP) - 8) / (HEIGHT + GAP), 1, 5);
        int pageCount = Math.max(1, (slotCount + visibleRows - 1) / visibleRows);
        midiPage = Math.clamp(midiPage, 0, pageCount - 1);

        y = addPlaybackRows(x, y, width, hasSongs);

        int start = midiPage * visibleRows;
        int pageEnd = Math.min(slotCount, start + visibleRows);
        for (int index = start; index < pageEnd; index++) {
            if (index < midiEntries.size()) {
                SongbookClientState.SongEntry entry = midiEntries.get(index);
                String marker = index == midiSelected ? "▶ " : "";
                int selected = index;
                add.accept(Button.builder(Component.literal(
                        fit(marker + entry.displayName(), width - 8)), ignored -> {
                    model.selectSong(selected);
                    midiSelected = model.selectedIndex();
                    rerender.run();
                }).bounds(x, y, width, HEIGHT).build());
            } else {
                String label = songbookCapacity <= 0 ? "Loading Songbook…" : "— Empty track —";
                Button empty = Button.builder(Component.literal(label), ignored -> { })
                        .bounds(x, y, width, HEIGHT).build();
                empty.active = false;
                add.accept(empty);
            }
            y += HEIGHT + GAP;
        }

        if (pageCount > SINGLE_PAGE) {
            int arrowWidth = 30;
            int pageWidth = width - arrowWidth * 2 - GAP * 2;
            add.accept(Button.builder(Component.literal("◀"), ignored -> {
                midiPage = Math.floorMod(midiPage - 1, pageCount);
                rerender.run();
            }).bounds(x, y, arrowWidth, HEIGHT).build());
            Button pageIndicator = Button.builder(Component.literal(
                            (midiPage + 1) + " / " + pageCount), ignored -> { })
                    .bounds(x + arrowWidth + GAP, y, pageWidth, HEIGHT).build();
            pageIndicator.active = false;
            add.accept(pageIndicator);
            add.accept(Button.builder(Component.literal("▶"), ignored -> {
                midiPage = (midiPage + 1) % pageCount;
                rerender.run();
            }).bounds(x + width - arrowWidth, y, arrowWidth, HEIGHT).build());
            y += HEIGHT + GAP;
        }

        if (ensembleActive) y = addBandSyncRow(x, y, width, hasSongs);
        addActionsRow(x, y, width, hasSongs);
        bookDropdown.addOverlay(add);
    }

    /** Two-column arrangement used only by the Visual Piano drawer. */
    void renderWide(int x, int y, int width, int contentBottom) {
        bookDropdown.beginFrame(2, screen.height - 2);
        int sectionGap = InstrumentPanelGeometry.SECTION_GAP;
        int inset = InstrumentPanelGeometry.SECTION_INSET;
        int leftSectionWidth = Math.max(120, (width - sectionGap) / 2);
        int rightSectionX = x + leftSectionWidth + sectionGap;
        int rightSectionWidth = Math.max(120, width - leftSectionWidth - sectionGap);
        int sectionHeight = Math.max(1, contentBottom - y);
        add.accept(new PianoInsetPanel(x, y, leftSectionWidth, sectionHeight,
                "Now playing"));
        add.accept(new PianoInsetPanel(rightSectionX, y, rightSectionWidth,
                sectionHeight, "Songbook songs"));
        int leftX = x + inset;
        int leftWidth = Math.max(1, leftSectionWidth - inset * 2);
        int rightX = rightSectionX + inset;
        int rightWidth = Math.max(1, rightSectionWidth - inset * 2);
        boolean hasSongs = !midiEntries.isEmpty();
        boolean ensembleActive = model.ensembleActive();

        int top = y + inset;
        int innerBottom = contentBottom - inset;
        int listFooterY = innerBottom - HEIGHT;
        int leftFooterY = innerBottom - (HEIGHT * 2 + 20);
        String title = midiSelected >= 0 && midiSelected < midiEntries.size()
                ? midiEntries.get(midiSelected).displayName() : "No song selected";
        addHeading(leftX, top, leftWidth, "Now Playing");
        addDisabledBox(leftX, top + 10, leftWidth, title);
        int leftY = top + 30;
        addHeading(leftX, leftY, leftWidth, "Controls");
        leftY += 10;
        leftY = addScrubberRow(leftX, leftY, leftWidth);
        leftY = addTempoRow(leftX, leftY, leftWidth);
        leftY = addTransportRow(leftX, leftY, leftWidth);
        leftY = addPlaybackModeRow(leftX, leftY, leftWidth, hasSongs,
                ensembleActive);
        addWideFooter(leftX, leftFooterY, leftWidth, hasSongs);

        addHeading(rightX, top, rightWidth, "Songbook");
        int rightY = addWideBookSelector(rightX, top + 10, rightWidth);
        addHeading(rightX, rightY, rightWidth, "Songs");
        rightY += 10;
        addWideTrackList(rightX, rightY, rightWidth, listFooterY);
        bookDropdown.addOverlay(add);
    }

    private void addHeading(int x, int y, int width, String label) {
        add.accept(new CenteredStringWidget(x, y, width, 10, client.font,
                Component.literal(label)));
    }

    private void addDisabledBox(int x, int y, int width, String label) {
        Button box = Button.builder(Component.literal(fit(label, width - 8)), ignored -> { })
                .bounds(x, y, width, HEIGHT).build();
        box.active = false;
        add.accept(box);
    }

    private int addPlaybackRows(int x, int y, int width, boolean hasSongs) {
        y = addScrubberRow(x, y, width);
        y = addTempoRow(x, y, width);
        y = addTransportRow(x, y, width);
        y = addPlaybackTogglesRow(x, y, width, hasSongs, false);
        addTransposeRow(x, y, width);
        return y + HEIGHT + GAP;
    }

    private int addScrubberRow(int x, int y, int width) {
        midiScrubber = PlaybackTransportPanel.addScrubber(add, x, y, width,
                HEIGHT, transport, true);
        return y + HEIGHT + GAP;
    }

    private int addTempoRow(int x, int y, int width) {
        return PlaybackTransportPanel.addTempoAndSpeed(add, screen, client.font,
                x, y, width, HEIGHT, GAP, transport, false);
    }

    private int addTransportRow(int x, int y, int width) {
        midiPlayPause = PlaybackTransportPanel.addTransport(add, x, y, width,
                HEIGHT, GAP, transport);
        return y + HEIGHT + GAP;
    }

    private int addPlaybackTogglesRow(int x, int y, int width, boolean hasSongs,
                                      boolean includeBandSync) {
        int columns = includeBandSync ? 3 : 2;
        int loopWidth = includeBandSync ? (width - GAP * 2) / columns
                : Math.min(width - 58, 92);
        Button loop = Button.builder(Component.literal(fit("Loop: "
                        + model.loopLabel(), loopWidth - 6)), ignored -> {
            model.cycleLoopMode();
            rerender.run();
        }).bounds(x, y, loopWidth, HEIGHT).build();
        loop.active = hasSongs;
        add.accept(loop);
        int shuffleWidth = includeBandSync ? loopWidth : width - loopWidth - GAP;
        Button shuffle = Button.builder(Component.literal(fit("Shuffle: "
                        + (model.shuffle() ? "On" : "Off"), shuffleWidth - 6)), ignored -> {
            model.setShuffle(!model.shuffle());
            rerender.run();
        }).bounds(x + loopWidth + GAP, y, shuffleWidth, HEIGHT).build();
        shuffle.active = model.canShuffle(midiEntries.size());
        add.accept(shuffle);
        if (includeBandSync) {
            int syncX = x + (loopWidth + GAP) * 2;
            int syncWidth = x + width - syncX;
            Button sync = Button.builder(Component.literal(fit("Sync: "
                    + (model.syncTempoWithEnsemble() ? "On" : "Off"),
                    syncWidth - 6)), ignored -> {
                model.setSyncTempoWithEnsemble(!model.syncTempoWithEnsemble());
                rerender.run();
            }).bounds(syncX, y, syncWidth, HEIGHT).build();
            sync.active = hasSongs;
            add.accept(sync);
        }
        return y + HEIGHT + GAP;
    }

    private int addPlaybackModeRow(int x, int y, int width, boolean hasSongs,
                                   boolean includeBandSync) {
        int half = (width - GAP) / 2;
        Button loop = Button.builder(Component.literal(fit(
                        model.loopLabel(), half - 6)), ignored -> {
            model.cycleLoopMode();
            rerender.run();
        }).bounds(x, y, half, HEIGHT).build();
        loop.active = hasSongs;
        add.accept(loop);
        Button shuffle = Button.builder(Component.literal("Shuffle: "
                        + (model.shuffle() ? "On" : "Off")), ignored -> {
            model.setShuffle(!model.shuffle());
            rerender.run();
        }).bounds(x + half + GAP, y, width - half - GAP, HEIGHT).build();
        shuffle.active = model.canShuffle(midiEntries.size());
        add.accept(shuffle);
        y += HEIGHT + GAP;
        return includeBandSync ? addBandSyncRow(x, y, width, hasSongs) : y;
    }

    private void addWideFooter(int x, int y, int width, boolean hasSongs) {
        addHeading(x, y, width, "Transpose");
        int transposeY = y + 10;
        addTransposeRow(x, transposeY, width);
        int actionsHeadingY = transposeY + HEIGHT;
        addHeading(x, actionsHeadingY, width, "Song Actions");
        addActionsRow(x, actionsHeadingY + 10, width, hasSongs);
    }

    private int addBandSyncRow(int x, int y, int width, boolean hasSongs) {
        Button sync = Button.builder(Component.literal("Sync Ensemble: "
                + (model.syncTempoWithEnsemble() ? "On" : "Off")), ignored -> {
            model.setSyncTempoWithEnsemble(!model.syncTempoWithEnsemble());
            rerender.run();
        }).bounds(x, y, width, HEIGHT).build();
        sync.active = hasSongs;
        add.accept(sync);
        return y + HEIGHT + GAP;
    }

    private void addActionsRow(int x, int y, int width, boolean hasSongs) {
        int half = (width - GAP) / 2;
        Button extract = Button.builder(Component.literal("Extract Song"), ignored -> {
            if (model.extractSelected()) rerender.run();
        }).bounds(x, y, half, HEIGHT).build();
        extract.active = model.canExtract(hasSongs);
        add.accept(extract);
        Button delete = Button.builder(Component.literal("Clear Song"), ignored -> {
            if (model.deleteSelected()) rerender.run();
        }).bounds(x + half + GAP, y, width - half - GAP, HEIGHT).build();
        delete.active = model.canDelete(hasSongs);
        add.accept(delete);
    }

    private int addWideBookSelector(int x, int y, int width) {
        addBookSelector(x, y, width, 6);
        return y + HEIGHT + GAP;
    }

    private void addBookSelector(int x, int y, int width, int maximumRows) {
        List<OverlayDropdown.Choice<SongbookClientState.BookOption>> choices;
        if (songbooks.isEmpty()) {
            choices = List.of(new OverlayDropdown.Choice<>(null, "No Songbook"));
        } else {
            choices = songbooks.stream().map(book -> new OverlayDropdown.Choice<>(
                    book, fit(book.label(), width - 8))).toList();
        }
        int selected = 0;
        for (int index = 0; index < songbooks.size(); index++) {
            if (songbooks.get(index).bookId().equals(activeBookId)) selected = index;
        }
        Button selector = bookDropdown.button("songbook", x, y, width, HEIGHT,
                fit(bookButtonLabel(), width - 16), choices, selected, maximumRows,
                book -> {
                    if (book != null) selectBook(book);
                });
        selector.active = !songbooks.isEmpty();
        add.accept(selector);
    }

    private String bookButtonLabel() {
        String fixed = model.fixedLibraryLabel(midiEntries.size(), songbookCapacity);
        if (fixed != null) return fixed;
        SongbookClientState.BookOption activeBook = songbooks.stream()
                .filter(book -> book.bookId().equals(activeBookId)).findFirst().orElse(null);
        if (activeBook == null) return "Select Songbook";
        int actualSongs = midiEntries.size();
        int totalSlots = songbookCapacity;
        return activeBook.label() + occupancySuffix(actualSongs, totalSlots);
    }

    static String occupancySuffix(int actualSongs, int totalSlots) {
        return totalSlots <= 0 ? "" : "  " + Math.max(0, actualSongs)
                + "/" + totalSlots;
    }

    private void selectBook(SongbookClientState.BookOption book) {
        bookDropdown.close();
        if (model.selectBook(book.bookId())) {
            activeBookId = book.bookId();
            midiEntries = List.of();
            midiSelected = -1;
            songbookCapacity = book.header().capacity();
            midiPage = 0;
        }
        rerender.run();
    }

    private void addWideTrackList(int x, int y, int width, int footerY) {
        int slotCount = songbookCapacity > 0 ? Math.max(songbookCapacity, midiEntries.size())
                : Math.max(1, midiEntries.size());
        int visibleRows = Math.clamp(
                (footerY - y - GAP) / (HEIGHT + GAP), 1, 6);
        int pageCount = Math.max(1, (slotCount + visibleRows - 1) / visibleRows);
        midiPage = Math.clamp(midiPage, 0, pageCount - 1);
        int start = midiPage * visibleRows;
        int end = Math.min(slotCount, start + visibleRows);
        for (int index = start; index < end; index++) {
            if (index < midiEntries.size()) {
                SongbookClientState.SongEntry entry = midiEntries.get(index);
                String marker = index == midiSelected ? "▶ " : "";
                int selected = index;
                add.accept(Button.builder(Component.literal(
                        fit(marker + (index + 1) + ". " + entry.displayName(), width - 8)), ignored -> {
                    model.selectSong(selected);
                    midiSelected = model.selectedIndex();
                    rerender.run();
                }).bounds(x, y, width, HEIGHT).build());
            } else {
                Button empty = Button.builder(Component.literal(
                                (index + 1) + ". [ Empty slot ]"), ignored -> { })
                        .bounds(x, y, width, HEIGHT).build();
                empty.active = false;
                add.accept(empty);
            }
            y += HEIGHT + GAP;
        }
        int arrowWidth = Math.max(30, width / 4);
        int pageWidth = width - arrowWidth * 2 - GAP * 2;
        Button previous = Button.builder(Component.literal("◀"),
                ignored -> {
                    midiPage = Math.floorMod(midiPage - 1, pageCount);
                    rerender.run();
                }).bounds(x, footerY, arrowWidth, HEIGHT).build();
        previous.active = pageCount > SINGLE_PAGE;
        add.accept(previous);
        Button indicator = Button.builder(Component.literal((midiPage + 1) + " / " + pageCount),
                        ignored -> { })
                .bounds(x + arrowWidth + GAP, footerY, pageWidth, HEIGHT).build();
        indicator.active = false;
        add.accept(indicator);
        Button next = Button.builder(Component.literal("▶"),
                ignored -> {
                    midiPage = (midiPage + 1) % pageCount;
                    rerender.run();
                }).bounds(x + width - arrowWidth, footerY, arrowWidth, HEIGHT).build();
        next.active = pageCount > SINGLE_PAGE;
        add.accept(next);
    }

    private void addTransposeRow(int x, int y, int width) {
        PlaybackTransportPanel.addTranspose(add, x, y, width, HEIGHT,
                transport, rerender);
    }

    private String fit(String text, int width) {
        if (client.font.width(text) <= width) return text;
        int end = text.length();
        while (end > 1 && client.font.width(text.substring(0, end) + "…") > width) end--;
        return text.substring(0, Math.max(1, end)) + "…";
    }


}
