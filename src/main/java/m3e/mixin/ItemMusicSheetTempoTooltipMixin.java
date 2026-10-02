package m3e.mixin;

import m3e.sheet.MusicSheetComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xerca.xercamusic.common.item.ItemMusicSheet;

import java.util.function.Consumer;

/** Shows the MIDI source BPM, never the internal high-resolution fallback BPS. */
@Mixin(ItemMusicSheet.class)
abstract class ItemMusicSheetTempoTooltipMixin {
    @Redirect(method = "appendHoverText",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V", ordinal = 3))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void m3e$showSourceMidiTempo(Consumer tooltip, Object original, ItemStack stack,
                                                     Item.TooltipContext context, TooltipDisplay tooltipDisplay,
                                                     Consumer<Component> originalTooltip, TooltipFlag tooltipFlag) {
        m3e.sheet.MusicScore score = stack.get(MusicSheetComponents.SCORE);
        Integer bpm = score == null ? stack.get(MusicSheetComponents.SOURCE_BPM)
                : score.initialBpm();
        tooltip.accept(bpm == null ? original : Component.translatable("note.tempo", bpm).withStyle(ChatFormatting.GRAY));
    }
}
