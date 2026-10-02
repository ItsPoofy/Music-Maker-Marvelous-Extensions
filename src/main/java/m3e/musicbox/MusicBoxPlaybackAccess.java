package m3e.musicbox;

import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;

/** Server-authoritative transport state attached to one Music Maker music box. */
public interface MusicBoxPlaybackAccess {
    /** Required mixin attachment for every Music Maker music-box block entity. */
    static MusicBoxPlaybackAccess of(TileEntityMusicBox musicBox) {
        return (MusicBoxPlaybackAccess) musicBox;
    }

    boolean m3e$isMusicBoxPlaying();

    void m3e$setMusicBoxPlaying(boolean playing);

    int m3e$musicBoxLoopMode();

    void m3e$setMusicBoxLoopMode(int mode);

    boolean m3e$isMusicBoxShuffle();

    void m3e$setMusicBoxShuffle(boolean shuffle);

    float m3e$musicBoxPlaybackSpeed();

    void m3e$setMusicBoxPlaybackSpeed(float speed);

    int m3e$musicBoxPlaybackTempoBpm();

    void m3e$setMusicBoxPlaybackTempoBpm(int tempoBpm);

    int m3e$musicBoxPlaybackTranspose();

    void m3e$setMusicBoxPlaybackTranspose(int semitones);

    void m3e$restartMusicBoxLoop();
}
