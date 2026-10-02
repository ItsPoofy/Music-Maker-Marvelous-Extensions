package m3e.mixin.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xerca.xercamusic.client.NoteSound;

/**
 * Music Maker notes may be several octaves away from their nearest recorded
 * sample. Vanilla's SoundEngine clamps every sound to 0.5x--2x, which would
 * flatten all farther notes into the same endpoint pitch. Keep that safety
 * clamp for Minecraft sounds, but pass the requested positive pitch through
 * for Music Maker note voices.
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineExtendedPitchMixin {
    @Inject(method = "calculatePitch", at = @At("HEAD"), cancellable = true)
    private void m3e$preserveExtendedNotePitch(SoundInstance sound,
                                                               CallbackInfoReturnable<Float> cir) {
        if (!(sound instanceof NoteSound)) return;
        float pitch = sound.getPitch();
        if (Float.isFinite(pitch) && pitch > 0.0F && (pitch < 0.5F || pitch > 2.0F)) {
            cir.setReturnValue(pitch);
        }
    }
}
