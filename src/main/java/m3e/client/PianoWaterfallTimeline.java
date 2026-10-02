package m3e.client;

import xerca.xercamusic.common.item.IItemInstrument;
import m3e.sheet.MusicScore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Cached playback-time notes and a bounded moving visibility window. */
final class PianoWaterfallTimeline {
    private final List<RenderNote> notes;
    private final ArrayList<RenderNote> visible = new ArrayList<>();
    private int nextIndex;
    private long previousPlayhead = Long.MIN_VALUE;
    private long previousWindowEnd = Long.MIN_VALUE;

    PianoWaterfallTimeline(MusicScore song, int bpm, int pitchShift) {
        ArrayList<RenderNote> converted = new ArrayList<>(song.notes().size());
        for (MusicScore.MidiNote note : song.notes()) {
            int pitch = note.pitch() + pitchShift;
            if (pitch < 21 || pitch > 117) continue;
            long start = InstrumentMidiPlayback.playbackMicrosAtTick(
                    song, note.startTick(), bpm);
            long release = InstrumentMidiPlayback.playbackMicrosAtTick(
                    song, note.keyReleaseTick(), bpm);
            converted.add(new RenderNote(IItemInstrument.noteToId(pitch),
                    note.velocity(), start, Math.max(start + 1L, release)));
        }
        converted.sort(Comparator.comparingLong(RenderNote::startMicros)
                .thenComparingInt(RenderNote::noteId));
        notes = List.copyOf(converted);
    }

    List<RenderNote> visible(long playheadMicros, long windowMicros) {
        long playhead = Math.max(0L, playheadMicros);
        long windowEnd = Math.max(playhead, Math.addExact(playhead, windowMicros));
        boolean discontinuity = previousPlayhead == Long.MIN_VALUE
                || playhead < previousPlayhead
                || playhead - previousPlayhead > windowMicros
                || windowEnd < previousWindowEnd;
        if (discontinuity) rebuild(playhead, windowEnd);
        else advance(playhead, windowEnd);
        previousPlayhead = playhead;
        previousWindowEnd = windowEnd;
        return List.copyOf(visible);
    }

    private void rebuild(long playhead, long windowEnd) {
        visible.clear();
        nextIndex = upperBoundStart(windowEnd);
        for (int index = 0; index < nextIndex; index++) {
            RenderNote note = notes.get(index);
            if (note.releaseMicros() >= playhead) visible.add(note);
        }
    }

    private void advance(long playhead, long windowEnd) {
        visible.removeIf(note -> note.releaseMicros() < playhead);
        while (nextIndex < notes.size()
                && notes.get(nextIndex).startMicros() <= windowEnd) {
            RenderNote note = notes.get(nextIndex++);
            if (note.releaseMicros() >= playhead) visible.add(note);
        }
    }

    private int upperBoundStart(long micros) {
        int low = 0;
        int high = notes.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (notes.get(middle).startMicros() <= micros) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    record RenderNote(int noteId, int velocity, long startMicros, long releaseMicros) { }
}
