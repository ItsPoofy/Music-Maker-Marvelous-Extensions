package m3e.client;

import m3e.sheet.MusicScore;
import m3e.sheet.ScorePlaybackEvents;
import m3e.debug.MusicDebugTelemetry;
import m3e.instrument.M3EInstruments;
import net.minecraft.client.Minecraft;
import xerca.xercamusic.client.NoteSound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/** Exact-tempo, pausable playback for one imported instrument MIDI channel. */
final class InstrumentMidiPlayback {
    private static final long DRUM_RELEASE_TAIL_MICROS = 900_000L;

    private final Object lock = new Object();
    private final boolean localPreview;
    /** Event ownership rather than pitch ownership: sustain can overlap repeated pitches. */
    private final Map<Long, ActiveEvent> activeEvents = new HashMap<>();
    private InstrumentMidiScreenAccess screen;
    private InstrumentMidiScreenAccess boundScreen;
    private MusicScore song;
    private State state = State.STOPPED;
    private long generation;
    private long visualizationSessionCounter;
    private long visualizationSessionId;
    private long playheadMicros;
    private long startNanos;
    private Future<?> worker;
    private List<Action> scheduledActions = List.of();
    private long nextLiveEventId = 1L << 40;
    private boolean looping;
    /** True only while Minecraft's single-player pause menu owns this pause. */
    private boolean resumeAfterSingleplayerPause;
    private int activeTempoBpm = 120;
    private int activePitchShift;
    private Runnable cycleCompleteListener = () -> { };

    InstrumentMidiPlayback() { this(false); }

    InstrumentMidiPlayback(boolean localPreview) {
        this.localPreview = localPreview;
    }

    void setCycleCompleteListener(Runnable listener) {
        synchronized (lock) {
            cycleCompleteListener = listener == null ? () -> { } : listener;
        }
    }

    void attach(InstrumentMidiScreenAccess next) {
        if (next == null) return;
        synchronized (lock) {
            MusicDebugTelemetry.event("songbook_transport", "screen_attached",
                    "screen", next.getClass().getName(), "state", state,
                    "active_notes", activeEvents.size());
            if (screen == null || state == State.STOPPED) {
                screen = next;
            } else if (screen != next) {
                InstrumentMidiScreenAccess previous = screen;
                if (previous.instrumentId() != next.instrumentId()) {
                    releaseActiveNotesLocked(previous);
                    screen = next;
                } else {
                    screen = next;
                    rebindActiveNotesLocked(previous, next);
                }
                boundScreen = next;
            }
        }
    }

    private void rebindActiveNotesLocked(InstrumentMidiScreenAccess previous,
                                         InstrumentMidiScreenAccess replacement) {
        if (isLayeredSynthLocked()) return;
        for (int pitch : activeEvents.values().stream().map(ActiveEvent::pitch).collect(java.util.stream.Collectors.toSet())) {
            NoteSound sound = previous.takeMidiNote(pitch);
            replacement.adoptMidiNote(pitch, sound);
        }
    }

    void detach(InstrumentMidiScreenAccess removed) {
        synchronized (lock) {
            MusicDebugTelemetry.event("songbook_transport", "screen_detached",
                    "screen", removed == null ? "none" : removed.getClass().getName(),
                    "state", state, "active_notes", activeEvents.size());
            // GuiInstrument only closes MIDI devices in removed(); its note
            // output is still usable, so a dropped UI must not cut a song off.
        }
    }

    void playPause(MusicScore selected, int tempoBpm, int pitchShift) {
        synchronized (lock) {
            if (screen == null) return;
            resumeAfterSingleplayerPause = false;
            if (state == State.PLAYING && song == selected) {
                pauseLocked();
                return;
            }
            if (state == State.PAUSED && song == selected) {
                startLocked(selected, playheadMicros,
                        activeTempoBpm, pitchShift);
                return;
            }
            if (state == State.STOPPED && song == selected) {
                visualizationSessionId = ++visualizationSessionCounter;
                startLocked(selected, playheadMicros,
                        tempoBpm, pitchShift);
                return;
            }
            stopLocked();
            visualizationSessionId = ++visualizationSessionCounter;
            startLocked(selected, 0L, tempoBpm, pitchShift);
        }
    }

    /** Seeks to zero and remains stopped, regardless of the prior state. */
    void resetToStart(MusicScore selected, int tempoBpm) {
        synchronized (lock) {
            if (screen == null) return;
            stopLocked();
            song = selected;
            activeTempoBpm = Math.clamp(tempoBpm, 1, 3_000);
            activePitchShift = 0;
            playheadMicros = 0L;
            state = State.STOPPED;
        }
    }

