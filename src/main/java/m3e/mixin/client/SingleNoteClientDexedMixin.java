package m3e.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.common.packets.clientbound.SingleNoteClientPacket;
import xerca.xercamusic.common.packets.clientbound.SingleNoteClientPacketHandler;
import m3e.client.DexedAudioManager;
import m3e.instrument.M3EInstruments;

@Mixin(SingleNoteClientPacketHandler.class)
abstract class SingleNoteClientDexedMixin {
    @Inject(method = "processMessage", at = @At("HEAD"), cancellable = true)
    private static void m3e$playDexed(SingleNoteClientPacket packet,
                                                      CallbackInfo ci) {
        if (!M3EInstruments.isDexed(packet.instrumentItem())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getId() != packet.playerId()
                && minecraft.level != null) {
            Entity performer = minecraft.level.getEntity(packet.playerId());
            if (performer != null) {
                DexedAudioManager.remoteNote(packet.playerId(), performer.position(),
                        packet.note(), Math.clamp(Math.round(packet.volume() * 127.0F), 1, 127),
                        packet.isStop());
                if (!packet.isStop()) {
                    minecraft.level.addParticle(ParticleTypes.NOTE,
                            performer.getX(), performer.getY() + 2.2D, performer.getZ(),
                            packet.note() / 24.0D, 0.0D, 0.0D);
                }
            }
        }
        ci.cancel();
    }
}
