package m3e.mixin.client;

import m3e.client.InstrumentOptions;
import m3e.client.InstrumentOctaveAccess;
import m3e.client.InstrumentPerformanceControls;
import m3e.client.InstrumentRangeOverride;
import m3e.client.KeyboardKeyMap;
import m3e.client.KeyboardNoteLayout;
import m3e.client.ViewportClippedWidget;
import m3e.client.PianoKeyboardGeometry;
import m3e.client.PianoKeyboardRenderer;
import m3e.client.PianoWaterfallState;
import m3e.api.client.M3EClientIntegration;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xerca.xercamusic.client.GuiInstrument;
import xerca.xercamusic.client.MidiHandler;
import xerca.xercamusic.common.item.IItemInstrument;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adds silent Left Split controls and the optional keyboard layouts to Music Maker's existing GUI. */
@Mixin(GuiInstrument.class)
abstract class GuiInstrumentKeyboardLayoutMixin implements InstrumentOctaveAccess {
    @Unique private static final int m3e$OFFSCREEN = -10_000;
    @Shadow private static int currentKeyboardOctave;
    @Shadow private int guiBaseX;
    @Shadow private int guiBaseY;
    @Shadow @Final private boolean[] buttonPushStates;
    @Shadow @Final private IItemInstrument instrument;
    @Shadow @Final private MidiHandler midiHandler;
    @Unique private final Map<Integer, Integer> m3e$heldControlByKey = new HashMap<>();
    @Unique private final Map<Integer, Integer> m3e$heldLayoutByKey = new HashMap<>();
    @Unique private int m3e$heldMouseNote = -1;
    @Unique private int m3e$layoutWidth = -1;
    @Unique private int m3e$layoutHeight = -1;
    @Unique private PianoKeyboardGeometry.Mode m3e$layoutMode;
    @Unique private PianoKeyboardGeometry.Layout m3e$cachedPianoLayout;

    @Invoker("playSound") protected abstract void m3e$playNote(int noteId);
    @Invoker("stopSound") protected abstract void m3e$stopNote(int noteId);
    @Invoker("increaseOctave") protected abstract void m3e$increaseOctave();
    @Invoker("decreaseOctave") protected abstract void m3e$decreaseOctave();

    @Override
    public int m3e$currentKeyboardOctave() {
        return currentKeyboardOctave;
    }

    @Override
    public String m3e$selectedKeyboardRange() {
        m3e$normalizeSelectedOctave();
        KeyboardNoteLayout.SelectionWindow window = m3e$keyboardSelection();
        if (window.isEmpty()) return "—";
        return KeyboardNoteLayout.noteName(window.fromInclusive(), InstrumentOptions.noteSpelling())
                + "–" + KeyboardNoteLayout.noteName(window.toExclusive() - 1,
                InstrumentOptions.noteSpelling());
    }

    @Override
    public boolean m3e$canDecreaseOctave() {
        m3e$normalizeSelectedOctave();
        return currentKeyboardOctave > KeyboardNoteLayout.midiOctave(
                m3e$minimumSelectedOctave(), m3e$noteFloor());
    }

    @Override
    public boolean m3e$canIncreaseOctave() {
        m3e$normalizeSelectedOctave();
        return currentKeyboardOctave < KeyboardNoteLayout.midiOctave(
                m3e$maximumSelectedOctave(), m3e$noteFloor());
    }

    @Override
    public void m3e$decreaseKeyboardOctave() {
        m3e$decreaseOctave();
    }

    @Override
    public void m3e$increaseKeyboardOctave() {
        m3e$increaseOctave();
    }