    boolean isActive(MusicScore selected) {
        synchronized (lock) {
            return song == selected && (state == State.PLAYING || state == State.PAUSED);
        }
    }

    boolean hasSelected(MusicScore selected) {
        synchronized (lock) {
            return song == selected;
        }
    }

    MusicScore playingSong() {
        synchronized (lock) {
            return state == State.PLAYING ? song : null;
        }
    }

    /** Changes only this player's MIDI clock and preserves its musical position. */
    void updateTempo(MusicScore selected, int oldTempoBpm, int newTempoBpm) {
        synchronized (lock) {
            if (song != selected) return;
            long oldDuration = durationMicros(selected, oldTempoBpm);
            long oldPosition = currentPlayheadLocked();
            long newDuration = durationMicros(selected, newTempoBpm);
            long newPosition = oldDuration <= 0L ? 0L
                    : Math.clamp(Math.round(oldPosition * (newDuration / (double) oldDuration)), 0L, newDuration);
            activeTempoBpm = Math.clamp(newTempoBpm, 1, 3_000);
            playheadMicros = newPosition;
            MusicDebugTelemetry.event("songbook_transport", "tempo_changed",
                    "from_bpm", oldTempoBpm, "to_bpm", activeTempoBpm,
                    "playhead_us", newPosition, "playing", state == State.PLAYING);
            if (state != State.PLAYING) return;

            generation++;
            if (worker != null) worker.cancel(false);
            startNanos = System.nanoTime() - newPosition * 1_000L;
            long runGeneration = ++generation;
            List<Action> remaining = actions(selected, newPosition,
                    activeTempoBpm, activePitchShift, false);
            scheduledActions = remaining;
            scheduleNext(selected, remaining, newDuration, runGeneration, 0);
        }
    }

    void updateSong(MusicScore updated, int tempoBpm, int pitchShift) {
        synchronized (lock) {
            if (updated == null || song == null || state != State.PLAYING) return;
            long position = currentPlayheadLocked();
            Set<Long> activeIds = Set.copyOf(activeEvents.keySet());
            List<Action> preserved = scheduledActions.stream()
                    .filter(action -> action.micros() >= position
                            && activeIds.contains(action.eventId())
                            && (action.type() == ActionType.OFF
                            || action.type() == ActionType.VOLUME))
                    .toList();

            long eventBase = nextLiveEventId;
            nextLiveEventId += Math.max(1L, updated.notes().size() + 1L);
            ArrayList<Action> replacement = new ArrayList<>(actions(updated, position,
                    tempoBpm, pitchShift, false, eventBase));
            replacement.addAll(preserved);
            replacement.sort(Comparator.comparingLong(Action::micros)
                    .thenComparing(Action::type));

            generation++;
            if (worker != null) worker.cancel(false);
            song = updated;
            playheadMicros = position;
            activeTempoBpm = Math.clamp(tempoBpm, 1, 3_000);
            activePitchShift = Math.clamp(pitchShift, -48, 48);
            startNanos = System.nanoTime() - position * 1_000L;
            long duration = durationMicros(updated, activeTempoBpm);
            for (Action action : preserved) duration = Math.max(duration, action.micros());
            long runGeneration = ++generation;
            List<Action> nextActions = List.copyOf(replacement);
            long nextDuration = duration;
            scheduledActions = nextActions;
            scheduleNext(updated, nextActions, nextDuration, runGeneration, 0);
        }
    }

    void setLooping(boolean value) {
        synchronized (lock) {
            looping = value;
            MusicDebugTelemetry.event("songbook_transport", "loop_changed", "enabled", value);
        }
    }

    void seek(MusicScore selected, long micros, int tempoBpm, int pitchShift) {
        synchronized (lock) {
            long target = Math.clamp(micros, 0L, durationMicros(selected, tempoBpm));
            State prior = song == selected ? state : State.STOPPED;
            boolean resume = prior == State.PLAYING;
            stopLocked();
            song = selected;
            playheadMicros = target;
            if (resume) startLocked(selected, target,
                    tempoBpm, pitchShift);
            else {
                activeTempoBpm = Math.clamp(tempoBpm, 1, 3_000);
                activePitchShift = Math.clamp(pitchShift, -48, 48);
                state = prior == State.PAUSED ? State.PAUSED : State.STOPPED;
            }
            MusicDebugTelemetry.event("songbook_transport", "seek",
                    "playhead_us", target, "resume", resume, "bpm", tempoBpm,
                    "pitch_shift", pitchShift);
        }
    }

