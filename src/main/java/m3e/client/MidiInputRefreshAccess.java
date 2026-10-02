package m3e.client;

/** Hot-plug lifecycle added to Music Maker's one-shot MIDI device scan. */
public interface MidiInputRefreshAccess {
    void m3e$refreshMidiInputs(boolean force);
}
