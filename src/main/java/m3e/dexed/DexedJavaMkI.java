package m3e.dexed;

final class DexedJavaMkI {
    static final int BLOCK_SIZE = 64;
    private static final int LOG_TABLE_SIZE = 1 << 10;
    private static final int EXP_TABLE_SIZE = 1 << 10;
    private static final int NEGATIVE_BIT = 0x8000;
    private static final int ENVELOPE_MAX = 1 << 14;
    private static final int LEVEL_THRESHOLD = ENVELOPE_MAX - 100;
    private static final int[] LOG_SINE = createLogSineTable();
    private static final int[] EXP_SINE = createExpSineTable();
    private static final int[][] ALGORITHMS = {
            {0xc1,0x11,0x11,0x14,0x01,0x14},
            {0x01,0x11,0x11,0x14,0xc1,0x14},
            {0xc1,0x11,0x14,0x01,0x11,0x14},
            {0xc1,0x11,0x94,0x01,0x11,0x14},
            {0xc1,0x14,0x01,0x14,0x01,0x14},
            {0xc1,0x94,0x01,0x14,0x01,0x14},
            {0xc1,0x11,0x05,0x14,0x01,0x14},
            {0x01,0x11,0xc5,0x14,0x01,0x14},
            {0x01,0x11,0x05,0x14,0xc1,0x14},
            {0x01,0x05,0x14,0xc1,0x11,0x14},
            {0xc1,0x05,0x14,0x01,0x11,0x14},
            {0x01,0x05,0x05,0x14,0xc1,0x14},
            {0xc1,0x05,0x05,0x14,0x01,0x14},
            {0xc1,0x05,0x11,0x14,0x01,0x14},
            {0x01,0x05,0x11,0x14,0xc1,0x14},
            {0xc1,0x11,0x02,0x25,0x05,0x14},
            {0x01,0x11,0x02,0x25,0xc5,0x14},
            {0x01,0x11,0x11,0xc5,0x05,0x14},
            {0xc1,0x14,0x14,0x01,0x11,0x14},
            {0x01,0x05,0x14,0xc1,0x14,0x14},
            {0x01,0x14,0x14,0xc1,0x14,0x14},
            {0xc1,0x14,0x14,0x14,0x01,0x14},
            {0xc1,0x14,0x14,0x01,0x14,0x04},
            {0xc1,0x14,0x14,0x14,0x04,0x04},
            {0xc1,0x14,0x14,0x04,0x04,0x04},
            {0xc1,0x05,0x14,0x01,0x14,0x04},
            {0x01,0x05,0x14,0xc1,0x14,0x04},
            {0x04,0xc1,0x11,0x14,0x01,0x14},
            {0xc1,0x14,0x01,0x14,0x04,0x04},
            {0x04,0xc1,0x11,0x14,0x04,0x04},
            {0xc1,0x14,0x04,0x04,0x04,0x04},
            {0xc4,0x04,0x04,0x04,0x04,0x04}
    };

    private final int[][] buses = {new int[BLOCK_SIZE], new int[BLOCK_SIZE]};

    static boolean isCarrier(int algorithm, int operator) {
        int flags = ALGORITHMS[algorithm][operator];
        return (flags & 0x07) == 0x04;
    }

    static boolean modulates(int algorithm, int sourceOperator, int targetOperator) {
        int[] busSources = new int[4];
        for (int operator = 0; operator < 6; operator++) {
            int flags = ALGORITHMS[algorithm][operator];
            int inputBus = (flags >> 4) & 3;
            if (operator == targetOperator) {
                return inputBus != 0
                        && (busSources[inputBus] & (1 << sourceOperator)) != 0;
            }
            int outputBus = flags & 3;
            if (outputBus == 0) continue;
            int source = 1 << operator;
            if ((flags & 0x04) != 0) busSources[outputBus] |= source;
            else busSources[outputBus] = source;
        }
        return false;
    }

