package m3e.client;

import m3e.sound.InstrumentSoundProfile;

/** Applied to Music Maker's NoteSound by the M3E mixin. */
public interface InstrumentEnvelopeAccess {
    void m3e$configureEnvelope(InstrumentSoundProfile profile);
    void m3e$setMidiExpression(float bendSemitones, float vibratoDepth);
    /** Independent arranger retune used by Yamaha RTR Pitch Shift. */
    void m3e$setArrangerPitchOffset(float semitones);
    /** Runtime ensemble fader, independent from the saved instrument profile. */
    void m3e$setDynamicGain(float gain);
    /** Updates a running voice when its Music Box volume slider moves. */
    void m3e$setProfileVolume(float volume);

    void m3e$stopImmediately();
}
