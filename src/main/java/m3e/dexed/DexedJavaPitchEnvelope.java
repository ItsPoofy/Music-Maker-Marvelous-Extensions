package m3e.dexed;

final class DexedJavaPitchEnvelope {
    private static final int BLOCK_SIZE = 64;
    private static final int[] RATE_TABLE = {
            1,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,10,11,11,12,12,13,13,
            14,14,15,16,16,17,18,18,19,20,21,22,23,24,25,26,27,28,30,31,
            33,34,36,37,38,39,41,42,44,46,47,49,51,53,54,56,58,60,62,64,
            66,68,70,72,74,76,79,82,85,88,91,94,98,102,106,110,115,120,
            125,130,135,141,147,153,159,165,171,178,185,193,202,211,232,243,
            254,255
    };
    private static final int[] LEVEL_TABLE = {
            -128,-116,-104,-95,-85,-76,-68,-61,-56,-52,-49,-46,-43,-41,-39,
            -37,-35,-33,-32,-31,-30,-29,-28,-27,-26,-25,-24,-23,-22,-21,-20,
            -19,-18,-17,-16,-15,-14,-13,-12,-11,-10,-9,-8,-7,-6,-5,-4,-3,-2,
            -1,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,
            23,24,25,26,27,28,29,30,31,32,33,34,35,38,40,43,46,49,53,58,65,
            73,82,92,103,115,127
    };

    private final int unit;
    private final int[] rates = new int[4];
    private final int[] levels = new int[4];
    private int level;
    private int targetLevel;
    private boolean rising;
    private int stage;
    private int increment;
    private boolean down;

    DexedJavaPitchEnvelope(double sampleRate) {
        unit = (int) (BLOCK_SIZE * (1 << 24) / (21.3 * sampleRate) + 0.5);
    }

    void set(int[] newRates, int[] newLevels) {
        System.arraycopy(newRates, 0, rates, 0, rates.length);
        System.arraycopy(newLevels, 0, levels, 0, levels.length);
        level = LEVEL_TABLE[levels[3]] << 19;
        down = true;
        advance(0);
    }

    /** Updates a live envelope without restarting its stage or snapping its pitch. */
    void update(int[] newRates, int[] newLevels) {
        System.arraycopy(newRates, 0, rates, 0, rates.length);
        System.arraycopy(newLevels, 0, levels, 0, levels.length);
        if (stage < 4) advance(stage);
    }

    int sample() {
        if (stage < 3 || (stage < 4 && !down)) {
            if (rising) {
                level += increment;
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

    private void advance(int newStage) {
        stage = newStage;
        if (stage >= 4) return;
        targetLevel = LEVEL_TABLE[levels[stage]] << 19;
        rising = targetLevel > level;
        increment = RATE_TABLE[rates[stage]] * unit;
    }
}
