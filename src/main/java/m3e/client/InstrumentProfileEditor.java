package m3e.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import m3e.sound.InstrumentSoundProfile;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Reusable tuning, volume, and ADSR controls for any instrument-profile owner. */
public final class InstrumentProfileEditor {
    private InstrumentProfileEditor() { }

    public static Binding binding(Supplier<InstrumentSoundProfile> reader,
                                  Consumer<InstrumentSoundProfile> writer) {
        return new Binding() {
            @Override public InstrumentSoundProfile get() {
                return safe(reader.get());
            }

            @Override public void set(InstrumentSoundProfile profile) {
                writer.accept(safe(profile));
            }
        };
    }

    /** Adds all profile rows and returns the first free y-coordinate below them. */
    public static int add(Screen screen, Font font, int x, int y, int width,
                          int rowHeight, int gap, int tuningLabelWidth, int resetWidth,
                          Consumer<AbstractWidget> addWidget, Binding binding,
                          Runnable rebuild) {
        int fieldWidth = Math.max(24, width - resetWidth - gap);
        y = addTuning(screen, font, x, y, width, rowHeight, gap,
                tuningLabelWidth, resetWidth, addWidget, binding, rebuild);

        for (Field field : Field.values()) {
            addWidget.accept(new ProfileSlider(x, y, fieldWidth, rowHeight, field, binding));
            Field current = field;
            addWidget.accept(resetButton(x + width - resetWidth, y, resetWidth, rowHeight, () -> {
                binding.set(current.reset(binding.get()));
                rebuild.run();
            }));
            y += rowHeight + gap;
        }
        return y;
    }

    /** Adds only controls that the DX7 backend can apply directly. */
    public static int addTuningAndVolume(Screen screen, Font font, int x, int y, int width,
                                         int rowHeight, int gap, int tuningLabelWidth,
                                         int resetWidth, Consumer<AbstractWidget> addWidget,
                                         Binding binding, Runnable rebuild) {
        y = addTuning(screen, font, x, y, width, rowHeight, gap,
                tuningLabelWidth, resetWidth, addWidget, binding, rebuild);
        int fieldWidth = Math.max(24, width - resetWidth - gap);
        addWidget.accept(new ProfileSlider(x, y, fieldWidth, rowHeight, Field.VOLUME, binding));
        addWidget.accept(resetButton(x + width - resetWidth, y, resetWidth, rowHeight, () -> {
            binding.set(Field.VOLUME.reset(binding.get()));
            rebuild.run();
        }));
        return y + rowHeight + gap;
    }

    /** Adds the exact tuning row used by the regular instrument profile menu. */
    public static int addTuning(Screen screen, Font font, int x, int y, int width,
                                int rowHeight, int gap, int tuningLabelWidth,
                                int resetWidth, Consumer<AbstractWidget> addWidget,
                                Binding binding, Runnable rebuild) {
        addWidget.accept(new StringWidget(x, y + Math.max(0, (rowHeight - 10) / 2),
                tuningLabelWidth, 10, Component.literal("Tuning"), font));
        addWidget.accept(new TuningBox(screen, font, x + tuningLabelWidth, y,
                Math.max(24, width - tuningLabelWidth - resetWidth - gap), rowHeight, binding));
        addWidget.accept(resetButton(x + width - resetWidth, y, resetWidth, rowHeight, () -> {
            binding.set(binding.get().withTuning(InstrumentSoundProfile.DEFAULT_TUNING_HZ));
            rebuild.run();
        }));
        return y + rowHeight + gap;
    }

    public static String formatTuning(float hz) {
        return Math.abs(hz - Math.round(hz)) < 0.001F
                ? Integer.toString(Math.round(hz))
                : String.format(Locale.ROOT, "%.2f", hz);
    }

    private static Button resetButton(int x, int y, int width, int height, Runnable reset) {
        return Button.builder(Component.literal("Reset"), ignored -> reset.run())
                .bounds(x, y, width, height).build();
    }

    private static InstrumentSoundProfile safe(InstrumentSoundProfile profile) {
        return profile == null ? InstrumentSoundProfile.DEFAULT : profile;
    }

    public interface Binding {
        InstrumentSoundProfile get();
        void set(InstrumentSoundProfile profile);
    }

    private static final class TuningBox extends M3EEditBox {
        private final Binding binding;
        private boolean committing;

        private TuningBox(Screen screen, Font font, int x, int y, int width, int height,
                          Binding binding) {
            super(screen, font, x, y, width, height, Component.literal("Tuning"));
            this.binding = binding;
            setMaxLength(7);
            setCentered(true);
            setHint(Component.literal("Tuning Hz"));
            setValue(formatTuning(binding.get().tuningHz()));
        }

