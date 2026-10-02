package m3e.sheet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import m3e.songbook.SongbookSongCodec;
import net.minecraft.world.item.ItemStack;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;
import xerca.xercamusic.common.item.ItemMusicSheet;
import xerca.xercamusic.common.item.Items;

/** Bounded reader shared by direct sheet drops and Songbook imports. */
public final class SheetFileIO {
    public static final String FORMAT_KEY = "m3e_format";
    public static final String SCORE_KEY = "m3e_score";
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final long MAX_DECODED_NBT_BYTES = MAX_FILE_BYTES * 4L;

    private SheetFileIO() { }

    public static byte[] readBytes(Path path) throws IOException {
        long size = Files.size(path);
        if (size < 1L || size > MAX_FILE_BYTES) {
            throw new IOException("The .sheet file is empty or exceeds 1 MiB");
        }
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < 1 || bytes.length > MAX_FILE_BYTES) {
            throw new IOException("The .sheet file changed size or exceeds 1 MiB");
        }
        return bytes;
    }

    public static CompoundTag read(Path path) throws IOException {
        return read(readBytes(path));
    }

    public static CompoundTag read(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 1 || bytes.length > MAX_FILE_BYTES) {
            throw new IOException("The .sheet file is empty or exceeds 1 MiB");
        }
        if (bytes.length >= 2 && Byte.toUnsignedInt(bytes[0]) == 0x1F
                && Byte.toUnsignedInt(bytes[1]) == 0x8B) {
            return readCompressed(bytes, null);
        }
        Exception uncompressedFailure;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            CompoundTag tag = NbtIo.read(input, NbtAccounter.create(MAX_DECODED_NBT_BYTES));
            if (tag == null) throw new IOException("The file is not a valid Music Maker sheet");
            if (input.available() != 0) throw new IOException("Trailing sheet data");
            return tag;
        } catch (IOException | RuntimeException exception) {
            uncompressedFailure = exception;
        }
        return readCompressed(bytes, uncompressedFailure);
    }

    public static MusicScore score(CompoundTag tag) {
        byte[] encoded = tag.getByteArray(SCORE_KEY).orElse(null);
        if (encoded != null && encoded.length > 0) return SongbookSongCodec.decode(encoded);
        return PortableSheetSidecar.read(tag);
    }

    public static void writeScore(Path path, MusicScore score) throws IOException {
        if (score == null) throw new IOException("The sheet has no M3E score");
        CompoundTag root = new CompoundTag();
        root.putInt(FORMAT_KEY, MusicScore.FORMAT_VERSION);
        root.putByteArray(SCORE_KEY, SongbookSongCodec.encode(score));
        Files.createDirectories(path.toAbsolutePath().getParent());
        NbtIo.write(root, path);
        if (Files.size(path) < 1L || Files.size(path) > MAX_FILE_BYTES) {
            Files.deleteIfExists(path);
            throw new IOException("The M3E sheet exceeds 1 MiB");
        }
    }

    public static void writeCompatibility(Path path, ItemStack sheet, MusicScore score)
            throws IOException {
        MusicScore.Projection projection = score.compatibilityExportView();
        CompoundTag root = new CompoundTag();
        NoteEvent.fillNBTFromArray(projection.notes(), root);
        if (!projection.volumeMarkers().isEmpty()) {
            VolumeMarker.fillNBTFromArray(projection.volumeMarkers(), root);
        }
        root.putByte(ItemMusicSheet.KEY_BPS, (byte) projection.bps());
        root.putInt(ItemMusicSheet.KEY_LENGTH, projection.length());
        root.putFloat(ItemMusicSheet.KEY_VOLUME,
                sheet.getOrDefault(Items.SHEET_VOLUME, 1.0F));
        root.putInt(ItemMusicSheet.KEY_GENERATION,
                sheet.getOrDefault(Items.SHEET_GENERATION, 0));
        root.putString(ItemMusicSheet.KEY_TITLE,
                sheet.getOrDefault(Items.SHEET_TITLE, ""));
        String author = sheet.get(Items.SHEET_AUTHOR);
        if (author != null && !author.isBlank()) root.putString(ItemMusicSheet.KEY_AUTHOR, author);
        Files.createDirectories(path.toAbsolutePath().getParent());
        NbtIo.write(root, path);
        if (Files.size(path) < 1L || Files.size(path) > MAX_FILE_BYTES) {
            Files.deleteIfExists(path);
            throw new IOException("The compatibility sheet exceeds 1 MiB");
        }
    }

    private static CompoundTag readCompressed(byte[] bytes, Exception uncompressedFailure)
            throws IOException {
        try {
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes),
                    NbtAccounter.create(MAX_DECODED_NBT_BYTES));
            if (tag == null) throw new IOException("The file is not a valid Music Maker sheet");
            return tag;
        } catch (IOException | RuntimeException compressedFailure) {
            if (uncompressedFailure != null) compressedFailure.addSuppressed(uncompressedFailure);
            if (compressedFailure instanceof IOException ioException) throw ioException;
            throw new IOException("The file is not a valid Music Maker sheet", compressedFailure);
        }
    }
}
