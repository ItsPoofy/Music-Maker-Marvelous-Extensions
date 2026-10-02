package m3e.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.Consumer;

final class PlaybackTransportPanel {
    private PlaybackTransportPanel() { }

    static TimelineSlider addScrubber(Consumer<AbstractWidget> add,
                                      int x, int y, int width, int height,
                                      Binding binding, boolean seekOnRelease) {
        TimelineSlider scrubber = new TimelineSlider(x, y, width, height,
                binding, seekOnRelease);
        scrubber.active = binding.available();
        add.accept(scrubber);
        return scrubber;
    }

    static int addTempoAndSpeed(Consumer<AbstractWidget> add, Screen screen, Font font,
                                int x, int y, int width, int height, int gap,
                                Binding binding, boolean commitSpeedOnRelease) {
        int labelWidth = InstrumentPanelGeometry.columnWidth(width, gap, 4, 0);
        int fieldX = InstrumentPanelGeometry.columnX(x, width, gap, 4, 1);
        int fieldWidth = InstrumentPanelGeometry.columnWidth(width, gap, 4, 1);
        add.accept(new StringWidget(x, y + 4, labelWidth, 10,
                Component.literal("Tempo"), font));
        TempoEditBox tempo = new TempoEditBox(screen, font, fieldX,
                y, fieldWidth, height, binding);
        tempo.setMaxLength(4);
        tempo.setCentered(true);
        tempo.setValue(Integer.toString(binding.displayTempoBpm()));
        tempo.setEditable(binding.available());
        tempo.active = binding.available();
        add.accept(tempo);

        int speedX = InstrumentPanelGeometry.columnX(x, width, gap, 4, 2);
        PlaybackSpeedSlider speed = new PlaybackSpeedSlider(speedX, y,
                Math.max(1, x + width - speedX), height, binding::speed,
                binding::setSpeed, commitSpeedOnRelease);
        speed.active = binding.available();
        add.accept(speed);
        return y + height + gap;
    }

    static void addTempo(Consumer<AbstractWidget> add, Screen screen, Font font,
                         int x, int y, int width, int height, int gap,
                         Binding binding) {
        int labelWidth = font.width("Tempo") + 4;
        add.accept(new StringWidget(x, y + 4, labelWidth, 10,
                Component.literal("Tempo"), font));
        TempoEditBox tempo = new TempoEditBox(screen, font,
                x + labelWidth + gap, y,
                Math.max(1, width - labelWidth - gap), height, binding);
        tempo.setMaxLength(4);
        tempo.setCentered(true);
        tempo.setValue(Integer.toString(binding.displayTempoBpm()));
        tempo.setEditable(binding.available());
        tempo.active = binding.available();
        add.accept(tempo);
    }

    static Button addTransport(Consumer<AbstractWidget> add,
                               int x, int y, int width, int height, int gap,
                               Binding binding) {
        int backWidth = InstrumentPanelGeometry.columnWidth(width, gap, 4, 0);
        Button back = Button.builder(Component.literal("◀10"), ignored ->
                        binding.seekMicros(binding.playheadMicros() - 10_000_000L))
                .bounds(x, y, backWidth, height).build();
        back.active = binding.available();
        add.accept(back);
        int stopX = InstrumentPanelGeometry.columnX(x, width, gap, 4, 1);
        int stopWidth = InstrumentPanelGeometry.columnWidth(width, gap, 4, 1);
        Button stop = Button.builder(Component.literal("■"), ignored -> binding.stop())
                .bounds(stopX, y, stopWidth, height).build();
        stop.active = binding.available();
        add.accept(stop);
        int playX = InstrumentPanelGeometry.columnX(x, width, gap, 4, 2);
        int playWidth = InstrumentPanelGeometry.columnWidth(width, gap, 4, 2);
        Button playPause = Button.builder(Component.literal(
                        binding.playing() ? "II" : "▶"), ignored -> binding.playPause())
                .bounds(playX, y, playWidth, height).build();
        playPause.active = binding.available();
        add.accept(playPause);
        int forwardX = InstrumentPanelGeometry.columnX(x, width, gap, 4, 3);
        int forwardWidth = InstrumentPanelGeometry.columnWidth(width, gap, 4, 3);
        Button forward = Button.builder(Component.literal("10▶"), ignored ->
                        binding.seekMicros(binding.playheadMicros() + 10_000_000L))
                .bounds(forwardX, y, forwardWidth, height).build();
        forward.active = binding.available();
        add.accept(forward);
        return playPause;
    }

    static Button addPreviewTransport(Consumer<AbstractWidget> add,
                                      int x, int y, int width, int height, int gap,
                                      Binding binding) {
        int third = Math.max(1, (width - gap * 2) / 3);
        Button back = Button.builder(Component.literal("◀10"), ignored ->
                        binding.seekMicros(binding.playheadMicros() - 10_000_000L))
                .bounds(x, y, third, height).build();
        back.active = binding.available();
        add.accept(back);
        Button playPause = Button.builder(Component.literal(
                        binding.playing() ? "II" : "▶"), ignored -> binding.playPause())
                .bounds(x + third + gap, y, third, height).build();
        playPause.active = binding.available();
        add.accept(playPause);
        int forwardX = x + (third + gap) * 2;
        Button forward = Button.builder(Component.literal("10▶"), ignored ->
                        binding.seekMicros(binding.playheadMicros() + 10_000_000L))
                .bounds(forwardX, y, Math.max(1, x + width - forwardX), height).build();
        forward.active = binding.available();
        add.accept(forward);
        return playPause;
    }

