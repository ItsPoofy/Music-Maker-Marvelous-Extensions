package m3e.client;

import m3e.sheet.MusicScore;

import java.util.List;

/** Format-neutral selection adapter for a parsed ABC tune. */
final class AbcScoreImportAnalysis implements ScoreImportAnalysis {
    private final AbcFile.AbcTuneAnalysis analysis;

    AbcScoreImportAnalysis(AbcFile.AbcTuneAnalysis analysis) {
        this.analysis = analysis;
    }

    @Override public String sourceName() { return analysis.document().sourceName(); }
    @Override public String formatName() { return "ABC"; }
    @Override public int partCount() { return analysis.voices().size(); }
    @Override public String partLabel(int index) { return analysis.voices().get(index).displayName(); }
    @Override public boolean drumPart(int index) { return false; }

    @Override public MusicScore song(List<Integer> selectedIndexes, List<Integer> drumMap) {
        return analysis.song(selectedIndexes);
    }

    @Override public String defaultName(List<Integer> selectedIndexes, boolean includeSource,
                                        boolean includeInstrument, boolean includePart) {
        return analysis.tune().title();
    }

    @Override public boolean midiOptions() { return false; }

    @Override
    public String separateName(int index, boolean includeSource,
                               boolean includeInstrument, boolean includePart) {
        String title = analysis.tune().title().strip();
        String voice = analysis.voices().get(index).displayName().strip();
        if (title.isEmpty()) return voice.isEmpty() ? "ABC voice" : voice;
        return voice.isEmpty() ? title : title + " · " + voice;
    }
}
