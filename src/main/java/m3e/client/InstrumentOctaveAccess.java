package m3e.client;

/** Exposes Music Maker's highlighted octave to the add-on header. */
public interface InstrumentOctaveAccess {
    int m3e$currentKeyboardOctave();
    String m3e$selectedKeyboardRange();
    boolean m3e$canDecreaseOctave();
    boolean m3e$canIncreaseOctave();
    void m3e$decreaseKeyboardOctave();
    void m3e$increaseKeyboardOctave();
    void m3e$pianoLayoutChanged();
}
