package m3e.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Single-choice tunebook screen using the same compact button-grid language as part selection. */
final class AbcTuneImportScreen extends Screen {
    private final Path path;
    private final AbcFile.AbcDocument document;
    private final ScoreImportDestination destination;
    private final BooleanSupplier destinationGuard;
    private final long generation;
    private final List<Button> tuneButtons = new ArrayList<>();
    private boolean parsing;
    private int page;
    private int pageCount;
    private Button previousButton;
    private Button nextButton;

    AbcTuneImportScreen(Path path, AbcFile.AbcDocument document,
                        ScoreImportDestination destination,
                        BooleanSupplier destinationGuard, long generation) {
        super(Component.literal("Choose ABC tune"));
        this.path = path;
        this.document = document;
        this.destination = destination;
        this.destinationGuard = destinationGuard;
        this.generation = generation;
    }

    @Override protected void init() {
        tuneButtons.clear();
        int maximumRows = Math.max(1, (height - 126) / 22);
        int perPage = maximumRows * 4;
        pageCount = Math.max(1, (document.tunes().size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pageCount - 1);
        int from = page * perPage;
        int visible = Math.min(perPage, document.tunes().size() - from);
        int columns = Math.clamp((visible + maximumRows - 1) / maximumRows, 1, 4);
        int rows = Math.max(1, (visible + columns - 1) / columns);
        int gap = 8;
        int buttonWidth = Math.max(1, Math.min(columns == 1 ? 300 : 240,
                (width - 16 - (columns - 1) * gap) / columns));
        int totalWidth = columns * buttonWidth + (columns - 1) * gap;
        int left = Math.max(8, (width - totalWidth) / 2);
        int top = Math.max(38, (height - rows * 22 - 34) / 2);
        for (int index = from; index < from + visible; index++) {
            AbcFile.AbcTuneSummary tune = document.tunes().get(index);
            int slot = index - from;
            int column = slot / rows;
            int row = slot % rows;
            Button button = addRenderableWidget(Button.builder(Component.literal(tune.displayName()), ignored -> {
                if (parsing) return;
                parsing = true;
                refreshButtons();
                AbcImportController.parseTune(path, this, document, tune, destination,
                        destinationGuard, generation);
            }).bounds(left + column * (buttonWidth + gap), top + row * 22,
                    buttonWidth, 20).build());
            tuneButtons.add(button);
        }
        int navigationY = top + rows * 22 + 8;
        previousButton = addRenderableWidget(Button.builder(Component.literal("◀"), ignored -> {
            page--;
            refreshPage();
        }).bounds(left, navigationY, 48, 20).build());
        Button pageLabel = addRenderableWidget(Button.builder(
                Component.literal((page + 1) + " / " + pageCount), ignored -> { })
                .bounds(left + 52, navigationY, Math.max(1, totalWidth - 104), 20).build());
        pageLabel.active = false;
        nextButton = addRenderableWidget(Button.builder(Component.literal("▶"), ignored -> {
            page++;
            refreshPage();
        }).bounds(left + totalWidth - 48, navigationY, 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(Math.max(8, (width - 200) / 2), navigationY + 24, 200, 20).build());
        refreshButtons();
    }

    private void refreshPage() {
        clearWidgets();
        init();
    }

    void parseFailed() {
        parsing = false;
        refreshButtons();
    }

    private void refreshButtons() {
        for (Button button : tuneButtons) button.active = !parsing;
        if (previousButton != null) previousButton.active = !parsing && page > 0;
        if (nextButton != null) nextButton.active = !parsing && page + 1 < pageCount;
    }

    @Override public void onClose() {
        AsyncMusicImport.cancel();
        destination.close();
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        graphics.fill(0, 0, width, height, 0xD0101010);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                             float partialTicks) {
        graphics.centeredText(font, Component.literal("Choose one tune from " + document.sourceName()),
                width / 2, 20, 0xFFCFCFCF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
    }
}
