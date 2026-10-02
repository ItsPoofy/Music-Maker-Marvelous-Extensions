package m3e.mixin.client;

import m3e.client.MusicSheetClientCache;
import m3e.client.MusicSheetSoundRunner;
import m3e.client.DexedMusicBoxSoundRunner;
import m3e.client.MusicBoxPlaybackControls;
import m3e.client.DexedSpiritSoundRunner;
import m3e.instrument.M3EInstruments;
import m3e.sheet.MusicScore;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.client.SoundController;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;

import java.util.List;

@Mixin(SoundController.class)
abstract class SoundControllerMusicSheetMixin {
    @Shadow private List<NoteEvent> notes;
    @Shadow private List<VolumeMarker> volumeMarkers;
    @Shadow private IItemInstrument instrument;
    @Shadow private byte bps;
    @Shadow private int spiritID;
    @Shadow private float volume;
    @Shadow private volatile boolean doStop;
    @Shadow private volatile double x;
    @Shadow private volatile double y;
    @Shadow private volatile double z;
    @Shadow private @Nullable TileEntityMusicBox musicBox;

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void m3e$playExactMidiTimeline(CallbackInfo ci) {
        MusicScore song = musicBox == null
                ? MusicSheetClientCache.findBound(notes, bps & 0xFF)
                : MusicSheetClientCache.resolve(musicBox.getSheetStack());
        MusicBoxPlaybackControls.State playback = musicBox == null ? null
                : MusicBoxPlaybackControls.state(musicBox);
        long requestedOffset = musicBox == null ? 0L
                : MusicBoxPlaybackControls.consumeStartOffset(musicBox.getBlockPos());
        long startOffsetMicros = musicBox == null ? 0L
                : requestedOffset >= 0L ? requestedOffset : playback.playheadMicros();
        double playbackRate = playback == null ? 1.0D
                : MusicBoxPlaybackControls.timing(musicBox).effectiveRate(
                playback.speed(), playback.tempoBpm());
        int transpose = playback == null ? 0 : playback.transpose();
        if (song == null) return;
        if (musicBox == null && M3EInstruments.isDexed(instrument)) {
            DexedSpiritSoundRunner.run(song, volume, spiritID,
                    () -> doStop, () -> x, () -> y, () -> z);
            ci.cancel();
        } else if (musicBox != null && M3EInstruments.isDexed(instrument)) {
            DexedMusicBoxSoundRunner.run(song, volume, musicBox,
                    startOffsetMicros, playbackRate, transpose,
                    () -> doStop, () -> x, () -> y, () -> z);
            ci.cancel();
        } else {
            MusicSheetSoundRunner.run(song, instrument, volume, musicBox, spiritID,
                    startOffsetMicros, playbackRate, transpose,
                    () -> doStop, () -> x, () -> y, () -> z);
            ci.cancel();
        }
    }
}
