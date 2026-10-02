package m3e.client;

import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;
import m3e.sound.InstrumentSoundProfile;

/** The exact synth instance edited by the shared Dexed instrument panel. */
interface DexedEditorTarget {
    long identity();

    DexedPatch patch();

    DexedPerformance performance();

    InstrumentSoundProfile profile();

    void apply(DexedPatch patch, DexedPerformance performance, boolean force);

    void setProfile(InstrumentSoundProfile profile);
}