    @Override
    public void m3e$pianoLayoutChanged() {
        if (m3e$heldMouseNote >= 0) {
            m3e$stopTrackedNote(m3e$heldMouseNote);
            m3e$heldMouseNote = -1;
        }
        m3e$releaseLayoutMelodyKeys();
        m3e$cachedPianoLayout = null;
        m3e$normalizeSelectedOctave();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void m3e$hideLegacyOctaveButtons(CallbackInfo ci) {
        if (!M3EClientIntegration.instrumentUiEnabled()) return;
        int legacyX = guiBaseX - 10;
        for (var widget : Screens.getWidgets((Screen) (Object) this)) {
            if (widget.getX() == legacyX && widget.getWidth() == 10
                    && widget.getHeight() == 10
                    && (widget.getY() == 30 || widget.getY() == 55)) {
                widget.visible = false;
                widget.active = false;
            }
        }
        m3e$normalizeSelectedOctave();
        guiBaseX = m3e$OFFSCREEN;
        guiBaseY = m3e$OFFSCREEN;
    }

    @Inject(method = "decreaseOctave", at = @At("HEAD"), cancellable = true)
    private void m3e$boundedDecrease(CallbackInfo ci) {
        if (!M3EClientIntegration.instrumentUiEnabled()) return;
        m3e$setSelectedOctave(currentKeyboardOctave - 1);
        ci.cancel();
    }

    @Inject(method = "increaseOctave", at = @At("HEAD"), cancellable = true)
    private void m3e$boundedIncrease(CallbackInfo ci) {
        if (!M3EClientIntegration.instrumentUiEnabled()) return;
        m3e$setSelectedOctave(currentKeyboardOctave + 1);
        ci.cancel();
    }

    @Redirect(method = "extractRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void m3e$hideLegacyOctaveNumber(
            GuiGraphicsExtractor graphics, Font font, String text,
            int x, int y, int color) {
        if (!M3EClientIntegration.instrumentUiEnabled()) {
            graphics.centeredText(font, text, x, y, color);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void m3e$clickPiano(MouseButtonEvent event,
                                               boolean isDoubleClick,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!m3e$usesNewPiano()) return;
        if (m3e$interactiveWidgetAt(event.x(), event.y())) return;
        int noteId = m3e$pianoLayout().hitTest(event.x(), event.y());
        if (noteId < 0) return;
        if (m3e$heldMouseNote >= 0
                && m3e$heldMouseNote != noteId) {
            m3e$stopTrackedNote(m3e$heldMouseNote);
        }
        m3e$heldMouseNote = noteId;
        m3e$playTrackedNote(noteId);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void m3e$releasePiano(MouseButtonEvent event,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!m3e$usesNewPiano()) return;
        if (m3e$heldMouseNote < 0) return;
        m3e$stopTrackedNote(m3e$heldMouseNote);
        m3e$heldMouseNote = -1;
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void m3e$dragPiano(MouseButtonEvent event,
                                              double deltaX, double deltaY,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (!m3e$usesNewPiano()) return;
        int noteId = m3e$pianoLayout().hitTest(event.x(), event.y());
        if (noteId == m3e$heldMouseNote) {
            if (noteId >= 0) cir.setReturnValue(true);
            return;
        }
        if (m3e$heldMouseNote >= 0) {
            m3e$stopTrackedNote(m3e$heldMouseNote);
        }
        m3e$heldMouseNote = noteId;
        if (noteId >= 0) m3e$playTrackedNote(noteId);
        if (noteId >= 0) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void m3e$keyboardLayout(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!M3EClientIntegration.instrumentUiEnabled()) return;
        Screen self = (Screen) (Object) this;
        if (ViewportClippedWidget.unwrap(self.getFocused())
                instanceof EditBox editBox && editBox.isFocused()) {
            boolean handled = editBox.keyPressed(event);
            if (handled || event.key() != GLFW.GLFW_KEY_ESCAPE) cir.setReturnValue(true);
            return;
        }
        int key = event.key();
        InstrumentOptions.KeyboardLayout layout = InstrumentOptions.keyboardLayout();
        int octaveShift = KeyboardKeyMap.octaveShift(layout, key, event.modifiers(),
                m3e$noteFloor(), m3e$noteLimit());
        if (octaveShift != 0) {
            if (octaveShift < 0) m3e$decreaseOctave();
            else m3e$increaseOctave();
            cir.setReturnValue(true);
            return;
        }
        KeyboardKeyMap.ResolvedBinding resolved = KeyboardKeyMap.resolve(layout,
                key, event.modifiers(), m3e$displayOctave(),
                InstrumentOptions.octaveOrigin(), m3e$noteFloor(),
                m3e$noteLimit(), M3EClientIntegration.splitControlEnabled());
        if (resolved != null) {
            if (resolved.control()) {
                m3e$pressControlKey(key, resolved.noteId());
            } else if (m3e$validNoteId(resolved.noteId())
                    && m3e$heldLayoutByKey.putIfAbsent(
                    key, resolved.noteId()) == null) {
                m3e$playTrackedNote(resolved.noteId());
            }
            cir.setReturnValue(true);
            return;
        }
        // Consume keys owned by the selected layout even at an unavailable
        // pitch so Music Maker's legacy handlers cannot leak through.
        if (KeyboardKeyMap.consumesUnresolvedKey(layout, key, event.scancode(),
                m3e$noteFloor(), m3e$noteLimit())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true)
    private void m3e$releaseKeyboardLayout(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!M3EClientIntegration.instrumentUiEnabled()) return;
        int key = event.key();
        Integer control = m3e$heldControlByKey.remove(key);
        if (control != null) {
            M3EClientIntegration.sendControlNote(control, 127, true);
            InstrumentPerformanceControls.noteOff(control);
            cir.setReturnValue(true);
            return;
        }
        Integer noteId = m3e$heldLayoutByKey.remove(key);
        if (noteId != null) {
            m3e$stopTrackedNote(noteId);
            cir.setReturnValue(true);
            return;
        }
        InstrumentOptions.KeyboardLayout layout = InstrumentOptions.keyboardLayout();
        if (KeyboardKeyMap.consumesUnresolvedKey(layout, key, event.scancode(),
                m3e$noteFloor(), m3e$noteLimit())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void m3e$releaseAllLayoutKeys(CallbackInfo ci) {
        if (m3e$heldMouseNote >= 0) {
            m3e$stopTrackedNote(m3e$heldMouseNote);
            m3e$heldMouseNote = -1;
        }
        for (int note : m3e$heldControlByKey.values()) {
            M3EClientIntegration.sendControlNote(note, 127, true);
            InstrumentPerformanceControls.noteOff(note);
        }
        m3e$heldControlByKey.clear();
        for (int noteId : m3e$heldLayoutByKey.values()) {
            m3e$stopTrackedNote(noteId);
        }
        m3e$heldLayoutByKey.clear();
    }

    @Unique
    private void m3e$releaseLayoutMelodyKeys() {
        for (int noteId : m3e$heldLayoutByKey.values()) {
            m3e$stopTrackedNote(noteId);
        }
        m3e$heldLayoutByKey.clear();
    }

    @Unique
    private void m3e$playTrackedNote(int noteId) {
        m3e$playNote(noteId);
    }

    @Unique
    private void m3e$stopTrackedNote(int noteId) {
        m3e$stopNote(noteId);
    }

    @Unique
    private void m3e$pressControlKey(int key, int note) {
        if (m3e$heldControlByKey.putIfAbsent(key, note) != null) return;
        int velocity = m3e$typingVelocity();
        M3EClientIntegration.sendControlNote(note, velocity, false);
        InstrumentPerformanceControls.noteOn(note, velocity);
    }

    @Unique
    private boolean m3e$usesNewPiano() {
        return M3EClientIntegration.instrumentUiEnabled();
    }

    @Unique
    private PianoKeyboardGeometry.Layout m3e$pianoLayout() {
        Screen screen = (Screen) (Object) this;
        PianoKeyboardGeometry.Mode mode = InstrumentOptions.compactInstrumentUi()
                ? PianoKeyboardGeometry.Mode.COMPACT : PianoKeyboardGeometry.Mode.STANDARD;
        if (m3e$cachedPianoLayout == null
                || m3e$layoutWidth != screen.width
                || m3e$layoutHeight != screen.height
                || m3e$layoutMode != mode) {
            m3e$layoutWidth = screen.width;
            m3e$layoutHeight = screen.height;
            m3e$layoutMode = mode;
            m3e$cachedPianoLayout = PianoKeyboardGeometry.layout(
                    screen.width, screen.height, mode);
        }
        return m3e$cachedPianoLayout;
    }

    @Unique
    private boolean m3e$interactiveWidgetAt(double x, double y) {
        for (var widget : Screens.getWidgets((Screen) (Object) this)) {
            if (widget.visible && widget.active && widget.isMouseOver(x, y)) return true;
        }
        return false;
    }

    @Unique
    private static int m3e$originNoteId() {
        // Do not clamp a negative endpoint octave. It intentionally selects
        // only the physical A0-up fragment left after the chosen origin's
        // previous octave begins below the keyboard.
        return 12 * m3e$displayOctave()
                + InstrumentOptions.octaveOriginOffset();
    }

    @Unique
    private static int m3e$selectionOrigin() {
        // Full Keyboard and Visual Piano are fixed C-anchored layouts. The
        // octave-origin preference remains stored for the layouts that expose
        // and use that control.
        InstrumentOptions.KeyboardLayout layout = InstrumentOptions.keyboardLayout();
        return layout == InstrumentOptions.KeyboardLayout.FULL
                || layout == InstrumentOptions.KeyboardLayout.VISUAL_PIANO
                ? 0 : InstrumentOptions.octaveOrigin();
    }

    @Unique
    private KeyboardNoteLayout.SelectionWindow m3e$selectionWindow() {
        return m3e$selectionWindowAt(m3e$displayOctave(),
                m3e$selectionOrigin());
    }

    @Unique
    private KeyboardNoteLayout.SelectionWindow m3e$selectionWindowAt(
            int octave, int origin) {
        boolean extendedRange = InstrumentRangeOverride.enabledFor(instrument);
        KeyboardNoteLayout.SelectionWindow raw = KeyboardNoteLayout.playableSelectionWindow(octave,
                origin, m3e$noteLimit(),
                extendedRange ? 0 : instrument.getMinOctave(),
                extendedRange ? (IItemInstrument.TOTAL_NOTES - 1) / 12 : instrument.getMaxOctave());
        if (raw.isEmpty()) return raw;
        int from = Math.max(raw.fromInclusive(), m3e$noteFloor());
        int to = Math.min(raw.toExclusive(), m3e$noteLimit());
        return to > from ? new KeyboardNoteLayout.SelectionWindow(from, to)
                : new KeyboardNoteLayout.SelectionWindow(-1, -1);
    }

    @Unique
    private boolean m3e$octaveHasPlayableNote(int octave) {
        return !m3e$selectionWindowAt(octave,
                m3e$selectionOrigin()).isEmpty();
    }

    @Unique
    private int m3e$minimumSelectedOctave() {
        for (int octave = -1; octave <= 7; octave++) {
            if (m3e$octaveHasPlayableNote(octave)) return octave;
        }
        return 0;
    }

    @Unique
    private int m3e$maximumSelectedOctave() {
        for (int octave = 7; octave >= -1; octave--) {
            if (m3e$octaveHasPlayableNote(octave)) return octave;
        }
        return 0;
    }

    @Unique
    private void m3e$normalizeSelectedOctave() {
        int normalized = Math.clamp(currentKeyboardOctave,
                KeyboardNoteLayout.midiOctave(m3e$minimumSelectedOctave(),
                        m3e$noteFloor()),
                KeyboardNoteLayout.midiOctave(m3e$maximumSelectedOctave(),
                        m3e$noteFloor()));
        if (normalized == currentKeyboardOctave) return;
        currentKeyboardOctave = normalized;
        midiHandler.setCurrentOctave(normalized);
    }

    @Unique
    private void m3e$setSelectedOctave(int requested) {
        int selected = Math.clamp(requested,
                KeyboardNoteLayout.midiOctave(m3e$minimumSelectedOctave(),
                        m3e$noteFloor()),
                KeyboardNoteLayout.midiOctave(m3e$maximumSelectedOctave(),
                        m3e$noteFloor()));
        if (selected == currentKeyboardOctave) return;
        currentKeyboardOctave = selected;
        midiHandler.setCurrentOctave(selected);
    }

    @Unique
    private boolean m3e$validNoteId(int noteId) {
        // The selected presentation range never manufactures unsupported
        // instrument pitches.
        return noteId >= m3e$noteFloor()
                && noteId < m3e$noteLimit()
                && instrument.getSound(IItemInstrument.idToNote(noteId)) != null;
    }

    @Unique
    private static int m3e$typingVelocity() {
        return InstrumentPerformanceControls.keyboardVelocity();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void m3e$renderPiano(GuiGraphicsExtractor graphics,
                                                int mouseX, int mouseY, float partialTicks,
                                                CallbackInfo ci) {
        if (!m3e$usesNewPiano()) return;
        m3e$normalizeSelectedOctave();
        Map<Integer, m3e.client.KeyboardChord> labels =
                m3e$keyboardLabels();
        KeyboardNoteLayout.SelectionWindow selection =
                m3e$selectionFromBindings(labels);
        if (InstrumentOptions.keyboardLayout()
                != InstrumentOptions.KeyboardLayout.VISUAL_PIANO) {
            labels = labels.entrySet().stream()
                    .filter(entry -> !selection.isEmpty()
                            && entry.getKey() >= selection.fromInclusive()
                            && entry.getKey() < selection.toExclusive()
                            && m3e$validNoteId(entry.getKey()))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                            Map.Entry::getValue, (left, right) -> left,
                            LinkedHashMap::new));
        }
        Screen screen = (Screen) (Object) this;
        PianoKeyboardRenderer.render(graphics, Minecraft.getInstance().font,
                m3e$pianoLayout(), buttonPushStates, instrument,
                InstrumentOptions.keyboardLayout()
                        == InstrumentOptions.KeyboardLayout.VISUAL_PIANO
                        ? null : selection,
                labels, screen.width, screen.height, PianoWaterfallState.frame());
    }

    @Unique
    private Map<Integer, m3e.client.KeyboardChord>
    m3e$keyboardLabels() {
        return KeyboardKeyMap.displayBindings(InstrumentOptions.keyboardLayout(),
                m3e$displayOctave(), InstrumentOptions.octaveOrigin(),
                m3e$noteFloor(), m3e$noteLimit());
    }

    @Unique
    private static int m3e$displayOctave() {
        return KeyboardNoteLayout.displayOctave(currentKeyboardOctave,
                m3e$noteFloor());
    }

    @Unique
    private KeyboardNoteLayout.SelectionWindow m3e$keyboardSelection() {
        return m3e$selectionFromBindings(
                m3e$keyboardLabels());
    }

    @Unique
    private KeyboardNoteLayout.SelectionWindow m3e$selectionFromBindings(
            Map<Integer, m3e.client.KeyboardChord> bindings) {
        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        for (int noteId : bindings.keySet()) {
            if (!m3e$validNoteId(noteId)) continue;
            first = Math.min(first, noteId);
            last = Math.max(last, noteId);
        }
        if (first != Integer.MAX_VALUE) {
            return new KeyboardNoteLayout.SelectionWindow(first, last + 1);
        }
        return m3e$selectionWindow();
    }

    @Unique
    private static int m3e$noteFloor() {
        return InstrumentOptions.compactInstrumentUi()
                ? PianoKeyboardGeometry.COMPACT_FIRST_NOTE_ID
                : PianoKeyboardGeometry.STANDARD_FIRST_NOTE_ID;
    }

    @Unique
    private static int m3e$noteLimit() {
        return InstrumentOptions.compactInstrumentUi()
                ? PianoKeyboardGeometry.COMPACT_LAST_NOTE_ID + 1
                : PianoKeyboardGeometry.STANDARD_LAST_NOTE_ID + 1;
    }
}
