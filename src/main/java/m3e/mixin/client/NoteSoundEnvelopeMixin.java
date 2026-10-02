package m3e.mixin.client;

import m3e.client.InstrumentEnvelopeAccess;
import m3e.client.RemoteEnvelopeContext;
import m3e.sound.InstrumentSoundProfile;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.client.NoteSound;

/** Adds a real attack/decay/sustain/release envelope to Music Maker notes. */
@Mixin(NoteSound.class)
abstract class NoteSoundEnvelopeMixin extends AbstractSoundInstance implements InstrumentEnvelopeAccess {
    @Shadow @Final private float originalVolume;
    @Shadow @Final private float originalPitch;
    @Shadow private boolean donePlaying;
    @Shadow private int remainingTicks;

    @Unique private InstrumentSoundProfile m3e$profile = InstrumentSoundProfile.DEFAULT;
    @Unique private int m3e$age;
    @Unique private boolean m3e$manualRelease;
    @Unique private int m3e$releaseTicks;
    @Unique private int m3e$releaseRemaining;
    @Unique private float m3e$releaseStartVolume;
    @Unique private float m3e$bendSemitones;
    @Unique private float m3e$vibratoDepth;
    @Unique private float m3e$arrangerPitchOffset;
    @Unique private float m3e$lastPitchRatio = 1.0F;
    @Unique private float m3e$dynamicGain = 1.0F;

    protected NoteSoundEnvelopeMixin(SoundEvent sound, SoundSource source, RandomSource random) {
        super(sound, source, random);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void m3e$applyProfileAtConstruction(
            SoundEvent sound, SoundSource source, float x, float y, float z,
            float volume, float pitch, int remainingTicks, CallbackInfo ci) {
        RemoteEnvelopeContext.apply((NoteSound) (Object) this);
    }

    @Override
    public void m3e$configureEnvelope(InstrumentSoundProfile profile) {
        m3e$profile = profile == null ? InstrumentSoundProfile.DEFAULT : profile;
        m3e$age = 0;
        m3e$manualRelease = false;
        m3e$restoreBasePitch();
        float initialGain = m3e$profile.envelopeGainAtTick(0);
        // OpenAL may discard a newly-created zero-gain source. A sub-audible
        // floor keeps the voice alive so Attack can actually rise.
        volume = originalVolume * m3e$profile.volume()
                * Math.max(0.001F, initialGain) * m3e$dynamicGain;
        m3e$applyPitchExpression();
    }

    @Override
    public void m3e$setMidiExpression(float bendSemitones, float vibratoDepth) {
        m3e$bendSemitones = Math.clamp(bendSemitones, -48.0F, 48.0F);
        m3e$vibratoDepth = Math.clamp(vibratoDepth, 0.0F, 0.5F);
        m3e$applyPitchExpression();
    }

    @Override
    public void m3e$setArrangerPitchOffset(float semitones) {
        m3e$arrangerPitchOffset = Math.clamp(semitones, -48.0F, 48.0F);
        m3e$applyPitchExpression();
    }

    @Override
    public void m3e$setDynamicGain(float gain) {
        m3e$dynamicGain = Math.clamp(gain, 0.0F, 1.0F);
    }

    @Override
    public void m3e$setProfileVolume(float volume) {
        m3e$profile = new InstrumentSoundProfile(volume,
                m3e$profile.attack(), m3e$profile.decay(),
                m3e$profile.sustain(), m3e$profile.release(),
                m3e$profile.tuningHz());
        if (m3e$profile.volume() == 0.0F) this.volume = 0.0F;
    }

    @Override
    public void m3e$stopImmediately() {
        volume = 0.0F;
        donePlaying = true;
        remainingTicks = -1;
        m3e$manualRelease = false;
    }

    @Inject(method = "stopSound", at = @At("HEAD"), cancellable = true)
    private void m3e$beginRelease(CallbackInfo ci) {
        if (m3e$profile.defaultRelease()) return;
        m3e$startRelease();
        ci.cancel();
    }

    /**
     * A finite Music Maker note normally marks itself done when its counter
     * reaches zero without calling stopSound. Intercept that exact boundary so
     * imported drums and arranger notes receive the same configured release as
     * manually released live notes.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void m3e$beginNaturalRelease(CallbackInfo ci) {
        // Music Maker computes glissando from its unmodified pitch during this
        // tick. Remove our prior multiplier first, then reapply it at TAIL.
        m3e$restoreBasePitch();
        if (remainingTicks == 0 && !m3e$profile.defaultRelease()
                && !m3e$manualRelease) {
            m3e$startRelease();
        }
    }

    @Unique
    private void m3e$startRelease() {
        int millis = m3e$profile.releaseMillis();
        int ticks = millis <= 0 ? 0 : Math.max(1, Math.round(millis / 50.0F));
        if (ticks == 0) {
            donePlaying = true;
        } else if (!m3e$manualRelease) {
            m3e$manualRelease = true;
            m3e$releaseTicks = ticks;
            m3e$releaseRemaining = ticks;
            float liveGain = m3e$profile.volume()
                    * m3e$dynamicGain;
            m3e$releaseStartVolume = liveGain <= 0.000001F
                    ? 0.0F : volume / liveGain;
            remainingTicks = -1;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void m3e$applyEnvelope(CallbackInfo ci) {
        if (donePlaying) return;
        if (m3e$manualRelease) {
            if (m3e$releaseRemaining <= 0) {
                volume = 0.0F;
                donePlaying = true;
                return;
            }
            volume = m3e$releaseStartVolume
                    * (m3e$releaseRemaining / (float) m3e$releaseTicks)
                    * m3e$profile.volume()
                    * m3e$dynamicGain;
            m3e$releaseRemaining--;
        } else if (m3e$profile.defaultEnvelope()) {
            if (remainingTicks >= 0) volume *= m3e$profile.volume();
            else volume = originalVolume * m3e$profile.volume();
        } else {
            float gain = m3e$profile.envelopeGainAtTick(m3e$age);
            // Music Maker reconstructs its three-tick note-off fade from
            // originalVolume every tick. Shape that fresh value exactly once;
            // held notes instead use the stable originalVolume baseline.
            if (remainingTicks >= 0) volume *= gain * m3e$profile.volume();
            else volume = originalVolume * gain * m3e$profile.volume();
        }
        if (!m3e$manualRelease) volume *= m3e$dynamicGain;
        m3e$applyPitchExpression();
        m3e$age++;
    }

    @Unique
    private void m3e$applyPitchExpression() {
        m3e$restoreBasePitch();
        double phase = m3e$age * (Math.PI * 2.0D * 5.5D / 20.0D);
        double vibrato = Math.sin(phase) * m3e$vibratoDepth;
        double semitones = m3e$arrangerPitchOffset
                + m3e$bendSemitones + vibrato;
        float expressionRatio = (float) Math.pow(2.0D, semitones / 12.0D);
        m3e$lastPitchRatio = m3e$profile.tuningPitchRatio()
                * expressionRatio;
        pitch *= m3e$lastPitchRatio;
    }

    @Unique
    private void m3e$restoreBasePitch() {
        if (Math.abs(m3e$lastPitchRatio) > 0.000001F) {
            pitch /= m3e$lastPitchRatio;
        }
        m3e$lastPitchRatio = 1.0F;
    }
}
