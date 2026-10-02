package m3e.mixin;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.common.item.Items;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.network.MusicBoxLabelPacket;

/** Adds the title omitted from Music Maker's compact music-box update packet. */
@Mixin(TileEntityMusicBox.class)
abstract class MusicBoxContentsLabelSyncMixin {
    @Inject(method = "setSheetStack", at = @At("TAIL"))
    private void m3e$syncSheetTitle(ItemStack sheetStack, boolean updateClient,
                                                   CallbackInfo ci) {
        TileEntityMusicBox musicBox = (TileEntityMusicBox) (Object) this;
        if (!updateClient || !(musicBox.getLevel() instanceof ServerLevel)) return;

        String title = sheetStack.getOrDefault(Items.SHEET_TITLE, "");
        MusicBoxLabelPacket packet = new MusicBoxLabelPacket(musicBox.getBlockPos(), title);
        for (var player : PlayerLookup.tracking(musicBox)) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    @Inject(method = "removeSheetStack", at = @At("TAIL"))
    private void m3e$clearSheetTitle(CallbackInfo ci) {
        TileEntityMusicBox musicBox = (TileEntityMusicBox) (Object) this;
        if (!(musicBox.getLevel() instanceof ServerLevel)) return;

        MusicBoxLabelPacket packet = new MusicBoxLabelPacket(musicBox.getBlockPos(), "");
        for (var player : PlayerLookup.tracking(musicBox)) {
            ServerPlayNetworking.send(player, packet);
        }
    }
}
