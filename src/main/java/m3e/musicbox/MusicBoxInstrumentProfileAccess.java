package m3e.musicbox;

import m3e.sound.InstrumentSoundProfile;

/** Server-synchronised sound profile stored on one loaded Music Maker music box. */
public interface MusicBoxInstrumentProfileAccess {
    InstrumentSoundProfile m3e$musicBoxProfile();

    int m3e$musicBoxProfileInstrumentId();

    void m3e$setMusicBoxProfile(int instrumentId, InstrumentSoundProfile profile);

    String m3e$musicBoxVariation();

    void m3e$setMusicBoxVariation(String variation);

    String m3e$musicBoxInstrumentInstanceId();
}
