package m3e.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Makes Music Maker's positional instrument protocol registry appendable by addons. */
@Mixin(Items.class)
public abstract class ItemsInstrumentRegistryMixin {
    @Shadow @Final @Mutable
    public static List<IItemInstrument> INSTRUMENTS;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void m3e$makeInstrumentRegistryAppendable(CallbackInfo ci) {
        INSTRUMENTS = new ArrayList<>(INSTRUMENTS);
    }
}
