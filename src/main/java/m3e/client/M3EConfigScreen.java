package m3e.client;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import m3e.debug.MusicDebugTelemetry;
import m3e.session.DrumNoteMap;

import java.util.ArrayList;

/** Standalone YACL editor for M3E-owned local import and diagnostic settings. */
public final class M3EConfigScreen {
    private M3EConfigScreen() { }

    public static Screen create(Screen parent) {
        int[] pending = DrumNoteMap.validatedArray(InstrumentOptions.drumNoteMap());
        int[] defaults = DrumNoteMap.identity();
        ConfigCategory.Builder drumCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Drum Mapping"));
        drumCategory.option(LabelOption.create(Component.literal(
                "This is stored on this client only. It never changes another player's imports or live styles.")));
        addDrumOptions(drumCategory, pending, defaults);

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Music Maker’s Marvelous Extensions Configuration"))
                .category(instrumentUiCategory())
                .category(sheetAndSongbookCategory())
                .category(drumCategory.build())
                .category(diagnosticsCategory())
                .save(() -> save(pending))
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory instrumentUiCategory() {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Instrument UI"))
                .option(ButtonOption.createBuilder()
                        .name(Component.literal("Edit keymaps"))
                        .text(Component.literal("Open editor"))
                        .action(screen -> Minecraft.getInstance().gui.setScreen(
                                new KeyboardKeymapScreen(screen)))
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(Component.literal("Import custom keymap"))
                        .text(Component.literal("Choose JSON or text"))
                        .action(KeyboardKeymapFiles::chooseImport)
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(Component.literal("Export custom keymap"))
                        .text(Component.literal("Save JSON or text"))
                        .action(screen -> KeyboardKeymapFiles.chooseExport())
                        .build())
                .build();
    }

    private static ConfigCategory sheetAndSongbookCategory() {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Sheet and Songbook"))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Include MIDI file name"))
                        .binding(false, InstrumentOptions::includeMidiName,
                                InstrumentOptions::setIncludeMidiName)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Include MIDI instrument name"))
                        .binding(false, InstrumentOptions::includeMidiInstrumentName,
                                InstrumentOptions::setIncludeMidiInstrumentName)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Include MIDI channel name"))
                        .binding(false, InstrumentOptions::includeMidiChannelName,
                                InstrumentOptions::setIncludeMidiChannelName)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Component.literal("Default import octave"))
                        .binding(0, InstrumentOptions::importOctaveOffset,
                                InstrumentOptions::setImportOctaveOffset)
                        .controller(option -> IntegerSliderControllerBuilder.create(option)
                                .range(-8, 8).step(1)
                                .valueFormatter(value -> Component.literal(
                                        (value >= 0 ? "+" : "") + value + " octave"
                                                + (Math.abs(value) == 1 ? "" : "s"))))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Component.literal("Pitch bend range"))
                        .binding(2, InstrumentOptions::pitchBendRangeSemitones,
                                InstrumentOptions::setPitchBendRangeSemitones)
                        .controller(option -> IntegerSliderControllerBuilder.create(option)
                                .range(1, 12).step(1)
                                .valueFormatter(value -> Component.literal(value + " st")))
                        .build())
                .build();
    }

    private static ConfigCategory diagnosticsCategory() {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Diagnostics"))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Debug music telemetry"))
                        .binding(false, InstrumentOptions::debugTelemetry,
                                InstrumentOptions::setDebugTelemetry)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(LabelOption.create(Component.literal(
                        "Dedicated servers can enable matching server diagnostics with -Dm3e.music.debug=true.")))
                .build();
    }

    private static void addDrumOptions(ConfigCategory.Builder category,
                                       int[] pending, int[] defaults) {
        for (int gmNote = DrumNoteMap.MIN_GM_NOTE;
             gmNote <= DrumNoteMap.MAX_GM_NOTE; gmNote++) {
            int index = DrumNoteMap.index(gmNote);
            category.option(Option.<Integer>createBuilder()
                    .name(Component.literal(gmNote + " " + DrumNoteMap.name(gmNote)))
                    .binding(defaults[index], () -> pending[index], value -> pending[index] = value)
                    .controller(option -> IntegerSliderControllerBuilder.create(option)
                            .range(DrumNoteMap.MIN_MUSIC_MAKER_NOTE,
                                    DrumNoteMap.MAX_MUSIC_MAKER_NOTE)
                            .step(1)
                            .valueFormatter(value -> Component.literal(
                                    "MM " + value + " / drum_kit" + value)))
                    .build());
        }
    }

    private static void save(int[] pending) {
        ArrayList<Integer> mapping = new ArrayList<>(pending.length);
        for (int note : pending) mapping.add(note);
        InstrumentOptions.setDrumNoteMap(java.util.List.copyOf(mapping));
        int[] defaults = DrumNoteMap.identity();
        int customized = 0;
        for (int index = 0; index < pending.length; index++) {
            if (pending[index] != defaults[index]) customized++;
        }
        MusicDebugTelemetry.event("midi_import", "local_drum_map_saved",
                "entries", pending.length, "customized", customized);
    }
}
