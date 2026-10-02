package m3e.sheet;

import m3e.session.DrumNoteMap;
import m3e.songbook.SongbookSongCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bounded wire document for an atomic set of independently imported score parts. */
public final class SheetBatchCodec {
    public static final int MAX_ENTRIES = 255;
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_NAME_BYTES = 512;

    public record Entry(String displayName, MusicScore song, List<Integer> drumMap) {
        public Entry {
            displayName = normalizedName(displayName);
            if (song == null) throw new IllegalArgumentException("missing sheet song");
            drumMap = drumMap == null ? List.of() : List.copyOf(drumMap);
            if (drumMap.size() > DrumNoteMap.SIZE) {
                throw new IllegalArgumentException("invalid drum map size");
            }
            if (!drumMap.isEmpty()) DrumNoteMap.validatedArray(drumMap);
        }
    }

    private SheetBatchCodec() { }

    public static byte[] encode(List<Entry> entries) {
        List<Entry> safe = validatedEntries(entries);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(FORMAT_VERSION);
                output.writeInt(safe.size());
                for (Entry entry : safe) {
                    byte[] name = entry.displayName().getBytes(StandardCharsets.UTF_8);
                    byte[] song = SongbookSongCodec.encode(entry.song());
                    if (name.length > MAX_NAME_BYTES) {
                        throw new IllegalArgumentException("sheet name is too long");
                    }
                    output.writeInt(name.length);
                    output.write(name);
                    output.writeInt(song.length);
                    output.write(song);
                    output.writeInt(entry.drumMap().size());
                    for (int note : entry.drumMap()) output.writeInt(note);
                    if (bytes.size() > MAX_BYTES) {
                        throw new IllegalArgumentException("separate sheet import is larger than 16 MiB");
                    }
                }
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAX_BYTES) {
                throw new IllegalArgumentException("invalid separate sheet import size");
            }
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static List<Entry> decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("invalid separate sheet import size");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != FORMAT_VERSION) {
                throw new IllegalArgumentException("unsupported separate sheet import version");
            }
            int count = input.readInt();
            if (count < 1 || count > MAX_ENTRIES) {
                throw new IllegalArgumentException("invalid separate sheet count");
            }
            ArrayList<Entry> entries = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int nameLength = boundedLength(input.readInt(), MAX_NAME_BYTES, "sheet name");
                String name = new String(readExact(input, nameLength), StandardCharsets.UTF_8);
                int songLength = boundedLength(input.readInt(), MAX_BYTES, "sheet song");
                MusicScore song = SongbookSongCodec.decode(readExact(input, songLength));
                int mapLength = input.readInt();
                if (mapLength < 0 || mapLength > DrumNoteMap.SIZE) {
                    throw new IllegalArgumentException("invalid drum map size");
                }
                ArrayList<Integer> map = new ArrayList<>(mapLength);
                for (int mapIndex = 0; mapIndex < mapLength; mapIndex++) map.add(input.readInt());
                entries.add(new Entry(name, song, map));
            }
            if (input.read() != -1) throw new IllegalArgumentException("trailing separate sheet data");
            return List.copyOf(entries);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("incomplete separate sheet data", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("could not read separate sheet data", exception);
        }
    }

    private static List<Entry> validatedEntries(List<Entry> entries) {
        if (entries == null || entries.isEmpty() || entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("invalid separate sheet count");
        }
        return List.copyOf(entries);
    }

    private static int boundedLength(int length, int maximum, String label) {
        if (length < 1 || length > maximum) throw new IllegalArgumentException("invalid " + label + " size");
        return length;
    }

    private static byte[] readExact(DataInputStream input, int length) throws IOException {
        byte[] value = input.readNBytes(length);
        if (value.length != length) throw new EOFException();
        return value;
    }

    private static String normalizedName(String name) {
        String value = name == null ? "" : name.strip();
        if (value.isEmpty()) value = "Imported score";
        return value.substring(0, Math.min(96, value.length()));
    }
}
