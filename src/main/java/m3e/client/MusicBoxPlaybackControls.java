package m3e.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import xerca.xercamusic.common.item.Items;
import m3e.musicbox.MusicBoxPlaybackAccess;
import m3e.musicbox.MusicBoxLoopMode;
import m3e.network.MusicBoxPlaybackControlPacket;
import m3e.network.MusicBoxPlaybackStatePacket;
import m3e.sheet.MusicScore;
import m3e.sheet.SheetTiming;

import java.util.HashMap;
import java.util.Map;

/** Client transport commands and state application for music-box options. */
public final class MusicBoxPlaybackControls {
    private static final Map<BlockPos, State> STATES = new HashMap<>();
    private static final Map<BlockPos, Long> START_OFFSETS_MICROS = new HashMap<>();
    private static final Map<BlockPos, PendingSeek> PENDING_SEEKS = new HashMap<>();
    private static final long SEEK_ACK_TIMEOUT_NANOS = 2_000_000_000L;
    private static Object lastLevel;

    private MusicBoxPlaybackControls() { }

    public static void request(BlockPos pos) {
        send(pos, MusicBoxPlaybackControlPacket.REQUEST, 0L);
    }

    public static void start(BlockPos pos) {
        send(pos, MusicBoxPlaybackControlPacket.START, 0L);
    }

    public static void pause(BlockPos pos) {
        send(pos, MusicBoxPlaybackControlPacket.PAUSE, 0L);
    }

    public static void stop(BlockPos pos) {
        send(pos, MusicBoxPlaybackControlPacket.STOP, 0L);
    }

    public static synchronized void seek(BlockPos pos, long sourceMicros) {
        BlockPos key = pos.immutable();
        long target = Math.max(0L, sourceMicros);
        State current = STATES.get(key);
        long now = System.nanoTime();
        if (current != null) {
            STATES.put(key, current.withPlayhead(target, now));
            PENDING_SEEKS.put(key, new PendingSeek(current.transportRevision(), now));
        }
        send(key, MusicBoxPlaybackControlPacket.SEEK_MICROS, target);
    }

    public static void setSpeed(BlockPos pos, double speed) {
        send(pos, MusicBoxPlaybackControlPacket.SET_SPEED_HUNDREDTHS,
                Math.round(PlaybackSpeedSlider.clamp(speed) * 100.0D));
    }

    public static void setTranspose(BlockPos pos, int semitones) {
        send(pos, MusicBoxPlaybackControlPacket.SET_TRANSPOSE,
                Math.clamp(semitones, -48, 48));
    }

    public static void setTempo(BlockPos pos, int tempoBpm) {
        send(pos, MusicBoxPlaybackControlPacket.SET_TEMPO,
                Math.clamp(tempoBpm, 1, 3_000));
    }

    public static void toggleLoop(BlockPos pos) {
        send(pos, MusicBoxPlaybackControlPacket.TOGGLE_LOOP, 0L);
    }

    public static void toggleShuffle(BlockPos pos) {
        send(pos, MusicBoxPlaybackControlPacket.TOGGLE_SHUFFLE, 0L);
    }

    private static void send(BlockPos pos, byte action, long value) {
        if (ClientPlayNetworking.canSend(MusicBoxPlaybackControlPacket.TYPE)) {
            ClientPlayNetworking.send(new MusicBoxPlaybackControlPacket(
                    pos.immutable(), action, value));
        }
    }

