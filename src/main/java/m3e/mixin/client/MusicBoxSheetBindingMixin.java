package m3e.mixin.client;

import m3e.client.MusicSheetClientCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.common.MusicManager;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;

import java.util.ArrayList;

/** Music boxes copy sheet notes during the UUID callback and start later. */
@Mixin(TileEntityMusicBox.class)
abstract class MusicBoxSheetBindingMixin {
    @Shadow @Final private ArrayList<NoteEvent> notes;

    @Inject(method = "applyMusicData", at = @At("TAIL"))
    private void m3e$bindCopiedSheetList(MusicManager.MusicData data, CallbackInfo ci) {
        MusicSheetClientCache.bindCurrentLookup(notes);
    }
}
