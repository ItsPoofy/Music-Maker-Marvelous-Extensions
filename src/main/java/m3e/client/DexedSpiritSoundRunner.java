package m3e.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import xerca.xercamusic.client.ModClient;
import xerca.xercamusic.common.NoteEvent;
import m3e.sheet.MusicScore;
import m3e.sheet.ScorePlaybackEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/** Native Redstone Synth playback for Music Maker spirit entities. */
public final class DexedSpiritSoundRunner {
    private DexedSpiritSoundRunner() { }

    public static void run(MusicScore song, float sheetVolume, int spiritId,
                           BooleanSupplier stopped, DoubleSupplier x,
                           DoubleSupplier y, DoubleSupplier z) {
        run(compiled(song, 0), sheetVolume, spiritId, stopped, x, y, z);
    }

    public static void run(List<NoteEvent> notes, int bps, float sheetVolume, int spiritId,
                           BooleanSupplier stopped, DoubleSupplier x,
                           DoubleSupplier y, DoubleSupplier z) {
        if (bps <= 0) return;
        long microsPerBeat = Math.round(1_000_000.0D / bps);
        ArrayList<Action> actions = new ArrayList<>();
        long eventId = 1L;
        for (NoteEvent note : notes) {
            int velocity = Math.clamp(Math.round(note.floatVolume() * 127.0F), 1, 127);
            actions.add(Action.note(note.time * microsPerBeat, ActionType.ON,
                    eventId, note.note, velocity));
            actions.add(Action.note((note.time + note.length) * microsPerBeat,
                    ActionType.OFF, eventId, note.note, 0));
            eventId++;
        }
        run(actions, sheetVolume, spiritId, stopped, x, y, z);
    }

    private static void run(List<Action> actions, float sheetVolume, int spiritId,
                            BooleanSupplier stopped, DoubleSupplier x,
                            DoubleSupplier y, DoubleSupplier z) {
        actions.sort(Comparator.comparingLong(Action::micros).thenComparing(Action::type));
        Minecraft minecraft = Minecraft.getInstance();
        PauseAwarePlaybackClock clock = new PauseAwarePlaybackClock(minecraft);
        Set<Long> active = new HashSet<>();
        Map<Long, Integer> activeTracks = new HashMap<>();
        boolean completed = false;
        try {
            for (Action action : actions) {
                if (stopped.getAsBoolean() || minecraft.level == null) return;
                if (!clock.awaitMicros(action.micros(), stopped)) return;
                if (stopped.getAsBoolean() || minecraft.level == null) return;
                minecraft.execute(() -> {
                    if (action.type() == ActionType.ON) {
                        int velocity = Math.clamp(Math.round(action.velocity()
                                * Math.clamp(sheetVolume, 0.0F, 1.0F)), 1, 127);
                        DexedAudioManager.remoteSongbookNoteOn(spiritId,
                                new Vec3(x.getAsDouble(), y.getAsDouble(), z.getAsDouble()),
                                action.eventId(), action.pitch(), velocity);
                        active.add(action.eventId());
                        activeTracks.put(action.eventId(), action.track());
                        DexedAudioManager.songbookVoiceExpression(spiritId, action.eventId(),
                                action.bendSemitones(), action.modulation());
                        DexedAudioManager.songbookVoiceGain(spiritId, action.eventId(),
                                action.dynamicGain());
                        if (action.glissandoWaypoints() != null) {
                            DexedAudioManager.songbookVoiceGlissando(spiritId, action.eventId(),
                                    action.glissandoWaypoints(), action.glissandoPositions(),
                                    action.durationMicros());
                        }
                        if (minecraft.level != null) minecraft.level.addParticle(ParticleTypes.NOTE,
                                x.getAsDouble(), y.getAsDouble() + 2.2D, z.getAsDouble(),
                                action.pitch() / 24.0D, 0.0D, 0.0D);
                    } else if (action.type() == ActionType.OFF) {
                        DexedAudioManager.songbookNoteOff(spiritId, action.eventId());
                        active.remove(action.eventId());
                        activeTracks.remove(action.eventId());
                    } else if (action.type() == ActionType.EXPRESSION) {
                        for (Map.Entry<Long, Integer> entry : activeTracks.entrySet()) {
                            if (entry.getValue() != action.track()) continue;
                            DexedAudioManager.songbookVoiceExpression(spiritId, entry.getKey(),
                                    action.bendSemitones(), action.modulation());
                            DexedAudioManager.songbookVoiceGain(spiritId, entry.getKey(),
                                    action.dynamicGain());
                        }
                    } else {
                        DexedAudioManager.songbookVoiceGain(spiritId, action.eventId(),
                                action.dynamicGain());
                    }
                });
            }
            completed = true;
        } finally {
            if (!completed || stopped.getAsBoolean()) minecraft.execute(() -> {
                for (long eventId : Set.copyOf(active)) {
                    DexedAudioManager.songbookNoteOff(spiritId, eventId);
                }
                active.clear();
            });
            if (completed && minecraft.player != null) {
                int playerId = minecraft.player.getId();
                minecraft.execute(() -> ModClient.endMusic(spiritId, playerId));
            }
        }
    }

    private static List<Action> compiled(MusicScore song, long fromMicros) {
        return new ArrayList<>(ScorePlaybackEvents.compile(song, fromMicros, song.initialBpm(),
                0, true, 0, 0).stream().map(event -> new Action(event.micros(),
                ActionType.valueOf(event.type().name()), event.eventId(), event.pitch(),
                event.velocity(), event.bendSemitones(), event.modulation(), event.gain(),
                event.track(), event.waypoints(), event.positions(), event.durationMicros())).toList());
    }

    private enum ActionType { OFF, EXPRESSION, ON, VOLUME }

    private record Action(long micros, ActionType type, long eventId, int pitch,
                          int velocity, float bendSemitones, int modulation,
                          float dynamicGain, int track, float[] glissandoWaypoints,
                          float[] glissandoPositions, long durationMicros) {
        private static Action note(long micros, ActionType type, long eventId,
                                   int pitch, int velocity) {
            return new Action(micros, type, eventId, pitch, velocity, 0.0F, 0,
                    1.0F, -1, null, null, 0L);
        }

        private static Action volume(long micros, long eventId, float gain) {
            return new Action(micros, ActionType.VOLUME, eventId, 0, 0,
                    0.0F, 0, gain, -1, null, null, 0L);
        }
    }
}