    void updatePitchShift(MusicScore selected, int tempoBpm, int pitchShift) {
        synchronized (lock) {
            int bounded = Math.clamp(pitchShift, -48, 48);
            if (song != selected || state == State.STOPPED) {
                activePitchShift = bounded;
                return;
            }
            State prior = state;
            long position = currentPlayheadLocked();
            generation++;
            if (worker != null) worker.cancel(false);
            worker = null;
            stopScreenNotesLocked();
            activePitchShift = bounded;
            playheadMicros = position;
            MusicDebugTelemetry.event("songbook_transport", "pitch_shift",
                    "semitones", bounded, "playhead_us", position, "state", prior);
            if (prior == State.PLAYING) {
                startLocked(selected, position,
                        tempoBpm, bounded);
            } else {
                song = selected;
                activeTempoBpm = Math.clamp(tempoBpm, 1, 3_000);
                state = State.PAUSED;
            }
        }
    }

    long playheadMicros() {
        synchronized (lock) { return currentPlayheadLocked(); }
    }

    TransportState transportState(int selectedTempoBpm) {
        synchronized (lock) {
            long sampledNanos = System.nanoTime();
            long position = song == null ? playheadMicros
                    : state == State.PLAYING
                    ? Math.clamp((sampledNanos - startNanos) / 1_000L, 0L,
                    durationMicros(song, activeTempoBpm))
                    : Math.clamp(playheadMicros, 0L, durationMicros(song, activeTempoBpm));
            return new TransportState(state == State.PLAYING,
                    song == null ? Math.clamp(selectedTempoBpm, 1, 3_000)
                            : tempoBpmAtPlaybackMicros(song, position, activeTempoBpm),
                    position, sampledNanos);
        }
    }

    long durationMicros(MusicScore selected, int tempoBpm) {
        return selected == null ? 0L : scaledMicros(selected.tempoMap().microsAt(selected.lengthTick()), selected, tempoBpm);
    }

    void stop() {
        synchronized (lock) {
            MusicDebugTelemetry.event("songbook_transport", "stop",
                    "playhead_us", currentPlayheadLocked(), "state", state);
            stopLocked();
        }
    }

    Set<Integer> activePitches() {
        synchronized (lock) {
            Set<Integer> pitches = new HashSet<>();
            for (ActiveEvent event : activeEvents.values()) pitches.add(event.pitch());
            return Set.copyOf(pitches);
        }
    }

    VisualizationSnapshot visualizationSnapshot() {
        synchronized (lock) {
            if (song == null || state == State.STOPPED) return null;
            long sampledNanos = System.nanoTime();
            long duration = durationMicros(song, activeTempoBpm);
            long position = state == State.PLAYING
                    ? Math.clamp((sampledNanos - startNanos) / 1_000L, 0L, duration)
                    : Math.clamp(playheadMicros, 0L, duration);
            return new VisualizationSnapshot(song, position, duration,
                    activeTempoBpm, activePitchShift, state == State.PLAYING,
                    visualizationSessionId);
        }
    }

    /**
     * The integrated server pauses single-player world time, so Songbook's
     * wall-clock scheduler must pause too. Multiplayer menus leave
     * {@link Minecraft#isPaused()} false and therefore continue normally.
     */
    void syncSingleplayerPause(boolean paused) {
        synchronized (lock) {
            if (paused) {
                if (state == State.PLAYING && song != null) {
                    pauseLocked();
                    resumeAfterSingleplayerPause = true;
                }
                return;
            }
            if (!resumeAfterSingleplayerPause) return;
            resumeAfterSingleplayerPause = false;
            if (state == State.PAUSED && song != null) {
                startLocked(song, playheadMicros,
                        activeTempoBpm, activePitchShift);
            }
        }
    }

    void validateInstrumentBinding() {
        synchronized (lock) {
            if (state != State.STOPPED && (boundScreen == null || !boundScreen.playbackInstrumentPresent())) {
                MusicDebugTelemetry.event("songbook_transport", "instrument_binding_lost",
                        "state", state, "playhead_us", currentPlayheadLocked());
                stopLocked();
            }
        }
    }

    String stateLabel() {
        synchronized (lock) {
            return switch (state) {
                case STOPPED -> "Play";
                case PLAYING -> "Pause";
                case PAUSED -> "Resume";
            };
        }
    }

