package m3e.client;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;
import m3e.mixin.client.SoundEngineAccessor;
import m3e.mixin.client.SoundManagerAccessor;
import m3e.sound.InstrumentSoundProfile;

import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One streaming polyphonic Dexed instance per audible performer. */
public final class DexedAudioManager {
    private static final long RELEASE_GRACE_NANOS = 12_000_000_000L;
    private static final ScheduledExecutorService STREAM_PUMP =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Redstone Synthesizer audio pump");
                thread.setDaemon(true);
                return thread;
            });
    private static final Map<SourceKey, Instance> INSTANCES = new HashMap<>();
    private static final Map<Integer, DexedPatch> REMOTE_PATCHES = new HashMap<>();
    private static final Map<Integer, DexedPerformance> REMOTE_PERFORMANCE = new HashMap<>();
    private static final Map<SourceKey, Float> SOURCE_GAINS = new HashMap<>();
    private static final Map<SourceKey, Expression> SOURCE_EXPRESSIONS = new HashMap<>();
    private static ClientLevel activeLevel;

    private DexedAudioManager() { }

    public static void receivePatch(int entityId, DexedPatch patch,
                                    DexedPerformance performance) {
        REMOTE_PATCHES.put(entityId, patch);
        REMOTE_PERFORMANCE.put(entityId, performance);
        Instance instance = INSTANCES.get(entityKey(entityId));
        if (instance != null && !patch.equals(instance.patch)) {
            instance.patch = patch;
            instance.stream.patch(patch);
        }
        if (instance != null) {
            instance.performance = performance;
            instance.stream.performancePitch(
                    profile(Minecraft.getInstance(), entityKey(entityId)).tuningHz(), performance);
        }
    }

    public static void noteOn(int entityId, Vec3 position, DexedPatch patch,
                              DexedPerformance performance, int note, int velocity) {
        Instance instance = instance(entityKey(entityId), position, patch, performance);
        if (instance == null) return;
        instance.position = position;
        int shifted = shift(note, performance);
        HeldVoice previous = instance.held.put(note, new HeldVoice(shifted, velocity));
        if (previous != null) instance.stream.noteOff(previous.pitch());
        instance.stream.noteOn(shifted, velocity);
        instance.lastActivity = System.nanoTime();
    }

    /** Starts one Songbook event without conflating repeated pitches under sustain. */
    public static void songbookNoteOn(int entityId, Vec3 position, DexedPatch patch,
                                      DexedPerformance performance, long eventId,
                                      int note, int velocity) {
        if (eventId == 0L) {
            noteOn(entityId, position, patch, performance, note, velocity);
            return;
        }
        layeredNoteOn(entityKey(entityId), position, patch, performance,
                eventId, note, velocity);
    }

    /** Starts one stationary music-box voice through the same FM engine as handheld play. */
    public static void musicBoxNoteOn(BlockPos pos, Vec3 position, DexedPatch patch,
                                      DexedPerformance performance, long eventId,
                                      int note, int velocity) {
        layeredNoteOn(musicBoxKey(pos), position, patch, performance,
                eventId, note, velocity);
    }

    public static void updateMusicBox(BlockPos pos, DexedPatch patch,
                                      DexedPerformance performance) {
        SourceKey key = musicBoxKey(pos);
        Instance instance = INSTANCES.get(key);
        if (instance == null || instance.closed) return;
        if (!patch.equals(instance.patch)) {
            instance.patch = patch;
            instance.stream.patch(patch);
        }
        instance.performance = performance;
        instance.stream.performancePitch(
                profile(Minecraft.getInstance(), key).tuningHz(), performance);
    }

    private static void layeredNoteOn(SourceKey key, Vec3 position, DexedPatch patch,
                                      DexedPerformance performance, long eventId,
                                      int note, int velocity) {
        Instance instance = instance(key, position, patch, performance);
        if (instance == null) return;
        instance.position = position;
        instance.startSongbookVoice(eventId, note, velocity);
        instance.lastActivity = System.nanoTime();
    }

    public static void remoteNote(int entityId, Vec3 position, int note, int velocity, boolean stop) {
        if (stop) noteOff(entityId, note);
        else noteOn(entityId, position,
                REMOTE_PATCHES.getOrDefault(entityId, DexedPatch.initVoice()),
                REMOTE_PERFORMANCE.getOrDefault(entityId, DexedPerformance.DEFAULT),
                note, velocity);
    }

    public static void remoteSongbookNoteOn(int entityId, Vec3 position, long eventId,
                                             int note, int velocity) {
        songbookNoteOn(entityId, position,
                REMOTE_PATCHES.getOrDefault(entityId, DexedPatch.initVoice()),
                REMOTE_PERFORMANCE.getOrDefault(entityId, DexedPerformance.DEFAULT),
                eventId, note, velocity);
    }

    public static void noteOff(int entityId, int note) {
        Instance instance = INSTANCES.get(entityKey(entityId));
        if (instance == null) return;
        HeldVoice voice = instance.held.remove(note);
        if (voice != null) instance.stream.noteOff(voice.pitch());
        instance.lastActivity = System.nanoTime();
    }

    /** Releases only the matching Songbook event, even when its pitch was retriggered. */
    public static void songbookNoteOff(int entityId, long eventId) {
        layeredNoteOff(entityKey(entityId), eventId);
    }

    public static void musicBoxNoteOff(BlockPos pos, long eventId) {
        layeredNoteOff(musicBoxKey(pos), eventId);
    }

    private static void layeredNoteOff(SourceKey key, long eventId) {
        Instance instance = INSTANCES.get(key);
        if (instance == null || eventId == 0L) return;
        instance.stopSongbookVoice(eventId);
        instance.lastActivity = System.nanoTime();
    }

    public static void expression(int entityId, int bend14, int bendRange, int modulation) {
        SourceKey key = entityKey(entityId);
        float bend = (Math.clamp(bend14, 0, 16_383) - 8192) / 8192.0F
                * Math.clamp(bendRange, 1, 48);
        SOURCE_EXPRESSIONS.put(key, new Expression(bend, modulation));
        Instance instance = INSTANCES.get(key);
        if (instance == null) return;
        instance.stream.bend(bend14, bendRange);
        instance.stream.cc(1, modulation);
    }

    public static void expressionSemitones(int entityId, float bendSemitones, int modulation) {
        SourceKey key = entityKey(entityId);
        SOURCE_EXPRESSIONS.put(key, new Expression(bendSemitones, modulation));
        Instance instance = INSTANCES.get(key);
        if (instance == null) return;
        instance.stream.bendSemitones(bendSemitones);
        instance.stream.cc(1, Math.clamp(modulation, 0, 127));
    }

    public static void musicBoxExpression(BlockPos pos, int bend14, int bendRange,
                                          int modulation) {
        Instance instance = INSTANCES.get(musicBoxKey(pos));
        if (instance == null) return;
        instance.stream.bend(bend14, bendRange);
        instance.stream.cc(1, modulation);
    }

    public static void musicBoxExpressionSemitones(BlockPos pos, float bendSemitones,
                                                    int modulation) {
        SourceKey key = musicBoxKey(pos);
        SOURCE_EXPRESSIONS.put(key, new Expression(bendSemitones, modulation));
        Instance instance = INSTANCES.get(key);
        if (instance == null) return;
        instance.stream.bendSemitones(bendSemitones);
        instance.stream.cc(1, Math.clamp(modulation, 0, 127));
    }

    public static void musicBoxDynamicGain(BlockPos pos, float gain) {
        SOURCE_GAINS.put(musicBoxKey(pos), Math.clamp(gain, 0.0F, 4.0F));
    }

    public static void entityDynamicGain(int entityId, float gain) {
        SOURCE_GAINS.put(entityKey(entityId), Math.clamp(gain, 0.0F, 4.0F));
    }

    public static void songbookVoiceExpression(int entityId, long eventId,
                                                float bendSemitones, int modulation) {
        voiceExpression(entityKey(entityId), eventId, bendSemitones, modulation);
    }

    public static void musicBoxVoiceExpression(BlockPos pos, long eventId,
                                                float bendSemitones, int modulation) {
        voiceExpression(musicBoxKey(pos), eventId, bendSemitones, modulation);
    }

    private static void voiceExpression(SourceKey key, long eventId, float bendSemitones,
                                        int modulation) {
        Instance instance = INSTANCES.get(key);
        if (instance != null) instance.voiceExpression(eventId, bendSemitones, modulation);
    }

    public static void songbookVoiceGain(int entityId, long eventId, float gain) {
        voiceGain(entityKey(entityId), eventId, gain);
    }

    public static void musicBoxVoiceGain(BlockPos pos, long eventId, float gain) {
        voiceGain(musicBoxKey(pos), eventId, gain);
    }

    private static void voiceGain(SourceKey key, long eventId, float gain) {
        Instance instance = INSTANCES.get(key);
        if (instance != null) instance.voiceGain(eventId, gain);
    }

    public static void songbookVoiceGlissando(int entityId, long eventId,
                                               float[] waypoints, float[] positions,
                                               long durationMicros) {
        voiceGlissando(entityKey(entityId), eventId, waypoints, positions, durationMicros);
    }

    public static void musicBoxVoiceGlissando(BlockPos pos, long eventId,
                                               float[] waypoints, float[] positions,
                                               long durationMicros) {
        voiceGlissando(musicBoxKey(pos), eventId, waypoints, positions, durationMicros);
    }

    private static void voiceGlissando(SourceKey key, long eventId, float[] waypoints,
                                       float[] positions, long durationMicros) {
        Instance instance = INSTANCES.get(key);
        if (instance != null) instance.voiceGlissando(eventId, waypoints, positions,
                durationMicros);
    }

    public static boolean prepareMusicBox(BlockPos pos, Vec3 position, DexedPatch patch,
                                          DexedPerformance performance) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Instance prepared = minecraft.submit(() -> instance(musicBoxKey(pos), position,
                    patch, performance)).get(250L, TimeUnit.MILLISECONDS);
            if (prepared == null) return false;
            prepared.ready.get(250L, TimeUnit.MILLISECONDS);
            return !prepared.closed;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
            return false;
        }
    }

    public static void stopEntity(int entityId) {
        SourceKey key = entityKey(entityId);
        Instance instance = INSTANCES.remove(key);
        if (instance != null) instance.close();
        SOURCE_GAINS.remove(key);
        SOURCE_EXPRESSIONS.remove(key);
    }

    public static void stopMusicBox(BlockPos pos) {
        SourceKey key = musicBoxKey(pos);
        Instance instance = INSTANCES.remove(key);
        if (instance != null) instance.close();
        SOURCE_GAINS.remove(key);
        SOURCE_EXPRESSIONS.remove(key);
    }

    public static void tick(Minecraft client) {
        if (client.level != activeLevel) {
            for (Instance instance : INSTANCES.values()) instance.close();
            INSTANCES.clear();
            activeLevel = client.level;
        }
        long now = System.nanoTime();
        List<Restart> restarts = new ArrayList<>();
        Iterator<Map.Entry<SourceKey, Instance>> iterator = INSTANCES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SourceKey, Instance> entry = iterator.next();
            SourceKey key = entry.getKey();
            Instance instance = entry.getValue();
            if (instance.closed) {
                instance.close();
                iterator.remove();
                SOURCE_GAINS.remove(key);
                SOURCE_EXPRESSIONS.remove(key);
                continue;
            }
            Entity entity = client.level == null || !key.followsEntity()
                    ? null : client.level.getEntity(key.entityId());
            if (entity != null) instance.position = entity.position();
            if (instance.handle != null && instance.handle.isStopped()) {
                if (!instance.held.isEmpty() || !instance.songbookVoices.isEmpty()) {
                    restarts.add(new Restart(key, instance.position,
                            instance.patch, instance.performance, Map.copyOf(instance.held),
                            Map.copyOf(instance.songbookVoices)));
                }
                instance.close();
                iterator.remove();
                continue;
            }
            if (instance.handle != null) {
                Vec3 position = instance.position;
                float volume = sourceVolume(client, key);
                instance.stream.performancePitch(
                        profile(client, key).tuningHz(), instance.performance);
                instance.handle.execute(channel -> {
                    channel.setSelfPosition(position);
                    channel.setVolume(volume);
                });
            }
            boolean sourceGone = key.followsEntity() ? entity == null
                    : client.level == null
                    || !(client.level.getBlockEntity(key.blockPos())
                    instanceof xerca.xercamusic.common.tile_entity.TileEntityMusicBox);
            if (sourceGone) {
                instance.close();
                iterator.remove();
                SOURCE_GAINS.remove(key);
                continue;
            }
            if (instance.held.isEmpty() && instance.songbookVoices.isEmpty()
                    && now - instance.lastActivity > RELEASE_GRACE_NANOS) {
                instance.close();
                iterator.remove();
                SOURCE_GAINS.remove(key);
            }
        }
        for (Restart restart : restarts) {
            for (Map.Entry<Integer, HeldVoice> note : restart.held.entrySet()) {
                if (restart.key.followsEntity()) {
                    noteOn(restart.key.entityId(), restart.position, restart.patch,
                            restart.performance, note.getKey(), note.getValue().velocity());
                }
            }
            for (Map.Entry<Long, SongbookVoice> note : restart.songbookVoices.entrySet()) {
                layeredNoteOn(restart.key, restart.position, restart.patch,
                        restart.performance, note.getKey(), note.getValue().pitch(),
                        note.getValue().velocity());
                voiceExpression(restart.key, note.getKey(),
                        note.getValue().bendSemitones, note.getValue().modulation);
                voiceGain(restart.key, note.getKey(), note.getValue().gain);
            }
        }
    }

    public static void clear() {
        for (Instance instance : INSTANCES.values()) instance.close();
        INSTANCES.clear();
        REMOTE_PATCHES.clear();
        REMOTE_PERFORMANCE.clear();
        SOURCE_GAINS.clear();
        SOURCE_EXPRESSIONS.clear();
        activeLevel = null;
    }

    private static Instance instance(SourceKey key, Vec3 position, DexedPatch patch,
                                     DexedPerformance performance) {
        Instance existing = INSTANCES.get(key);
        if (existing != null && existing.closed) {
            INSTANCES.remove(key);
            existing = null;
        }
        if (existing != null) {
            if (!patch.equals(existing.patch)) {
                existing.patch = patch;
                existing.stream.patch(patch);
            }
            existing.performance = performance;
            existing.stream.performancePitch(
                    profile(Minecraft.getInstance(), key).tuningHz(), performance);
            return existing;
        }
        DexedAudioStream stream = new DexedAudioStream(patch);
        Instance created = new Instance(stream, position, patch, performance);
        INSTANCES.put(key, created);
        Minecraft minecraft = Minecraft.getInstance();
        stream.performancePitch(profile(minecraft, key).tuningHz(), performance);
        Expression retained = SOURCE_EXPRESSIONS.get(key);
        if (retained != null) {
            stream.bendSemitones(retained.bendSemitones());
            stream.cc(1, retained.modulation());
        }
        ChannelAccess access = ((SoundEngineAccessor) ((SoundManagerAccessor)
                minecraft.getSoundManager()).m3e$soundEngine())
                .m3e$channelAccess();
        access.createHandle(Library.Pool.STREAMING).thenAccept(handle -> {
            if (handle == null) {
                created.close();
                return;
            }
            created.handle = handle;
            handle.execute(channel -> {
                if (created.closed) {
                    channel.stop();
                    return;
                }
                channel.setSelfPosition(created.position);
                channel.linearAttenuation(48.0F);
                channel.setVolume(sourceVolume(minecraft, key));
                channel.attachBufferStream(stream);
                channel.play();
                created.ready.complete(null);
            });
        });
        return created;
    }

    private static final class Instance {
        private final DexedAudioStream stream;
        private final Map<Integer, HeldVoice> held = new HashMap<>();
        private final Map<Long, SongbookVoice> songbookVoices = new HashMap<>();
        private volatile ChannelAccess.ChannelHandle handle;
        private volatile Vec3 position;
        private volatile long lastActivity = System.nanoTime();
        private volatile boolean closed;
        private final AtomicBoolean pumpPending = new AtomicBoolean();
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private final ScheduledFuture<?> pumpTask;
        private DexedPatch patch;
        private DexedPerformance performance;

        private Instance(DexedAudioStream stream, Vec3 position, DexedPatch patch,
                         DexedPerformance performance) {
            this.stream = stream;
            this.position = position;
            this.patch = patch;
            this.performance = performance;
            pumpTask = STREAM_PUMP.scheduleAtFixedRate(
                    this::maintainStream, 0L, 5L, TimeUnit.MILLISECONDS);
        }

        private void maintainStream() {
            ChannelAccess.ChannelHandle current = handle;
            if (closed || current == null || current.isStopped()
                    || !pumpPending.compareAndSet(false, true)) return;
            current.execute(channel -> {
                try {
                    if (closed) return;
                    channel.updateStream();
                    // Recover inside the sound executor before ChannelAccess's
                    // 20 Hz cleanup mistakes a transient underrun for EOF.
                    if (channel.stopped()) channel.play();
                } finally {
                    pumpPending.set(false);
                }
            });
        }

        private void startSongbookVoice(long eventId, int note, int velocity) {
            SongbookVoice previous = songbookVoices.remove(eventId);
            if (previous != null) stream.noteOffVoice(previous.voiceToken());
            long voiceToken = stream.noteOn(shift(note, performance), velocity);
            if (voiceToken != 0L) {
                songbookVoices.put(eventId, new SongbookVoice(note, velocity, voiceToken));
            }
        }

        private void stopSongbookVoice(long eventId) {
            SongbookVoice voice = songbookVoices.remove(eventId);
            if (voice != null) stream.noteOffVoice(voice.voiceToken());
        }

        private void voiceExpression(long eventId, float bendSemitones,
                                     int modulation) {
            SongbookVoice voice = songbookVoices.get(eventId);
            if (voice == null) return;
            voice.bendSemitones = Math.clamp(bendSemitones, -96.0F, 96.0F);
            voice.modulation = Math.clamp(modulation, 0, 127);
            voice.apply(stream);
        }

        private void voiceGain(long eventId, float gain) {
            SongbookVoice voice = songbookVoices.get(eventId);
            if (voice == null) return;
            voice.gain = Math.clamp(gain, 0.0F, 4.0F);
            voice.apply(stream);
        }

        private void voiceGlissando(long eventId, float[] waypoints, float[] positions,
                                    long durationMicros) {
            SongbookVoice voice = songbookVoices.get(eventId);
            if (voice != null) stream.voiceGlissando(voice.voiceToken(), waypoints,
                    positions, durationMicros);
        }

        private void close() {
            if (closed) return;
            closed = true;
            ready.completeExceptionally(new IllegalStateException("Dexed stream closed"));
            pumpTask.cancel(false);
            // ChannelAccess owns its handles. Stopping lets its next scheduled
            // tick release and remove the handle atomically; calling release()
            // directly leaves a null channel inside ChannelAccess's live set.
            if (handle != null) handle.execute(com.mojang.blaze3d.audio.Channel::stop);
            stream.close();
        }
    }

    private static final class SongbookVoice {
        private final int pitch;
        private final int velocity;
        private final long voiceToken;
        private float bendSemitones;
        private int modulation;
        private float gain = 1.0F;

        private SongbookVoice(int pitch, int velocity, long voiceToken) {
            this.pitch = pitch;
            this.velocity = velocity;
            this.voiceToken = voiceToken;
        }

        private int pitch() { return pitch; }
        private int velocity() { return velocity; }
        private long voiceToken() { return voiceToken; }
        private void apply(DexedAudioStream stream) {
            stream.voiceExpression(voiceToken, bendSemitones, modulation, gain);
        }
    }

    /** Remembers the actual transposed pitch so changing Coarse cannot strand a held note. */
    private record HeldVoice(int pitch, int velocity) { }

    private record Expression(float bendSemitones, int modulation) {
        private Expression {
            bendSemitones = Math.clamp(bendSemitones, -96.0F, 96.0F);
            modulation = Math.clamp(modulation, 0, 127);
        }
    }

    private static SourceKey entityKey(int entityId) {
        return new SourceKey(entityId, null);
    }

    private static SourceKey musicBoxKey(BlockPos pos) {
        return new SourceKey(-1, pos.immutable());
    }

    private static float sourceVolume(Minecraft minecraft, SourceKey key) {
        SoundSource source = key.followsEntity() ? SoundSource.PLAYERS : SoundSource.RECORDS;
        return Math.clamp(minecraft.options.getFinalSoundSourceVolume(source)
                * profileGain(profile(minecraft, key))
                * SOURCE_GAINS.getOrDefault(key, 1.0F), 0.0F, 1.0F);
    }

    static float profileGain(InstrumentSoundProfile profile) {
        return profile.midiVolume() / (float) InstrumentSoundProfile.MAX_MIDI_VOLUME;
    }

    private static m3e.sound.InstrumentSoundProfile profile(
            Minecraft minecraft, SourceKey key) {
        if (!key.followsEntity() && minecraft.level != null
                && minecraft.level.getBlockEntity(key.blockPos())
                instanceof xerca.xercamusic.common.tile_entity.TileEntityMusicBox musicBox) {
            return MusicBoxInstrumentProfiles.profile(musicBox);
        }
        if (minecraft.player != null && minecraft.player.getId() == key.entityId()) {
            return InstrumentOptions.playerDefaultProfile(
                    m3e.instrument.M3EInstruments.dexed().getInstrumentId());
        }
        return InstrumentProfileClientState.remoteProfile(key.entityId(),
                m3e.instrument.M3EInstruments.dexed().getInstrumentId());
    }

    private static int shift(int note, DexedPerformance performance) {
        return Math.clamp(note + performance.coarseSemitones(), 0, 127);
    }

    private record SourceKey(int entityId, BlockPos blockPos) {
        private boolean followsEntity() { return blockPos == null; }
    }

    private record Restart(SourceKey key, Vec3 position, DexedPatch patch,
                           DexedPerformance performance,
                           Map<Integer, HeldVoice> held,
                           Map<Long, SongbookVoice> songbookVoices) { }
}
