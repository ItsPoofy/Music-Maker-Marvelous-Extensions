package m3e.mixin.client;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import m3e.instrument.M3EInstruments;

/** Parameter components alter sound state, not the Synth's visible held-item model. */
@Mixin(ItemInHandRenderer.class)
abstract class DexedItemSwapAnimationMixin {
    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void m3e$keepSynthVisuallyEquipped(ItemStack visible,
                                                               ItemStack current,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (visible.getItem() == M3EInstruments.dexed()
                && current.getItem() == M3EInstruments.dexed()) {
            cir.setReturnValue(true);
        }
    }
}
