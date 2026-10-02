package m3e.sound;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.instrument.M3EInstruments;
import m3e.instrument.InstrumentVariations;
import m3e.musicbox.MusicBoxInstrumentProfileAccess;
import m3e.musicbox.MusicBoxDexedPatchAccess;
import m3e.network.MusicBoxInstrumentProfileStatePacket;
import m3e.network.MusicBoxInstrumentProfileUpdatePacket;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/** Validates and broadcasts profile updates for music-box block entities. */
public final class MusicBoxInstrumentProfileServer {
    private static final int MAX_UPDATES_PER_SECOND = 40;
    private static final PlayerRateLimiter RATE = new PlayerRateLimiter();

    private MusicBoxInstrumentProfileServer() { }

    public static boolean allow(ServerPlayer player) {
        return RATE.allow(player.getUUID(), MAX_UPDATES_PER_SECOND);
    }

    public static void update(ServerPlayer player, MusicBoxInstrumentProfileUpdatePacket packet) {
        if (!(player.level() instanceof ServerLevel level)) return;
        BlockPos pos = packet.pos();
        if (!level.hasChunkAt(pos) || !player.mayInteract(level, pos)
                || player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D) > 64.0D) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof TileEntityMusicBox musicBox)
                || !(musicBox instanceof MusicBoxInstrumentProfileAccess access)) return;
        IItemInstrument instrument = musicBox.getInstrument();
        int loadedInstrumentId = instrument instanceof Item item
                ? M3EInstruments.protocolId(item) : -1;
        if (loadedInstrumentId < 0 || loadedInstrumentId != packet.instrumentId()) return;

        String variation = InstrumentVariations.normalize(
                loadedInstrumentId, packet.variation());
        boolean unchanged = access.m3e$musicBoxProfileInstrumentId() == loadedInstrumentId
                && access.m3e$musicBoxProfile().equals(packet.profile())
                && variation.equals(access.m3e$musicBoxVariation());
        if (unchanged && musicBox instanceof MusicBoxDexedPatchAccess dexedAccess
                && M3EInstruments.isDexed(instrument)) {
            unchanged = Arrays.equals(dexedAccess.m3e$musicBoxDexedPatch().bytes(),
                    packet.dexedPatch().bytes())
                    && dexedAccess.m3e$musicBoxDexedPerformance()
                    .equals(packet.dexedPerformance());
        }
        if (unchanged) return;
        access.m3e$setMusicBoxProfile(loadedInstrumentId, packet.profile());
        access.m3e$setMusicBoxVariation(variation);
        if (musicBox instanceof MusicBoxDexedPatchAccess dexedAccess
                && M3EInstruments.isDexed(instrument)) {
            dexedAccess.m3e$setMusicBoxDexedPatch(packet.dexedPatch());
            dexedAccess.m3e$setMusicBoxDexedPerformance(
                    packet.dexedPerformance());
        }
        musicBox.setChanged();
        MusicBoxInstrumentProfileStatePacket state = state(
                musicBox, loadedInstrumentId, packet.profile());
        boolean sentToSource = false;
        for (ServerPlayer recipient : PlayerLookup.tracking(musicBox)) {
            ServerPlayNetworking.send(recipient, state);
            sentToSource |= recipient == player;
        }
        if (!sentToSource) ServerPlayNetworking.send(player, state);
    }

    public static void broadcast(TileEntityMusicBox musicBox, @Nullable ServerPlayer source) {
        IItemInstrument instrument = musicBox.getInstrument();
        int instrumentId = instrument instanceof Item item
                ? M3EInstruments.protocolId(item) : -1;
        if (instrumentId < 0) return;
        InstrumentSoundProfile profile = musicBox instanceof MusicBoxInstrumentProfileAccess access
                ? access.m3e$musicBoxProfile() : InstrumentSoundProfile.DEFAULT;
        MusicBoxInstrumentProfileStatePacket state = state(musicBox, instrumentId, profile);
        boolean sentToSource = false;
        for (ServerPlayer recipient : PlayerLookup.tracking(musicBox)) {
            ServerPlayNetworking.send(recipient, state);
            sentToSource |= recipient == source;
        }
        // Fabric GameTest mock players deliberately have no network listener.
        // A real connected source still receives the authoritative state.
        if (source != null && source.connection != null && !sentToSource) {
            ServerPlayNetworking.send(source, state);
        }
    }

    private static MusicBoxInstrumentProfileStatePacket state(
            TileEntityMusicBox musicBox, int instrumentId, InstrumentSoundProfile profile) {
        DexedPatch patch = musicBox instanceof MusicBoxDexedPatchAccess access
                ? access.m3e$musicBoxDexedPatch() : DexedPatch.initVoice();
        DexedPerformance performance = musicBox instanceof MusicBoxDexedPatchAccess access
                ? access.m3e$musicBoxDexedPerformance()
                : DexedPerformance.DEFAULT;
        String variation = musicBox instanceof MusicBoxInstrumentProfileAccess access
                ? InstrumentVariations.normalize(instrumentId,
                        access.m3e$musicBoxVariation())
                : InstrumentVariations.defaultId(instrumentId);
        return new MusicBoxInstrumentProfileStatePacket(
                musicBox.getBlockPos(), instrumentId, profile, variation,
                patch, performance);
    }

    public static void remove(ServerPlayer player) { RATE.remove(player.getUUID()); }

    public static void clear() { RATE.clear(); }

}
