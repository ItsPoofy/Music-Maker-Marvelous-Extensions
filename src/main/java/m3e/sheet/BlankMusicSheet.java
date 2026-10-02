package m3e.sheet;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import xerca.xercamusic.common.item.ItemMusicSheet;
import m3e.songbook.SongbookComponents;

import java.util.ArrayList;
import java.util.List;

/** Shared definition and deterministic inventory order for untouched Music Sheets. */
public final class BlankMusicSheet {
    private BlankMusicSheet() { }

    public static boolean isStrictlyBlank(ItemStack stack) {
        return stack.getItem() instanceof ItemMusicSheet && ItemMusicSheet.isEmptySheet(stack)
                && stack.get(SongbookComponents.EXTRACTED_PAGE) == null;
    }

    /** Main hand first, then ordinary inventory order, then offhand. */
    public static List<Integer> orderedSlots(Player player) {
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        ArrayList<Integer> slots = new ArrayList<>();
        if (isStrictlyBlank(inventory.getItem(selected))) slots.add(selected);
        int ordinarySlots = inventory.getNonEquipmentItems().size();
        for (int slot = 0; slot < ordinarySlots; slot++) {
            if (slot != selected && isStrictlyBlank(inventory.getItem(slot))) slots.add(slot);
        }
        if (isStrictlyBlank(inventory.getItem(Inventory.SLOT_OFFHAND))) {
            slots.add(Inventory.SLOT_OFFHAND);
        }
        return List.copyOf(slots);
    }

    public static int capacity(Player player) {
        if (!isStrictlyBlank(player.getMainHandItem())) return 0;
        return orderedSlots(player).size();
    }

    public static int importCapacity(Player player) {
        int physical = capacity(player);
        return importCapacity(player.isCreative(),
                player.getMainHandItem().getItem() instanceof ItemMusicSheet, physical);
    }

    private static int importCapacity(boolean creative, boolean holdingSheet, int physical) {
        if (creative && holdingSheet) return SheetBatchCodec.MAX_ENTRIES;
        return Math.min(physical, SheetBatchCodec.MAX_ENTRIES);
    }
}
