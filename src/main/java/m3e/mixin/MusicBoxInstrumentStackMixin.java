package m3e.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xerca.xercamusic.common.block.BlockMusicBox;
import xerca.xercamusic.common.block.Blocks;
import xerca.xercamusic.common.item.ItemInstrument;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.instrument.InstrumentItemComponents;
import m3e.instrument.M3EInstruments;
import m3e.musicbox.MusicBoxInstrumentProfileAccess;
import m3e.musicbox.MusicBoxLibraryAccess;
import m3e.sound.InstrumentSoundProfile;
import m3e.sound.MusicBoxInstrumentProfileServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Captures the exact instrument stack before Music Maker consumes it. */
@Mixin(ItemInstrument.class)
abstract class MusicBoxInstrumentStackMixin {
    @Inject(method = "useInstrumentOn", at = @At("HEAD"))
    private static void m3e$captureInsertedStack(
            UseOnContext context, CallbackInfoReturnable<Boolean> cir) {
        if (context.getLevel().isClientSide()) return;
        var state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.getBlock() != Blocks.MUSIC_BOX
                || state.getValue(BlockMusicBox.HAS_INSTRUMENT)
                || !(context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof TileEntityMusicBox musicBox)) return;
        MusicBoxLibraryAccess library = MusicBoxLibraryAccess.of(musicBox);
        ItemStack source = context.getItemInHand();
        String instanceId = source.getOrDefault(
                InstrumentItemComponents.INSTANCE_ID, "");
        if (instanceId.isBlank()) {
            instanceId = UUID.randomUUID().toString();
            source.set(InstrumentItemComponents.INSTANCE_ID, instanceId);
        }
        ItemStack inserted = source.copyWithCount(1);
        library.m3e$setMusicBoxInstrumentStack(inserted);
        if (musicBox instanceof MusicBoxInstrumentProfileAccess profileAccess) {
            Item item = inserted.getItem();
            int instrumentId = M3EInstruments.protocolId(item);
            InstrumentSoundProfile profile = inserted.getOrDefault(
                    InstrumentItemComponents.SOUND_PROFILE,
                    InstrumentSoundProfile.DEFAULT);
            profileAccess.m3e$setMusicBoxProfile(instrumentId, profile);
        }
    }

    @Inject(method = "useInstrumentOn", at = @At("RETURN"))
    private static void m3e$syncInsertedStack(
            UseOnContext context, CallbackInfoReturnable<Boolean> cir) {
        if (context.getLevel().isClientSide() || !Boolean.TRUE.equals(cir.getReturnValue())
                || !(context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof TileEntityMusicBox musicBox)) return;
        musicBox.setChanged();
        var state = context.getLevel().getBlockState(context.getClickedPos());
        context.getLevel().sendBlockUpdated(context.getClickedPos(), state, state, 3);
        MusicBoxInstrumentProfileServer.broadcast(musicBox,
                context.getPlayer() instanceof ServerPlayer player ? player : null);
    }
}
