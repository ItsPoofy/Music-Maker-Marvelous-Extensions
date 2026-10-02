package m3e.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashSet;

/** Selection, grouping, and per-part overrides independent of the import screen. */
final class ScoreImportSession {
    private final ScoreImportAnalysis analysis;
    ScoreImportSession(ScoreImportAnalysis analysis) { this.analysis = analysis; }
    final Set<Integer> selectedPartIndexes = new LinkedHashSet<>();
    final Map<Integer, Integer> partOctaves = new HashMap<>();
    final Map<Integer, Integer> partTempos = new HashMap<>();
    final Map<Integer, String> partInstrumentIds = new HashMap<>();
    final Map<Integer, String> partNames = new HashMap<>();
    final Map<Integer, MergeState> merges = new HashMap<>();
    final Set<Integer> localOctaveParts = new LinkedHashSet<>();
    final Set<Integer> localTempoParts = new LinkedHashSet<>();
    int nextMergeNumber = 1;
    List<Integer> visiblePartIndexes() {
        LinkedHashSet<Integer> hidden = new LinkedHashSet<>();
        for (var entry : merges.entrySet()) {
            for (int member : entry.getValue().members) {
                if (member != entry.getKey()) hidden.add(member);
            }
        }
        ArrayList<Integer> visible = new ArrayList<>();
        for (int index = 0; index < analysis.partCount(); index++) {
            if (!hidden.contains(index)) visible.add(index);
        }
        return List.copyOf(visible);
    }

    List<Integer> groupParts(int partIndex) {
        MergeState merge = merges.get(partIndex);
        return merge == null ? List.of(partIndex) : merge.members;
    }

    boolean expandableMerge(int partIndex) {
        MergeState merge = merges.get(partIndex);
        return merge != null && merge.members.size() > 1;
    }

    int groupRepresentative(int partIndex) {
        if (merges.containsKey(partIndex)) return partIndex;
        for (var entry : merges.entrySet()) {
            if (entry.getValue().members.contains(partIndex)) return entry.getKey();
        }
        return partIndex;
    }

    List<Integer> selectedGroupParts(int partIndex) {
        MergeState merge = merges.get(partIndex);
        if (merge == null) return List.of(partIndex);
        return merge.members.stream().filter(merge.selectedMembers::contains).toList();
    }

