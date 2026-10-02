package m3e.mixin.client;

import m3e.client.MusicSheetClientCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.client.SoundController;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;
import xerca.xercamusic.common.item.IItemInstrument;

import java.util.List;

/** Binds entity-spirit controllers created inside Music Manager's UUID callback. */
@Mixin(SoundController.class)
abstract class SoundControllerSheetBindingMixin {
    @Inject(method = "<init>(Ljava/util/List;Ljava/util/List;DDDLxerca/xercamusic/common/item/IItemInstrument;BFI)V",
            at = @At("RETURN"))
    private void m3e$bindSheetList(List<NoteEvent> notes, List<VolumeMarker> markers,
                                              double x, double y, double z, IItemInstrument instrument,
                                              byte bps, float volume, int spiritId, CallbackInfo ci) {
        MusicSheetClientCache.bindCurrentLookup(notes);
    }
}