    void render(int[] output, OperatorParameters[] parameters, int algorithm,
                int[] feedback, int feedbackShift) {
        boolean[] hasContents = {true, false, false};
        boolean feedbackOn = feedbackShift < 16;

        for (int operator = 0; operator < 6; operator++) {
            int flags = ALGORITHMS[algorithm][operator];
            if ((algorithm == 3 || algorithm == 5) && operator == 0 && feedbackOn) {
                flags = 0xc4;
            }
            boolean add = (flags & 0x04) != 0;
            OperatorParameters parameter = parameters[operator];
            int inputBus = (flags >> 4) & 3;
            int outputBus = flags & 3;
            int[] destination = outputBus == 0 ? output : buses[outputBus - 1];
            int firstGain = parameter.gainOutput == 0
                    ? ENVELOPE_MAX - 1 : parameter.gainOutput;
            int secondGain = ENVELOPE_MAX - (parameter.levelInput >> 14);
            parameter.gainOutput = secondGain;

            if (firstGain <= LEVEL_THRESHOLD || secondGain <= LEVEL_THRESHOLD) {
                if (!hasContents[outputBus]) add = false;
                if (inputBus == 0 || !hasContents[inputBus]) {
                    if ((flags & 0xc0) == 0xc0 && feedbackOn) {
                        switch (algorithm) {
                            case 3 -> {
                                computeFeedbackThree(destination, parameters, firstGain,
                                        secondGain, feedback,
                                        Math.min(feedbackShift + 2, 16));
                                parameters[1].phase += parameters[1].frequency << 6;
                                parameters[2].phase += parameters[2].frequency << 6;
                                operator += 2;
                            }
                            case 5 -> {
                                computeFeedbackTwo(destination, parameters, firstGain,
                                        secondGain, feedback,
                                        Math.min(feedbackShift + 2, 16));
                                parameters[1].phase += parameters[1].frequency << 6;
                                operator++;
                            }
                            case 31 -> computeFeedback(destination, parameter.phase,
                                    parameter.frequency, firstGain, secondGain, feedback,
                                    Math.min(feedbackShift + 2, 16), add);
                            default -> computeFeedback(destination, parameter.phase,
                                    parameter.frequency, firstGain, secondGain, feedback,
                                    feedbackShift, add);
                        }
                    } else {
                        computePure(destination, parameter.phase, parameter.frequency,
                                firstGain, secondGain, add);
                    }
                } else {
                    compute(destination, buses[inputBus - 1], parameter.phase,
                            parameter.frequency, firstGain, secondGain, add);
                }
                hasContents[outputBus] = true;
            } else if (!add) {
                hasContents[outputBus] = false;
            }
            parameter.phase += parameter.frequency << 6;
        }
    }

    private static void compute(int[] output, int[] input, int initialPhase,
                                int frequency, int firstGain, int secondGain,
                                boolean add) {
        int deltaGain = (secondGain - firstGain + (BLOCK_SIZE >> 1)) >> 6;
        int gain = firstGain;
        int phase = initialPhase;
        for (int index = 0; index < BLOCK_SIZE; index++) {
            gain += deltaGain;
            int sample = sine(phase + input[index], gain);
            output[index] = sample + (add ? output[index] : 0);
            phase += frequency;
        }
    }

    private static void computePure(int[] output, int initialPhase, int frequency,
                                    int firstGain, int secondGain, boolean add) {
        int deltaGain = (secondGain - firstGain + (BLOCK_SIZE >> 1)) >> 6;
        int gain = firstGain;
        int phase = initialPhase;
        for (int index = 0; index < BLOCK_SIZE; index++) {
            gain += deltaGain;
            int sample = sine(phase, gain);
            output[index] = sample + (add ? output[index] : 0);
            phase += frequency;
        }
    }

    private static void computeFeedback(int[] output, int initialPhase, int frequency,
                                        int firstGain, int secondGain, int[] feedback,
                                        int feedbackShift, boolean add) {
        int deltaGain = (secondGain - firstGain + (BLOCK_SIZE >> 1)) >> 6;
        int gain = firstGain;
        int phase = initialPhase;
        int previous = feedback[0];
        int current = feedback[1];
        for (int index = 0; index < BLOCK_SIZE; index++) {
            gain += deltaGain;
            int scaledFeedback = (previous + current) >> (feedbackShift + 1);
            previous = current;
            current = sine(phase + scaledFeedback, gain);
            output[index] = current + (add ? output[index] : 0);
            phase += frequency;
        }
        feedback[0] = previous;
        feedback[1] = current;
    }

    private static void computeFeedbackTwo(int[] output, OperatorParameters[] parameters,
                                           int firstGain, int secondGain, int[] feedback,
                                           int feedbackShift) {
        int firstDelta = (secondGain - firstGain + (BLOCK_SIZE >> 1)) >> 6;
        int[] gains = {firstGain, 0};
        int[] phases = {parameters[0].phase, parameters[1].phase};
        int previous = feedback[0];
        int current = feedback[1];
        parameters[1].gainOutput = ENVELOPE_MAX - (parameters[1].levelInput >> 14);
        gains[1] = parameters[1].gainOutput == 0
                ? ENVELOPE_MAX - 1 : parameters[1].gainOutput;
        int secondDelta = parameters[1].gainOutput
                - (parameters[1].gainOutput == 0 ? ENVELOPE_MAX - 1
                : parameters[1].gainOutput);

        for (int index = 0; index < BLOCK_SIZE; index++) {
            int scaledFeedback = (previous + current) >> (feedbackShift + 1);
            gains[0] += firstDelta;
            previous = current;
            current = sine(phases[0] + scaledFeedback, gains[0]);
            phases[0] += parameters[0].frequency;
            gains[1] += secondDelta;
            current = sine(phases[1] + current, gains[1]);
            phases[1] += parameters[1].frequency;
            output[index] = current;
        }
        feedback[0] = previous;
        feedback[1] = current;
    }

