package m3e.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.sound.MusicBoxPlaybackServer;

/** Evicts transient transport state when a music box unloads or is destroyed. */
@Mixin(BlockEntity.class)
public abstract class MusicBoxRemovalMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void m3e$forgetMusicBoxPlayback(CallbackInfo ci) {
        if ((Object) this instanceof TileEntityMusicBox musicBox
                && musicBox.getLevel() != null && !musicBox.getLevel().isClientSide()) {
            MusicBoxPlaybackServer.forget(musicBox);
        }
    }
}
