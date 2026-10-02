package m3e.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.Items;
import m3e.debug.MusicDebugTelemetry;
import m3e.sheet.MusicScore;
import m3e.sheet.SheetBatchCodec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class ScorePartImportScreen extends Screen {
    private static final int MAX_WIDTH = 640;
    private static final int MAX_HEIGHT = 310;
    private static final int FOOTER_HEIGHT = 34;
    private static final int INSET = InstrumentPanelGeometry.SECTION_INSET;
    private static final int GAP = InstrumentPanelGeometry.SECTION_GAP;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_GAP = InstrumentPanelGeometry.GAP;
    private static final int TEXT = 0xFFF0F0F0;

    private final ScoreImportAnalysis analysis;
    private final ScoreImportSession model;
    private final ScoreImportDestination destination;
    private final InstrumentMidiPlayback previewPlayback = new InstrumentMidiPlayback(true);
    private final List<Button> partButtons = new ArrayList<>();
    private final List<Button> partCheckboxButtons = new ArrayList<>();
    private final List<Button> partDisclosureButtons = new ArrayList<>();
    private final List<PartRow> partButtonRows = new ArrayList<>();
    private final Set<TrackTarget> focusedTargets = new LinkedHashSet<>();
    private final OverlayDropdown.Controller instrumentDropdown;
    private final List<AbstractWidget> previewControls = new ArrayList<>();
    private MusicScore previewSong;
    private InstrumentMidiScreenAccess previewOutput;
    private PlaybackTransportPanel.TimelineSlider previewScrubber;
    private Button previewPlayPause;
    private int octaveOffset;
    private int tempoBpm;
    private M3EEditBox nameField;
    private TempoEditBox tempoField;
    private Button octaveLabel;
    private Button octaveDownButton;
    private Button octaveUpButton;
    private Button selectAllButton;
    private Button mergeButton;
    private Button mergeInstrumentButton;
    private Button includeSourceButton;
    private Button includeInstrumentButton;
    private Button includePartButton;
    private Button drumRemapButton;
    private Button dynamicsButton;
    private Button glissandoButton;
    private Button followOctaveButton;
    private Button followTempoButton;
    private Button importButton;
    private Button cancelButton;
    private Button importModeButton;
    private boolean includeSource;
    private boolean includeInstrument;
    private boolean includePart;
    private boolean convertDynamics = true;
    private boolean convertGlissando = true;
    private String globalInstrumentId = "";
    private String preservedName;
    private String preservedTempo;
    private int scroll;
    private int partViewportX;
    private int partViewportY;
    private int partViewportWidth;
    private int partViewportHeight;
    private int partContentHeight;
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
    private int partsX;
    private int partsY;
    private int partsWidth;
    private int partsHeight;
    private int optionsX;
    private int optionsY;
    private int optionsWidth;
    private int optionsHeight;
    private int nameFieldPanelX;
    private int nameFieldPanelY;
    private int nameFieldPanelWidth;
    private int nameFieldPanelHeight;
    private int octavePanelX;
    private int octavePanelY;
    private int octavePanelWidth;
    private int octavePanelHeight;
    private int midiOptionsX;
    private int midiOptionsY;
    private int midiOptionsWidth;
    private int midiOptionsHeight;
    private boolean separateMode;
    private boolean uploading;
    private int separateCapacity;
    private int uploadSentBytes;
    private int uploadTotalBytes;
    private UUID batchTransactionId;
    private final PlaybackTransportPanel.Binding previewTransport =
            new PlaybackTransportPanel.Binding() {
                @Override public boolean available() { return previewAvailable(); }
                @Override public long playheadMicros() {
                    return previewPlayback.playheadMicros();
                }
                @Override public long durationMicros() {
                    return previewSong == null ? 0L
                            : previewPlayback.durationMicros(previewSong, displayedTempo());
                }
                @Override public int tempoBpm() { return displayedTempo(); }
                @Override public int displayTempoBpm() {
                    return previewPlayback.transportState(displayedTempo()).bpm();
                }
                @Override public void setTempoBpm(int bpm) { }
                @Override public double speed() { return 1.0D; }
                @Override public void setSpeed(double speed) { }
                @Override public boolean playing() {
                    return previewPlayback.transportState(tempoBpm).playing();
                }
                @Override public void playPause() {
                    if (!previewAvailable() || !commitTempo()) return;
                    previewPlayback.playPause(previewSong, displayedTempo(), 0);
                    refreshPreviewControls();
                }
                @Override public void stop() {
                    previewPlayback.stop();
                    refreshPreviewControls();
                }
                @Override public void seekMicros(long micros) {
                    if (!previewAvailable()) return;
                    previewPlayback.seek(previewSong, micros, displayedTempo(), 0);
                    refreshPreviewControls();
                }
                @Override public int transpose() { return 0; }
                @Override public boolean canTranspose(int delta) { return false; }
                @Override public void transpose(int delta) { }
            };

    ScorePartImportScreen(ScoreImportAnalysis analysis, ScoreImportDestination destination) {
        super(Component.literal("Import Score"));
        this.analysis = analysis;
        this.model = new ScoreImportSession(analysis);
        this.destination = destination;
        this.instrumentDropdown = new OverlayDropdown.Controller(this::refreshLayout);
        includeSource = analysis.midiOptions() && InstrumentOptions.includeMidiName();
        includeInstrument = analysis.midiOptions() && InstrumentOptions.includeMidiInstrumentName();
        includePart = analysis.midiOptions() && InstrumentOptions.includeMidiChannelName();
        octaveOffset = InstrumentOptions.importOctaveOffset();
        tempoBpm = analysis.initialBpm();
        restoreInitialMerges();
    }

    @Override protected void init() {
        partButtons.clear();
        partCheckboxButtons.clear();
        partDisclosureButtons.clear();
        partButtonRows.clear();
        previewControls.clear();
        previewScrubber = null;
        previewPlayPause = null;
        nameField = null;
        tempoField = null;
        octaveLabel = null;
        octaveDownButton = null;
        octaveUpButton = null;
        includeSourceButton = null;
        includeInstrumentButton = null;
        includePartButton = null;
        drumRemapButton = null;
        dynamicsButton = null;
        glissandoButton = null;
        followOctaveButton = null;
        followTempoButton = null;
        importButton = null;
        cancelButton = null;
        importModeButton = null;
        mergeButton = null;
        mergeInstrumentButton = null;
        separateCapacity = destination.separateCapacity();
        boolean midi = analysis.midiOptions();
        boolean offersDrums = midi && hasDrumParts();

        shellWidth = Math.max(1, Math.min(MAX_WIDTH, width - 12));
        shellHeight = Math.max(1, Math.min(MAX_HEIGHT, height - 12));
        shellX = (width - shellWidth) / 2;
        shellY = (height - shellHeight) / 2;
        footerY = shellY + shellHeight - FOOTER_HEIGHT;

        int contentX = shellX + 10;
        int contentY = shellY + 10;
        int contentWidth = Math.max(1, shellWidth - 20);
        int contentHeight = Math.max(1, footerY - INSET - contentY);
        optionsWidth = Math.clamp(contentWidth * 34 / 100, 184, 218);
        partsX = contentX;
        partsY = contentY;
        partsWidth = Math.max(1, contentWidth - optionsWidth - GAP);
        partsHeight = contentHeight;
        optionsX = partsX + partsWidth + GAP;
        optionsY = contentY;
        optionsHeight = contentHeight;
        instrumentDropdown.beginFrame(shellY + 4, footerY - 1);

        int partsInnerX = partsX + INSET;
        int partsInnerWidth = Math.max(1, partsWidth - INSET * 2);
        int selectAllY = partsY + INSET;
        selectAllButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> selectAll())
                .bounds(partsInnerX, selectAllY, partsInnerWidth, CONTROL_HEIGHT).build());

        int listTop = selectAllY + CONTROL_HEIGHT + GAP;
        partViewportX = partsInnerX;
        partViewportY = listTop;
        partViewportWidth = partsInnerWidth;
        partViewportHeight = Math.max(1, partsY + partsHeight - INSET - listTop);
        List<PartRow> visibleRows = visibleRows();
        partContentHeight = Math.max(0, visibleRows.size()
                * (CONTROL_HEIGHT + CONTROL_GAP) - CONTROL_GAP);
        int maximumScroll = Math.max(0, partContentHeight - partViewportHeight);
        scroll = Math.clamp(scroll, 0, maximumScroll);
        boolean scrollable = maximumScroll > 0;
        int buttonWidth = Math.max(1, partsInnerWidth - (scrollable ? 9 : 0));
        int checkboxWidth = CONTROL_HEIGHT;

        for (int visibleIndex = 0; visibleIndex < visibleRows.size(); visibleIndex++) {
            PartRow row = visibleRows.get(visibleIndex);
            int index = row.partIndex();
            int y = partViewportY + visibleIndex * (CONTROL_HEIGHT + CONTROL_GAP) - scroll;
            int column = checkboxWidth + CONTROL_GAP;
            boolean expandable = !row.child() && expandableMerge(index);
            int checkboxX = partsInnerX + (row.child() ? column : 0);
            Button checkbox = Button.builder(Component.empty(), ignored -> {
                        if (row.child()) toggleMergeMember(row.mergeRepresentative(), index);
                        else togglePart(index);
                    })
                    .bounds(checkboxX, y, checkboxWidth, CONTROL_HEIGHT).build();
            checkbox.visible = ViewportClippedWidget.intersects(checkbox,
                    partViewportX, partViewportY, partViewportWidth, partViewportHeight);
            if (checkbox.visible) addRenderableWidget(ViewportClippedWidget.clip(checkbox,
                    partViewportX, partViewportY, partViewportWidth, partViewportHeight));
            partCheckboxButtons.add(checkbox);
            int disclosureX = partsInnerX + column;
            Button disclosure = Button.builder(Component.empty(), ignored -> toggleMergeExpanded(index))
                    .bounds(disclosureX, y, checkboxWidth, CONTROL_HEIGHT).build();
            disclosure.visible = expandable
                    && ViewportClippedWidget.intersects(disclosure,
                    partViewportX, partViewportY, partViewportWidth, partViewportHeight);
            if (disclosure.visible) addRenderableWidget(ViewportClippedWidget.clip(disclosure,
                    partViewportX, partViewportY, partViewportWidth, partViewportHeight));
            partDisclosureButtons.add(disclosure);
            int rowX = partsInnerX + column * 2;
            int rowWidth = Math.max(1, buttonWidth - (rowX - partsInnerX));
            Button button;
            if (row.child()) {
                button = new LeftAlignedButton(font, rowX, y, rowWidth, CONTROL_HEIGHT,
                        mergeMemberLabel(row), ignored -> focusTarget(targetFor(row)));
            } else if (expandableMerge(index)) {
                button = new LeftAlignedButton(font, rowX, y, rowWidth, CONTROL_HEIGHT,
                        Component.literal(mergeLabel(index)),
                        ignored -> focusTarget(targetFor(row)));
            } else {
                button = new LeftAlignedButton(font,
                        rowX, y, rowWidth, CONTROL_HEIGHT,
                        label(index, rowWidth), ignored -> focusTarget(targetFor(row)));
            }
            button.visible = ViewportClippedWidget.intersects(button,
                    partViewportX, partViewportY, partViewportWidth, partViewportHeight);
            if (button.visible) {
                AbstractWidget clipped = ViewportClippedWidget.clip(button,
                        partViewportX, partViewportY,
                        partViewportWidth, partViewportHeight);
                addRenderableWidget(clipped);
            }
            partButtons.add(button);
            partButtonRows.add(row);
        }
        scrollTrackWidth = 5;
        scrollTrackX = partViewportX + partViewportWidth - scrollTrackWidth - 1;
        scrollTrackY = partViewportY;
        scrollTrackHeight = partViewportHeight;
        if (!scrollable) scrollbarDragging = false;

        addOptions(midi, offersDrums);
        addActions("Import");
        instrumentDropdown.addOverlay(this::addRenderableWidget);
        refreshLabels();
    }

    private void addPreviewControls(int x, int scrubberY, int width) {
        java.util.function.Consumer<AbstractWidget> add = widget -> {
            addRenderableWidget(widget);
            previewControls.add(widget);
        };
        previewScrubber = PlaybackTransportPanel.addScrubber(add,
                x, scrubberY, width, CONTROL_HEIGHT, previewTransport, true);
        previewPlayPause = PlaybackTransportPanel.addTransport(add,
                x, scrubberY + CONTROL_HEIGHT + 4, width, CONTROL_HEIGHT,
                CONTROL_GAP, previewTransport);
        refreshPreviewControls();
    }

    private void addOptions(boolean midi, boolean offersDrums) {
        int innerX = optionsX + INSET;
        int innerWidth = Math.max(1, optionsWidth - INSET * 2);
        int nextY = optionsY + INSET;

        if (destination.supportsSeparate(analysis.midiOptions()) && analysis.partCount() > 1) {
            importModeButton = addRenderableWidget(Button.builder(Component.empty(),
                    ignored -> setSeparateMode(!separateMode))
                    .bounds(innerX, nextY, innerWidth, CONTROL_HEIGHT).build());
            nextY += CONTROL_HEIGHT + GAP;
        }

        nameFieldPanelX = innerX;
        nameFieldPanelY = nextY;
        nameFieldPanelWidth = innerWidth;
        boolean globalSeparate = separateMode && focusedTargets.isEmpty();
        nameFieldPanelHeight = namePanelHeight(midi && globalSeparate);
        nameField = new M3EEditBox(this, font, nameFieldPanelX + 6, nameFieldPanelY + 6,
                Math.max(1, nameFieldPanelWidth - 12), CONTROL_HEIGHT,
                Component.literal("Imported score name"));
        nameField.setMaxLength(96);
        nameField.setValue(nameFieldValue());
        nameField.setHint(Component.literal(separateMode
                ? ScoreImportSession.separateNameTemplate(analysis, includeSource, includeInstrument, includePart)
                : "Imported score name"));
        nameField.active = !uploading && (!separateMode
                || focusedTarget().filter(TrackTarget::group).isPresent());
        addRenderableWidget(nameField);

        if (midi && globalSeparate) {
            int toggleGap = 4;
            int toggleAreaX = nameFieldPanelX + 6;
            int toggleAreaWidth = Math.max(1, nameFieldPanelWidth - 12);
            int toggleWidth = Math.max(1, (toggleAreaWidth - toggleGap * 2) / 3);
            int toggleY = nameFieldPanelY + 6 + CONTROL_HEIGHT + 4;
            includeSourceButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                includeSource = !includeSource;
                InstrumentOptions.setIncludeMidiName(includeSource);
                resetGeneratedName();
                refreshLabels();
            }).bounds(toggleAreaX, toggleY, toggleWidth, CONTROL_HEIGHT).build());
            includeInstrumentButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                includeInstrument = !includeInstrument;
                InstrumentOptions.setIncludeMidiInstrumentName(includeInstrument);
                resetGeneratedName();
                refreshLabels();
            }).bounds(toggleAreaX + toggleWidth + toggleGap, toggleY,
                    toggleWidth, CONTROL_HEIGHT).build());
            includePartButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                includePart = !includePart;
                InstrumentOptions.setIncludeMidiChannelName(includePart);
                resetGeneratedName();
                refreshLabels();
            }).bounds(toggleAreaX + (toggleWidth + toggleGap) * 2, toggleY,
                    Math.max(1, toggleAreaWidth - toggleWidth * 2 - toggleGap * 2),
                    CONTROL_HEIGHT).build());
        }
        nextY += nameFieldPanelHeight + GAP;

        octavePanelX = innerX;
        octavePanelY = nextY;
        octavePanelWidth = innerWidth;
        boolean showPreview = !separateMode || focusedTargets.size() == 1;
        octavePanelHeight = showPreview ? 106 : 58;
        int octaveRowX = octavePanelX + 6;
        int previewScrubberY = octavePanelY + 7;
        int octaveRowY = showPreview
                ? previewScrubberY + CONTROL_HEIGHT * 2 + 8 : octavePanelY + 7;
        int octaveRowWidth = Math.max(1, octavePanelWidth - 12);
        if (showPreview) addPreviewControls(octaveRowX, previewScrubberY, octaveRowWidth);
        int octaveTextWidth = 48;
        int octaveControlsX = octaveRowX + octaveTextWidth;
        int octaveControlsWidth = Math.max(1, octaveRowWidth - octaveTextWidth);
        int octaveButtonWidth = 30;
        octaveDownButton = addRenderableWidget(Button.builder(Component.literal("−"), ignored -> changeOctave(-1))
                .bounds(octaveControlsX, octaveRowY, octaveButtonWidth, CONTROL_HEIGHT).build());
        octaveLabel = addRenderableWidget(Button.builder(Component.empty(), ignored -> { })
                .bounds(octaveControlsX + octaveButtonWidth + 4, octaveRowY,
                        Math.max(1, octaveControlsWidth - octaveButtonWidth * 2 - 8),
                        CONTROL_HEIGHT).build());
        octaveLabel.active = false;
        octaveUpButton = addRenderableWidget(Button.builder(Component.literal("+"), ignored -> changeOctave(1))
                .bounds(octaveControlsX + octaveControlsWidth - octaveButtonWidth, octaveRowY,
                        octaveButtonWidth, CONTROL_HEIGHT).build());
        int tempoRowY = octaveRowY + CONTROL_HEIGHT + 4;
        tempoField = new TempoEditBox(font, octaveControlsX, tempoRowY,
                octaveControlsWidth, CONTROL_HEIGHT);
        tempoField.setMaxLength(4);
        tempoField.setCentered(true);
        tempoField.setValue(preservedTempo == null
                ? Integer.toString(displayedTempo()) : preservedTempo);
        boolean trackSettingsEditable = !separateMode || focusedTargets.size() <= 1;
        octaveDownButton.active = !uploading && trackSettingsEditable;
        octaveUpButton.active = !uploading && trackSettingsEditable;
        tempoField.active = !uploading && trackSettingsEditable;
        addRenderableWidget(tempoField);
        refreshOctaveLabel();
        nextY += octavePanelHeight + GAP;

        if (midi) {
            midiOptionsX = innerX;
            midiOptionsY = nextY;
            midiOptionsWidth = innerWidth;
            midiOptionsHeight = addMidiOptions(offersDrums);
            nextY += midiOptionsHeight + GAP;
        }
    }

    private int addMidiOptions(boolean offersDrums) {
        int x = midiOptionsX + 6;
        int width = Math.max(1, midiOptionsWidth - 12);
        int y = midiOptionsY + 7;
        List<InstrumentChoiceCatalog.Entry> catalog = InstrumentChoiceCatalog.entries();
        ArrayList<OverlayDropdown.Choice<String>> choices = new ArrayList<>(catalog.size());
        for (InstrumentChoiceCatalog.Entry entry : catalog) {
            choices.add(new OverlayDropdown.Choice<>(entry.id(), entry.name()));
        }
        String instrumentId = focusedTarget()
                .map(target -> model.partInstrumentIds.getOrDefault(
                        target.representative(), globalInstrumentId))
                .orElse(globalInstrumentId);
        int selected = 0;
        for (int index = 1; index < choices.size(); index++) {
            if (choices.get(index).value().equals(instrumentId)) {
                selected = index;
                break;
            }
        }
        String label = "Instrument: " + choices.get(selected).label();
        Button selector = instrumentDropdown.button("import-instrument", x, y, width,
                CONTROL_HEIGHT, label, choices, selected, 7, this::setDisplayedInstrument);
        selector.active = !uploading && focusedTargets.size() <= 1;
        addRenderableWidget(selector);
        y += CONTROL_HEIGHT + 4;

        int half = Math.max(1, (width - 4) / 2);
        if (focusedTargets.isEmpty()) {
            dynamicsButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                convertDynamics = !convertDynamics;
                refreshLabels();
            }).bounds(x, y, half, CONTROL_HEIGHT).build());
            glissandoButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                convertGlissando = !convertGlissando;
                refreshLabels();
            }).bounds(x + half + 4, y, width - half - 4, CONTROL_HEIGHT).build());
            return 58;
        }
        if (focusedTargets.size() == 1) {
            TrackTarget target = focusedTarget().orElseThrow();
            int part = target.part();
            followOctaveButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                if (!model.localOctaveParts.remove(part)) {
                    model.localOctaveParts.add(part);
                    model.partOctaves.put(part, octaveOffset);
                }
                preservedTempo = null;
                refreshLayout();
            }).bounds(x, y, half, CONTROL_HEIGHT).build());
            followTempoButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                int track = target.representative();
                if (!model.localTempoParts.remove(track)) {
                    model.localTempoParts.add(track);
                    model.partTempos.put(track, tempoBpm);
                }
                preservedTempo = null;
                refreshLayout();
            }).bounds(x + half + 4, y, width - half - 4, CONTROL_HEIGHT).build());
            y += CONTROL_HEIGHT + 4;
            if (offersDrums && analysis.drumPart(part)) {
                drumRemapButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                    InstrumentOptions.setUseDrumRemapping(!InstrumentOptions.useDrumRemapping());
                    rebuildPreview();
                    refreshLabels();
                }).bounds(x, y, width, CONTROL_HEIGHT).build());
                return focusedTarget().filter(TrackTarget::group).isPresent()
                        && expandableMerge(part) ? 106 : 82;
            }
            return focusedTarget().filter(TrackTarget::group).isPresent()
                    && expandableMerge(part) ? 82 : 58;
        }
        return 34;
    }

    private Optional<TrackTarget> focusedTarget() {
        return focusedTargets.size() == 1
                ? focusedTargets.stream().findFirst() : Optional.empty();
    }

    private TrackTarget targetFor(PartRow row) {
        return row.child() ? TrackTarget.member(row.mergeRepresentative(), row.partIndex())
                : TrackTarget.group(groupRepresentative(row.partIndex()));
    }

    private List<Integer> partsFor(TrackTarget target) {
        return target.group() ? selectedGroupParts(target.representative())
                : List.of(target.part());
    }

    private String assignedInstrumentId(int part) {
        return model.partInstrumentIds.getOrDefault(groupRepresentative(part), globalInstrumentId);
    }

    private void setDisplayedInstrument(String id) {
        if (focusedTargets.isEmpty()) globalInstrumentId = id;
        else focusedTarget().ifPresent(target -> model.partInstrumentIds.put(target.representative(), id));
        rebuildPreview();
        refreshLayout();
    }

    private int displayedTempo() {
        return focusedTarget().map(TrackTarget::representative).filter(model.localTempoParts::contains)
                .map(part -> model.partTempos.getOrDefault(part, tempoBpm)).orElse(tempoBpm);
    }

    private int displayedOctave() {
        return focusedTarget().map(TrackTarget::part).filter(model.localOctaveParts::contains)
                .map(part -> model.partOctaves.getOrDefault(part, octaveOffset)).orElse(octaveOffset);
    }

    private String nameFieldValue() {
        if (!separateMode) return preservedName == null ? defaultName() : preservedName;
        Optional<TrackTarget> focused = focusedTarget();
        if (focused.isEmpty()) return "";
        int part = focused.orElseThrow().representative();
        return model.partNames.getOrDefault(part, separatePartName(part));
    }

    private void addActions(String importLabel) {
        int footerContentTop = footerY + 1;
        int footerContentBottom = shellY + shellHeight - 4;
        int actionY = footerContentTop
                + Math.max(0, footerContentBottom - footerContentTop - CONTROL_HEIGHT) / 2;
        int actionWidth = Math.max(1, (optionsWidth - GAP) / 2);
        if (separateMode && destination.supportsSeparate(analysis.midiOptions())
                && analysis.partCount() > 1) {
            mergeButton = addRenderableWidget(Button.builder(Component.literal("Merge Selected"),
                            ignored -> runMergeAction())
                    .bounds(partsX, actionY, actionWidth, CONTROL_HEIGHT).build());
            mergeInstrumentButton = addRenderableWidget(Button.builder(
                            Component.literal("Merge Instruments"),
                            ignored -> mergeInstruments())
                    .bounds(partsX + actionWidth + GAP, actionY,
                            actionWidth, CONTROL_HEIGHT).build());
        }
        int importX = optionsX;
        int cancelX = importX + actionWidth + GAP;
        importButton = addRenderableWidget(Button.builder(Component.literal(importLabel),
                ignored -> importSelected()).bounds(importX, actionY, actionWidth, CONTROL_HEIGHT).build());
        cancelButton = addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(cancelX, actionY,
                        Math.max(1, optionsX + optionsWidth - cancelX), CONTROL_HEIGHT).build());
    }

    private void setSeparateMode(boolean separate) {
        if (uploading || !destination.supportsSeparate(analysis.midiOptions())
                || analysis.partCount() < 2
                || separateMode == separate) return;
        separateMode = separate;
        model.selectedPartIndexes.clear();
        focusedTargets.clear();
        if (separate) {
            clearSelection();
        } else {
            restoreInitialMerges();
        }
        preservedName = null;
        previewPlayback.stop();
        previewSong = null;
        previewOutput = null;
        refreshLayout();
    }

    private void togglePart(int partIndex) {
        ScoreImportSession.MergeState merge = model.merges.get(partIndex);
        if (merge != null) {
            if (merge.selectedMembers.size() == merge.members.size()) {
                merge.selectedMembers.clear();
                model.selectedPartIndexes.remove(partIndex);
            } else {
                if (merge.selectedMembers.isEmpty() && separateMode
                        && effectiveSelectedIndexes().size() >= separateCapacity) return;
                merge.selectedMembers.clear();
                merge.selectedMembers.addAll(merge.members);
                model.selectedPartIndexes.add(partIndex);
            }
        } else if (model.selectedPartIndexes.contains(partIndex)) {
            model.selectedPartIndexes.remove(partIndex);
        } else {
            if (separateMode && effectiveSelectedIndexes().size() >= separateCapacity) return;
            model.selectedPartIndexes.add(partIndex);
        }
        resetGeneratedName();
        rebuildPreview();
        refreshLabels();
    }

    private void toggleMergeMember(int representative, int member) {
        ScoreImportSession.MergeState merge = model.merges.get(representative);
        if (merge == null) return;
        LinkedHashSet<Integer> selected = merge.selectedMembers;
        if (selected.remove(member)) {
            model.selectedPartIndexes.remove(representative);
        } else {
            if (selected.isEmpty() && separateMode
                    && effectiveSelectedIndexes().size() >= separateCapacity) return;
            selected.add(member);
            if (selected.size() == merge.members.size()) model.selectedPartIndexes.add(representative);
        }
        resetGeneratedName();
        rebuildPreview();
        refreshLabels();
    }

    private void focusTarget(TrackTarget target) {
        preserveFocusedInputs();
        if (minecraft.hasControlDown()) {
            if (!focusedTargets.remove(target)) focusedTargets.add(target);
        } else if (focusedTargets.size() == 1 && focusedTargets.contains(target)) {
            focusedTargets.clear();
        } else {
            focusedTargets.clear();
            focusedTargets.add(target);
        }
        preservedName = null;
        preservedTempo = null;
        rebuildPreview();
        nameField = null;
        tempoField = null;
        clearWidgets();
        init();
    }

    private void addFocusedTarget(TrackTarget target) {
        preserveFocusedInputs();
        if (!focusedTargets.remove(target)) focusedTargets.add(target);
        preservedName = null;
        preservedTempo = null;
        rebuildPreview();
        refreshLayout();
    }

    private List<Integer> visiblePartIndexes() { return model.visiblePartIndexes(); }

    private List<PartRow> visibleRows() {
        ArrayList<PartRow> rows = new ArrayList<>();
        for (int index : visiblePartIndexes()) {
            rows.add(new PartRow(index, index, 0));
            ScoreImportSession.MergeState merge = model.merges.get(index);
            if (merge == null || merge.members.size() < 2 || !merge.expanded) continue;
            int ordinal = 1;
            for (int member : merge.members) rows.add(new PartRow(member, index, ordinal++));
        }
        return List.copyOf(rows);
    }

    private void toggleMergeExpanded(int representative) {
        ScoreImportSession.MergeState merge = model.merges.get(representative);
        if (merge == null || merge.members.size() < 2) return;
        merge.expanded = !merge.expanded;
        refreshLayout();
    }

    private Component mergeMemberLabel(PartRow row) {
        int merge = displayPosition(row.mergeRepresentative());
        String prefix = focusedTargets.contains(targetFor(row))
                ? OverlayDropdown.SELECTED_MARKER : "";
        return Component.literal(prefix + merge + "." + row.subtrackNumber()
                + " · " + partInstrumentName(row.partIndex()));
    }

    private List<Integer> groupParts(int partIndex) { return model.groupParts(partIndex); }

    private boolean expandableMerge(int partIndex) { return model.expandableMerge(partIndex); }

    private int groupRepresentative(int partIndex) { return model.groupRepresentative(partIndex); }

    private List<Integer> selectedGroupParts(int partIndex) { return model.selectedGroupParts(partIndex); }

    private String mergeLabel(int representative) {
        String prefix = focusedTargets.contains(TrackTarget.group(representative))
                ? OverlayDropdown.SELECTED_MARKER : "";
        ScoreImportSession.MergeState merge = model.merges.get(representative);
        if (merge != null && merge.instrumentGroup) {
            return prefix + displayPosition(representative) + " · "
                    + analysis.instrumentMergeLabel(merge.members);
        }
        return prefix + displayPosition(representative) + " · "
                + partChannelName(representative) + " · Grouped";
    }

    private String partChannelName(int index) {
        return partDetails(index)[0];
    }

    private String partInstrumentName(int index) {
        return partDetails(index)[1];
    }

    private String[] partDetails(int index) {
        String label = analysis.partLabel(index);
        int first = label.indexOf(" · ");
        String details = first < 0 ? label : label.substring(first + 3);
        int second = details.indexOf(" · ");
        return second < 0
                ? new String[]{details, details}
                : new String[]{details.substring(0, second), details.substring(second + 3)};
    }

    private String separatePartName(int representative) {
        ScoreImportSession.MergeState state = model.merges.get(representative);
        if (state == null) {
            return analysis.separateName(representative, includeSource,
                    includeInstrument, includePart);
        }
        if (state.sourceChannel) {
            return analysis.separateName(representative, includeSource,
                    includeInstrument, includePart);
        }
        if (state.instrumentGroup) {
            return analysis.defaultName(state.members, includeSource,
                    includeInstrument, includePart);
        }
        String merge = "Merge " + state.number;
        return includeSource && !analysis.sourceName().isBlank()
                ? analysis.sourceName() + " · " + merge : merge;
    }

    private void mergeFocused() {
        LinkedHashSet<Integer> focusedGroups = focusedTargets.stream()
                .map(TrackTarget::representative)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!separateMode || focusedGroups.size() < 2) return;
        int representative = mergeGroups(focusedGroups);
        if (representative < 0) return;
        focusedTargets.clear();
        focusedTargets.add(TrackTarget.group(representative));
        preservedName = null;
        preservedTempo = null;
        rebuildPreview();
        refreshLayout();
    }

    private void runMergeAction() {
        if (hasFocusedSubtracks()) unmergeFocusedSubtracks();
        else mergeFocused();
    }

    private boolean hasFocusedSubtracks() {
        return focusedTargets.stream().anyMatch(target -> !target.group());
    }

    private void unmergeFocusedSubtracks() {
        Map<Integer, LinkedHashSet<Integer>> removals = new HashMap<>();
        for (TrackTarget target : focusedTargets) {
            if (!target.group()) {
                removals.computeIfAbsent(target.representative(), ignored -> new LinkedHashSet<>())
                        .add(target.member());
            }
        }
        if (removals.isEmpty()) return;
        for (var entry : removals.entrySet()) {
            unmergeMembers(entry.getKey(), entry.getValue());
        }
        focusedTargets.clear();
        preservedName = null;
        preservedTempo = null;
        rebuildPreview();
        refreshLayout();
    }

    private void unmergeMembers(int representative, Set<Integer> removedMembers) { model.unmergeMembers(representative,removedMembers); }

    private void restoreUnmergedSettings(int part, String name, String instrument,
                                         Integer tempo, boolean localTempo) { model.restoreUnmergedSettings(part,name,instrument,tempo,localTempo); }

    private int mergeGroups(LinkedHashSet<Integer> focusedGroups) { return model.mergeGroups(focusedGroups); }

    private void mergeInstruments() {
        if (!separateMode) return;
        model.mergeInstruments(separateMode, tempoBpm, octaveOffset);
        focusedTargets.clear();
        preservedName = null;
        preservedTempo = null;
        rebuildPreview();
        refreshLayout();
    }

    private void restoreGroupSettings(int representative, int source,
                                      Map<Integer, String> names,
                                      Map<Integer, String> instruments,
                                      Map<Integer, Integer> tempos,
                                      Set<Integer> tempoOverrides) { model.restoreGroupSettings(representative,source,names,instruments,tempos,tempoOverrides); }

    private boolean hasInstrumentMergeCandidates() { return model.hasInstrumentMergeCandidates(); }

    private void restoreInitialMerges() { model.restoreInitialMerges(); }

    private void preserveFocusedInputs() {
        focusedTarget().filter(TrackTarget::group).ifPresent(target -> {
            if (nameField != null && !nameField.getValue().strip().isEmpty()) {
                model.partNames.put(target.representative(), nameField.getValue().strip());
            }
        });
        commitTempo();
    }

    private void selectAll() {
        if (separateMode) {
            List<Integer> visible = visiblePartIndexes();
            int limit = Math.min(visible.size(), separateCapacity);
            if (limit > 0 && model.selectedPartIndexes.size() == limit) {
                clearSelection();
            } else {
                selectAllSeparateParts();
            }
            resetGeneratedName();
            refreshLabels();
            return;
        }
        boolean drums = selectedKind().orElseGet(this::preferredDrumKind);
        List<Integer> compatible = compatibleIndexes(drums);
        if (!compatible.isEmpty() && model.selectedPartIndexes.containsAll(compatible)) {
            clearSelection();
        } else {
            clearSelection();
            for (int index : compatible) {
                ScoreImportSession.MergeState merge = model.merges.get(index);
                if (merge != null) merge.selectedMembers.addAll(merge.members);
                model.selectedPartIndexes.add(index);
            }
        }
        resetGeneratedName();
        rebuildPreview();
        refreshLabels();
    }

    private void selectAllSeparateParts() {
        List<Integer> visible = visiblePartIndexes();
        int limit = Math.min(visible.size(), separateCapacity);
        model.selectedPartIndexes.clear();
        model.selectedPartIndexes.addAll(visible.subList(0, limit));
        for (int index : visible.subList(0, limit)) {
            ScoreImportSession.MergeState merge = model.merges.get(index);
            if (merge == null) continue;
            merge.selectedMembers.clear();
            merge.selectedMembers.addAll(merge.members);
        }
    }

    private void clearSelection() {
        model.selectedPartIndexes.clear();
        for (ScoreImportSession.MergeState merge : model.merges.values()) merge.selectedMembers.clear();
    }

    private Component label(int index, int buttonWidth) {
        String prefix = focusedTargets.contains(TrackTarget.group(index))
                ? OverlayDropdown.SELECTED_MARKER : "";
        String value = displayLabel(index);
        String text = prefix + value;
        int maximumWidth = Math.max(1, buttonWidth - 10);
        while (font.width(text) > maximumWidth && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
            text = prefix + value + "…";
        }
        return Component.literal(text);
    }

    private int displayPosition(int representative) {
        int position = visiblePartIndexes().indexOf(representative);
        return position < 0 ? 1 : position + 1;
    }

    private String displayLabel(int index) {
        String label = analysis.partLabel(index);
        int separator = label.indexOf(" · ");
        if (separator >= 0) label = label.substring(separator + 3);
        ScoreImportSession.MergeState merge = model.merges.get(index);
        if (merge != null && merge.instrumentGroup) {
            label = analysis.instrumentMergeLabel(merge.members);
        }
        return displayPosition(index) + " · " + label;
    }

    private void refreshLabels() {
        Optional<Boolean> selectedKind = selectedKind();
        for (int slot = 0; slot < partButtons.size(); slot++) {
            PartRow row = partButtonRows.get(slot);
            int index = row.partIndex();
            Button button = partButtons.get(slot);
            button.setMessage(row.child() ? mergeMemberLabel(row)
                    : expandableMerge(index)
                    ? Component.literal(mergeLabel(index))
                    : label(index, button.getWidth()));
            if (slot < partCheckboxButtons.size()) {
                Button checkbox = partCheckboxButtons.get(slot);
                boolean checked = row.child()
                        ? model.merges.get(row.mergeRepresentative()).selectedMembers.contains(index)
                        : model.selectedPartIndexes.contains(index);
                checkbox.setMessage(Component.literal(checked
                        ? "☑" : "☐"));
                int representative = row.child() ? row.mergeRepresentative() : index;
                boolean selectable = separateMode
                        ? ScoreImportSession.separatePartSelectable(effectiveSelectedIndexes().contains(representative),
                        effectiveSelectedIndexes().size(), separateCapacity)
                        : selectedKind.isEmpty() || effectiveSelectedIndexes().contains(representative)
                        || analysis.drumPart(index) == selectedKind.get();
                checkbox.active = !uploading && checkbox.visible && selectable;
            }
            if (slot < partDisclosureButtons.size()) {
                Button disclosure = partDisclosureButtons.get(slot);
                ScoreImportSession.MergeState merge = model.merges.get(index);
                disclosure.setMessage(Component.literal(merge != null && merge.expanded ? "▼" : "▶"));
                disclosure.active = !uploading && disclosure.visible;
            }
            button.active = !uploading && button.visible && (row.child() || separateMode
                    || selectedKind.isEmpty() || analysis.drumPart(index) == selectedKind.get());
        }
        if (selectAllButton != null) {
            if (separateMode) {
                List<Integer> visible = visiblePartIndexes();
                int limit = Math.min(visible.size(), separateCapacity);
                boolean all = limit > 0 && model.selectedPartIndexes.size() == limit
                        && model.selectedPartIndexes.containsAll(visible.subList(0, limit));
                selectAllButton.setMessage(Component.literal(all ? "Deselect All" : "Select All"));
                selectAllButton.active = !uploading && limit > 0;
            } else {
                boolean drums = selectedKind.orElseGet(this::preferredDrumKind);
                List<Integer> compatible = compatibleIndexes(drums);
                boolean all = !compatible.isEmpty() && model.selectedPartIndexes.containsAll(compatible);
                String suffix = hasMixedKinds() ? (drums ? " Drums" : " Melodic") : "";
                selectAllButton.setMessage(Component.literal((all ? "Deselect All" : "Select All") + suffix));
                selectAllButton.active = !uploading;
            }
        }
        if (importModeButton != null) {
            importModeButton.setMessage(Component.literal(ScoreImportSession.importModeLabel(
                    separateMode, effectiveSelectedIndexes().size(), separateCapacity)));
            importModeButton.active = !uploading;
        }
        if (mergeButton != null) {
            boolean unmerge = hasFocusedSubtracks();
            mergeButton.setMessage(Component.literal(unmerge ? "Unmerge" : "Merge Selected"));
            long focusedGroups = focusedTargets.stream().map(TrackTarget::representative)
                    .distinct().count();
            mergeButton.active = separateMode && !uploading && (unmerge || focusedGroups >= 2
                    && focusedTargets.stream().map(TrackTarget::representative).distinct()
                    .map(this::groupParts).flatMap(List::stream)
                    .map(analysis::drumPart).distinct().count() == 1);
        }
        if (mergeInstrumentButton != null) {
            mergeInstrumentButton.active = separateMode && !uploading
                    && hasInstrumentMergeCandidates();
        }
        if (includeSourceButton != null) includeSourceButton.setMessage(Component.literal(
                "File · " + (includeSource ? "On" : "Off")));
        if (includeInstrumentButton != null) includeInstrumentButton.setMessage(Component.literal(
                "Inst. · " + (includeInstrument ? "On" : "Off")));
        if (includePartButton != null) includePartButton.setMessage(Component.literal(
                "Track · " + (includePart ? "On" : "Off")));
        if (includeSourceButton != null) includeSourceButton.active = !uploading;
        if (includeInstrumentButton != null) includeInstrumentButton.active = !uploading;
        if (includePartButton != null) includePartButton.active = !uploading;
        if (drumRemapButton != null) {
            drumRemapButton.active = !uploading;
            drumRemapButton.setMessage(Component.literal("Drum Remap: "
                    + (InstrumentOptions.useDrumRemapping() ? "On" : "Off")));
        }
        if (dynamicsButton != null) dynamicsButton.setMessage(Component.literal(
                "Dynamics: " + (convertDynamics ? "On" : "Off")));
        if (glissandoButton != null) glissandoButton.setMessage(Component.literal(
                "Glissando: " + (convertGlissando ? "On" : "Off")));
        if (followOctaveButton != null) followOctaveButton.setMessage(Component.literal(
                "Octave: " + (focusedTarget().map(TrackTarget::part)
                        .filter(model.localOctaveParts::contains).isPresent()
                        ? "Track" : "Global")));
        if (followTempoButton != null) followTempoButton.setMessage(Component.literal(
                "Tempo: " + (focusedTarget().map(TrackTarget::representative)
                        .filter(model.localTempoParts::contains).isPresent()
                        ? "Track" : "Global")));
        if (nameField != null) {
            nameField.active = !uploading && (!separateMode
                    || focusedTarget().filter(TrackTarget::group).isPresent());
            nameField.setHint(Component.literal(separateMode
                    ? focusedTargets.isEmpty()
                    ? ScoreImportSession.separateNameTemplate(analysis, includeSource, includeInstrument, includePart)
                    : focusedTargets.size() == 1
                    ? (focusedTarget().filter(TrackTarget::group).isPresent()
                    ? "Track name" : "Grouped subtrack")
                    : "Select one track to rename"
                    : "Imported score name"));
        }
        if (tempoField != null) tempoField.active = !uploading
                && (!separateMode || focusedTargets.size() <= 1);
        if (octaveDownButton != null) octaveDownButton.active = !uploading
                && (!separateMode || focusedTargets.size() <= 1);
        if (octaveUpButton != null) octaveUpButton.active = !uploading
                && (!separateMode || focusedTargets.size() <= 1);
        if (importButton != null) {
            int selectedCount = effectiveSelectedIndexes().size();
            importButton.active = !uploading && selectedCount > 0
                    && (!separateMode || selectedCount <= separateCapacity);
            importButton.setMessage(Component.literal(uploading
                    ? uploadLabel() : "Import"));
        }
        if (cancelButton != null) cancelButton.active = true;
        refreshPreviewControls();
    }

    private void importSelected() {
        if (!commitTempo()) return;
        List<Integer> selected = selectedIndexes();
        if (separateMode) {
            importSeparate(selected);
            return;
        }
        selected = expandedIndexes(selected);
        try {
            boolean anyDrums = selected.stream().anyMatch(analysis::drumPart);
            boolean allDrums = anyDrums && selected.stream().allMatch(analysis::drumPart);
            boolean remapDrums = InstrumentOptions.useDrumRemapping();
            boolean mapDrumsHere = ScoreImportSession.useClientDrumMap(
                    remapDrums, destination.songbook(), anyDrums, allDrums);
            List<Integer> drumMap = mapDrumsHere ? InstrumentOptions.drumNoteMap() : List.of();
            String name = importName();
            MusicScore song = analysis.song(selected, drumMap)
                    .withConversion(convertDynamics, convertGlissando)
                    .withInitialBpm(tempoBpm).transposeOctaves(octaveOffset).renamed(name)
                    .withMetadata("", false, globalInstrumentId);
            float tuning = song.tuningHz();
            if (song.tempoMap().microsAt(song.lengthTick()) > 7_200_000_000L) {
                throw new IllegalArgumentException("Tempo would make the score longer than two hours");
            }
            if (analysis.midiOptions()) {
                MusicDebugTelemetry.event("midi_import", "confirmed",
                        "destination", destination.kind().name(), "parts", selected.size(),
                        "drum_parts", selected.stream().filter(analysis::drumPart).count(),
                        "drum_remapping", remapDrums,
                        "map_applied_client", mapDrumsHere, "octave", octaveOffset,
                        "tempo_bpm", song.initialBpm(), "tuning_hz", tuning,
                        "notes", song.notes().size(), "name", name);
            } else {
                MusicDebugTelemetry.event("abc_import", "confirmed",
                        "destination", destination.kind().name(), "voices", selected.size(),
                        "octave", octaveOffset, "tempo_bpm", song.initialBpm(),
                        "notes", song.notes().size(), "name", name);
            }
            List<Integer> serverDrumMap = ScoreImportSession.useServerDrumMap(
                    remapDrums, destination.songbook(), song.percussion())
                    ? InstrumentOptions.drumNoteMap() : List.of();
            if (destination.complete(song, name, serverDrumMap)) {
                previewPlayback.stop();
            } else if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(
                        "The import target is no longer available.")
                        .withStyle(ChatFormatting.RED));
            }
        } catch (IllegalArgumentException exception) {
            MusicDebugTelemetry.warning(analysis.formatName().toLowerCase() + "_import", "rejected",
                    exception, "destination", destination.kind().name(), "parts", selected.size());
            if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(
                    analysis.formatName() + " part could not be loaded: " + exception.getMessage())
                    .withStyle(ChatFormatting.RED));
        }
    }

    private void importSeparate(List<Integer> selected) {
        try {
            if (selected.isEmpty() || selected.size() > separateCapacity) {
                throw new IllegalArgumentException(destination.separateCapacityError());
            }
            boolean remapDrums = InstrumentOptions.useDrumRemapping();
            ArrayList<SheetBatchCodec.Entry> entries = new ArrayList<>(selected.size());
            for (int index : selected) {
                if (focusedTarget().filter(TrackTarget::group)
                        .filter(target -> target.representative() == index).isPresent()
                        && nameField != null && !nameField.getValue().strip().isEmpty()) {
                    model.partNames.put(groupRepresentative(index), nameField.getValue().strip());
                }
                int track = groupRepresentative(index);
                String name = model.partNames.getOrDefault(track, separatePartName(track));
                int partTempo = model.localTempoParts.contains(track)
                        ? model.partTempos.getOrDefault(track, tempoBpm) : tempoBpm;
                List<Integer> importParts = selectedGroupParts(index);
                if (importParts.isEmpty()) continue;
                MusicScore song = transposeParts(analysis.song(importParts, List.of()),
                                importParts, track)
                        .withConversion(convertDynamics, convertGlissando)
                        .withInitialBpm(partTempo).renamed(name)
                        .withMetadata("", false, assignedInstrumentId(index));
                if (song.tempoMap().microsAt(song.lengthTick()) > 7_200_000_000L) {
                    throw new IllegalArgumentException("Tempo would make a score longer than two hours");
                }
                List<Integer> drumMap = remapDrums && song.percussion()
                        ? InstrumentOptions.drumNoteMap() : List.of();
                entries.add(new SheetBatchCodec.Entry(name, song, drumMap));
            }
            uploading = true;
            uploadSentBytes = 0;
            uploadTotalBytes = 0;
            for (var child : children()) {
                if (child instanceof AbstractWidget widget) widget.active = false;
            }
            if (cancelButton != null) cancelButton.active = true;
            batchTransactionId = destination.completeBatch(entries,
                    new SheetBatchImportClient.Listener() {
                        @Override public void progress(int sentBytes, int totalBytes) {
                            uploadSentBytes = sentBytes;
                            uploadTotalBytes = totalBytes;
                            refreshLabels();
                        }

                        @Override public void complete(boolean success, String message) {
                            uploading = false;
                            batchTransactionId = null;
                            if (success) {
                                destination.openBatchResult();
                                return;
                            }
                            if (minecraft.player != null && message != null && !message.isBlank()) {
                                minecraft.player.sendSystemMessage(Component.literal(message)
                                        .withStyle(ChatFormatting.RED));
                            }
                            refreshLayout();
                        }
                    });
            if (batchTransactionId == null) {
                uploading = false;
                throw new IllegalArgumentException("The separate import could not start");
            }
            MusicDebugTelemetry.event(analysis.formatName().toLowerCase() + "_batch_import",
                    "confirmed", "parts", selected.size(), "octave", octaveOffset,
                    "tempo_bpm", tempoBpm, "drum_remapping", remapDrums);
            refreshLabels();
        } catch (IllegalArgumentException exception) {
            uploading = false;
            refreshLayout();
            if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(
                    analysis.formatName() + " parts could not be loaded: " + exception.getMessage())
                    .withStyle(ChatFormatting.RED));
        }
    }

    private String uploadLabel() {
        if (uploadTotalBytes <= 0) return "Preparing…";
        int percent = Math.clamp((int) Math.round(uploadSentBytes * 100.0D / uploadTotalBytes), 0, 100);
        return percent < 100 ? "Uploading " + percent + "%" : "Finishing…";
    }

    private void changeOctave(int delta) {
        int current = displayedOctave();
        int next = Math.clamp(current + delta, -8, 8);
        if (next == current) return;
        try {
            Optional<TrackTarget> focused = focusedTarget();
            if (separateMode && focused.isPresent()) {
                TrackTarget target = focused.orElseThrow();
                int part = target.part();
                int representative = target.representative();
                List<Integer> parts = partsFor(target);
                if (parts.isEmpty()) parts = List.of(part);
                validateOctaveChange(analysis.song(parts, List.of()), parts,
                        representative, part, next);
                model.localOctaveParts.add(part);
                model.partOctaves.put(part, next);
            } else {
                if (separateMode) {
                    for (int index : selectedIndexes()) {
                        List<Integer> parts = selectedGroupParts(index);
                        transposeParts(analysis.song(parts, List.of()), parts, index, next);
                    }
                } else {
                    analysis.song(expandedIndexes(selectedIndexes()), List.of())
                            .transposeOctaves(next);
                }
                octaveOffset = next;
                InstrumentOptions.setImportOctaveOffset(next);
            }
            refreshOctaveLabel();
            refreshLabels();
            rebuildPreview();
        } catch (IllegalArgumentException ignored) { }
    }

    private void refreshOctaveLabel() {
        int value = displayedOctave();
        if (octaveLabel != null) octaveLabel.setMessage(Component.literal(value == 0
                ? "Original" : "Octave " + (value > 0 ? "+" : "") + value));
    }

    private List<Integer> selectedIndexes() { return model.selectedIndexes(); }

    private List<Integer> effectiveSelectedIndexes() { return model.effectiveSelectedIndexes(); }

    private List<Integer> expandedIndexes(List<Integer> representatives) { return model.expandedIndexes(representatives); }

    private MusicScore transposeParts(MusicScore song, List<Integer> parts,
                                             int representative) {
        return transposeParts(song, parts, representative, octaveOffset);
    }

    private MusicScore transposeParts(MusicScore song, List<Integer> parts,
                                             int representative, int fallback) {
        int groupOctave = model.localOctaveParts.contains(representative)
                ? model.partOctaves.getOrDefault(representative, fallback) : fallback;
        ArrayList<Integer> octaves = new ArrayList<>(parts.size());
        for (int part : parts) {
            octaves.add(model.localOctaveParts.contains(part)
                    ? model.partOctaves.getOrDefault(part, groupOctave) : groupOctave);
        }
        return song.transposeTracks(octaves);
    }

    private void validateOctaveChange(MusicScore song, List<Integer> parts,
                                      int representative, int changedPart, int next) {
        int groupOctave = changedPart == representative ? next
                : model.localOctaveParts.contains(representative)
                ? model.partOctaves.getOrDefault(representative, octaveOffset) : octaveOffset;
        ArrayList<Integer> octaves = new ArrayList<>(parts.size());
        for (int part : parts) {
            octaves.add(part == changedPart ? next : model.localOctaveParts.contains(part)
                    ? model.partOctaves.getOrDefault(part, groupOctave) : groupOctave);
        }
        song.transposeTracks(octaves);
    }

    private Optional<Boolean> selectedKind() {
        return effectiveSelectedIndexes().stream().findFirst().map(analysis::drumPart);
    }

    private boolean preferredDrumKind() {
        for (int index = 0; index < analysis.partCount(); index++) {
            if (!analysis.drumPart(index)) return false;
        }
        return analysis.partCount() > 0;
    }

    private List<Integer> compatibleIndexes(boolean drums) {
        ArrayList<Integer> result = new ArrayList<>();
        for (int index : visiblePartIndexes()) {
            if (analysis.drumPart(index) == drums) result.add(index);
        }
        return List.copyOf(result);
    }











    private void trimEffectiveSelectionToCapacity(int capacity) {
        List<Integer> selected = effectiveSelectedIndexes();
        int safeCapacity = Math.max(0, capacity);
        for (int index = selected.size() - 1; index >= safeCapacity; index--) {
            int representative = selected.get(index);
            model.selectedPartIndexes.remove(representative);
            ScoreImportSession.MergeState merge = model.merges.get(representative);
            if (merge != null) merge.selectedMembers.clear();
        }
    }

    private boolean hasMixedKinds() {
        boolean drums = false;
        boolean melodic = false;
        for (int index = 0; index < analysis.partCount(); index++) {
            if (analysis.drumPart(index)) drums = true;
            else melodic = true;
        }
        return drums && melodic;
    }

    private boolean hasDrumParts() {
        for (int index = 0; index < analysis.partCount(); index++) {
            if (analysis.drumPart(index)) return true;
        }
        return false;
    }





    static int namePanelHeight(boolean midi) {
        return midi ? 56 : 32;
    }

    private String importName() {
        String value = nameField == null ? "" : nameField.getValue().strip();
        return value.isEmpty() ? defaultName() : value;
    }

    private String defaultName() {
        return ScoreImportSession.generatedName(analysis, separateMode ? selectedIndexes()
                        : expandedIndexes(selectedIndexes()), includeSource,
                includeInstrument, includePart);
    }



    private void resetGeneratedName() {
        if (nameField == null) return;
        nameField.setValue(separateMode ? "" : defaultName());
        nameField.setCursorPosition(0);
        nameField.setHighlightPos(0);
    }

    private void refreshLayout() {
        if (nameField != null) {
            if (separateMode && focusedTargets.size() == 1) {
                focusedTarget().filter(TrackTarget::group).ifPresent(target ->
                        model.partNames.put(target.representative(),
                                nameField.getValue().strip()));
            } else if (!separateMode) {
                preservedName = nameField.getValue();
            }
        }
        if (tempoField != null) preservedTempo = tempoField.getValue();
        clearWidgets();
        init();
    }

    private boolean commitTempo() {
        if (tempoField == null) return true;
        try {
            int value = Integer.parseInt(tempoField.getValue().strip());
            if (value < 1 || value > 3_000) throw new NumberFormatException();
            int previous = displayedTempo();
            Optional<TrackTarget> focused = focusedTarget();
            int track = focused.map(TrackTarget::representative).orElse(-1);
            if (track >= 0 && model.localTempoParts.contains(track)) {
                model.partTempos.put(track, value);
            } else {
                tempoBpm = value;
            }
            preservedTempo = Integer.toString(value);
            tempoField.setValue(preservedTempo);
            tempoField.setTextColor(0xFFE0E0E0);
            if (previous != value) rebuildPreview();
            return true;
        } catch (NumberFormatException invalid) {
            tempoField.setTextColor(0xFFFF5555);
            return false;
        }
    }

    private void rebuildPreview() {
        if (separateMode && focusedTargets.size() != 1) {
            previewPlayback.stop();
            previewSong = null;
            previewOutput = null;
            refreshPreviewControls();
            return;
        }
        InstrumentMidiPlayback.TransportState previous =
                previewPlayback.transportState(displayedTempo());
        TrackTarget focused = separateMode ? focusedTarget().orElseThrow() : null;
        List<Integer> selected = separateMode
                ? partsFor(focused)
                : expandedIndexes(selectedIndexes());
        if (selected.isEmpty()) {
            previewPlayback.stop();
            previewSong = null;
            previewOutput = null;
            refreshPreviewControls();
            return;
        }
        try {
            boolean drums = selected.stream().allMatch(analysis::drumPart);
            List<Integer> drumMap = drums && InstrumentOptions.useDrumRemapping()
                    ? InstrumentOptions.drumNoteMap() : List.of();
            int previewTempo = displayedTempo();
            int previewOctave = displayedOctave();
            String assigned = separateMode ? assignedInstrumentId(selected.getFirst())
                    : globalInstrumentId;
            int representative = separateMode
                    ? focused.representative() : -1;
            MusicScore baseSong = analysis.song(selected, drumMap);
            MusicScore nextSong = (separateMode
                    ? transposeParts(baseSong, selected, representative)
                    : baseSong.transposeOctaves(previewOctave))
                    .withConversion(convertDynamics, convertGlissando)
                    .withInitialBpm(previewTempo)
                    .withMetadata("", false, assigned);
            InstrumentMidiScreenAccess nextOutput;
            if (InstrumentChoiceCatalog.resolve(assigned) != null) {
                nextOutput = new ScoreImportPreviewOutput(
                        InstrumentChoiceCatalog.resolve(assigned));
            } else if (drums) {
                nextOutput = new ScoreImportPreviewOutput((IItemInstrument) Items.DRUM_KIT);
            } else if (destination.previewInstrument() != null) {
                nextOutput = destination.previewInstrument();
            } else {
                nextOutput = new ScoreImportPreviewOutput((IItemInstrument) Items.HARP_MC);
            }
            previewPlayback.stop();
            previewSong = nextSong;
            previewOutput = nextOutput;
            previewPlayback.attach(nextOutput);
            long duration = previewPlayback.durationMicros(nextSong, previewTempo);
            long playhead = Math.clamp(previous.playheadMicros(), 0L, duration);
            previewPlayback.seek(nextSong, playhead, previewTempo, 0);
            if (previous.playing() && playhead < duration) {
                previewPlayback.playPause(nextSong, previewTempo, 0);
            }
        } catch (IllegalArgumentException ignored) {
            previewPlayback.stop();
            previewSong = null;
            previewOutput = null;
        }
        refreshPreviewControls();
    }

    private boolean previewAvailable() {
        return previewSong != null && previewOutput != null
                && previewOutput.playbackInstrumentPresent();
    }

    private void refreshPreviewControls() {
        boolean available = previewAvailable();
        for (AbstractWidget control : previewControls) control.active = available;
        if (previewScrubber != null) previewScrubber.refresh();
        if (previewPlayPause != null) previewPlayPause.setMessage(Component.literal(
                previewTransport.playing() ? "II" : "▶"));
    }

    @Override public void tick() {
        super.tick();
        int currentCapacity = destination.separateCapacity();
        if (currentCapacity != separateCapacity) {
            separateCapacity = currentCapacity;
            if (separateMode) {
                trimEffectiveSelectionToCapacity(separateCapacity);
                resetGeneratedName();
            }
            refreshLabels();
        }
        if (!separateMode) previewPlayback.validateInstrumentBinding();
        refreshPreviewControls();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY,
                                           double horizontal, double vertical) {
        if (instrumentDropdown.mouseScrolled(mouseX, mouseY, vertical)) return true;
        if (vertical != 0.0D && containsPartViewport(mouseX, mouseY)) {
            int maximum = Math.max(0, partContentHeight - partViewportHeight);
            int next = Math.clamp(scroll - (int) Math.round(vertical * 22.0D), 0, maximum);
            if (next != scroll) {
                scroll = next;
                refreshLayout();
            }
            return maximum > 0;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (instrumentDropdown.mouseClicked(event)) return true;
        if (instrumentDropdown.closeFromOutsideClick(event)) return true;
        if (event.button() == 0 && partContentHeight > partViewportHeight
                && event.x() >= scrollTrackX
                && event.x() < scrollTrackX + scrollTrackWidth
                && event.y() >= scrollTrackY
                && event.y() < scrollTrackY + scrollTrackHeight) {
            int thumbHeight = ScrollbarGeometry.scrollThumbHeight(
                    scrollTrackHeight, partViewportHeight, partContentHeight);
            int thumbY = ScrollbarGeometry.scrollThumbY(scrollTrackY, scrollTrackHeight,
                    thumbHeight, partViewportHeight, partContentHeight, scroll);
            scrollDragOffset = event.y() >= thumbY && event.y() < thumbY + thumbHeight
                    ? (int) Math.round(event.y() - thumbY) : thumbHeight / 2;
            scrollbarDragging = true;
            updateScrollFromMouse(event.y());
            return true;
        }
        if (containsPartViewport(event.x(), event.y())) {
            for (int slot = 0; slot < partButtons.size(); slot++) {
                Button button = partButtons.get(slot);
                if (!button.visible || !button.isMouseOver(event.x(), event.y())) continue;
                PartRow row = partButtonRows.get(slot);
                if (event.button() == 1) {
                    addFocusedTarget(targetFor(row));
                    return true;
                }
                break;
            }
        }
        boolean handled = super.mouseClicked(event, doubled);
        if (!handled && event.button() == 0 && !focusedTargets.isEmpty()) {
            preserveFocusedInputs();
            focusedTargets.clear();
            preservedName = null;
            preservedTempo = null;
            rebuildPreview();
            nameField = null;
            tempoField = null;
            clearWidgets();
            init();
            return true;
        }
        return handled;
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

    private boolean containsPartViewport(double mouseX, double mouseY) {
        return mouseX >= partViewportX && mouseX < partViewportX + partViewportWidth
                && mouseY >= partViewportY && mouseY < partViewportY + partViewportHeight;
    }

    private void updateScrollFromMouse(double mouseY) {
        int maximum = Math.max(0, partContentHeight - partViewportHeight);
        int thumbHeight = ScrollbarGeometry.scrollThumbHeight(
                scrollTrackHeight, partViewportHeight, partContentHeight);
        int travel = Math.max(1, scrollTrackHeight - thumbHeight);
        int thumbTop = Math.clamp((int) Math.round(mouseY) - scrollDragOffset,
                scrollTrackY, scrollTrackY + travel);
        int next = Math.clamp(Math.round((thumbTop - scrollTrackY)
                * maximum / (float) travel), 0, maximum);
        if (next == scroll) return;
        scroll = next;
        refreshLayout();
    }

    @Override public void onClose() {
        if (batchTransactionId != null) SheetBatchImportClient.cancel(batchTransactionId);
        batchTransactionId = null;
        uploading = false;
        previewPlayback.stop();
        AsyncMusicImport.cancel();
        destination.close();
    }

    @Override public void removed() {
        if (batchTransactionId != null) SheetBatchImportClient.cancel(batchTransactionId);
        batchTransactionId = null;
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
        PianoInsetPanel.draw(graphics, partsX, partsY, partsWidth, partsHeight);
        if (partContentHeight > partViewportHeight) {
            graphics.fill(scrollTrackX, scrollTrackY,
                    scrollTrackX + scrollTrackWidth, scrollTrackY + scrollTrackHeight, 0xFF252D34);
            int thumbHeight = ScrollbarGeometry.scrollThumbHeight(
                    scrollTrackHeight, partViewportHeight, partContentHeight);
            int thumbY = ScrollbarGeometry.scrollThumbY(scrollTrackY, scrollTrackHeight,
                    thumbHeight, partViewportHeight, partContentHeight, scroll);
            graphics.fill(scrollTrackX, thumbY,
                    scrollTrackX + scrollTrackWidth, thumbY + thumbHeight, 0xFF758895);
        }
        PianoInsetPanel.draw(graphics, optionsX, optionsY, optionsWidth, optionsHeight);
        PianoInsetPanel.draw(graphics, nameFieldPanelX, nameFieldPanelY,
                nameFieldPanelWidth, nameFieldPanelHeight);
        PianoInsetPanel.draw(graphics, octavePanelX, octavePanelY,
                octavePanelWidth, octavePanelHeight);
        graphics.text(font, Component.literal("Octave"),
                octavePanelX + 8, octavePanelY + (octavePanelHeight == 106 ? 61 : 13), TEXT);
        graphics.text(font, Component.literal("Tempo"),
                octavePanelX + 8, octavePanelY + (octavePanelHeight == 106 ? 85 : 37), TEXT);
        if (analysis.midiOptions()) {
            if (midiOptionsHeight > 0) {
                PianoInsetPanel.draw(graphics, midiOptionsX, midiOptionsY,
                        midiOptionsWidth, midiOptionsHeight);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }

    private final class TempoEditBox extends M3EEditBox {
        private TempoEditBox(Font font, int x, int y, int width, int height) {
            super(ScorePartImportScreen.this, font, x, y, width, height,
                    Component.literal("Import tempo"));
            setHint(Component.literal("Tempo BPM"));
        }

        @Override protected void onFocusLost() { commitTempo(); }
    }

    private static final class LeftAlignedButton extends Button {
        private final Font font;

        private LeftAlignedButton(Font font, int x, int y, int width, int height,
                                  Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.font = font;
        }

        @Override protected void extractContents(GuiGraphicsExtractor graphics,
                                                 int mouseX, int mouseY,
                                                 float partialTick) {
            extractDefaultSprite(graphics);
            ActiveTextCollector text = graphics.textRendererForWidget(
                    this, GuiGraphicsExtractor.HoveredTextEffects.NONE);
            int textY = getY() + Math.max(0, getHeight() - font.lineHeight) / 2 + 1;
            text.accept(TextAlignment.LEFT, getX() + 6, textY, getMessage());
        }
    }

    private record PartRow(int partIndex, int mergeRepresentative, int subtrackNumber) {
        private boolean child() { return subtrackNumber > 0; }
    }

    private record TrackTarget(int representative, Integer member) {
        private static TrackTarget group(int representative) {
            return new TrackTarget(representative, null);
        }

        private static TrackTarget member(int representative, int member) {
            return new TrackTarget(representative, member);
        }

        private boolean group() { return member == null; }
        private int part() { return group() ? representative : member; }
    }


}
