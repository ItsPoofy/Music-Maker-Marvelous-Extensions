package m3e.mixin.client;

import m3e.client.MusicBoxPlaybackControls;
import m3e.client.MusicSheetClientCache;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;

@Mixin(TileEntityMusicBox.class)
abstract class MusicBoxSheetLifecycleMixin {
    @Inject(method = "setSheetStack", at = @At("TAIL"))
    private void m3e$resetTransportForInsertedSheet(ItemStack sheetStack,
                                                     boolean updateClient,
                                                     CallbackInfo ci) {
        MusicSheetClientCache.resolve(sheetStack);
        MusicBoxPlaybackControls.sheetChanged((TileEntityMusicBox) (Object) this);
    }

    @Inject(method = "removeSheetStack", at = @At("TAIL"))
    private void m3e$resetTransportForRemovedSheet(CallbackInfo ci) {
        MusicBoxPlaybackControls.sheetChanged((TileEntityMusicBox) (Object) this);
    }
}
