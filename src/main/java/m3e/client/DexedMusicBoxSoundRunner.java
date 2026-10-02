package m3e.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.sheet.MusicScore;
import m3e.sheet.ScorePlaybackEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

/** Routes music-box sheets into the real FM backend instead of its silent SoundEvent shim. */
public final class DexedMusicBoxSoundRunner {
    private DexedMusicBoxSoundRunner() { }

    public static void run(MusicScore song, float sheetVolume,
                           TileEntityMusicBox musicBox, long startOffsetMicros,
                           double speed, int transpose,
                           BooleanSupplier stopped,
                           DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
        List<Action> actions = compiled(song, startOffsetMicros);
        run(actions, sheetVolume, musicBox, startOffsetMicros, speed, transpose, stopped, x, y, z);
    }

    public static void run(List<NoteEvent> notes, List<VolumeMarker> volumeMarkers,
                           int bps, float sheetVolume, TileEntityMusicBox musicBox,
                           long startOffsetMicros, double speed, int transpose,
                           BooleanSupplier stopped, DoubleSupplier x,
                           DoubleSupplier y, DoubleSupplier z) {
        if (bps <= 0) return;
        long microsPerBeat = Math.round(1_000_000.0 / bps);
        ArrayList<Action> actions = new ArrayList<>(notes.size() * 2);
        long eventId = 1L;
        for (NoteEvent note : notes) {
            long start = note.time * microsPerBeat;
            long end = (note.time + note.length) * microsPerBeat;
            if (end <= startOffsetMicros) {
                eventId++;
                continue;
            }
            int resumedBeat = (int) Math.max(note.time,
                    Math.min(Integer.MAX_VALUE, startOffsetMicros / microsPerBeat));
            VolumeMarker marker = legacyMarker(note, volumeMarkers);
            float initial = marker == null ? note.floatVolume()
                    : Math.max(0.0F, marker.getVolumeAt((short) resumedBeat));
            int velocity = Math.clamp(Math.round(initial
                    * 127.0F), 1, 127);
            actions.add(Action.note(Math.max(start, startOffsetMicros), ActionType.ON,
                    eventId, note.note, velocity));
            actions.add(Action.note(end, ActionType.OFF,
                    eventId, note.note, velocity));
            if (marker != null && initial > 0.0F) {
                for (int beat = resumedBeat + 1; beat < note.time + note.length; beat++) {
                    float marked = marker.getVolumeAt((short) beat);
                    if (marked >= 0.0F) actions.add(Action.volume(beat * microsPerBeat,
                            eventId, marked / initial));
                }
            }
            eventId++;
        }
        run(actions, sheetVolume, musicBox, startOffsetMicros, speed, transpose,
                stopped, x, y, z);
    }

    private static void run(List<Action> actions, float sheetVolume,
                            TileEntityMusicBox musicBox, long startOffsetMicros,
                            double speed, int transpose,
                            BooleanSupplier stopped,
                            DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
        actions.sort(Comparator.comparingLong(Action::micros).thenComparing(Action::type));
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos pos = musicBox.getBlockPos().immutable();
        Vec3 initialPosition = new Vec3(x.getAsDouble() + 0.5D,
                y.getAsDouble() + 0.5D, z.getAsDouble() + 0.5D);
        DexedAudioManager.prepareMusicBox(pos, initialPosition,
                MusicBoxInstrumentProfiles.dexedPatch(musicBox),
                MusicBoxInstrumentProfiles.dexedPerformance(musicBox));
        PauseAwarePlaybackClock clock = new PauseAwarePlaybackClock(
                minecraft, startOffsetMicros, speed);
        Map<Long, Integer> activeTracks = new HashMap<>();
        boolean completed = false;
        try {
            for (Action action : actions) {
                if (stopped.getAsBoolean()
                        || !MusicBoxPlaybackValidity.present(minecraft, musicBox)) return;
                if (!clock.awaitMicros(action.micros(), stopped)) return;
                if (stopped.getAsBoolean()
                        || !MusicBoxPlaybackValidity.present(minecraft, musicBox)) return;
                minecraft.execute(() -> dispatch(action, sheetVolume, transpose,
                        pos, musicBox, minecraft, x, y, z, activeTracks));
            }
            completed = true;
        } finally {
            minecraft.execute(() -> DexedAudioManager.musicBoxDynamicGain(pos, 1.0F));
            // Natural note-offs leave the patch's authored release intact.
            // A forced stop must silence every remaining voice immediately.
            if (!completed || stopped.getAsBoolean()) {
                minecraft.execute(() -> DexedAudioManager.stopMusicBox(pos));
            }
        }
    }

