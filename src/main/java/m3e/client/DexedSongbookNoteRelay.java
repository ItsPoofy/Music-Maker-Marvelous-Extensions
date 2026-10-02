package m3e.client;

import m3e.instrument.M3EInstruments;
import m3e.network.DexedVoiceAutomationPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.HashMap;
import java.util.Map;

/** Gives every Redstone Synth note a stable identity across the network. */
public final class DexedSongbookNoteRelay {
    private static final Map<Long, Long> LOCAL_EVENTS = new HashMap<>();

    private DexedSongbookNoteRelay() { }

    public static synchronized void noteOn(long localEventId, int note, int velocity) {
        if (localEventId == 0L) return;
        long wireEvent = LiveInstrumentNoteRelay.noteOn(
                M3EInstruments.dexed().getInstrumentId(), note, velocity);
        if (wireEvent <= 0L) return;
        LOCAL_EVENTS.put(localEventId, wireEvent);
    }

    public static synchronized void noteOff(long localEventId, int note) {
        Long wireEvent = LOCAL_EVENTS.remove(localEventId);
        if (wireEvent == null) return;
        LiveInstrumentNoteRelay.noteOff(wireEvent,
                M3EInstruments.dexed().getInstrumentId(), note);
    }

    public static synchronized void expression(long localEventId, float bendSemitones,
                                               int modulation) {
        send(localEventId, DexedVoiceAutomationPacket.EXPRESSION, bendSemitones,
                modulation, 1.0F, null, null, 0L);
    }

    public static synchronized void gain(long localEventId, float gain) {
        send(localEventId, DexedVoiceAutomationPacket.GAIN, 0.0F, 0, gain,
                null, null, 0L);
    }

    public static synchronized void glissando(long localEventId, float[] waypoints,
                                              float[] positions, long durationMicros) {
        send(localEventId, DexedVoiceAutomationPacket.GLISSANDO, 0.0F, 0, 1.0F,
                waypoints, positions, durationMicros);
    }

    private static void send(long localEventId, int kind, float bend, int modulation,
                             float gain, float[] waypoints, float[] positions,
                             long durationMicros) {
        Long wireEvent = LOCAL_EVENTS.get(localEventId);
        if (wireEvent == null || !ClientPlayNetworking.canSend(
                DexedVoiceAutomationPacket.TYPE)) return;
        ClientPlayNetworking.send(new DexedVoiceAutomationPacket(0, wireEvent, kind,
                bend, modulation, gain, waypoints, positions, durationMicros));
    }

    /** Starts an ordinary live keyboard/MIDI note and returns its exact wire identity. */
    public static synchronized long liveNoteOn(int note, int velocity) {
        return LiveInstrumentNoteRelay.noteOn(
                M3EInstruments.dexed().getInstrumentId(), note, velocity);
    }

    /** Releases the live note that owns {@code wireEvent}, never another same-pitch voice. */
    public static synchronized void liveNoteOff(long wireEvent, int note) {
        LiveInstrumentNoteRelay.noteOff(wireEvent,
                M3EInstruments.dexed().getInstrumentId(), note);
    }

    public static synchronized void clear() {
        LOCAL_EVENTS.clear();
    }
}
