package m3e.dexed;

/** Active standard-tuning DX7 note path used by M3E's Mark I host. */
final class DexedJavaNote {
    private static final int FEEDBACK_BIT_DEPTH = 8;
    private static final int[] COARSE_MULTIPLIER = {
            -16777216,0,16777216,26591258,33554432,38955489,43368474,47099600,
            50331648,53182516,55732705,58039632,60145690,62083076,63876816,
            65546747,67108864,68576247,69959732,71268397,72509921,73690858,
            74816848,75892776,76922906,77910978,78860292,79773775,80654032,
            81503396,82323963,83117622
    };
    private static final int[] VELOCITY = {
            0,70,86,97,106,114,121,126,132,138,142,148,152,156,160,163,
            166,170,173,174,178,181,184,186,189,190,194,196,198,200,202,205,
            206,209,211,214,216,218,220,222,224,225,227,229,230,232,233,235,
            237,238,240,241,242,243,244,246,246,248,249,250,251,252,253,254
    };
    private static final int[] EXPONENTIAL_SCALE = {
            0,1,2,3,4,5,6,7,8,9,11,14,16,19,23,27,33,39,47,56,66,80,94,
            110,126,142,158,174,190,206,222,238,250
    };
    private static final int[] PITCH_MODULATION_SENSITIVITY = {0,10,20,33,55,92,153,255};
    private static final int[] AMPLITUDE_MODULATION_SENSITIVITY = {
            0,4_342_338,7_171_437,16_777_216
    };

    private final DexedJavaEnvelope[] envelopes = new DexedJavaEnvelope[6];
    private final DexedJavaMkI.OperatorParameters[] parameters =
            new DexedJavaMkI.OperatorParameters[6];
    private final DexedJavaPitchEnvelope pitchEnvelope;
    private final DexedJavaMath.FrequencyLookup frequencyLookup;
    private final DexedJavaPortamento portamento;
    private final DexedJavaMkI core;
    private final int[] basePitch = new int[6];
    private final int[] feedback = new int[2];
    private final int[] amplitudeModulationSensitivity = new int[6];
    private final int[] operatorMode = new int[6];
    private final int[] portamentoPitch = new int[6];
    private boolean initialized;
    private int feedbackShift;
    private int amplitudeModulationDepth;
    private int algorithm;
    private int pitchModulationDepth;
    private int pitchModulationSensitivity;

    DexedJavaNote(double sampleRate, DexedJavaMath.FrequencyLookup frequencyLookup,
                  DexedJavaPortamento portamento, DexedJavaMkI core) {
        this.frequencyLookup = frequencyLookup;
        this.portamento = portamento;
        this.core = core;
        pitchEnvelope = new DexedJavaPitchEnvelope(sampleRate);
        for (int operator = 0; operator < 6; operator++) {
            envelopes[operator] = new DexedJavaEnvelope(sampleRate);
            parameters[operator] = new DexedJavaMkI.OperatorParameters(0, 0, 0, 0);
        }
    }

    void initialize(byte[] patch, int midiNote, int velocity,
                    DexedJavaControllers controllers) {
        initialized = true;
        int[] rates = new int[4];
        int[] levels = new int[4];
        for (int operator = 0; operator < 6; operator++) {
            int offset = operator * 21;
            readFour(patch, offset, rates);
            readFour(patch, offset + 4, levels);
            int outputLevel = operatorOutputLevel(patch, offset, midiNote, velocity);
            int rateScaling = scaleRate(midiNote, unsigned(patch[offset + 13]));
            envelopes[operator].initialize(rates, levels, outputLevel, rateScaling);

            int mode = unsigned(patch[offset + 17]);
            int frequency = oscillatorFrequency(midiNote, mode,
                    unsigned(patch[offset + 18]), unsigned(patch[offset + 19]),
                    unsigned(patch[offset + 20]));
            operatorMode[operator] = mode;
            basePitch[operator] = frequency;
            portamentoPitch[operator] = frequency;
            amplitudeModulationSensitivity[operator] =
                    AMPLITUDE_MODULATION_SENSITIVITY[unsigned(patch[offset + 14]) & 3];
        }
        readFour(patch, 126, rates);
        readFour(patch, 130, levels);
        pitchEnvelope.set(rates, levels);
        algorithm = unsigned(patch[134]);
        int patchFeedback = unsigned(patch[135]);
        feedbackShift = patchFeedback != 0 ? FEEDBACK_BIT_DEPTH - patchFeedback : 16;
        pitchModulationDepth = (unsigned(patch[139]) * 165) >> 6;
        pitchModulationSensitivity =
                PITCH_MODULATION_SENSITIVITY[unsigned(patch[143]) & 7];
        amplitudeModulationDepth = (unsigned(patch[140]) * 165) >> 6;
    }

