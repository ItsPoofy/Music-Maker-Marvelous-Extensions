package m3e.client;

import m3e.instrument.InstrumentVariations;
import m3e.sheet.MusicScore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import xerca.xercamusic.client.MidiHandler;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.ItemMusicSheet;
import xerca.xercamusic.common.item.Items;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

public final class PianoRollScreen extends Screen implements SheetImportScreenAccess {
    private static final int MAX_WIDTH = 560;
    private static final int MAX_HEIGHT = 300;
    private static final int HEADER_HEIGHT = 58;
    private static final int MARGIN = 6;
    private static final int INNER_MARGIN = 10;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_GAP = InstrumentPanelGeometry.GAP;
    private static final int SECTION_INSET = InstrumentPanelGeometry.SECTION_INSET;
    private static final int RULER = 12;
    private static final int KEYS = 50;
    private int rowHeight = 10;
    private static final int VELOCITY_HEIGHT = 38;
    private static final int DEFAULT_NOTE_COLOR = 0xE69ED1A5;
    private static final int SELECTED_NOTE_COLOR = 0xE6E28B8D;
    private static final int VELOCITY_MIDDLE = 64;
    private static final int VELOCITY_BRIGHTNESS_RANGE = 24;
    private static final int AUDITION_COLOR = 0xE6FB9244;
    private static final int SELECTION_BORDER = 0xFFFB9244;
    private static final int SELECTION_FILL = 0x18FB9244;
    private static final Identifier INSTRUMENT_TEXTURES =
            Identifier.fromNamespaceAndPath("xercamusic", "textures/gui/instruments.png");
    private static final double DEFAULT_PIXELS_PER_BEAT = 32.0D;
    private static final int[] LINE_DIVISIONS = {64, 32, 16, 8, 4, 2, 1};
    private static final String[] NOTE_NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };
    private static List<ScoreEditSession.ClipboardNote> clipboard = List.of();
    private static final Map<UUID, ViewState> VIEW_STATES = new LinkedHashMap<>();
    private static KeyboardStyle preferredKeyboardStyle = KeyboardStyle.REGULAR;
    private static KeyLabels preferredKeyLabels = KeyLabels.ROOT_NOTES;
    private static SaveFormat preferredSaveFormat = SaveFormat.M3E;

    private ItemStack sheet;
    private UUID sheetId;
    private boolean blankSheet;
    private int version;
    private ScoreEditSession session;
    private final InstrumentMidiPlayback playback = new InstrumentMidiPlayback(true);
    private final MidiHandler midiHandler;
    private final ScoreImportPreviewOutput output = new ScoreImportPreviewOutput(
            this::instrument, () -> InstrumentVariations.DEFAULT_ID,
            () -> session == null ? 440.0F : session.song().tuningHz());
    private MusicScore transportSong;
    private int shellX;
    private int shellY;
    private int shellWidth;
    private int shellHeight;
    private int contentX;
    private int contentRight;
    private int rulerY;
    private int headerDividerY;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridHeight;
    private double topPitch = 84.0D;
    private double leftTick;
    private double pixelsPerTick;
    private SnapMode snapMode = SnapMode.LINE;
    private Gesture gesture = Gesture.NONE;
    private long gestureNote = -1;
    private double pressX;
    private double pressY;
    private double pressTick;
    private int pressPitch;
    private double currentX;
    private double currentY;
    private double dragPointerX;
    private double dragPointerY;
    private long autoScrollNanos;
    private boolean gestureDragged;
    private double panLeft;
    private double panTop;
    private int auditionPitch = -1;
    private Button playButton;
    private Button recordButton;
    private PlaybackTransportPanel.TimelineSlider scrubber;
    private PlaybackTransportPanel.Binding transportBinding;
    private int tempoBpm;
    private int highlightInterval;
    private boolean replacementPending;
    private UUID replacementSheetId;
    private int replacementVersion = -1;
    private long resizeCursor;
    private long moveCursor;
    private boolean resizeCursorShown;
    private boolean moveCursorShown;
    private final OverlayDropdown.Controller snapDropdown;
    private ResizeEdge resizeEdge = ResizeEdge.END;
    private boolean copyGesture;
    private boolean toggleMarquee;
    private final PianoRollGeometry geometry = new PianoRollGeometry();
    private PianoRollDrag drag;
    private int gestureButton = -1, defaultVelocity = 100, defaultTrack, auditionVelocity = -1;
    private boolean leftResizing, typingEnabled;
    private boolean previewDuringPlayback = true;
    private Tool tool = Tool.DRAW;
    private int velocityY, velocityFeedback = 100, strokeVelocity;
    private long strokeLength;
    private double strokeTime, strokePitch, sliceEndTime, sliceEndPitch, pressContinuousPitch;
    private List<ScoreEditSession.Note> velocityTargets = List.of();
    private long rangeStart = -1, rangeEnd = -1;
    private boolean timeSelection;
    private long pasteAnchor = -1;
    private PianoRollNoteEditor noteEditor;
    private long lastTouchedLength = -1L;
    private boolean viewportInitialized;
    private ScoreEditSession extentSession;
    private long contentExtent;
    private boolean saveSubmitted;
    private boolean importRequired;
    private UUID draftKey;
    private boolean draftConflict;
    private int conflictVersion;
    private boolean conflictShown;
    private boolean exportAfterSave;
    private boolean signRequested;
    private boolean gettingSigned;
    private String signTitle = "";
    private boolean settingsOpen;
    private SettingsTab settingsTab = SettingsTab.DISPLAY;
    private KeyboardStyle keyboardStyle = preferredKeyboardStyle;
    private KeyLabels keyLabels = preferredKeyLabels;
    private SaveFormat saveFormat = preferredSaveFormat;
    private boolean metronomeEnabled;
    private int settingsBodyY;
    private boolean previewInstrumentLocked;
    private int previewInstrument = -1;
    private boolean instrumentLockDirty;
    private final Map<Integer, Integer> heldTypingNotes = new LinkedHashMap<>();
    private final Set<Integer> heldMidiNotes = new LinkedHashSet<>();
    private int typingOctave = 3;
    private final PianoRollRecording recorder = new PianoRollRecording(
            this::finishRecordingNote, this::playMetronomeClick);

    private enum Gesture { NONE, MOVE, RESIZE, MARQUEE, PAN, AUDITION, SCRUB, ERASE, PAINT, SLICE, VELOCITY, RANGE }
    private enum Tool {
        DRAW("Draw (P)"), PAINT("Paint (B)"), SELECT("Select (E)"), SLICE("Slice (C)"), DELETE("Delete (D)");
        final String label;
        Tool(String label) { this.label = label; }
    }
    private enum ResizeEdge { START, END }
    private enum SettingsTab { DISPLAY, SONG, COMPAT }
    private enum KeyboardStyle { FLAT, REGULAR }
    private enum KeyLabels { ALL_NOTES, ROOT_NOTES, NONE }
    private enum SaveFormat { M3E, MM }
    private record EdgeHit(ScoreEditSession.Note note, ResizeEdge edge) { }
    private record ViewState(double leftTick, double topPitch, double pixelsPerTick, int rowHeight) { }
    private record RenderedNote(int left, int right, int pitch, int velocity,
                                boolean selected, boolean highlighted) { }

    private enum SnapMode {
        LINE("Line"), CELL("Cell"), NONE("(none)"),
        SIXTH_STEP("1/6 step"), QUARTER_STEP("1/4 step"),
        THIRD_STEP("1/3 step"), HALF_STEP("1/2 step"), STEP("Step"),
        SIXTH_BEAT("1/6 beat"), QUARTER_BEAT("1/4 beat"),
        THIRD_BEAT("1/3 beat"), HALF_BEAT("1/2 beat"),
        BEAT("Beat"), BAR("Bar");

        private final String label;

        SnapMode(String label) { this.label = label; }
    }

    private static final List<OverlayDropdown.Choice<SnapMode>> SNAP_CHOICES =
            java.util.Arrays.stream(SnapMode.values())
                    .map(mode -> new OverlayDropdown.Choice<>(mode, mode.label))
                    .toList();
    private static final List<OverlayDropdown.Choice<Integer>> NUMERATOR_CHOICES =
            IntStream.rangeClosed(1, 16).boxed()
                    .map(value -> new OverlayDropdown.Choice<>(value, Integer.toString(value)))
                    .toList();
    private static final List<OverlayDropdown.Choice<Integer>> DENOMINATOR_CHOICES =
            List.of(2, 4, 8, 16).stream()
                    .map(value -> new OverlayDropdown.Choice<>(value, Integer.toString(value)))
                    .toList();

    public PianoRollScreen(ItemStack sheet) {
        super(Component.literal("Music Sheet Piano Roll"));
        snapDropdown = new OverlayDropdown.Controller(this::refreshWidgets);
        this.sheet = sheet.copy();
        draftKey = SheetEditController.key(sheet);
        sheetId = sheet.get(Items.SHEET_ID);
        version = sheet.getOrDefault(Items.SHEET_VERSION, -1);
        blankSheet = ItemMusicSheet.isEmptySheet(sheet);
        importRequired = blankSheet;
        highlightInterval = Math.clamp(Byte.toUnsignedInt(
                sheet.getOrDefault(Items.SHEET_HIGHLIGHT_INTERVAL, (byte) 12)), 1, 24);
        previewInstrumentLocked = sheet.getOrDefault(Items.SHEET_PREV_INSTRUMENT_LOCKED, false);
        Byte storedInstrument = sheet.get(Items.SHEET_PREV_INSTRUMENT);
        previewInstrument = storedInstrument == null ? -1 : Byte.toUnsignedInt(storedInstrument);
        if (blankSheet) session = new ScoreEditSession(blankSong());
        else {
            MusicScore song = MusicSheetClientCache.resolve(sheet);
            if (song != null) session = new ScoreEditSession(song);
        }
        SheetDraftStore.Draft retained = SheetEditController.draft(draftKey);
        if (retained != null) {
            conflictVersion = version;
            draftConflict = retained.baseVersion() != version;
            importRequired = retained.importing();
            version = retained.baseVersion();
            session = new ScoreEditSession(retained.song());
            signRequested = retained.sign();
            highlightInterval = retained.highlightInterval();
            previewInstrument = retained.instrument();
            previewInstrumentLocked = retained.instrumentLocked();
            instrumentLockDirty = true;
        }
        if (session != null) tempoBpm = session.initialBpm();
        playback.attach(output);
        midiHandler = new MidiHandler(this::midiNoteOn, this::midiNoteOff);
        midiHandler.setCurrentOctave(0);
    }

    private static MusicScore blankSong() {
        int ppq = 480;
        return new MusicScore(ppq, false, "", ppq * 16L, 440.0F,
                List.of(new MusicScore.TempoChange(0, 500_000)),
                List.of(new MusicScore.TimeSignature(0, 4, 4)), List.of(), List.of(),
                MusicScore.AutomationData.EMPTY,
                List.of(new MusicScore.TrackInfo(0, "", false, 0, List.of())),
                "", false, "", true, true);
    }

    @Override protected void init() {
        shellWidth = Math.max(320, Math.min(MAX_WIDTH, width - MARGIN * 2));
        shellHeight = Math.max(210, Math.min(MAX_HEIGHT, height - MARGIN * 2));
        shellX = (width - shellWidth) / 2;
        shellY = (height - shellHeight) / 2;
        contentX = shellX + INNER_MARGIN;
        contentRight = shellX + shellWidth - INNER_MARGIN;
        headerDividerY = shellY + HEADER_HEIGHT;
        int toolbarY = shellY + 7;
        rulerY = headerDividerY + SECTION_INSET + 1;
        gridX = contentX + KEYS;
        gridY = rulerY + RULER;
        gridWidth = contentRight - gridX;
        gridHeight = shellY + shellHeight - INNER_MARGIN - gridY - VELOCITY_HEIGHT - 5;
        velocityY = gridY + gridHeight + 5;
        if (session != null && pixelsPerTick == 0.0D) {
            ViewState saved = !viewportInitialized && sheetId != null
                    ? VIEW_STATES.get(sheetId) : null;
            pixelsPerTick = saved == null
                    ? DEFAULT_PIXELS_PER_BEAT / session.ppq() : saved.pixelsPerTick();
            if (saved != null) {
                leftTick = saved.leftTick();
                topPitch = saved.topPitch();
                rowHeight = saved.rowHeight();
            }
            viewportInitialized = true;
            clampZoom();
            topPitch = boundedTopPitch(topPitch);
        }
        if (noteEditor != null) {
            playButton = recordButton = null; scrubber = null;
            setInitialFocus(noteEditor.addWidgets(this, font, this::addRenderableWidget, width, height));
            return;
        }
        snapDropdown.beginFrame(shellY + 4, shellY + shellHeight - 4);
        if (gettingSigned) {
            int cancelWidth = 54;
            int finalizeWidth = 54;
            int cancelX = contentRight - cancelWidth;
            int finalizeX = cancelX - CONTROL_GAP - finalizeWidth;
            EditBox title = addRenderableWidget(new EditBox(font, contentX, toolbarY,
                    Math.max(80, finalizeX - CONTROL_GAP - contentX), CONTROL_HEIGHT,
                    Component.translatable("note.title")));
            title.setMaxLength(16);
            title.setValue(signTitle);
            Button finalize = addRenderableWidget(Button.builder(
                            Component.translatable("note.finalizeButton"), ignored -> finalizeSign(title))
                    .bounds(finalizeX, toolbarY, finalizeWidth, CONTROL_HEIGHT).build());
            finalize.active = !title.getValue().trim().isEmpty();
            title.setResponder(value -> {
                signTitle = value;
                finalize.active = !value.trim().isEmpty();
            });
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> {
                        gettingSigned = false;
                        refreshWidgets();
                    }).bounds(cancelX, toolbarY, cancelWidth, CONTROL_HEIGHT).build());
            setInitialFocus(title);
            return;
        }
        if (!previewInstrumentLocked) previewInstrument = currentInstrumentIndex();
        int x = contentX;
        addRenderableWidget(Button.builder(Component.literal("⚙"), ignored -> {
                    finishGesture();
                    settingsOpen = !settingsOpen;
                    snapDropdown.close();
                    refreshWidgets();
                })
                .bounds(x, toolbarY, CONTROL_HEIGHT, CONTROL_HEIGHT).build());
        x += CONTROL_HEIGHT + CONTROL_GAP;
        addRenderableWidget(snapDropdown.button("piano-roll-snap",
                x, toolbarY, 70, CONTROL_HEIGHT, snapMode.label,
                SNAP_CHOICES, snapMode.ordinal(), SNAP_CHOICES.size(), this::setSnapMode));
        x += 70 + CONTROL_GAP;
        playButton = addRenderableWidget(Button.builder(playPauseMessage(), ignored -> playPause())
                .bounds(x, toolbarY, CONTROL_HEIGHT, CONTROL_HEIGHT).build());
        x += CONTROL_HEIGHT + CONTROL_GAP;
        addRenderableWidget(Button.builder(Component.literal("■"), ignored -> stopPlayback())
                .bounds(x, toolbarY, CONTROL_HEIGHT, CONTROL_HEIGHT).build());
        x += CONTROL_HEIGHT + CONTROL_GAP;
        recordButton = addRenderableWidget(Button.builder(recordMessage(), ignored -> recordButton())
                .bounds(x, toolbarY, CONTROL_HEIGHT, CONTROL_HEIGHT).build());
        x += CONTROL_HEIGHT + CONTROL_GAP;
        transportBinding = createTransportBinding();
        int tempoWidth = 76;
        int tempoX = contentRight - tempoWidth;
        int metronomeWidth = 32;
        int metronomeX = tempoX - metronomeWidth - CONTROL_GAP;
        int scrubberWidth = Math.min(120, metronomeX - x - CONTROL_GAP);
        scrubber = scrubberWidth >= 40 ? PlaybackTransportPanel.addScrubber(this::addRenderableWidget,
                x, toolbarY, scrubberWidth, CONTROL_HEIGHT, transportBinding, false) : null;
        addRenderableWidget(Button.builder(metronomeLabel(), ignored -> {
                    metronomeEnabled = !metronomeEnabled;
                    recorder.resetMetronome();
                    refreshWidgets();
                }).bounds(metronomeX, toolbarY, metronomeWidth, CONTROL_HEIGHT).build());
        PlaybackTransportPanel.addTempo(this::addRenderableWidget, this, font,
                tempoX, toolbarY, tempoWidth, CONTROL_HEIGHT, CONTROL_GAP, transportBinding);
        addRenderableWidget(new InstrumentLockButton(contentX + 4,
                shellY + shellHeight + 2));
        if (settingsOpen) addSettingsWidgets();
        else addEditingWidgets(toolbarY + CONTROL_HEIGHT + CONTROL_GAP);
        snapDropdown.addOverlay(this::addRenderableWidget);
    }

    private void addEditingWidgets(int y) {
        addRenderableWidget(snapDropdown.button("piano-roll-tool", contentX, y, 91, CONTROL_HEIGHT,
                tool.label, java.util.Arrays.stream(Tool.values()).map(value ->
                        new OverlayDropdown.Choice<>(value, value.label)).toList(), tool.ordinal(), Tool.values().length,
                value -> { finishGesture(); tool = value; refreshWidgets(); }));
        List<OverlayDropdown.Choice<String>> commands = List.of("Repeat", "Copy", "Cut", "Paste", "Glue", "Chop", "Fit all", "Zoom selection")
                .stream().map(value -> new OverlayDropdown.Choice<>(value, value)).toList();
        addRenderableWidget(snapDropdown.button("piano-roll-edit", contentX + 91 + CONTROL_GAP, y, 64,
                CONTROL_HEIGHT, "Edit", commands, 0, commands.size(), this::editCommand));
        List<OverlayDropdown.Choice<Integer>> rows = List.of(6, 8, 10, 12, 16, 20).stream()
                .map(value -> new OverlayDropdown.Choice<>(value, "Rows " + value)).toList();
        addRenderableWidget(snapDropdown.button("piano-roll-rows", contentX + 155 + CONTROL_GAP * 2, y, 67,
                CONTROL_HEIGHT, "Rows " + rowHeight, rows, List.of(6, 8, 10, 12, 16, 20).indexOf(rowHeight), rows.size(),
                value -> { finishGesture(); rowHeight = value; topPitch = boundedTopPitch(topPitch); refreshWidgets(); }));
    }

    private void editCommand(String command) {
        finishGesture();
        if (session == null) return;
        switch (command) {
            case "Copy" -> clipboard = copyContext(false);
            case "Cut" -> { clipboard = copyContext(true); session.deleteAll(clipboard.stream().map(entry -> entry.note().id()).toList()); }
            case "Paste" -> pasteContext();
            case "Repeat" -> repeatContext();
            case "Glue" -> session.glueSelection();
            case "Chop" -> session.chopSelection(snapStep());
            case "Fit all" -> zoomTo(session.notes(), false);
            case "Zoom selection" -> zoomTo(contextNotes(), true);
            default -> throw new IllegalArgumentException(command);
        }
        refreshTransportScore();
    }

    private List<ScoreEditSession.Note> contextNotes() {
        if (session.selectedCount() > 0) return session.selectedNotes();
        if (timeSelection && rangeStart != rangeEnd) {
            long start = Math.min(rangeStart, rangeEnd), end = Math.max(rangeStart, rangeEnd);
            return session.notes().stream().filter(note -> note.start() >= start && note.start() < end).toList();
        }
        return session.notes();
    }
    private List<ScoreEditSession.ClipboardNote> copyContext(boolean destructive) {
        if (destructive && session.selectedCount() == 0 && !timeSelection) return List.of();
        return session.copyNotes(contextNotes());
    }
    private void repeatContext() {
        List<ScoreEditSession.Note> targets = contextNotes();
        if (targets.isEmpty()) return;
        long first = targets.stream().mapToLong(ScoreEditSession.Note::start).min().orElse(0);
        long end = targets.stream().mapToLong(ScoreEditSession.Note::end).max().orElse(first);
        long destination = end;
        if (timeSelection && rangeStart != rangeEnd) destination = first + Math.abs(rangeEnd - rangeStart);
        List<ScoreEditSession.ClipboardNote> copied = copyContext(false);
        if (destination > MusicScore.MAX_TICKS - (end - first)) return;
        long revision = session.revision();
        session.paste(copied, destination);
        if (revision == session.revision()) return;
        if (timeSelection) { long span = Math.abs(rangeEnd - rangeStart); rangeStart += span; rangeEnd += span; }
        revealSelection();
    }
    private void pasteContext() {
        if (clipboard.isEmpty()) return;
        long first = clipboard.stream().mapToLong(entry -> entry.note().start()).min().orElse(0);
        long anchor = timeSelection ? Math.min(rangeStart, rangeEnd)
                : pasteAnchor >= leftTick && pasteAnchor <= tickAt(contentRight) ? pasteAnchor
                : (long) Math.rint(leftTick / barTicks()) * barTicks() + first % barTicks();
        session.paste(clipboard, anchor); revealSelection();
    }
    private void revealSelection() {
        List<ScoreEditSession.Note> notes = session.selectedNotes();
        if (notes.isEmpty()) return;
        long first = notes.stream().mapToLong(ScoreEditSession.Note::start).min().orElse(0);
        long end = notes.stream().mapToLong(ScoreEditSession.Note::end).max().orElse(first);
        if (first < leftTick) leftTick = first;
        else if (end > leftTick + gridWidth / pixelsPerTick) leftTick = Math.max(0, Math.min(first, end - gridWidth / pixelsPerTick));
        int low = notes.stream().mapToInt(ScoreEditSession.Note::pitch).min().orElse(0);
        int high = notes.stream().mapToInt(ScoreEditSession.Note::pitch).max().orElse(low);
        if (high > topPitch) topPitch = high + 1;
        else if (low < topPitch - gridHeight / (double) rowHeight) topPitch = low + gridHeight / (double) rowHeight - 1;
        clampLeftTick(); topPitch = boundedTopPitch(topPitch);
    }
    private void zoomTo(List<ScoreEditSession.Note> notes, boolean useRange) {
        if (notes.isEmpty()) return;
        long first = notes.stream().mapToLong(ScoreEditSession.Note::start).min().orElse(0);
        long end = notes.stream().mapToLong(ScoreEditSession.Note::end).max().orElse(first + 1);
        if (useRange && timeSelection && rangeStart != rangeEnd) { first = Math.min(rangeStart, rangeEnd); end = Math.max(rangeStart, rangeEnd); }
        pixelsPerTick = (gridWidth - 12.0D) / Math.max(session.ppq(), end - first); clampZoom();
        leftTick = Math.max(0, first - 6 / pixelsPerTick);
        int low = notes.stream().mapToInt(ScoreEditSession.Note::pitch).min().orElse(60);
        int high = notes.stream().mapToInt(ScoreEditSession.Note::pitch).max().orElse(low);
        rowHeight = List.of(20, 16, 12, 10, 8, 6).stream().filter(value -> (high - low + 3) * value <= gridHeight).findFirst().orElse(6);
        topPitch = boundedTopPitch(high + 1); clampLeftTick(); refreshWidgets();
    }
    private boolean inVelocity(double x, double y) {
        return x >= gridX && x < gridX + gridWidth && y >= velocityY + 10 && y < velocityY + VELOCITY_HEIGHT;
    }
    private int velocityAt(double y) {
        return Math.clamp((int) Math.round((velocityY + VELOCITY_HEIGHT - 1 - y) * 127 / (VELOCITY_HEIGHT - 11.0D)), 1, 127);
    }
    private ScoreEditSession.Note nearestVelocityNote(double x) {
        return session.notes().stream().filter(note -> note.end() >= leftTick && note.start() <= tickAt(gridX + gridWidth))
                .min(java.util.Comparator.comparingDouble(note -> Math.abs(xAt(note.start()) - x))).orElse(null);
    }
    private void applyVelocity(double x, double y) {
        int value = minecraft.hasAltDown() ? 100 : minecraft.hasShiftDown() ? strokeVelocity : velocityAt(y);
        Map<Long, Integer> changes = PianoRollStroke.velocities(velocityTargets, strokeTime, strokeVelocity, tickAt(x), value);
        session.setVelocities(changes); defaultVelocity = velocityFeedback = value;
        strokeTime = tickAt(x); strokeVelocity = value;
        if (!changes.isEmpty()) {
            ScoreEditSession.Note note = session.note(changes.keySet().iterator().next());
            if (note != null) audition(note.pitch(), note.velocity());
        }
    }
    private void drawVelocity(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(contentX, velocityY, contentRight, velocityY + VELOCITY_HEIGHT, 0xFF20252B);
        ScoreEditSession.Note hover = inVelocity(mouseX, mouseY) ? nearestVelocityNote(mouseX) : inGrid(mouseX, mouseY) ? noteAtPixel(mouseX, mouseY) : null;
        graphics.text(font, Component.literal("Velocity " + (hover == null ? velocityFeedback : hover.velocity())), contentX + 3, velocityY + 1, 0xFFE6E6E6, false);
        graphics.enableScissor(gridX, velocityY + 10, contentRight, velocityY + VELOCITY_HEIGHT);
        for (ScoreEditSession.Note note : session.notes()) {
            if (note.start() < leftTick || note.start() > tickAt(contentRight)) continue;
            int x = xAt(note.start()), bottom = velocityY + VELOCITY_HEIGHT - 1;
            int top = bottom - (int) Math.round(note.velocity() * (VELOCITY_HEIGHT - 11.0D) / 127);
            graphics.fill(x, top, x + 2, bottom, session.selected(note.id()) ? SELECTED_NOTE_COLOR : DEFAULT_NOTE_COLOR);
        }
        graphics.disableScissor();
    }
    private void openNoteProperties(long id) {
        ScoreEditSession.Note note = session.note(id);
        if (note == null) return;
        stopTypingAuditions();
        noteEditor = new PianoRollNoteEditor(note, values -> {
            boolean valid = session.editNote(id, values.pitch(), values.velocity(), values.start(), values.length());
            if (valid) { touch(session.note(id)); refreshTransportScore(); }
            return valid;
        }, () -> { noteEditor = null; refreshWidgets(); });
        snapDropdown.close();
        refreshWidgets();
    }

    private void addSettingsWidgets() {
        int panelX = contentX + SECTION_INSET;
        int panelRight = contentRight - SECTION_INSET;
        int settingsTabsY = rulerY + SECTION_INSET;
        settingsBodyY = settingsTabsY + CONTROL_HEIGHT + CONTROL_GAP + SECTION_INSET;
        int tabsWidth = Math.min(270, panelRight - panelX);
        int tabWidth = Math.max(1, (tabsWidth - CONTROL_GAP * 2) / 3);
        Button display = addRenderableWidget(Button.builder(Component.literal(
                (settingsTab == SettingsTab.DISPLAY ? "▶ " : "") + "Display"), ignored -> {
                    settingsTab = SettingsTab.DISPLAY;
                    refreshWidgets();
                }).bounds(panelX, settingsTabsY, tabWidth, CONTROL_HEIGHT).build());
        display.active = settingsTab != SettingsTab.DISPLAY;
        Button song = addRenderableWidget(Button.builder(Component.literal(
                (settingsTab == SettingsTab.SONG ? "▶ " : "") + "Song"), ignored -> {
                    settingsTab = SettingsTab.SONG;
                    refreshWidgets();
                }).bounds(panelX + tabWidth + CONTROL_GAP, settingsTabsY,
                    tabWidth, CONTROL_HEIGHT).build());
        song.active = settingsTab != SettingsTab.SONG;
        Button compat = addRenderableWidget(Button.builder(Component.literal(
                (settingsTab == SettingsTab.COMPAT ? "▶ " : "") + "Compat"), ignored -> {
                    settingsTab = SettingsTab.COMPAT;
                    refreshWidgets();
                }).bounds(panelX + (tabWidth + CONTROL_GAP) * 2, settingsTabsY,
                    tabsWidth - tabWidth * 2 - CONTROL_GAP * 2, CONTROL_HEIGHT).build());
        compat.active = settingsTab != SettingsTab.COMPAT;

        int rowX = panelX + SECTION_INSET;
        int rowRight = panelRight - SECTION_INSET;
        if (session == null) return;
        if (settingsTab == SettingsTab.DISPLAY) {
            int styleY = settingsBodyY + 2;
            int styleWidth = Math.max(1, (rowRight - rowX - CONTROL_GAP) / 2);
            addOptionButton("Flat", keyboardStyle == KeyboardStyle.FLAT,
                    rowX, styleY, styleWidth, () -> setKeyboardStyle(KeyboardStyle.FLAT));
            addOptionButton("Regular", keyboardStyle == KeyboardStyle.REGULAR,
                    rowX + styleWidth + CONTROL_GAP, styleY,
                    rowRight - rowX - styleWidth - CONTROL_GAP,
                    () -> setKeyboardStyle(KeyboardStyle.REGULAR));

            int labelsY = styleY + CONTROL_HEIGHT + CONTROL_GAP;
            int labelsWidth = Math.max(1, (rowRight - rowX - CONTROL_GAP * 2) / 3);
            addOptionButton("All Notes", keyLabels == KeyLabels.ALL_NOTES,
                    rowX, labelsY, labelsWidth, () -> setKeyLabels(KeyLabels.ALL_NOTES));
            addOptionButton("Root Notes", keyLabels == KeyLabels.ROOT_NOTES,
                    rowX + labelsWidth + CONTROL_GAP, labelsY, labelsWidth,
                    () -> setKeyLabels(KeyLabels.ROOT_NOTES));
            addOptionButton("None", keyLabels == KeyLabels.NONE,
                    rowX + (labelsWidth + CONTROL_GAP) * 2, labelsY,
                    rowRight - rowX - labelsWidth * 2 - CONTROL_GAP * 2,
                    () -> setKeyLabels(KeyLabels.NONE));
            int optionY = labelsY + CONTROL_HEIGHT + CONTROL_GAP;
            addToggleButton("Left resize", leftResizing, rowX, optionY, styleWidth,
                    () -> { leftResizing = !leftResizing; refreshWidgets(); });
            addToggleButton("Preview during playback", previewDuringPlayback, rowX,
                    optionY + CONTROL_HEIGHT + CONTROL_GAP, rowRight - rowX,
                    () -> { previewDuringPlayback = !previewDuringPlayback; stopAudition(); refreshWidgets(); });
            addToggleButton("Typing (Ctrl+T)", typingEnabled, rowX + styleWidth + CONTROL_GAP,
                    optionY, rowRight - rowX - styleWidth - CONTROL_GAP,
                    () -> { typingEnabled = !typingEnabled; stopTypingAuditions(); refreshWidgets(); });
        } else if (settingsTab == SettingsTab.SONG) {
            int fieldX = rowX + 70;
            int fieldWidth = Math.max(60, rowRight - fieldX);
            int nameY = settingsBodyY + 13;
            M3EEditBox name = addRenderableWidget(new M3EEditBox(this, font,
                    fieldX, nameY, fieldWidth, CONTROL_HEIGHT, Component.literal("Sheet name")));
            name.setMaxLength(16);
            name.setValue(session.song().sourceName());
            name.active = !session.song().signed();
            name.setResponder(value -> {
                if (!session.song().signed()) {
                    session.rename(value);
                            }
            });

            int tuningY = nameY + CONTROL_HEIGHT + CONTROL_GAP;
            M3EEditBox tuning = addRenderableWidget(new M3EEditBox(this, font,
                    fieldX, tuningY, fieldWidth, CONTROL_HEIGHT, Component.literal("Tuning")));
            tuning.setMaxLength(8);
            tuning.setValue(formatTuning(session.song().tuningHz()));
            tuning.setResponder(value -> {
                try {
                    float hz = Float.parseFloat(value.strip());
                    if (hz >= 100.0F && hz <= 1_000.0F) {
                        session.setTuning(hz);
                                    }
                } catch (NumberFormatException ignored) { }
            });

            MusicScore.TimeSignature signature = session.timeSignature();
            int signatureY = tuningY + CONTROL_HEIGHT + CONTROL_GAP;
            int signatureGap = CONTROL_GAP;
            int signatureWidth = Math.max(1,
                    (fieldWidth - signatureGap) / 2);
            addRenderableWidget(snapDropdown.button("piano-roll-numerator",
                    fieldX, signatureY, signatureWidth, CONTROL_HEIGHT,
                    signature.numerator() + " Numerator", NUMERATOR_CHOICES,
                    Math.clamp(signature.numerator() - 1, 0, NUMERATOR_CHOICES.size() - 1),
                    8, value -> setTimeSignature(value, session.timeSignature().denominator())));
            int denominatorIndex = Math.max(0, List.of(2, 4, 8, 16)
                    .indexOf(signature.denominator()));
            addRenderableWidget(snapDropdown.button("piano-roll-denominator",
                    fieldX + signatureWidth + signatureGap, signatureY,
                    fieldWidth - signatureWidth - signatureGap, CONTROL_HEIGHT,
                    signature.denominator() + " Denominator", DENOMINATOR_CHOICES,
                    denominatorIndex, DENOMINATOR_CHOICES.size(),
                    value -> setTimeSignature(session.timeSignature().numerator(), value)));

            int actionY = signatureY + CONTROL_HEIGHT + CONTROL_GAP * 2;
            int actionWidth = Math.max(1, (rowRight - rowX - CONTROL_GAP) / 2);
            Button export = addRenderableWidget(Button.builder(Component.literal("Export"),
                            ignored -> exportSheet())
                    .bounds(rowX, actionY, actionWidth, CONTROL_HEIGHT).build());
            export.active = !session.notes().isEmpty();
            Button sign = addRenderableWidget(Button.builder(
                            Component.translatable("note.signButton"), ignored -> beginSigning())
                    .bounds(rowX + actionWidth + CONTROL_GAP, actionY,
                            rowRight - rowX - actionWidth - CONTROL_GAP, CONTROL_HEIGHT).build());
            sign.active = !session.song().signed();
        } else {
            int formatY = settingsBodyY + 13;
            int formatWidth = Math.max(1, (rowRight - rowX - CONTROL_GAP) / 2);
            addOptionButton("M3E", saveFormat == SaveFormat.M3E,
                    rowX, formatY, formatWidth, () -> setSaveFormat(SaveFormat.M3E));
            addOptionButton("MM", saveFormat == SaveFormat.MM,
                    rowX + formatWidth + CONTROL_GAP, formatY,
                    rowRight - rowX - formatWidth - CONTROL_GAP,
                    () -> setSaveFormat(SaveFormat.MM));
        }
    }

    private static String formatTuning(float hz) {
        return hz == Math.round(hz) ? Integer.toString(Math.round(hz))
                : Float.toString(hz);
    }

    private void addOptionButton(String label, boolean selected, int x, int y,
                                 int width, Runnable action) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run())
                .bounds(x, y, width, CONTROL_HEIGHT).build());
        button.active = !selected;
    }

    private void addToggleButton(String label, boolean selected, int x, int y, int width, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label + (selected ? ": On" : ": Off")), ignored -> action.run())
                .bounds(x, y, width, CONTROL_HEIGHT).build());
    }

    @Override public void tick() {
        if (GLFW.glfwGetWindowAttrib(minecraft.getWindow().handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_FALSE) {
            finishGesture(); stopAudition(); stopTypingAuditions(); showNoteCursor(false, false);
        }
        if (draftConflict && !conflictShown) {
            conflictShown = true;
            minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(useDraft -> {
                if (useDraft) {
                    version = conflictVersion;
                } else {
                    SheetEditController.discard(draftKey);
                    MusicScore latest = MusicSheetClientCache.resolve(sheet);
                    if (latest != null) adopt(sheet, latest);
                    else { session = null; importRequired = blankSheet; }
                    instrumentLockDirty = false;
                    signRequested = false;
                    version = conflictVersion;
                }
                draftConflict = false;
                minecraft.gui.setScreen(this);
            }, Component.literal("Sheet changed since this draft"),
                    Component.literal("Use the retained draft to replace the newer sheet? Choose No to discard the draft.")));
            return;
        }
        ((MidiInputRefreshAccess) midiHandler).m3e$refreshMidiInputs(false);
        tickCountIn();
        if (replacementPending && !saveSubmitted && minecraft.player != null) {
            ItemStack held = minecraft.player.getMainHandItem();
            MusicScore replacement = MusicSheetClientCache.resolve(held);
            UUID heldId = held.get(Items.SHEET_ID);
            int heldVersion = held.getOrDefault(Items.SHEET_VERSION, -1);
            if (replacement != null
                    && (replacementSheetId == null || replacementSheetId.equals(heldId))
                    && (replacementVersion < 0 || heldVersion >= replacementVersion)) {
                adopt(held, replacement);
            }
        } else if (session == null) {
            MusicScore song = MusicSheetClientCache.resolve(sheet);
            if (song != null) {
                session = new ScoreEditSession(song);
                tempoBpm = session.initialBpm();
                clearWidgets();
                init();
            }
        }
        if (playButton != null && session != null) {
            playButton.setMessage(playPauseMessage());
        }
        if (recordButton != null) recordButton.setMessage(recordMessage());
        if (scrubber != null) scrubber.refresh();
        tickMetronome();
    }

    private ScoreEditSession session() {
        if (session == null) throw new IllegalStateException("score is still loading");
        return session;
    }

    private long snapStep() {
        long beat = beatTicks();
        return switch (snapMode) {
            case LINE, CELL -> lineStep();
            case NONE -> 1L;
            case SIXTH_STEP -> fraction(beat, 24);
            case QUARTER_STEP -> fraction(beat, 16);
            case THIRD_STEP -> fraction(beat, 12);
            case HALF_STEP -> fraction(beat, 8);
            case STEP, QUARTER_BEAT -> fraction(beat, 4);
            case SIXTH_BEAT -> fraction(beat, 6);
            case THIRD_BEAT -> fraction(beat, 3);
            case HALF_BEAT -> fraction(beat, 2);
            case BEAT -> beat;
            case BAR -> barTicks();
        };
    }

    private static long fraction(long ticks, int denominator) {
        return Math.max(1L, Math.round(ticks / (double) denominator));
    }

    private long lineStep() {
        long beat = beatTicks();
        for (int division : LINE_DIVISIONS) {
            long step = fraction(beat, division);
            if (step * pixelsPerTick >= 8.0D) return step;
        }
        long step = beat;
        while (step * pixelsPerTick < 8.0D && step <= Long.MAX_VALUE / 2L) step *= 2L;
        return step;
    }

    private MusicScore.TimeSignature timeSignature() {
        return session().timeSignature();
    }

    private long beatTicks() {
        return Math.max(1L, Math.round(session().ppq() * 4.0D
                / timeSignature().denominator()));
    }

    private long barTicks() {
        return Math.multiplyExact(beatTicks(), timeSignature().numerator());
    }

    private long snapTick(double tick) {
        double bounded = Math.max(0.0D, tick);
        if (snapBypassed()) return Math.round(bounded);
        long step = snapStep();
        double units = bounded / step;
        return Math.max(0L, (snapMode == SnapMode.CELL
                ? (long) Math.floor(units) : Math.round(units)) * step);
    }

    private long newNoteLength() {
        return lastTouchedLength > 0 ? lastTouchedLength : Math.max(1L, session.ppq() / 4L);
    }


    private boolean snapBypassed() {
        boolean maximumZoomLineMode = pixelsPerTick >= 2.0D - 1.0E-9D
                && (snapMode == SnapMode.LINE || snapMode == SnapMode.CELL);
        return minecraft.hasAltDown() || snapMode == SnapMode.NONE || maximumZoomLineMode;
    }
    private double tickAt(double x) { return leftTick + (x - gridX) / pixelsPerTick; }
    private double continuousPitchAt(double y) { return (rowOrigin() - y) / (double) rowHeight; }
    private int pitchAt(double y) {
        int pixel = (int) Math.floor(y);
        return Math.clamp(-Math.floorDiv(pixel - rowOrigin(), rowHeight),
                IItemInstrument.MIN_NOTE, IItemInstrument.MAX_NOTE);
    }
    private int xAt(long tick) { return gridX + (int) Math.round((tick - leftTick) * pixelsPerTick); }
    private int rowOrigin() { return gridY + (int) Math.round(topPitch * rowHeight); }

    private int rowTop(int pitch) {
        return rowOrigin() - pitch * rowHeight;
    }

    private int rowBottom(int pitch) { return rowTop(pitch) + rowHeight; }

    private int keyboardPitchAt(double x, double y) {
        int chromatic = pitchAt(y);
        if (keyboardStyle != KeyboardStyle.REGULAR) return chromatic;
        int blackRight = contentX + Math.max(1, KEYS * 2 / 3);
        int highest = Math.min(IItemInstrument.MAX_NOTE, (int) Math.ceil(topPitch) + 2);
        int lowest = Math.max(IItemInstrument.MIN_NOTE, pitchAt(gridY + gridHeight) - 2);
        if (x < blackRight) return chromatic;
        for (int pitch = highest; pitch >= lowest; pitch--) {
            if (!isBlack(pitch) && y >= regularWhiteTop(pitch) && y < regularWhiteBottom(pitch)) {
                return pitch;
            }
        }
        return chromatic;
    }
    private boolean inGrid(double x, double y) {
        return x >= gridX && x < gridX + gridWidth && y >= gridY && y < gridY + gridHeight;
    }
    private boolean inKeyboard(double x, double y) {
        return x >= contentX && x < gridX
                && y >= gridY && y < gridY + gridHeight;
    }

    private boolean inRuler(double x, double y) {
        return x >= gridX && x < gridX + gridWidth
                && y >= rulerY && y < gridY;
    }

    private long contentEndTick() {
        if (extentSession != session) { extentSession = session; contentExtent = 0; }
        contentExtent = Math.max(contentExtent, session().song().lengthTick());
        long bar = barTicks();
        return Math.max(bar * 4L, Math.min(MusicScore.MAX_TICKS, contentExtent + bar));
    }

    private double minimumZoom() {
        return gridWidth / (double) Math.max(1L, contentEndTick());
    }

    private void clampZoom() {
        if (session == null || gridWidth <= 0) return;
        double minimum = minimumZoom();
        double requested = Math.clamp(pixelsPerTick, minimum, 2.0D);
        pixelsPerTick = requested <= minimum * (1.0D + 1.0E-9D)
                ? minimum : quantizedZoom(requested);
        clampLeftTick();
    }

    private void clampLeftTick() {
        leftTick = Math.max(0.0D, leftTick);
    }

    private double quantizedZoom(double requested) {
        long unit = barTicks();
        while (unit * requested < 1.0D && unit <= Long.MAX_VALUE / 2L) unit *= 2L;
        double pixels = Math.max(1.0D, Math.ceil(unit * requested - 1.0E-9D));
        return Math.clamp(pixels / unit, Double.MIN_NORMAL, 2.0D);
    }

    private List<PianoRollGeometry.Rect> visibleNotes() {
        return geometry.visible(session, leftTick, pixelsPerTick, topPitch, rowHeight, gridX, gridY, gridWidth, gridHeight);
    }
    private PianoRollGeometry.Hit hitAt(double x, double y) {
        visibleNotes(); return geometry.hit(x, y, leftResizing, tool == Tool.DRAW);
    }
    private void touch(ScoreEditSession.Note note) {
        if (note == null) return;
        lastTouchedLength = note.length(); defaultVelocity = velocityFeedback = note.velocity(); defaultTrack = note.track();
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (replacementPending || saveSubmitted || gesture != Gesture.NONE) return true;
        if (noteEditor != null) {
            if (noteEditor.contains(event.x(), event.y())) super.mouseClicked(event, doubled);
            return true;
        }
        if (snapDropdown.mouseClicked(event) || snapDropdown.closeFromOutsideClick(event)) return true;
        if (settingsOpen || session == null) return super.mouseClicked(event, doubled);
        if (event.button() == 0 && inVelocity(event.x(), event.y())) {
            velocityTargets = session.selectedCount() > 0 ? session.selectedNotes() : session.notes();
            begin(Gesture.VELOCITY, event); strokeTime = pressTick; strokeVelocity = velocityAt(event.y());
            applyVelocity(event.x(), event.y()); refreshTransportScore(); return true;
        }
        if (event.button() == 0 && inKeyboard(event.x(), event.y())) {
            begin(Gesture.AUDITION, event); audition(keyboardPitchAt(event.x(), event.y())); return true;
        }
        if (event.button() == 0 && inRuler(event.x(), event.y())) {
            if (minecraft.hasControlDown()) {
                begin(Gesture.RANGE, event); rangeStart = rangeEnd = snapTick(pressTick); timeSelection = true;
            } else { timeSelection = false; pasteAnchor = snapTick(tickAt(event.x())); begin(Gesture.SCRUB, event); scrubAt(event.x()); }
            return true;
        }
        if (event.button() == 2 && (inGrid(event.x(), event.y()) || inKeyboard(event.x(), event.y()))) {
            begin(Gesture.PAN, event); panLeft = leftTick; panTop = topPitch; return true;
        }
        if (!inGrid(event.x(), event.y())) return super.mouseClicked(event, doubled);
        PianoRollGeometry.Hit hit = hitAt(event.x(), event.y());
        ScoreEditSession.Note note = hit == null ? null : hit.note();
        if (event.button() == 1 || tool == Tool.DELETE) {
            if (event.button() != 0 && event.button() != 1) return false;
            begin(Gesture.ERASE, event); eraseAlong(event.x(), event.y(), event.x(), event.y()); refreshTransportScore(); return true;
        }
        if (event.button() != 0) return false;
        if (tool == Tool.SLICE && !minecraft.hasControlDown()) {
            begin(Gesture.SLICE, event); sliceEndTime = pressTick; sliceEndPitch = pressContinuousPitch; return true;
        }
        if (minecraft.hasControlDown() || tool == Tool.SELECT) {
            if (note != null && minecraft.hasControlDown()) session.toggle(note.id());
            else {
                toggleMarquee = minecraft.hasShiftDown(); if (!toggleMarquee) session.clearSelection();
                begin(Gesture.MARQUEE, event);
            }
            return true;
        }
        touch(note);
        if (tool == Tool.PAINT) {
            begin(Gesture.PAINT, event); strokeLength = newNoteLength(); strokeTime = pressTick; strokePitch = pressContinuousPitch;
            paintAlong(strokeTime, strokePitch); refreshTransportScore(); return true;
        }
        if (note == null) {
            boolean stretch = GLFW.glfwGetKey(minecraft.getWindow().handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
            begin(stretch ? Gesture.RESIZE : Gesture.MOVE, event);
            long length = stretch ? snapStep() : newNoteLength(), start = snapTick(pressTick);
            length = Math.min(length, MusicScore.MAX_TICKS - start);
            gestureNote = session.add(defaultTrack, pitchAt(event.y()), defaultVelocity, start, length);
            if (gestureNote < 0) { session.cancelEdit(); resetGesture(); refreshTransportScore(); return true; }
            resizeEdge = ResizeEdge.END; copyGesture = false;
            if (gesture == Gesture.RESIZE) drag = new PianoRollDrag(start + length, pressContinuousPitch);
            touch(session.note(gestureNote)); audition(pitchAt(event.y()), defaultVelocity); refreshTransportScore(); return true;
        }
        if (!session.selected(note.id())) session.selectOnly(note.id());
        gestureNote = note.id();
        boolean edge = hit.part() != PianoRollGeometry.Part.BODY;
        resizeEdge = hit.part() == PianoRollGeometry.Part.START ? ResizeEdge.START : ResizeEdge.END;
        copyGesture = !edge && minecraft.hasShiftDown();
        begin(edge ? Gesture.RESIZE : Gesture.MOVE, event);
        if (!edge && session.selectedCount() == 1) audition(note.pitch(), note.velocity());
        if (doubled && !edge) { finishGesture(); openNoteProperties(note.id()); }
        return true;
    }
    private EdgeHit noteEdgeAt(double x, double y) {
        PianoRollGeometry.Hit hit = hitAt(x, y);
        return hit == null || hit.part() == PianoRollGeometry.Part.BODY ? null
                : new EdgeHit(hit.note(), hit.part() == PianoRollGeometry.Part.START ? ResizeEdge.START : ResizeEdge.END);
    }
    private ScoreEditSession.Note noteAtPixel(double x, double y) {
        PianoRollGeometry.Hit hit = hitAt(x, y); return hit == null ? null : hit.note();
    }
    private void begin(Gesture next, MouseButtonEvent event) {
        gesture = next; gestureButton = event.button();
        pressX = currentX = dragPointerX = event.x(); pressY = currentY = dragPointerY = event.y();
        pressTick = tickAt(event.x()); pressPitch = pitchAt(event.y()); pressContinuousPitch = continuousPitchAt(event.y());
        drag = new PianoRollDrag(pressTick, pressContinuousPitch, snapMode == SnapMode.CELL && !snapBypassed() ? snapStep() : 0);
        autoScrollNanos = System.nanoTime(); gestureDragged = false;
        if (next == Gesture.MOVE || next == Gesture.RESIZE || next == Gesture.PAINT
                || next == Gesture.ERASE || next == Gesture.SLICE || next == Gesture.VELOCITY) session.beginEdit();
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (gesture == Gesture.NONE) return super.mouseDragged(event, deltaX, deltaY);
        if (event.button() == gestureButton) updatePointer(event.x(), event.y());
        return true;
    }
    private void updatePointer(double rawX, double rawY) {
        gestureDragged |= Math.abs(rawX - pressX) >= 1 || Math.abs(rawY - pressY) >= 1;
        double priorX = currentX, priorY = currentY;
        dragPointerX = rawX; dragPointerY = rawY;
        currentX = Math.clamp(rawX, gridX, gridX + gridWidth - 1.0D);
        currentY = Math.clamp(rawY, gridY, gridY + gridHeight - 1.0D);
        long revision = session.revision();
        if (gesture == Gesture.PAN) {
            leftTick = Math.max(0, panLeft - (rawX - pressX) / pixelsPerTick); clampLeftTick();
            topPitch = boundedTopPitch(panTop + (rawY - pressY) / rowHeight);
        } else if (gesture == Gesture.AUDITION) audition(keyboardPitchAt(Math.clamp(rawX, contentX, gridX - 1.0D), currentY));
        else if (gesture == Gesture.SCRUB) scrubAt(currentX);
        else if (gesture == Gesture.RANGE) rangeEnd = snapTick(tickAt(currentX));
        else if (gesture == Gesture.ERASE) eraseAlong(priorX, priorY, currentX, currentY);
        else if (gesture == Gesture.PAINT) paintAlong(tickAt(currentX), continuousPitchAt(currentY));
        else if (gesture == Gesture.VELOCITY) applyVelocity(currentX, rawY);
        else if (gesture == Gesture.SLICE) { sliceEndTime = tickAt(currentX); sliceEndPitch = continuousPitchAt(currentY); }
        else if (gestureDragged && (gesture == Gesture.MOVE || gesture == Gesture.RESIZE)) applyDrag();
        if (session.revision() != revision) refreshTransportScore();
    }
    private void applyDrag() {
        ScoreEditSession.Note note = session.note(gestureNote);
        if (note == null) return;
        long anchor = gesture == Gesture.MOVE || resizeEdge == ResizeEdge.START ? note.start() : note.end();
        long ticks = drag.timeDelta(anchor, tickAt(currentX), minecraft.hasControlDown(), this::snapTick);
        int pitch = gesture == Gesture.MOVE ? drag.pitchDelta(continuousPitchAt(currentY), minecraft.hasShiftDown()) : 0;
        if (gesture == Gesture.MOVE) {
            ticks = session.boundedTickDelta(ticks); pitch = session.boundedPitchDelta(pitch);
            int anchorIndex = session.selectedNotes().indexOf(note);
            session.moveSelection(ticks, pitch, copyGesture);
            if (copyGesture && (ticks != 0 || pitch != 0) && anchorIndex >= 0 && anchorIndex < session.selectedCount()) {
                ScoreEditSession.Note moved = session.selectedNotes().get(anchorIndex);
                if (moved.id() != gestureNote) { gestureNote = moved.id(); copyGesture = false; }
            }
        } else {
            long minimum = snapBypassed() ? 1L : snapStep();
            if (resizeEdge == ResizeEdge.START) session.resizeSelectionStart(gestureNote, anchor + ticks, minimum);
            else session.resizeSelectionEnd(gestureNote, anchor + ticks, minimum);
        }
        ScoreEditSession.Note actual = session.note(gestureNote);
        if (actual == null) return;
        long actualTicks = gesture == Gesture.MOVE || resizeEdge == ResizeEdge.START
                ? actual.start() - note.start() : actual.end() - note.end();
        drag.applied(actualTicks, actual.pitch() - note.pitch()); touch(actual);
        if (gesture == Gesture.MOVE && session.selectedCount() == 1) audition(actual.pitch(), actual.velocity());
    }
    private void paintAlong(double time, double pitch) {
        long step = snapBypassed() ? strokeLength : snapStep();
        long previousNote = gestureNote;
        PianoRollStroke.paint(strokeTime, strokePitch, time, pitch, step, (tick, value) -> {
            if (value < MusicScore.MIN_PITCH || value > MusicScore.MAX_PITCH) return;
            long id = session.paint(defaultTrack, value, defaultVelocity, tick, strokeLength);
            if (id >= 0) { gestureNote = id; touch(session.note(id)); }
        });
        if (gestureNote != previousNote) {
            ScoreEditSession.Note last = session.note(gestureNote);
            audition(last.pitch(), last.velocity());
        }
        strokeTime = time; strokePitch = pitch;
    }
    private void eraseAlong(double x1, double y1, double x2, double y2) {
        visibleNotes(); session.deleteAll(geometry.swept(x1, y1, x2, y2).stream().map(ScoreEditSession.Note::id).toList());
    }
    private void advanceDragAutoScroll() {
        if (!gestureDragged || gesture == Gesture.NONE || gesture == Gesture.PAN || gesture == Gesture.AUDITION || gesture == Gesture.VELOCITY) {
            autoScrollNanos = 0; return;
        }
        long now = System.nanoTime();
        double elapsed = autoScrollNanos == 0 ? 0 : Math.clamp((now - autoScrollNanos) / 1_000_000_000.0D, 0, 0.05D);
        autoScrollNanos = now;
        double horizontal = edgeStrength(dragPointerX, gridX, gridX + gridWidth);
        double vertical = gesture == Gesture.MARQUEE || gesture == Gesture.MOVE || gesture == Gesture.PAINT
                ? edgeStrength(dragPointerY, gridY, gridY + gridHeight) : 0;
        if (horizontal == 0 && vertical == 0) return;
        leftTick += horizontal * gridWidth / pixelsPerTick * 0.45D * elapsed; clampLeftTick();
        topPitch = boundedTopPitch(topPitch - vertical * 12 * elapsed); updatePointer(dragPointerX, dragPointerY);
    }
    private static double edgeStrength(double position, double start, double end) {
        if (position < start + 3) return -Math.min(4, (start + 3 - position) / 12);
        if (position > end - 3) return Math.min(4, (position - end + 3) / 12);
        return 0;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (gesture == Gesture.NONE) return super.mouseReleased(event);
        if (event.button() != gestureButton) return true;
        updatePointer(event.x(), event.y()); finishGesture(); return true;
    }
    private void resetGesture() {
        gesture = Gesture.NONE; gestureButton = -1; autoScrollNanos = 0; gestureDragged = false;
        gestureNote = -1; copyGesture = false; toggleMarquee = false; velocityTargets = List.of();
    }
    private void finishGesture() {
        if (gesture == Gesture.NONE || session == null) return;
        if (gesture == Gesture.MARQUEE) {
            long a = Math.max(0, (long) Math.floor(Math.min(pressTick, tickAt(currentX))));
            long b = Math.max(a + 1, (long) Math.ceil(Math.max(pressTick, tickAt(currentX))));
            int low = Math.min(pressPitch, pitchAt(currentY)), high = Math.max(pressPitch, pitchAt(currentY));
            if (toggleMarquee) session.toggleIn(a, b, low, high); else session.selectIn(a, b, low, high, false);
        } else if (gesture == Gesture.SLICE && gestureDragged) {
            for (ScoreEditSession.Note note : session.notes()) {
                double tick = PianoRollStroke.sliceTime(note.pitch(), pressTick, pressContinuousPitch, sliceEndTime, sliceEndPitch);
                if (tick >= 0) session.slice(note.id(), snapTick(tick));
            }
        }
        session.commitEdit(); stopAudition(); resetGesture(); refreshTransportScore();
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (noteEditor != null) return true;
        if (gesture == Gesture.NONE && snapDropdown.mouseScrolled(mouseX, mouseY, vertical)) return true;
        if (settingsOpen) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        if ((!inGrid(mouseX, mouseY) && !inKeyboard(mouseX, mouseY) && !inVelocity(mouseX, mouseY)) || session == null)
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        if (minecraft.hasAltDown() && vertical != 0) {
            if (gesture != Gesture.NONE) return true;
            List<ScoreEditSession.Note> targets = session.selectedNotes();
            if (targets.isEmpty()) {
                ScoreEditSession.Note note = inVelocity(mouseX, mouseY) ? nearestVelocityNote(mouseX) : noteAtPixel(mouseX, mouseY);
                if (note == null && inGrid(mouseX, mouseY)) { visibleNotes(); note = geometry.nearest(mouseX, mouseY); }
                if (note != null) targets = List.of(note);
            }
            Map<Long, Integer> values = new LinkedHashMap<>();
            int delta = (int) Math.copySign(Math.max(1, Math.round(Math.abs(vertical))) * (minecraft.hasControlDown() ? 1 : 4), vertical);
            for (ScoreEditSession.Note note : targets) values.put(note.id(), note.velocity() + delta);
            session.setVelocities(values); if (!targets.isEmpty()) touch(session.note(targets.getLast().id())); refreshTransportScore();
        } else if (minecraft.hasControlDown() && inKeyboard(mouseX, mouseY)) {
            double anchor = continuousPitchAt(mouseY);
            int[] rows = {6, 8, 10, 12, 16, 20}; int index = java.util.Arrays.binarySearch(rows, rowHeight);
            rowHeight = rows[Math.clamp(index + (vertical > 0 ? 1 : -1), 0, rows.length - 1)];
            topPitch = boundedTopPitch(anchor + (mouseY - gridY) / rowHeight);
        } else if (minecraft.hasControlDown()) {
            double anchor = tickAt(mouseX), previous = pixelsPerTick;
            pixelsPerTick *= Math.pow(1.15D, vertical); clampZoom();
            if (vertical > 0 && pixelsPerTick <= previous) pixelsPerTick = nextZoomIn(previous);
            else if (vertical < 0 && pixelsPerTick >= previous) pixelsPerTick = nextZoomOut(previous);
            clampZoom(); leftTick = Math.max(0, anchor - (mouseX - gridX) / pixelsPerTick); clampLeftTick();
        } else if (minecraft.hasShiftDown() || horizontal != 0) {
            leftTick = Math.max(0, leftTick - (vertical + horizontal) * visibleGridStep() * 1.5D); clampLeftTick();
        } else topPitch = boundedTopPitch(topPitch + vertical * 1.5D);
        if (gesture != Gesture.NONE) updatePointer(dragPointerX, dragPointerY);
        return true;
    }

    private double nextZoomIn(double current) {
        long bar = barTicks();
        double barPixels = current * bar;
        return Math.min(2.0D, (barPixels < 1.0D ? barPixels * 2.0D
                : Math.floor(barPixels) + 1.0D) / bar);
    }

    private double nextZoomOut(double current) {
        long bar = barTicks();
        double barPixels = current * bar;
        return Math.max(minimumZoom(), (barPixels <= 1.0D ? barPixels / 2.0D
                : Math.max(1.0D, Math.ceil(barPixels) - 1.0D)) / bar);
    }

    private double boundedTopPitch(double value) {
        double minimum = Math.min(IItemInstrument.MAX_NOTE,
                IItemInstrument.MIN_NOTE + gridHeight / (double) rowHeight);
        return Math.clamp(value, minimum, IItemInstrument.MAX_NOTE);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (noteEditor != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) { noteEditor = null; refreshWidgets(); return true; }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { noteEditor.submit(); return true; }
            return super.keyPressed(event);
        }
        if (gesture != Gesture.NONE) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) return true;
            if (event.key() == GLFW.GLFW_KEY_LEFT_CONTROL || event.key() == GLFW.GLFW_KEY_RIGHT_CONTROL
                    || event.key() == GLFW.GLFW_KEY_LEFT_SHIFT || event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT
                    || event.key() == GLFW.GLFW_KEY_LEFT_ALT || event.key() == GLFW.GLFW_KEY_RIGHT_ALT) {
                updatePointer(dragPointerX, dragPointerY); return true;
            }
            finishGesture();
        }
        if ((replacementPending || saveSubmitted) && event.key() != GLFW.GLFW_KEY_ESCAPE) return true;
        if (ViewportClippedWidget.unwrap(getFocused()) instanceof EditBox) {
            return super.keyPressed(event);
        }
        if (settingsOpen && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            settingsOpen = false;
            refreshWidgets();
            return true;
        }
        if (settingsOpen) return super.keyPressed(event);
        if (session == null) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            session.deleteSelection(); refreshTransportScore(); clampZoom(); return true;
        }
        if (minecraft.hasControlDown() && event.key() == GLFW.GLFW_KEY_Z) {
            if (minecraft.hasShiftDown()) session.redo(); else session.undo(); tempoBpm = session.initialBpm(); refreshTransportScore(); clampZoom(); return true;
        }
        if (minecraft.hasControlDown() && event.key() == GLFW.GLFW_KEY_Y) {
            session.redo(); tempoBpm = session.initialBpm(); refreshTransportScore(); clampZoom(); return true;
        }
        if (minecraft.hasControlDown() && event.key() == GLFW.GLFW_KEY_D) {
            session.clearSelection();

            return true;
        }
        if (minecraft.hasControlDown()
                && (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN)) {
            nudgeSelection(0L, event.key() == GLFW.GLFW_KEY_UP ? 12 : -12);
            return true;
        }
        if (minecraft.hasShiftDown()) {
            if (event.key() == GLFW.GLFW_KEY_LEFT || event.key() == GLFW.GLFW_KEY_RIGHT) {
                long step = minecraft.hasAltDown() ? Math.max(1, (long) Math.rint(1 / pixelsPerTick)) : snapStep();
                nudgeSelection(event.key() == GLFW.GLFW_KEY_RIGHT ? step : -step, 0);
                revealSelection();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN) {
                nudgeSelection(0L, event.key() == GLFW.GLFW_KEY_UP ? 1 : -1);
                return true;
            }
        }
        if (minecraft.hasControlDown() && event.key() == GLFW.GLFW_KEY_B) {
            repeatContext();

            refreshTransportScore();
            clampZoom();
            return true;
        }
        if (minecraft.hasControlDown() && event.key() == GLFW.GLFW_KEY_A) {
            session.selectAll();

            return true;
        }
        if (minecraft.hasControlDown() && event.key() == GLFW.GLFW_KEY_C) {
            clipboard = copyContext(false); return true;
        }
        if (minecraft.hasControlDown() && event.key() == GLFW.GLFW_KEY_X) { editCommand("Cut"); return true; }
        if (minecraft.hasControlDown() && event.key() == GLFW.GLFW_KEY_V) { editCommand("Paste"); return true; }
        if (minecraft.hasControlDown() && event.key() == GLFW.GLFW_KEY_T) {
            typingEnabled = !typingEnabled; stopTypingAuditions(); refreshWidgets(); return true;
        }
        if (!typingEnabled && !minecraft.hasControlDown() && !minecraft.hasAltDown() && !minecraft.hasShiftDown()) {
            Tool chosen = switch (event.key()) {
                case GLFW.GLFW_KEY_P -> Tool.DRAW; case GLFW.GLFW_KEY_B -> Tool.PAINT;
                case GLFW.GLFW_KEY_E -> Tool.SELECT; case GLFW.GLFW_KEY_C -> Tool.SLICE; case GLFW.GLFW_KEY_D -> Tool.DELETE;
                default -> null;
            };
            if (chosen != null) { tool = chosen; refreshWidgets(); return true; }
            if (event.key() == GLFW.GLFW_KEY_LEFT || event.key() == GLFW.GLFW_KEY_RIGHT) {
                leftTick = Math.max(0, leftTick + (event.key() == GLFW.GLFW_KEY_RIGHT ? 1 : -1) * visibleGridStep()); clampLeftTick(); return true;
            }
            if (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN) {
                topPitch = boundedTopPitch(topPitch + (event.key() == GLFW.GLFW_KEY_UP ? 1 : -1)); return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_SPACE) { playPause(); return true; }
        if (typingEnabled && handleTypingKeyPressed(event)) return true;
        return super.keyPressed(event);
    }

    @Override public boolean keyReleased(KeyEvent event) {
        if (gesture != Gesture.NONE) updatePointer(dragPointerX, dragPointerY);
        Integer pitch = heldTypingNotes.remove(event.key());
        if (pitch != null) {
            if (!heldTypingNotes.containsValue(pitch)) inputNoteOff(pitch);
            return true;
        }
        InstrumentOptions.KeyboardLayout layout = InstrumentOptions.keyboardLayout();
        if (typingEnabled && KeyboardKeyMap.consumesUnresolvedKey(layout, event.key(), event.scancode(),
                0, IItemInstrument.TOTAL_NOTES)) return true;
        return super.keyReleased(event);
    }

    private boolean handleTypingKeyPressed(KeyEvent event) {
        if (ViewportClippedWidget.unwrap(getFocused()) instanceof EditBox editBox
                && editBox.isFocused()) return false;
        InstrumentOptions.KeyboardLayout layout = InstrumentOptions.keyboardLayout();
        int shift = KeyboardKeyMap.octaveShift(layout, event.key(), event.modifiers(),
                0, IItemInstrument.TOTAL_NOTES);
        if (shift != 0) {
            stopTypingAuditions();
            typingOctave = Math.clamp(typingOctave + shift, 0, 7);
            return true;
        }
        KeyboardKeyMap.ResolvedBinding resolved = KeyboardKeyMap.resolve(layout,
                event.key(), event.modifiers(), typingOctave,
                InstrumentOptions.octaveOrigin(), 0, IItemInstrument.TOTAL_NOTES, false);
        if (resolved != null && !resolved.control()) {
            int pitch = IItemInstrument.idToNote(resolved.noteId());
            if (!heldTypingNotes.containsKey(event.key())) {
                boolean alreadyHeld = heldTypingNotes.containsValue(pitch)
                        || heldMidiNotes.contains(pitch);
                if (!alreadyHeld) {
                    inputNoteOn(pitch, InstrumentPerformanceControls.keyboardVelocity());
                }
                heldTypingNotes.put(event.key(), pitch);
            }
            return true;
        }
        return KeyboardKeyMap.consumesUnresolvedKey(layout, event.key(), event.scancode(),
                0, IItemInstrument.TOTAL_NOTES);
    }

    private void stopTypingAuditions() {
        for (int pitch : new LinkedHashSet<>(heldTypingNotes.values())) {
            heldTypingNotes.entrySet().removeIf(entry -> entry.getValue() == pitch);
            inputNoteOff(pitch);
        }
        heldTypingNotes.clear();
    }

    private void nudgeSelection(long tickDelta, int pitchDelta) {
        session.moveSelection(tickDelta, pitchDelta, false);
        refreshTransportScore();
        clampZoom(); revealSelection();
    }

    private void setSnapMode(SnapMode mode) {
        if (mode == null || mode == snapMode) return;
        snapMode = mode;
        refreshWidgets();
    }

    private void setTimeSignature(int numerator, int denominator) {
        if (session == null || numerator < 1 || numerator > 16
                || !List.of(2, 4, 8, 16).contains(denominator)) return;
        session.setTimeSignature(numerator, denominator);
        recorder.resetMetronome();
        refreshTransportScore();
        clampZoom();
        refreshWidgets();
    }

    private void setKeyboardStyle(KeyboardStyle style) {
        if (style == null || style == keyboardStyle) return;
        keyboardStyle = preferredKeyboardStyle = style;
        refreshWidgets();
    }

    private void setKeyLabels(KeyLabels labels) {
        if (labels == null || labels == keyLabels) return;
        keyLabels = preferredKeyLabels = labels;
        refreshWidgets();
    }

    private void setSaveFormat(SaveFormat format) {
        if (format == null || format == saveFormat) return;
        saveFormat = preferredSaveFormat = format;
        refreshWidgets();
    }

    private Component metronomeLabel() {
        return Component.literal("Met.").withColor(metronomeEnabled ? 0x9FD89F : 0xE6E6E6);
    }

    private void refreshWidgets() {
        clearWidgets();
        init();
    }

    private IItemInstrument instrument() {
        IItemInstrument assigned = session == null ? null : InstrumentChoiceCatalog.resolve(session.instrumentId());
        if (assigned == null && previewInstrumentLocked
                && previewInstrument >= 0 && previewInstrument < Items.INSTRUMENTS.size()) {
            assigned = Items.INSTRUMENTS.get(previewInstrument);
        }
        if (assigned == null && minecraft != null && minecraft.player != null
                && minecraft.player.getOffhandItem().getItem() instanceof IItemInstrument offhand) {
            assigned = offhand;
        }
        return assigned != null ? assigned : (IItemInstrument) Items.HARP_MC;
    }

    private int currentInstrumentIndex() {
        if (minecraft != null && minecraft.player != null
                && minecraft.player.getOffhandItem().getItem() instanceof IItemInstrument instrument) {
            return Math.clamp(instrument.getInstrumentId(), 0, Items.INSTRUMENTS.size() - 1);
        }
        if (previewInstrument >= 0 && previewInstrument < Items.INSTRUMENTS.size()) {
            return previewInstrument;
        }
        return Math.max(0, Items.INSTRUMENTS.indexOf(Items.HARP_MC));
    }

    private void toggleInstrumentLock() {
        previewInstrumentLocked = !previewInstrumentLocked;
        if (!previewInstrumentLocked || previewInstrument < 0) {
            previewInstrument = currentInstrumentIndex();
        }
        sheet.set(Items.SHEET_PREV_INSTRUMENT, (byte) previewInstrument);
        sheet.set(Items.SHEET_PREV_INSTRUMENT_LOCKED, previewInstrumentLocked);
        instrumentLockDirty = true;
        refreshWidgets();
    }

    private void audition(int pitch) { audition(pitch, defaultVelocity); }
    private void audition(int pitch, int velocity) {
        if (!previewDuringPlayback && playback.transportState(tempoBpm).playing()) { stopAudition(); return; }
        if (pitch == auditionPitch && velocity == auditionVelocity) return;
        stopAudition();
        if (output.audition(pitch, velocity)) { auditionPitch = pitch; auditionVelocity = velocity; }
    }

    private void stopAudition() {
        if (auditionPitch >= 0) output.stopAudition(auditionPitch);
        auditionPitch = -1; auditionVelocity = -1;
    }

    private void midiNoteOn(MidiHandler.MidiData data) {
        int pitch = IItemInstrument.idToNote(data.noteId());
        if (pitch < IItemInstrument.MIN_NOTE || pitch > MusicScore.MAX_PITCH) return;
        int velocity = Math.clamp(Math.round(data.volume() * 127.0F), 1, 127);
        boolean alreadyHeld = heldTypingNotes.containsValue(pitch)
                || heldMidiNotes.contains(pitch);
        if (!heldMidiNotes.add(pitch)) return;
        if (!alreadyHeld) inputNoteOn(pitch, velocity);
    }

    private void midiNoteOff(int noteId) {
        int pitch = IItemInstrument.idToNote(noteId);
        if (!heldMidiNotes.remove(pitch)) return;
        inputNoteOff(pitch);
    }

    private void inputNoteOn(int pitch, int velocity) {
        if (!heldTypingNotes.containsValue(pitch) && auditionPitch != pitch) {
            output.audition(pitch, velocity);
        }
        recorder.noteOn(pitch, velocity, recordingTick());
    }

    private void inputNoteOff(int pitch) {
        if (heldMidiNotes.contains(pitch) || heldTypingNotes.containsValue(pitch)) return;
        if (auditionPitch != pitch) output.stopAudition(pitch);
        recorder.noteOff(pitch, recordingTick());
    }

    private void finishRecordingNote(PianoRollRecording.Note note, long endTick) {
        if (session == null) return;
        long length = Math.max(1L, endTick - note.startTick());
        session.addRecorded(0, note.pitch(), note.velocity(), note.startTick(), length);
        if (!session.editing()) lastTouchedLength = length;
    }

    private long recordingTick() {
        if (transportSong == null) return Math.max(0L, recorder.startTick());
        return InstrumentMidiPlayback.playbackTick(transportSong,
                playback.playheadMicros(), tempoBpm);
    }

    private Component recordMessage() {
        return Component.literal(recorder.counting() || recorder.active() ? "■" : "●");
    }

    private void recordButton() {
        if (recorder.counting() || recorder.active()) stopPlayback();
        else startPreRecording();
    }

    private void startPreRecording() {
        if (session == null) return;
        if (transportSong == null) transportSong = session.song();
        if (playback.transportState(tempoBpm).playing()) {
            playback.playPause(transportSong, tempoBpm, 0);
        }
        recorder.begin(InstrumentMidiPlayback.playbackTick(transportSong,
                playback.playheadMicros(), tempoBpm), System.nanoTime());
        updateTransportButtons();
    }

    private void tickCountIn() {
        if (session != null && minecraft != null
                && recorder.countIn(timeSignature(), tempoBpm, System.nanoTime())) startRecording();
    }

    private void startRecording() {
        if (!recorder.counting() || session == null) return;
        recorder.start();
        long hourOfTicks = Math.multiplyExact((long) session.ppq(),
                Math.multiplyExact((long) Math.max(1, tempoBpm), 60L));
        transportSong = session.song().extendedTo(Math.min(MusicScore.MAX_TICKS,
                recorder.startTick() + hourOfTicks));
        long micros = InstrumentMidiPlayback.playbackMicrosAtTick(
                transportSong, recorder.startTick(), tempoBpm);
        playback.seek(transportSong, micros, tempoBpm, 0);
        playback.playPause(transportSong, tempoBpm, 0);
        updateTransportButtons();
    }

    private void playMetronomeClick(boolean accent) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_HAT.value(), accent ? 1.35F : 1.0F, 0.8F));
    }

    private void playPause() {
        if (recorder.counting() || recorder.active()) return;
        if (session == null || session.notes().isEmpty()) return;
        MusicScore current = session.song();
        if (transportSong != null && transportSong.equals(current)
                && playback.hasSelected(transportSong)) {
            playback.playPause(transportSong, tempoBpm, 0);
            return;
        }
        long tick = transportSong == null ? 0L
                : InstrumentMidiPlayback.playbackTick(transportSong,
                playback.playheadMicros(), tempoBpm);
        transportSong = current;
        if (tick >= current.lengthTick()) {
            playback.resetToStart(current, tempoBpm);
        } else {
            long micros = InstrumentMidiPlayback.playbackMicrosAtTick(
                    current, tick, tempoBpm);
            playback.seek(current, micros, tempoBpm, 0);
        }
        playback.playPause(current, tempoBpm, 0);
    }

    private Component playPauseMessage() {
        return Component.literal(transportSong != null
                && playback.transportState(tempoBpm).playing() ? "II" : "▶");
    }

    private void stopPlayback() {
        boolean wasRecording = recorder.active();
        recorder.stop(wasRecording ? recordingTick() : recorder.startTick());
        playback.stop();
        transportSong = null;
        recorder.resetMetronome();
        if (session != null && wasRecording) clampZoom();
        updateTransportButtons();
    }

    private void updateTransportButtons() {
        if (playButton != null) playButton.setMessage(playPauseMessage());
        if (recordButton != null) recordButton.setMessage(recordMessage());
    }

    private void tickMetronome() {
        if (!metronomeEnabled || transportSong == null || minecraft == null) return;
        InstrumentMidiPlayback.TransportState state = playback.transportState(tempoBpm);
        if (!state.playing()) return;
        long tick = InstrumentMidiPlayback.playbackTick(
                transportSong, state.playheadMicros(), tempoBpm);
        recorder.metronome(transportSong, tick);
    }

    private void refreshTransportScore() {
        if (session != null) {
            String error = session.takeError();
            if (error != null && minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(error));
        }
        if (session == null || transportSong == null) return;
        MusicScore next = session.song();
        if (next == transportSong) return;
        if (playback.transportState(tempoBpm).playing()) {
            playback.updateSong(next, tempoBpm, 0);
            transportSong = next;
            return;
        }
        MusicScore previous = transportSong;
        long tick = InstrumentMidiPlayback.playbackTick(previous,
                playback.playheadMicros(), tempoBpm);
        next = next.extendedTo(tick);
        if (previous.equals(next)) return;
        long micros = InstrumentMidiPlayback.playbackMicrosAtTick(next,
                tick, tempoBpm);
        transportSong = next;
        playback.seek(next, micros, tempoBpm, 0);
    }

    private void scrubAt(double mouseX) {
        if (session == null) return;
        MusicScore song = transportSong != null ? transportSong : session.song();
        long tick = Math.clamp(snapTick(tickAt(mouseX)), 0L, MusicScore.MAX_TICKS);
        long visibleEnd = Math.min(MusicScore.MAX_TICKS, Math.max(tick,
                (long) Math.ceil(tickAt(gridX + gridWidth))));
        boolean playing = playback.transportState(tempoBpm).playing();
        if (song.lengthTick() < visibleEnd) song = song.extendedTo(visibleEnd);
        long micros = InstrumentMidiPlayback.playbackMicrosAtTick(song, tick, tempoBpm);
        transportSong = song;
        playback.seek(song, micros, tempoBpm, 0);
        if (playing && !playback.transportState(tempoBpm).playing()) {
            playback.playPause(song, tempoBpm, 0);
        }
    }

    private void commitChanges() {
        if (draftConflict || saveSubmitted || replacementPending || session == null
                || (!importRequired && !session.dirty() && !signRequested && !instrumentLockDirty)) return;
        saveSubmitted = SheetEditController.save(draftKey, sheetId, version, session, importRequired,
                signRequested, highlightInterval, previewInstrument, previewInstrumentLocked,
                this::saveCompleted);
    }

    private void saveCompleted(m3e.network.MusicSheetSaveResultPacket result) {
        saveSubmitted = false;
        if (!result.success()) {
            replacementPending = false;
            exportAfterSave = false;
            return;
        }
        draftKey = result.sheetId();
        if (replacementPending) {
            MusicScore score = MusicSheetClientCache.get(result.sheetId());
            if (score != null) {
                ItemStack replacement = sheet.copy();
                replacement.set(Items.SHEET_ID, result.sheetId());
                replacement.set(Items.SHEET_VERSION, result.version());
                adopt(replacement, score);
            }
        } else {
            sheetId = result.sheetId();
            version = result.version();
            sheet.set(Items.SHEET_ID, sheetId);
            sheet.set(Items.SHEET_VERSION, version);
            blankSheet = false;
            importRequired = false;
            instrumentLockDirty = false;
            signRequested = false;
        }
        if (exportAfterSave) { exportAfterSave = false; exportSavedSheet(); }
    }

    private void beginSigning() {
        if (session == null || session.song().signed()) return;
        gettingSigned = true;
        signTitle = session.song().sourceName();
        refreshWidgets();
    }

    private void exportSheet() {
        if (session == null || session.notes().isEmpty() || minecraft.player == null || draftConflict) return;
        stopPlayback();
        if (importRequired || session.dirty() || instrumentLockDirty || signRequested || saveSubmitted) {
            exportAfterSave = true;
            commitChanges();
            if (!saveSubmitted) exportAfterSave = false;
        } else exportSavedSheet();
    }

    private void exportSavedSheet() {
        MusicScore song = session.song();
        SheetExportController.queue(sheetId, version,
                sheet.getOrDefault(Items.SHEET_VOLUME, 1.0F), sheet.getOrDefault(Items.SHEET_GENERATION, 0),
                song.sourceName(), previewInstrument, previewInstrumentLocked, highlightInterval,
                song.sourceName(), saveFormat == SaveFormat.MM);
        if (minecraft.gui.screen() == this) minecraft.gui.setScreen(null);
    }

    private void finalizeSign(EditBox title) {
        if (session == null || session.song().signed()) return;
        String cleanTitle = title.getValue().trim();
        if (cleanTitle.isEmpty()) return;
        session.rename(cleanTitle);
        signRequested = true;
        onClose();
    }

    private void rememberViewport() {
        if (session == null || sheetId == null || pixelsPerTick <= 0.0D) return;
        VIEW_STATES.put(sheetId, new ViewState(leftTick, topPitch, pixelsPerTick, rowHeight));
        while (VIEW_STATES.size() > 64) {
            VIEW_STATES.remove(VIEW_STATES.keySet().iterator().next());
        }
    }

    @Override public void onClose() {
        finishGesture();
        stopPlayback();
        rememberViewport();
        commitChanges();
        stopAudition();
        stopTypingAuditions();
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void removed() {
        finishGesture();
        stopPlayback();
        rememberViewport();
        commitChanges();
        stopTypingAuditions();
        output.stopAuditions();
        midiHandler.closeDevices();
        showNoteCursor(false, false);
        if (resizeCursor != 0L) {
            GLFW.glfwDestroyCursor(resizeCursor);
            resizeCursor = 0L;
        }
        if (moveCursor != 0L) {
            GLFW.glfwDestroyCursor(moveCursor);
            moveCursor = 0L;
        }
        super.removed();
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0101215);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        advanceDragAutoScroll();
        PianoKeyboardRenderer.drawPanelShadow(graphics, shellX, shellY + 3, shellWidth, shellHeight);
        PianoKeyboardRenderer.drawFourPixelPanel(graphics, shellX, shellY, shellWidth, shellHeight);
        PianoInsetPanel.draw(graphics, contentX - 1, rulerY - 1,
                contentRight - contentX + 2,
                gridY + gridHeight - rulerY + 2);
        graphics.fill(shellX + 4, headerDividerY,
                shellX + shellWidth - 4, headerDividerY + 1,
                PianoKeyboardRenderer.STATUS_DIVIDER);
        if (settingsOpen) {
            drawSettings(graphics);
            showNoteCursor(false, false);
        } else {
            graphics.fill(gridX, rulerY, gridX + gridWidth, gridY, 0xFF292D31);
            graphics.fill(contentX, gridY, gridX, gridY + gridHeight, 0xFF252B31);
            graphics.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, 0xFF24292E);
            if (session != null) drawRoll(graphics);
            else graphics.text(font, Component.literal("Loading score..."),
                    gridX + 8, gridY + 8, 0xFFE6E6E6, false);
            EdgeHit hoveredEdge = session != null && inGrid(mouseX, mouseY)
                    ? noteEdgeAt(mouseX, mouseY) : null;
            ScoreEditSession.Note hoveredNote = session != null && inGrid(mouseX, mouseY)
                    ? noteAtPixel(mouseX, mouseY) : null;
            showNoteCursor(noteEditor == null && hoveredEdge != null, noteEditor == null && hoveredEdge == null && hoveredNote != null);
        }
        if (!settingsOpen && session != null) drawVelocity(graphics, mouseX, mouseY);
        if (noteEditor != null) noteEditor.draw(graphics, font, width, height);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSettings(GuiGraphicsExtractor graphics) {
        graphics.fill(contentX, rulerY, contentRight, velocityY + VELOCITY_HEIGHT, 0xFF1B1D20);
        int labelX = contentX + SECTION_INSET * 2;
        if (settingsTab == SettingsTab.SONG) {
            graphics.text(font, Component.literal("Sheet name"), labelX,
                    settingsBodyY + 18, 0xFFE6E6E6, false);
            graphics.text(font, Component.literal("Tuning"), labelX,
                    settingsBodyY + 18 + CONTROL_HEIGHT + CONTROL_GAP,
                    0xFFE6E6E6, false);
            graphics.text(font, Component.literal("Time Signature"), labelX,
                    settingsBodyY + 18 + (CONTROL_HEIGHT + CONTROL_GAP) * 2,
                    0xFFE6E6E6, false);
        } else if (settingsTab == SettingsTab.COMPAT) {
            graphics.text(font, Component.literal("File Save Format"), labelX,
                    settingsBodyY, 0xFFE6E6E6, false);
        }
    }

    private void drawRoll(GuiGraphicsExtractor graphics) {
        long beat = beatTicks();
        long bar = barTicks();
        int highest = Math.min(IItemInstrument.MAX_NOTE, (int) Math.ceil(topPitch));
        int lowest = Math.max(IItemInstrument.MIN_NOTE, pitchAt(gridY + gridHeight) - 1);
        long lineStep = visibleGridStep();
        long firstLine = Math.max(0, (long) Math.ceil(leftTick / lineStep) * lineStep);
        Set<Integer> soundingPitches = highlightedPitches();
        graphics.enableScissor(gridX, gridY, gridX + gridWidth, gridY + gridHeight);
        for (int pitch = highest; pitch >= lowest; pitch--) {
            int y = rowTop(pitch);
            boolean black = isBlack(pitch);
            graphics.fill(gridX, y, gridX + gridWidth, rowBottom(pitch),
                    black ? 0xFF20252A : 0xFF292E33);
        }
        for (int pitch : soundingPitches) {
            int y = noteY(pitch);
            graphics.fill(gridX, y, gridX + gridWidth, y + rowHeight, 0x18FFFFFF);
        }
        for (int pitch = highest; pitch >= lowest; pitch--) {
            int bottom = rowBottom(pitch);
            graphics.fill(gridX, bottom - 1, gridX + gridWidth, bottom, 0xFF363C42);
        }
        for (long tick = firstLine; xAt(tick) < gridX + gridWidth; tick += lineStep) {
            int x = xAt(tick);
            if (x <= gridX) continue;
            int color = tick % bar == 0 ? 0xFF747D84 : tick % beat == 0 ? 0xFF50585E : 0xFF383F44;
            graphics.fill(x, gridY, x + 1, gridY + gridHeight, color);
        }
        List<RenderedNote> renderedNotes = new ArrayList<>();
        for (PianoRollGeometry.Rect rect : visibleNotes()) {
            ScoreEditSession.Note note = rect.note();
            renderedNotes.add(new RenderedNote(rect.left(), rect.right(), note.pitch(), note.velocity(),
                    session.selected(note.id()), session.selected(note.id())));
        }
        drawNotes(graphics, renderedNotes);
        if (gesture == Gesture.SLICE) {
            int x1 = xAt((long) pressTick), x2 = xAt((long) sliceEndTime);
            int y1 = rowOrigin() - (int) Math.round(pressContinuousPitch * rowHeight);
            int y2 = rowOrigin() - (int) Math.round(sliceEndPitch * rowHeight);
            int samples = Math.max(1, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
            for (int i = 0; i <= samples; i++) {
                int x = x1 + (x2 - x1) * i / samples, y = y1 + (y2 - y1) * i / samples;
                graphics.fill(x, y, x + 1, y + 1, SELECTION_BORDER);
            }
        }
        if (transportSong != null) {
            long micros = playback.playheadMicros();
            long tick = transportSong.tempoMap().tickAtMicros(micros, transportSong.lengthTick());
            int x = xAt(tick);
            graphics.fill(x, gridY, x + 1, gridY + gridHeight, 0xFFFFFFFF);
        }
        if (gesture == Gesture.MARQUEE) {
            int x1 = (int) Math.round(Math.min(xAt((long) pressTick), currentX));
            int x2 = (int) Math.round(Math.max(xAt((long) pressTick), currentX));
            int y1 = (int) Math.round(Math.min(rowOrigin() - pressContinuousPitch * rowHeight, currentY));
            int y2 = (int) Math.round(Math.max(rowOrigin() - pressContinuousPitch * rowHeight, currentY));
            graphics.fill(x1 + 1, y1 + 1, x2, y2, SELECTION_FILL);
            graphics.fill(x1, y1, x2 + 1, y1 + 1, SELECTION_BORDER);
            graphics.fill(x1, y2, x2 + 1, y2 + 1, SELECTION_BORDER);
            graphics.fill(x1, y1, x1 + 1, y2 + 1, SELECTION_BORDER);
            graphics.fill(x2, y1, x2 + 1, y2 + 1, SELECTION_BORDER);
        }
        graphics.disableScissor();
        drawKeyboard(graphics, highest, lowest, soundingPitches);
        drawRuler(graphics, beat, bar);
        if (timeSelection) {
            graphics.enableScissor(gridX, rulerY, contentRight, gridY);
            graphics.fill(xAt(Math.min(rangeStart, rangeEnd)), rulerY, xAt(Math.max(rangeStart, rangeEnd)), gridY, SELECTION_FILL);
            graphics.disableScissor();
        }
    }

    private long visibleGridStep() {
        long step = snapMode == SnapMode.NONE ? lineStep() : snapStep();
        double pixels = step * pixelsPerTick;
        while (pixels < 8.0D && step <= Long.MAX_VALUE / 2L) {
            step *= 2L;
            pixels = step * pixelsPerTick;
        }
        return step;
    }

    private void drawKeyboard(GuiGraphicsExtractor graphics, int highest, int lowest,
                              Set<Integer> soundingPitches) {
        if (keyboardStyle == KeyboardStyle.REGULAR) {
            drawRegularKeyboard(graphics, highest, lowest, soundingPitches);
            return;
        }
        graphics.enableScissor(contentX, gridY, gridX, gridY + gridHeight);
        for (int pitch = highest; pitch >= lowest; pitch--) {
            if (isBlack(pitch)) continue;
            graphics.fill(contentX, rowTop(pitch), gridX, rowBottom(pitch), 0xFFE4E1D9);
        }
        for (int pitch = highest; pitch >= lowest; pitch--) {
            if (!isBlack(pitch)) continue;
            graphics.fill(contentX, rowTop(pitch), gridX, rowBottom(pitch), 0xFF15191D);
        }
        for (int pitch : soundingPitches) {
            int y = noteY(pitch);
            graphics.fill(contentX, y, gridX, y + rowHeight, AUDITION_COLOR);
        }
        for (int pitch = highest; pitch >= lowest; pitch--) {
            int y = rowTop(pitch);
            int separator = isBlack(pitch) ? 0xFF555A5E : 0xFF777777;
            graphics.fill(contentX, rowBottom(pitch) - 1, gridX, rowBottom(pitch), separator);
        }
        for (int pitch = highest; pitch >= lowest; pitch--) drawKeyLabel(graphics, pitch,
                gridX - 3, isBlack(pitch) ? 0xFFE6E6E6 : 0xFF222222);
        graphics.disableScissor();
    }

    private void drawRegularKeyboard(GuiGraphicsExtractor graphics, int highest, int lowest,
                                     Set<Integer> soundingPitches) {
        graphics.enableScissor(contentX, gridY, gridX, gridY + gridHeight);
        int keyboardWidth = Math.max(1, gridX - contentX);
        int blackRight = contentX + Math.max(1, KEYS * 2 / 3);
        int renderHighest = Math.min(IItemInstrument.MAX_NOTE, highest + 2);
        int renderLowest = Math.max(IItemInstrument.MIN_NOTE, lowest - 2);
        for (int x = contentX; x < gridX; x++) {
            double amount = (x - contentX) / (double) Math.max(1, keyboardWidth - 1);
            graphics.fill(x, gridY, x + 1, gridY + gridHeight,
                    lerpColor(0xFFD0D5D8, 0xFFF1F3F4, amount));
        }

        for (int pitch = renderHighest; pitch >= renderLowest; pitch--) {
            if (isBlack(pitch)) continue;
            if (soundingPitches.contains(pitch)) {
                graphics.fill(contentX, rowTop(pitch), blackRight,
                        rowBottom(pitch), AUDITION_COLOR);
                graphics.fill(blackRight, regularWhiteTop(pitch), gridX,
                        regularWhiteBottom(pitch), AUDITION_COLOR);
            }
        }

        for (int pitch = renderHighest; pitch >= renderLowest; pitch--) {
            if (isBlack(pitch)) continue;
            int pitchClass = Math.floorMod(pitch, 12);
            if (pitchClass != 4 && pitchClass != 11) continue;
            int divider = regularWhiteTop(pitch) - 1;
            graphics.fill(contentX, divider, gridX, divider + 1, 0xFF777777);
        }

        for (int pitch = renderHighest; pitch >= renderLowest; pitch--) {
            if (!isBlack(pitch)) continue;
            int top = rowTop(pitch);
            int bottom = rowBottom(pitch);
            graphics.fill(contentX, top, blackRight, bottom, 0xFF181B1E);
            graphics.fill(contentX + 1, top + 1, blackRight - 1, bottom - 1, 0xFF404448);
            if (soundingPitches.contains(pitch)) {
                graphics.fill(contentX, top, blackRight, bottom, AUDITION_COLOR);
            }
        }

        for (int pitch = renderHighest; pitch >= renderLowest; pitch--) {
            if (!isBlack(pitch)) {
                drawRegularKeyLabel(graphics, pitch, gridX - 3, 0xFF303438,
                        regularWhiteTop(pitch), regularWhiteBottom(pitch));
            }
        }
        graphics.disableScissor();
    }

    private int regularWhiteTop(int pitch) {
        int pitchClass = Math.floorMod(pitch, 12);
        int half = rowHeight / 2;
        return switch (pitchClass) {
            case 0, 2, 5, 7, 9 -> rowTop(pitch + 1) + half;
            case 4, 11 -> rowTop(pitch);
            default -> rowTop(pitch);
        };
    }

    private int regularWhiteBottom(int pitch) {
        int pitchClass = Math.floorMod(pitch, 12);
        int half = rowHeight / 2;
        return switch (pitchClass) {
            case 0, 5 -> rowBottom(pitch);
            case 2, 4, 7, 9, 11 -> rowTop(pitch - 1) + half;
            default -> rowBottom(pitch);
        };
    }

    private void drawKeyLabel(GuiGraphicsExtractor graphics, int pitch, int right, int color) {
        if (rowHeight < font.lineHeight || keyLabels == KeyLabels.NONE
                || keyLabels == KeyLabels.ROOT_NOTES && pitch % 12 != 0) return;
        String label = noteName(pitch);
        graphics.text(font, Component.literal(label), right - font.width(label),
                rowTop(pitch) + Math.max(0, (rowHeight - font.lineHeight) / 2), color, false);
    }

    private void drawRegularKeyLabel(GuiGraphicsExtractor graphics, int pitch, int right,
                                     int color, int top, int bottom) {
        if (rowHeight < font.lineHeight || keyLabels == KeyLabels.NONE
                || keyLabels == KeyLabels.ROOT_NOTES && pitch % 12 != 0) return;
        String label = noteName(pitch);
        graphics.text(font, Component.literal(label), right - font.width(label),
                top + Math.max(0, (bottom - top - font.lineHeight) / 2) + 1, color, false);
    }

    private Set<Integer> highlightedPitches() {
        Set<Integer> pitches = new LinkedHashSet<>(playback.activePitches());
        if (auditionPitch >= 0) pitches.add(auditionPitch);
        pitches.addAll(heldTypingNotes.values());
        pitches.addAll(heldMidiNotes);
        return pitches;
    }

    private void drawRuler(GuiGraphicsExtractor graphics, long beat, long bar) {
        graphics.enableScissor(gridX, rulerY, gridX + gridWidth, gridY);
        double pixelsPerBar = bar * pixelsPerTick;
        long interval = 1L;
        while (pixelsPerBar * interval < 48.0D) interval *= 2L;
        long visibleBar = Math.max(0L, (long) Math.floor(leftTick / bar));
        long first = ((visibleBar + interval - 1L) / interval) * interval;
        for (long barIndex = first; xAt(barIndex * bar) < gridX + gridWidth; barIndex += interval) {
            String label = Long.toString(barIndex + 1L);
            int x = xAt(barIndex * bar) + 2;
            if (x + font.width(label) > gridX + gridWidth) break;
            graphics.text(font, Component.literal(label), x, rulerY + 2, 0xFFD8D8D8, false);
        }
        graphics.disableScissor();
    }

    private void drawNotes(GuiGraphicsExtractor graphics, List<RenderedNote> notes) {
        Map<Integer, List<RenderedNote>> notesByPitch = new LinkedHashMap<>();
        for (RenderedNote note : notes) {
            notesByPitch.computeIfAbsent(note.pitch(), ignored -> new ArrayList<>()).add(note);
        }
        for (List<RenderedNote> pitchNotes : notesByPitch.values()) {
            pitchNotes.sort((left, right) -> Integer.compare(left.left(), right.left()));
        }
        for (boolean selectedLayer : new boolean[]{false, true}) {
            for (RenderedNote note : notes) {
                if (note.selected() != selectedLayer) continue;
                int fill = noteColor(note.highlighted()
                                ? SELECTED_NOTE_COLOR : DEFAULT_NOTE_COLOR,
                        note.velocity());
                graphics.fill(note.left() + 1, rowTop(note.pitch()), note.right(),
                        rowBottom(note.pitch()) - 1, fill);
            }
            for (RenderedNote note : notes) {
                if (note.selected() != selectedLayer) continue;
                int top = noteVisualTop(note.pitch());
                int bottom = noteVisualBottom(note.pitch());
                int fill = noteColor(note.highlighted()
                                ? SELECTED_NOTE_COLOR : DEFAULT_NOTE_COLOR,
                        note.velocity());
                int border = borderColor(fill);
                graphics.fill(note.left(), top + 1, note.left() + 1, bottom - 1, border);
                graphics.fill(note.right(), top + 1, note.right() + 1, bottom - 1, border);
                graphics.fill(note.left(), bottom - 1, note.right() + 1, bottom, border);
                drawUnsharedTopBorder(graphics, note,
                        notesByPitch.getOrDefault(note.pitch() + 1, List.of()), border);
            }
            for (RenderedNote note : notes) {
                if (note.selected() != selectedLayer) continue;
                String label = noteName(note.pitch());
                if (rowHeight >= font.lineHeight + 1 && note.right() - note.left() >= font.width(label) + 3) {
                    graphics.text(font, Component.literal(label), note.left() + 2,
                            rowTop(note.pitch()) + Math.max(0, (rowHeight - font.lineHeight) / 2) + 1,
                            note.highlighted() ? 0xFFFFFFFF : 0xFF2F6E4B, false);
                }
            }
        }
    }

    private void drawUnsharedTopBorder(GuiGraphicsExtractor graphics, RenderedNote note,
                                       List<RenderedNote> notesAbove, int color) {
        int right = note.right() + 1;
        int cursor = note.left();
        int top = noteVisualTop(note.pitch());
        for (RenderedNote other : notesAbove) {
            if (other.right() + 1 <= cursor) continue;
            if (other.left() >= right) break;
            int start = Math.max(cursor, other.left());
            if (start > cursor) graphics.fill(cursor, top, start, top + 1, color);
            cursor = Math.max(cursor, Math.min(right, other.right() + 1));
            if (cursor >= right) return;
        }
        if (cursor < right) graphics.fill(cursor, top, right, top + 1, color);
    }

    private static int borderColor(int color) {
        int red = Math.round((color >> 16 & 0xFF) * 0.67F);
        int green = Math.round((color >> 8 & 0xFF) * 0.67F);
        int blue = Math.round((color & 0xFF) * 0.67F);
        return color & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int noteColor(int base, int velocity) {
        int boundedVelocity = Math.clamp(velocity, 1, 127);
        int brightness = Math.round((boundedVelocity - VELOCITY_MIDDLE)
                * VELOCITY_BRIGHTNESS_RANGE / 63.0F);
        int red = Math.clamp((base >> 16 & 0xFF) + brightness, 0, 255);
        int green = Math.clamp((base >> 8 & 0xFF) + brightness, 0, 255);
        int blue = Math.clamp((base & 0xFF) + brightness, 0, 255);
        return base & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int lerpColor(int from, int to, double amount) {
        double bounded = Math.clamp(amount, 0.0D, 1.0D);
        int a = (int) Math.round((from >>> 24) * (1.0D - bounded) + (to >>> 24) * bounded);
        int r = (int) Math.round((from >> 16 & 0xFF) * (1.0D - bounded)
                + (to >> 16 & 0xFF) * bounded);
        int g = (int) Math.round((from >> 8 & 0xFF) * (1.0D - bounded)
                + (to >> 8 & 0xFF) * bounded);
        int b = (int) Math.round((from & 0xFF) * (1.0D - bounded)
                + (to & 0xFF) * bounded);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static String noteName(int pitch) {
        return NOTE_NAMES[Math.floorMod(pitch, 12)] + Math.floorDiv(pitch, 12);
    }

    private int noteVisualTop(int pitch) { return rowTop(pitch) - 1; }
    private int noteVisualBottom(int pitch) { return rowBottom(pitch); }
    private int noteY(int pitch) { return rowTop(pitch); }

    private void adopt(ItemStack held, MusicScore song) {
        sheet = held.copy();
        sheetId = held.get(Items.SHEET_ID);
        version = held.getOrDefault(Items.SHEET_VERSION, -1);
        highlightInterval = Math.clamp(Byte.toUnsignedInt(
                held.getOrDefault(Items.SHEET_HIGHLIGHT_INTERVAL, (byte) 12)), 1, 24);
        previewInstrumentLocked = held.getOrDefault(
                Items.SHEET_PREV_INSTRUMENT_LOCKED, false);
        Byte storedInstrument = held.get(Items.SHEET_PREV_INSTRUMENT);
        previewInstrument = storedInstrument == null ? -1 : Byte.toUnsignedInt(storedInstrument);
        instrumentLockDirty = false;
        replacementPending = false;
        replacementSheetId = null;
        replacementVersion = -1;
        blankSheet = false;
        importRequired = false;
        session = new ScoreEditSession(song);
        tempoBpm = session.initialBpm();

        lastTouchedLength = -1L;
        leftTick = 0;
        pixelsPerTick = 0;
        if (minecraft.gui.screen() == this) { clearWidgets(); init(); }
    }

    @Override public void onFilesDrop(List<java.nio.file.Path> paths) {
        if (!SheetDropController.handle(this, paths)) super.onFilesDrop(paths);
    }

    @Override public void m3e$prepareForReplacementImport() {
        stopPlayback();
        stopAudition();
    }

    @Override public void m3e$beginReplacementImport() {
        replacementPending = true;
        replacementSheetId = null;
        replacementVersion = -1;
    }

    @Override public boolean m3e$acceptReplacementImport(UUID id, int replacementVersion,
                                                          List<NoteEvent> notes,
                                                          List<VolumeMarker> markers) {
        if (!replacementPending) return false;
        sheetId = id;
        version = replacementVersion;
        MusicSheetClientCache.request(id, replacementVersion);
        return true;
    }

    void acceptImportedScore(UUID id, int importedVersion, MusicScore song) {
        if (saveSubmitted || !replacementPending || song == null
                || (replacementSheetId != null && !replacementSheetId.equals(id))
                || (replacementVersion >= 0 && importedVersion < replacementVersion)) return;
        ItemStack replacement = sheet.copy();
        replacement.set(Items.SHEET_ID, id);
        replacement.set(Items.SHEET_VERSION, importedVersion);
        replacement.set(m3e.sheet.MusicSheetComponents.SCORE, song);
        adopt(replacement, song);
    }

    boolean canAcceptImport() {
        if (minecraft == null || minecraft.player == null) return false;
        ItemStack held = minecraft.player.getMainHandItem();
        if (!(held.getItem() instanceof ItemMusicSheet)) return false;
        if (minecraft.player.isCreative() || ItemMusicSheet.isEmptySheet(held)) return true;
        if (session == null || sheetId == null || !sheetId.equals(held.get(Items.SHEET_ID))
                || version != held.getOrDefault(Items.SHEET_VERSION, -1)) return false;
        int generation = held.getOrDefault(Items.SHEET_GENERATION, 0);
        return generation == 0 || generation > 1
                || held.getOrDefault(Items.SHEET_AUTHOR, "")
                .equals(minecraft.player.getName().getString());
    }

    boolean importScore(MusicScore song, List<Integer> drumMap) {
        if (!canAcceptImport() || song == null || minecraft.player == null || saveSubmitted) return false;
        ItemStack held = minecraft.player.getMainHandItem();
        stopPlayback();
        stopAudition();
        boolean importing = minecraft.player.isCreative() || ItemMusicSheet.isEmptySheet(held);
        session = new ScoreEditSession(song.withDrumMap(drumMap));
        importRequired = importing;
        instrumentLockDirty = true;
        signRequested = false;
        tempoBpm = session.initialBpm();
        replacementPending = true;
        replacementSheetId = importing ? null : sheetId;
        replacementVersion = importing ? -1 : version + 1;
        saveSubmitted = SheetEditController.save(draftKey, sheetId, version, session, importing, false,
                highlightInterval, previewInstrument, previewInstrumentLocked,
                this::saveCompleted);
        if (!saveSubmitted) replacementPending = false;
        minecraft.gui.setScreen(this);
        clearWidgets();
        init();
        return saveSubmitted;
    }

    private final class InstrumentLockButton extends Button {
        private InstrumentLockButton(int x, int y) {
            super(x, y, 16, 16, Component.empty(), ignored -> toggleInstrumentLock(),
                    DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX,
                                       int mouseY, float partialTick) {
            int instrument = Math.clamp(previewInstrument, 0, Items.INSTRUMENTS.size() - 1);
            int textureY = 32 * ((instrument + 1) / 16) + (isHovered && active ? 16 : 0);
            graphics.blit(RenderPipelines.GUI_TEXTURED, INSTRUMENT_TEXTURES,
                    getX(), getY(), instrument * 16 + 16, textureY,
                    width, height, 256, 256);
            if (previewInstrumentLocked) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, INSTRUMENT_TEXTURES,
                        getX(), getY(), 0, 240,
                        width, height, 256, 256);
            }
        }
    }

    private PlaybackTransportPanel.Binding createTransportBinding() {
        return new PlaybackTransportPanel.Binding() {
            @Override public boolean available() {
                return session != null;
            }
            @Override public long playheadMicros() { return playback.playheadMicros(); }
            @Override public long durationMicros() {
                return session == null ? 0L : playback.durationMicros(session.song(), tempoBpm);
            }
            @Override public int tempoBpm() { return tempoBpm; }
            @Override public void setTempoBpm(int bpm) {
                if (session == null) return;
                int bounded = Math.clamp(bpm, 1, 3_000);
                if (bounded == tempoBpm) return;
                boolean playing = transportSong != null
                        && playback.transportState(tempoBpm).playing();
                long oldDuration = playback.durationMicros(session.song(), tempoBpm);
                long oldPosition = playback.playheadMicros();
                session.setInitialBpm(bounded);
                tempoBpm = bounded;
                MusicScore next = session.song();
                long nextDuration = playback.durationMicros(next, tempoBpm);
                long nextPosition = oldDuration <= 0L ? 0L
                        : Math.round(oldPosition * nextDuration / (double) oldDuration);
                transportSong = next;
                playback.seek(next, nextPosition, tempoBpm, 0);
                if (playing) playback.playPause(next, tempoBpm, 0);
            }
            @Override public double speed() { return 1.0D; }
            @Override public void setSpeed(double speed) { }
            @Override public boolean playing() {
                return transportSong != null && playback.transportState(tempoBpm).playing();
            }
            @Override public void playPause() { PianoRollScreen.this.playPause(); }
            @Override public void stop() { stopPlayback(); }
            @Override public void seekMicros(long micros) {
                if (session == null) return;
                transportSong = session.song();
                playback.seek(transportSong, micros, tempoBpm, 0);
            }
            @Override public int transpose() { return 0; }
            @Override public boolean canTranspose(int delta) { return false; }
            @Override public void transpose(int delta) { }
        };
    }

    private static boolean isBlack(int pitch) {
        return switch (Math.floorMod(pitch, 12)) { case 1, 3, 6, 8, 10 -> true; default -> false; };
    }

    private void showNoteCursor(boolean resize, boolean move) {
        if (minecraft == null || resizeCursorShown == resize && moveCursorShown == move) return;
        resizeCursorShown = resize;
        moveCursorShown = move;
        if (resize && resizeCursor == 0L) {
            resizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);
        }
        if (move && moveCursor == 0L) {
            moveCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_ALL_CURSOR);
        }
        GLFW.glfwSetCursor(minecraft.getWindow().handle(), resize ? resizeCursor : move ? moveCursor : 0L);
    }
}
