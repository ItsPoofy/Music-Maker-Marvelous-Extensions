package m3e.client;

import net.minecraft.client.sounds.AudioStream;
import m3e.dexed.DexedEngine;
import m3e.dexed.DexedJavaEngine;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class DexedAudioStream implements AudioStream {
    static final int SAMPLE_RATE = 48_000;
    /** Ten milliseconds; four OpenAL buffers keep the live queue near 40 ms. */
    private static final int LOW_LATENCY_FRAMES = SAMPLE_RATE / 100;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private DexedEngine engine;
    private int bend14 = 8192;
    private int bendRange = 2;
    private float performancePitchSemitones;

    DexedAudioStream(DexedPatch patch) {
        engine = new DexedJavaEngine(SAMPLE_RATE);
        engine.setPatch(patch.bytes());
    }

    @Override public AudioFormat getFormat() { return FORMAT; }

    @Override public synchronized ByteBuffer read(int requestedBytes) {
        // Vanilla requests one second and prequeues four buffers. The synth's
        // dedicated sound-thread pump keeps these 10 ms chunks supplied more
        // frequently than Minecraft's normal 20 Hz streaming tick.
        int frames = Math.min(LOW_LATENCY_FRAMES, Math.max(1, requestedBytes / 2));
        ByteBuffer result = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
        if (engine != null) engine.render(result, frames);
        result.position(0);
        result.limit(frames * 2);
        return result;
    }

    synchronized void patch(DexedPatch patch) { if (engine != null) engine.setPatch(patch.bytes()); }
    synchronized long noteOn(int note, int velocity) {
        return engine == null ? 0L : engine.noteOn(note, velocity);
    }
    synchronized void noteOff(int note) { if (engine != null) engine.noteOff(note); }
    synchronized void noteOffVoice(long voiceToken) {
        if (engine != null && voiceToken != 0L) engine.noteOffVoice(voiceToken);
    }
    synchronized void voiceExpression(long voiceToken, float bendSemitones,
                                      int modulation, float gain) {
        if (engine != null) engine.voiceExpression(voiceToken, bendSemitones,
                modulation, gain);
    }
    synchronized void voiceGlissando(long voiceToken, float[] waypoints,
                                     float[] positions, long durationMicros) {
        if (engine != null) engine.voiceGlissando(voiceToken, waypoints, positions,
                Math.max(1L, Math.round(durationMicros * SAMPLE_RATE / 1_000_000.0D)));
    }
    synchronized void cc(int controller, int value) {
        if (engine != null) engine.controlChange(controller, value);
    }
    synchronized void bend(int value, int range) {
        bend14 = Math.clamp(value, 0, 16_383);
        bendRange = Math.clamp(range, 1, 48);
        applyPitch();
    }

    synchronized void bendSemitones(float semitones) {
        bend14 = Math.clamp(Math.round(8192.0F
                + Math.clamp(semitones, -48.0F, 48.0F) / 48.0F * 8192.0F), 0, 16_383);
        bendRange = 48;
        applyPitch();
    }

    synchronized void performancePitch(float tuningHz, DexedPerformance performance) {
        if (engine != null) engine.setPerformance(performance);
        float safeTuning = Math.clamp(tuningHz, 100.0F, 1_000.0F);
        performancePitchSemitones = (float) (12.0D * Math.log(safeTuning / 440.0D)
                / Math.log(2.0D)) + performance.fineCents() / 100.0F;
        applyPitch();
    }

    private void applyPitch() {
        if (engine == null) return;
        float bend = (bend14 - 8192) / 8192.0F * bendRange;
        float combined = Math.clamp(bend + performancePitchSemitones, -48.0F, 48.0F);
        int combined14 = Math.clamp(Math.round(8192.0F + combined / 48.0F * 8192.0F),
                0, 16_383);
        engine.pitchBend(combined14, 48);
    }

    @Override public synchronized void close() {
        if (engine == null) return;
        engine.close();
        engine = null;
    }
}
