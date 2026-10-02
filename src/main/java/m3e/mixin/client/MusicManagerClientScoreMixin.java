package m3e.mixin.client;

import m3e.client.MusicSheetClientCache;
import m3e.client.SheetEditorImportRefresh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xerca.xercamusic.client.MusicManagerClient;
import xerca.xercamusic.common.MusicManager;
import xerca.xercamusic.common.item.Items;
import m3e.sheet.MusicSheetComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

@Mixin(MusicManagerClient.class)
abstract class MusicManagerClientScoreMixin {
    @ModifyVariable(method = "checkMusicDataAndRun", at = @At("HEAD"), argsOnly = true)
    private static Runnable m3e$carrySheetIdentity(Runnable task, UUID id, int version) {
        return m3e.client.MusicSheetPlaybackContext.wrap(id, version, task);
    }

    @Inject(method = "checkMusicDataAndRun", at = @At("HEAD"), cancellable = true)
    private static void m3e$requestAlongsideMusic(UUID id, int version, Runnable task, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            ItemStack sheet = m3e$heldSheet(minecraft, id, version);
            var score = sheet == null ? null : sheet.get(MusicSheetComponents.SCORE);
            if (score != null) {
                var view = score.editorView();
                MusicManagerClient.setMusicData(id, version, view.notes(), view.volumeMarkers());
                MusicSheetClientCache.receiveLocal(id, version, score);
                task.run();
                ci.cancel();
                return;
            }
        }
        MusicSheetClientCache.request(id, version);
    }

    private static ItemStack m3e$heldSheet(Minecraft minecraft, UUID id, int version) {
        ItemStack mainHand = minecraft.player.getMainHandItem();
        if (m3e$matches(mainHand, id, version)) return mainHand;
        ItemStack offHand = minecraft.player.getOffhandItem();
        return m3e$matches(offHand, id, version) ? offHand : null;
    }

    private static boolean m3e$matches(ItemStack stack, UUID id, int version) {
        return id.equals(stack.get(Items.SHEET_ID))
                && version == stack.getOrDefault(Items.SHEET_VERSION, -1);
    }

    @Inject(method = "getMusicData", at = @At("HEAD"))
    private static void m3e$requestAlongsideDirectGet(UUID id, int version,
                                                                 CallbackInfoReturnable<MusicManager.MusicData> cir) {
        MusicSheetClientCache.request(id, version);
    }

    @Inject(method = "setMusicData", at = @At("TAIL"))
    private static void m3e$refreshDroppedSheetEditor(UUID id, int version,
                                                                 java.util.List<xerca.xercamusic.common.NoteEvent> notes,
                                                                 java.util.List<xerca.xercamusic.common.VolumeMarker> markers,
                                                                 CallbackInfo ci) {
        SheetEditorImportRefresh.acceptNativeData(id, version, notes, markers);
    }
}
