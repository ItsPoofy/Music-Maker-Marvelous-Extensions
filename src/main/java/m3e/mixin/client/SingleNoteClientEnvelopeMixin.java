package m3e.mixin.client;

import m3e.client.RemoteEnvelopeContext;
import m3e.instrument.InstrumentRangeState;
import m3e.instrument.InstrumentVariations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.common.packets.clientbound.SingleNoteClientPacket;
import xerca.xercamusic.common.packets.clientbound.SingleNoteClientPacketHandler;
import xerca.xercamusic.common.item.IItemInstrument;

/** Makes the packet profile available to Music Maker's nested client lambda. */
@Mixin(SingleNoteClientPacketHandler.class)
abstract class SingleNoteClientEnvelopeMixin {
    @Inject(method = "processMessage", at = @At("HEAD"))
    private static void m3e$beginRemoteEnvelope(
            SingleNoteClientPacket packet, CallbackInfo ci) {
        RemoteEnvelopeContext.begin(packet);
        InstrumentRangeState.beginRemotePlayback();
    }

    @Inject(method = "processMessage", at = @At("RETURN"))
    private static void m3e$endRemoteEnvelope(
            SingleNoteClientPacket packet, CallbackInfo ci) {
        InstrumentRangeState.endRemotePlayback();
        RemoteEnvelopeContext.end();
    }

    @Redirect(method = "processMessage", at = @At(value = "INVOKE", target =
            "Lxerca/xercamusic/common/item/IItemInstrument;getSound(I)Lxerca/xercamusic/common/item/IItemInstrument$InsSound;"))
    private static IItemInstrument.InsSound m3e$remoteVariation(
            IItemInstrument instrument, int note) {
        return InstrumentVariations.resolve(instrument, note,
                RemoteEnvelopeContext.currentVariation(instrument.getInstrumentId()));
    }
}
