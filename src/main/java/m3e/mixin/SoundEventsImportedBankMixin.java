package m3e.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.common.SoundEvents;
import m3e.instrument.M3EInstruments;

/** Reapplies M3E replacements if Music Maker initializes its original banks afterward. */
@Mixin(SoundEvents.class)
public abstract class SoundEventsImportedBankMixin {
    @Inject(method = "registerSoundEvents", at = @At("TAIL"))
    private static void m3e$applyImportedBanksAfterOriginals(CallbackInfo ci) {
        M3EInstruments.applyImportedReplacements();
    }
}
