package m3e.instrument;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.ItemInstrument;
import xerca.xercamusic.common.item.Items;
import m3e.M3E;
import m3e.dexed.DexedInstrumentItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class M3EInstruments {
    public static final String DEXED_PATH = "redstone_synthesizer";
    public static final String REDSTONE_DRUM_KIT_PATH = "redstone_drum_kit";
    private static final int[] SOFT_PIANO_ROOTS = {
            24, 30, 36, 42, 48, 54, 60, 66,
            72, 78, 84, 90, 96, 102, 108
    };
    private static final int[] FLUTE_ROOTS = {60, 66, 72, 78, 84, 90};
    private static final int[] OBOE_ROOTS = {58, 59, 60, 66, 72, 78, 84, 90};

    private static DexedInstrumentItem dexed;
    private static ItemInstrument redstoneDrumKit;
    private static final Map<VariationKey, VariationBank> VARIATION_BANKS =
            new HashMap<>();
    private static List<IItemInstrument.Pair<Integer, SoundEvent>> fluteSounds = List.of();
    private static List<IItemInstrument.Pair<Integer, SoundEvent>> oboeSounds = List.of();

    private M3EInstruments() {
    }

    public static void register() {
        if (dexed != null) return;

        dexed = registerDexed();
        redstoneDrumKit = registerRedstoneDrumKit();
        fluteSounds = registerSounds("flute", FLUTE_ROOTS);
        oboeSounds = registerSounds("oboe", OBOE_ROOTS);
        List<IItemInstrument.Pair<Integer, SoundEvent>> softPianoSounds =
                registerSounds("piano_soft_piano", SOFT_PIANO_ROOTS);
        for (Item piano : List.of(Items.PIANO, Items.REDSTONE_PIANO)) {
            int pianoId = protocolId(piano);
            if (pianoId < 0) throw new IllegalStateException("Music Maker piano is not registered");
            registerDefaultVariation(pianoId, "Default");
            registerVariation(pianoId, "soft_piano", "Soft Piano",
                    IItemInstrument.MIN_NOTE, highestPlayableNote(), softPianoSounds);
        }
        applyImportedReplacements();

        ResourceKey<CreativeModeTab> musicTab = ResourceKey.create(
                Registries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath("xercamusic", "music_tab")
        );
        CreativeModeTabEvents.modifyOutputEvent(musicTab).register(output -> {
            output.accept(dexed);
            output.accept(redstoneDrumKit);
        });
        M3E.LOGGER.info(
                "Registered Redstone Synthesizer and Redstone Drum Kit as Music Maker instruments {} and {}; replaced Flute and Oboe sample banks",
                dexed.getInstrumentId(),
                redstoneDrumKit.getInstrumentId());
    }

    private static DexedInstrumentItem registerDexed() {
        Identifier itemId = M3E.id(DEXED_PATH);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, itemId);
        int instrumentId = Items.INSTRUMENTS.size();
        DexedInstrumentItem instrument = new DexedInstrumentItem(
                instrumentId, new Item.Properties().setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemId, instrument);
        Identifier soundId = M3E.id("dexed_silent");
        SoundEvent sound = SoundEvent.createVariableRangeEvent(soundId);
        Registry.register(BuiltInRegistries.SOUND_EVENT, soundId, sound);
        instrument.setSounds(List.of(IItemInstrument.Pair.of(60, sound)));
        Items.INSTRUMENTS.add(instrument);
        return instrument;
    }

    private static ItemInstrument registerRedstoneDrumKit() {
        Identifier itemId = M3E.id(REDSTONE_DRUM_KIT_PATH);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, itemId);
        int instrumentId = Items.INSTRUMENTS.size();
        ItemInstrument instrument = new ItemInstrument(
                instrumentId, 0, 7, new Item.Properties().setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemId, instrument);
        configureSampleRange(instrument, 35, 87);
        List<IItemInstrument.Pair<Integer, SoundEvent>> defaultSounds = List.of();
        for (String variation : List.of("909", "78", "707", "808")) {
            List<IItemInstrument.Pair<Integer, SoundEvent>> sounds =
                    registerSounds(REDSTONE_DRUM_KIT_PATH + "_" + variation, 35, 87);
            registerVariation(instrumentId, variation, variation, 35, 87, sounds);
            if ("909".equals(variation)) defaultSounds = sounds;
        }
        instrument.setSounds(defaultSounds);
        Items.INSTRUMENTS.add(instrument);
        return instrument;
    }

    private static List<IItemInstrument.Pair<Integer, SoundEvent>> registerSounds(
            String soundPath, int minNote, int maxNote) {
        List<IItemInstrument.Pair<Integer, SoundEvent>> sounds =
                new ArrayList<>(maxNote - minNote + 1);
        for (int note = minNote; note <= maxNote; note++) {
            Identifier soundId = M3E.id(soundPath + "_" + note);
            SoundEvent sound = SoundEvent.createVariableRangeEvent(soundId);
            Registry.register(BuiltInRegistries.SOUND_EVENT, soundId, sound);
            sounds.add(IItemInstrument.Pair.of(note, sound));
        }
        return List.copyOf(sounds);
    }

    private static void replaceInstrumentSamples(ItemInstrument instrument, int minNote, int maxNote,
                                                 List<IItemInstrument.Pair<Integer, SoundEvent>> sounds) {
        configureSampleRange(instrument, minNote, maxNote);
        instrument.setSounds(sounds);
    }

    public static void applyImportedReplacements() {
        if (fluteSounds.isEmpty() || oboeSounds.isEmpty()) return;
        replaceInstrumentSamples((ItemInstrument) Items.FLUTE, 60, 93, fluteSounds);
        replaceInstrumentSamples((ItemInstrument) Items.OBOE, 58, 90, oboeSounds);
    }

    private static void configureSampleRange(ItemInstrument instrument, int minNote, int maxNote) {
        ((InstrumentSampleRangeAccess) instrument).m3e$setSampleRange(minNote, maxNote);
    }

    private static int highestPlayableNote() {
        return IItemInstrument.MIN_NOTE + IItemInstrument.TOTAL_NOTES - 1;
    }

    private static List<IItemInstrument.Pair<Integer, SoundEvent>> registerSounds(String soundPath, int[] roots) {
        List<IItemInstrument.Pair<Integer, SoundEvent>> sounds = new ArrayList<>(roots.length);
        for (int root : roots) {
            Identifier soundId = M3E.id(soundPath + "_" + root);
            SoundEvent sound = SoundEvent.createVariableRangeEvent(soundId);
            Registry.register(BuiltInRegistries.SOUND_EVENT, soundId, sound);
            sounds.add(IItemInstrument.Pair.of(root, sound));
        }
        return sounds;
    }

    public static DexedInstrumentItem dexed() {
        if (dexed == null) throw new IllegalStateException("M3E instruments have not been registered");
        return dexed;
    }

    public static ItemInstrument redstoneDrumKit() {
        if (redstoneDrumKit == null) throw new IllegalStateException("M3E instruments have not been registered");
        return redstoneDrumKit;
    }

    private static void registerDefaultVariation(int instrumentId, String label) {
        InstrumentVariations.register(instrumentId, InstrumentVariations.DEFAULT_ID, label);
    }

    private static void registerVariation(int instrumentId, String variationId, String label,
                                          int minNote, int maxNote,
                                          List<IItemInstrument.Pair<Integer, SoundEvent>> sounds) {
        InstrumentVariations.register(instrumentId, variationId, label);
        VARIATION_BANKS.put(new VariationKey(instrumentId, variationId),
                new VariationBank(minNote, maxNote, sounds));
    }

    public static IItemInstrument.InsSound variationSound(int instrumentId,
                                                           String variationId, int note) {
        VariationBank bank = VARIATION_BANKS.get(new VariationKey(instrumentId, variationId));
        if (bank == null || note < bank.minNote || note > bank.maxNote) return null;
        IItemInstrument.Pair<Integer, SoundEvent> root = bank.sounds.get(
                ItemInstrument.getClosest(note, bank.sounds));
        return new IItemInstrument.InsSound(root.second(),
                (float) Math.pow(1.05946314465679D, note - root.first()));
    }

    public static boolean isDexed(IItemInstrument instrument) {
        return dexed != null && instrument == dexed;
    }

    public static boolean isRedstoneDrumKit(IItemInstrument instrument) {
        return redstoneDrumKit != null && instrument == redstoneDrumKit;
    }

    public static boolean isRedstoneDrumKit(int instrumentId) {
        return redstoneDrumKit != null && instrumentId == redstoneDrumKit.getInstrumentId();
    }

    public static int protocolId(Item item) {
        if (!(item instanceof IItemInstrument instrument)) return -1;
        int id = instrument.getInstrumentId();
        return id >= 0 && id < Items.INSTRUMENTS.size() && Items.INSTRUMENTS.get(id) == instrument
                ? id : -1;
    }

    private record VariationKey(int instrumentId, String variationId) { }

    private record VariationBank(int minNote, int maxNote,
                                 List<IItemInstrument.Pair<Integer, SoundEvent>> sounds) { }
}
