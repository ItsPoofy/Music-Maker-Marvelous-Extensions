package m3e.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xerca.xercamusic.common.item.ItemMusicSheet;
import m3e.songbook.SongbookItems;
import m3e.songbook.SongbookService;

/** Lets a main-hand sheet insert into an off-hand Songbook instead of opening the sheet editor. */
@Mixin(ItemMusicSheet.class)
abstract class ItemMusicSheetSongbookMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void m3e$insertIntoOppositeHandBook(Level level, Player player,
                                                               InteractionHand sheetHand,
                                                               CallbackInfoReturnable<InteractionResult> cir) {
        InteractionHand other = sheetHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!player.getItemInHand(other).is(SongbookItems.SONGBOOK)) return;
        if (!level.isClientSide()) SongbookService.insertHeldSheet(player, other, sheetHand);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
