package m3e.sound;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import xerca.xercamusic.common.item.Items;

/** Shared server-side authorization for handheld instrument control packets. */
final class HeldInstrumentAuthorization {
    private HeldInstrumentAuthorization() { }

    static boolean holds(ServerPlayer player, int instrumentId) {
        if (instrumentId < 0 || instrumentId >= Items.INSTRUMENTS.size()) return false;
        Object instrument = Items.INSTRUMENTS.get(instrumentId);
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() == instrument) return true;
        }
        return false;
    }
}
