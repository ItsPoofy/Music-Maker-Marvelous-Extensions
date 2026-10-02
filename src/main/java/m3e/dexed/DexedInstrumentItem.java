package m3e.dexed;

import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerPlayer;
import xerca.xercamusic.common.item.ItemInstrument;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.musicbox.MusicBoxDexedPatchAccess;
import m3e.sound.MusicBoxInstrumentProfileServer;

public final class DexedInstrumentItem extends ItemInstrument {
    public DexedInstrumentItem(int instrumentId, Item.Properties properties) {
        super(instrumentId, 0, 7, properties.stacksTo(1)
                .component(DexedComponents.PATCH, DexedPatch.initVoice())
                .component(DexedComponents.PERFORMANCE, DexedPerformance.DEFAULT));
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        DexedPatch patch = context.getItemInHand().getOrDefault(
                DexedComponents.PATCH, DexedPatch.initVoice());
        DexedPerformance performance = context.getItemInHand().getOrDefault(
                DexedComponents.PERFORMANCE, DexedPerformance.DEFAULT);
        if (!ItemInstrument.useInstrumentOn(context)) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide()
                && context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof TileEntityMusicBox musicBox
                && musicBox instanceof MusicBoxDexedPatchAccess access) {
            access.m3e$setMusicBoxDexedPatch(patch);
            access.m3e$setMusicBoxDexedPerformance(performance);
            musicBox.setChanged();
            MusicBoxInstrumentProfileServer.broadcast(musicBox,
                    context.getPlayer() instanceof ServerPlayer player ? player : null);
        }
        return InteractionResult.SUCCESS;
    }
}
