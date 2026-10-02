package m3e.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.Items;

import java.util.ArrayList;
import java.util.List;

final class InstrumentChoiceCatalog {
    private InstrumentChoiceCatalog() { }

    static List<Entry> entries() {
        ArrayList<Entry> result = new ArrayList<>(Items.INSTRUMENTS.size() + 1);
        result.add(new Entry("", "None", null));
        for (IItemInstrument instrument : Items.INSTRUMENTS) {
            if (!(instrument instanceof net.minecraft.world.item.Item item)) continue;
            result.add(new Entry(BuiltInRegistries.ITEM.getKey(item).toString(),
                    new ItemStack(item).getHoverName().getString(), instrument));
        }
        return List.copyOf(result);
    }

    static IItemInstrument resolve(String id) {
        if (id == null || id.isEmpty()) return null;
        return entries().stream().filter(entry -> entry.id().equals(id))
                .map(Entry::instrument).findFirst().orElse(null);
    }

    record Entry(String id, String name, IItemInstrument instrument) { }
}
