package m3e.dexed;

/** Active controller subset configured by M3E's Mark I host. */
final class DexedJavaControllers {
    static final int PITCH = 128;
    static final int PITCH_RANGE_UP = 129;
    static final int PITCH_STEP = 130;
    static final int PITCH_RANGE_DOWN = 131;

    final int[] values = new int[132];
    final boolean[] operatorEnabled = {true, true, true, true, true, true};
    final Modulation wheel = new Modulation();
    final Modulation foot = new Modulation();
    final Modulation breath = new Modulation();
    final Modulation aftertouch = new Modulation();
    int amplitudeModulation;
    int pitchModulation;
    int envelopeModulation;
    int aftertouchController;
    int breathController;
    int footController;
    int modulationWheelController;
    boolean portamentoEnabled;
    int portamentoController;
    boolean portamentoGlissando;
    int masterTune;
    DexedJavaControllers() {
        values[PITCH] = 0x2000;
        values[PITCH_RANGE_UP] = 2;
        values[PITCH_RANGE_DOWN] = 2;
        values[PITCH_STEP] = 0;
        wheel.range = 99;
        wheel.pitch = true;
        refresh();
    }

    void refresh() {
        amplitudeModulation = 0;
        pitchModulation = 0;
        envelopeModulation = 0;
        apply(modulationWheelController, wheel);
        apply(breathController, breath);
        apply(footController, foot);
        apply(aftertouchController, aftertouch);
        if (!(wheel.envelope || foot.envelope || breath.envelope || aftertouch.envelope)) {
            envelopeModulation = 127;
        }
    }

    private void apply(int controller, Modulation modulation) {
        float range = (float) (0.01 * modulation.range);
        int total = (int) (controller * range);
        if (modulation.amplitude) amplitudeModulation = Math.max(amplitudeModulation, total);
        if (modulation.pitch) pitchModulation = Math.max(pitchModulation, total);
        if (modulation.envelope) envelopeModulation = Math.max(envelopeModulation, total);
    }

    static final class Modulation {
        int range;
        boolean pitch;
        boolean amplitude;
        boolean envelope;
    }
}
