package m3e.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import m3e.dexed.DexedJavaEngine;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;
import m3e.sound.InstrumentSoundProfile;

import java.util.function.IntFunction;
import java.util.function.Supplier;

/** Concept-faithful DX7 voice editor embedded in the piano Instrument drawer. */
final class DexedAdvancedPanel {
    private static final int CONTROL_HEIGHT = InstrumentPanelGeometry.BUTTON_HEIGHT;
    private static final int GAP = 3;
    private static final int CARD_INSET = 6;
    private static final int STAGE_GAP = 6;
    private static final int NODE_HEIGHT = 15;
    private static final int MIN_NODE_WIDTH = 18;
    private static final int MAX_NODE_WIDTH = 18;
    private static final int NODE_GAP = 3;
    private static final int ALGORITHM_ROW_STEP = 22;
    private static final int DIAGRAM_PADDING = 3;
    private static final int SCROLLBAR_GUTTER = 8;
    private static final int EDIT_HEADER_HEIGHT = 25;
    private static final int MAX_DROPDOWN_ROWS = 7;
    private static final String[] CURVES = {"- Lin", "- Exp", "+ Exp", "+ Lin"};
    private static final String[] LFO_WAVES = {
            "Triangle", "Saw down", "Saw up", "Square", "Sine", "Sample & hold"
    };
    private static final String[] STAGE_NAMES = {
            "Atk", "Dec", "Sus", "Rel"
    };
    private static final String[] OPERATOR_STAGE_NAMES = {
            "Atk", "Dec", "Sus", "Rel"
    };
    private static final String[] BREAK_NAMES = {
            "A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#"
    };

    /* [display operator, x, y, legacy route hint, OPS7 feedback-loop shape]. */
    private static final int[][][] ALGORITHM_LAYOUTS = {
            {{6,3,0,0,1},{5,3,1,0,0},{4,3,2,0,0},{3,3,3,2,0},{2,2,2,0,0},{1,2,3,1,0}},
            {{6,3,0,0,0},{5,3,1,0,0},{4,3,2,0,0},{3,3,3,2,0},{2,2,2,0,1},{1,2,3,1,0}},
            {{6,3,1,0,1},{5,3,2,0,0},{4,3,3,2,0},{3,2,1,0,0},{2,2,2,0,0},{1,2,3,1,0}},
            {{6,3,1,0,2},{5,3,2,0,0},{4,3,3,2,0},{3,2,1,0,0},{2,2,2,0,0},{1,2,3,1,0}},
            {{6,4,2,0,1},{5,4,3,2,0},{4,3,2,0,0},{3,3,3,1,0},{2,2,2,0,0},{1,2,3,1,0}},
            {{6,4,2,0,3},{5,4,3,2,0},{4,3,2,0,0},{3,3,3,1,0},{2,2,2,0,0},{1,2,3,1,0}},
            {{6,4,1,0,1},{5,4,2,7,0},{4,3,2,0,0},{3,3,3,2,0},{2,2,2,0,0},{1,2,3,1,0}},
            {{6,4,1,0,0},{5,4,2,7,0},{4,3,2,0,4},{3,3,3,2,0},{2,2,2,0,0},{1,2,3,1,0}},
            {{6,4,1,0,0},{5,4,2,7,0},{4,3,2,0,0},{3,3,3,2,0},{2,2,2,0,1},{1,2,3,1,0}},
            {{6,2,2,0,0},{5,1,2,1,0},{4,2,3,1,0},{3,3,1,0,1},{2,3,2,0,0},{1,3,3,2,0}},
            {{6,2,2,0,1},{5,1,2,1,0},{4,2,3,1,0},{3,3,1,0,0},{2,3,2,0,0},{1,3,3,2,0}},
            {{6,3,2,7,0},{5,2,2,0,0},{4,1,2,1,0},{3,2,3,6,0},{2,4,2,0,1},{1,4,3,2,0}},
            {{6,3,2,7,1},{5,2,2,0,0},{4,1,2,1,0},{3,2,3,6,0},{2,4,2,0,0},{1,4,3,2,0}},
            {{6,4,1,7,1},{5,3,1,0,0},{4,3,2,0,0},{3,3,3,2,0},{2,2,2,0,0},{1,2,3,1,0}},
            {{6,4,1,7,0},{5,3,1,0,0},{4,3,2,0,0},{3,3,3,2,0},{2,2,2,0,4},{1,2,3,1,0}},
            {{6,4,1,0,1},{5,4,2,7,0},{4,3,1,0,0},{3,3,2,0,0},{2,2,2,1,0},{1,3,3,0,0}},
            {{6,4,1,0,0},{5,4,2,7,0},{4,3,1,0,0},{3,3,2,0,0},{2,2,2,1,4},{1,3,3,0,0}},
            {{6,4,0,0,0},{5,4,1,0,0},{4,4,2,7,0},{3,3,2,0,4},{2,2,2,1,0},{1,3,3,0,0}},
            {{6,3,2,3,1},{5,4,3,2,0},{4,3,3,1,0},{3,2,1,0,0},{2,2,2,0,0},{1,2,3,1,0}},
            {{6,4,2,0,0},{5,3,2,1,0},{4,4,3,2,0},{3,1,2,3,1},{2,2,3,6,0},{1,1,3,1,0}},
            {{6,3,2,3,0},{5,4,3,2,0},{4,3,3,1,0},{3,1,2,3,1},{2,2,3,1,0},{1,1,3,1,0}},
            {{6,3,2,4,1},{5,4,3,2,0},{4,3,3,1,0},{3,2,3,1,0},{2,1,2,0,0},{1,1,3,1,0}},
            {{6,3,2,3,1},{5,4,3,2,0},{4,3,3,1,0},{3,2,2,0,0},{2,2,3,1,0},{1,1,3,1,0}},
            {{6,3,2,4,1},{5,4,3,2,0},{4,3,3,1,0},{3,2,3,1,0},{2,1,3,1,0},{1,0,3,1,0}},
            {{6,3,2,3,1},{5,4,3,2,0},{4,3,3,1,0},{3,2,3,1,0},{2,1,3,1,0},{1,0,3,1,0}},
            {{6,4,2,0,1},{5,3,2,1,0},{4,4,3,2,0},{3,2,2,0,0},{2,2,3,6,0},{1,1,3,1,0}},
            {{6,5,2,0,0},{5,4,2,1,0},{4,5,3,2,0},{3,2,2,0,1},{2,2,3,6,0},{1,1,3,1,0}},
            {{6,4,3,2,0},{5,3,1,0,1},{4,3,2,0,0},{3,3,3,1,0},{2,2,2,0,0},{1,2,3,1,0}},
            {{6,4,2,0,1},{5,4,3,2,0},{4,3,2,0,0},{3,3,3,1,0},{2,2,3,1,0},{1,1,3,1,0}},
            {{6,4,3,2,0},{5,3,1,0,1},{4,3,2,0,0},{3,3,3,1,0},{2,2,3,1,0},{1,1,3,1,0}},
            {{6,4,2,0,1},{5,4,3,2,0},{4,3,3,1,0},{3,2,3,1,0},{2,1,3,1,0},{1,0,3,1,0}},
            {{6,5,3,2,1},{5,4,3,1,0},{4,3,3,1,0},{3,2,3,1,0},{2,1,3,1,0},{1,0,3,1,0}}
    };