    void compute(int[] output, int lfoValue, int lfoDelay,
                 DexedJavaControllers controllers, float voiceBendSemitones,
                 int voiceModulation) {
        int pitchDepth = pitchModulationDepth * lfoDelay;
        int sensitiveLfo = pitchModulationSensitivity * (lfoValue - (1 << 23));
        int firstPitchModulation = (int) ((Integer.toUnsignedLong(pitchDepth)
                * sensitiveLfo) >> 39);
        firstPitchModulation = Math.abs(firstPitchModulation);
        int secondPitchModulation = (int) (((long) Math.max(controllers.pitchModulation,
                Math.clamp(voiceModulation, 0, 127))
                * sensitiveLfo) >> 14);
        secondPitchModulation = Math.abs(secondPitchModulation);
        int pitchModulation = Math.max(firstPitchModulation, secondPitchModulation);
        pitchModulation = pitchEnvelope.sample()
                + pitchModulation * (sensitiveLfo < 0 ? -1 : 1);

        int pitchBend = controllers.values[DexedJavaControllers.PITCH];
        int bend = pitchBend - 0x2000;
        if (bend != 0) {
            if (controllers.values[DexedJavaControllers.PITCH_STEP] == 0) {
                int range = bend >= 0
                        ? controllers.values[DexedJavaControllers.PITCH_RANGE_UP]
                        : controllers.values[DexedJavaControllers.PITCH_RANGE_DOWN];
                bend = (int) (((float) (bend << 11) * (float) range) / 12.0);
            } else {
                int step = 12 / controllers.values[DexedJavaControllers.PITCH_STEP];
                bend = bend * step / 8191;
                bend = (bend * (8191 / step)) << 11;
            }
        }
        int pitchBase = bend + controllers.masterTune
                + Math.round(voiceBendSemitones * (1 << 24) / 12.0F);
        pitchModulation += pitchBase;

        int invertedLfo = (1 << 24) - lfoValue;
        int firstAmplitude = (int) (((long) amplitudeModulationDepth * lfoDelay) >> 8);
        firstAmplitude = (int) ((Integer.toUnsignedLong(firstAmplitude)
                * Integer.toUnsignedLong(invertedLfo)) >> 24);
        int secondAmplitude = (int) ((Integer.toUnsignedLong(controllers.amplitudeModulation)
                * Integer.toUnsignedLong(invertedLfo)) >> 7);
        int amplitudeModulation = unsignedMax(firstAmplitude, secondAmplitude);
        int envelopeAmplitude = (controllers.envelopeModulation + 1) << 17;
        amplitudeModulation = unsignedMax((1 << 24) - envelopeAmplitude,
                amplitudeModulation);

        int portamentoRate = controllers.portamentoEnabled
                ? portamento.rate(controllers.portamentoController,
                controllers.portamentoGlissando)
                : portamento.rate(0, false);

        for (int operator = 0; operator < 6; operator++) {
            if (!controllers.operatorEnabled[operator]) {
                envelopes[operator].sample();
                parameters[operator].levelInput = 0;
                continue;
            }
            int operatorBasePitch = basePitch[operator];
            if (operatorMode[operator] != 0) {
                parameters[operator].frequency = frequencyLookup.lookup(
                        operatorBasePitch + pitchBase);
            } else {
                if (!controllers.portamentoEnabled) {
                    portamentoPitch[operator] = basePitch[operator];
                }
                if (portamentoPitch[operator] != basePitch[operator]) {
                    operatorBasePitch = portamentoPitch[operator];
                    if (controllers.portamentoGlissando) {
                        operatorBasePitch = roundLogFrequencyToSemitone(operatorBasePitch);
                    }
                    int current = portamentoPitch[operator];
                    int destination = basePitch[operator];
                    boolean rising = current < destination;
                    int next = current + (rising ? portamentoRate : -portamentoRate);
                    if ((rising && next > destination) || (!rising && next < destination)) {
                        next = destination;
                    }
                    portamentoPitch[operator] = next;
                }
                parameters[operator].frequency = frequencyLookup.lookup(
                        operatorBasePitch + pitchModulation);
            }

            int level = envelopes[operator].sample();
            if (amplitudeModulationSensitivity[operator] != 0) {
                int sensitiveAmplitude = (int) ((Integer.toUnsignedLong(amplitudeModulation)
                        * Integer.toUnsignedLong(amplitudeModulationSensitivity[operator])) >> 24);
                int point = (int) StrictMath.exp(((float) sensitiveAmplitude) / 262_144
                        * 0.07 + 12.2);
                int levelDifference = (int) ((Integer.toUnsignedLong(level)
                        * (Integer.toUnsignedLong(point) << 4)) >> 28);
                level -= levelDifference;
            }
            parameters[operator].levelInput = level;
        }
        core.render(output, parameters, algorithm, feedback, feedbackShift);
    }

