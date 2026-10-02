package m3e.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;
import m3e.instrument.M3EInstruments;
import m3e.sound.InstrumentSoundProfile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Compact patch browser, local-bank workspace and everyday synth controls. */
final class DexedSimplePanel {
    private static final int ROW_HEIGHT = InstrumentPanelGeometry.BUTTON_HEIGHT;
    private static final int GAP = 3;
    private static DexedPatch patchClipboard;
    private static final Map<Long, PatchRef> SESSION_PATCHES = new HashMap<>();

    private final Minecraft client;
    private final Screen screen;
    private final Consumer<AbstractWidget> add;
    private final Runnable rerender;
    private final Supplier<DexedEditorTarget> targetSupplier;
    private final DexedAdvancedPanel advancedPanel;
    private List<DexedBankLibrary.Bank> banks = List.of();
    private String selectedBankId;
    private String query = "";
    private String renderedQuery = "";
    private int page;
    private boolean bankMenuOpen;
    private boolean creatingBank;
    private String deletingBankId;
    private String bankNameDraft = "User Bank";
    private boolean refocusBankName;
    private int bankScroll;
    private int bankMaximumScroll;
    private int bankMenuX;
    private int bankMenuY;
    private int bankMenuWidth;
    private int bankMenuHeight;
    private boolean refocusSearch;
    private boolean refocusName;
    private boolean advancedOpen;
    private PatchRef activePatch;
    private Long activeStackIdentity;

    DexedSimplePanel(Minecraft client, Screen screen, Consumer<AbstractWidget> add,
                     Runnable rerender) {
        this(client, screen, add, rerender, DexedPatchClient::editorTarget);
    }

    DexedSimplePanel(Minecraft client, Screen screen, Consumer<AbstractWidget> add,
                     Runnable rerender, Supplier<DexedEditorTarget> targetSupplier) {
        this.client = client;
        this.screen = screen;
        this.add = add;
        this.rerender = rerender;
        this.targetSupplier = targetSupplier;
        this.advancedPanel = new DexedAdvancedPanel(client, screen, add, rerender,
                targetSupplier);
    }

    void render(int x, int y, int width, int bottom) {
        DexedEditorTarget held = targetSupplier.get();
        if (held == null) {
            add.accept(new StringWidget(x, y, width, 10,
                    Component.literal("Redstone Synthesizer is not held"), client.font));
            return;
        }
        banks = DexedBankLibrary.banks();
        if (activeStackIdentity == null
                || activeStackIdentity.longValue() != held.identity()) {
            activeStackIdentity = held.identity();
            activePatch = null;
        }
        resolveActivePatch(held);
        if (advancedOpen) {
            advancedPanel.render(x, y, width, bottom, held, true, () -> {
                DexedEditorTarget current = targetSupplier.get();
                if (current != null) {
                    current.apply(current.patch(), current.performance(), true);
                }
                advancedOpen = false;
                rerender.run();
            });
            return;
        }

        int sectionGap = InstrumentPanelGeometry.SECTION_GAP;
        int controlsWidth = Math.clamp(width / 3, 154, 184);
        int browserWidth = Math.max(220, width - controlsWidth - sectionGap);
        controlsWidth = Math.max(1, width - browserWidth - sectionGap);
        int controlsX = x + browserWidth + sectionGap;

        add.accept(new PianoInsetPanel(x, y, browserWidth, Math.max(1, bottom - y),
                "Patch browser"));
        add.accept(new PianoInsetPanel(controlsX, y, controlsWidth,
                Math.max(1, bottom - y), "Synth controls"));

        int browserInset = 6;
        int controlsInset = 6;
        int innerControlsWidth = Math.max(1, controlsWidth - controlsInset * 2);
        renderPatchName(controlsX + controlsInset, y + controlsInset,
                innerControlsWidth, held);

        int headerY = y + controlsInset + ROW_HEIGHT + GAP;
        int advancedWidth = Math.min(82, Math.max(54, innerControlsWidth * 3 / 5));
        add.accept(new CenteredStringWidget(controlsX + controlsInset, headerY + 4,
                Math.max(1, innerControlsWidth - advancedWidth - GAP), 10,
                client.font, Component.literal("Controls")));
        add.accept(Button.builder(Component.literal("Advanced"), ignored -> {
                    advancedOpen = true;
                    advancedPanel.open();
                    rerender.run();
                }).bounds(controlsX + controlsInset + innerControlsWidth - advancedWidth,
                        headerY, advancedWidth, ROW_HEIGHT).build());

        int browserY = y + browserInset;
        int browserBottom = bottom - browserInset;
        int rowGap = patchRowGap(browserY, browserBottom);
        renderBrowser(x + browserInset, browserY,
                Math.max(1, browserWidth - browserInset * 2), browserBottom, rowGap, held);
        renderControls(controlsX + controlsInset, headerY + ROW_HEIGHT + rowGap,
                innerControlsWidth, rowGap, held);
    }

