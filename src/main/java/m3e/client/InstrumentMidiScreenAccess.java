package m3e.client;

import org.jspecify.annotations.Nullable;
import xerca.xercamusic.client.NoteSound;

/**
 * Stable boundary between the add-on's imported MIDI player and Music Maker's
 * instrument screen. The GuiInstrument mixin implements this by delegating to
 * Music Maker's real playSound/stopSound methods, preserving its visuals,
 * packets, chord input, and local monitor.
 */
public interface InstrumentMidiScreenAccess {
    /**
     * Starts a song-player-owned note unless that physical key is already held
     * by live keyboard/MIDI input. Returns true only when this call acquired
     * the key, so playback never releases a musician's manually held note.
     */
    boolean playMidiNote(int midiNote, int velocity);

    void stopMidiNote(int midiNote);

    default void stopMidiNoteImmediately(int midiNote) { stopMidiNote(midiNote); }

    /** Applies a native Music Maker sheet glissando to the just-started note. */
    void applyMidiGlissando(int midiNote, byte[] semitoneWaypoints,
                            @Nullable byte[] waypointPositions, int lengthTicks);

    default void applyMidiGlissando(long eventId, int midiNote, byte[] semitoneWaypoints,
                                    @Nullable byte[] waypointPositions,
                                    long durationMicros) {
        applyMidiGlissando(midiNote, semitoneWaypoints, waypointPositions,
                Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                        Math.round(durationMicros / 50_000.0D))));
    }

    /** Applies a native sheet crescendo/decrescendo value to the active note. */
    void setMidiDynamicVolume(int midiNote, float volume);

    default void setMidiDynamicVolume(long eventId, int midiNote, float volume) {
        setMidiDynamicVolume(midiNote, volume);
    }

    /** Applies imported channel expression only to a songbook-owned note. */
    void setMidiNoteExpression(int midiNote, float bendSemitones,
                               float vibratoDepth);

    default void setMidiNoteExpression(long eventId, int midiNote, float bendSemitones,
                                       float vibratoDepth) {
        setMidiNoteExpression(midiNote, bendSemitones, vibratoDepth);
    }

    /** Moves an already-playing local note to a replacement GUI without retriggering audio. */
    @Nullable NoteSound takeMidiNote(int midiNote);

    /** Installs an already-playing local note in this GUI's pressed-note state. */
    void adoptMidiNote(int midiNote, @Nullable NoteSound sound);

    int instrumentId();

    /** Regular held-item editor state; absent for previews and Music Box UI. */
    default @Nullable HeldInstrumentEditSession heldInstrumentEditSession() { return null; }

    /** True while the exact handheld stack or instrument block remains available. */
    boolean playbackInstrumentPresent();

    /**
     * Opens a fresh equivalent Music Maker instrument screen. GuiInstrument
     * closes its MIDI devices in removed(), so reusing a removed instance is
     * not safe.
     */
    void reopenInstrumentScreen();
}