    void keyUp() {
        for (DexedJavaEnvelope envelope : envelopes) envelope.keyDown(false);
        pitchEnvelope.keyDown(false);
    }

    /** Changes a held mono note without restarting its envelopes or oscillator phases. */
    void retarget(byte[] patch, int midiNote, int velocity, boolean glide) {
        update(patch, midiNote, velocity);
        if (!glide) snapPortamento();
    }

    /** Starts a newly triggered voice at the prior played pitch for Always/poly glide. */
    void startPortamentoFrom(byte[] patch, int midiNote) {
        for (int operator = 0; operator < 6; operator++) {
            int offset = operator * 21;
            int mode = unsigned(patch[offset + 17]);
            if (mode == 0) {
                portamentoPitch[operator] = oscillatorFrequency(midiNote, mode,
                        unsigned(patch[offset + 18]), unsigned(patch[offset + 19]),
                        unsigned(patch[offset + 20]));
            }
        }
    }

    private void snapPortamento() {
        System.arraycopy(basePitch, 0, portamentoPitch, 0, basePitch.length);
    }

    void update(byte[] patch, int midiNote, int velocity) {
        int[] rates = new int[4];
        int[] levels = new int[4];
        for (int operator = 0; operator < 6; operator++) {
            int offset = operator * 21;
            int mode = unsigned(patch[offset + 17]);
            basePitch[operator] = oscillatorFrequency(midiNote, mode,
                    unsigned(patch[offset + 18]), unsigned(patch[offset + 19]),
                    unsigned(patch[offset + 20]));
            amplitudeModulationSensitivity[operator] =
                    AMPLITUDE_MODULATION_SENSITIVITY[unsigned(patch[offset + 14]) & 3];
            operatorMode[operator] = mode;
            readFour(patch, offset, rates);
            readFour(patch, offset + 4, levels);
            envelopes[operator].update(rates, levels,
                    operatorOutputLevel(patch, offset, midiNote, velocity),
                    scaleRate(midiNote, unsigned(patch[offset + 13])));
        }
        readFour(patch, 126, rates);
        readFour(patch, 130, levels);
        pitchEnvelope.update(rates, levels);
        algorithm = unsigned(patch[134]);
        int patchFeedback = unsigned(patch[135]);
        feedbackShift = patchFeedback != 0 ? FEEDBACK_BIT_DEPTH - patchFeedback : 16;
        pitchModulationDepth = (unsigned(patch[139]) * 165) >> 6;
        pitchModulationSensitivity =
                PITCH_MODULATION_SENSITIVITY[unsigned(patch[143]) & 7];
        amplitudeModulationDepth = (unsigned(patch[140]) * 165) >> 6;
    }

    void oscillatorSync() {
        for (DexedJavaMkI.OperatorParameters parameter : parameters) {
            parameter.gainOutput = 0;
            parameter.phase = 0;
        }
    }

