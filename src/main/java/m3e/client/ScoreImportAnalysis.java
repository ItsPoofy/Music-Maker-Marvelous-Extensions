package m3e.client;

import m3e.sheet.MusicScore;

import java.util.List;

interface ScoreImportAnalysis {
    String sourceName();
    String formatName();
    int partCount();
    String partLabel(int index);
    boolean drumPart(int index);
    default String instrumentGroupKey(int index) { return ""; }
    default int instrumentMergeRepresentative(List<Integer> indexes) { return indexes.getFirst(); }
    default String instrumentMergeLabel(List<Integer> indexes) { return ""; }
    default List<List<Integer>> initialMergeGroups() { return List.of(); }
    MusicScore song(List<Integer> selectedIndexes, List<Integer> drumMap);
    default int initialBpm() {
        return partCount() == 0 ? 120 : song(List.of(0), List.of()).initialBpm();
    }
    String defaultName(List<Integer> selectedIndexes, boolean includeSource,
                       boolean includeInstrument, boolean includePart);
    default String separateName(int index, boolean includeSource,
                                boolean includeInstrument, boolean includePart) {
        return defaultName(List.of(index), includeSource, includeInstrument, includePart);
    }
    boolean midiOptions();
}
