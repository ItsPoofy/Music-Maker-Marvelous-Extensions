package m3e.mixin;

import m3e.sheet.LegacySheetDropImports;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xerca.xercamusic.common.CommandImport;
import xerca.xercamusic.common.item.ItemMusicSheet;

/** Keeps a dropped proprietary sheet in the editor's held stack, including in Creative. */
@Mixin(CommandImport.class)
abstract class CommandImportInPlaceSheetMixin {
    /** The editor transition is the confirmation; retain only import failures in chat. */
    @Redirect(method = "doImport", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            ordinal = 1))
    private static void m3e$hideSuccessfulImportMessage(Player player,
                                                                       Component message) {
    }

    @Redirect(method = "doImport", at = @At(value = "INVOKE",
            target = "Lxerca/xercamusic/common/CommandImport;giveImportedSheetToPlayer(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/entity/player/Player;)Z"))
    private static boolean m3e$keepDroppedSheetInHand(CompoundTag tag, Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return m3e$callVanillaGive(tag, player);
        }
        LegacySheetDropImports.PendingImport pending =
                LegacySheetDropImports.consumeIfMatches(serverPlayer, tag);
        if (pending == null) return m3e$callVanillaGive(tag, player);
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ItemMusicSheet)
                || (!player.isCreative() && !ItemMusicSheet.isEmptySheet(held))) {
            return m3e$callVanillaGive(tag, player);
        }
        LegacySheetDropImports.installExact(serverPlayer, tag, pending);
        return true;
    }

    @Invoker("giveImportedSheetToPlayer")
    public static boolean m3e$callVanillaGive(CompoundTag tag, Player player) {
        throw new AssertionError();
    }

}
