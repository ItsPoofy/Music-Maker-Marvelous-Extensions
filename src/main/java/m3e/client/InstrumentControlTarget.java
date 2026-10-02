package m3e.client;

import org.jspecify.annotations.Nullable;
import m3e.sound.InstrumentSoundProfile;

/** The instrument instance edited by the shared Instrument tab. */
interface InstrumentControlTarget {
    int instrumentId();

    InstrumentSoundProfile profile();

    void setProfile(InstrumentSoundProfile profile);

    String variation();

    void setVariation(String variation);

    @Nullable DexedEditorTarget dexed();
}