    boolean isPlaying() {
        if (!initialized) return false;
        for (int operator = 0; operator < 6; operator++) {
            if (DexedJavaMkI.isCarrier(algorithm, operator)
                    && envelopes[operator].isActive()) return true;
        }
        return false;
    }

    int envelopeStageForTest() { return envelopes[0].stage(); }

    int portamentoPitchForTest() { return portamentoPitch[0]; }

    int soundingPortamentoPitchForTest(boolean glissando) {
        return glissando ? roundLogFrequencyToSemitone(portamentoPitch[0])
                : portamentoPitch[0];
    }

    private static int operatorOutputLevel(byte[] patch, int offset,
                                           int midiNote, int velocity) {
        int outputLevel = DexedJavaEnvelope.scaleOutputLevel(unsigned(patch[offset + 16]));
        outputLevel += scaleLevel(midiNote, unsigned(patch[offset + 8]),
                unsigned(patch[offset + 9]), unsigned(patch[offset + 10]),
                unsigned(patch[offset + 11]), unsigned(patch[offset + 12]));
        outputLevel = Math.min(127, outputLevel) << 5;
        outputLevel += scaleVelocity(velocity, unsigned(patch[offset + 15]));
        return Math.max(0, outputLevel);
    }

    static int oscillatorFrequency(int midiNote, int mode, int coarse,
                                   int fine, int detune) {
        if (mode != 0) {
            int logFrequency = (4_458_616 * ((coarse & 3) * 100 + fine)) >> 3;
            if (detune > 7) logFrequency += 13_457 * (detune - 7);
            return logFrequency;
        }
        int logFrequency = standardLogFrequency(midiNote);
        double detuneRatio = 0.0209 * StrictMath.exp(-0.396
                * (((float) logFrequency) / (1 << 24))) / 7.0;
        logFrequency = (int) (logFrequency
                + detuneRatio * logFrequency * (detune - 7));
        logFrequency += COARSE_MULTIPLIER[coarse & 31];
        if (fine != 0) {
            logFrequency += (int) StrictMath.floor(24_204_406.323123
                    * StrictMath.log(1.0 + 0.01 * fine) + 0.5);
        }
        return logFrequency;
    }

    static int standardLogFrequency(int midiNote) {
        double value = (StrictMath.log(440.0) / StrictMath.log(2.0)
                + (midiNote - 69) / 12.0) * (1 << 24);
        return (int) StrictMath.round(value);
    }

    private static int scaleVelocity(int velocity, int sensitivity) {
        int value = VELOCITY[Math.clamp(velocity, 0, 127) >> 1] - 239;
        return ((sensitivity * value + 7) >> 3) << 4;
    }

    private static int scaleRate(int midiNote, int sensitivity) {
        int value = Math.min(31, Math.max(0, midiNote / 3 - 7));
        return (sensitivity * value) >> 3;
    }

    private static int scaleLevel(int midiNote, int breakPoint, int leftDepth,
                                  int rightDepth, int leftCurve, int rightCurve) {
        int offset = midiNote - breakPoint - 17;
        return offset >= 0
                ? scaleCurve((offset + 1) / 3, rightDepth, rightCurve)
                : scaleCurve(-(offset - 1) / 3, leftDepth, leftCurve);
    }

    private static int scaleCurve(int group, int depth, int curve) {
        int scale;
        if (curve == 0 || curve == 3) {
            scale = (group * depth * 329) >> 12;
        } else {
            int raw = EXPONENTIAL_SCALE[Math.min(group, EXPONENTIAL_SCALE.length - 1)];
            scale = (raw * depth * 329) >> 15;
        }
        return curve < 2 ? -scale : scale;
    }

    private static int roundLogFrequencyToSemitone(int frequency) {
        int base = 50_857_777;
        int step = (1 << 24) / 12;
        return frequency - (frequency - base) % step;
    }

    private static void readFour(byte[] source, int offset, int[] destination) {
        for (int index = 0; index < 4; index++) {
            destination[index] = unsigned(source[offset + index]);
        }
    }

    private static int unsigned(byte value) { return Byte.toUnsignedInt(value); }

    private static int unsignedMax(int first, int second) {
        return Integer.compareUnsigned(first, second) >= 0 ? first : second;
    }
}
