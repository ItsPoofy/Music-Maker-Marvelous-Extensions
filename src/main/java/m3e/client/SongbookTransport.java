package m3e.client;

import m3e.client.SongbookClientState.SongEntry;

/** Playback lifetime and performance controls independent of Songbook catalogs. */
final class SongbookTransport {
    final InstrumentMidiPlayback playback = new InstrumentMidiPlayback();
    final SongbookPlaybackQueue queue = new SongbookPlaybackQueue();
    boolean syncTempoWithEnsemble;
    int playbackSemitoneShift;
    double playbackSpeed = 1.0D;
    boolean startPlaybackAfterLoad;
    long pendingSeekMicros = -1L;
    SongbookTransport(Runnable cycleCompleted) {
        playback.setCycleCompleteListener(cycleCompleted);
    }
    int effectiveTempoBpm(SongEntry entry) {
        return Math.clamp((int) Math.round(entry.tempoBpm() * playbackSpeed), 1, 3_000);
    }

    double playbackRate(SongEntry entry) {
        return effectiveTempoBpm(entry) / (double) Math.max(1, entry.tempoBpm());
    }

}
