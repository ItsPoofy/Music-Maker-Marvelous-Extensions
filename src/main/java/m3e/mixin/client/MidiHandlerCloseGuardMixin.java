package m3e.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import m3e.client.MidiInputRefreshAccess;
import m3e.client.InstrumentExpressionController;
import m3e.debug.MusicDebugTelemetry;
import xerca.xercamusic.client.MidiHandler;
import xerca.xercamusic.common.Mod;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Transmitter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Some Windows MIDI providers report a device as open even though their input
 * thread failed to finish initializing.  Their close implementation then
 * throws from inside the JDK.  Guard only those provider close calls; do not
 * shadow or replace Music Maker's device lists or constructor lifecycle.
 */
@Mixin(MidiHandler.class)
abstract class MidiHandlerCloseGuardMixin implements MidiInputRefreshAccess {
    @Unique private static final long m3e$SCAN_INTERVAL_NANOS = 250_000_000L;
    @Shadow @Final private ArrayList<MidiDevice> devices;
    @Shadow @Final private ArrayList<Transmitter> transmitters;
    @Unique private long m3e$lastMidiScan;

    @Override
    public synchronized void m3e$refreshMidiInputs(boolean force) {
        long now = System.nanoTime();
        if (!force && now - m3e$lastMidiScan
                < m3e$SCAN_INTERVAL_NANOS) return;
        m3e$lastMidiScan = now;

        Map<String, MidiDevice.Info> available = new HashMap<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice candidate = MidiSystem.getMidiDevice(info);
                if (candidate.getMaxTransmitters() != 0) {
                    available.put(m3e$deviceKey(info), info);
                }
            } catch (MidiUnavailableException | RuntimeException exception) {
                Mod.LOGGER.debug("Unable to inspect MIDI input {}: {}", info, exception.toString());
            }
        }

        Set<String> connected = new HashSet<>();
        boolean removedInput = false;
        for (int index = devices.size() - 1; index >= 0; index--) {
            MidiDevice device = devices.get(index);
            String key = m3e$deviceKey(device.getDeviceInfo());
            if (device.isOpen() && available.containsKey(key)) {
                connected.add(key);
                continue;
            }
            if (index < transmitters.size()) {
                m3e$closeTransmitterSafely(transmitters.remove(index));
            }
            devices.remove(index);
            removedInput = true;
            if (device.isOpen()) m3e$closeDeviceSafely(device);
            MusicDebugTelemetry.event("midi_input", "device_removed", "device", key);
        }
        if (removedInput) InstrumentExpressionController.resetControllers();

        MidiHandler owner = (MidiHandler) (Object) this;
        for (Map.Entry<String, MidiDevice.Info> entry : available.entrySet()) {
            if (connected.contains(entry.getKey())) continue;
            MidiDevice device = null;
            try {
                device = MidiSystem.getMidiDevice(entry.getValue());
                device.open();
                Transmitter transmitter = device.getTransmitter();
                transmitter.setReceiver(owner.new MidiInputReceiver(
                        device.getDeviceInfo().toString()));
                transmitters.add(transmitter);
                devices.add(device);
                connected.add(entry.getKey());
                MusicDebugTelemetry.event("midi_input", "device_connected",
                        "device", entry.getKey());
            } catch (MidiUnavailableException | RuntimeException exception) {
                if (device != null && device.isOpen()) m3e$closeDeviceSafely(device);
                Mod.LOGGER.debug("MIDI input {} is not available yet: {}",
                        entry.getValue(), exception.toString());
            }
        }
    }

    @Unique
    private static String m3e$deviceKey(MidiDevice.Info info) {
        return info.getName() + '\u001f' + info.getVendor() + '\u001f'
                + info.getDescription() + '\u001f' + info.getVersion();
    }

    @Inject(method = "closeDevices", at = @At("RETURN"))
    private synchronized void m3e$allowImmediateRescan(CallbackInfo ci) {
        m3e$lastMidiScan = 0L;
    }

    @Redirect(method = "closeDevices",
            at = @At(value = "INVOKE", target = "Ljavax/sound/midi/Transmitter;close()V"))
    private void m3e$closeTransmitterSafely(Transmitter transmitter) {
        try {
            transmitter.close();
        } catch (RuntimeException exception) {
            Mod.LOGGER.warn("Ignored failure while closing a MIDI transmitter: {}", exception.toString());
        }
    }

    @Redirect(method = "closeDevices",
            at = @At(value = "INVOKE", target = "Ljavax/sound/midi/MidiDevice;close()V"))
    private void m3e$closeDeviceSafely(MidiDevice device) {
        try {
            device.close();
        } catch (RuntimeException exception) {
            Mod.LOGGER.warn("Ignored failure while closing MIDI device {}: {}",
                    device.getDeviceInfo(), exception.toString());
        }
    }
}
