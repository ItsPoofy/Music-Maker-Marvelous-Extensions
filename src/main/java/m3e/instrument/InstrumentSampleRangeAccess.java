package m3e.instrument;

/** Exact MIDI-note range layered over Music Maker's whole-octave instrument bounds. */
public interface InstrumentSampleRangeAccess {
    void m3e$setSampleRange(int minNote, int maxNote);

    int m3e$getSampleMinNote();

    int m3e$getSampleMaxNote();
}
