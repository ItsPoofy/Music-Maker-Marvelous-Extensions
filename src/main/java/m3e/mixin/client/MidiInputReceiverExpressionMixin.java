package m3e.mixin.client;

import m3e.client.InstrumentExpressionController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;

/** Reads standard pitch-wheel and CC1 messages before Music Maker ignores them. */
@Mixin(targets = "xerca.xercamusic.client.MidiHandler$MidiInputReceiver")
abstract class MidiInputReceiverExpressionMixin {
    @Inject(method = "send", at = @At("HEAD"))
    private void m3e$expression(MidiMessage message, long timestamp,
                                               CallbackInfo ci) {
        if (!(message instanceof ShortMessage shortMessage)) return;
        if (shortMessage.getCommand() == ShortMessage.PITCH_BEND) {
            InstrumentExpressionController.acceptPitchBend(
                    (shortMessage.getData2() << 7) | shortMessage.getData1());
        } else if (shortMessage.getCommand() == ShortMessage.CONTROL_CHANGE) {
            if (shortMessage.getData1() == 1) {
                InstrumentExpressionController.acceptModulation(shortMessage.getData2());
            } else if (shortMessage.getData1() == 121) {
                InstrumentExpressionController.resetControllers();
            }
        }
    }
}
