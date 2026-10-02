package m3e.dexed;

final class DexedJavaMath {
    private static final int EXP2_LG_N_SAMPLES = 10;
    private static final int EXP2_N_SAMPLES = 1 << EXP2_LG_N_SAMPLES;
    private static final int[] EXP2_TABLE = createExp2Table();

    private static final int SIN_LG_N_SAMPLES = 10;
    private static final int SIN_N_SAMPLES = 1 << SIN_LG_N_SAMPLES;
    private static final int[] SIN_TABLE = createSinTable();

    private DexedJavaMath() { }

    /** Q24 input and Q24 output, matching {@code Exp2::lookup}. */
    static int exp2Lookup(int x) {
        int shift = 24 - EXP2_LG_N_SAMPLES;
        int lowBits = x & ((1 << shift) - 1);
        int xInteger = (x >> (shift - 1)) & ((EXP2_N_SAMPLES - 1) << 1);
        int delta = EXP2_TABLE[xInteger];
        int start = EXP2_TABLE[xInteger + 1];
        int interpolated = start + (int) (((long) delta * lowBits) >> shift);
        return interpolated >> (6 - (x >> 24));
    }

    /** Q24 phase input and Q24 amplitude output, matching {@code Sin::lookup}. */
    static int sinLookup(int phase) {
        int shift = 24 - SIN_LG_N_SAMPLES;
        int lowBits = phase & ((1 << shift) - 1);
        int phaseInteger = (phase >> (shift - 1))
                & ((SIN_N_SAMPLES - 1) << 1);
        int delta = SIN_TABLE[phaseInteger];
        int start = SIN_TABLE[phaseInteger + 1];
        return start + (int) (((long) delta * lowBits) >> shift);
    }

    private static int[] createExp2Table() {
        int[] table = new int[EXP2_N_SAMPLES << 1];
        double increment = StrictMath.pow(2.0, 1.0 / EXP2_N_SAMPLES);
        double value = 1 << 30;
        for (int index = 0; index < EXP2_N_SAMPLES; index++) {
            table[(index << 1) + 1] = (int) StrictMath.floor(value + 0.5);
            value *= increment;
        }
        for (int index = 0; index < EXP2_N_SAMPLES - 1; index++) {
            table[index << 1] = table[(index << 1) + 3] - table[(index << 1) + 1];
        }
        table[(EXP2_N_SAMPLES << 1) - 2] = (int) ((1L << 31)
                - table[(EXP2_N_SAMPLES << 1) - 1]);
        return table;
    }

    private static int[] createSinTable() {
        int[] table = new int[SIN_N_SAMPLES << 1];
        double phaseStep = 2.0 * StrictMath.PI / SIN_N_SAMPLES;
        int cosine = (int) StrictMath.floor(
                StrictMath.cos(phaseStep) * (1 << 30) + 0.5);
        int sine = (int) StrictMath.floor(
                StrictMath.sin(phaseStep) * (1 << 30) + 0.5);
        int real = 1 << 30;
        int imaginary = 0;
        long rounding = 1 << 29;
        for (int index = 0; index < SIN_N_SAMPLES / 2; index++) {
            table[(index << 1) + 1] = (imaginary + 32) >> 6;
            table[((index + SIN_N_SAMPLES / 2) << 1) + 1] = -((imaginary + 32) >> 6);
            int nextImaginary = (int) (((long) real * sine
                    + (long) imaginary * cosine + rounding) >> 30);
            real = (int) (((long) real * cosine
                    - (long) imaginary * sine + rounding) >> 30);
            imaginary = nextImaginary;
        }
        for (int index = 0; index < SIN_N_SAMPLES - 1; index++) {
            table[index << 1] = table[(index << 1) + 3] - table[(index << 1) + 1];
        }
        table[(SIN_N_SAMPLES << 1) - 2] = -table[(SIN_N_SAMPLES << 1) - 1];
        return table;
    }

    /** Per-engine log-frequency lookup, initialized for the exact render sample rate. */
    static final class FrequencyLookup {
        private static final int LG_N_SAMPLES = 10;
        private static final int N_SAMPLES = 1 << LG_N_SAMPLES;
        private static final int SAMPLE_SHIFT = 24 - LG_N_SAMPLES;
        private static final int MAX_LOG_FREQUENCY_INTEGER = 20;
        private final int[] table = new int[N_SAMPLES + 1];

        FrequencyLookup(double sampleRate) {
            double value = (1L << (24 + MAX_LOG_FREQUENCY_INTEGER)) / sampleRate;
            double increment = StrictMath.pow(2.0, 1.0 / N_SAMPLES);
            for (int index = 0; index < table.length; index++) {
                table[index] = (int) StrictMath.floor(value + 0.5);
                value *= increment;
            }
        }

        int lookup(int logFrequency) {
            int index = (logFrequency & 0x00ff_ffff) >> SAMPLE_SHIFT;
            int start = table[index];
            int end = table[index + 1];
            int lowBits = logFrequency & ((1 << SAMPLE_SHIFT) - 1);
            int interpolated = start
                    + (int) (((long) (end - start) * lowBits) >> SAMPLE_SHIFT);
            int highBits = logFrequency >> 24;
            return interpolated >> (MAX_LOG_FREQUENCY_INTEGER - highBits);
        }
    }
}