        @Override protected void onFocusLost() { commit(); }

        private void commit() {
            if (committing) return;
            committing = true;
            try {
                float hz = Float.parseFloat(getValue().strip());
                if (!Float.isFinite(hz) || hz < InstrumentSoundProfile.MIN_TUNING_HZ
                        || hz > InstrumentSoundProfile.MAX_TUNING_HZ) {
                    throw new NumberFormatException("out of range");
                }
                binding.set(binding.get().withTuning(hz));
                setValue(formatTuning(binding.get().tuningHz()));
                setTextColor(0xFFE0E0E0);
            } catch (NumberFormatException invalid) {
                setTextColor(0xFFFF5555);
            } finally {
                committing = false;
            }
        }
    }

    private static final class ProfileSlider extends AbstractSliderButton {
        private final Field field;
        private final Binding binding;

        private ProfileSlider(int x, int y, int width, int height, Field field,
                              Binding binding) {
            super(x, y, width, height, Component.empty(), field.value(binding.get()));
            this.field = field;
            this.binding = binding;
            updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(Component.literal(field.label(field.update(binding.get(), (float) value))));
        }

        @Override protected void applyValue() {
            binding.set(field.update(binding.get(), (float) value));
            updateMessage();
        }
    }

    private enum Field {
        VOLUME {
            @Override double value(InstrumentSoundProfile p) {
                return p.midiVolume() / (double) InstrumentSoundProfile.MAX_MIDI_VOLUME;
            }
            @Override InstrumentSoundProfile update(InstrumentSoundProfile p, float value) {
                return p.withMidiVolume(Math.round(value * InstrumentSoundProfile.MAX_MIDI_VOLUME));
            }
            @Override InstrumentSoundProfile reset(InstrumentSoundProfile p) {
                return p.withMidiVolume(InstrumentSoundProfile.DEFAULT_MIDI_VOLUME);
            }
            @Override String label(InstrumentSoundProfile p) { return "Volume: " + p.midiVolume(); }
        },
        ATTACK {
            @Override double value(InstrumentSoundProfile p) { return p.attack(); }
            @Override InstrumentSoundProfile update(InstrumentSoundProfile p, float value) {
                return new InstrumentSoundProfile(p.volume(), value, p.decay(), p.sustain(),
                        p.release(), p.tuningHz());
            }
            @Override String label(InstrumentSoundProfile p) {
                return p.defaultAttack() ? "Attack: Default" : "Attack: " + p.attackMillis() + " ms";
            }
        },
        DECAY {
            @Override double value(InstrumentSoundProfile p) { return p.decay(); }
            @Override InstrumentSoundProfile update(InstrumentSoundProfile p, float value) {
                return new InstrumentSoundProfile(p.volume(), p.attack(), value, p.sustain(),
                        p.release(), p.tuningHz());
            }
            @Override String label(InstrumentSoundProfile p) {
                return p.defaultDecay() ? "Decay: Default" : "Decay: " + p.decayMillis() + " ms";
            }
        },
        SUSTAIN {
            @Override double value(InstrumentSoundProfile p) { return p.sustain(); }
            @Override InstrumentSoundProfile update(InstrumentSoundProfile p, float value) {
                return new InstrumentSoundProfile(p.volume(), p.attack(), p.decay(), value,
                        p.release(), p.tuningHz());
            }
            @Override String label(InstrumentSoundProfile p) {
                return p.defaultSustain() ? "Sustain: Default"
                        : "Sustain: " + Math.round(p.sustainLevel() * 100.0F) + "%";
            }
        },
        RELEASE {
            @Override double value(InstrumentSoundProfile p) { return p.release(); }
            @Override InstrumentSoundProfile update(InstrumentSoundProfile p, float value) {
                return new InstrumentSoundProfile(p.volume(), p.attack(), p.decay(), p.sustain(),
                        value, p.tuningHz());
            }
            @Override String label(InstrumentSoundProfile p) {
                return p.defaultRelease() ? "Release: Default" : "Release: " + p.releaseMillis() + " ms";
            }
        };

        abstract double value(InstrumentSoundProfile profile);
        abstract InstrumentSoundProfile update(InstrumentSoundProfile profile, float value);
        abstract String label(InstrumentSoundProfile profile);

        InstrumentSoundProfile reset(InstrumentSoundProfile profile) {
            return update(profile, InstrumentSoundProfile.DEFAULT_SLIDER);
        }
    }
}
