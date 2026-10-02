package m3e.client;

/** Active Music Maker instrument surface controlled by one physical MIDI input. */
public interface InstrumentExpressionTarget {
    int instrumentId();
    void applyMidiExpression(float bendSemitones, float vibratoDepth);
}
