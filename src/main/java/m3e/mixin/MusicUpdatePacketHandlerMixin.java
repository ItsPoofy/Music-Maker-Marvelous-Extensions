package m3e.mixin;

import m3e.sheet.MusicSheetServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.common.packets.serverbound.MusicUpdatePacket;
import xerca.xercamusic.common.packets.serverbound.MusicUpdatePacketHandler;

@Mixin(MusicUpdatePacketHandler.class)
abstract class MusicUpdatePacketHandlerMixin {
    @Inject(method = "processMessage", at = @At("HEAD"), cancellable = true)
    private static void m3e$updateCanonicalScore(MusicUpdatePacket packet,
                                                 ServerPlayer player,
                                                 MinecraftServer server,
                                                 CallbackInfo ci) {
        if (MusicSheetServer.updateFromEditor(player, packet)) ci.cancel();
    }
}