    private final Minecraft client;
    private final Screen screen;
    private final java.util.function.Consumer<AbstractWidget> add;
    private final Runnable rerender;
    private final Supplier<DexedEditorTarget> targetSupplier;
    private Tab tab = Tab.EDIT;
    private int activeOperator = 1;
    private int scroll;
    private int[] copiedOperator;
    private int viewportX;
    private int viewportY;
    private int viewportWidth;
    private int viewportHeight;
    private int contentHeight;
    private final OverlayDropdown.Controller dropdowns;
    private int scrollTrackX;
    private int scrollTrackY;
    private int scrollTrackWidth;
    private int scrollTrackHeight;
    private int scrollDragOffset;
    private boolean scrollbarDragging;
    private boolean patchEditable = true;

    DexedAdvancedPanel(Minecraft client, Screen screen,
                       java.util.function.Consumer<AbstractWidget> add, Runnable rerender) {
        this(client, screen, add, rerender, DexedPatchClient::editorTarget);
    }

    DexedAdvancedPanel(Minecraft client, Screen screen,
                       java.util.function.Consumer<AbstractWidget> add, Runnable rerender,
                       Supplier<DexedEditorTarget> targetSupplier) {
        this.client = client;
        this.screen = screen;
        this.add = add;
        this.rerender = rerender;
        this.targetSupplier = targetSupplier;
        this.dropdowns = new OverlayDropdown.Controller(rerender);
    }

    void open() {
        tab = Tab.EDIT;
        activeOperator = 1;
        scroll = 0;
        dropdowns.close();
        scrollbarDragging = false;
    }

    void render(int x, int y, int width, int bottom, DexedEditorTarget fallback,
                boolean editable, Runnable close) {
        patchEditable = editable;
        int height = Math.max(1, bottom - y);
        add.accept(new Backdrop(x, y, width, height));
        int inset = InstrumentPanelGeometry.SECTION_INSET;
        int backWidth = Math.min(72, Math.max(54, width / 7));
        int backX = x + width - backWidth - inset;
        int tabAreaWidth = Math.min(226,
                Math.max(1, backX - (x + inset) - GAP));
        int tabInnerWidth = Math.max(1, tabAreaWidth - GAP * 2);
        int editWidth = Math.max(1, tabInnerWidth * 28 / 100);
        int lfoWidth = Math.max(1, tabInnerWidth * 44 / 100);
        int miscWidth = Math.max(1, tabInnerWidth - editWidth - lfoWidth);
        add.accept(tabButton("Edit", Tab.EDIT, x + inset, y + inset, editWidth));
        add.accept(tabButton("LFO & Pitch", Tab.LFO_PITCH,
                x + inset + editWidth + GAP, y + inset, lfoWidth));
        add.accept(tabButton("Misc", Tab.MISC,
                x + inset + editWidth + GAP + lfoWidth + GAP,
                y + inset, miscWidth));
        add.accept(Button.builder(Component.literal("Back"), ignored -> {
                    dropdowns.close();
                    close.run();
                }).bounds(backX, y + inset,
                        backWidth, CONTROL_HEIGHT).build());

        viewportX = x + inset;
        int editorY = y + CONTROL_HEIGHT + inset * 2;
        viewportY = editorY + (tab == Tab.EDIT ? EDIT_HEADER_HEIGHT : 0);
        viewportWidth = Math.max(1, width - inset * 2);
        viewportHeight = Math.max(1, bottom - viewportY - inset);
        dropdowns.beginFrame(editorY, viewportY + viewportHeight);
        contentHeight = switch (tab) {
            case EDIT -> 244;
            case LFO_PITCH -> 167;
            case MISC -> 150;
        };
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        scroll = Math.clamp(scroll, 0, maxScroll);
        add.accept(new EditorCanvas(viewportX, viewportY, viewportWidth, viewportHeight,
                scroll, tab, currentPatch(fallback)));
        if (tab == Tab.EDIT) {
            renderEditHeader(viewportX, editorY, viewportWidth, fallback);
            renderEdit(viewportX, viewportY, viewportWidth, viewportHeight, fallback);
        } else if (tab == Tab.LFO_PITCH) {
            renderLfo(viewportX, viewportY, viewportWidth, viewportHeight, fallback);
        } else {
            renderMisc(viewportX, viewportY, viewportWidth, viewportHeight, fallback);
        }
        scrollTrackWidth = 5;
        scrollTrackX = viewportX + viewportWidth - scrollTrackWidth - 1;
        scrollTrackY = viewportY;
        scrollTrackHeight = viewportHeight;
        if (contentHeight <= viewportHeight) scrollbarDragging = false;
        add.accept(new ScrollBar(scrollTrackX, scrollTrackY, scrollTrackWidth,
                scrollTrackHeight, viewportHeight, contentHeight, scroll));
        dropdowns.addOverlay(add);
    }

