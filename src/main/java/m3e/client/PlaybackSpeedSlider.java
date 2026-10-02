package m3e.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

final class PlaybackSpeedSlider extends AbstractSliderButton {
    static final double MIN = 0.25D;
    static final double MAX = 2.0D;

    private final DoubleSupplier current;
    private final DoubleConsumer changed;
    private final boolean commitOnRelease;

    PlaybackSpeedSlider(int x, int y, int width, int height,
                        DoubleSupplier current, DoubleConsumer changed) {
        this(x, y, width, height, current, changed, false);
    }

    PlaybackSpeedSlider(int x, int y, int width, int height,
                        DoubleSupplier current, DoubleConsumer changed,
                        boolean commitOnRelease) {
        super(x, y, width, height, Component.empty(), normalize(current.getAsDouble()));
        this.current = current;
        this.changed = changed;
        this.commitOnRelease = commitOnRelease;
        updateMessage();
    }

    static double clamp(double speed) {
        return Math.clamp(speed, MIN, MAX);
    }

    private static double normalize(double speed) {
        return (clamp(speed) - MIN) / (MAX - MIN);
    }

    private double speed() {
        return MIN + value * (MAX - MIN);
    }

    @Override protected void updateMessage() {
        setMessage(Component.literal(String.format(Locale.ROOT, "Speed: %.2fx", speed())));
    }

    @Override protected void applyValue() {
        if (commitOnRelease) return;
        publish();
    }

    @Override public void onRelease(MouseButtonEvent event) {
        if (commitOnRelease) publish();
    }

    private void publish() {
        double next = Math.round(speed() * 100.0D) / 100.0D;
        if (Math.abs(next - current.getAsDouble()) >= 0.005D) changed.accept(next);
    }
}