    private void pauseLocked() {
        if (state != State.PLAYING || song == null) return;
        playheadMicros = currentPlayheadLocked();
        generation++;
        if (worker != null) worker.cancel(false);
        worker = null;
        scheduledActions = List.of();
        state = State.PAUSED;
        stopScreenNotesLocked();
        if (!localPreview) InstrumentExpressionController.clearSongbookExpression();
        MusicDebugTelemetry.event("songbook_transport", "pause",
                "playhead_us", playheadMicros, "bpm", activeTempoBpm);
    }

    private void stopLocked() {
        generation++;
        if (worker != null) worker.cancel(false);
        worker = null;
        scheduledActions = List.of();
        stopScreenNotesLocked();
        state = State.STOPPED;
        song = null;
        if (!localPreview) InstrumentExpressionController.clearSongbookExpression();
        boundScreen = null;
        playheadMicros = 0L;
        startNanos = 0L;
        activePitchShift = 0;
        resumeAfterSingleplayerPause = false;
    }

    private void stopScreenNotesLocked() {
        if (screen != null) {
            releaseActiveNotesLocked(screen);
        }
        activeEvents.clear();
    }

    /** Releases notes through the instrument that created them before a different one takes over. */
    private void releaseActiveNotesLocked(InstrumentMidiScreenAccess owner) {
        for (Map.Entry<Long, ActiveEvent> active : List.copyOf(activeEvents.entrySet())) {
            stopMidiNoteImmediatelyLocked(owner, active.getValue().pitch(), active.getKey());
        }
        activeEvents.clear();
    }

    private void stopMidiNoteImmediatelyLocked(InstrumentMidiScreenAccess owner,
                                                int pitch, long eventId) {
        if (owner == null || song == null) return;
        withPlaybackContext(song.tuningHz(), song.percussion() ? 9 : 0, eventId, () -> {
            owner.stopMidiNoteImmediately(pitch);
            return null;
        });
    }

    private void releasePitchOwnersLocked(int pitch) {
        for (Map.Entry<Long, ActiveEvent> active : List.copyOf(activeEvents.entrySet())) {
            if (active.getValue().pitch() == pitch) {
                activeEvents.remove(active.getKey());
                stopMidiNoteImmediatelyLocked(screen, pitch, active.getKey());
            }
        }
    }

    private boolean isLayeredSynthLocked() {
        return screen != null && screen.instrumentId()
                == M3EInstruments.dexed().getInstrumentId();
    }

    private void stopMidiNoteLocked(int pitch, long eventId) {
        stopMidiNoteLocked(screen, pitch, eventId);
    }

    private void stopMidiNoteLocked(InstrumentMidiScreenAccess owner, int pitch, long eventId) {
        if (owner == null || song == null) return;
        withPlaybackContext(song.tuningHz(), song.percussion() ? 9 : 0, eventId, () -> {
                    owner.stopMidiNote(pitch);
                    return null;
                });
    }

    private void startLocked(MusicScore selected, long fromMicros,
                             int tempoBpm, int pitchShift) {
        if (screen == null || !screen.playbackInstrumentPresent()) {
            stopLocked();
            return;
        }
        boundScreen = screen;
        song = selected;
        state = State.PLAYING;
        playheadMicros = Math.max(0L, fromMicros);
        activeTempoBpm = Math.clamp(tempoBpm, 1, 3_000);
        activePitchShift = Math.clamp(pitchShift, -48, 48);
        MusicScore.ExpressionEvent initialExpression = expressionAtMicros(selected,
                0, selected.tempoMap(), playheadMicros, activeTempoBpm);
        if (!localPreview) InstrumentExpressionController.showSongbookExpression(
                initialExpression.bendSemitones(), initialExpression.modulation());
        startNanos = System.nanoTime() - playheadMicros * 1_000L;
        long runGeneration = ++generation;
        List<Action> actions = actions(selected,
                playheadMicros, activeTempoBpm, activePitchShift, true);
        scheduledActions = actions;
        MusicDebugTelemetry.event("songbook_transport", "play",
                "song", selected.sourceName(), "playhead_us", playheadMicros,
                "bpm", activeTempoBpm, "pitch_shift", activePitchShift,
                "notes", selected.notes().size(), "actions", actions.size());
        long durationMicros = durationMicros(selected, activeTempoBpm);
        scheduleNext(selected, actions, durationMicros, runGeneration, 0);
    }