    private static void dispatch(Action action, float sheetVolume, int transpose,
                                 BlockPos pos, TileEntityMusicBox musicBox,
                                 Minecraft minecraft,
                                 DoubleSupplier x, DoubleSupplier y, DoubleSupplier z,
                                 Map<Long, Integer> activeTracks) {
        if (action.type() == ActionType.ON) {
            int velocity = Math.clamp(Math.round(action.velocity()
                    * Math.clamp(sheetVolume, 0.0F, 1.0F)), 1, 127);
            Vec3 position = new Vec3(x.getAsDouble() + 0.5D,
                    y.getAsDouble() + 0.5D, z.getAsDouble() + 0.5D);
            DexedAudioManager.musicBoxNoteOn(pos, position,
                    MusicBoxInstrumentProfiles.dexedPatch(musicBox),
                    MusicBoxInstrumentProfiles.dexedPerformance(musicBox),
                    action.eventId(), action.pitch() + transpose, velocity);
            activeTracks.put(action.eventId(), action.track());
            DexedAudioManager.musicBoxVoiceExpression(pos, action.eventId(),
                    action.bendSemitones(), action.modulation());
            DexedAudioManager.musicBoxVoiceGain(pos, action.eventId(), action.dynamicGain());
            if (action.glissandoWaypoints() != null) {
                DexedAudioManager.musicBoxVoiceGlissando(pos, action.eventId(),
                        action.glissandoWaypoints(), action.glissandoPositions(),
                        action.durationMicros());
            }
            if (minecraft.level != null) {
                minecraft.level.addParticle(ParticleTypes.NOTE,
                        x.getAsDouble() + 0.5D, y.getAsDouble() + 2.2D,
                        z.getAsDouble() + 0.5D,
                        (action.pitch() + transpose) / 24.0D, 0.0D, 0.0D);
            }
        } else if (action.type() == ActionType.OFF) {
            DexedAudioManager.musicBoxNoteOff(pos, action.eventId());
            activeTracks.remove(action.eventId());
        } else if (action.type() == ActionType.EXPRESSION) {
            for (Map.Entry<Long, Integer> active : activeTracks.entrySet()) {
                if (active.getValue() == action.track()) {
                    DexedAudioManager.musicBoxVoiceExpression(pos, active.getKey(),
                            action.bendSemitones(), action.modulation());
                    DexedAudioManager.musicBoxVoiceGain(pos, active.getKey(),
                            action.dynamicGain());
                }
            }
        } else {
            DexedAudioManager.musicBoxVoiceGain(pos, action.eventId(), action.dynamicGain());
        }
    }

    private static VolumeMarker legacyMarker(NoteEvent note, List<VolumeMarker> markers) {
        for (VolumeMarker marker : markers) {
            if (marker.fullyContains(note.time, note.length, note.note)) {
                return marker;
            }
        }
        return null;
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
            return new Action(micros, type, eventId, pitch, velocity, 0.0F, 0, 1.0F,
                    -1, null, null, 0L);
        }

        private static Action volume(long micros, long eventId, float dynamicGain) {
            return new Action(micros, ActionType.VOLUME, eventId, 0, 0,
                    0.0F, 0, Math.clamp(dynamicGain, 0.0F, 4.0F),
                    -1, null, null, 0L);
        }
    }
}
