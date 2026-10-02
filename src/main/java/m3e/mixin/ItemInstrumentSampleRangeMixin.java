package m3e.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.ItemInstrument;
import m3e.instrument.InstrumentSampleRangeAccess;
import m3e.instrument.InstrumentRangeState;

/** Lets imported sample banks retain their real endpoint notes instead of artificial octave padding. */
@Mixin(ItemInstrument.class)
public abstract class ItemInstrumentSampleRangeMixin implements InstrumentSampleRangeAccess {
    @Shadow @Final @Mutable
    public int minOctave;

    @Shadow @Final @Mutable
    public int maxOctave;

    @Unique
    private int m3e$sampleMinNote = IItemInstrument.MIN_NOTE;

    @Unique
    private int m3e$sampleMaxNote = IItemInstrument.MIN_NOTE + IItemInstrument.TOTAL_NOTES - 1;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void m3e$initializeSampleRange(
            int instrumentId, int minimumOctave, int maximumOctave,
            Item.Properties properties, CallbackInfo ci) {
        int highestPlayableNote = IItemInstrument.MIN_NOTE + IItemInstrument.TOTAL_NOTES - 1;
        m3e$sampleMinNote = Math.clamp(
                IItemInstrument.MIN_NOTE + minimumOctave * 12,
                IItemInstrument.MIN_NOTE, highestPlayableNote);
        m3e$sampleMaxNote = Math.clamp(
                IItemInstrument.MIN_NOTE + (maximumOctave + 1) * 12 - 1,
                m3e$sampleMinNote, highestPlayableNote);
    }

    @Override
    public void m3e$setSampleRange(int minNote, int maxNote) {
        int highestPlayableNote = IItemInstrument.MIN_NOTE + IItemInstrument.TOTAL_NOTES - 1;
        if (minNote < IItemInstrument.MIN_NOTE || maxNote > highestPlayableNote || minNote > maxNote) {
            throw new IllegalArgumentException("Invalid instrument sample range " + minNote + ".." + maxNote);
        }
        m3e$sampleMinNote = minNote;
        m3e$sampleMaxNote = maxNote;
        minOctave = Math.floorDiv(minNote - IItemInstrument.MIN_NOTE, 12);
        maxOctave = Math.floorDiv(maxNote - IItemInstrument.MIN_NOTE, 12);
    }

    @Override
    public int m3e$getSampleMinNote() {
        return m3e$sampleMinNote;
    }

    @Override
    public int m3e$getSampleMaxNote() {
        return m3e$sampleMaxNote;
    }

    @Inject(method = "getSound", at = @At("HEAD"), cancellable = true)
    private void m3e$enforceExactSampleRange(
            int note, CallbackInfoReturnable<IItemInstrument.InsSound> cir) {
        // On the client, Full Keyboard Range deliberately delegates notes
        // outside the recorded range to the endpoint-sample resolver. Do not
        // cancel that resolver here. Dedicated servers never enable this
        // process-local client preference and retain the exact clamp.
        if (InstrumentRangeState.extendsRange((IItemInstrument) (Object) this)) return;
        if (note < m3e$sampleMinNote || note > m3e$sampleMaxNote) {
            cir.setReturnValue(null);
        }
    }
}
