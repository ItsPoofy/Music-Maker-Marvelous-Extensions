package m3e.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.ItemInstrument;
import m3e.client.InstrumentRangeOverride;
import m3e.instrument.InstrumentSampleRangeAccess;

/** Re-pitches an endpoint sample when the player enables the optional full keyboard range. */
@Mixin(value = ItemInstrument.class, priority = 1200)
abstract class ItemInstrumentExtendedRangeMixin {
    @Shadow private IItemInstrument.InsSound[] insSounds;

    @Inject(method = "getSound", at = @At("HEAD"), cancellable = true)
    private void m3e$extendFromEndpoint(int note,
            CallbackInfoReturnable<IItemInstrument.InsSound> cir) {
        IItemInstrument instrument = (IItemInstrument) (Object) this;
        if (!InstrumentRangeOverride.enabledFor(instrument)
                || !(instrument instanceof InstrumentSampleRangeAccess range)) return;
        int minimum = range.m3e$getSampleMinNote();
        int maximum = range.m3e$getSampleMaxNote();
        if (note >= minimum && note <= maximum) return;
        int noteId = IItemInstrument.noteToId(note);
        if (noteId < 0 || noteId >= IItemInstrument.TOTAL_NOTES || insSounds == null) return;
        int endpoint = note < minimum ? minimum : maximum;
        int endpointId = IItemInstrument.noteToId(endpoint);
        if (endpointId < 0 || endpointId >= insSounds.length || insSounds[endpointId] == null) return;
        IItemInstrument.InsSound source = insSounds[endpointId];
        float pitch = source.pitch() * (float) Math.pow(2.0D, (note - endpoint) / 12.0D);
        cir.setReturnValue(new IItemInstrument.InsSound(source.sound(), pitch));
    }
}
