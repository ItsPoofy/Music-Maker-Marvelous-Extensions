package m3e.client;

import m3e.sheet.MusicScore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class MidiScoreImportAnalysis implements ScoreImportAnalysis {
    private final MidiSheetFile.Analysis analysis;
    private final List<MidiSheetFile.ChannelSummary> parts;

    MidiScoreImportAnalysis(MidiSheetFile.Analysis analysis) {
        this.analysis = analysis;
        this.parts = analysis.channels().stream()
                .sorted(Comparator.comparingInt(MidiSheetFile.ChannelSummary::channel)
                        .thenComparingInt(MidiSheetFile.ChannelSummary::trackIndex)
                        .thenComparingInt(MidiSheetFile.ChannelSummary::program))
                .toList();
    }

    @Override public String sourceName() { return analysis.sourceName(); }
    @Override public String formatName() { return "MIDI"; }
    @Override public int partCount() { return parts.size(); }
    @Override public String partLabel(int index) {
        MidiSheetFile.ChannelSummary part = parts.get(index);
        return (index + 1) + " · " + part.channelName() + " · " + part.instrumentName();
    }
    @Override public boolean drumPart(int index) { return parts.get(index).channel() == 9; }
    @Override public String instrumentGroupKey(int index) {
        MidiSheetFile.ChannelSummary part = parts.get(index);
        return (part.channel() == 9 ? "drum:" : "melodic:") + part.program();
    }
    @Override public int instrumentMergeRepresentative(List<Integer> indexes) {
        return baseInstrumentPart(indexes);
    }
    @Override public String instrumentMergeLabel(List<Integer> indexes) {
        int base = baseInstrumentPart(indexes);
        MidiSheetFile.ChannelSummary part = parts.get(base);
        return indexes.size() > 1 && isBaseInstrument(base)
                ? part.channelName() + " · " + part.instrumentName()
                : part.instrumentName();
    }
    @Override public boolean midiOptions() { return true; }

    private int baseInstrumentPart(List<Integer> indexes) {
        return indexes.stream().filter(this::isBaseInstrument).findFirst()
                .orElse(indexes.getFirst());
    }

    private boolean isBaseInstrument(int index) {
        MidiSheetFile.ChannelSummary candidate = parts.get(index);
        long candidateStart = firstNoteTick(candidate);
        for (int other = 0; other < parts.size(); other++) {
            MidiSheetFile.ChannelSummary part = parts.get(other);
            if (part.trackIndex() != candidate.trackIndex() || part.channel() != candidate.channel()) {
                continue;
            }
            long otherStart = firstNoteTick(part);
            if (otherStart < candidateStart || (otherStart == candidateStart && other < index)) return false;
        }
        return true;
    }

    private static long firstNoteTick(MidiSheetFile.ChannelSummary part) {
        return part.notes().isEmpty() ? Long.MAX_VALUE : part.notes().getFirst().startTick();
    }

    @Override
    public List<List<Integer>> initialMergeGroups() {
        record Source(int track, int channel) { }
        Map<Source, List<Integer>> groups = new LinkedHashMap<>();
        for (int index = 0; index < parts.size(); index++) {
            MidiSheetFile.ChannelSummary part = parts.get(index);
            groups.computeIfAbsent(new Source(part.trackIndex(), part.channel()),
                    ignored -> new ArrayList<>()).add(index);
        }
        return groups.values().stream().filter(group -> group.size() > 1)
                .map(List::copyOf).toList();
    }

    @Override
    public MusicScore song(List<Integer> selectedIndexes, List<Integer> drumMap) {
        List<MidiSheetFile.ChannelSummary> selected = selectedIndexes.stream()
                .map(parts::get).toList();
        return analysis.song(selected, drumMap);
    }

    @Override
    public String defaultName(List<Integer> selectedIndexes, boolean includeSource,
                              boolean includeInstrument, boolean includePart) {
        List<MidiSheetFile.ChannelSummary> selected = selectedIndexes.stream()
                .map(parts::get).toList();
        return defaultImportName(analysis, selected, includeSource, includeInstrument, includePart);
    }

    static String defaultImportName(MidiSheetFile.Analysis analysis,
                                    List<MidiSheetFile.ChannelSummary> selected,
                                    boolean includeMidiName,
                                    boolean includeMidiInstrumentName,
                                    boolean includeMidiChannelName) {
        List<String> parts = new ArrayList<>(3);
        if (includeMidiName) parts.add(stripMidiExtension(analysis.sourceName()));
        if (!selected.isEmpty() && includeMidiInstrumentName) {
            long instrumentCount = selected.stream()
                    .map(part -> (part.channel() == 9 ? "drum:" : "melodic:") + part.program())
                    .distinct().count();
            parts.add(instrumentCount == 1 ? selected.getFirst().instrumentName()
                    : instrumentCount + " instruments");
        }
        if (!selected.isEmpty() && includeMidiChannelName) {
            parts.add(selected.size() == 1 ? selected.getFirst().channelName()
                    : selected.size() + " tracks");
        }
        return parts.isEmpty() ? "Imported MIDI" : String.join(" · ", parts);
    }

    private static String stripMidiExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".midi")) return name.substring(0, name.length() - 5);
        if (lower.endsWith(".mid")) return name.substring(0, name.length() - 4);
        return name;
    }
}