    void mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (mouseX < viewportX || mouseX >= viewportX + viewportWidth
                || mouseY < viewportY || mouseY >= viewportY + viewportHeight) return;
        if (dropdowns.mouseScrolled(mouseX, mouseY, vertical)) return;
        dropdowns.close();
        int maximum = Math.max(0, contentHeight - viewportHeight);
        int next = Math.clamp(scroll - (int) Math.round(vertical * 22D), 0, maximum);
        if (next != scroll) {
            scroll = next;
            rerender.run();
        }
    }

    void mouseClicked(MouseButtonEvent event) {
        if (event.button() != 0 || contentHeight <= viewportHeight
                || event.x() < scrollTrackX
                || event.x() >= scrollTrackX + scrollTrackWidth
                || event.y() < scrollTrackY
                || event.y() >= scrollTrackY + scrollTrackHeight) return;
        dropdowns.close();
        int thumbHeight = ScrollbarGeometry.scrollThumbHeight(
                scrollTrackHeight, viewportHeight, contentHeight);
        int thumbY = ScrollbarGeometry.scrollThumbY(scrollTrackY, scrollTrackHeight,
                thumbHeight, viewportHeight, contentHeight, scroll);
        scrollDragOffset = event.y() >= thumbY && event.y() < thumbY + thumbHeight
                ? (int) Math.round(event.y() - thumbY) : thumbHeight / 2;
        scrollbarDragging = true;
        updateScrollFromMouse(event.y());
    }

    void mouseDragged(MouseButtonEvent event) {
        if (scrollbarDragging && event.button() == 0) updateScrollFromMouse(event.y());
    }

    void mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) scrollbarDragging = false;
    }

    private void updateScrollFromMouse(double mouseY) {
        int maximum = Math.max(0, contentHeight - viewportHeight);
        int thumbHeight = ScrollbarGeometry.scrollThumbHeight(
                scrollTrackHeight, viewportHeight, contentHeight);
        int travel = Math.max(1, scrollTrackHeight - thumbHeight);
        int thumbTop = Math.clamp((int) Math.round(mouseY) - scrollDragOffset,
                scrollTrackY, scrollTrackY + travel);
        int next = Math.clamp(Math.round((thumbTop - scrollTrackY)
                * maximum / (float) travel), 0, maximum);
        if (next == scroll) return;
        scroll = next;
        rerender.run();
    }

    private AbstractWidget tabButton(String label, Tab target, int x, int y, int width) {
        Button button = Button.builder(Component.literal((tab == target ? "\u25b6 " : "") + label), ignored -> {
            tab = target;
            scroll = 0;
            dropdowns.close();
            rerender.run();
        }).bounds(x, y, width, CONTROL_HEIGHT).build();
        button.active = tab != target;
        return button;
    }

    private void renderEditHeader(int x, int y, int width,
                                  DexedEditorTarget fallback) {
        DexedPatch patch = currentPatch(fallback);
        int usableWidth = width;
        int leftWidth = Math.clamp(usableWidth * 31 / 100, 142, 164);
        int sectionGap = InstrumentPanelGeometry.SECTION_GAP;
        int rightX = x + leftWidth + sectionGap;
        int rightWidth = Math.max(1, usableWidth - leftWidth - sectionGap);
        int algorithm = patch.value(134);

        Button previousAlgorithm = Button.builder(Component.literal("\u25c0"), ignored ->
                        set(fallback, 134, (currentPatch(fallback).value(134) + 31) % 32, true))
                        .bounds(x, y + 1, 24, CONTROL_HEIGHT).build();
        previousAlgorithm.active = patchEditable;
        addHeader(previousAlgorithm);
        addHeader(dropdown("algorithm", x + 27, y + 1,
                Math.max(1, leftWidth - 54), "Algorithm", algorithm,
                labels(32, value -> Integer.toString(value + 1)),
                value -> set(fallback, 134, value, true)));
        Button nextAlgorithm = Button.builder(Component.literal("\u25b6"), ignored ->
                        set(fallback, 134, (currentPatch(fallback).value(134) + 1) % 32, true))
                        .bounds(x + leftWidth - 24, y + 1, 24, CONTROL_HEIGHT).build();
        nextAlgorithm.active = patchEditable;
        addHeader(nextAlgorithm);

        int actionsWidth = 42 + GAP + 43 + GAP + 45;
        int actionsX = rightX + rightWidth - actionsWidth;
        addHeader(Button.builder(Component.literal("Copy"), ignored -> copyOperator(fallback))
                .bounds(actionsX, y + 1, 42, CONTROL_HEIGHT).build());
        Button paste = Button.builder(Component.literal("Paste"), ignored -> pasteOperator(fallback))
                .bounds(actionsX + 42 + GAP, y + 1, 43, CONTROL_HEIGHT).build();
        paste.active = copiedOperator != null && patchEditable;
        addHeader(paste);
        addHeader(operatorIndicator(actionsX + 42 + GAP + 43 + GAP,
                y + 1, 45, fallback));
    }

    private void renderEdit(int x, int y, int width, int height,
                            DexedEditorTarget fallback) {
        DexedPatch patch = currentPatch(fallback);
        int usableWidth = width - SCROLLBAR_GUTTER;
        int leftWidth = Math.clamp(usableWidth * 31 / 100, 142, 164);
        int sectionGap = InstrumentPanelGeometry.SECTION_GAP;
        int rightX = x + leftWidth + sectionGap;
        int rightWidth = Math.max(1, usableWidth - leftWidth - sectionGap);
        int algorithm = patch.value(134);

        int top = 0;
        int diagramX = x + 3;
        int diagramWidth = leftWidth - 6;
        int nodeWidth = algorithmNodeWidth(diagramWidth);
        int[][] layout = ALGORITHM_LAYOUTS[algorithm];
        int diagramY = centeredAlgorithmBaseY(sy(y, top), layout);
        for (int[] node : layout) {
            int nodeX = diagramX + centeredAlgorithmNodeX(
                    diagramWidth, nodeWidth, layout, node[1]);
            int nodeY = diagramY + node[2] * ALGORITHM_ROW_STEP;
            int displayOperator = node[0];
            boolean carrier = isCarrier(algorithm, displayOperator);
            boolean enabled = operatorEnabled(patch, displayOperator);
            OperatorNode widget = new OperatorNode(nodeX, nodeY, nodeWidth, NODE_HEIGHT,
                    displayOperator, carrier, enabled,
                    displayOperator == activeOperator, () -> {
                dropdowns.close();
                activeOperator = displayOperator;
                rerender.run();
            }, () -> toggleOperator(fallback, displayOperator));
            addVisible(widget, y, height);
        }

        int secondTop = top + 106;
        addVisible(new PatchSlider(x + 5, sy(y, secondTop + 6), leftWidth - 10,
                "Feedback", 135, 7, fallback, Integer::toString), y, height);

        int groupGap = InstrumentPanelGeometry.SECTION_GAP;
        int groupWidth = Math.max(1, (rightWidth - groupGap) / 2);
        int col2 = rightX + groupWidth + groupGap;
        int base = storageOperator(activeOperator) * 21;
        addSliderRows(rightX + CARD_INSET, y, top + 16,
                groupWidth - CARD_INSET * 2, height, fallback,
                new Field("Mode", base + 17, 1, value -> value == 0 ? "Ratio" : "Fixed", true),
                new Field("Coarse", base + 18, 31),
                new Field("Fine", base + 19, 99),
                new Field("Detune", base + 20, 14, value -> signed(value - 7), false));
        addSliderRows(col2 + CARD_INSET, y, top + 16,
                groupWidth - CARD_INSET * 2, height, fallback,
                new Field("Output", base + 16, 99),
                new Field("Velocity", base + 15, 7),
                new Field("Amp mod", base + 14, 3),
                new Field("Rate scale", base + 13, 7));

        addSliderRows(rightX + CARD_INSET, y, secondTop + 16,
                groupWidth - CARD_INSET * 2, height, fallback,
                new Field("Break point", base + 8, 99, DexedAdvancedPanel::breakPointName, true),
                new Field("Left depth", base + 9, 99),
                new Field("L curve", base + 11, 3, DexedAdvancedPanel::curveName, true),
                new Field("Right depth", base + 10, 99),
                new Field("R curve", base + 12, 3, DexedAdvancedPanel::curveName, true));

        int stageWidth = Math.max(1,
                (groupWidth - CARD_INSET * 2 - STAGE_GAP) / 2);
        for (int stage = 0; stage < 4; stage++) {
            int stageX = col2 + CARD_INSET
                    + (stage % 2) * (stageWidth + STAGE_GAP);
            int stageTop = secondTop + 16 + (stage / 2) * 58;
            int stageIndex = stage;
            addVisible(new PatchSlider(stageX + 3, sy(y, stageTop + 12),
                    Math.max(1, stageWidth - 6),
                    "R", base + stageIndex, 99, fallback, Integer::toString), y, height);
            addVisible(new PatchSlider(stageX + 3, sy(y, stageTop + 31),
                    Math.max(1, stageWidth - 6),
                    "L", base + 4 + stageIndex, 99, fallback, Integer::toString), y, height);
        }
    }

    private void renderLfo(int x, int y, int width, int height,
                           DexedEditorTarget fallback) {
        int usableWidth = width - SCROLLBAR_GUTTER;
        int groupGap = InstrumentPanelGeometry.SECTION_GAP;
        int groupWidth = Math.max(1, (usableWidth - groupGap) / 2);
        int rightX = x + groupWidth + groupGap;
        addSliderRows(x + CARD_INSET, y, 18,
                groupWidth - CARD_INSET * 2, height, fallback,
                new Field("Wave shape", 142, 5, value -> LFO_WAVES[value], true),
                new Field("Speed", 137, 99),
                new Field("Fade-in delay", 138, 99),
                new Field("Pitch depth", 139, 99),
                new Field("Pitch sensitivity", 143, 7),
                new Field("Amplitude depth", 140, 99));
        addVisible(onOffToggle(x + CARD_INSET, sy(y, 138),
                groupWidth - CARD_INSET * 2,
                "Restart LFO on each note", 141, fallback), y, height);

        int stageWidth = Math.max(1,
                (groupWidth - CARD_INSET * 2 - STAGE_GAP) / 2);
        for (int stage = 0; stage < 4; stage++) {
            int stageX = rightX + CARD_INSET
                    + (stage % 2) * (stageWidth + STAGE_GAP);
            int stageTop = 19 + (stage / 2) * 71;
            addVisible(new PatchSlider(stageX + 3, sy(y, stageTop + 18),
                    Math.max(1, stageWidth - 6),
                    "Rate", 126 + stage, 99, fallback, Integer::toString), y, height);
            addVisible(new PatchSlider(stageX + 3, sy(y, stageTop + 39),
                    Math.max(1, stageWidth - 6),
                    "Level", 130 + stage, 99, fallback, Integer::toString), y, height);
        }

    }

    private void renderMisc(int x, int y, int width, int height,
                            DexedEditorTarget fallback) {
        int usableWidth = width - SCROLLBAR_GUTTER;
        int controlX = x + CARD_INSET;
        int controlWidth = Math.max(1, usableWidth - CARD_INSET * 2);
        addVisible(new PerformanceSlider(controlX, sy(y, 18), controlWidth,
                PerformanceField.COARSE, fallback), y, height);
        addVisible(new PerformanceSlider(controlX, sy(y, 38), controlWidth,
                PerformanceField.FINE, fallback), y, height);
        InstrumentProfileEditor.addTuning(screen, client.font, controlX, sy(y, 58),
                controlWidth, CONTROL_HEIGHT, GAP, 40, 42,
                widget -> addVisible(widget, y, height),
                InstrumentProfileEditor.binding(this::currentProfile, this::applyProfile),
                rerender);

        addVisible(onOffToggle(controlX, sy(y, 103), controlWidth,
                "Key sync", 136, fallback), y, height);
        DexedPerformance performance = currentPerformance(fallback);
        int performanceY = sy(y, 123);
        int half = Math.max(1, (controlWidth - GAP) / 2);
        addVisible(Button.builder(Component.literal("Glissando: "
                        + (performance.glissando() ? "On" : "Off")), ignored -> {
                    DexedPerformance current = currentPerformance(fallback);
                    applyPerformance(fallback,
                            current.withGlissando(!current.glissando()));
                    rerender.run();
                }).bounds(controlX, performanceY, half,
                        CONTROL_HEIGHT).build(), y, height);
        Button polyBehavior = Button.builder(Component.literal("Poly glide: "
                        + (performance.polyPortamento()
                        == DexedPerformance.PolyPortamento.RETAIN
                        ? "Retain" : "Follow")), ignored -> {
                    DexedPerformance current = currentPerformance(fallback);
                    DexedPerformance.PolyPortamento next = current.polyPortamento()
                            == DexedPerformance.PolyPortamento.RETAIN
                            ? DexedPerformance.PolyPortamento.FOLLOW
                            : DexedPerformance.PolyPortamento.RETAIN;
                    applyPerformance(fallback, current.withPolyPortamento(next));
                    rerender.run();
                }).bounds(controlX + half + GAP, performanceY,
                        Math.max(1, controlWidth - half - GAP),
                        CONTROL_HEIGHT).build();
        polyBehavior.active = !performance.mono();
        addVisible(polyBehavior, y, height);
    }

    private void addSliderRows(int x, int viewportY, int contentY, int width,
                               int viewportHeight, DexedEditorTarget fallback,
                               Field... fields) {
        for (int row = 0; row < fields.length; row++) {
            Field field = fields[row];
            int widgetY = sy(viewportY, contentY + row * 20);
            AbstractWidget widget;
            if (field.categorical && "Mode".equals(field.label)) {
                int value = currentPatch(fallback).value(field.index);
                widget = Button.builder(Component.literal(field.label + ": "
                                + field.display.apply(value)), ignored ->
                                set(fallback, field.index, value == 0 ? 1 : 0, true))
                        .bounds(x, widgetY, width, CONTROL_HEIGHT).build();
                widget.active = patchEditable;
            } else if (field.categorical) {
                int value = currentPatch(fallback).value(field.index);
                widget = dropdown("parameter:" + field.index, x, widgetY, width,
                        field.label, value,
                        labels(field.maximum + 1, field.display),
                        selected -> set(fallback, field.index, selected, true));
            } else {
                widget = new PatchSlider(x, widgetY, width, field.label, field.index,
                        field.maximum, fallback, field.display);
            }
            addVisible(widget, viewportY, viewportHeight);
        }
    }

    private AbstractWidget dropdown(String key, int x, int y, int width,
                                    String label, int selected, String[] values,
                                    java.util.function.IntConsumer select) {
        java.util.List<OverlayDropdown.Choice<Integer>> choices =
                new java.util.ArrayList<>(values.length);
        for (int index = 0; index < values.length; index++) {
            choices.add(new OverlayDropdown.Choice<>(index, values[index]));
        }
        Button result = dropdowns.button(key, x, y, width, CONTROL_HEIGHT,
                label + ": " + values[selected], choices, selected,
                MAX_DROPDOWN_ROWS, select::accept);
        result.active = patchEditable;
        return result;
    }

    private static String[] labels(int count, IntFunction<String> display) {
        String[] values = new String[count];
        for (int value = 0; value < count; value++) values[value] = display.apply(value);
        return values;
    }

    private AbstractWidget toggle(int x, int y, int width, String label, int index,
                                  DexedEditorTarget fallback) {
        boolean enabled = currentPatch(fallback).value(index) != 0;
        Button result = Button.builder(Component.literal(label + ": "
                        + (enabled ? "On" : "Off")), ignored ->
                        set(fallback, index, currentPatch(fallback).value(index) == 0 ? 1 : 0, true))
                .bounds(x, y, width, CONTROL_HEIGHT).build();
        result.active = patchEditable;
        return result;
    }

    private AbstractWidget onOffToggle(int x, int y, int width, String label,
                                       int index, DexedEditorTarget fallback) {
        return toggle(x, y, width, label, index, fallback);
    }

    private AbstractWidget operatorIndicator(int x, int y, int width,
                                              DexedEditorTarget fallback) {
        int bit = 1 << storageOperator(activeOperator);
        boolean enabled = (currentPatch(fallback).value(155) & bit) != 0;
        Button indicator = Button.builder(Component.literal(enabled ? "On" : "Off"),
                        ignored -> toggleOperator(fallback, activeOperator))
                .bounds(x, y, width, CONTROL_HEIGHT).build();
        indicator.active = patchEditable;
        return indicator;
    }

    private void toggleOperator(DexedEditorTarget fallback, int displayOperator) {
        int mask = currentPatch(fallback).value(155);
        set(fallback, 155, toggledOperatorMask(mask, displayOperator), true);
    }

    static int toggledOperatorMask(int mask, int displayOperator) {
        return mask ^ (1 << storageOperator(displayOperator));
    }

    private void copyOperator(DexedEditorTarget fallback) {
        copiedOperator = new int[21];
        int base = storageOperator(activeOperator) * 21;
        DexedPatch patch = currentPatch(fallback);
        for (int index = 0; index < copiedOperator.length; index++) {
            copiedOperator[index] = patch.value(base + index);
        }
        rerender.run();
    }

    private void pasteOperator(DexedEditorTarget fallback) {
        if (copiedOperator == null || !patchEditable) return;
        byte[] bytes = currentPatch(fallback).bytes();
        int base = storageOperator(activeOperator) * 21;
        for (int index = 0; index < copiedOperator.length; index++) {
            bytes[base + index] = (byte) copiedOperator[index];
        }
        apply(fallback, new DexedPatch(bytes), true);
        rerender.run();
    }

    private void set(DexedEditorTarget fallback, int index, int value,
                     boolean rerenderAfter) {
        if (!patchEditable) return;
        apply(fallback, currentPatch(fallback).with(index, value), false);
        if (rerenderAfter) rerender.run();
    }

    private void apply(DexedEditorTarget fallback, DexedPatch patch, boolean force) {
        DexedEditorTarget current = targetSupplier.get();
        DexedEditorTarget held = current == null ? fallback : current;
        held.apply(patch, held.performance(), force);
    }

    private DexedPatch currentPatch(DexedEditorTarget fallback) {
        DexedEditorTarget current = targetSupplier.get();
        return current == null ? fallback.patch() : current.patch();
    }

    private DexedPerformance currentPerformance(DexedEditorTarget fallback) {
        DexedEditorTarget current = targetSupplier.get();
        return current == null ? fallback.performance() : current.performance();
    }

    private InstrumentSoundProfile currentProfile() {
        DexedEditorTarget target = targetSupplier.get();
        return target == null ? InstrumentSoundProfile.DEFAULT : target.profile();
    }

    private void applyProfile(InstrumentSoundProfile profile) {
        DexedEditorTarget target = targetSupplier.get();
        if (target != null) target.setProfile(profile);
    }

    private void applyPerformance(DexedEditorTarget fallback,
                                  DexedPerformance performance) {
        DexedEditorTarget current = targetSupplier.get();
        DexedEditorTarget held = current == null ? fallback : current;
        held.apply(held.patch(), performance, false);
    }

    private int sy(int viewportY, int contentY) {
        return viewportY + contentY - scroll;
    }

    private void addVisible(AbstractWidget widget, int viewportY, int viewportHeight) {
        boolean intersects = ViewportClippedWidget.intersects(widget,
                viewportX, viewportY, viewportWidth, viewportHeight);
        widget.visible = intersects;
        widget.active = widget.active && intersects;
        if (dropdowns.isOpen() && !dropdowns.isOpenButton(widget)) {
            widget.active = false;
        }
        if (intersects) {
            add.accept(ViewportClippedWidget.clip(widget,
                    viewportX, viewportY, viewportWidth, viewportHeight));
        }
    }

    private void addHeader(AbstractWidget widget) {
        if (dropdowns.isOpen() && !dropdowns.isOpenButton(widget)) {
            widget.active = false;
        }
        add.accept(widget);
    }

    static int storageOperator(int displayOperator) {
        return 5 - (displayOperator - 1);
    }

    static boolean isCarrier(int algorithm, int displayOperator) {
        return DexedJavaEngine.isCarrier(algorithm, storageOperator(displayOperator));
    }

    static boolean operatorEnabled(DexedPatch patch, int displayOperator) {
        int bit = 1 << storageOperator(displayOperator);
        return (patch.value(155) & bit) != 0;
    }

    static int algorithmNodeWidth(int diagramWidth) {
        int available = Math.max(1,
                diagramWidth - DIAGRAM_PADDING * 2 - NODE_GAP * 5);
        return Math.clamp(available / 6, MIN_NODE_WIDTH, MAX_NODE_WIDTH);
    }

    static int algorithmNodeX(int diagramWidth, int gridX) {
        int nodeWidth = algorithmNodeWidth(diagramWidth);
        int step = Math.max(nodeWidth + NODE_GAP,
                (diagramWidth - DIAGRAM_PADDING * 2 - nodeWidth) / 5);
        return DIAGRAM_PADDING + Math.clamp(gridX, 0, 5) * step;
    }

    private static int centeredAlgorithmNodeX(int diagramWidth, int nodeWidth,
                                              int[][] layout, int gridX) {
        int step = algorithmNodeX(diagramWidth, 1) - algorithmNodeX(diagramWidth, 0);
        int minimum = 5;
        int maximum = 0;
        for (int[] node : layout) {
            minimum = Math.min(minimum, node[1]);
            maximum = Math.max(maximum, node[1]);
        }
        int occupiedWidth = (maximum - minimum) * step + nodeWidth;
        int leftExtension = 0;
        int rightExtension = 0;
        for (int[] node : layout) {
            if (node[4] == 4 && node[1] == minimum) leftExtension = 4;
            else if (node[4] != 0 && node[1] == maximum) rightExtension = 4;
        }
        int start = Math.max(0,
                (diagramWidth - occupiedWidth - leftExtension - rightExtension) / 2
                        + leftExtension);
        return start + (Math.clamp(gridX, minimum, maximum) - minimum) * step;
    }

    private static int centeredAlgorithmBaseY(int cardY, int[][] layout) {
        int visualTop = ALGORITHM_ROW_STEP * 3;
        for (int[] node : layout) {
            int nodeTop = node[2] * ALGORITHM_ROW_STEP;
            visualTop = Math.min(visualTop, node[4] == 0 ? nodeTop : nodeTop - 4);
        }
        int visualBottom = ALGORITHM_ROW_STEP * 3 + NODE_HEIGHT + 4;
        int visualHeight = visualBottom - visualTop;
        return cardY + (100 - visualHeight) / 2 - visualTop;
    }

    static String breakPointName(int value) {
        int bounded = Math.clamp(value, 0, 99);
        return BREAK_NAMES[bounded % 12] + ((bounded + 9) / 12 - 1);
    }

    static int[][] algorithmLayout(int algorithm) {
        int[][] source = ALGORITHM_LAYOUTS[Math.clamp(algorithm, 0, 31)];
        int[][] copy = new int[source.length][];
        for (int index = 0; index < source.length; index++) copy[index] = source[index].clone();
        return copy;
    }

    static String operatorStageName(int stage) {
        return OPERATOR_STAGE_NAMES[Math.clamp(stage, 0, OPERATOR_STAGE_NAMES.length - 1)];
    }

    static String pitchStageName(int stage) {
        return STAGE_NAMES[Math.clamp(stage, 0, STAGE_NAMES.length - 1)];
    }

    static String curveName(int value) {
        return CURVES[Math.clamp(value, 0, CURVES.length - 1)];
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private enum Tab { EDIT, LFO_PITCH, MISC }

    private enum PerformanceField { COARSE, FINE }

    private record Field(String label, int index, int maximum,
                         IntFunction<String> display, boolean categorical) {
        private Field(String label, int index, int maximum) {
            this(label, index, maximum, Integer::toString, false);
        }
    }

    private final class PatchSlider extends AbstractSliderButton {
        private final String label;
        private final int index;
        private final int maximum;
        private final DexedEditorTarget fallback;
        private final IntFunction<String> display;

        private PatchSlider(int x, int y, int width, String label, int index, int maximum,
                            DexedEditorTarget fallback, IntFunction<String> display) {
            super(x, y, width, CONTROL_HEIGHT, Component.empty(),
                    currentPatch(fallback).value(index) / (double) maximum);
            this.label = label;
            this.index = index;
            this.maximum = maximum;
            this.fallback = fallback;
            this.display = display;
            active = patchEditable;
            updateMessage();
        }

        private int selectedValue() {
            return Math.clamp((int) Math.round(value * maximum), 0, maximum);
        }

        @Override protected void updateMessage() {
            setMessage(Component.literal(label + ": " + display.apply(selectedValue())));
        }

        @Override protected void applyValue() {
            int selected = selectedValue();
            value = selected / (double) maximum;
            set(fallback, index, selected, false);
            updateMessage();
        }
    }

    private final class PerformanceSlider extends AbstractSliderButton {
        private final PerformanceField field;
        private final DexedEditorTarget fallback;

        private PerformanceSlider(int x, int y, int width, PerformanceField field,
                                  DexedEditorTarget fallback) {
            super(x, y, width, CONTROL_HEIGHT, Component.empty(),
                    initial(field, currentPerformance(fallback)));
            this.field = field;
            this.fallback = fallback;
            updateMessage();
        }

        private static double initial(PerformanceField selected,
                                      DexedPerformance performance) {
            return switch (selected) {
                case COARSE -> (performance.coarseSemitones() - DexedPerformance.MIN_COARSE)
                        / (double) (DexedPerformance.MAX_COARSE - DexedPerformance.MIN_COARSE);
                case FINE -> (performance.fineCents() - DexedPerformance.MIN_FINE)
                        / (double) (DexedPerformance.MAX_FINE - DexedPerformance.MIN_FINE);
            };
        }

        @Override protected void updateMessage() {
            DexedPerformance performance = currentPerformance(fallback);
            setMessage(Component.literal(switch (field) {
                case COARSE -> "Coarse pitch: " + signed(performance.coarseSemitones()) + " st";
                case FINE -> "Fine pitch: " + signed(performance.fineCents()) + " ct";
            }));
        }

        @Override protected void applyValue() {
            DexedPerformance performance = currentPerformance(fallback);
            DexedPerformance next = switch (field) {
                case COARSE -> performance.withCoarse(Math.round(
                        DexedPerformance.MIN_COARSE + (float) value
                                * (DexedPerformance.MAX_COARSE - DexedPerformance.MIN_COARSE)));
                case FINE -> performance.withFine(Math.round(
                        DexedPerformance.MIN_FINE + (float) value
                                * (DexedPerformance.MAX_FINE - DexedPerformance.MIN_FINE)));
            };
            applyPerformance(fallback, next);
            updateMessage();
        }
    }

    private static final class Backdrop extends AbstractWidget {
        private Backdrop(int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Advanced synthesizer editor"));
            active = false;
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                           int mouseX, int mouseY,
                                                           float partialTick) {
            PianoInsetPanel.draw(graphics, getX(), getY(), getWidth(), getHeight());
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }

    private static final class OperatorNode extends AbstractWidget {
        private static final int MODULATOR_EDGE = 0xFF75693F;
        private static final int MODULATOR_FACE = 0xFF3B331D;
        private static final int MODULATOR_TEXT = 0xFFF4E3A5;
        private static final int CARRIER_EDGE = 0xFF4C7C92;
        private static final int CARRIER_FACE = 0xFF193B49;
        private static final int CARRIER_TEXT = 0xFFE6F7FF;
        private static final int SELECTED_EDGE = 0xFF58B7E8;
        private final boolean carrier;
        private final boolean enabled;
        private final boolean selected;
        private final Runnable selectAction;
        private final Runnable toggleAction;

        private OperatorNode(int x, int y, int width, int height, int number,
                             boolean carrier, boolean enabled,
                             boolean selected, Runnable selectAction,
                             Runnable toggleAction) {
            super(x, y, width, height, Component.literal(Integer.toString(number)));
            this.carrier = carrier;
            this.enabled = enabled;
            this.selected = selected;
            this.selectAction = selectAction;
            this.toggleAction = toggleAction;
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                           int mouseX, int mouseY,
                                                           float partialTick) {
            int edge = carrier ? CARRIER_EDGE : MODULATOR_EDGE;
            int face = carrier ? CARRIER_FACE : MODULATOR_FACE;
            int text = carrier ? CARRIER_TEXT : MODULATOR_TEXT;
            graphics.fill(getX(), getY(), getRight(), getBottom(), edge);
            graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1,
                    face);
            if (selected) graphics.outline(getX() - 1, getY() - 1,
                    getWidth() + 2, getHeight() + 2, SELECTED_EDGE);
            graphics.centeredText(Minecraft.getInstance().font, getMessage(),
                    getX() + getWidth() / 2, getY() + 3, text);
            if (!enabled) graphics.fill(getX() + 1, getY() + 1,
                    getRight() - 1, getBottom() - 1, 0x66000000);
        }

        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 1) toggleAction.run();
            else selectAction.run();
        }

        @Override protected boolean isValidClickButton(MouseButtonInfo button) {
            return button.button() == 0 || button.button() == 1;
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static final class EditorCanvas extends AbstractWidget {
        private final int scroll;
        private final Tab tab;
        private final DexedPatch patch;

        private EditorCanvas(int x, int y, int width, int height,
                             int scroll, Tab tab, DexedPatch patch) {
            super(x, y, width, height, Component.literal("Synth editor controls"));
            this.scroll = scroll;
            this.tab = tab;
            this.patch = patch;
            active = false;
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                           int mouseX, int mouseY,
                                                           float partialTick) {
            graphics.enableScissor(getX(), getY(), getRight(), getBottom());
            if (tab == Tab.EDIT) drawEdit(graphics);
            else if (tab == Tab.LFO_PITCH) drawLfo(graphics);
            else drawMisc(graphics);
            graphics.disableScissor();
        }

        private void drawEdit(GuiGraphicsExtractor graphics) {
            int usableWidth = getWidth() - SCROLLBAR_GUTTER;
            int leftWidth = Math.clamp(usableWidth * 31 / 100, 142, 164);
            int sectionGap = InstrumentPanelGeometry.SECTION_GAP;
            int rightX = getX() + leftWidth + sectionGap;
            int rightWidth = Math.max(1, usableWidth - leftWidth - sectionGap);
            int groupWidth = Math.max(1, (rightWidth - sectionGap) / 2);
            int col2 = rightX + groupWidth + sectionGap;
            int top = 0;
            int secondTop = top + 106;
            card(graphics, getX(), cy(top), leftWidth, 100, "");
            drawAlgorithm(graphics, getX() + 3, cy(top), leftWidth - 6,
                    patch.value(134));
            card(graphics, getX(), cy(secondTop), leftWidth, 30, "");
            card(graphics, rightX, cy(top), groupWidth, 100, "Frequency");
            card(graphics, col2, cy(top), groupWidth, 100, "Level & response");
            card(graphics, rightX, cy(secondTop), groupWidth, 120, "Keyboard scaling");
            card(graphics, col2, cy(secondTop), groupWidth, 132, "Envelope");
            int stageWidth = Math.max(1,
                    (groupWidth - CARD_INSET * 2 - STAGE_GAP) / 2);
            for (int stage = 0; stage < 4; stage++) {
                int stageX = col2 + CARD_INSET
                        + (stage % 2) * (stageWidth + STAGE_GAP);
                int stageY = cy(secondTop + 16 + (stage / 2) * 58);
                miniCard(graphics, stageX, stageY, stageWidth, 52,
                        operatorStageName(stage));
            }
        }

        private void drawLfo(GuiGraphicsExtractor graphics) {
            int usableWidth = getWidth() - SCROLLBAR_GUTTER;
            int sectionGap = InstrumentPanelGeometry.SECTION_GAP;
            int groupWidth = Math.max(1, (usableWidth - sectionGap) / 2);
            int rightX = getX() + groupWidth + sectionGap;
            card(graphics, getX() + 1, cy(1), groupWidth, 166, "LFO");
            card(graphics, rightX, cy(1), groupWidth, 166, "Pitch envelope");
            int stageWidth = Math.max(1,
                    (groupWidth - CARD_INSET * 2 - STAGE_GAP) / 2);
            for (int stage = 0; stage < 4; stage++) {
                int stageX = rightX + CARD_INSET
                        + (stage % 2) * (stageWidth + STAGE_GAP);
                int stageY = cy(20 + (stage / 2) * 71);
                miniCard(graphics, stageX, stageY, stageWidth, 65,
                        pitchStageName(stage));
            }
        }

        private void drawMisc(GuiGraphicsExtractor graphics) {
            int usableWidth = getWidth() - SCROLLBAR_GUTTER;
            card(graphics, getX() + 1, cy(1), usableWidth, 82, "Pitch");
            card(graphics, getX() + 1, cy(88), usableWidth, 60, "Performance");
        }

        private void drawAlgorithm(GuiGraphicsExtractor graphics, int x, int cardY,
                                   int width, int algorithm) {
            int nodeWidth = algorithmNodeWidth(width);
            int[][] layout = ALGORITHM_LAYOUTS[algorithm];
            int y = centeredAlgorithmBaseY(cardY, layout);
            for (int[] target : layout) {
                for (int[] source : layout) {
                    if (!DexedJavaEngine.modulates(algorithm,
                            storageOperator(source[0]), storageOperator(target[0]))) continue;
                    drawModulationPath(graphics,
                            nodeCenterX(x, width, nodeWidth, layout, source),
                            nodeTopY(y, source) + NODE_HEIGHT,
                            nodeCenterX(x, width, nodeWidth, layout, target),
                            nodeTopY(y, target), 0xFF77797D);
                }
            }
            drawCarrierBus(graphics, x, y, width, nodeWidth, algorithm, layout);
            for (int[] node : layout) {
                if (node[4] != 0) drawFeedbackLoop(graphics, x, y, width,
                        nodeWidth, algorithm, layout, node, node[4]);
            }
        }

        private static void drawCarrierBus(GuiGraphicsExtractor graphics,
                                           int x, int y, int width, int nodeWidth,
                                           int algorithm, int[][] layout) {
            int busY = y + ALGORITHM_ROW_STEP * 3 + NODE_HEIGHT + 4;
            int minimum = Integer.MAX_VALUE;
            int maximum = Integer.MIN_VALUE;
            int carriers = 0;
            for (int[] node : layout) {
                if (!isCarrier(algorithm, node[0])) continue;
                int centerX = nodeCenterX(x, width, nodeWidth, layout, node);
                graphics.verticalLine(centerX, nodeTopY(y, node) + NODE_HEIGHT - 1,
                        busY, 0xFF77797D);
                minimum = Math.min(minimum, centerX);
                maximum = Math.max(maximum, centerX);
                carriers++;
            }
            if (carriers > 1) {
                graphics.horizontalLine(minimum, maximum, busY, 0xFF77797D);
            }
        }

        private static void drawFeedbackLoop(GuiGraphicsExtractor graphics,
                                             int x, int y, int width, int nodeWidth,
                                             int algorithm, int[][] layout,
                                             int[] node, int shape) {
            int nodeX = x + centeredAlgorithmNodeX(width, nodeWidth, layout, node[1]);
            int nodeY = nodeTopY(y, node);
            int centerX = nodeX + nodeWidth / 2;
            int topY = nodeY - 4;
            int sideX = shape == 4 ? nodeX - 4 : nodeX + nodeWidth + 4;
            graphics.verticalLine(centerX, topY, nodeY + 1, 0xFF77797D);
            graphics.horizontalLine(Math.min(centerX, sideX), Math.max(centerX, sideX),
                    topY, 0xFF77797D);

            Integer branchY = feedbackBranchJoinY(x, y, width, nodeWidth,
                    algorithm, layout, node, shape, centerX, sideX);
            if (branchY != null) {
                graphics.verticalLine(sideX, topY, branchY, 0xFF77797D);
                return;
            }

            int depth = shape == 2 ? 2 : shape == 3 ? 1 : 0;
            int bottomY = nodeY + depth * ALGORITHM_ROW_STEP + NODE_HEIGHT + 2;
            graphics.verticalLine(sideX, topY, bottomY, 0xFF77797D);
            graphics.horizontalLine(Math.min(centerX, sideX), Math.max(centerX, sideX),
                    bottomY, 0xFF77797D);
        }

        private static Integer feedbackBranchJoinY(int x, int y, int width,
                                                   int nodeWidth, int algorithm,
                                                   int[][] layout, int[] source,
                                                   int shape, int sourceX,
                                                   int sideX) {
            if (shape != 1 && shape != 4) return null;
            int[] nearest = null;
            int nearestDistance = Integer.MAX_VALUE;
            for (int[] target : layout) {
                if (!DexedJavaEngine.modulates(algorithm,
                        storageOperator(source[0]), storageOperator(target[0]))) continue;
                int targetX = nodeCenterX(x, width, nodeWidth, layout, target);
                boolean reachesSide = shape == 4 ? targetX <= sideX : targetX >= sideX;
                if (!reachesSide) continue;
                int distance = Math.abs(targetX - sourceX);
                if (distance < nearestDistance) {
                    nearest = target;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) return null;

            int sourceY = nodeTopY(y, source) + NODE_HEIGHT;
            int targetX = nodeCenterX(x, width, nodeWidth, layout, nearest);
            int targetY = nodeTopY(y, nearest);
            int lineStartY = modulationSourceLeadY(sourceY, targetY);
            int lineEndY = modulationTargetLeadY(sourceY, targetY);
            int numerator = (lineEndY - lineStartY) * (sideX - sourceX);
            return lineStartY + Math.round((float) numerator / (targetX - sourceX));
        }

        private static int nodeCenterX(int x, int width, int nodeWidth,
                                       int[][] layout, int[] node) {
            return x + centeredAlgorithmNodeX(width, nodeWidth, layout, node[1])
                    + nodeWidth / 2;
        }

        private static int nodeTopY(int y, int[] node) {
            return y + node[2] * ALGORITHM_ROW_STEP;
        }

        private static void drawModulationPath(GuiGraphicsExtractor graphics,
                                               int sourceX, int sourceY,
                                               int targetX, int targetY, int color) {
            if (sourceX == targetX) {
                graphics.verticalLine(sourceX, sourceY - 1, targetY + 1, color);
                return;
            }
            int lineStartY = modulationSourceLeadY(sourceY, targetY);
            int lineEndY = modulationTargetLeadY(sourceY, targetY);
            graphics.verticalLine(sourceX, sourceY - 1, lineStartY, color);
            drawLine(graphics, sourceX, lineStartY, targetX, lineEndY, color);
            graphics.verticalLine(targetX, lineEndY, targetY + 1, color);
        }

        private static int modulationSourceLeadY(int sourceY, int targetY) {
            return Math.min(targetY, sourceY + 2);
        }

        private static int modulationTargetLeadY(int sourceY, int targetY) {
            return Math.max(modulationSourceLeadY(sourceY, targetY), targetY - 2);
        }

        private static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0,
                                     int x1, int y1, int color) {
            int dx = Math.abs(x1 - x0);
            int dy = Math.abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1;
            int sy = y0 < y1 ? 1 : -1;
            int error = dx - dy;
            while (true) {
                graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
                if (x0 == x1 && y0 == y1) return;
                int twice = error * 2;
                if (twice > -dy) {
                    error -= dy;
                    x0 += sx;
                }
                if (twice < dx) {
                    error += dx;
                    y0 += sy;
                }
            }
        }

        private int cy(int contentY) {
            return getY() + contentY - scroll;
        }

        private static void card(GuiGraphicsExtractor graphics, int x, int y,
                                 int width, int height, String title) {
            PianoInsetPanel.draw(graphics, x, y, width, height);
            if (!title.isEmpty()) graphics.text(Minecraft.getInstance().font, title,
                    x + CARD_INSET, y + 4, 0xFFFFFFFF);
        }

        private static void miniCard(GuiGraphicsExtractor graphics, int x, int y,
                                     int width, int height, String title) {
            PianoInsetPanel.draw(graphics, x, y, width, height);
            graphics.text(Minecraft.getInstance().font, title, x + 3, y + 3,
                    0xFFFFFFFF);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }





    private static final class ScrollBar extends AbstractWidget {
        private final int viewportHeight;
        private final int contentHeight;
        private final int current;

        private ScrollBar(int x, int y, int width, int height, int viewportHeight,
                          int contentHeight, int current) {
            super(x, y, width, height, Component.literal("Scroll advanced settings"));
            this.viewportHeight = viewportHeight;
            this.contentHeight = contentHeight;
            this.current = current;
            active = false;
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                           int mouseX, int mouseY,
                                                           float partialTick) {
            if (contentHeight <= viewportHeight) return;
            graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF252D34);
            int thumbHeight = ScrollbarGeometry.scrollThumbHeight(
                    getHeight(), viewportHeight, contentHeight);
            int thumbY = ScrollbarGeometry.scrollThumbY(getY(), getHeight(), thumbHeight,
                    viewportHeight, contentHeight, current);
            graphics.fill(getX(), thumbY, getRight(), thumbY + thumbHeight, 0xFF758895);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }
}
