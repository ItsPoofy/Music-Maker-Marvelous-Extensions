package m3e.songbook;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import xerca.xercamusic.common.item.ItemMusicSheet;
import xerca.xercamusic.common.item.Items;

/** Creates a one-page Songbook or adds one completely blank page to an existing book. */
public final class SongbookCapacityRecipe extends CustomRecipe {
    public static final SongbookCapacityRecipe INSTANCE = new SongbookCapacityRecipe();
    public static final MapCodec<SongbookCapacityRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, SongbookCapacityRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    private SongbookCapacityRecipe() { }

    @Override public boolean matches(CraftingInput input, Level level) {
        Input parsed = parse(input);
        if (parsed == null) return false;
        if (parsed.songbook().isEmpty()) return !parsed.vanillaBook().isEmpty();
        SongbookHeader header = parsed.songbook().get(SongbookComponents.HEADER);
        return header != null && header.capacity() < SongbookContents.MAX_CAPACITY;
    }

    @Override public ItemStack assemble(CraftingInput input) {
        Input parsed = parse(input);
        if (parsed == null) return ItemStack.EMPTY;
        if (parsed.songbook().isEmpty()) return SongbookItem.create(1);
        ItemStack result = parsed.songbook().copyWithCount(1);
        SongbookContents contents = result.get(SongbookComponents.CONTENTS);
        if (contents != null && contents.capacity() < SongbookContents.MAX_CAPACITY) {
            SongbookService.write(result, contents.withCapacity(contents.capacity() + 1));
            return result;
        }
        SongbookHeader header = result.get(SongbookComponents.HEADER);
        if (header == null || header.capacity() >= SongbookContents.MAX_CAPACITY) return ItemStack.EMPTY;
        result.set(SongbookComponents.HEADER, new SongbookHeader(header.bookId(), header.revision() + 1,
                header.capacity() + 1, header.songCount(), header.encodedBytes()));
        return result;
    }

    @Override public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SongbookItems.SONGBOOK_CAPACITY_RECIPE;
    }

    private static Input parse(CraftingInput input) {
        ItemStack songbook = ItemStack.EMPTY;
        ItemStack vanillaBook = ItemStack.EMPTY;
        ItemStack blankSheet = ItemStack.EMPTY;
        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getItem(index);
            if (stack.isEmpty()) continue;
            if (stack.is(SongbookItems.SONGBOOK) && songbook.isEmpty() && vanillaBook.isEmpty()) {
                songbook = stack;
            } else if (stack.is(net.minecraft.world.item.Items.BOOK) && vanillaBook.isEmpty() && songbook.isEmpty()) {
                vanillaBook = stack;
            } else if (stack.is(Items.MUSIC_SHEET) && ItemMusicSheet.isEmptySheet(stack)
                    && SongbookService.isCompletelyBlank(stack) && blankSheet.isEmpty()) {
                blankSheet = stack;
            } else {
                return null;
            }
        }
        if (blankSheet.isEmpty() || songbook.isEmpty() == vanillaBook.isEmpty()) return null;
        return new Input(songbook, vanillaBook);
    }

    private record Input(ItemStack songbook, ItemStack vanillaBook) { }
}
