package m3e.sound;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Per-instrument performance profile. ADSR values are normalized slider
 * positions so 0.5 is an exact, recoverable "use Music Maker default".
 */
public record InstrumentSoundProfile(float volume, float attack, float decay,
                                     float sustain, float release, float tuningHz) {
    public static final Codec<InstrumentSoundProfile> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("volume").forGetter(InstrumentSoundProfile::volume),
                    Codec.FLOAT.fieldOf("attack").forGetter(InstrumentSoundProfile::attack),
                    Codec.FLOAT.fieldOf("decay").forGetter(InstrumentSoundProfile::decay),
                    Codec.FLOAT.fieldOf("sustain").forGetter(InstrumentSoundProfile::sustain),
                    Codec.FLOAT.fieldOf("release").forGetter(InstrumentSoundProfile::release),
                    Codec.FLOAT.fieldOf("tuning_hz").forGetter(InstrumentSoundProfile::tuningHz)
            ).apply(instance, InstrumentSoundProfile::new));
    public static final StreamCodec<FriendlyByteBuf, InstrumentSoundProfile> STREAM_CODEC =
            StreamCodec.ofMember(InstrumentSoundProfile::write, InstrumentSoundProfile::read);
    public static final float DEFAULT_SLIDER = 0.5F;
    public static final int DEFAULT_MIDI_VOLUME = 100;
    public static final int MAX_MIDI_VOLUME = 127;
    public static final float MAX_VOLUME = MAX_MIDI_VOLUME / 100.0F;
    public static final float DEFAULT_TUNING_HZ = 440.0F;
    public static final float MIN_TUNING_HZ = 100.0F;
    public static final float MAX_TUNING_HZ = 1_000.0F;
    public static final int MAX_ATTACK_MILLIS = 750;
    public static final int MAX_DECAY_MILLIS = 1_200;
    public static final int MAX_RELEASE_MILLIS = 2_000;
    public static final InstrumentSoundProfile DEFAULT =
            new InstrumentSoundProfile(1.0F, DEFAULT_SLIDER, DEFAULT_SLIDER,
                    DEFAULT_SLIDER, DEFAULT_SLIDER, DEFAULT_TUNING_HZ);

    public InstrumentSoundProfile(float volume, float attack, float decay,
                                  float sustain, float release) {
        this(volume, attack, decay, sustain, release, DEFAULT_TUNING_HZ);
    }

    public InstrumentSoundProfile {
        volume = Float.isFinite(volume) ? volume : 1.0F;
        attack = Float.isFinite(attack) ? attack : DEFAULT_SLIDER;
        decay = Float.isFinite(decay) ? decay : DEFAULT_SLIDER;
        sustain = Float.isFinite(sustain) ? sustain : DEFAULT_SLIDER;
        release = Float.isFinite(release) ? release : DEFAULT_SLIDER;
        tuningHz = Float.isFinite(tuningHz) ? tuningHz : DEFAULT_TUNING_HZ;
        volume = Math.clamp(volume, 0.0F, MAX_VOLUME);
        attack = Math.clamp(attack, 0.0F, 1.0F);
        decay = Math.clamp(decay, 0.0F, 1.0F);
        sustain = Math.clamp(sustain, 0.0F, 1.0F);
        release = Math.clamp(release, 0.0F, 1.0F);
        tuningHz = Math.clamp(tuningHz, MIN_TUNING_HZ, MAX_TUNING_HZ);
    }

    public boolean defaultAttack() { return centered(attack); }
    public boolean defaultDecay() { return centered(decay); }
    public boolean defaultSustain() { return centered(sustain); }
    public boolean defaultRelease() { return centered(release); }
    public boolean defaultEnvelope() {
        return defaultAttack() && defaultDecay() && defaultSustain() && defaultRelease();
    }

    public int attackMillis() { return envelopeMillis(attack, MAX_ATTACK_MILLIS); }
    public int decayMillis() { return envelopeMillis(decay, MAX_DECAY_MILLIS); }
    public float sustainLevel() { return defaultSustain() ? 1.0F : sustain; }
    public int releaseMillis() { return envelopeMillis(release, MAX_RELEASE_MILLIS); }

    public InstrumentSoundProfile withTuning(float hz) {
        return new InstrumentSoundProfile(volume, attack, decay, sustain, release, hz);
    }

    public int midiVolume() {
        return Math.clamp(Math.round(volume * 100.0F), 0, MAX_MIDI_VOLUME);
    }

    public InstrumentSoundProfile withMidiVolume(int value) {
        return new InstrumentSoundProfile(
                Math.clamp(value, 0, MAX_MIDI_VOLUME) / 100.0F,
                attack, decay, sustain, release, tuningHz);
    }

    public float tuningPitchRatio() {
        return tuningHz / DEFAULT_TUNING_HZ;
    }

    private static InstrumentSoundProfile read(FriendlyByteBuf buffer) {
        return new InstrumentSoundProfile(buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeFloat(volume);
        buffer.writeFloat(attack);
        buffer.writeFloat(decay);
        buffer.writeFloat(sustain);
        buffer.writeFloat(release);
        buffer.writeFloat(tuningHz);
    }

    /**
     * Gain for a held note at a Music Maker sound tick. Attack begins at
     * silence, so a newly-created sound can be shaped before SoundManager sees
     * it instead of leaking one full-volume frame.
     */
    public float envelopeGainAtTick(int ageTicks) {
        int age = Math.max(0, ageTicks);
        int attackTicks = defaultAttack() ? 0 : millisToTicks(attackMillis());
        int decayTicks = defaultDecay() ? 0 : millisToTicks(decayMillis());
        if (attackTicks > 0 && age < attackTicks) {
            return age / (float) attackTicks;
        }
        int decayAge = age - attackTicks;
        if (decayTicks > 0 && decayAge < decayTicks) {
            return 1.0F + (sustainLevel() - 1.0F)
                    * (decayAge / (float) decayTicks);
        }
        return sustainLevel();
    }

    private static int millisToTicks(int millis) {
        return millis <= 0 ? 0 : Math.max(1, Math.round(millis / 50.0F));
    }

    private static int envelopeMillis(float value, int maximum) {
        return centered(value) ? -1 : Math.round(maximum * value);
    }

    private static boolean centered(float value) {
        return Math.abs(value - DEFAULT_SLIDER) < 0.0001F;
    }
}
