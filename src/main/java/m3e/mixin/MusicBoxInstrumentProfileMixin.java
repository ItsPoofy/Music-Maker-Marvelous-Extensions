package m3e.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jspecify.annotations.Nullable;
import xerca.xercamusic.client.SoundController;
import xerca.xercamusic.common.block.BlockMusicBox;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.musicbox.MusicBoxInstrumentProfileAccess;
import m3e.musicbox.MusicBoxLibraryAccess;
import m3e.musicbox.MusicBoxLoopMode;
import m3e.musicbox.MusicBoxPlaybackAccess;
import m3e.musicbox.MusicBoxDexedPatchAccess;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;
import m3e.sound.InstrumentSoundProfile;
import m3e.sound.MusicBoxPlaybackServer;
import m3e.sound.MusicBoxLibraryServer;
import m3e.sheet.MusicSheetComponents;
import m3e.sheet.MusicScore;
import m3e.sheet.SheetTiming;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;
import xerca.xercamusic.common.item.Items;
import java.util.ArrayList;

/** Persists the server-approved sound profile and loop transport state. */
@Mixin(TileEntityMusicBox.class)
abstract class MusicBoxInstrumentProfileMixin
        implements MusicBoxInstrumentProfileAccess, MusicBoxPlaybackAccess,
        MusicBoxDexedPatchAccess, MusicBoxLibraryAccess {
    @Shadow private boolean isPlaying;
    @Shadow private boolean oldPoweredState;
    @Shadow private boolean isPowering;
    @Shadow private int poweringAge;
    @Shadow private int playingAge;
    @Shadow private @Nullable SoundController soundController;
    @Shadow @Final private ArrayList<NoteEvent> notes;
    @Shadow @Final private ArrayList<VolumeMarker> volumeMarkers;
    @Shadow private ItemStack sheetStack;
    @Shadow private byte bps;
    @Shadow private float volume;
    @Shadow private int length;
    @Unique private final m3e.musicbox.MusicBoxState m3e$state = new m3e.musicbox.MusicBoxState();

    @Inject(method = "loadMusicData", at = @At("HEAD"), cancellable = true)
    private void m3e$loadCanonicalScore(Level level, CallbackInfo ci) {
        if (m3e$prepareCanonicalClock()) ci.cancel();
    }

    @Inject(method = "setSheetStack", at = @At("TAIL"))
    private void m3e$initializeCanonicalSheetClock(ItemStack stack, boolean update,
                                                   CallbackInfo ci) {
        m3e$prepareCanonicalClock();
    }

    @Inject(method = "musicStart", at = @At("HEAD"))
    private static void m3e$initializeClockBeforePlayback(TileEntityMusicBox musicBox,
                                                          net.minecraft.core.BlockPos pos,
                                                          CallbackInfo ci) {
        ((MusicBoxInstrumentProfileMixin) (Object) musicBox).m3e$prepareCanonicalClock();
    }

    @Unique private boolean m3e$prepareCanonicalClock() {
        if (sheetStack.isEmpty() || !SheetTiming.hasCanonicalClock(sheetStack)) {
            return false;
        }
        bps = 20;
        volume = Math.clamp(sheetStack.getOrDefault(Items.SHEET_VOLUME, 1.0F), 0.0F, 1.0F);
        MusicScore score = sheetStack.get(MusicSheetComponents.SCORE);
        if (score == null) {
            length = Integer.MAX_VALUE;
        } else {
            long durationMicros = score.tempoMap().microsAt(score.lengthTick());
            length = (int) Math.min(Integer.MAX_VALUE,
                    Math.max(1L, (durationMicros + 49_999L) / 50_000L));
        }
        notes.clear();
        volumeMarkers.clear();
        return true;
    }

    @Override public InstrumentSoundProfile m3e$musicBoxProfile() { return m3e$state.m3e$musicBoxProfile(); }

    @Override public int m3e$musicBoxProfileInstrumentId() { return m3e$state.m3e$musicBoxProfileInstrumentId(); }

    @Override public void m3e$setMusicBoxProfile(int instrumentId,
                                                                  InstrumentSoundProfile profile) { m3e$state.m3e$setMusicBoxProfile(instrumentId,profile); }

    @Override public boolean m3e$isMusicBoxPlaying() { return isPlaying; }

    @Override public int m3e$musicBoxLoopMode() { return m3e$state.m3e$musicBoxLoopMode(); }

    @Override public String m3e$musicBoxVariation() { return m3e$state.m3e$musicBoxVariation(); }

    @Override public void m3e$setMusicBoxVariation(String variation) { m3e$state.m3e$setMusicBoxVariation(variation); }

    @Override public String m3e$musicBoxInstrumentInstanceId() { return m3e$state.m3e$musicBoxInstrumentInstanceId(); }

    @Override public void m3e$setMusicBoxLoopMode(int mode) { m3e$state.m3e$setMusicBoxLoopMode(mode); }

    @Override public boolean m3e$isMusicBoxShuffle() { return m3e$state.m3e$isMusicBoxShuffle(); }

    @Override public void m3e$setMusicBoxShuffle(boolean shuffle) { m3e$state.m3e$setMusicBoxShuffle(shuffle); }

    @Override public float m3e$musicBoxPlaybackSpeed() { return m3e$state.m3e$musicBoxPlaybackSpeed(); }

    @Override public void m3e$setMusicBoxPlaybackSpeed(float speed) { m3e$state.m3e$setMusicBoxPlaybackSpeed(speed); }

    @Override public int m3e$musicBoxPlaybackTempoBpm() { return m3e$state.m3e$musicBoxPlaybackTempoBpm(); }

    @Override public void m3e$setMusicBoxPlaybackTempoBpm(int tempoBpm) { m3e$state.m3e$setMusicBoxPlaybackTempoBpm(tempoBpm); }

    @Override public int m3e$musicBoxPlaybackTranspose() { return m3e$state.m3e$musicBoxPlaybackTranspose(); }

    @Override public void m3e$setMusicBoxPlaybackTranspose(int semitones) { m3e$state.m3e$setMusicBoxPlaybackTranspose(semitones); }

    @Override public DexedPatch m3e$musicBoxDexedPatch() { return m3e$state.m3e$musicBoxDexedPatch(); }

    @Override public void m3e$setMusicBoxDexedPatch(DexedPatch patch) { m3e$state.m3e$setMusicBoxDexedPatch(patch); }

    @Override public DexedPerformance m3e$musicBoxDexedPerformance() { return m3e$state.m3e$musicBoxDexedPerformance(); }

    @Override public void m3e$setMusicBoxDexedPerformance(
            DexedPerformance performance) { m3e$state.m3e$setMusicBoxDexedPerformance(performance); }

    @Override public ItemStack m3e$musicBoxSongbook() { return m3e$state.m3e$musicBoxSongbook(); }

    @Override public void m3e$setMusicBoxSongbook(ItemStack stack) { m3e$state.m3e$setMusicBoxSongbook(stack); }

    @Override public int m3e$musicBoxSongIndex() { return m3e$state.m3e$musicBoxSongIndex(); }

    @Override public void m3e$setMusicBoxSongIndex(int index) { m3e$state.m3e$setMusicBoxSongIndex(index); }

    @Override public ItemStack m3e$musicBoxInstrumentStack() { return m3e$state.m3e$musicBoxInstrumentStack(); }

    @Override public void m3e$setMusicBoxInstrumentStack(ItemStack stack) { m3e$state.m3e$setMusicBoxInstrumentStack(stack); }



    @Override public void m3e$setMusicBoxPlaying(boolean playing) {
        TileEntityMusicBox musicBox = (TileEntityMusicBox) (Object) this;
        // Transport state and client sound-thread lifetime can briefly diverge
        // at the natural end of a sheet. Stop must always reach the controller,
        // even when the boolean already says stopped.
        if (!playing && soundController != null) soundController.setStop();
        if (isPlaying == playing) {
            if (playing && (soundController == null || !soundController.isAlive())) {
                TileEntityMusicBox.musicStart(musicBox, musicBox.getBlockPos());
            }
            return;
        }
        isPlaying = playing;
        playingAge = 0;
        isPowering = false;
        poweringAge = 0;

        Level level = musicBox.getLevel();
        if (level != null) {
            BlockState state = musicBox.getBlockState();
            oldPoweredState = state.getValue(BlockMusicBox.POWERED);
            if (state.getValue(BlockMusicBox.POWERING)) {
                level.setBlockAndUpdate(musicBox.getBlockPos(),
                        state.setValue(BlockMusicBox.POWERING, false));
            }
            if (playing) TileEntityMusicBox.musicStart(musicBox, musicBox.getBlockPos());
        }
    }

    @Override public void m3e$restartMusicBoxLoop() {
        TileEntityMusicBox musicBox = (TileEntityMusicBox) (Object) this;
        isPlaying = true;
        playingAge = 0;
        MusicBoxPlaybackServer.restart(musicBox);
        TileEntityMusicBox.musicStart(musicBox, musicBox.getBlockPos());
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void m3e$saveProfile(ValueOutput output, CallbackInfo ci) {
        m3e$state.save(output);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void m3e$loadProfile(ValueInput input, CallbackInfo ci) {
        m3e$state.load(input);
    }

    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void m3e$omitLargePrivateStacks(
            HolderLookup.Provider registries,
            CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag == null) return;
        tag.remove(m3e.musicbox.MusicBoxState.m3e$SONGBOOK);
        tag.remove(m3e.musicbox.MusicBoxState.m3e$INSTRUMENT_STACK);
        m3e$state.writeSnapshot(tag, registries);
    }

    @Inject(method = "removeSheetStack", at = @At("TAIL"))
    private void m3e$clearSongbook(CallbackInfo ci) {
        TileEntityMusicBox musicBox = (TileEntityMusicBox) (Object) this;
        if (musicBox.getLevel() != null && !musicBox.getLevel().isClientSide()) {
            MusicBoxLibraryServer.forget(musicBox);
            MusicBoxPlaybackServer.forget(musicBox);
        }
        m3e$state.clearLibrary();
    }

    @Inject(method = "removeInstrument", at = @At("TAIL"))
    private void m3e$clearInstrumentStack(CallbackInfo ci) {
        m3e$state.m3e$setMusicBoxInstrumentStack(ItemStack.EMPTY);
    }

    @Inject(method = "musicOver", at = @At("HEAD"), cancellable = true)
    private static void m3e$restartLoop(TileEntityMusicBox musicBox,
                                                        BlockState state, CallbackInfo ci) {
        if (!(musicBox instanceof MusicBoxPlaybackAccess access)) return;
        Level level = musicBox.getLevel();
        if (level != null && !level.isClientSide()
                && MusicBoxLibraryServer.advanceAtEnd(musicBox)) {
            ci.cancel();
            return;
        }
        if (level != null && level.isClientSide()) {
            MusicBoxLoopMode mode = MusicBoxLoopMode.byId(
                    access.m3e$musicBoxLoopMode());
            if (mode == MusicBoxLoopMode.LOOP_SONG) {
                access.m3e$restartMusicBoxLoop();
                ci.cancel();
            } else if (mode == MusicBoxLoopMode.FINISH_SONGBOOK
                    || mode == MusicBoxLoopMode.LOOP_SONGBOOK) {
                // The server chooses and materializes the next Songbook entry.
                // Keep the client from pulsing redstone for the old entry while
                // it waits for that authoritative replacement.
                ci.cancel();
            } else if (SheetTiming.hasCanonicalClock(musicBox.getSheetStack())) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void m3e$publishPlaybackTransitions(
            Level level, net.minecraft.core.BlockPos pos, BlockState state,
            TileEntityMusicBox musicBox, CallbackInfo ci) {
        if (!level.isClientSide()) MusicBoxPlaybackServer.observe(musicBox);
    }
}
