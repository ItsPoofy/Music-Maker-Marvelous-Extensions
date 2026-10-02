package m3e.dexed;

final class DexedJavaPortamento {
    private static final int BLOCK_SIZE = 64;
    private final int[] rates = new int[128];
    private final int[] glissandoRates = new int[128];

    DexedJavaPortamento(double sampleRate) {
        int step = (1 << 24) / 12;
        for (int index = 0; index < rates.length; index++) {
            double semitonesPerSecond = 2_100.0 * StrictMath.pow(2.0, -0.062 * index);
            rates[index] = (int) (0.5f
                    + step * (semitonesPerSecond / sampleRate * BLOCK_SIZE));
            semitonesPerSecond = 1_300.0 * StrictMath.pow(2.0, -0.062 * index);
            glissandoRates[index] = (int) (0.5f
                    + step * (semitonesPerSecond / sampleRate * BLOCK_SIZE));
        }
    }

    int rate(int controller, boolean glissando) {
        return (glissando ? glissandoRates : rates)[controller];
    }
}