    public static synchronized void receive(MusicBoxPlaybackStatePacket packet) {
        Minecraft client = Minecraft.getInstance();
        BlockPos pos = packet.pos().immutable();
        State previous = STATES.get(pos);
        long offsetMicros = Math.max(0L, packet.playheadMicros());
        long sampledNanos = System.nanoTime();
        double speed = Math.clamp(packet.speedHundredths() / 100.0D, 0.25D, 2.0D);
        int tempoBpm = Math.clamp(packet.tempoBpm(), 1, 3_000);
        TileEntityMusicBox musicBox = client.level != null
                && client.level.getBlockEntity(pos) instanceof TileEntityMusicBox box
                ? box : null;
        if (packet.playing() && musicBox != null && NetworkClockClient.isSynchronized()) {
            long sampledAtClient = NetworkClockClient.serverToClientNanos(
                    packet.serverSampleNanos());
            long elapsedNanos = Math.clamp(sampledNanos - sampledAtClient,
                    0L, 5_000_000_000L);
            offsetMicros += Math.round(elapsedNanos / 1_000.0D
                    * timing(musicBox).effectiveRate(speed, tempoBpm));
            offsetMicros = Math.min(offsetMicros, sourceDurationMicros(musicBox));
        }
        PendingSeek pending = PENDING_SEEKS.get(pos);
        if (pending != null) {
            boolean waitingForAck = sampledNanos - pending.sentNanos()
                    < SEEK_ACK_TIMEOUT_NANOS;
            if (waitingForAck && packet.transportRevision() <= pending.baseRevision()) return;
            PENDING_SEEKS.remove(pos);
        }
        boolean restartSound = shouldRestartSound(previous, packet.playing(),
                packet.transportRevision());
        STATES.put(pos, new State(packet.playing(), packet.loopMode(),
                packet.shuffle(), packet.powered(), offsetMicros,
                speed, tempoBpm,
                Math.clamp(packet.transpose(), -48, 48),
                packet.transportRevision(), sampledNanos));
        // State requests arrive periodically while the options screen is open.
        // Only seed the sound runner when playback actually begins; otherwise a
        // poll received just before a loop restart would incorrectly seek it.
        if (restartSound) {
            START_OFFSETS_MICROS.put(pos, offsetMicros);
        } else if (!packet.playing()) {
            START_OFFSETS_MICROS.remove(pos);
        }
        if (musicBox == null) return;
        MusicBoxPlaybackAccess access = MusicBoxPlaybackAccess.of(musicBox);
        access.m3e$setMusicBoxLoopMode(packet.loopMode());
        access.m3e$setMusicBoxShuffle(packet.shuffle());
        if (restartSound) {
            // The authoritative revision changed while a controller may still
            // be alive. Replace it so the newly seeded offset reaches the
            // actual sound scheduler instead of only moving the UI playhead.
            access.m3e$setMusicBoxPlaying(false);
            access.m3e$setMusicBoxPlaying(true);
        } else {
            access.m3e$setMusicBoxPlaying(packet.playing());
        }
    }

    public static synchronized State state(TileEntityMusicBox musicBox) {
        State cached = STATES.get(musicBox.getBlockPos());
        MusicBoxPlaybackAccess access = MusicBoxPlaybackAccess.of(musicBox);
        boolean powered = cached != null ? cached.powered() : musicBox.getBlockState().getValue(
                xerca.xercamusic.common.block.BlockMusicBox.POWERED);
        if (cached == null) {
            int tempo = timing(musicBox).playbackTempoBpm(
                    access.m3e$musicBoxPlaybackTempoBpm());
            return new State(access.m3e$isMusicBoxPlaying(),
                    access.m3e$musicBoxLoopMode(),
                    access.m3e$isMusicBoxShuffle(), powered, 0L,
                    access.m3e$musicBoxPlaybackSpeed(),
                    tempo,
                    access.m3e$musicBoxPlaybackTranspose(), 0,
                    System.nanoTime());
        }
        long playhead = cached.playheadMicros();
        if (cached.playing()) {
            double rate = timing(musicBox).effectiveRate(
                    cached.speed(), cached.tempoBpm());
            playhead += Math.max(0L, Math.round(
                    (System.nanoTime() - cached.sampledNanos()) / 1_000.0D * rate));
        }
        return new State(access.m3e$isMusicBoxPlaying(),
                cached.loopMode(), cached.shuffle(), powered,
                Math.clamp(playhead, 0L, sourceDurationMicros(musicBox)),
                cached.speed(), cached.tempoBpm(), cached.transpose(),
                cached.transportRevision(), System.nanoTime());
    }

