package m3e.client;

import m3e.sheet.MusicScore;

/** Screen-local presentation state for transport-driven piano visualization. */
public final class PianoWaterfallState {
    private static long observedSessionId = -1L;
    private static long dismissedSessionId = -1L;
    private static boolean visible;

    private PianoWaterfallState() { }

    static boolean sync() {
        InstrumentMidiPlayback.VisualizationSnapshot snapshot =
                SongbookClientState.visualizationSnapshot();
        if (!shouldActivate(InstrumentOptions.pianoWaterfallEnabled(), snapshot)) {
            visible = false;
            return false;
        }
        if (snapshot.sessionId() != observedSessionId) {
            observedSessionId = snapshot.sessionId();
            dismissedSessionId = -1L;
        }
        visible = snapshot.sessionId() != dismissedSessionId;
        return visible;
    }

    static boolean shouldActivate(boolean enabled,
                                  InstrumentMidiPlayback.VisualizationSnapshot snapshot) {
        return enabled && snapshot != null;
    }

    static void dismiss() {
        InstrumentMidiPlayback.VisualizationSnapshot snapshot =
                SongbookClientState.visualizationSnapshot();
        if (snapshot != null) dismissedSessionId = snapshot.sessionId();
        visible = false;
    }

    static void screenClosed() {
        visible = false;
    }

    public static Frame frame() {
        if (!visible) return null;
        InstrumentMidiPlayback.VisualizationSnapshot snapshot =
                SongbookClientState.visualizationSnapshot();
        if (snapshot == null || snapshot.sessionId() == dismissedSessionId) return null;
        return new Frame(snapshot.song(), snapshot.playheadMicros(),
                snapshot.durationMicros(), snapshot.bpm(), snapshot.pitchShift(),
                snapshot.playing(), snapshot.sessionId());
    }

    public record Frame(MusicScore song, long playheadMicros,
                        long durationMicros, int bpm, int pitchShift,
                        boolean playing, long sessionId) { }
}
