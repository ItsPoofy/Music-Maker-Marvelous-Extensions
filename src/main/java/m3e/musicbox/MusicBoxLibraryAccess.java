package m3e.musicbox;

import net.minecraft.world.item.ItemStack;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;

/** Server-owned exact stacks and selection attached to a Music Maker music box. */
public interface MusicBoxLibraryAccess {
    /** Required mixin attachment for every Music Maker music-box block entity. */
    static MusicBoxLibraryAccess of(TileEntityMusicBox musicBox) {
        return (MusicBoxLibraryAccess) musicBox;
    }

    ItemStack m3e$musicBoxSongbook();

    void m3e$setMusicBoxSongbook(ItemStack stack);

    int m3e$musicBoxSongIndex();

    void m3e$setMusicBoxSongIndex(int index);

    ItemStack m3e$musicBoxInstrumentStack();

    void m3e$setMusicBoxInstrumentStack(ItemStack stack);
}