    public static synchronized long consumeStartOffset(BlockPos pos) {
        Long offset = START_OFFSETS_MICROS.remove(pos);
        return offset == null ? -1L : offset;
    }

    public static synchronized void sheetChanged(TileEntityMusicBox musicBox) {
        BlockPos pos = musicBox.getBlockPos().immutable();
        STATES.remove(pos);
        START_OFFSETS_MICROS.remove(pos);
        PENDING_SEEKS.remove(pos);
        MusicBoxPlaybackAccess.of(musicBox).m3e$setMusicBoxPlaybackTempoBpm(
                sourceTempoBpm(musicBox));
    }

    public static synchronized void tick(Minecraft client) {
        if (lastLevel == client.level) return;
        lastLevel = client.level;
        STATES.clear();
        START_OFFSETS_MICROS.clear();
        PENDING_SEEKS.clear();
    }

    public static synchronized void clear() {
        STATES.clear();
        START_OFFSETS_MICROS.clear();
        PENDING_SEEKS.clear();
        lastLevel = null;
    }

    static boolean shouldRestartSound(State previous, boolean playing, int revision) {
        return playing && (previous == null || !previous.playing()
                || previous.transportRevision() != revision);
    }

    public static int sourceTempoBpm(TileEntityMusicBox musicBox) {
        return timing(musicBox).musicalTempoBpm();
    }

    public static int currentTempoBpm(TileEntityMusicBox musicBox, State state) {
        MusicScore song = exactSong(musicBox);
        double speed = Math.clamp(state.speed(), 0.25D, 2.0D);
        if (song == null) {
            return Math.clamp((int) Math.round(state.tempoBpm() * speed), 1, 3_000);
        }
        double scale = state.tempoBpm() / (double) song.initialBpm();
        return Math.clamp((int) Math.round(song.tempoBpmAtMicros(state.playheadMicros())
                * scale * speed), 1, 3_000);
    }

    public static SheetTiming timing(TileEntityMusicBox musicBox) {
        SheetTiming timing = SheetTiming.from(musicBox.getSheetStack());
        MusicScore song = exactSong(musicBox);
        if (song != null) return timing.withMusicalTempo(song.initialBpm());
        return timing;
    }

    private static MusicScore exactSong(TileEntityMusicBox musicBox) {
        return MusicSheetClientCache.resolve(musicBox.getSheetStack());
    }

    public static long sourceDurationMicros(TileEntityMusicBox musicBox) {
        return timing(musicBox).durationMicros();
    }

    public record State(boolean playing, int loopMode, boolean shuffle, boolean powered,
                        long playheadMicros, double speed, int tempoBpm,
                        int transpose, int transportRevision, long sampledNanos) {
        public String loopLabel() { return MusicBoxLoopMode.byId(loopMode).label(); }

        public State withPlayhead(long value, long sampledAtNanos) {
            return new State(playing, loopMode, shuffle, powered, value, speed,
                    tempoBpm, transpose, transportRevision, sampledAtNanos);
        }

        public State withSpeed(double value) {
            return new State(playing, loopMode, shuffle, powered, playheadMicros,
                    value, tempoBpm, transpose, transportRevision, sampledNanos);
        }

        public State withTempo(int value) {
            return new State(playing, loopMode, shuffle, powered, playheadMicros,
                    speed, value, transpose, transportRevision, sampledNanos);
        }

        public State withTranspose(int value) {
            return new State(playing, loopMode, shuffle, powered, playheadMicros,
                    speed, tempoBpm, value, transportRevision, sampledNanos);
        }
    }

    private record PendingSeek(int baseRevision, long sentNanos) { }
}