    private static void computeFeedbackThree(int[] output, OperatorParameters[] parameters,
                                             int firstGain, int secondGain, int[] feedback,
                                             int feedbackShift) {
        int[] gains = new int[3];
        int[] deltas = new int[3];
        int[] phases = {parameters[0].phase, parameters[1].phase, parameters[2].phase};
        int previous = feedback[0];
        int current = feedback[1];
        parameters[1].gainOutput = ENVELOPE_MAX - (parameters[1].levelInput >> 14);
        parameters[2].gainOutput = ENVELOPE_MAX - (parameters[2].levelInput >> 14);
        gains[0] = firstGain;
        gains[1] = parameters[1].gainOutput == 0
                ? ENVELOPE_MAX - 1 : parameters[1].gainOutput;
        gains[2] = parameters[2].gainOutput == 0
                ? ENVELOPE_MAX - 1 : parameters[2].gainOutput;
        deltas[0] = (secondGain - firstGain + (BLOCK_SIZE >> 1)) >> 6;
        deltas[1] = parameters[1].gainOutput
                - (parameters[1].gainOutput == 0 ? ENVELOPE_MAX - 1
                : parameters[1].gainOutput);
        deltas[2] = parameters[2].gainOutput
                - (parameters[2].gainOutput == 0 ? ENVELOPE_MAX - 1
                : parameters[2].gainOutput);

        for (int index = 0; index < BLOCK_SIZE; index++) {
            int scaledFeedback = (previous + current) >> (feedbackShift + 1);
            gains[0] += deltas[0];
            previous = current;
            current = sine(phases[0] + scaledFeedback, gains[0]);
            phases[0] += parameters[0].frequency;
            gains[1] += deltas[1];
            current = sine(phases[1] + current, gains[1]);
            phases[1] += parameters[1].frequency;
            gains[2] += deltas[2];
            current = sine(phases[2] + current, gains[2]);
            phases[2] += parameters[2].frequency;
            output[index] = current;
        }
        feedback[0] = previous;
        feedback[1] = current;
    }

    private static int sine(int phase, int envelope) {
        int phi = (phase >> 12) & 0xffff;
        int tableIndex = phi & (LOG_TABLE_SIZE - 1);
        int logarithm = switch (phi & (LOG_TABLE_SIZE * 3)) {
            case 0 -> LOG_SINE[tableIndex];
            case LOG_TABLE_SIZE -> LOG_SINE[tableIndex ^ (LOG_TABLE_SIZE - 1)];
            case LOG_TABLE_SIZE * 2 -> LOG_SINE[tableIndex] | NEGATIVE_BIT;
            default -> LOG_SINE[tableIndex ^ (LOG_TABLE_SIZE - 1)] | NEGATIVE_BIT;
        };
        int exponent = (logarithm + (envelope & 0xffff)) & 0xffff;
        boolean negative = (exponent & NEGATIVE_BIT) != 0;
        exponent &= ~NEGATIVE_BIT;
        int result = 4_096 + EXP_SINE[(exponent & 0x3ff) ^ 0x3ff];
        result >>= exponent >> 10;
        return negative ? (-result - 1) << 13 : result << 13;
    }

    private static int[] createLogSineTable() {
        int[] table = new int[LOG_TABLE_SIZE];
        float resolution = LOG_TABLE_SIZE;
        for (int index = 0; index < table.length; index++) {
            float sine = (float) StrictMath.sin(((0.5 + index) / resolution)
                    * StrictMath.PI / 2.0);
            table[index] = (int) StrictMath.round(-1_024.0
                    * (StrictMath.log(sine) / StrictMath.log(2.0))) & 0xffff;
        }
        return table;
    }

    private static int[] createExpSineTable() {
        int[] table = new int[EXP_TABLE_SIZE];
        float resolution = EXP_TABLE_SIZE;
        for (int index = 0; index < table.length; index++) {
            float value = (float) ((StrictMath.pow(2.0, (float) index / resolution) - 1.0)
                    * 4_096.0);
            table[index] = (int) StrictMath.round(value) & 0xffff;
        }
        return table;
    }

    static final class OperatorParameters {
        int levelInput;
        int gainOutput;
        int frequency;
        int phase;

        OperatorParameters(int levelInput, int gainOutput, int frequency, int phase) {
            this.levelInput = levelInput;
            this.gainOutput = gainOutput;
            this.frequency = frequency;
            this.phase = phase;
        }
    }
}
