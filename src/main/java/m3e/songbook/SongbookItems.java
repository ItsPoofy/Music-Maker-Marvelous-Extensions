package m3e.songbook;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import m3e.M3E;

public final class SongbookItems {
    public static final SongbookItem SONGBOOK = new SongbookItem(new Item.Properties().setId(
            ResourceKey.create(Registries.ITEM, M3E.id("songbook"))));
    public static final RecipeSerializer<SongbookCapacityRecipe> SONGBOOK_CAPACITY_RECIPE =
            new RecipeSerializer<>(SongbookCapacityRecipe.MAP_CODEC, SongbookCapacityRecipe.STREAM_CODEC);

    private SongbookItems() { }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, M3E.id("songbook"), SONGBOOK);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                M3E.id("crafting_special_songbook_capacity"), SONGBOOK_CAPACITY_RECIPE);
        ResourceKey<CreativeModeTab> musicTab = ResourceKey.create(
                Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("xercamusic", "music_tab"));
        CreativeModeTabEvents.modifyOutputEvent(musicTab).register(output -> output.accept(SongbookItem.create(1)));
    }
}
