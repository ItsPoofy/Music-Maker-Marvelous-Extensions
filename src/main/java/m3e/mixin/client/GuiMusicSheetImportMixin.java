package m3e.mixin.client;

import m3e.client.MusicSheetClientCache;
import m3e.client.PianoRollScreen;
import m3e.sheet.MusicSheetComponents;
import m3e.sheet.MusicScore;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.client.GuiMusicSheet;
import xerca.xercamusic.common.item.Items;

import java.util.UUID;

@Mixin(GuiMusicSheet.class)
abstract class GuiMusicSheetImportMixin {
    @Unique private @Nullable ItemStack m3e$openedSheet;
    @Unique private boolean m3e$replacementScheduled;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void m3e$captureScore(Player player, ItemStack sheet, Component title, CallbackInfo ci) {
        m3e$openedSheet = sheet;
        UUID id = sheet.get(Items.SHEET_ID);
        int version = sheet.getOrDefault(Items.SHEET_VERSION, -1);
        MusicScore score = sheet.get(MusicSheetComponents.SCORE);
        if (id != null && version >= 0 && score != null) {
            MusicSheetClientCache.receiveLocal(id, version, score);
        } else if (id != null && version >= 0) {
            MusicSheetClientCache.request(id, version);
        }
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void m3e$openPianoRoll(CallbackInfo ci) {
        ci.cancel();
        if (m3e$replacementScheduled || m3e$openedSheet == null) return;
        m3e$replacementScheduled = true;
        ItemStack sheet = m3e$openedSheet;
        var client = net.minecraft.client.Minecraft.getInstance();
        client.execute(() -> client.gui.setScreen(new PianoRollScreen(sheet)));
    }
}
