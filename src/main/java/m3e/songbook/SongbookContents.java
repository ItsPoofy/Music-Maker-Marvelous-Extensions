package m3e.songbook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Immutable, bounded bulk state persisted on the server-side Songbook ItemStack. */
public record SongbookContents(int formatVersion, UUID bookId, int revision, int capacity,
                               List<SongbookEntry> entries) {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_CAPACITY = 255;
    public static final int MAX_ENCODED_BYTES = 1024 * 1024;
    public static final Codec<SongbookContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("format").forGetter(SongbookContents::formatVersion),
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(SongbookContents::bookId),
            Codec.INT.fieldOf("revision").forGetter(SongbookContents::revision),
            Codec.INT.fieldOf("capacity").forGetter(SongbookContents::capacity),
            SongbookEntry.CODEC.listOf().fieldOf("entries").forGetter(SongbookContents::entries)
    ).apply(instance, SongbookContents::new));

    public SongbookContents {
        // Repair the short-lived format-1 builds that allowed extracting the
        // final physical page.  A Songbook always owns at least its bound
        // cover page, including when that page has no song on it.
        capacity = Math.max(1, capacity);
        if (formatVersion != FORMAT_VERSION || bookId == null || revision < 0
                || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("invalid Songbook contents");
        }
        entries = List.copyOf(entries == null ? List.of() : entries);
        if (entries.size() > capacity) throw new IllegalArgumentException("Songbook has more songs than pages");
        HashSet<UUID> ids = new HashSet<>();
        int bytes = 64;
        for (SongbookEntry entry : entries) {
            if (!ids.add(entry.entryId())) throw new IllegalArgumentException("duplicate Songbook entry id");
            bytes = Math.addExact(bytes, entry.encodedBytes());
        }
        if (bytes > MAX_ENCODED_BYTES) throw new IllegalArgumentException("Songbook exceeds 1 MiB");
    }

    public static SongbookContents create(int capacity) {
        return new SongbookContents(FORMAT_VERSION, UUID.randomUUID(), 0, capacity, List.of());
    }

    public int encodedBytes() {
        int bytes = 64;
        for (SongbookEntry entry : entries) bytes += entry.encodedBytes();
        return bytes;
    }

    public SongbookHeader header() {
        return new SongbookHeader(bookId, revision, capacity, entries.size(), encodedBytes());
    }

    public SongbookContents withCapacity(int value) {
        return new SongbookContents(formatVersion, bookId, revision + 1, value, entries);
    }

    public SongbookContents add(SongbookEntry entry, boolean restoreExtractedPage) {
        int updatedCapacity = restoreExtractedPage ? capacity + 1 : capacity;
        if (restoreExtractedPage && updatedCapacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("Songbook is already at maximum capacity");
        }
        if (entries.size() >= updatedCapacity) throw new IllegalArgumentException("Songbook has no empty page");
        ArrayList<SongbookEntry> updated = new ArrayList<>(entries);
        updated.add(entry);
        return new SongbookContents(formatVersion, bookId, revision + 1, updatedCapacity, updated);
    }

    public SongbookContents addAll(List<SongbookEntry> additions) {
        List<SongbookEntry> safe = List.copyOf(additions == null ? List.of() : additions);
        if (safe.isEmpty()) throw new IllegalArgumentException("No songs were selected");
        if (safe.size() > capacity - entries.size()) {
            throw new IllegalArgumentException("Songbook does not have enough empty pages");
        }
        ArrayList<SongbookEntry> updated = new ArrayList<>(entries);
        updated.addAll(safe);
        return new SongbookContents(formatVersion, bookId, revision + 1, capacity, updated);
    }

    public SongbookContents replace(SongbookEntry replacement) {
        ArrayList<SongbookEntry> updated = new ArrayList<>(entries);
        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).entryId().equals(replacement.entryId())) {
                updated.set(index, replacement);
                return new SongbookContents(formatVersion, bookId, revision + 1, capacity, updated);
            }
        }
        throw new IllegalArgumentException("Songbook song is missing");
    }

    public SongbookContents remove(UUID entryId, boolean extractPage) {
        if (extractPage && capacity <= 1) {
            throw new IllegalArgumentException("A Songbook must keep at least one page; delete the song instead");
        }
        ArrayList<SongbookEntry> updated = new ArrayList<>(entries);
        if (!updated.removeIf(entry -> entry.entryId().equals(entryId))) {
            throw new IllegalArgumentException("Songbook song is missing");
        }
        int updatedCapacity = capacity - (extractPage ? 1 : 0);
        return new SongbookContents(formatVersion, bookId, revision + 1, updatedCapacity, updated);
    }

    public SongbookContents move(UUID entryId, int targetIndex) {
        ArrayList<SongbookEntry> updated = new ArrayList<>(entries);
        int sourceIndex = -1;
        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).entryId().equals(entryId)) {
                sourceIndex = index;
                break;
            }
        }
        if (sourceIndex < 0) throw new IllegalArgumentException("Songbook song is missing");
        int destination = Math.clamp(targetIndex, 0, updated.size() - 1);
        if (sourceIndex == destination) return this;
        SongbookEntry moved = updated.remove(sourceIndex);
        updated.add(destination, moved);
        return new SongbookContents(formatVersion, bookId, revision + 1, capacity, updated);
    }
}