    private void scheduleNext(MusicScore selected, List<Action> actions,
                              long durationMicros, long runGeneration, int index) {
        long deadline = index < actions.size() ? actions.get(index).micros() : durationMicros;
        worker = PlaybackScheduler.schedule(() -> {
            synchronized (lock) {
                if (generation != runGeneration || state != State.PLAYING) return;
                int next = index;
                long elapsed = (System.nanoTime() - startNanos) / 1_000L;
                while (next < actions.size() && actions.get(next).micros() <= elapsed) next++;
                int limit = next;
                if (limit > index) Minecraft.getInstance().execute(() -> {
                    for (int event = index; event < limit; event++) dispatch(actions.get(event), runGeneration);
                });
                if (next == actions.size() && elapsed >= durationMicros) {
                    Minecraft.getInstance().execute(() -> complete(selected, runGeneration));
                } else {
                    scheduleNext(selected, actions, durationMicros, runGeneration, next);
                }
            }
        }, startNanos + deadline * 1_000L - System.nanoTime());
    }

    private void dispatch(Action action, long runGeneration) {
        synchronized (lock) {
            if (generation != runGeneration || state != State.PLAYING || screen == null) return;
            if (action.type() == ActionType.ON) {
                if (!localPreview && !isLayeredSynthLocked()) {
                    releasePitchOwnersLocked(action.pitch());
                }
                boolean acquired = withPlaybackContext(
                        song.tuningHz(), song.percussion() ? 9 : 0, action.eventId(),
                        () -> screen.playMidiNote(action.pitch(), action.velocity()));
                if (acquired) {
                    activeEvents.put(action.eventId(), new ActiveEvent(action.track(), action.pitch()));
                }
                if (acquired) {
                    screen.setMidiNoteExpression(action.eventId(), action.pitch(),
                            action.bendSemitones(),
                            InstrumentExpressionController.vibratoDepth(
                                    action.modulation()));
                    screen.setMidiDynamicVolume(action.eventId(), action.pitch(),
                            action.dynamicVolume());
                }
                if (acquired && action.glissandoWaypoints() != null) {
                    screen.applyMidiGlissando(action.eventId(), action.pitch(),
                            action.glissandoWaypoints(), action.glissandoPositions(),
                            action.lengthTicks() * 50_000L);
                }
            } else if (action.type() == ActionType.EXPRESSION) {
                if (!localPreview) InstrumentExpressionController.showSongbookExpression(
                        action.bendSemitones(), action.modulation());
                for (Map.Entry<Long, ActiveEvent> entry : new HashSet<>(activeEvents.entrySet())) {
                    ActiveEvent active = entry.getValue();
                    if (active.track() != action.track()) continue;
                    screen.setMidiNoteExpression(entry.getKey(), active.pitch(),
                            action.bendSemitones(),
                            InstrumentExpressionController.vibratoDepth(action.modulation()));
                    screen.setMidiDynamicVolume(entry.getKey(), active.pitch(),
                            action.dynamicVolume());
                }
            } else if (action.type() == ActionType.VOLUME) {
                ActiveEvent activePitch = activeEvents.get(action.eventId());
                if (activePitch != null && activePitch.pitch() == action.pitch()) {
                    screen.setMidiDynamicVolume(action.eventId(), action.pitch(),
                            action.dynamicVolume());
                }
            } else {
                ActiveEvent activePitch = activeEvents.remove(action.eventId());
                if (activePitch != null) stopMidiNoteLocked(activePitch.pitch(), action.eventId());
            }
        }
    }

    private void complete(MusicScore selected, long runGeneration) {
        Runnable completed;
        synchronized (lock) {
            if (generation != runGeneration || state != State.PLAYING || song != selected) return;
            stopScreenNotesLocked();
            scheduledActions = List.of();
            if (looping) {
                worker = null;
                startLocked(selected, 0L, activeTempoBpm,
                        activePitchShift);
                MusicDebugTelemetry.event("songbook_transport", "loop_restart",
                        "song", selected.sourceName(), "bpm", activeTempoBpm);
            } else {
                MusicDebugTelemetry.event("songbook_transport", "completed",
                        "song", selected.sourceName(), "bpm", activeTempoBpm);
                state = State.STOPPED;
                song = null;
                if (!localPreview) InstrumentExpressionController.clearSongbookExpression();
                boundScreen = null;
                playheadMicros = 0L;
                startNanos = 0L;
                worker = null;
                resumeAfterSingleplayerPause = false;
            }
            completed = cycleCompleteListener;
        }
        completed.run();
    }