    void unmergeMembers(int representative, Set<Integer> removedMembers) {
        MergeState merge = merges.remove(representative);
        if (merge == null) return;
        LinkedHashSet<Integer> removed = merge.members.stream()
                .filter(removedMembers::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (removed.isEmpty()) {
            merges.put(representative, merge);
            return;
        }

        List<Integer> remaining = merge.members.stream().filter(member -> !removed.contains(member)).toList();
        String name = partNames.remove(representative);
        String instrument = partInstrumentIds.remove(representative);
        Integer tempo = partTempos.remove(representative);
        boolean localTempo = localTempoParts.remove(representative);
        selectedPartIndexes.remove(representative);

        for (int member : removed) {
            restoreUnmergedSettings(member, name, instrument, tempo, localTempo);
            if (merge.selectedMembers.contains(member)) selectedPartIndexes.add(member);
        }
        if (remaining.size() == 1) {
            int member = remaining.getFirst();
            restoreUnmergedSettings(member, name, instrument, tempo, localTempo);
            if (merge.selectedMembers.contains(member)) selectedPartIndexes.add(member);
        } else if (remaining.size() > 1) {
            int nextRepresentative = remaining.getFirst();
            LinkedHashSet<Integer> selected = remaining.stream().filter(merge.selectedMembers::contains)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            merges.put(nextRepresentative, new MergeState(merge.number, remaining, selected,
                    merge.sourceChannel, merge.instrumentGroup, merge.expanded));
            restoreUnmergedSettings(nextRepresentative, name, instrument, tempo, localTempo);
            if (selected.size() == remaining.size()) selectedPartIndexes.add(nextRepresentative);
        }
    }

    void restoreUnmergedSettings(int part, String name, String instrument,
                                         Integer tempo, boolean localTempo) {
        if (name != null) partNames.put(part, name);
        if (instrument != null) partInstrumentIds.put(part, instrument);
        if (tempo != null) partTempos.put(part, tempo);
        if (localTempo) localTempoParts.add(part);
    }

    int mergeGroups(LinkedHashSet<Integer> focusedGroups) {
        LinkedHashSet<Integer> members = new LinkedHashSet<>();
        for (int focused : focusedGroups) members.addAll(groupParts(focused));
        if (members.size() < 2) return -1;
        boolean drums = analysis.drumPart(members.getFirst());
        if (members.stream().anyMatch(member -> analysis.drumPart(member) != drums)) return -1;

        Set<Integer> effectiveSelected = new LinkedHashSet<>(effectiveSelectedIndexes());
        boolean included = focusedGroups.stream().anyMatch(effectiveSelected::contains);
        LinkedHashSet<Integer> includedMembers = new LinkedHashSet<>();
        for (int focused : focusedGroups) {
            if (effectiveSelected.contains(focused)) includedMembers.addAll(selectedGroupParts(focused));
        }
        int settingsSource = focusedGroups.getFirst();
        String name = partNames.get(settingsSource);
        String instrument = partInstrumentIds.get(settingsSource);
        Integer tempo = partTempos.get(settingsSource);
        boolean localTempo = localTempoParts.contains(settingsSource);
        for (int focused : focusedGroups) {
            merges.remove(focused);
            partNames.remove(focused);
            partInstrumentIds.remove(focused);
            partTempos.remove(focused);
            localTempoParts.remove(focused);
        }
        int representative = members.stream().mapToInt(Integer::intValue).min().orElseThrow();
        List<Integer> group = members.stream().sorted().toList();
        merges.put(representative, new MergeState(nextMergeNumber++, group,
                included ? includedMembers : new LinkedHashSet<>(group), false));
        if (name != null) partNames.put(representative, name);
        if (instrument != null) partInstrumentIds.put(representative, instrument);
        if (tempo != null) partTempos.put(representative, tempo);
        if (localTempo) localTempoParts.add(representative);
        selectedPartIndexes.removeAll(group);
        if (included && includedMembers.size() == group.size()) selectedPartIndexes.add(representative);
        return representative;
    }

    void restoreGroupSettings(int representative, int source,
                                      Map<Integer, String> names,
                                      Map<Integer, String> instruments,
                                      Map<Integer, Integer> tempos,
                                      Set<Integer> tempoOverrides) {
        if (names.containsKey(source)) partNames.put(representative, names.get(source));
        if (instruments.containsKey(source)) {
            partInstrumentIds.put(representative, instruments.get(source));
        }
        if (tempoOverrides.contains(source)) {
            localTempoParts.add(representative);
            partTempos.put(representative, tempos.get(source));
        }
    }

    boolean hasInstrumentMergeCandidates() {
        if (merges.values().stream().anyMatch(merge -> !merge.instrumentGroup)) return true;
        Map<String, Set<Integer>> roots = new HashMap<>();
        for (int part = 0; part < analysis.partCount(); part++) {
            String key = analysis.instrumentGroupKey(part);
            if (key.isBlank()) continue;
            roots.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                    .add(groupRepresentative(part));
        }
        return roots.values().stream().anyMatch(group -> group.size() > 1);
    }

    void restoreInitialMerges() {
        merges.clear();
        nextMergeNumber = 1;
        for (List<Integer> source : analysis.initialMergeGroups()) {
            List<Integer> members = source.stream().distinct().sorted()
                    .filter(index -> index >= 0 && index < analysis.partCount()).toList();
            if (members.size() < 2) continue;
            int representative = members.getFirst();
            merges.put(representative, new MergeState(nextMergeNumber++, members,
                    new LinkedHashSet<>(), true));
        }
    }

    List<Integer> selectedIndexes() {
        return effectiveSelectedIndexes().stream().sorted()
                .filter(index -> index >= 0 && index < analysis.partCount()).toList();
    }

    List<Integer> effectiveSelectedIndexes() {
        LinkedHashSet<Integer> selected = new LinkedHashSet<>(selectedPartIndexes);
        for (var entry : merges.entrySet()) {
            if (!entry.getValue().selectedMembers.isEmpty()) selected.add(entry.getKey());
        }
        return selected.stream().sorted().toList();
    }

    List<Integer> expandedIndexes(List<Integer> representatives) {
        return representatives.stream().flatMap(index -> selectedGroupParts(index).stream())
                .distinct().sorted().toList();
    }

    void mergeInstruments(boolean separateMode, int tempoBpm, int octaveOffset) {
        if (!separateMode) return;
        Map<String, LinkedHashSet<Integer>> byInstrument = new java.util.LinkedHashMap<>();
        for (int part = 0; part < analysis.partCount(); part++) {
            String key = analysis.instrumentGroupKey(part);
            if (!key.isBlank()) byInstrument.computeIfAbsent(key,
                    ignored -> new LinkedHashSet<>()).add(part);
        }
        if (byInstrument.isEmpty()) return;

        Set<Integer> selectedParts = new LinkedHashSet<>();
        for (int representative : effectiveSelectedIndexes()) {
            selectedParts.addAll(selectedGroupParts(representative));
        }
        Map<Integer, String> names = new HashMap<>();
        Map<Integer, String> instruments = new HashMap<>();
        Map<Integer, Integer> tempos = new HashMap<>();
        Set<Integer> tempoOverrides = new LinkedHashSet<>();
        Map<Integer, Integer> octaves = new HashMap<>();
        Set<Integer> octaveOverrides = new LinkedHashSet<>();
        for (int part = 0; part < analysis.partCount(); part++) {
            int representative = groupRepresentative(part);
            if (partNames.containsKey(representative)) names.put(part, partNames.get(representative));
            if (partInstrumentIds.containsKey(representative)) {
                instruments.put(part, partInstrumentIds.get(representative));
            }
            if (localTempoParts.contains(representative)) {
                tempoOverrides.add(part);
                tempos.put(part, partTempos.getOrDefault(representative, tempoBpm));
            }
            int octaveSource = localOctaveParts.contains(part) ? part
                    : localOctaveParts.contains(representative) ? representative : -1;
            if (octaveSource >= 0) {
                octaveOverrides.add(part);
                octaves.put(part, partOctaves.getOrDefault(octaveSource, octaveOffset));
            }
        }

        merges.clear();
        selectedPartIndexes.clear();
        partNames.clear();
        partInstrumentIds.clear();
        partTempos.clear();
        localTempoParts.clear();
        partOctaves.clear();
        localOctaveParts.clear();
        nextMergeNumber = 1;
        Set<Integer> flattened = new LinkedHashSet<>();
        for (LinkedHashSet<Integer> members : byInstrument.values()) {
            List<Integer> group = members.stream().sorted().toList();
            int representative = analysis.instrumentMergeRepresentative(group);
            LinkedHashSet<Integer> selected = group.stream().anyMatch(selectedParts::contains)
                    ? new LinkedHashSet<>(group) : new LinkedHashSet<>();
            merges.put(representative, new MergeState(nextMergeNumber++, group, selected,
                    false, true));
            if (selected.size() == group.size()) selectedPartIndexes.add(representative);
            restoreGroupSettings(representative, representative, names, instruments,
                    tempos, tempoOverrides);
            flattened.addAll(group);
        }
        for (List<Integer> source : analysis.initialMergeGroups()) {
            List<Integer> remaining = source.stream().filter(part -> !flattened.contains(part))
                    .distinct().sorted().toList();
            if (remaining.size() < 2) continue;
            int representative = remaining.getFirst();
            LinkedHashSet<Integer> selected = remaining.stream().filter(selectedParts::contains)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            merges.put(representative, new MergeState(nextMergeNumber++, remaining, selected, true));
            if (selected.size() == remaining.size()) selectedPartIndexes.add(representative);
            restoreGroupSettings(representative, remaining.getFirst(), names, instruments,
                    tempos, tempoOverrides);
        }
        for (int part = 0; part < analysis.partCount(); part++) {
            if (merges.containsKey(part) || groupRepresentative(part) != part) continue;
            if (selectedParts.contains(part)) selectedPartIndexes.add(part);
            restoreGroupSettings(part, part, names, instruments, tempos, tempoOverrides);
        }
        for (int part : octaveOverrides) {
            localOctaveParts.add(part);
            partOctaves.put(part, octaves.get(part));
        }
    }
    static final class MergeState {
        final int number;
        final List<Integer> members;
        final LinkedHashSet<Integer> selectedMembers;
        final boolean sourceChannel;
        final boolean instrumentGroup;
        boolean expanded;

        MergeState(int number, List<Integer> members,
                           LinkedHashSet<Integer> selectedMembers,
                           boolean sourceChannel) {
            this(number, members, selectedMembers, sourceChannel, false);
        }

        MergeState(int number, List<Integer> members,
                           LinkedHashSet<Integer> selectedMembers,
                           boolean sourceChannel, boolean instrumentGroup) {
            this(number, members, selectedMembers, sourceChannel, instrumentGroup, false);
        }

        MergeState(int number, List<Integer> members,
                           LinkedHashSet<Integer> selectedMembers,
                           boolean sourceChannel, boolean instrumentGroup, boolean expanded) {
            this.number = number;
            this.members = List.copyOf(members);
            this.selectedMembers = new LinkedHashSet<>(selectedMembers);
            this.sourceChannel = sourceChannel;
            this.instrumentGroup = instrumentGroup;
            this.expanded = expanded;
        }
    }
    static List<Integer> cappedIndexes(int partCount, int capacity) {
        int limit = Math.min(Math.max(0, partCount), Math.max(0, capacity));
        ArrayList<Integer> result = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) result.add(index);
        return List.copyOf(result);
    }

