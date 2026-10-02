package m3e.client;

import m3e.sheet.MusicScore;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Bounded ABC 2.1 catalog reader and playback parser. */
final class AbcFile {
    static final int PPQ = 20_160;
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_TUNES = 4_096;
    private static final int MAX_VOICES = 128;
    private static final int MAX_EXPANDED_CHARS = 4 * 1024 * 1024;

    private AbcFile() { }

    static AbcDocument read(Path file) throws IOException {
        long size = Files.size(file);
        if (size < 1 || size > MAX_FILE_BYTES) {
            throw new IOException("ABC file must be between 1 byte and 16 MiB");
        }
        byte[] bytes = Files.readAllBytes(file);
        String text = decode(bytes);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = List.of(text.split("\n", -1));
        ArrayList<Integer> starts = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String stripped = stripComment(lines.get(index)).stripLeading();
            if (isField(stripped, 'X')) starts.add(index);
        }
        if (starts.size() > MAX_TUNES) throw new IOException("ABC file contains too many tunes");

        ArrayList<AbcTuneSummary> tunes = new ArrayList<>();
        if (starts.isEmpty()) {
            if (lines.stream().noneMatch(line -> isField(stripComment(line).stripLeading(), 'K'))) {
                throw new IOException("ABC file contains no tune with a K: field");
            }
            String title = firstField(lines, 0, lines.size(), 'T');
            tunes.add(new AbcTuneSummary(0, "1", fallbackTitle(title, file, "1"), 0,
                    lines.size(), List.of()));
        } else {
            List<String> defaults = List.copyOf(lines.subList(0, starts.getFirst()));
            for (int index = 0; index < starts.size(); index++) {
                int start = starts.get(index);
                int end = index + 1 < starts.size() ? starts.get(index + 1) : lines.size();
                String reference = fieldValue(lines.get(start));
                if (reference.isBlank()) reference = Integer.toString(index + 1);
                String title = firstField(lines, start, end, 'T');
                tunes.add(new AbcTuneSummary(index, reference,
                        fallbackTitle(title, file, reference), start, end, defaults));
            }
        }
        return new AbcDocument(file, file.getFileName().toString(), lines, List.copyOf(tunes));
    }

    private static String decode(byte[] bytes) throws IOException {
        try {
            return decode(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException utf8Failure) {
            String probe = new String(bytes, StandardCharsets.ISO_8859_1);
            if (!hasLotroProvenance(probe)) {
                throw new IOException("ABC file is not valid UTF-8 or ASCII", utf8Failure);
            }
            try {
                return decode(bytes, Charset.forName("windows-1252"));
            } catch (CharacterCodingException legacyFailure) {
                throw new IOException("LOTRO ABC file is neither valid UTF-8 nor Windows-1252",
                        legacyFailure);
            }
        }
    }

    private static String decode(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static boolean hasLotroProvenance(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("lotro midi player")
                || lower.contains("lotro abc player")
                || lower.contains("lotro.acasylum.com/midi")) {
            return true;
        }
        return lower.lines().anyMatch(line ->
                line.contains("abc-creator") && line.contains("maestro"));
    }

    private static String fallbackTitle(String title, Path file, String reference) {
        String cleaned = title == null ? "" : title.strip();
        if (!cleaned.isEmpty()) return truncate(cleaned, 96);
        String name = file.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".abc")) name = name.substring(0, name.length() - 4);
        return name.isBlank() ? "Tune " + reference : truncate(name, 96);
    }

    private static String firstField(List<String> lines, int start, int end, char field) {
        for (int index = start; index < end; index++) {
            String line = stripComment(lines.get(index)).stripLeading();
            if (isField(line, field)) return fieldValue(line);
        }
        return "";
    }

    private static boolean isField(String line, char field) {
        return line.length() >= 2 && line.charAt(0) == field && line.charAt(1) == ':';
    }

    private static String fieldValue(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? "" : line.substring(colon + 1).strip();
    }

    private static String stripComment(String line) {
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '%' && !escaped) return line.substring(0, index);
            escaped = character == '\\' && !escaped;
            if (character != '\\') escaped = false;
        }
        return line;
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    record AbcTuneSummary(int ordinal, String reference, String title, int startLine,
                          int endLine, List<String> fileDefaults) {
        String displayName() { return title + "  [X:" + reference + "]"; }
    }

    record AbcDocument(Path path, String sourceName, List<String> lines,
                       List<AbcTuneSummary> tunes) {
        AbcTuneAnalysis parse(AbcTuneSummary tune) {
            if (tune == null || !tunes.contains(tune)) {
                throw new IllegalArgumentException("ABC tune is not part of this file");
            }
            return new Parser(this, tune).parse();
        }
    }

    record Voice(String id, String name, long lengthTick, int outOfRangeNotes,
                 List<MusicScore.TempoChange> tempos,
                 List<MusicScore.TimeSignature> timeSignatures,
                 List<MusicScore.MidiNote> notes) {
        String displayName() {
            String label = name == null || name.isBlank() ? "Voice " + id : name;
            return label + " · " + notes.size() + (notes.size() == 1 ? " note" : " notes");
        }
    }

    record AbcTuneAnalysis(AbcDocument document, AbcTuneSummary tune, List<Voice> voices) {
        AbcTuneAnalysis {
            voices = List.copyOf(voices);
            if (voices.isEmpty()) throw new IllegalArgumentException("ABC tune contains no playable voices");
        }

        MusicScore song(List<Integer> selectedIndexes) {
            if (selectedIndexes == null || selectedIndexes.isEmpty()) {
                throw new IllegalArgumentException("Select at least one ABC voice");
            }
            LinkedHashSet<Integer> unique = new LinkedHashSet<>(selectedIndexes);
            if (unique.size() != selectedIndexes.size()
                    || unique.stream().anyMatch(index -> index < 0 || index >= voices.size())) {
                throw new IllegalArgumentException("ABC voice selection is invalid");
            }
            ArrayList<MusicScore.MidiNote> notes = new ArrayList<>();
            TreeMap<Long, Integer> tempos = new TreeMap<>();
            TreeMap<Long, MusicScore.TimeSignature> signatures = new TreeMap<>();
            long length = 1;
            for (int index : selectedIndexes) {
                Voice voice = voices.get(index);
                if (voice.outOfRangeNotes() > 0) {
                    throw new IllegalArgumentException("ABC voice " + voice.id() + " contains "
                            + voice.outOfRangeNotes() + " notes outside Music Maker's C0-A#7 range");
                }
                notes.addAll(voice.notes());
                length = Math.max(length, voice.lengthTick());
                for (MusicScore.TempoChange tempo : voice.tempos()) {
                    Integer previous = tempos.putIfAbsent(tempo.tick(), tempo.microsPerQuarter());
                    if (previous != null && previous != tempo.microsPerQuarter()) {
                        throw new IllegalArgumentException("Selected ABC voices contain conflicting tempo changes");
                    }
                }
                for (MusicScore.TimeSignature signature : voice.timeSignatures()) {
                    MusicScore.TimeSignature previous = signatures.putIfAbsent(signature.tick(), signature);
                    if (previous != null && (previous.numerator() != signature.numerator()
                            || previous.denominator() != signature.denominator())) {
                        throw new IllegalArgumentException("Selected ABC voices contain conflicting meter changes");
                    }
                }
            }
            notes.sort(Comparator.comparingLong(MusicScore.MidiNote::startTick)
                    .thenComparingInt(MusicScore.MidiNote::pitch)
                    .thenComparingLong(MusicScore.MidiNote::endTick));
            if (notes.isEmpty()) throw new IllegalArgumentException("Selected ABC voices contain no playable notes");
            if (notes.size() > MusicScore.MAX_NOTES) {
                throw new IllegalArgumentException("Selected ABC voices exceed "
                        + MusicScore.MAX_NOTES + " notes");
            }
            for (MusicScore.MidiNote note : notes) {
                length = Math.max(length, note.endTick());
            }
            List<MusicScore.TempoChange> tempoList = tempos.entrySet().stream()
                    .map(entry -> new MusicScore.TempoChange(entry.getKey(), entry.getValue())).toList();
            MusicScore song = new MusicScore(PPQ, false,
                    tune.title(), length, 440.0F, tempoList,
                    List.copyOf(signatures.values()), List.of(), List.copyOf(notes));
            if (song.tempoMap().microsAt(song.lengthTick()) > 7_200_000_000L) {
                throw new IllegalArgumentException("ABC performance exceeds the two-hour import limit");
            }
            return song;
        }
    }

    private static final class Parser {
        private final AbcDocument document;
        private final AbcTuneSummary tune;
        private final boolean lotroChordTiming;
        private final Settings header = new Settings();
        private final LinkedHashMap<String, VoiceSource> sources = new LinkedHashMap<>();
        private String currentVoice = "1";
        private String partOrder = "";
        private String activePart = "";
        private boolean body;
        private boolean keySeen;

        private Parser(AbcDocument document, AbcTuneSummary tune) {
            this.document = document;
            this.tune = tune;
            lotroChordTiming = isLotroTune(document, tune);
        }

        private AbcTuneAnalysis parse() {
            for (int lineIndex = 0; lineIndex < tune.fileDefaults().size(); lineIndex++) {
                processLine(tune.fileDefaults().get(lineIndex), true, lineIndex);
            }
            for (int lineIndex = tune.startLine(); lineIndex < tune.endLine(); lineIndex++) {
                processLine(document.lines().get(lineIndex), false, lineIndex);
            }
            if (!keySeen) throw error("ABC tune is missing its required K: field", tune.startLine(), 1);
            if (sources.isEmpty()) sources.put("1", new VoiceSource("1", "", header.copy()));
            ArrayList<Voice> voices = new ArrayList<>();
            for (VoiceSource source : sources.values()) {
                if (source.muted || source.music.toString().isBlank()) continue;
                Voice parsed = new VoiceParser(source, partOrder, tune.reference(), tune.startLine(),
                        lotroChordTiming).parse();
                if (!parsed.notes().isEmpty()) voices.add(parsed);
            }
            return new AbcTuneAnalysis(document, tune, voices);
        }

        private void processLine(String original, boolean fileDefault, int lineNumber) {
            try {
                processLineUnchecked(original, fileDefault, lineNumber);
            } catch (ArithmeticException exception) {
                throw error("ABC numeric value is too large", lineNumber, 1);
            } catch (IllegalArgumentException exception) {
                String message = exception.getMessage();
                if (message != null && (message.startsWith("Tune X:") || message.startsWith("Voice "))) {
                    throw exception;
                }
                throw error(message == null ? "Invalid ABC input" : message, lineNumber, 1);
            }
        }

        private void processLineUnchecked(String original, boolean fileDefault, int lineNumber) {
            String raw = original.strip();
            if (raw.startsWith("%abc")) return;
            if (raw.startsWith("%%")) {
                processDirective(raw.substring(2).strip());
                return;
            }
            String uncommented = stripComment(original);
            String line = uncommented.strip();
            if (line.isEmpty()) return;
            int sourceColumn = 1;
            while (sourceColumn <= uncommented.length()
                    && Character.isWhitespace(uncommented.charAt(sourceColumn - 1))) {
                sourceColumn++;
            }
            if (line.length() >= 2 && line.charAt(1) == ':') {
                char field = line.charAt(0);
                String value = fieldValue(line);
                if (!body || fileDefault) {
                    processHeaderField(field, value, fileDefault);
                } else {
                    processBodyField(field, value, lineNumber, sourceColumn);
                }
                return;
            }
            if (!body) return;
            distributeMusic(line, lineNumber, sourceColumn);
        }

        private void processHeaderField(char field, String value, boolean fileDefault) {
            switch (field) {
                case 'M' -> header.meter(value);
                case 'L' -> header.unit(value);
                case 'Q' -> header.tempo(value);
                case 'K' -> {
                    header.key(value);
                    if (!fileDefault) {
                        keySeen = true;
                        body = true;
                        for (VoiceSource source : sources.values()) source.applyHeader(header);
                        ensureVoice(currentVoice, "", header.copy());
                    }
                }
                case 'V' -> declareVoice(value);
                case 'P' -> partOrder = parsePartOrder(value);
                case 'I' -> processInformation(value);
                case 'm', 'U' -> throw new IllegalArgumentException(
                        "ABC playback macros are not supported");
                default -> { }
            }
        }

        private void processBodyField(char field, String value, int lineNumber, int sourceColumn) {
            switch (field) {
                case 'V' -> switchVoice(value);
                case 'M', 'L', 'Q', 'K' -> {
                    VoiceSource source = current();
                    appendSourceMarker(source, lineNumber, sourceColumn);
                    source.music.append('[').append(field)
                            .append(':').append(value).append("]\n");
                }
                case 'P' -> {
                    activePart = partLabel(value);
                    for (VoiceSource source : sources.values()) {
                        source.music.append("\n[P:").append(activePart).append("]\n");
                    }
                }
                case 'I' -> processInformation(value);
                case 'm', 'U' -> throw new IllegalArgumentException(
                        "ABC playback macros are not supported");
                default -> { }
            }
        }

        private void declareVoice(String value) {
            VoiceDefinition definition = VoiceDefinition.parse(value);
            currentVoice = definition.id;
            VoiceSource source = ensureVoice(definition.id, definition.name, header.copy());
            source.apply(definition);
        }

        private void switchVoice(String value) {
            VoiceDefinition definition = VoiceDefinition.parse(value);
            currentVoice = definition.id;
            VoiceSource source = ensureVoice(definition.id, definition.name, header.copy());
            source.apply(definition);
        }

        private void distributeMusic(String line, int lineNumber, int sourceColumn) {
            int cursor = 0;
            while (cursor < line.length()) {
                int marker = line.indexOf("[V:", cursor);
                if (marker < 0) {
                    appendSourceMarker(current(), lineNumber, sourceColumn + cursor);
                    current().music.append(line, cursor, line.length()).append('\n');
                    return;
                }
                if (marker > cursor) {
                    appendSourceMarker(current(), lineNumber, sourceColumn + cursor);
                    current().music.append(line, cursor, marker);
                }
                int close = line.indexOf(']', marker + 3);
                if (close < 0) throw new IllegalArgumentException("Unclosed inline V: field");
                switchVoice(line.substring(marker + 3, close));
                cursor = close + 1;
            }
            current().music.append('\n');
        }

        private void appendSourceMarker(VoiceSource source, int lineNumber, int column) {
            source.music.append("[I:xercamusic-source=").append(lineNumber + 1)
                    .append(':').append(column).append(']');
        }

        private void processDirective(String directive) {
            String lower = directive.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("midi")) return;
            if (lower.startsWith("midi program") || lower.startsWith("midi bank")) return;
            if (!lower.startsWith("midi voice")) {
                throw new IllegalArgumentException("Unsupported playback directive: %%" + directive);
            }
            String remainder = directive.substring("midi voice".length()).strip();
            String id = currentVoice;
            if (!remainder.isEmpty() && !remainder.contains("=")
                    && !remainder.toLowerCase(Locale.ROOT).startsWith("mute")) {
                int space = remainder.indexOf(' ');
                id = space < 0 ? remainder : remainder.substring(0, space);
            }
            if (lower.contains("mute")) ensureVoice(id, "", header.copy()).muted = true;
        }

        private void processInformation(String value) {
            String cleaned = value.strip();
            if (cleaned.toLowerCase(Locale.ROOT).startsWith("midi ")) processDirective(cleaned);
        }

        private String parsePartOrder(String value) {
            String cleaned = value.strip();
            if (cleaned.length() > 1_024
                    || !cleaned.matches("[A-Za-z0-9().\\s]*")) {
                throw new IllegalArgumentException("Unsupported ABC part order: " + value);
            }
            return cleaned;
        }

        private VoiceSource current() {
            return ensureVoice(currentVoice, "", header.copy());
        }

        private VoiceSource ensureVoice(String id, String name, Settings settings) {
            if (!sources.containsKey(id) && sources.size() >= MAX_VOICES) {
                throw new IllegalArgumentException("ABC tune contains too many voices");
            }
            return sources.computeIfAbsent(id, ignored -> {
                VoiceSource source = new VoiceSource(id, name, settings);
                if (!activePart.isBlank()) {
                    source.music.append("\n[P:").append(activePart).append("]\n");
                }
                return source;
            });
        }

        private IllegalArgumentException error(String message, int line, int column) {
            return new IllegalArgumentException("Tune X:" + tune.reference() + ", line "
                    + (line + 1) + ", column " + column + ": " + message);
        }
    }

    private static boolean isLotroTune(AbcDocument document, AbcTuneSummary tune) {
        StringBuilder relevant = new StringBuilder();
        for (String line : tune.fileDefaults()) relevant.append(line).append('\n');
        for (int index = tune.startLine(); index < tune.endLine(); index++) {
            relevant.append(document.lines().get(index)).append('\n');
        }
        return hasLotroProvenance(relevant.toString())
                || containsRestInsideChord(document.lines(), tune.startLine(), tune.endLine());
    }

    private static boolean containsRestInsideChord(List<String> lines, int start, int end) {
        boolean body = false;
        for (int lineIndex = start; lineIndex < end; lineIndex++) {
            String line = stripComment(lines.get(lineIndex)).strip();
            if (line.length() >= 2 && line.charAt(1) == ':') {
                if (line.charAt(0) == 'K') body = true;
                continue;
            }
            if (!body) continue;
            for (int open = line.indexOf('['); open >= 0; open = line.indexOf('[', open + 1)) {
                if (open + 1 >= line.length() || Character.isDigit(line.charAt(open + 1))
                        || open + 2 < line.length() && line.charAt(open + 2) == ':') {
                    continue;
                }
                int close = line.indexOf(']', open + 1);
                if (close < 0) break;
                char decoration = 0;
                boolean quoted = false;
                for (int index = open + 1; index < close; index++) {
                    char character = line.charAt(index);
                    if (quoted) {
                        if (character == '"' && line.charAt(index - 1) != '\\') quoted = false;
                        continue;
                    }
                    if (decoration != 0) {
                        if (character == decoration) decoration = 0;
                        continue;
                    }
                    if (character == '"') quoted = true;
                    else if (character == '!' || character == '+') decoration = character;
                    else if (character == 'z' || character == 'x') return true;
                }
                open = close;
            }
        }
        return false;
    }

    private static final class VoiceParser {
        private final VoiceSource source;
        private final String partOrder;
        private final String tuneReference;
        private final int tuneStartLine;
        private final boolean lotroChordTiming;
        private final Settings settings;
        private final ArrayList<MutableNote> notes = new ArrayList<>();
        private final TreeMap<Long, Integer> tempos = new TreeMap<>();
        private final TreeMap<Long, MusicScore.TimeSignature> signatures = new TreeMap<>();
        private final Map<NoteKey, Integer> barAccidentals = new HashMap<>();
        private final Map<Integer, Integer> ties = new HashMap<>();
        private final ArrayList<Integer> pendingGrace = new ArrayList<>();
        private Fraction cursor = Fraction.ZERO;
        private Fraction barStart = Fraction.ZERO;
        private Fraction overlayMaximum = Fraction.ZERO;
        private Fraction nextRhythm = Fraction.ONE;
        private Fraction tupletScale = Fraction.ONE;
        private int tupletRemaining;
        private int velocity = 96;
        private boolean staccato;
        private boolean accent;
        private boolean overlay;
        private int outOfRange;
        private int sourceLine;
        private int sourceColumn = 1;
        private int sourceColumnIndex;
        private String music;
        private int index;

        private VoiceParser(VoiceSource source, String partOrder,
                            String tuneReference, int tuneStartLine,
                            boolean lotroChordTiming) {
            this.source = source;
            this.partOrder = partOrder;
            this.tuneReference = tuneReference;
            this.tuneStartLine = tuneStartLine;
            this.lotroChordTiming = lotroChordTiming;
            settings = source.settings.copy();
        }

        private Voice parse() {
            music = source.music.toString();
            if (music.length() > MAX_EXPANDED_CHARS) {
                throw failure("ABC voice source is too large");
            }
            try {
                music = expandRepeats(expandParts(music, partOrder));
            } catch (ArithmeticException exception) {
                throw failure("ABC numeric value is too large");
            } catch (IllegalArgumentException exception) {
                throw located(exception);
            }
            tempos.put(0L, settings.microsPerQuarter);
            signatures.put(0L, new MusicScore.TimeSignature(0,
                    settings.meterNumerator, settings.meterDenominator));
            while (index < music.length()) {
                try {
                    parseNext();
                } catch (ArithmeticException exception) {
                    throw failure("ABC numeric value is too large");
                } catch (IllegalArgumentException exception) {
                    throw located(exception);
                }
            }
            if (tupletRemaining > 0) throw failure("Incomplete ABC tuplet");
            if (!pendingGrace.isEmpty()) throw failure("Grace group is missing an anchor note");
            if (!ties.isEmpty()) throw failure("ABC tie is missing its destination note");
            if (overlay) cursor = cursor.max(overlayMaximum);
            long length = Math.max(1, cursor.roundLong());
            ArrayList<MusicScore.MidiNote> immutable = new ArrayList<>(notes.size());
            for (MutableNote note : notes) {
                if (note.end <= note.start) continue;
                immutable.add(new MusicScore.MidiNote(note.pitch, note.velocity,
                        note.start, Math.max(note.start + 1, note.release), note.end));
            }
            immutable.sort(Comparator.comparingLong(MusicScore.MidiNote::startTick)
                    .thenComparingInt(MusicScore.MidiNote::pitch));
            return new Voice(source.id, source.name, length, outOfRange,
                    tempos.entrySet().stream().map(entry ->
                            new MusicScore.TempoChange(entry.getKey(), entry.getValue())).toList(),
                    List.copyOf(signatures.values()), List.copyOf(immutable));
        }

        private void parseNext() {
            char character = music.charAt(index);
            if (Character.isWhitespace(character) || character == '`' || character == '\\'
                    || character == '$' || character == 'y'
                    || "#*;?@".indexOf(character) >= 0) {
                index++;
                return;
            }
            if (character == '"') {
                skipQuoted();
                return;
            }
            if (character == '!' || character == '+') {
                parseDecoration(character);
                return;
            }
            if (character == '.') {
                staccato = true;
                index++;
                return;
            }
            if (character == '(') {
                if (index + 1 < music.length() && Character.isDigit(music.charAt(index + 1))) parseTuplet();
                else index++;
                return;
            }
            if (character == ')' || character == '~') {
                index++;
                return;
            }
            if (character == '&') {
                overlay = true;
                overlayMaximum = overlayMaximum.max(cursor);
                cursor = barStart;
                index++;
                return;
            }
            if (character == '|') {
                finishBar();
                index++;
                if (index < music.length() && ":]|".indexOf(music.charAt(index)) >= 0) index++;
                return;
            }
            if (character == ':') {
                index++;
                if (index < music.length() && music.charAt(index) == '|') index++;
                finishBar();
                return;
            }
            if (character == '[') {
                if (index + 2 < music.length() && Character.isLetter(music.charAt(index + 1))
                        && music.charAt(index + 2) == ':') parseInlineField();
                else if (index + 1 < music.length() && Character.isDigit(music.charAt(index + 1))) skipEnding();
                else parseChord();
                return;
            }
            if (character == '{') {
                parseGraceGroup();
                return;
            }
            if (character == 'z' || character == 'x') {
                index++;
                Fraction duration = duration(parseLength());
                Suffix suffix = parseSuffix();
                if (suffix.tie) throw failure("ABC rests cannot be tied");
                cursor = cursor.add(duration.multiply(suffix.currentScale));
                nextRhythm = suffix.nextScale;
                afterGroup();
                return;
            }
            if (character == 'Z' || character == 'X') {
                index++;
                int measures = parseInteger(1);
                Fraction measure = Fraction.of(settings.meterNumerator, settings.meterDenominator)
                        .multiply(4L * PPQ);
                cursor = cursor.add(measure.multiply(measures));
                return;
            }
            if (isPitchStart(character)) {
                parseSingleNote();
                return;
            }
            if (isRedefinableSymbol(character)) {
                if (character == 'm' || character == 'U') {
                    throw failure("Unsupported ABC playback macro");
                }
                if (character == 'L') accent = true;
                index++;
                return;
            }
            throw failure("Unexpected ABC music character '" + character + "'");
        }

        private void parseSingleNote() {
            PitchResult pitch = parsePitch();
            Fraction duration = duration(parseLength());
            Suffix suffix = parseSuffix();
            addGroup(List.of(pitch.pitch), duration.multiply(suffix.currentScale), suffix.tie);
            nextRhythm = suffix.nextScale;
            afterGroup();
        }

        private void parseChord() {
            index++;
            ArrayList<ChordMember> members = new ArrayList<>();
            Fraction firstLength = null;
            Fraction shortestLength = null;
            while (index < music.length() && music.charAt(index) != ']') {
                ChordDecoration decoration = parseChordDecoration();
                if (index >= music.length() || music.charAt(index) == ']') break;
                if (music.charAt(index) == 'z' || music.charAt(index) == 'x') {
                    index++;
                    Fraction inner = parseLength();
                    if (index < music.length() && music.charAt(index) == '-') {
                        throw failure("ABC rests cannot be tied");
                    }
                    shortestLength = shortestLength == null ? inner : shortestLength.min(inner);
                    continue;
                }
                if (!isPitchStart(music.charAt(index))) throw failure("Invalid note inside ABC chord");
                PitchResult pitch = parsePitch();
                Fraction inner = parseLength();
                boolean memberTie = false;
                if (index < music.length() && music.charAt(index) == '-') {
                    memberTie = true;
                    index++;
                }
                if (firstLength == null) firstLength = inner;
                shortestLength = shortestLength == null ? inner : shortestLength.min(inner);
                members.add(new ChordMember(pitch.pitch, inner, memberTie,
                        decoration.staccato, decoration.accent, decoration.velocity));
            }
            if (index >= music.length()) throw failure("Unclosed ABC chord");
            if (members.isEmpty()) throw failure("ABC chord contains no playable notes");
            index++;
            Fraction outer = parseLength();
            Fraction baseDuration = duration(outer);
            Suffix suffix = parseSuffix();
            Fraction advanceLength = lotroChordTiming ? shortestLength : firstLength;
            addChordGroup(members, baseDuration.multiply(suffix.currentScale),
                    advanceLength, suffix.tie);
            nextRhythm = suffix.nextScale;
            afterGroup();
        }

        private ChordDecoration parseChordDecoration() {
            boolean memberStaccato = false;
            boolean memberAccent = false;
            while (index < music.length()) {
                char character = music.charAt(index);
                if (Character.isWhitespace(character) || character == '`' || character == '\\'
                        || character == '$' || character == 'y'
                        || "#*;?@".indexOf(character) >= 0) {
                    index++;
                } else if (character == '"') {
                    skipQuoted();
                } else if (character == '!' || character == '+') {
                    DecorationEffect effect = readDecoration(character);
                    if (effect.velocity != null) velocity = effect.velocity;
                    memberStaccato |= effect.staccato;
                    memberAccent |= effect.accent;
                } else if (character == '.') {
                    memberStaccato = true;
                    index++;
                } else if (isRedefinableSymbol(character) || character == '~') {
                    if (character == 'm' || character == 'U') {
                        throw failure("Unsupported ABC playback macro");
                    }
                    if (character == 'L') memberAccent = true;
                    index++;
                } else {
                    break;
                }
            }
            return new ChordDecoration(memberStaccato, memberAccent, velocity);
        }

        private void addChordGroup(List<ChordMember> members, Fraction baseDuration,
                                   Fraction advanceLength, boolean tieAfter) {
            Fraction duration = baseDuration.multiply(advanceLength);
            if (duration.signum() <= 0) throw failure("ABC chord has no duration");
            Fraction startFraction = cursor;
            Fraction principalStart = principalStart(startFraction, duration);
            for (ChordMember member : members) {
                Fraction memberDuration = baseDuration.multiply(member.length);
                int noteVelocity = Math.clamp(member.velocity
                        + (accent || member.accent ? 12 : 0), 1, 127);
                addNote(member.pitch, principalStart, startFraction.add(memberDuration),
                        tieAfter || member.tieAfter, noteVelocity,
                        staccato || member.staccato);
            }
            cursor = startFraction.add(duration);
            staccato = false;
            accent = false;
        }

        private void addGroup(List<Integer> pitches, Fraction duration, boolean tieAfter) {
            if (duration.signum() <= 0) throw failure("ABC note has no duration");
            Fraction startFraction = cursor;
            Fraction endFraction = cursor.add(duration);
            Fraction principalStart = principalStart(startFraction, duration);
            int noteVelocity = Math.clamp(velocity + (accent ? 12 : 0), 1, 127);
            for (int pitch : pitches) {
                addNote(pitch, principalStart, endFraction, tieAfter, noteVelocity, staccato);
            }
            cursor = endFraction;
            staccato = false;
            accent = false;
        }

        private Fraction principalStart(Fraction startFraction, Fraction duration) {
            Fraction principalStart = startFraction;
            if (!pendingGrace.isEmpty()) {
                Fraction graceTotal = duration.divide(8).min(Fraction.of(PPQ, 8));
                Fraction each = graceTotal.divide(pendingGrace.size());
                Fraction graceCursor = startFraction;
                for (int gracePitch : pendingGrace) {
                    addNote(gracePitch, graceCursor, graceCursor.add(each), false, 72, false);
                    graceCursor = graceCursor.add(each);
                }
                principalStart = graceCursor;
                pendingGrace.clear();
            }
            return principalStart;
        }

        private void addNote(int pitch, Fraction startFraction, Fraction endFraction,
                             boolean tieAfter, int noteVelocity, boolean noteStaccato) {
            long start = startFraction.roundLong();
            long fullEnd = Math.max(start + 1, endFraction.roundLong());
            Integer previousIndex = ties.remove(pitch);
            if (previousIndex != null) {
                MutableNote previous = notes.get(previousIndex);
                if (previous.end < start) throw failure("Tied ABC notes are not contiguous");
                previous.end = Math.max(previous.end, fullEnd);
                previous.release = Math.max(previous.release, fullEnd);
                if (tieAfter) ties.put(pitch, previousIndex);
                return;
            }
            if (pitch < 21 || pitch > 117) {
                outOfRange++;
                return;
            }
            long audibleEnd = noteStaccato && !tieAfter
                    ? Math.max(start + 1, start + (fullEnd - start) / 2) : fullEnd;
            MutableNote note = new MutableNote(pitch, noteVelocity, start, audibleEnd, audibleEnd);
            notes.add(note);
            if (tieAfter) ties.put(pitch, notes.size() - 1);
            if (notes.size() > MusicScore.MAX_NOTES) {
                throw failure("ABC voice exceeds " + MusicScore.MAX_NOTES + " notes");
            }
        }

        private void afterGroup() {
            if (tupletRemaining > 0 && --tupletRemaining == 0) tupletScale = Fraction.ONE;
        }

        private Fraction duration(Fraction multiplier) {
            Fraction unit = settings.unit == null ? settings.defaultUnit() : settings.unit;
            Fraction result = unit.multiply(4L * PPQ).multiply(multiplier).multiply(nextRhythm);
            nextRhythm = Fraction.ONE;
            if (tupletRemaining > 0) result = result.multiply(tupletScale);
            return result;
        }

        private Suffix parseSuffix() {
            boolean tie = false;
            if (index < music.length() && music.charAt(index) == '-') {
                tie = true;
                index++;
            }
            int right = 0, left = 0;
            while (index < music.length() && (music.charAt(index) == '>' || music.charAt(index) == '<')) {
                if (music.charAt(index++) == '>') right++;
                else left++;
            }
            if (right > 0 && left > 0) throw failure("Mixed ABC broken-rhythm direction");
            int count = Math.max(right, left);
            if (count == 0) return new Suffix(tie, Fraction.ONE, Fraction.ONE);
            if (count > 16) throw failure("ABC broken rhythm is too deeply subdivided");
            long power = 1L << count;
            Fraction longScale = Fraction.of((power << 1) - 1, power);
            Fraction shortScale = Fraction.of(1, power);
            return right > 0 ? new Suffix(tie, longScale, shortScale)
                    : new Suffix(tie, shortScale, longScale);
        }

        private PitchResult parsePitch() {
            int accidental = Integer.MIN_VALUE;
            if (music.charAt(index) == '=' || music.charAt(index) == '^' || music.charAt(index) == '_') {
                char symbol = music.charAt(index++);
                accidental = 0;
                if (symbol != '=') {
                    int direction = symbol == '^' ? 1 : -1;
                    accidental = direction;
                    if (index < music.length() && music.charAt(index) == symbol) {
                        accidental += direction;
                        index++;
                    }
                    if (index < music.length() && music.charAt(index) == '/') {
                        throw failure("Microtonal ABC accidentals cannot be represented by Music Maker");
                    }
                }
            }
            if (index >= music.length() || !isPitchLetter(music.charAt(index))) {
                throw failure("Expected ABC pitch");
            }
            char letter = music.charAt(index++);
            boolean lower = Character.isLowerCase(letter);
            int natural = switch (Character.toUpperCase(letter)) {
                case 'C' -> 0; case 'D' -> 2; case 'E' -> 4; case 'F' -> 5;
                case 'G' -> 7; case 'A' -> 9; case 'B' -> 11;
                default -> throw failure("Invalid ABC pitch");
            };
            int octave = lower ? 5 : 4;
            while (index < music.length() && (music.charAt(index) == '\'' || music.charAt(index) == ',')) {
                octave += music.charAt(index++) == '\'' ? 1 : -1;
            }
            octave += settings.octave;
            int letterIndex = "CDEFGAB".indexOf(Character.toUpperCase(letter));
            NoteKey key = new NoteKey(letterIndex, octave);
            if (accidental == Integer.MIN_VALUE) {
                accidental = barAccidentals.getOrDefault(key, settings.keyAccidental(letterIndex));
            } else {
                barAccidentals.put(key, accidental);
            }
            return new PitchResult((octave + 1) * 12 + natural + accidental + settings.transpose);
        }

        private Fraction parseLength() {
            long numerator = 0;
            boolean hasNumerator = false;
            while (index < music.length() && Character.isDigit(music.charAt(index))) {
                hasNumerator = true;
                numerator = Math.addExact(Math.multiplyExact(numerator, 10), music.charAt(index++) - '0');
            }
            if (!hasNumerator) numerator = 1;
            if (index >= music.length() || music.charAt(index) != '/') return Fraction.of(numerator, 1);
            int slashCount = 0;
            while (index < music.length() && music.charAt(index) == '/') {
                slashCount++;
                index++;
            }
            if (slashCount > 30) throw failure("ABC note length is too deeply subdivided");
            long denominator = 0;
            boolean hasDenominator = false;
            while (index < music.length() && Character.isDigit(music.charAt(index))) {
                hasDenominator = true;
                denominator = Math.addExact(Math.multiplyExact(denominator, 10), music.charAt(index++) - '0');
            }
            if (!hasDenominator) denominator = 1L << Math.min(30, slashCount);
            else if (slashCount > 1) denominator = Math.multiplyExact(denominator,
                    1L << Math.min(30, slashCount - 1));
            if (numerator < 1 || denominator < 1) throw failure("Invalid ABC note length");
            return Fraction.of(numerator, denominator);
        }

        private void parseTuplet() {
            index++;
            int p = parseInteger(-1);
            if (p < 2 || p > 9) throw failure("ABC tuplets must contain 2-9 notes");
            int q = defaultTupletQ(p);
            int r = p;
            if (index < music.length() && music.charAt(index) == ':') {
                index++;
                int parsedQ = parseInteger(-1);
                if (parsedQ > 0) q = parsedQ;
                if (index < music.length() && music.charAt(index) == ':') {
                    index++;
                    int parsedR = parseInteger(-1);
                    if (parsedR > 0) r = parsedR;
                }
            }
            if (q < 1 || q > 9 || r < 1 || r > 64) throw failure("Invalid ABC tuplet ratio");
            tupletScale = Fraction.of(q, p);
            tupletRemaining = r;
        }

        private int defaultTupletQ(int p) {
            if (p == 2 || p == 4 || p == 8) return 3;
            if (p == 3 || p == 6) return 2;
            boolean compound = settings.meterDenominator == 8
                    && settings.meterNumerator % 3 == 0;
            return compound ? 3 : 2;
        }

        private void parseInlineField() {
            int close = music.indexOf(']', index + 3);
            if (close < 0) throw failure("Unclosed inline ABC field");
            char field = music.charAt(index + 1);
            String value = music.substring(index + 3, close).strip();
            index = close + 1;
            switch (field) {
                case 'M' -> {
                    settings.meter(value);
                    long tick = cursor.roundLong();
                    signatures.put(tick, new MusicScore.TimeSignature(tick,
                            settings.meterNumerator, settings.meterDenominator));
                }
                case 'L' -> settings.unit(value);
                case 'Q' -> {
                    settings.tempo(value);
                    tempos.put(cursor.roundLong(), settings.microsPerQuarter);
                }
                case 'K' -> {
                    settings.key(value);
                    barAccidentals.clear();
                }
                case 'I' -> parseSourceMarker(value);
                case 'm', 'U' -> throw failure("ABC playback macros are not supported");
                default -> { }
            }
        }

        private void parseSourceMarker(String value) {
            String prefix = "xercamusic-source=";
            if (!value.startsWith(prefix)) return;
            int colon = value.indexOf(':', prefix.length());
            if (colon < 0) return;
            try {
                sourceLine = Integer.parseInt(value.substring(prefix.length(), colon));
                sourceColumn = Integer.parseInt(value.substring(colon + 1));
                sourceColumnIndex = index;
            } catch (NumberFormatException ignored) {
                sourceLine = 0;
            }
        }

        private void parseDecoration(char delimiter) {
            DecorationEffect effect = readDecoration(delimiter);
            if (effect.velocity != null) velocity = effect.velocity;
            staccato |= effect.staccato;
            accent |= effect.accent;
        }

        private DecorationEffect readDecoration(char delimiter) {
            int close = music.indexOf(delimiter, index + 1);
            if (close < 0) throw failure("Unclosed ABC decoration");
            String name = music.substring(index + 1, close).toLowerCase(Locale.ROOT);
            index = close + 1;
            Integer dynamic = switch (name) {
                case "ppp" -> 32; case "pp" -> 48; case "p" -> 64; case "mp" -> 80;
                case "mf" -> 96; case "f" -> 108; case "ff" -> 120; case "fff" -> 127;
                default -> null;
            };
            return new DecorationEffect(dynamic, name.equals("staccato"),
                    name.equals("accent") || name.equals("emphasis") || name.equals(">"));
        }

        private void parseGraceGroup() {
            index++;
            if (index < music.length() && music.charAt(index) == '/') index++;
            while (index < music.length() && music.charAt(index) != '}') {
                if (Character.isWhitespace(music.charAt(index)) || music.charAt(index) == '<'
                        || music.charAt(index) == '>') {
                    index++;
                    continue;
                }
                if (!isPitchStart(music.charAt(index))) throw failure("Invalid ABC grace note");
                pendingGrace.add(parsePitch().pitch);
                parseLength();
            }
            if (index >= music.length()) throw failure("Unclosed ABC grace-note group");
            index++;
        }

        private void skipQuoted() {
            index++;
            boolean escaped = false;
            while (index < music.length()) {
                char character = music.charAt(index++);
                if (character == '"' && !escaped) return;
                escaped = character == '\\' && !escaped;
                if (character != '\\') escaped = false;
            }
            throw failure("Unclosed ABC annotation");
        }

        private void skipEnding() {
            index++;
            while (index < music.length() && (Character.isDigit(music.charAt(index))
                    || music.charAt(index) == ',' || music.charAt(index) == '-')) index++;
        }

        private int parseInteger(int fallback) {
            int value = 0;
            boolean found = false;
            while (index < music.length() && Character.isDigit(music.charAt(index))) {
                found = true;
                value = Math.addExact(Math.multiplyExact(value, 10), music.charAt(index++) - '0');
            }
            return found ? value : fallback;
        }

        private void finishBar() {
            if (overlay) cursor = cursor.max(overlayMaximum);
            overlay = false;
            overlayMaximum = Fraction.ZERO;
            barStart = cursor;
            barAccidentals.clear();
        }

        private IllegalArgumentException failure(String message) {
            if (sourceLine > 0) {
                int line = sourceLine;
                int column = sourceColumn;
                for (int at = sourceColumnIndex; at < Math.min(index, music.length()); at++) {
                    if (music.charAt(at) == '\n') { line++; column = 1; }
                    else column++;
                }
                return new IllegalArgumentException("Tune X:" + tuneReference + ", voice "
                        + source.id + ", line " + line + ", column " + column + ": " + message);
            }
            int line = 1, column = 1;
            for (int at = 0; at < Math.min(index, music.length()); at++) {
                if (music.charAt(at) == '\n') { line++; column = 1; }
                else column++;
            }
            return new IllegalArgumentException("Tune X:" + tuneReference + ", voice " + source.id
                    + ", line " + (tuneStartLine + line) + ", column " + column + ": " + message);
        }

        private IllegalArgumentException located(IllegalArgumentException exception) {
            String message = exception.getMessage();
            return message != null && message.startsWith("Tune X:")
                    ? exception : failure(message == null ? "Invalid ABC input" : message);
        }
    }

    private static String expandRepeats(String source) {
        String expanded = source.replace("::", ":||:");
        int passes = 0;
        while (expanded.contains(":|") && passes++ < 64) {
            int end = expanded.indexOf(":|");
            int start = expanded.lastIndexOf("|:", end);
            int bodyStart = start < 0 ? 0 : start + 2;
            String inside = expanded.substring(bodyStart, end);
            int firstEnding = Math.max(inside.lastIndexOf("|1"), inside.lastIndexOf("[1"));
            int after = end + 2;
            String replacement;
            int consumedEnd = after;
            if (firstEnding >= 0) {
                String common = inside.substring(0, firstEnding);
                String first = inside.substring(firstEnding + 2);
                if (after < expanded.length() && expanded.charAt(after) == '2') after++;
                int terminal = nextTerminal(expanded, after);
                String second = expanded.substring(after, terminal);
                replacement = common + first + "|" + common + second;
                consumedEnd = terminal;
            } else {
                replacement = inside + "|" + inside;
            }
            int replaceStart = start < 0 ? 0 : start;
            expanded = expanded.substring(0, replaceStart) + replacement + expanded.substring(consumedEnd);
            if (expanded.length() > MAX_EXPANDED_CHARS) {
                throw new IllegalArgumentException("ABC repeat expansion is too large");
            }
        }
        if (expanded.contains(":|")) throw new IllegalArgumentException("ABC repeat structure is too deeply nested");
        return expanded.replace("|:", "|").replace("|1", "|").replace("|2", "|")
                .replace("[1", "|").replace("[2", "|");
    }

    private static int nextTerminal(String value, int from) {
        int result = value.length();
        for (String marker : List.of("|]", "||", "|:", ":|")) {
            int found = value.indexOf(marker, from);
            if (found >= 0) result = Math.min(result, found);
        }
        return result;
    }

    private static String expandParts(String source, String order) {
        java.util.regex.Matcher marker = java.util.regex.Pattern.compile("\\[P:([^]\\r\\n]+)]")
                .matcher(source);
        if (!marker.find()) {
            if (!order.isBlank()) {
                throw new IllegalArgumentException("ABC part order has no matching body sections");
            }
            return source;
        }
        if (order.isBlank()) {
            return source.replaceAll("\\[P:[^]\\r\\n]+]", "|");
        }
        String prefix = source.substring(0, marker.start());
        String nonControlPrefix = prefix.replaceAll("\\[(?:M|L|Q|K|I):[^]\\r\\n]*]", "");
        if (!nonControlPrefix.isBlank()) {
            throw new IllegalArgumentException("ABC notes before the first P: section are ambiguous");
        }
        LinkedHashMap<String, StringBuilder> parts = new LinkedHashMap<>();
        int bodyStart = marker.end();
        String label = partLabel(marker.group(1));
        while (marker.find()) {
            parts.computeIfAbsent(label, ignored -> new StringBuilder())
                    .append(source, bodyStart, marker.start()).append('|');
            label = partLabel(marker.group(1));
            bodyStart = marker.end();
        }
        parts.computeIfAbsent(label, ignored -> new StringBuilder())
                .append(source, bodyStart, source.length()).append('|');

        StringBuilder expanded = new StringBuilder(source.length()).append(prefix);
        for (String requested : new PartOrderParser(order).parse()) {
            StringBuilder part = parts.get(requested);
            if (part == null) throw new IllegalArgumentException(
                    "ABC part order references missing body section " + requested);
            expanded.append(part);
            if (expanded.length() > MAX_EXPANDED_CHARS) {
                throw new IllegalArgumentException("ABC part expansion is too large");
            }
        }
        return expanded.toString();
    }

    private static String partLabel(String value) {
        String cleaned = value.strip();
        if (cleaned.length() != 1 || !Character.isLetter(cleaned.charAt(0))) {
            throw new IllegalArgumentException("ABC part labels must be one letter");
        }
        return cleaned;
    }

    private static final class PartOrderParser {
        private final String source;
        private int index;

        private PartOrderParser(String source) { this.source = source; }

        private List<String> parse() {
            List<String> result = sequence(false);
            if (index != source.length() || result.isEmpty()) {
                throw new IllegalArgumentException("Invalid ABC part order: " + source);
            }
            return result;
        }

        private List<String> sequence(boolean nested) {
            ArrayList<String> result = new ArrayList<>();
            while (index < source.length()) {
                char character = source.charAt(index);
                if (Character.isWhitespace(character) || character == '.') { index++; continue; }
                if (character == ')') {
                    if (!nested) throw new IllegalArgumentException("Unexpected ) in ABC part order");
                    index++;
                    return result;
                }
                List<String> atom;
                if (character == '(') {
                    index++;
                    atom = sequence(true);
                    if (atom.isEmpty()) throw new IllegalArgumentException("Empty ABC part-order group");
                } else if (Character.isLetter(character)) {
                    index++;
                    atom = List.of(Character.toString(character));
                } else {
                    throw new IllegalArgumentException("Invalid character in ABC part order");
                }
                int repeat = parseRepeat();
                for (int pass = 0; pass < repeat; pass++) {
                    result.addAll(atom);
                    if (result.size() > 4_096) {
                        throw new IllegalArgumentException("ABC part order is too large");
                    }
                }
            }
            if (nested) throw new IllegalArgumentException("Unclosed ABC part-order group");
            return result;
        }

        private int parseRepeat() {
            int start = index;
            int value = 0;
            while (index < source.length() && Character.isDigit(source.charAt(index))) {
                value = Math.addExact(Math.multiplyExact(value, 10), source.charAt(index++) - '0');
            }
            if (start == index) return 1;
            if (value < 1 || value > 64) throw new IllegalArgumentException("Invalid ABC part repeat");
            return value;
        }
    }

    private static boolean isPitchStart(char character) {
        return character == '^' || character == '_' || character == '=' || isPitchLetter(character);
    }

    private static boolean isRedefinableSymbol(char character) {
        return character >= 'H' && character <= 'W'
                || character >= 'h' && character <= 'w';
    }

    private static boolean isPitchLetter(char character) {
        char upper = Character.toUpperCase(character);
        return upper >= 'A' && upper <= 'G';
    }

    private static final class Settings {
        private int meterNumerator = 4;
        private int meterDenominator = 4;
        private Fraction unit;
        private int microsPerQuarter = 500_000;
        private int fifths;
        private final int[] keyOverrides = {-99, -99, -99, -99, -99, -99, -99};
        private int transpose;
        private int octave;

        private Settings copy() {
            Settings copy = new Settings();
            copy.copyFrom(this);
            return copy;
        }

        private void copyFrom(Settings source) {
            meterNumerator = source.meterNumerator;
            meterDenominator = source.meterDenominator;
            unit = source.unit;
            microsPerQuarter = source.microsPerQuarter;
            fifths = source.fifths;
            System.arraycopy(source.keyOverrides, 0, keyOverrides, 0, keyOverrides.length);
            transpose = source.transpose;
            octave = source.octave;
        }

        private Fraction defaultUnit() {
            return Fraction.of(1,
                    (long) meterNumerator * 4 < (long) meterDenominator * 3 ? 16 : 8);
        }

        private void meter(String value) {
            String cleaned = value.strip();
            if (cleaned.isEmpty() || cleaned.equalsIgnoreCase("none")) {
                meterNumerator = 4;
                meterDenominator = 4;
                return;
            }
            if (cleaned.equals("C")) { meterNumerator = 4; meterDenominator = 4; return; }
            if (cleaned.equals("C|")) { meterNumerator = 2; meterDenominator = 2; return; }
            int slash = cleaned.indexOf('/');
            if (slash < 1) throw new IllegalArgumentException("Invalid ABC meter: " + value);
            String numerator = cleaned.substring(0, slash).replace("(", "").replace(")", "");
            int sum = 0;
            for (String part : numerator.split("\\+")) sum = Math.addExact(sum, Integer.parseInt(part.strip()));
            int denominator = Integer.parseInt(cleaned.substring(slash + 1).strip());
            if (sum < 1 || sum > 32 || denominator < 1 || denominator > 64
                    || Integer.bitCount(denominator) != 1) {
                throw new IllegalArgumentException("Unsupported ABC meter: " + value);
            }
            meterNumerator = sum;
            meterDenominator = denominator;
        }

        private void unit(String value) {
            unit = Fraction.parse(value, "ABC unit note length");
            if (unit.compareTo(Fraction.of(1, 128)) < 0) {
                throw new IllegalArgumentException("ABC unit note length is shorter than 1/128");
            }
        }

        private void tempo(String value) {
            String cleaned = value.replaceAll("\"[^\"]*\"", " ").strip();
            int equals = cleaned.lastIndexOf('=');
            String bpmText = equals < 0 ? cleaned : cleaned.substring(equals + 1).strip();
            if (!bpmText.matches("\\d+")) return;
            int bpm = Integer.parseInt(bpmText);
            if (bpm < 1 || bpm > 3_000) throw new IllegalArgumentException("Invalid ABC tempo");
            String beats = equals < 0 ? "" : cleaned.substring(0, equals).strip();
            Fraction beat = Fraction.ZERO;
            if (!beats.isEmpty()) {
                if (beats.equalsIgnoreCase("C")) beat = Fraction.of(1, 4);
                else for (String part : beats.split("\\s+")) {
                    beat = beat.add(Fraction.parse(part, "ABC tempo beat"));
                }
            }
            if (beat.signum() == 0) beat = Fraction.of(1, 4);
            Fraction quarterLength = beat.multiply(4);
            double rate = bpm * quarterLength.toDouble();
            microsPerQuarter = Math.clamp((int) Math.round(60_000_000.0 / rate), 1, 60_000_000);
        }

        private void key(String value) {
            String cleaned = value.strip();
            parseModifiers(cleaned);
            java.util.Arrays.fill(keyOverrides, -99);
            String token = cleaned.split("\\s+", 2)[0];
            if (token.isBlank() || token.equalsIgnoreCase("none") || isClef(token)) {
                fifths = 0;
                return;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^([A-Ga-g])([#b]?)(.*)$").matcher(token);
            if (!matcher.matches()) throw new IllegalArgumentException("Invalid ABC key: " + value);
            String root = matcher.group(1).toUpperCase(Locale.ROOT) + matcher.group(2);
            String mode = matcher.group(3).toLowerCase(Locale.ROOT);
            int rootFifths = switch (root) {
                case "Cb" -> -7; case "Gb" -> -6; case "Db" -> -5; case "Ab" -> -4;
                case "Eb" -> -3; case "Bb" -> -2; case "F" -> -1; case "C" -> 0;
                case "G" -> 1; case "D" -> 2; case "A" -> 3; case "E" -> 4;
                case "B" -> 5; case "F#" -> 6; case "C#" -> 7;
                default -> throw new IllegalArgumentException("Unsupported ABC key: " + root);
            };
            int adjustment = mode.isEmpty() || mode.startsWith("maj") || mode.startsWith("ion") ? 0
                    : mode.equals("m") || mode.startsWith("min") || mode.startsWith("aeo") ? -3
                    : mode.startsWith("mix") ? -1 : mode.startsWith("dor") ? -2
                    : mode.startsWith("phr") ? -4 : mode.startsWith("lyd") ? 1
                    : mode.startsWith("loc") ? -5
                    : throwMode(mode);
            int signatureFifths = rootFifths + adjustment;
            if (signatureFifths < -7 || signatureFifths > 7) {
                throw new IllegalArgumentException("ABC key requires more than seven accidentals: " + token);
            }
            fifths = signatureFifths;
            java.util.regex.Matcher explicit = java.util.regex.Pattern
                    .compile("(?<!\\S)(\\^{1,2}|_{1,2}|=)([A-Ga-g])(?!\\S)")
                    .matcher(cleaned);
            while (explicit.find()) {
                String accidental = explicit.group(1);
                int letter = "CDEFGAB".indexOf(explicit.group(2).toUpperCase(Locale.ROOT));
                keyOverrides[letter] = accidental.equals("=") ? 0
                        : accidental.charAt(0) == '^' ? accidental.length() : -accidental.length();
            }
        }

        private static int throwMode(String mode) {
            throw new IllegalArgumentException("Unsupported ABC mode: " + mode);
        }

        private void parseModifiers(String value) {
            java.util.regex.Matcher transposeMatcher = java.util.regex.Pattern
                    .compile("(?:^|\\s)(?:transpose|t)=(-?\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(value);
            if (transposeMatcher.find()) transpose = Integer.parseInt(transposeMatcher.group(1));
            java.util.regex.Matcher octaveMatcher = java.util.regex.Pattern
                    .compile("(?:^|\\s)octave=(-?\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(value);
            if (octaveMatcher.find()) octave = Integer.parseInt(octaveMatcher.group(1));
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.matches(".*(?:treble|bass|alto|tenor)[+-]8.*")) {
                octave += lower.matches(".*(?:treble|bass|alto|tenor)\\+8.*") ? 1 : -1;
            }
            if (transpose < -96 || transpose > 96 || octave < -8 || octave > 8) {
                throw new IllegalArgumentException("ABC transposition is outside Music Maker's range");
            }
        }

        private static boolean isClef(String token) {
            String lower = token.toLowerCase(Locale.ROOT);
            return lower.startsWith("treble") || lower.startsWith("bass")
                    || lower.startsWith("alto") || lower.startsWith("tenor")
                    || lower.equals("perc") || lower.equals("none");
        }

        private int keyAccidental(int letterIndex) {
            if (keyOverrides[letterIndex] != -99) return keyOverrides[letterIndex];
            int[] sharps = {3, 0, 4, 1, 5, 2, 6}; // F C G D A E B
            int[] flats = {6, 2, 5, 1, 4, 0, 3};  // B E A D G C F
            if (fifths > 0) {
                for (int index = 0; index < fifths; index++) if (sharps[index] == letterIndex) return 1;
            } else {
                for (int index = 0; index < -fifths; index++) if (flats[index] == letterIndex) return -1;
            }
            return 0;
        }
    }

    private static final class VoiceSource {
        private final String id;
        private String name;
        private final Settings settings;
        private final StringBuilder music = new StringBuilder();
        private boolean muted;
        private Integer declaredTranspose;
        private Integer declaredOctave;

        private VoiceSource(String id, String name, Settings settings) {
            this.id = id;
            this.name = name;
            this.settings = settings;
        }

        private void apply(VoiceDefinition definition) {
            if (!definition.name.isBlank()) name = definition.name;
            muted |= definition.muted;
            if (definition.transpose != 0) {
                if (declaredTranspose != null && declaredTranspose != definition.transpose) {
                    throw new IllegalArgumentException("Conflicting transpose values for ABC voice " + id);
                }
                if (declaredTranspose == null) settings.transpose += definition.transpose;
                declaredTranspose = definition.transpose;
            }
            if (definition.octave != 0) {
                if (declaredOctave != null && declaredOctave != definition.octave) {
                    throw new IllegalArgumentException("Conflicting octave values for ABC voice " + id);
                }
                if (declaredOctave == null) settings.octave += definition.octave;
                declaredOctave = definition.octave;
            }
            if (settings.transpose < -96 || settings.transpose > 96
                    || settings.octave < -8 || settings.octave > 8) {
                throw new IllegalArgumentException("ABC voice transposition is outside Music Maker's range");
            }
        }

        private void applyHeader(Settings header) {
            int voiceTranspose = declaredTranspose == null ? 0 : declaredTranspose;
            int voiceOctave = declaredOctave == null ? 0 : declaredOctave;
            settings.copyFrom(header);
            settings.transpose += voiceTranspose;
            settings.octave += voiceOctave;
        }
    }

    private record VoiceDefinition(String id, String name, int transpose, int octave, boolean muted) {
        private static VoiceDefinition parse(String value) {
            String cleaned = value.strip();
            if (cleaned.isEmpty()) return new VoiceDefinition("1", "", 0, 0, false);
            int space = cleaned.indexOf(' ');
            String id = space < 0 ? cleaned : cleaned.substring(0, space);
            if (id.length() > 32 || !id.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException("Invalid ABC voice ID: " + id);
            }
            String params = space < 0 ? "" : cleaned.substring(space + 1);
            String name = truncate(parameter(params, "name", parameter(params, "nm", "")), 96);
            int transpose = integerParameter(params, "transpose", integerParameter(params, "t", 0));
            int octave = integerParameter(params, "octave", 0);
            boolean muted = java.util.regex.Pattern.compile(
                    "(?:^|\\s)mute(?:=(?:1|true))?(?:\\s|$)",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(params).find();
            return new VoiceDefinition(id, name, transpose, octave, muted);
        }

        private static String parameter(String source, String key, String fallback) {
            java.util.regex.Matcher quoted = java.util.regex.Pattern.compile(
                    "(?:^|\\s)" + key + "=\"([^\"]*)\"", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(source);
            if (quoted.find()) return quoted.group(1);
            java.util.regex.Matcher plain = java.util.regex.Pattern.compile(
                    "(?:^|\\s)" + key + "=([^\\s]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(source);
            return plain.find() ? plain.group(1) : fallback;
        }

        private static int integerParameter(String source, String key, int fallback) {
            String value = parameter(source, key, "");
            return value.isEmpty() ? fallback : Integer.parseInt(value);
        }
    }

    private record PitchResult(int pitch) { }
    private record ChordMember(int pitch, Fraction length, boolean tieAfter,
                               boolean staccato, boolean accent, int velocity) { }
    private record ChordDecoration(boolean staccato, boolean accent, int velocity) { }
    private record DecorationEffect(Integer velocity, boolean staccato, boolean accent) { }
    private record NoteKey(int letter, int octave) { }
    private record Suffix(boolean tie, Fraction currentScale, Fraction nextScale) { }

    private static final class MutableNote {
        private final int pitch;
        private final int velocity;
        private final long start;
        private long release;
        private long end;

        private MutableNote(int pitch, int velocity, long start, long release, long end) {
            this.pitch = pitch;
            this.velocity = velocity;
            this.start = start;
            this.release = release;
            this.end = end;
        }
    }

    /** Small reduced rational used to keep ABC timing exact until tick emission. */
    private record Fraction(long numerator, long denominator) implements Comparable<Fraction> {
        private static final Fraction ZERO = new Fraction(0, 1);
        private static final Fraction ONE = new Fraction(1, 1);

        private Fraction {
            if (denominator == 0) throw new IllegalArgumentException("zero denominator");
            if (denominator < 0) { numerator = -numerator; denominator = -denominator; }
            long gcd = BigInteger.valueOf(numerator).gcd(BigInteger.valueOf(denominator)).longValue();
            numerator /= gcd;
            denominator /= gcd;
        }

        private static Fraction of(long numerator, long denominator) {
            return new Fraction(numerator, denominator);
        }

        private static Fraction parse(String source, String label) {
            String value = source.strip();
            int slash = value.indexOf('/');
            try {
                long numerator = slash < 0 ? Long.parseLong(value)
                        : Long.parseLong(value.substring(0, slash).strip());
                long denominator = slash < 0 ? 1
                        : Long.parseLong(value.substring(slash + 1).strip());
                if (numerator < 1 || denominator < 1) throw new NumberFormatException();
                return of(numerator, denominator);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid " + label + ": " + source, exception);
            }
        }

        private Fraction add(Fraction other) {
            long gcd = BigInteger.valueOf(denominator).gcd(BigInteger.valueOf(other.denominator)).longValue();
            long left = other.denominator / gcd;
            long right = denominator / gcd;
            return of(Math.addExact(Math.multiplyExact(numerator, left),
                            Math.multiplyExact(other.numerator, right)),
                    Math.multiplyExact(denominator, left));
        }

        private Fraction multiply(Fraction other) {
            return of(Math.multiplyExact(numerator, other.numerator),
                    Math.multiplyExact(denominator, other.denominator));
        }

        private Fraction multiply(long value) { return of(Math.multiplyExact(numerator, value), denominator); }
        private Fraction divide(long value) { return of(numerator, Math.multiplyExact(denominator, value)); }
        private Fraction min(Fraction other) { return compareTo(other) <= 0 ? this : other; }
        private Fraction max(Fraction other) { return compareTo(other) >= 0 ? this : other; }
        private int signum() { return Long.compare(numerator, 0); }
        private double toDouble() { return (double) numerator / denominator; }

        private long roundLong() {
            return Math.floorDiv(Math.addExact(Math.multiplyExact(numerator, 2), denominator),
                    Math.multiplyExact(denominator, 2));
        }

        @Override public int compareTo(Fraction other) {
            return BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(other.denominator))
                    .compareTo(BigInteger.valueOf(other.numerator).multiply(BigInteger.valueOf(denominator)));
        }
    }
}
