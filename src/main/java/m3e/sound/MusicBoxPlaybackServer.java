package m3e.sound;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import xerca.xercamusic.common.item.Items;
import m3e.musicbox.MusicBoxPlaybackAccess;
import m3e.musicbox.MusicBoxLoopMode;
import m3e.network.MusicBoxPlaybackControlPacket;
import m3e.network.MusicBoxPlaybackStatePacket;
import m3e.sheet.SheetTiming;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Validates music-box transport commands and publishes their actual state. */
public final class MusicBoxPlaybackServer {
    private static final PlayerRateLimiter RATE = new PlayerRateLimiter();
    private static final Map<PlaybackKey, Session> SESSIONS = new HashMap<>();
    private static final Map<PlaybackKey, SheetIdentity> SHEETS = new HashMap<>();

    private MusicBoxPlaybackServer() { }

    public static boolean allow(ServerPlayer player) {
        return RATE.allow(player.getUUID(), 25);
    }

    public static void control(ServerPlayer player, MusicBoxPlaybackControlPacket packet) {
        ServerLevel level = player.level();
        BlockPos pos = packet.pos();
        if (!level.hasChunkAt(pos) || !player.mayInteract(level, pos)
                || player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D) > 64.0D) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof TileEntityMusicBox musicBox)
                || musicBox.getInstrument() == null) return;
        MusicBoxPlaybackAccess access = MusicBoxPlaybackAccess.of(musicBox);
        if (packet.action() < MusicBoxPlaybackControlPacket.REQUEST
                || packet.action() > MusicBoxPlaybackControlPacket.TOGGLE_SHUFFLE) return;
        PlaybackKey key = key(musicBox);
        Session prior = session(musicBox);
        long playhead = currentPlayheadMicros(musicBox, prior);
        boolean restartRunner = false;
        if (packet.action() == MusicBoxPlaybackControlPacket.START) {
            // The transport screen is an explicit manual control. Redstone can
            // still toggle playback normally, but it must not disable Play.
            if (musicBox.getSheetStack().isEmpty()) {
                send(player, musicBox);
                return;
            }
            long resumeAt = prior.playheadMicros() >= sourceDurationMicros(musicBox)
                    ? 0L : prior.playheadMicros();
            SESSIONS.put(key, new Session(level.getGameTime(), resumeAt,
                    nextRevision(prior), true));
            access.m3e$setMusicBoxPlaying(true);
        } else if (packet.action() == MusicBoxPlaybackControlPacket.PAUSE) {
            SESSIONS.put(key, new Session(level.getGameTime(), playhead,
                    nextRevision(prior), false));
            access.m3e$setMusicBoxPlaying(false);
        } else if (packet.action() == MusicBoxPlaybackControlPacket.STOP) {
            SESSIONS.put(key, new Session(level.getGameTime(), 0L,
                    nextRevision(prior), false));
            access.m3e$setMusicBoxPlaying(false);
        } else if (packet.action() == MusicBoxPlaybackControlPacket.TOGGLE_LOOP) {
            MusicBoxLibraryServer.cycleLoopMode(musicBox);
        } else if (packet.action() == MusicBoxPlaybackControlPacket.TOGGLE_SHUFFLE) {
            MusicBoxLibraryServer.toggleShuffle(musicBox);
        } else if (packet.action() == MusicBoxPlaybackControlPacket.SEEK_MICROS) {
            long target = Math.clamp(packet.value(), 0L, sourceDurationMicros(musicBox));
            SESSIONS.put(key, new Session(level.getGameTime(), target,
                    nextRevision(prior), prior.playing()));
            restartRunner = prior.playing();
        } else if (packet.action()
                == MusicBoxPlaybackControlPacket.SET_SPEED_HUNDREDTHS) {
            access.m3e$setMusicBoxPlaybackSpeed(
                    Math.clamp(packet.value(), 25L, 200L) / 100.0F);
            SESSIONS.put(key, new Session(level.getGameTime(), playhead,
                    nextRevision(prior), prior.playing()));
            restartRunner = prior.playing();
        } else if (packet.action() == MusicBoxPlaybackControlPacket.SET_TRANSPOSE) {
            access.m3e$setMusicBoxPlaybackTranspose(
                    (int) Math.clamp(packet.value(), -48L, 48L));
            SESSIONS.put(key, new Session(level.getGameTime(), playhead,
                    nextRevision(prior), prior.playing()));
            restartRunner = prior.playing();
        } else if (packet.action() == MusicBoxPlaybackControlPacket.SET_TEMPO) {
            access.m3e$setMusicBoxPlaybackTempoBpm(
                    (int) Math.clamp(packet.value(), 1L, 3_000L));
            SESSIONS.put(key, new Session(level.getGameTime(), playhead,
                    nextRevision(prior), prior.playing()));
            restartRunner = prior.playing();
        }
        if (packet.action() == MusicBoxPlaybackControlPacket.REQUEST) {
            MusicBoxLibraryServer.send(player, musicBox);
        } else {
            musicBox.setChanged();
        }
        if (restartRunner) {
            // Seed the client-side offset/revision before the replacement
            // SoundController starts, including backward seeks.
            broadcast(musicBox, player);
            restartRunner(access);
            return;
        }
        broadcast(musicBox, player);
    }

    public static void broadcast(TileEntityMusicBox musicBox, @Nullable ServerPlayer source) {
        MusicBoxPlaybackStatePacket state = state(musicBox);
        boolean sentToSource = false;
        for (ServerPlayer recipient : PlayerLookup.tracking(musicBox)) {
            ServerPlayNetworking.send(recipient, state);
            sentToSource |= recipient == source;
        }
        if (source != null && !sentToSource) ServerPlayNetworking.send(source, state);
    }

    private static void send(ServerPlayer player, TileEntityMusicBox musicBox) {
        ServerPlayNetworking.send(player, state(musicBox));
    }

    private static MusicBoxPlaybackStatePacket state(TileEntityMusicBox musicBox) {
        MusicBoxPlaybackAccess access = MusicBoxPlaybackAccess.of(musicBox);
        ServerLevel level = (ServerLevel) musicBox.getLevel();
        boolean playing = access.m3e$isMusicBoxPlaying();
        Session session = session(musicBox);
        long playhead = currentPlayheadMicros(musicBox, session);
        SheetTiming timing = sourceTiming(musicBox);
        int tempo = timing.playbackTempoBpm(
                access.m3e$musicBoxPlaybackTempoBpm());
        int loopMode = access.m3e$musicBoxLoopMode();
        if (!MusicBoxLibraryServer.hasSongbook(musicBox)
                && MusicBoxLoopMode.byId(loopMode) != MusicBoxLoopMode.LOOP_SONG) {
            loopMode = MusicBoxLoopMode.NO_LOOP.ordinal();
        }
        return new MusicBoxPlaybackStatePacket(musicBox.getBlockPos(),
                playing,
                loopMode,
                access.m3e$isMusicBoxShuffle(),
                level.hasNeighborSignal(musicBox.getBlockPos()),
                playhead,
                Math.round(access.m3e$musicBoxPlaybackSpeed() * 100.0F),
                tempo,
                access.m3e$musicBoxPlaybackTranspose(),
                session.revision(), System.nanoTime());
    }

    /** Publishes redstone/vanilla transport transitions observed by the block entity tick. */
    public static void observe(TileEntityMusicBox musicBox) {
        if (!(musicBox.getLevel() instanceof ServerLevel level)) return;
        MusicBoxPlaybackAccess access = MusicBoxPlaybackAccess.of(musicBox);
        boolean playing = access.m3e$isMusicBoxPlaying();
        PlaybackKey key = key(musicBox);
        SheetIdentity sheet = sheetIdentity(musicBox);
        SheetIdentity priorSheet = SHEETS.put(key, sheet);
        if (priorSheet != null && !priorSheet.equals(sheet)) {
            access.m3e$setMusicBoxPlaybackTempoBpm(sourceTempoBpm(musicBox));
            Session prior = SESSIONS.get(key);
            SESSIONS.put(key, new Session(level.getGameTime(), 0L,
                    nextRevision(prior), playing));
            musicBox.setChanged();
            broadcast(musicBox, null);
            return;
        }
        Session prior = SESSIONS.get(key);
        boolean knownPlaying = prior != null && prior.playing();
        if (playing != knownPlaying) {
            SESSIONS.put(key, new Session(level.getGameTime(), 0L,
                    nextRevision(prior), playing));
            broadcast(musicBox, null);
            return;
        }
        // Fabric does not provide this block entity with a dedicated
        // start-tracking callback. A lightweight heartbeat lets players who
        // enter the chunk mid-song receive the authoritative playhead.
        if (playing && level.getGameTime() % 20L == 0L) broadcast(musicBox, null);
    }

    /** Resets the authoritative playhead when a looping sheet starts over. */
    public static void restart(TileEntityMusicBox musicBox) {
        if (!(musicBox.getLevel() instanceof ServerLevel level)) return;
        Session prior = session(musicBox);
        SESSIONS.put(key(musicBox), new Session(level.getGameTime(), 0L,
                nextRevision(prior), true));
        broadcast(musicBox, null);
    }

    /** Resets transport after a Songbook selection materializes a new sheet. */
    public static void replaceSheet(TileEntityMusicBox musicBox, boolean playing) {
        if (!(musicBox.getLevel() instanceof ServerLevel level)) return;
        Session prior = session(musicBox);
        SESSIONS.put(key(musicBox), new Session(level.getGameTime(), 0L,
                nextRevision(prior), playing));
        MusicBoxPlaybackAccess access = MusicBoxPlaybackAccess.of(musicBox);
        access.m3e$setMusicBoxPlaying(playing);
        musicBox.setChanged();
        broadcast(musicBox, null);
    }

    private static PlaybackKey key(TileEntityMusicBox musicBox) {
        return new PlaybackKey(musicBox.getLevel().dimension(), musicBox.getBlockPos().immutable());
    }

    public static void remove(ServerPlayer player) { RATE.remove(player.getUUID()); }

    public static void forget(TileEntityMusicBox musicBox) {
        PlaybackKey key = key(musicBox);
        SESSIONS.remove(key);
        SHEETS.remove(key);
    }

    public static void clear() { RATE.clear(); SESSIONS.clear(); SHEETS.clear(); }

    private static Session session(TileEntityMusicBox musicBox) {
        Session existing = SESSIONS.get(key(musicBox));
        if (existing != null) return existing;
        long tick = musicBox.getLevel() == null ? 0L : musicBox.getLevel().getGameTime();
        return new Session(tick, 0L, 0,
                MusicBoxPlaybackAccess.of(musicBox).m3e$isMusicBoxPlaying());
    }

    private static long currentPlayheadMicros(TileEntityMusicBox musicBox, Session session) {
        if (!session.playing() || musicBox.getLevel() == null) {
            return Math.clamp(session.playheadMicros(), 0L, sourceDurationMicros(musicBox));
        }
        MusicBoxPlaybackAccess access = MusicBoxPlaybackAccess.of(musicBox);
        long elapsedMicros = Math.max(0L,
                musicBox.getLevel().getGameTime() - session.anchorGameTick()) * 50_000L;
        SheetTiming timing = sourceTiming(musicBox);
        int tempo = timing.playbackTempoBpm(
                access.m3e$musicBoxPlaybackTempoBpm());
        double rate = timing.effectiveRate(
                access.m3e$musicBoxPlaybackSpeed(), tempo);
        return Math.clamp(session.playheadMicros() + Math.round(elapsedMicros * rate),
                0L, sourceDurationMicros(musicBox));
    }

    private static int sourceTempoBpm(TileEntityMusicBox musicBox) {
        return sourceTiming(musicBox).musicalTempoBpm();
    }

    private static SheetTiming sourceTiming(TileEntityMusicBox musicBox) {
        return SheetTiming.from(musicBox.getSheetStack());
    }

    private static long sourceDurationMicros(TileEntityMusicBox musicBox) {
        return sourceTiming(musicBox).durationMicros();
    }

    private static int nextRevision(@Nullable Session session) {
        return session == null ? 1 : session.revision() + 1;
    }

    private static void restartRunner(MusicBoxPlaybackAccess access) {
        access.m3e$setMusicBoxPlaying(false);
        access.m3e$setMusicBoxPlaying(true);
    }

    private record PlaybackKey(ResourceKey<Level> dimension, BlockPos pos) { }

    private static SheetIdentity sheetIdentity(TileEntityMusicBox musicBox) {
        ItemStack sheet = musicBox.getSheetStack();
        SheetTiming timing = SheetTiming.from(sheet);
        return new SheetIdentity(sheet.get(Items.SHEET_ID),
                sheet.getOrDefault(Items.SHEET_VERSION, -1),
                timing.lengthBeats(), timing.projectionBps(), timing.musicalTempoBpm());
    }

    private record SheetIdentity(@Nullable UUID id, int version, int length, int bps,
                                 int sourceBpm) { }

    private record Session(long anchorGameTick, long playheadMicros,
                           int revision, boolean playing) { }
}