    static String importModeLabel(boolean separate, int selectedParts, int capacity) {
        if (!separate) return "Import Mode: Combined";
        if (capacity == Integer.MAX_VALUE) return "Import Mode: Separate";
        return "Import Mode: Separate · " + selectedParts + "/" + capacity;
    }

    static boolean separatePartSelectable(boolean selected, int selectedCount, int capacity) {
        return selected || selectedCount < Math.max(0, capacity);
    }

    static String separateNameTemplate(ScoreImportAnalysis analysis,
                                       boolean includeSource,
                                       boolean includeInstrument,
                                       boolean includePart) {
        if (!analysis.midiOptions()) return "[Tune title] · Voice name";
        ArrayList<String> parts = new ArrayList<>(3);
        if (includeSource) parts.add("[MIDI name]");
        if (includeInstrument) parts.add("Inst. name");
        if (includePart) parts.add("Track name");
        return parts.isEmpty() ? "Imported MIDI" : String.join(" · ", parts);
    }

    static void trimToCapacity(Set<Integer> selected, int capacity) {
        int safeCapacity = Math.max(0, capacity);
        while (selected.size() > safeCapacity) {
            int highest = selected.stream().mapToInt(Integer::intValue).max().orElse(-1);
            if (highest < 0) break;
            selected.remove(highest);
        }
    }

    static boolean useClientDrumMap(boolean enabled, boolean songbook,
                                    boolean anyDrums, boolean allDrums) {
        return enabled && ((songbook && anyDrums) || (anyDrums && !allDrums));
    }

    static boolean useServerDrumMap(boolean enabled, boolean songbook, boolean percussion) {
        return enabled && !songbook && percussion;
    }

    static String generatedName(ScoreImportAnalysis analysis, List<Integer> selectedIndexes,
                                boolean includeSource, boolean includeInstrument,
                                boolean includePart) {
        if (selectedIndexes.isEmpty()) return "";
        return analysis.defaultName(selectedIndexes, includeSource,
                includeInstrument, includePart);
    }

}
