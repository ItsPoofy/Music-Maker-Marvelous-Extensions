package m3e.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import xerca.xercamusic.common.block.BlockMusicBox;

/** Reserves shift-right-click on loaded music boxes for the options screen server-side. */
@Mixin(BlockMusicBox.class)
abstract class MusicBoxShiftRightClickMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void m3e$reserveOptionsClick(ItemStack stack, BlockState state,
                                                         Level level, BlockPos pos, Player player,
                                                         InteractionHand hand, BlockHitResult hitResult,
                                                         CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide() || !player.isShiftKeyDown()
                || !(level.getBlockEntity(pos) instanceof TileEntityMusicBox musicBox)
                || musicBox.getInstrument() == null) return;
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
