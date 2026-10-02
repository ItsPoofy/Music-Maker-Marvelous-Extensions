package m3e.dexed;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DexedJavaEngine implements DexedEngine {
    private static final int VOICE_COUNT = 16;
    private static final int PATCH_BYTES = DexedPatch.SIZE;
    private static final long MIX_FULL_SCALE = 1L << 24;
    private static final long LIMITER_TARGET = MIX_FULL_SCALE / 2;
    private static final double LIMITER_RELEASE = 0.005;

    private final DexedJavaMkI core = new DexedJavaMkI();
    private final DexedJavaControllers controllers = new DexedJavaControllers();
    private final DexedJavaMath.FrequencyLookup frequencyLookup;
    private final DexedJavaPortamento portamento;
    private final DexedJavaLfo lfo;
    private final Voice[] voices = new Voice[VOICE_COUNT];
    private final int[] renderBlock = new int[DexedJavaMkI.BLOCK_SIZE];
    private final int[] voiceBlock = new int[DexedJavaMkI.BLOCK_SIZE];
    private byte[] patch;
    private int renderOffset = DexedJavaMkI.BLOCK_SIZE;
    private long sequence;
    private boolean sustain;
    private boolean closed;
    private DexedPerformance performance = DexedPerformance.DEFAULT;
    private final Map<Long, MonoKey> monoKeys = new LinkedHashMap<>();
    private MonoPriority monoPriority = MonoPriority.NONE;
    private long activeMonoToken;
    private int lastMonoNote = -1;
    private int lastPlayedNote = -1;
    private double limiterGain = 1.0;
    private double limiterStep;
    private boolean limiterPrimed;

    public DexedJavaEngine(int sampleRate) {
        frequencyLookup = new DexedJavaMath.FrequencyLookup(sampleRate);
        portamento = new DexedJavaPortamento(sampleRate);
        lfo = new DexedJavaLfo(sampleRate);
        for (int index = 0; index < voices.length; index++) {
            voices[index] = new Voice(new DexedJavaNote(sampleRate,
                    frequencyLookup, portamento, core));
        }
        setPatch(DexedPatch.initVoice().bytes());
    }

    @Override public synchronized void setPatch(byte[] source) {
        requireOpen();
        if (source.length != PATCH_BYTES) {
            throw new IllegalArgumentException("DX7 patch must contain 156 bytes");
        }
        patch = source.clone();
        patch[155] &= 0x3f;
        for (int operator = 0; operator < 6; operator++) {
            controllers.operatorEnabled[operator] = ((patch[155] >> operator) & 1) != 0;
        }
        lfo.reset(Arrays.copyOfRange(patch, 137, 143));
        for (Voice voice : voices) {
            if (voice.live) voice.note.update(patch,
                    transposedMidiNote(voice.midiNote, patch), voice.velocity);
        }
    }

    @Override public synchronized void setPerformance(DexedPerformance next) {
        requireOpen();
        next = next == null ? DexedPerformance.DEFAULT : next;
        if (performance.mono() != next.mono()) allNotesOffLocked();
        performance = next;
        controllers.portamentoEnabled = glideEnabled(next);
        controllers.portamentoController = Math.clamp(Math.round(
                next.portamentoTime() * 127.0F / DexedPerformance.MAX_PORTAMENTO_TIME),
                0, 127);
        controllers.portamentoGlissando = next.glissando();
    }

    @Override public synchronized long noteOn(int midiNote, int velocity) {
        requireOpen();
        if (velocity <= 0) {
            noteOffLocked(midiNote);
            return 0L;
        }
        if (performance.mono()) return noteOnMono(midiNote, velocity);
        return noteOnPoly(midiNote, velocity);
    }

    private long noteOnPoly(int midiNote, int velocity) {
        int boundedNote = Math.clamp(midiNote, 0, 127);
        int index = chooseVoice(boundedNote);
        Voice voice = voices[index];
        int retainedPitch = voice.live ? voice.midiNote : lastPlayedNote;
        int glideFrom = performance.polyPortamento()
                == DexedPerformance.PolyPortamento.FOLLOW
                ? lastPlayedNote : retainedPitch;
        voice.midiNote = boundedNote;
        voice.velocity = Math.clamp(velocity, 1, 127);
        voice.down = true;
        voice.sustained = false;
        voice.live = true;
        voice.sequence = ++sequence;
        voice.resetAutomation();
        voice.note.initialize(patch, transposedMidiNote(voice.midiNote, patch),
                voice.velocity, controllers);
        if (glideEnabled() && glideFrom >= 0
                && glideFrom != boundedNote) {
            voice.note.startPortamentoFrom(patch, transposedMidiNote(glideFrom, patch));
        }
        if (patch[136] != 0) voice.note.oscillatorSync();
        lfo.keyDown();
        lastPlayedNote = boundedNote;
        return voice.sequence << 4 | index;
    }

    private long noteOnMono(int midiNote, int velocity) {
        int boundedNote = Math.clamp(midiNote, 0, 127);
        int boundedVelocity = Math.clamp(velocity, 1, 127);
        Voice voice = voices[0];
        boolean hadHeldKeys = hasDownMonoKey();
        int previousNote = activeMonoToken != 0L ? voice.midiNote : lastMonoNote;
        if (monoPriority == MonoPriority.NONE && hadHeldKeys && previousNote >= 0
                && boundedNote != previousNote) {
            monoPriority = boundedNote > previousNote
                    ? MonoPriority.HIGH : MonoPriority.LOW;
        }
        long token = ++sequence << 4;
        monoKeys.put(token, new MonoKey(token, boundedNote, boundedVelocity, true, false));
        MonoKey selected = selectedMonoKey();
        if (selected == null) return token;
        if (!hadHeldKeys || !voice.live || !voice.note.isPlaying()) {
            triggerMonoVoice(voice, selected, previousNote,
                    glideEnabled()
                            && performance.monoPortamento()
                            == DexedPerformance.MonoPortamento.ALWAYS);
        } else if (selected.token() != activeMonoToken) {
            retargetMonoVoice(voice, selected, glideEnabled());
        }
        lastPlayedNote = boundedNote;
        return token;
    }

    private void triggerMonoVoice(Voice voice, MonoKey selected, int previousNote,
                                  boolean glideAcrossGap) {
        voice.midiNote = selected.note();
        voice.velocity = selected.velocity();
        voice.down = selected.down();
        voice.sustained = selected.sustained();
        voice.live = true;
        voice.sequence = selected.token() >>> 4;
        voice.resetAutomation();
        voice.note.initialize(patch, transposedMidiNote(selected.note(), patch),
                selected.velocity(), controllers);
        if (glideAcrossGap && previousNote >= 0 && previousNote != selected.note()) {
            voice.note.startPortamentoFrom(patch,
                    transposedMidiNote(previousNote, patch));
        }
        if (patch[136] != 0) voice.note.oscillatorSync();
        lfo.keyDown();
        activeMonoToken = selected.token();
    }

    private void retargetMonoVoice(Voice voice, MonoKey selected, boolean glide) {
        lastMonoNote = voice.midiNote;
        voice.midiNote = selected.note();
        voice.velocity = selected.velocity();
        voice.down = selected.down();
        voice.sustained = selected.sustained();
        voice.resetAutomation();
        voice.note.retarget(patch, transposedMidiNote(selected.note(), patch),
                selected.velocity(), glide);
        activeMonoToken = selected.token();
    }

    @Override public synchronized void noteOff(int midiNote) {
        if (!closed) noteOffLocked(midiNote);
    }

    @Override public synchronized void noteOffVoice(long token) {
        if (closed) return;
        if (monoKeys.containsKey(token)) {
            releaseMonoKey(token);
            return;
        }
        int index = (int) (token & 0x0f);
        long generation = token >>> 4;
        if (index < 0 || index >= voices.length) return;
        Voice voice = voices[index];
        if (!voice.live || voice.sequence != generation) return;
        voice.down = false;
        if (sustain) voice.sustained = true;
        else voice.note.keyUp();
    }

    @Override public synchronized void voiceExpression(long token, float bendSemitones,
                                                        int modulation, float gain) {
        Voice voice = voice(token);
        if (voice == null) return;
        voice.bendSemitones = Math.clamp(bendSemitones, -96.0F, 96.0F);
        voice.modulation = Math.clamp(modulation, 0, 127);
        voice.gain = Math.clamp(gain, 0.0F, 4.0F);
    }

    @Override public synchronized void voiceGlissando(long token, float[] waypoints,
                                                       float[] positions,
                                                       long durationFrames) {
        Voice voice = voice(token);
        if (voice == null || waypoints == null || waypoints.length == 0) return;
        voice.glissando = new Glissando(waypoints, positions, durationFrames);
    }

    private Voice voice(long token) {
        if (closed || token == 0L) return null;
        if (monoKeys.containsKey(token)) return token == activeMonoToken ? voices[0] : null;
        int index = (int) (token & 0x0f);
        if (index < 0 || index >= voices.length) return null;
        Voice voice = voices[index];
        return voice.live && voice.sequence == (token >>> 4) ? voice : null;
    }

    @Override public synchronized void controlChange(int controller, int value) {
        if (closed) return;
        int bounded = Math.clamp(value, 0, 127);
        if (controller == 1) {
            controllers.modulationWheelController = bounded;
            controllers.refresh();
        } else if (controller == 64) {
            boolean next = bounded >= 64;
            if (sustain && !next && performance.mono()) {
                sustain = false;
                releaseSustainedMonoKeys();
                return;
            }
            if (sustain && !next) {
                for (Voice voice : voices) {
                    if (voice.sustained && !voice.down) {
                        voice.note.keyUp();
                        voice.sustained = false;
                    }
                }
            }
            sustain = next;
        } else if (controller == 5) {
            controllers.portamentoController = bounded;
        } else if (controller == 65) {
            controllers.portamentoEnabled = bounded >= 64;
        }
    }

    @Override public synchronized void pitchBend(int bend14, int rangeSemitones) {
        if (closed) return;
        controllers.values[DexedJavaControllers.PITCH] = Math.clamp(bend14, 0, 16_383);
        int range = boundedPitchBendRange(rangeSemitones);
        controllers.values[DexedJavaControllers.PITCH_RANGE_UP] = range;
        controllers.values[DexedJavaControllers.PITCH_RANGE_DOWN] = range;
    }

    /** Exposes the renderer's carrier role so the editor cannot drift from its audio routing. */
    public static boolean isCarrier(int algorithm, int storageOperator) {
        return DexedJavaMkI.isCarrier(Math.clamp(algorithm, 0, 31),
                Math.clamp(storageOperator, 0, 5));
    }

    /** Returns whether one stored DX7 operator directly modulates another. */
    public static boolean modulates(int algorithm, int sourceStorageOperator,
                                    int targetStorageOperator) {
        return DexedJavaMkI.modulates(Math.clamp(algorithm, 0, 31),
                Math.clamp(sourceStorageOperator, 0, 5),
                Math.clamp(targetStorageOperator, 0, 5));
    }

    static int transposedMidiNote(int midiNote, byte[] voice) {
        int transpose = Byte.toUnsignedInt(voice[144]) - 24;
        return Math.clamp(midiNote + transpose, 0, 127);
    }

    static int boundedPitchBendRange(int semitones) {
        return Math.clamp(semitones, 1, 48);
    }

    @Override public synchronized void allNotesOff() {
        if (closed) return;
        allNotesOffLocked();
    }

    private void allNotesOffLocked() {
        sustain = false;
        for (Voice voice : voices) {
            if (voice.live) voice.note.keyUp();
            voice.down = false;
            voice.sustained = false;
        }
        monoKeys.clear();
        monoPriority = MonoPriority.NONE;
        activeMonoToken = 0L;
        lastMonoNote = -1;
        lastPlayedNote = -1;
    }

    @Override public synchronized int render(ByteBuffer destination, int frames) {
        requireOpen();
        if (frames <= 0 || destination.capacity() < frames * Short.BYTES) return 0;
        int written = 0;
        while (written < frames) {
            if (renderOffset >= DexedJavaMkI.BLOCK_SIZE) renderNextBlock();
            int count = Math.min(DexedJavaMkI.BLOCK_SIZE - renderOffset, frames - written);
            for (int index = 0; index < count; index++) {
                long value = Math.round((renderBlock[renderOffset + index] >> 4) * limiterGain);
                int clipped = value < -MIX_FULL_SCALE ? -32_768
                        : value >= MIX_FULL_SCALE ? 32_767 : (int) (value >> 9);
                destination.putShort((written + index) * Short.BYTES, (short) clipped);
                limiterGain += limiterStep;
            }
            renderOffset += count;
            written += count;
        }
        return written;
    }

    @Override public synchronized void close() {
        if (closed) return;
        allNotesOff();
        closed = true;
    }

    private void renderNextBlock() {
        Arrays.fill(renderBlock, 0);
        int lfoValue = lfo.sample();
        int lfoDelay = lfo.delay();
        for (Voice voice : voices) {
            if (!voice.live) continue;
            Arrays.fill(voiceBlock, 0);
            float bend = voice.bendSemitones;
            if (voice.glissando != null) {
                bend += voice.glissando.value();
                voice.glissando.advance(DexedJavaMkI.BLOCK_SIZE);
            }
            voice.note.compute(voiceBlock, lfoValue, lfoDelay, controllers,
                    bend, voice.modulation);
            for (int index = 0; index < renderBlock.length; index++) {
                long mixed = renderBlock[index]
                        + Math.round(voiceBlock[index] * (double) voice.gain);
                renderBlock[index] = (int) Math.clamp(mixed,
                        Integer.MIN_VALUE, Integer.MAX_VALUE);
            }
            if (!voice.down && !voice.sustained && !voice.note.isPlaying()) voice.live = false;
        }
        long peak = 0L;
        for (int sample : renderBlock) {
            long value = ((long) sample) >> 4;
            peak = Math.max(peak, value < 0 ? -value : value);
        }
        double requiredGain = peak > LIMITER_TARGET
                ? LIMITER_TARGET / (double) peak : 1.0;
        if (peak == 0L) {
            limiterGain = 1.0;
            limiterStep = 0.0;
            limiterPrimed = false;
        } else if (!limiterPrimed) {
            limiterGain = requiredGain;
            limiterStep = 0.0;
            limiterPrimed = true;
        } else {
            double targetGain = Math.min(requiredGain,
                    limiterGain + (1.0 - limiterGain) * LIMITER_RELEASE);
            limiterStep = (targetGain - limiterGain) / DexedJavaMkI.BLOCK_SIZE;
        }
        renderOffset = 0;
    }

    synchronized int[] authenticityRawBlock() {
        requireOpen();
        renderNextBlock();
        return renderBlock.clone();
    }

    synchronized int activeMonoNoteForTest() {
        return activeMonoToken == 0L ? -1 : voices[0].midiNote;
    }

    synchronized int activeMonoEnvelopeStageForTest() {
        return voices[0].note.envelopeStageForTest();
    }

    synchronized int activeMonoPortamentoPitchForTest() {
        return voices[0].note.portamentoPitchForTest();
    }

    synchronized int activeMonoSoundingPitchForTest() {
        return voices[0].note.soundingPortamentoPitchForTest(performance.glissando());
    }

    synchronized int voicePortamentoPitchForTest(long token) {
        int index = (int) (token & 0x0f);
        return index < 0 || index >= voices.length
                ? Integer.MIN_VALUE : voices[index].note.portamentoPitchForTest();
    }

    private int chooseVoice(int midiNote) {
        int best = 0;
        int bestScore = -1;
        for (int index = 0; index < voices.length; index++) {
            Voice voice = voices[index];
            int score = (!voice.live || !voice.note.isPlaying() ? 4 : 0)
                    + (!voice.down ? 2 : 0) + (voice.midiNote == midiNote ? 1 : 0);
            if (score > bestScore
                    || (score == bestScore && voice.sequence < voices[best].sequence)) {
                best = index;
                bestScore = score;
            }
        }
        return best;
    }

    private void noteOffLocked(int midiNote) {
        if (performance.mono()) {
            ArrayList<Long> tokens = new ArrayList<>();
            for (MonoKey key : monoKeys.values()) {
                if (key.down() && key.note() == midiNote) tokens.add(key.token());
            }
            for (long token : tokens) releaseMonoKey(token);
            return;
        }
        for (Voice voice : voices) {
            if (!voice.down || voice.midiNote != midiNote) continue;
            voice.down = false;
            if (sustain) voice.sustained = true;
            else voice.note.keyUp();
        }
    }

    private void releaseMonoKey(long token) {
        MonoKey key = monoKeys.get(token);
        if (key == null || !key.down()) return;
        if (sustain) {
            monoKeys.put(token, new MonoKey(token, key.note(), key.velocity(),
                    false, true));
        } else {
            monoKeys.remove(token);
        }
        settleMonoVoice();
    }

    private void releaseSustainedMonoKeys() {
        monoKeys.values().removeIf(key -> !key.down());
        settleMonoVoice();
    }

    private void settleMonoVoice() {
        Voice voice = voices[0];
        MonoKey selected = selectedMonoKey();
        if (selected != null) {
            if (selected.token() != activeMonoToken) {
                retargetMonoVoice(voice, selected, glideEnabled());
            } else {
                voice.down = selected.down();
                voice.sustained = selected.sustained();
            }
        } else if (voice.live) {
            lastMonoNote = voice.midiNote;
            voice.down = false;
            voice.sustained = false;
            voice.note.keyUp();
            activeMonoToken = 0L;
        }
        if (!hasDownMonoKey()) monoPriority = MonoPriority.NONE;
    }

    private boolean hasDownMonoKey() {
        return monoKeys.values().stream().anyMatch(MonoKey::down);
    }

    private boolean glideEnabled() { return glideEnabled(performance); }

    private static boolean glideEnabled(DexedPerformance value) {
        return value.portamentoEnabled()
                && value.portamentoTime() > DexedPerformance.MIN_PORTAMENTO_TIME;
    }

    private MonoKey selectedMonoKey() {
        MonoKey selected = null;
        for (MonoKey key : monoKeys.values()) {
            if (!key.down() && !key.sustained()) continue;
            if (selected == null) {
                selected = key;
                continue;
            }
            if (monoPriority == MonoPriority.HIGH && key.note() > selected.note()
                    || monoPriority == MonoPriority.LOW && key.note() < selected.note()
                    || monoPriority == MonoPriority.NONE && key.token() > selected.token()) {
                selected = key;
            }
        }
        return selected;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Dexed engine is closed");
    }

    private static final class Voice {
        private final DexedJavaNote note;
        private int midiNote = -1;
        private int velocity;
        private boolean down;
        private boolean sustained;
        private boolean live;
        private long sequence;
        private float bendSemitones;
        private int modulation;
        private float gain = 1.0F;
        private Glissando glissando;

        private Voice(DexedJavaNote note) { this.note = note; }

        private void resetAutomation() {
            bendSemitones = 0.0F;
            modulation = 0;
            gain = 1.0F;
            glissando = null;
        }
    }

    private static final class Glissando {
        private final float[] waypoints;
        private final float[] positions;
        private final long durationFrames;
        private long frame;

        private Glissando(float[] waypoints, float[] positions, long durationFrames) {
            this.waypoints = waypoints.clone();
            this.positions = normalizedPositions(positions, waypoints.length);
            this.durationFrames = Math.max(1L, durationFrames);
        }

        private float value() {
            float progress = Math.clamp(frame / (float) durationFrames, 0.0F, 1.0F);
            float priorPosition = 0.0F;
            float priorValue = 0.0F;
            for (int index = 0; index < waypoints.length; index++) {
                float nextPosition = positions[index];
                if (progress <= nextPosition) {
                    float span = Math.max(0.0001F, nextPosition - priorPosition);
                    return priorValue + (waypoints[index] - priorValue)
                            * ((progress - priorPosition) / span);
                }
                priorPosition = nextPosition;
                priorValue = waypoints[index];
            }
            return waypoints[waypoints.length - 1];
        }

        private void advance(int frames) { frame = Math.min(durationFrames, frame + frames); }

        private static float[] normalizedPositions(float[] positions, int count) {
            float[] result = new float[count];
            for (int index = 0; index < count; index++) {
                result[index] = positions != null && positions.length == count
                        ? Math.clamp(positions[index], 0.0F, 1.0F)
                        : (index + 1.0F) / count;
            }
            return result;
        }
    }

    private enum MonoPriority { NONE, HIGH, LOW }

    private record MonoKey(long token, int note, int velocity,
                           boolean down, boolean sustained) { }
}
