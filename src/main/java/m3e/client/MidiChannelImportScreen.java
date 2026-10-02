package m3e.client;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Compatibility facade for the format-neutral score-part import screen. */
final class MidiChannelImportScreen {
    private MidiChannelImportScreen() { }

    static Screen forSheet(Screen parent, MidiSheetFile.Analysis analysis) {
        return new ScorePartImportScreen(new MidiScoreImportAnalysis(analysis),
                ScoreImportDestination.sheet(parent));
    }

    static Screen forInstrumentSongbook(
            Screen parent, MidiSheetFile.Analysis analysis,
            InstrumentMidiScreenAccess instrumentAccess,
            SongbookClientState.ImportTarget target) {
        return new ScorePartImportScreen(new MidiScoreImportAnalysis(analysis),
                ScoreImportDestination.instrumentSongbook(parent, instrumentAccess, target));
    }

    static Screen forSongbook(Screen parent, MidiSheetFile.Analysis analysis,
                              SongbookClientState.ImportTarget target, boolean managed) {
        return new ScorePartImportScreen(new MidiScoreImportAnalysis(analysis),
                ScoreImportDestination.songbook(parent, target, managed));
    }

    static String defaultImportName(MidiSheetFile.Analysis analysis,
                                    List<MidiSheetFile.ChannelSummary> selected,
                                    boolean includeMidiName,
                                    boolean includeMidiInstrumentName,
                                    boolean includeMidiChannelName) {
        return MidiScoreImportAnalysis.defaultImportName(analysis, selected, includeMidiName,
                includeMidiInstrumentName, includeMidiChannelName);
    }
}
