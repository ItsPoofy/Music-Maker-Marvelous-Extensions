package m3e.dexed;

import java.nio.ByteBuffer;

/**
 * Stateful, 16-voice DX7 Mark I renderer contract used by the pure-Java
 * Redstone Synthesizer runtime.
 */
public interface DexedEngine extends AutoCloseable {
    void setPatch(byte[] patch);

    void setPerformance(DexedPerformance performance);

    long noteOn(int midiNote, int velocity);

    void noteOff(int midiNote);

    void noteOffVoice(long voiceToken);

    void voiceExpression(long voiceToken, float bendSemitones, int modulation, float gain);

    void voiceGlissando(long voiceToken, float[] semitoneWaypoints,
                        float[] waypointPositions, long durationFrames);

    void controlChange(int controller, int value);

    void pitchBend(int bend14, int rangeSemitones);

    void allNotesOff();

    int render(ByteBuffer destination, int frames);

    @Override void close();
}
