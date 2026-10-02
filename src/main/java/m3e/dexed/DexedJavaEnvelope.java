package m3e.dexed;

final class DexedJavaEnvelope {
    private static final int BLOCK_SIZE = 64;
    private static final int[] LEVEL_TABLE = {
            0, 5, 9, 13, 17, 20, 23, 25, 27, 29,
            31, 33, 35, 37, 39, 41, 42, 43, 45, 46
    };
    private static final int[] STATIC_COUNTS = {
            1_764_000, 1_764_000, 1_411_200, 1_411_200, 1_190_700, 1_014_300,
            992_250, 882_000, 705_600, 705_600, 584_325, 507_150, 502_740,
            441_000, 418_950, 352_800, 308_700, 286_650, 253_575, 220_500,
            220_500, 176_400, 145_530, 145_530, 125_685, 110_250, 110_250,
            88_200, 88_200, 74_970, 61_740, 61_740, 55_125, 48_510, 44_100,
            37_485, 31_311, 30_870, 27_562, 27_562, 22_050, 18_522, 17_640,
            15_435, 14_112, 13_230, 11_025, 9_261, 9_261, 7_717, 6_615, 6_615,
            5_512, 5_512, 4_410, 3_969, 3_969, 3_439, 2_866, 2_690, 2_249,
            1_984, 1_896, 1_808, 1_411, 1_367, 1_234, 1_146, 926, 837, 837,
            705, 573, 573, 529, 441, 441
    };

    private final int sampleRateMultiplier;
    private final int[] rates = new int[4];
    private final int[] levels = new int[4];
    private boolean initialized;
    private int outputLevel;
    private int rateScaling;
    private int level;
    private int targetLevel;
    private boolean rising;
    private int stage;
    private int increment;
    private int staticCount;
    private boolean down;

    DexedJavaEnvelope(double sampleRate) {
        sampleRateMultiplier = (int) ((44_100.0 / sampleRate) * (1 << 24));
    }

    void initialize(int[] newRates, int[] newLevels, int newOutputLevel,
                    int newRateScaling) {
        initialized = true;
        System.arraycopy(newRates, 0, rates, 0, rates.length);
        System.arraycopy(newLevels, 0, levels, 0, levels.length);
        outputLevel = newOutputLevel;
        rateScaling = newRateScaling;
        level = 0;
        down = true;
        advance(0);
    }

    void update(int[] newRates, int[] newLevels, int newOutputLevel,
                int newRateScaling) {
        System.arraycopy(newRates, 0, rates, 0, rates.length);
        System.arraycopy(newLevels, 0, levels, 0, levels.length);
        outputLevel = newOutputLevel;
        rateScaling = newRateScaling;
        if (initialized && stage < 4) advance(stage);
    }

    int sample() {
        if (staticCount != 0) {
            staticCount -= BLOCK_SIZE;
            if (staticCount <= 0) {
                staticCount = 0;
                advance(stage + 1);
            }
        }

        if (stage < 3 || (stage < 4 && !down)) {
            if (staticCount != 0) {
                // The accurate envelope intentionally holds this stage.
            } else if (rising) {
                int jumpTarget = 1_716;
                if (level < (jumpTarget << 16)) level = jumpTarget << 16;
                level += (((17 << 24) - level) >> 24) * increment;
                if (level >= targetLevel) {
                    level = targetLevel;
                    advance(stage + 1);
                }
            } else {
                level -= increment;
                if (level <= targetLevel) {
                    level = targetLevel;
                    advance(stage + 1);
                }
            }
        }
        return level;
    }

    void keyDown(boolean pressed) {
        if (down == pressed) return;
        down = pressed;
        advance(pressed ? 0 : 3);
    }

    int stage() { return stage; }

    boolean isActive() { return initialized && (stage < 4 || levels[3] > 0); }

    static int scaleOutputLevel(int outputLevel) {
        return outputLevel >= 20 ? 28 + outputLevel : LEVEL_TABLE[outputLevel];
    }

    private void advance(int newStage) {
        stage = newStage;
        if (stage >= 4) return;

        int newLevel = levels[stage];
        int actualLevel = scaleOutputLevel(newLevel) >> 1;
        actualLevel = (actualLevel << 6) + outputLevel - 4_256;
        actualLevel = Math.max(actualLevel, 16);
        targetLevel = actualLevel << 16;
        rising = targetLevel > level;

        int quantizedRate = (rates[stage] * 41) >> 6;
        quantizedRate += rateScaling;
        quantizedRate = Math.min(quantizedRate, 63);

        if (targetLevel == level || (stage == 0 && newLevel == 0)) {
            int staticRate = Math.min(rates[stage] + rateScaling, 99);
            staticCount = staticRate < STATIC_COUNTS.length
                    ? STATIC_COUNTS[staticRate] : 20 * (99 - staticRate);
            if (staticRate < STATIC_COUNTS.length && stage == 0 && newLevel == 0) {
                staticCount /= 20;
            }
            staticCount = (int) (((long) staticCount * sampleRateMultiplier) >> 24);
        } else {
            staticCount = 0;
        }

        int shift = 8 + (quantizedRate >> 2);
        increment = (4 + (quantizedRate & 3)) << shift;
        increment = (int) (((long) increment * sampleRateMultiplier) >> 24);
    }
}
