package m3e.client;

import net.minecraft.core.BlockPos;
import m3e.network.MusicBoxLabelPacket;

import java.util.HashMap;
import java.util.Map;

/** Client-only titles received alongside Music Maker's compact music-box sync. */
public final class MusicBoxContentsLabelCache {
    private static final Map<BlockPos, String> TITLES = new HashMap<>();

    private MusicBoxContentsLabelCache() {
    }

    public static void receive(MusicBoxLabelPacket packet) {
        if (packet.sheetTitle().isBlank()) {
            TITLES.remove(packet.pos());
        } else {
            TITLES.put(packet.pos().immutable(), packet.sheetTitle());
        }
    }

    public static String titleAt(BlockPos pos) {
        return TITLES.getOrDefault(pos, "");
    }

    public static void clear() {
        TITLES.clear();
    }
}
