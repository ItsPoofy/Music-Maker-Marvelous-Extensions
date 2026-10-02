package m3e.mixin.client;

import net.minecraft.client.Minecraft;
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
import m3e.client.MusicBoxInstrumentOptionsScreen;

/** Opens the local options screen for a loaded music box on shift-right-click. */
@Mixin(targets = "xerca.xercamusic.common.block.BlockMusicBox")
abstract class MusicBoxOptionsScreenMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void m3e$openOptions(ItemStack stack, BlockState state, Level level,
                                                 BlockPos pos, Player player, InteractionHand hand,
                                                 BlockHitResult hitResult,
                                                 CallbackInfoReturnable<InteractionResult> cir) {
        if (!level.isClientSide() || !player.isShiftKeyDown()
                || !(level.getBlockEntity(pos) instanceof TileEntityMusicBox musicBox)
                || musicBox.getInstrument() == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == player) {
            client.gui.setScreen(new MusicBoxInstrumentOptionsScreen(client.gui.screen(), pos));
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
