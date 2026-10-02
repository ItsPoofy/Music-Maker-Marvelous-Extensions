package m3e.songbook;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import xerca.xercamusic.common.item.ItemMusicSheet;
import xerca.xercamusic.common.block.BlockMusicBox;
import xerca.xercamusic.common.block.Blocks;
import m3e.network.SongbookOpenPacket;
import m3e.sound.MusicBoxLibraryServer;

import java.util.function.Consumer;

public final class SongbookItem extends Item {
    SongbookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack create(int capacity) {
        ItemStack stack = new ItemStack(SongbookItems.SONGBOOK);
        SongbookContents contents = SongbookContents.create(capacity);
        SongbookService.write(stack, contents);
        return stack;
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (player.getItemInHand(other).getItem() instanceof ItemMusicSheet) {
            if (!level.isClientSide()) SongbookService.insertHeldSheet(player, hand, other);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            SongbookHeader header = player.getItemInHand(hand).get(SongbookComponents.HEADER);
            if (header != null) ServerPlayNetworking.send(serverPlayer,
                    new SongbookOpenPacket(header.bookId()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        var state = level.getBlockState(context.getClickedPos());
        if (state.getBlock() != Blocks.MUSIC_BOX
                || !state.hasProperty(BlockMusicBox.HAS_MUSIC)
                || state.getValue(BlockMusicBox.HAS_MUSIC)) return InteractionResult.PASS;
        if (!level.isClientSide() && context.getPlayer() instanceof ServerPlayer player
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && MusicBoxLibraryServer.insert(player, serverLevel,
                context.getClickedPos(), context.getItemInHand())
                && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                          Consumer<Component> tooltip, TooltipFlag flag) {
        SongbookHeader header = stack.get(SongbookComponents.HEADER);
        if (header == null) {
            tooltip.accept(Component.literal("Invalid Songbook data").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.accept(Component.literal(header.songCount() + " / " + header.capacity() + " songs")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal((header.encodedBytes() + 1023) / 1024 + " / 1024 KiB")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
