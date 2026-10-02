package m3e.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xerca.xercamusic.common.block.BlockMusicBox;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.dexed.DexedComponents;
import m3e.instrument.InstrumentItemComponents;
import m3e.instrument.M3EInstruments;
import m3e.musicbox.MusicBoxDexedPatchAccess;
import m3e.musicbox.MusicBoxInstrumentProfileAccess;
import m3e.musicbox.MusicBoxLibraryAccess;

/** Restores the edited voice to a Redstone Synthesizer ejected from a music box. */
@Mixin(BlockMusicBox.class)
abstract class MusicBoxDexedPatchEjectMixin {
    @Inject(method = "getEjectedStack", at = @At("RETURN"), cancellable = true)
    private void m3e$restoreSynthPatch(TileEntityMusicBox musicBox,
                                                       boolean music,
                                                       CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        MusicBoxLibraryAccess library = MusicBoxLibraryAccess.of(musicBox);
        if (music && !library.m3e$musicBoxSongbook().isEmpty()) {
            cir.setReturnValue(library.m3e$musicBoxSongbook()
                    .copyWithCount(1));
            return;
        }
        if (!music && !library.m3e$musicBoxInstrumentStack().isEmpty()) {
            result = library.m3e$musicBoxInstrumentStack().copyWithCount(1);
            cir.setReturnValue(result);
        }
        if (!music && !result.isEmpty()
                && musicBox instanceof MusicBoxInstrumentProfileAccess profileAccess) {
            result.set(InstrumentItemComponents.SOUND_PROFILE,
                    profileAccess.m3e$musicBoxProfile());
        }
        if (!music && result.getItem() == M3EInstruments.dexed()
                && musicBox instanceof MusicBoxDexedPatchAccess access) {
            result.set(DexedComponents.PATCH, access.m3e$musicBoxDexedPatch());
            result.set(DexedComponents.PERFORMANCE,
                    access.m3e$musicBoxDexedPerformance());
        }
    }
}
