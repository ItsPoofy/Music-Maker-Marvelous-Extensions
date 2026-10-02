package m3e.client;

import net.minecraft.client.Minecraft;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;

/** Shared lifetime check for sound-controller threads retaining a music-box reference. */
final class MusicBoxPlaybackValidity {
    private MusicBoxPlaybackValidity() { }

    static boolean present(Minecraft minecraft, TileEntityMusicBox musicBox) {
        return minecraft.level != null && !musicBox.isRemoved()
                && minecraft.level.hasChunkAt(musicBox.getBlockPos())
                && minecraft.level.getBlockEntity(musicBox.getBlockPos()) == musicBox;
    }
}
