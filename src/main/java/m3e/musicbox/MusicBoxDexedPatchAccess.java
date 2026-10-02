package m3e.musicbox;

import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;

/** Persistent DX7 voice carried by a Redstone Synthesizer installed in a music box. */
public interface MusicBoxDexedPatchAccess {
    DexedPatch m3e$musicBoxDexedPatch();

    void m3e$setMusicBoxDexedPatch(DexedPatch patch);

    DexedPerformance m3e$musicBoxDexedPerformance();

    void m3e$setMusicBoxDexedPerformance(DexedPerformance performance);
}
