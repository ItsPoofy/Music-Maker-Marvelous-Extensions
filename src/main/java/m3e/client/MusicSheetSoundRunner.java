package m3e.client;

import m3e.sheet.MusicScore;
import m3e.sheet.ScorePlaybackEvents;
import m3e.sound.InstrumentSoundProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import xerca.xercamusic.client.ModClient;
import xerca.xercamusic.client.NoteSound;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.instrument.InstrumentVariations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import org.jspecify.annotations.Nullable;

/** Exact-tempo scheduler for enhanced sheets, executed on SoundController's thread. */
public final class MusicSheetSoundRunner {
    private MusicSheetSoundRunner() { }

    public static void run(MusicScore song, IItemInstrument instrument, float sheetVolume,
                           @Nullable TileEntityMusicBox musicBox, int spiritId,
                           long startOffsetMicros, double speed, int transpose,
                           BooleanSupplier stopped,
                           DoubleSupplier x, DoubleSupplier y, DoubleSupplier z) {
        List<ScorePlaybackEvents.Event> actions = ScorePlaybackEvents.compile(song,
                startOffsetMicros, song.initialBpm(), transpose, true, 0, 0);

        Minecraft minecraft = Minecraft.getInstance();
        Map<Long, ActiveSound> active = new HashMap<>();
        PauseAwarePlaybackClock clock = new PauseAwarePlaybackClock(
                minecraft, startOffsetMicros, speed);
        boolean completed = false;
        try {
            for (ScorePlaybackEvents.Event action : actions) {
                if (stopped.getAsBoolean() || minecraft.level == null
                        || musicBox != null && !MusicBoxPlaybackValidity.present(minecraft, musicBox)) return;
                if (!clock.awaitMicros(action.micros(), stopped)) return;
                if (stopped.getAsBoolean() || minecraft.level == null
                        || musicBox != null && !MusicBoxPlaybackValidity.present(minecraft, musicBox)) return;
                if (action.type() == ScorePlaybackEvents.Type.ON) {
                    int pitch = action.pitch();
                    IItemInstrument.InsSound sound = musicBox == null
                            ? instrument.getSound(pitch)
                            : InstrumentVariations.resolve(instrument, pitch,
                            MusicBoxInstrumentProfiles.variation(musicBox));
                    if (sound == null) continue;
                    minecraft.submit(() -> {
                        NoteSound playing;
                        InstrumentSoundProfile profile = musicBox == null
                                ? InstrumentSoundProfile.DEFAULT.withTuning(song.tuningHz())
                                : MusicBoxInstrumentProfiles.profile(musicBox);
                        RemoteEnvelopeContext.beginProfileOnly(profile);
                        try {
                            playing = ModClient.playNote(sound.sound(), x.getAsDouble(), y.getAsDouble(),
                                    z.getAsDouble(), musicBox != null ? SoundSource.RECORDS : SoundSource.PLAYERS,
                                    MidiNoteVolume.scale(sheetVolume, action.velocity()), sound.pitch(),
                                    (byte) -1);
                        } finally {
                            RemoteEnvelopeContext.end();
                        }
                        if (playing instanceof InstrumentEnvelopeAccess access) {
                            access.m3e$setMidiExpression(
                                    action.bendSemitones(),
                                    InstrumentExpressionController.vibratoDepth(
                                            action.modulation()));
                            access.m3e$setDynamicGain(action.gain());
                        }
                        if (action.glissando() != null) {
                            float[] pitches = new float[action.glissando().points().size()];
                            float[] positions = new float[pitches.length];
                            for (int index = 0; index < pitches.length; index++) {
                                MusicScore.GlissandoPoint point = action.glissando().points().get(index);
                                pitches[index] = sound.pitch() * (float) Math.pow(2.0D,
                                        point.semitones() / 12.0D);
                                positions[index] = point.positionPercent() / 100.0F;
                            }
                            playing.setGlissando(pitches, positions, action.lengthTicks());
                        }
                        if (musicBox != null) {
                            MusicBoxInstrumentProfiles.registerActiveSound(
                                    musicBox.getBlockPos(), playing);
                        }
                        synchronized (active) {
                            active.put(action.eventId(), new ActiveSound(action.note().track(), playing));
                        }
                        if (minecraft.level != null) {
                            int scatter = (int) Math.floorMod(action.eventId(), 5);
                            double angle = scatter * Math.PI * 2.0 / 5.0;
                            double radius = musicBox != null ? 0.14 : 0.08;
                            minecraft.level.addParticle(ParticleTypes.NOTE,
                                    x.getAsDouble() + (musicBox != null ? 0.5 : 0) + Math.cos(angle) * radius,
                                    y.getAsDouble() + 2.2 + (scatter % 2) * 0.04,
                                    z.getAsDouble() + (musicBox != null ? 0.5 : 0) + Math.sin(angle) * radius,
                                    pitch / 24.0, 0, 0);
                        }
                    });
                } else if (action.type() == ScorePlaybackEvents.Type.EXPRESSION) {
                    minecraft.submit(() -> {
                        synchronized (active) {
                            for (ActiveSound activeSound : active.values()) {
                                if (activeSound.track != action.track()) continue;
                                NoteSound playing = activeSound.sound;
                                if (playing instanceof InstrumentEnvelopeAccess access
                                        && !playing.isStopped()) {
                                    access.m3e$setMidiExpression(
                                            action.bendSemitones(),
                                            InstrumentExpressionController.vibratoDepth(
                                                    action.modulation()));
                                    access.m3e$setDynamicGain(action.gain());
                                }
                            }
                        }
                    });
                } else if (action.type() == ScorePlaybackEvents.Type.VOLUME) {
                    minecraft.submit(() -> {
                        synchronized (active) {
                            ActiveSound activeSound = active.get(action.eventId());
                            if (activeSound != null && activeSound.sound instanceof InstrumentEnvelopeAccess access
                                    && !activeSound.sound.isStopped()) {
                                access.m3e$setDynamicGain(action.gain());
                            }
                        }
                    });
                } else {
                    minecraft.submit(() -> {
                        synchronized (active) {
                            ActiveSound activeSound = active.remove(action.eventId());
                            NoteSound playing = activeSound == null ? null : activeSound.sound;
                            if (playing != null) {
                                stop(playing, musicBox == null);
                            }
                        }
                    });
                }
            }
            completed = true;
        } finally {
            minecraft.submit(() -> {
                synchronized (active) {
                    for (ActiveSound activeSound : active.values()) {
                        stop(activeSound.sound, musicBox == null);
                    }
                    active.clear();
                }
            });
            if (completed && spiritId >= 0 && minecraft.player != null) {
                int playerId = minecraft.player.getId();
                minecraft.submit(() -> ModClient.endMusic(spiritId, playerId));
            }
        }
    }

    private static void stop(NoteSound sound, boolean immediate) {
        if (immediate && sound instanceof InstrumentEnvelopeAccess access) {
            access.m3e$stopImmediately();
        } else {
            sound.stopSound();
        }
    }

    private record ActiveSound(int track, NoteSound sound) { }
}
