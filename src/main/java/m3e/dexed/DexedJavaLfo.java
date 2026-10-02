package m3e.dexed;

final class DexedJavaLfo {
    private static final int BLOCK_SIZE = 64;
    private static final double[] SOURCE = {
            0.062541,0.125031,0.312393,0.437120,0.624610,0.750694,0.936330,
            1.125302,1.249609,1.436782,1.560915,1.752081,1.875117,2.062494,
            2.247191,2.374451,2.560492,2.686728,2.873976,2.998950,3.188013,
            3.369840,3.500175,3.682224,3.812065,4.000800,4.186202,4.310716,
            4.501260,4.623209,4.814636,4.930480,5.121901,5.315191,5.434783,
            5.617346,5.750431,5.946717,6.062811,6.248438,6.431695,6.564264,
            6.749460,6.868132,7.052186,7.250580,7.375719,7.556294,7.687577,
            7.877738,7.993605,8.181967,8.372405,8.504848,8.685079,8.810573,
            8.986341,9.122423,9.300595,9.500285,9.607994,9.798158,9.950249,
            10.117361,11.251125,11.384335,12.562814,13.676149,13.904338,
            15.092062,16.366612,16.638935,17.869907,19.193858,19.425019,
            20.833333,21.034918,22.502250,24.003841,24.260068,25.746653,
            27.173913,27.578599,29.052876,30.693677,31.191516,32.658393,
            34.317090,34.674064,36.416606,38.197097,38.550501,40.387722,
            40.749796,42.625746,44.326241,44.883303,46.772685,48.590865,
            49.261084
    };

    private final int unit;
    private final long ratio;
    private int phase;
    private int delta;
    private int waveform;
    private int randomState;
    private boolean sync;
    private int delayState;
    private int delayIncrement;
    private int secondaryDelayIncrement;

    DexedJavaLfo(double sampleRate) {
        unit = (int) (BLOCK_SIZE * 25_190_424.0 / sampleRate + 0.5);
        ratio = (long) (4_437_500_000.0 * BLOCK_SIZE / sampleRate);
    }

    void reset(byte[] parameters) {
        int rate = Byte.toUnsignedInt(parameters[0]);
        delta = (int) (long) (SOURCE[rate] * ratio);
        int adjustedDelay = 99 - Byte.toUnsignedInt(parameters[1]);
        if (adjustedDelay == 99) {
            delayIncrement = -1;
            secondaryDelayIncrement = -1;
        } else {
            adjustedDelay = (16 + (adjustedDelay & 15)) << (1 + (adjustedDelay >> 4));
            delayIncrement = unit * adjustedDelay;
            adjustedDelay &= 0xff80;
            adjustedDelay = Math.max(0x80, adjustedDelay);
            secondaryDelayIncrement = unit * adjustedDelay;
        }
        waveform = Byte.toUnsignedInt(parameters[5]);
        sync = parameters[4] != 0;
    }

    int sample() {
        phase += delta;
        return switch (waveform) {
            case 0 -> {
                int value = phase >>> 7;
                value ^= -(phase >>> 31);
                yield value & ((1 << 24) - 1);
            }
            case 1 -> (~phase ^ (1 << 31)) >>> 8;
            case 2 -> (phase ^ (1 << 31)) >>> 8;
            case 3 -> ((~phase) >>> 7) & (1 << 24);
            case 4 -> (1 << 23) + (DexedJavaMath.sinLookup(phase >>> 8) >> 1);
            case 5 -> {
                if (Integer.compareUnsigned(phase, delta) < 0) {
                    randomState = (randomState * 179 + 17) & 0xff;
                }
                int value = randomState ^ 0x80;
                yield (value + 1) << 16;
            }
            default -> 1 << 23;
        };
    }

    int delay() {
        int increment = delayState >= 0 ? delayIncrement : secondaryDelayIncrement;
        long value = Integer.toUnsignedLong(delayState) + Integer.toUnsignedLong(increment);
        if (value > 0xffff_ffffL) return 1 << 24;
        delayState = (int) value;
        if (delayState >= 0) return 0;
        return (int) (value >> 7) & ((1 << 24) - 1);
    }

    void keyDown() {
        if (sync) phase = Integer.MAX_VALUE;
        delayState = 0;
    }
}