    static void addTranspose(Consumer<AbstractWidget> add,
                             int x, int y, int width, int height,
                             Binding binding, Runnable afterChange) {
        int gap = 2;
        int displayWidth = Math.clamp(width / 4, 42, 58);
        int buttonWidth = Math.max(1, (width - displayWidth - gap * 4) / 4);
        addTransposeButton(add, x, y, buttonWidth, height,
                "-Oct", -12, binding, afterChange);
        addTransposeButton(add, x + buttonWidth + gap, y, buttonWidth, height,
                "-1", -1, binding, afterChange);
        int displayX = x + (buttonWidth + gap) * 2;
        Button display = Button.builder(Component.literal(
                        transposeLabel(binding.transpose())), ignored -> { })
                .bounds(displayX, y, displayWidth, height).build();
        display.active = false;
        add.accept(display);
        int right = displayX + displayWidth + gap;
        addTransposeButton(add, right, y, buttonWidth, height,
                "+1", 1, binding, afterChange);
        addTransposeButton(add, right + buttonWidth + gap, y,
                x + width - (right + buttonWidth + gap), height,
                "+Oct", 12, binding, afterChange);
    }

    static String transposeLabel(int semitones) {
        return (semitones >= 0 ? "+" : "") + semitones + " st";
    }

    private static void addTransposeButton(Consumer<AbstractWidget> add,
                                           int x, int y, int width, int height,
                                           String label, int delta, Binding binding,
                                           Runnable afterChange) {
        Button button = Button.builder(Component.literal(label), ignored -> {
                    binding.transpose(delta);
                    afterChange.run();
                }).bounds(x, y, width, height).build();
        button.active = binding.available() && binding.canTranspose(delta);
        add.accept(button);
    }

    interface Binding {
        boolean available();
        long playheadMicros();
        long durationMicros();
        int tempoBpm();
        default int displayTempoBpm() { return tempoBpm(); }
        void setTempoBpm(int bpm);
        double speed();
        void setSpeed(double speed);
        boolean playing();
        void playPause();
        void stop();
        void seekMicros(long micros);
        int transpose();
        boolean canTranspose(int delta);
        void transpose(int delta);
    }

    static final class TimelineSlider extends AbstractSliderButton {
        private final Binding binding;
        private final boolean seekOnRelease;
        private boolean syncing;
        private boolean dragging;

        private TimelineSlider(int x, int y, int width, int height,
                               Binding binding, boolean seekOnRelease) {
            super(x, y, width, height, Component.empty(), 0.0D);
            this.binding = binding;
            this.seekOnRelease = seekOnRelease;
            refresh();
        }

        void refresh() {
            if (dragging) return;
            long duration = binding.durationMicros();
            syncing = true;
            value = duration <= 0L ? 0.0D : Math.clamp(
                    binding.playheadMicros() / (double) duration, 0.0D, 1.0D);
            updateMessage();
            syncing = false;
        }

        @Override protected void updateMessage() {
            long duration = binding.durationMicros();
            long position = Math.round(value * duration);
            setMessage(Component.literal(formatTime(position) + " / " + formatTime(duration)));
        }

        @Override protected void applyValue() {
            if (!syncing && !seekOnRelease) seek();
        }

        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
            dragging = true;
            super.onClick(event, doubleClick);
        }

        @Override public void onRelease(MouseButtonEvent event) {
            if (!syncing && seekOnRelease) seek();
            dragging = false;
        }

        private void seek() {
            binding.seekMicros(Math.round(value * binding.durationMicros()));
            updateMessage();
        }

        private static String formatTime(long micros) {
            long totalSeconds = Math.max(0L, micros / 1_000_000L);
            return String.format(Locale.ROOT, "%d:%02d",
                    totalSeconds / 60, totalSeconds % 60);
        }
    }

    private static final class TempoEditBox extends M3EEditBox {
        private final Binding binding;
        private boolean committing;

        private TempoEditBox(Screen screen, Font font, int x, int y, int width,
                             int height, Binding binding) {
            super(screen, font, x, y, width, height, Component.literal("Playback tempo"));
            this.binding = binding;
            setHint(Component.literal("Tempo (Enter to apply)"));
        }

        @Override public boolean keyPressed(KeyEvent event) {
            if (event.key() == GLFW.GLFW_KEY_ENTER
                    || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                commit();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                setValue(Integer.toString(binding.tempoBpm()));
                setTextColor(0xFFE0E0E0);
                return true;
            }
            return super.keyPressed(event);
        }

        @Override protected void onFocusLost() {
            setValue(Integer.toString(binding.tempoBpm()));
            setTextColor(0xFFE0E0E0);
        }

        @Override public void setFocused(boolean focused) {
            if (focused && !isFocused()) {
                setValue(Integer.toString(binding.tempoBpm()));
                setTextColor(0xFFE0E0E0);
            }
            super.setFocused(focused);
        }

        @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                       int mouseX, int mouseY,
                                                       float partialTick) {
            if (!isFocused()) {
                String displayed = Integer.toString(binding.displayTempoBpm());
                if (!displayed.equals(getValue())) setValue(displayed);
            }
            super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
        }

        private void commit() {
            if (committing) return;
            committing = true;
            try {
                binding.setTempoBpm(Integer.parseInt(getValue().strip()));
                setValue(Integer.toString(binding.tempoBpm()));
                setTextColor(0xFFE0E0E0);
            } catch (NumberFormatException invalid) {
                setTextColor(0xFFFF5555);
            } finally {
                committing = false;
            }
        }
    }
}