    private long currentPlayheadLocked() {
        if (song == null) return playheadMicros;
        if (state != State.PLAYING) return Math.clamp(playheadMicros, 0L, durationMicros(song, activeTempoBpm));
        return Math.clamp((System.nanoTime() - startNanos) / 1_000L, 0L, durationMicros(song, activeTempoBpm));
    }

    private static List<Action> actions(MusicScore song, long fromMicros,
                                        int tempoBpm, int pitchShift,
                                        boolean resumeHeldNotes) {
        return actions(song, fromMicros, tempoBpm, pitchShift, resumeHeldNotes, 0L);
    }

    private static List<Action> actions(MusicScore song, long fromMicros,
                                        int tempoBpm, int pitchShift,
                                        boolean resumeHeldNotes, long eventIdBase) {
        return ScorePlaybackEvents.compile(song, fromMicros, tempoBpm, pitchShift,
                        resumeHeldNotes, eventIdBase, DRUM_RELEASE_TAIL_MICROS).stream()
                .filter(event -> !(event.type() == ScorePlaybackEvents.Type.ON
                        && playbackMicrosAtTick(song, event.sourceStartTick(), tempoBpm) < fromMicros
                        && song.tracks().get(event.track()).percussion()))
                .map(event -> new Action(event.micros(), ActionType.valueOf(event.type().name()),
                        event.eventId(), event.pitch(), event.velocity(), event.sourceStartTick(),
                        event.byteWaypoints(), event.bytePositions(), event.lengthTicks(),
                        event.gain(), event.bendSemitones(), event.modulation(), event.track()))
                .toList();
    }

    static long playbackTick(MusicScore song, long playbackMicros, int tempoBpm) {
        return ScorePlaybackEvents.tick(song, playbackMicros, tempoBpm);
    }

    private <T> T withPlaybackContext(float tuningHz, int channel, long eventId,
                                      Supplier<T> action) {
        long scopedEventId = localPreview ? eventId | Long.MIN_VALUE : eventId;
        return localPreview
                ? InstrumentPlaybackContext.withLocalPreview(
                        tuningHz, channel, scopedEventId, action)
                : InstrumentPlaybackContext.withPlayback(
                        tuningHz, channel, scopedEventId, action);
    }

    private static MusicScore.ExpressionEvent expressionAtMicros(
            MusicScore song, int track, MusicScore.TempoMap tempoMap, long micros, int tempoBpm) {
        return ScorePlaybackEvents.expressionAt(song, track, playbackTick(song, micros, tempoBpm));
    }

    static long playbackMicrosAtTick(MusicScore song, long tick, int tempoBpm) {
        return scaledMicros(song.tempoMap().microsAt(tick), song, tempoBpm);
    }

    private static int tempoBpmAtPlaybackMicros(MusicScore song,
                                                 long playbackMicros,
                                                 int initialBpm) {
        int boundedInitial = Math.clamp(initialBpm, 1, 3_000);
        long sourceMicros = Math.max(0L, Math.round(playbackMicros
                * (boundedInitial / (double) song.initialBpm())));
        return Math.clamp((int) Math.round(song.tempoBpmAtMicros(sourceMicros)
                * (boundedInitial / (double) song.initialBpm())), 1, 3_000);
    }

    private static long scaledMicros(long sourceMicros, MusicScore song, int tempoBpm) {
        return Math.max(0L, Math.round(sourceMicros
                * (song.initialBpm() / (double) Math.clamp(tempoBpm, 1, 3_000))));
    }

    private enum State { STOPPED, PLAYING, PAUSED }

    record TransportState(boolean playing, int bpm, long playheadMicros, long clientSampleNanos) { }

    record VisualizationSnapshot(MusicScore song, long playheadMicros,
                                 long durationMicros, int bpm, int pitchShift,
                                 boolean playing, long sessionId) { }

    private enum ActionType { OFF, EXPRESSION, ON, VOLUME }

    private record Action(long micros, ActionType type, long eventId, int pitch, int velocity,
                          long sourceStartTick, byte[] glissandoWaypoints,
                          byte[] glissandoPositions, int lengthTicks,
                          float dynamicVolume, float bendSemitones,
                          int modulation, int track) {
    }

    private record ActiveEvent(int track, int pitch) { }

    String stateIcon() {
        synchronized (lock) {
            return state == State.PLAYING ? "II" : "▶";
        }
    }
}