    private void renderPatchName(int x, int y, int width, DexedEditorTarget held) {
        M3EEditBox name = new M3EEditBox(screen, client.font, x, y,
                width, ROW_HEIGHT,
                Component.literal("Patch name"));
        name.setMaxLength(10);
        name.setValue(currentPatch(held).name());
        name.setEditable(true);
        name.setResponder(value -> {
            DexedEditorTarget current = currentHeld(held);
            DexedPatch renamed = current.patch().withName(value);
            if (renamed.equals(current.patch())) return;
            current.apply(renamed, current.performance(), false);
            refocusName = true;
            rerender.run();
        });
        add.accept(name);
        if (refocusName) {
            screen.setFocused(name);
            name.setCursorPosition(name.getValue().length());
            refocusName = false;
        }
    }

    private void renderBrowser(int x, int y, int width, int bottom, int rowGap,
                               DexedEditorTarget held) {
        int railWidth = Math.clamp(width / 4, 76, 92);
        int contentX = x + railWidth + GAP;
        int contentWidth = Math.max(1, width - railWidth - GAP);
        add.accept(Button.builder(Component.literal(selectedBankName() + " ▾"), ignored -> {
                    bankMenuOpen = !bankMenuOpen;
                    if (!bankMenuOpen) cancelBankAction();
                    bankScroll = 0;
                    rerender.run();
                }).bounds(x, y, railWidth, ROW_HEIGHT).build());

        if (bankMenuOpen) {
            renderBankMenu(x, y + ROW_HEIGHT + GAP, width, bottom);
            return;
        }

        int gridY = y + ROW_HEIGHT + GAP;
        boolean saveable = isPatchSaveable();
        Button save = Button.builder(Component.literal("Save"), ignored -> savePatch(held))
                .bounds(x, gridY, railWidth, ROW_HEIGHT).build();
        save.active = saveable && isDirty(held);
        add.accept(save);
        add.accept(Button.builder(Component.literal("Copy"), ignored -> {
                    patchClipboard = currentPatch(held);
                    rerender.run();
                }).bounds(x, gridY + ROW_HEIGHT + rowGap,
                        railWidth, ROW_HEIGHT).build());
        Button paste = Button.builder(Component.literal("Paste"), ignored -> {
                    if (patchClipboard == null) return;
                    DexedEditorTarget current = currentHeld(held);
                    current.apply(patchClipboard, current.performance(), true);
                    if (activePatch != null) {
                        rememberSession(activePatch, current.identity());
                    }
                    rerender.run();
                }).bounds(x, gridY + (ROW_HEIGHT + rowGap) * 2,
                        railWidth, ROW_HEIGHT).build();
        paste.active = patchClipboard != null;
        add.accept(paste);

        M3EEditBox search = new M3EEditBox(screen, client.font, contentX, y,
                contentWidth, ROW_HEIGHT, Component.literal("Search patches"));
        search.setHint(Component.literal("Search patches..."));
        search.setMaxLength(48);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            page = 0;
            refocusSearch = true;
        });
        renderedQuery = query;
        add.accept(search);
        if (refocusSearch) {
            screen.setFocused(search);
            search.setCursorPosition(search.getValue().length());
            refocusSearch = false;
        }

        List<PatchChoice> choices = filteredPatches();
        int navY = bottom - ROW_HEIGHT;
        int patchRows = Math.clamp(
                (navY - gridY + 2) / (ROW_HEIGHT + 2), 2, 6);
        int patchesPerPage = patchRows * 2;
        int pages = Math.max(1, (choices.size() + patchesPerPage - 1) / patchesPerPage);
        page = Math.clamp(page, 0, pages - 1);
        int cardGap = 3;
        int from = page * patchesPerPage;
        int to = Math.min(choices.size(), from + patchesPerPage);
        for (int index = from; index < to; index++) {
            PatchChoice choice = choices.get(index);
            int slot = index - from;
            int column = slot % 2;
            int cardX = InstrumentPanelGeometry.columnX(
                    contentX, contentWidth, cardGap, 2, column);
            int cardWidth = InstrumentPanelGeometry.columnWidth(
                    contentWidth, cardGap, 2, column);
            int cardY = gridY + (slot / 2) * (ROW_HEIGHT + rowGap);
            boolean active = choice.ref().equals(activePatch);
            add.accept(Button.builder(Component.literal(
                            (active ? "▶ " : "") + choice.patch().name()),
                            ignored -> load(choice, held))
                    .bounds(cardX, cardY, cardWidth, ROW_HEIGHT).build());
        }

        Button initialize = Button.builder(Component.literal("Initialize"), ignored -> {
                    DexedEditorTarget current = currentHeld(held);
                    current.apply(DexedPatch.initVoice(), current.performance(), true);
                    if (activePatch != null) {
                        rememberSession(activePatch, current.identity());
                    }
                    rerender.run();
                }).bounds(x, navY, railWidth, ROW_HEIGHT).build();
        add.accept(initialize);
        Button previous = Button.builder(Component.literal("◀"), ignored -> {
            page--;
            rerender.run();
        }).bounds(contentX, navY, 32, ROW_HEIGHT).build();
        previous.active = page > 0;
        add.accept(previous);
        Button pageLabel = Button.builder(Component.literal((page + 1) + " / " + pages),
                        ignored -> { })
                .bounds(contentX + 36, navY, Math.max(1, contentWidth - 72),
                        ROW_HEIGHT).build();
        pageLabel.active = false;
        add.accept(pageLabel);
        Button next = Button.builder(Component.literal("▶"), ignored -> {
            page++;
            rerender.run();
        }).bounds(contentX + contentWidth - 32, navY, 32, ROW_HEIGHT).build();
        next.active = page + 1 < pages;
        add.accept(next);
    }

    private void renderBankMenu(int x, int y, int width, int bottom) {
        bankMenuX = x;
        bankMenuY = y;
        bankMenuWidth = width;
        bankMenuHeight = Math.max(1, bottom - y);
        int panelInset = 6;
        int actionPanelHeight = ROW_HEIGHT + panelInset * 2;
        int actionPanelY = bottom - actionPanelHeight;
        int actionsY = actionPanelY + panelInset;
        boolean confirmingDeletion = deletingBankId != null;
        int namePanelY = creatingBank || confirmingDeletion
                ? actionPanelY - actionPanelHeight - GAP : actionPanelY;
        int gridBottom = namePanelY - GAP;
        int columns = 2;
        int cellGap = 3;
        int cellWidth = Math.max(1, (width - cellGap) / columns);
        int visibleRows = Math.max(1, (gridBottom - y) / (ROW_HEIGHT + 2));
        ArrayList<BankChoice> choices = new ArrayList<>();
        choices.add(new BankChoice(null, "All"));
        for (DexedBankLibrary.Bank bank : banks) {
            choices.add(new BankChoice(bank.id(), bank.name()));
        }
        int totalRows = (choices.size() + columns - 1) / columns;
        bankMaximumScroll = Math.max(0, totalRows - visibleRows);
        bankScroll = Math.clamp(bankScroll, 0, bankMaximumScroll);
        int first = bankScroll * columns;
        int visible = Math.min(choices.size(), first + visibleRows * columns);
        for (int index = first; index < visible; index++) {
            BankChoice choice = choices.get(index);
            int local = index - first;
            int cellX = x + (local % columns) * (cellWidth + cellGap);
            int cellY = y + (local / columns) * (ROW_HEIGHT + 2);
            add.accept(Button.builder(Component.literal(choice.name()), ignored -> {
                        selectedBankId = choice.id();
                        page = 0;
                        bankMenuOpen = false;
                        cancelBankAction();
                        rerender.run();
                    }).bounds(cellX, cellY, cellWidth, ROW_HEIGHT).build());
        }

        if (creatingBank) {
            renderBankNameField(x, namePanelY, width);
        } else if (confirmingDeletion) {
            renderBankDeleteConfirmation(x, namePanelY, width);
        }

        add.accept(new PianoInsetPanel(x, actionPanelY, width, actionPanelHeight,
                "Bank actions"));
        int actionGap = 3;
        int actionX = x + panelInset;
        int actionInnerWidth = Math.max(1, width - panelInset * 2);
        int actionWidth = Math.max(1, (actionInnerWidth - actionGap * 3) / 4);
        Button create = Button.builder(Component.literal("Create"), ignored -> beginBankCreation())
                .bounds(actionX, actionsY, actionWidth, ROW_HEIGHT).build();
        create.active = !creatingBank && !confirmingDeletion;
        add.accept(create);
        add.accept(Button.builder(Component.literal("Import"), ignored -> importBank())
                .bounds(actionX + actionWidth + actionGap, actionsY,
                        actionWidth, ROW_HEIGHT).build());
        DexedBankLibrary.Bank selected = selectedBank();
        Button export = Button.builder(Component.literal("Export"), ignored -> exportBank())
                .bounds(actionX + (actionWidth + actionGap) * 2, actionsY,
                        actionWidth, ROW_HEIGHT).build();
        export.active = selected != null;
        add.accept(export);
        Button delete = Button.builder(Component.literal("Delete"), ignored -> beginBankDeletion())
                .bounds(actionX + (actionWidth + actionGap) * 3, actionsY,
                        Math.max(1, actionInnerWidth - (actionWidth + actionGap) * 3),
                        ROW_HEIGHT).build();
        delete.active = !creatingBank && !confirmingDeletion
                && selected != null && !selected.factory();
        add.accept(delete);
    }

    private void renderBankNameField(int x, int panelY, int width) {
        int inset = 6;
        int panelHeight = ROW_HEIGHT + inset * 2;
        add.accept(new PianoInsetPanel(x, panelY, width, panelHeight, "New bank name"));
        int innerX = x + inset;
        int innerY = panelY + inset;
        int innerWidth = Math.max(1, width - inset * 2);
        int buttonGap = 3;
        int confirmWidth = 48;
        int cancelWidth = 48;
        int fieldWidth = Math.max(40,
                innerWidth - confirmWidth - cancelWidth - buttonGap * 2);
        M3EEditBox name = new M3EEditBox(screen, client.font, innerX, innerY,
                fieldWidth,
                ROW_HEIGHT, Component.literal("Bank name"));
        name.setMaxLength(32);
        name.setValue(bankNameDraft);
        name.setHint(Component.literal("Bank name"));
        name.setResponder(value -> bankNameDraft = value);
        add.accept(name);
        add.accept(Button.builder(Component.literal("Create"), ignored -> createBank(bankNameDraft))
                .bounds(innerX + fieldWidth + buttonGap, innerY,
                        confirmWidth, ROW_HEIGHT).build());
        add.accept(Button.builder(Component.literal("Cancel"), ignored -> {
                    cancelBankCreation();
                    rerender.run();
                }).bounds(innerX + fieldWidth + buttonGap + confirmWidth + buttonGap,
                        innerY, cancelWidth, ROW_HEIGHT).build());
        if (refocusBankName) {
            screen.setFocused(name);
            name.setCursorPosition(name.getValue().length());
            refocusBankName = false;
        }
    }

    private void renderBankDeleteConfirmation(int x, int panelY, int width) {
        DexedBankLibrary.Bank selected = banks.stream()
                .filter(bank -> bank.id().equals(deletingBankId))
                .findFirst().orElse(null);
        if (selected == null || selected.factory()) {
            deletingBankId = null;
            return;
        }
        int inset = 6;
        int panelHeight = ROW_HEIGHT + inset * 2;
        add.accept(new PianoInsetPanel(x, panelY, width, panelHeight, "Delete bank"));
        int innerX = x + inset;
        int innerY = panelY + inset;
        int innerWidth = Math.max(1, width - inset * 2);
        int buttonGap = 3;
        int confirmWidth = 48;
        int cancelWidth = 48;
        int fieldWidth = Math.max(40,
                innerWidth - confirmWidth - cancelWidth - buttonGap * 2);
        M3EEditBox name = new M3EEditBox(screen, client.font, innerX, innerY,
                fieldWidth, ROW_HEIGHT, Component.literal("Bank name"));
        name.setValue(selected.name());
        name.setEditable(false);
        add.accept(name);
        add.accept(Button.builder(Component.literal("Delete"), ignored -> deleteBank(selected))
                .bounds(innerX + fieldWidth + buttonGap, innerY,
                        confirmWidth, ROW_HEIGHT).build());
        add.accept(Button.builder(Component.literal("Cancel"), ignored -> {
                    deletingBankId = null;
                    rerender.run();
                }).bounds(innerX + fieldWidth + buttonGap + confirmWidth + buttonGap,
                        innerY, cancelWidth, ROW_HEIGHT).build());
    }

    private void renderControls(int x, int y, int width, int rowGap,
                                DexedEditorTarget held) {
        add.accept(new VolumeSlider(x, y, width, ROW_HEIGHT,
                () -> currentHeld(held).profile(), this::setProfile));
        y += ROW_HEIGHT + rowGap;

        DexedPerformance performance = heldPerformance(held);
        add.accept(Button.builder(Component.literal("Mode: "
                        + (performance.mono() ? "Mono" : "Poly")), ignored -> {
                    setPerformance(held, heldPerformance(held)
                            .withMono(!heldPerformance(held).mono()));
                    rerender.run();
                }).bounds(x, y, width, ROW_HEIGHT).build());
        y += ROW_HEIGHT + rowGap;

        add.accept(Button.builder(Component.literal(glideLabel(performance)), ignored -> {
                    DexedPerformance current = heldPerformance(held);
                    DexedPerformance next;
                    if (!current.portamentoEnabled()) {
                        next = current.withPortamentoEnabled(true);
                    } else if (current.mono()
                            && current.monoPortamento()
                            == DexedPerformance.MonoPortamento.LEGATO) {
                        next = current.withMonoPortamento(
                                DexedPerformance.MonoPortamento.ALWAYS);
                    } else {
                        next = current.withPortamentoEnabled(false);
                    }
                    setPerformance(held, next);
                    rerender.run();
                }).bounds(x, y, width, ROW_HEIGHT).build());
        y += ROW_HEIGHT + rowGap;

        PortamentoTimeSlider time = new PortamentoTimeSlider(x, y, width, ROW_HEIGHT,
                () -> heldPerformance(held), next -> setPerformance(held, next));
        time.active = performance.portamentoEnabled();
        add.accept(time);
    }

    private static int patchRowGap(int browserY, int browserBottom) {
        int gridY = browserY + ROW_HEIGHT + GAP;
        int navY = browserBottom - ROW_HEIGHT;
        int patchRows = Math.clamp(
                (navY - gridY + 2) / (ROW_HEIGHT + 2), 2, 6);
        return InstrumentPanelGeometry.distributedGap(
                gridY, navY, ROW_HEIGHT, patchRows, 2);
    }

    private static String glideLabel(DexedPerformance performance) {
        if (!performance.portamentoEnabled()) return "Glide: Off";
        if (!performance.mono()) return "Glide: On";
        return "Glide: " + (performance.monoPortamento()
                == DexedPerformance.MonoPortamento.LEGATO ? "Legato" : "Always");
    }

    private DexedPerformance heldPerformance(DexedEditorTarget fallback) {
        return currentHeld(fallback).performance();
    }

    private void setProfile(InstrumentSoundProfile profile) {
        DexedEditorTarget target = targetSupplier.get();
        if (target != null) target.setProfile(profile);
    }

    private void setPerformance(DexedEditorTarget fallback,
                                DexedPerformance performance) {
        DexedEditorTarget held = currentHeld(fallback);
        held.apply(held.patch(), performance, false);
    }

    private void load(PatchChoice choice, DexedEditorTarget fallback) {
        DexedEditorTarget held = currentHeld(fallback);
        activePatch = choice.ref();
        rememberSession(activePatch, held.identity());
        // Navigation always reloads the saved bank slot. The item stack is the
        // temporary working copy, so leaving a patch deliberately abandons it.
        DexedPatch saved = patchAt(activePatch);
        if (saved == null) return;
        held.apply(saved, held.performance(), true);
        rerender.run();
    }

    private void savePatch(DexedEditorTarget fallback) {
        DexedBankLibrary.Bank bank = activeBank();
        if (bank == null || bank.factory() || activePatch == null) return;
        try {
            DexedEditorTarget held = currentHeld(fallback);
            DexedBankLibrary.saveVoice(bank, activePatch.slot(), held.patch());
            banks = DexedBankLibrary.banks();
            toast("Patch saved", held.patch().name());
        } catch (IOException | IllegalArgumentException exception) {
            toast("Patch save failed", message(exception, "Could not save that patch"));
        }
        rerender.run();
    }

    private void beginBankCreation() {
        deletingBankId = null;
        creatingBank = true;
        bankNameDraft = "User Bank";
        refocusBankName = true;
        bankScroll = 0;
        rerender.run();
    }

    private void cancelBankCreation() {
        creatingBank = false;
        refocusBankName = false;
        bankNameDraft = "User Bank";
    }

    private void cancelBankAction() {
        cancelBankCreation();
        deletingBankId = null;
    }

    private void beginBankDeletion() {
        DexedBankLibrary.Bank selected = selectedBank();
        if (selected == null || selected.factory()) return;
        cancelBankCreation();
        deletingBankId = selected.id();
        rerender.run();
    }

    private void createBank(String name) {
        if (name == null || name.isBlank()) {
            toast("Bank creation failed", "Enter a bank name");
            refocusBankName = true;
            rerender.run();
            return;
        }
        try {
            DexedBankLibrary.Bank created = DexedBankLibrary.createBank(name);
            banks = DexedBankLibrary.banks();
            selectedBankId = created.id();
            bankMenuOpen = false;
            cancelBankCreation();
            page = 0;
        } catch (IOException exception) {
            toast("Bank creation failed", message(exception, "Could not create that bank"));
        }
        rerender.run();
    }

    private void importBank() {
        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.syx")).flip();
            selected = TinyFileDialogs.tinyfd_openFileDialog("Import DX7 Bank", null,
                    filters, "DX7 Bank", false);
        }
        if (selected == null) return;
        try {
            DexedBankLibrary.Bank imported = DexedBankLibrary.importBank(Path.of(selected));
            banks = DexedBankLibrary.banks();
            selectedBankId = imported.id();
            query = "";
            page = 0;
            bankMenuOpen = false;
        } catch (IOException | IllegalArgumentException exception) {
            toast("Bank import failed", message(exception, "Could not import that bank"));
        }
        rerender.run();
    }

    private void exportBank() {
        DexedBankLibrary.Bank selected = selectedBank();
        if (selected == null) return;
        String path;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.syx")).flip();
            path = TinyFileDialogs.tinyfd_saveFileDialog("Export DX7 Bank",
                    selected.name().replace(' ', '_') + ".syx", filters, "DX7 Bank");
        }
        if (path == null) return;
        try {
            DexedBankLibrary.exportBank(selected, Path.of(path));
            toast("Bank exported", selected.name());
        } catch (IOException | IllegalArgumentException exception) {
            toast("Bank export failed", message(exception, "Could not export that bank"));
        }
        rerender.run();
    }

    private void deleteBank(DexedBankLibrary.Bank selected) {
        if (selected == null || selected.factory()) return;
        try {
            DexedBankLibrary.delete(selected);
            if (activePatch != null && activePatch.bankId().equals(selected.id())) {
                activePatch = null;
            }
            forgetBank(selected.id());
            banks = DexedBankLibrary.banks();
            selectedBankId = null;
            page = 0;
            deletingBankId = null;
        } catch (IOException exception) {
            toast("Bank deletion failed", message(exception, "Could not delete that bank"));
        }
        rerender.run();
    }

    private void resolveActivePatch(DexedEditorTarget held) {
        DexedPatch current = held.patch();
        if (activePatch == null) activePatch = SESSION_PATCHES.get(held.identity());
        if (activePatch != null && patchAt(activePatch) != null) return;
        SESSION_PATCHES.remove(held.identity());
        activePatch = null;
        for (DexedBankLibrary.Bank bank : banks) {
            for (int slot = 0; slot < bank.voices().size(); slot++) {
                if (bank.voices().get(slot).equals(current)) {
                    activePatch = new PatchRef(bank.id(), slot);
                    rememberSession(activePatch, held.identity());
                    return;
                }
            }
        }
    }

    private DexedPatch patchAt(PatchRef ref) {
        if (ref == null) return null;
        return savedPatch(banks, ref.bankId(), ref.slot());
    }

    private static void rememberSession(PatchRef reference, long stackIdentity) {
        SESSION_PATCHES.put(stackIdentity, reference);
    }

    private static void forgetBank(String bankId) {
        SESSION_PATCHES.values().removeIf(reference -> reference.bankId().equals(bankId));
    }

    static DexedPatch savedPatch(List<DexedBankLibrary.Bank> banks,
                                 String bankId, int slot) {
        DexedBankLibrary.Bank bank = banks.stream()
                .filter(candidate -> candidate.id().equals(bankId))
                .findFirst().orElse(null);
        return bank == null || slot < 0 || slot >= bank.voices().size()
                ? null : bank.voices().get(slot);
    }

    private DexedPatch currentPatch(DexedEditorTarget fallback) {
        return currentHeld(fallback).patch();
    }

    private DexedEditorTarget currentHeld(DexedEditorTarget fallback) {
        DexedEditorTarget current = targetSupplier.get();
        return current == null ? fallback : current;
    }

    private boolean isDirty(DexedEditorTarget held) {
        DexedPatch saved = patchAt(activePatch);
        return saved != null && !saved.equals(currentPatch(held));
    }

    private boolean isPatchSaveable() {
        DexedBankLibrary.Bank bank = activeBank();
        return bank != null && !bank.factory();
    }

    private DexedBankLibrary.Bank activeBank() {
        return activePatch == null ? null : bank(activePatch.bankId());
    }

    private DexedBankLibrary.Bank bank(String id) {
        if (id == null) return null;
        return banks.stream().filter(bank -> bank.id().equals(id)).findFirst().orElse(null);
    }

    private DexedBankLibrary.Bank selectedBank() {
        return bank(selectedBankId);
    }

    private String selectedBankName() {
        DexedBankLibrary.Bank selected = selectedBank();
        return selected == null ? "All" : selected.name();
    }

    private List<PatchChoice> filteredPatches() {
        String filter = query.strip().toLowerCase(Locale.ROOT);
        DexedBankLibrary.Bank selected = selectedBank();
        ArrayList<PatchChoice> result = new ArrayList<>();
        for (DexedBankLibrary.Bank bank : banks) {
            if (selected != null && !selected.id().equals(bank.id())) continue;
            for (int slot = 0; slot < bank.voices().size(); slot++) {
                DexedPatch patch = bank.voices().get(slot);
                if (filter.isEmpty()
                        || patch.name().toLowerCase(Locale.ROOT).contains(filter)) {
                    result.add(new PatchChoice(new PatchRef(bank.id(), slot), patch));
                }
            }
        }
        return result;
    }

    boolean needsRerender() {
        return !query.equals(renderedQuery);
    }

    void mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (advancedOpen) {
            advancedPanel.mouseScrolled(mouseX, mouseY, vertical);
            return;
        }
        if (!bankMenuOpen || mouseX < bankMenuX || mouseX >= bankMenuX + bankMenuWidth
                || mouseY < bankMenuY || mouseY >= bankMenuY + bankMenuHeight) return;
        int direction = vertical < 0D ? 1 : vertical > 0D ? -1 : 0;
        int next = Math.clamp(bankScroll + direction, 0, bankMaximumScroll);
        if (next != bankScroll) {
            bankScroll = next;
            rerender.run();
        }
    }

    void mouseClicked(MouseButtonEvent event) {
        if (advancedOpen) advancedPanel.mouseClicked(event);
    }

    void mouseDragged(MouseButtonEvent event) {
        if (advancedOpen) advancedPanel.mouseDragged(event);
    }

    void mouseReleased(MouseButtonEvent event) {
        if (advancedOpen) advancedPanel.mouseReleased(event);
    }

    private void toast(String title, String body) {
        client.gui.toastManager().addToast(new SystemToast(
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal(title), Component.literal(body)));
    }

    private static String message(Exception exception, String fallback) {
        return exception.getMessage() == null ? fallback : exception.getMessage();
    }

    private record PatchRef(String bankId, int slot) { }
    private record PatchChoice(PatchRef ref, DexedPatch patch) { }
    private record BankChoice(String id, String name) { }
    private static final class VolumeSlider extends AbstractSliderButton {
        private final java.util.function.Supplier<InstrumentSoundProfile> profile;
        private final java.util.function.Consumer<InstrumentSoundProfile> profileWriter;

        private VolumeSlider(int x, int y, int width, int height,
                             java.util.function.Supplier<InstrumentSoundProfile> profile,
                             java.util.function.Consumer<InstrumentSoundProfile> profileWriter) {
            super(x, y, width, height, Component.empty(), profile.get().midiVolume()
                    / (double) InstrumentSoundProfile.MAX_MIDI_VOLUME);
            this.profile = profile;
            this.profileWriter = profileWriter;
            updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(Component.literal("Volume: " + profile.get().midiVolume()));
        }

        @Override protected void applyValue() {
            profileWriter.accept(profile.get().withMidiVolume(
                    Math.round((float) value * InstrumentSoundProfile.MAX_MIDI_VOLUME)));
            updateMessage();
        }
    }

    private static final class PortamentoTimeSlider extends AbstractSliderButton {
        private final java.util.function.Supplier<DexedPerformance> performance;
        private final java.util.function.Consumer<DexedPerformance> writer;

        private PortamentoTimeSlider(int x, int y, int width, int height,
                                     java.util.function.Supplier<DexedPerformance> performance,
                                     java.util.function.Consumer<DexedPerformance> writer) {
            super(x, y, width, height, Component.empty(),
                    performance.get().portamentoTime()
                            / (double) DexedPerformance.MAX_PORTAMENTO_TIME);
            this.performance = performance;
            this.writer = writer;
            updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(Component.literal("Glide time: "
                    + performance.get().portamentoTime()));
        }

        @Override protected void applyValue() {
            writer.accept(performance.get().withPortamentoTime(
                    Math.round((float) value * DexedPerformance.MAX_PORTAMENTO_TIME)));
            updateMessage();
        }
    }
}
