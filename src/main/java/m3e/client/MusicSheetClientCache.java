package m3e.client;

import m3e.network.MusicSheetDataPacket;
import m3e.network.MusicSheetRequestPacket;
import m3e.sheet.MusicScore;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.item.Items;
import m3e.sheet.MusicSheetComponents;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client cache and sheet-identity binding used without changing Music Maker classes. */
public final class MusicSheetClientCache {
    private static final int MAX_CACHED_SHEETS = 256;
    private static final int MAX_CACHED_EVENTS = 262_144;
    private static final int MAX_PENDING_REQUESTS = 64;
    private static final int MAX_KNOWN_ABSENT = 256;
    private record Entry(int version, MusicScore song) { }
    private static final Map<UUID, Entry> BY_ID = new ConcurrentHashMap<>();
    private static final Map<List<NoteEvent>, MusicSheetPlaybackContext.SheetKey> NOTE_LIST_BINDINGS =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();
    private static final Set<String> KNOWN_ABSENT = ConcurrentHashMap.newKeySet();

    private MusicSheetClientCache() { }

    public static void request(UUID id, int version) {
        Entry cached = BY_ID.get(id);
        if (cached != null && cached.version == version) return;
        if (KNOWN_ABSENT.contains(key(id, version))) return;
        if (!PENDING.containsKey(id) && PENDING.size() >= MAX_PENDING_REQUESTS) return;
        if (PENDING.putIfAbsent(id, version) == null) {
            ClientPlayNetworking.send(new MusicSheetRequestPacket(id, version));
        }
    }

    public static void receive(MusicSheetDataPacket packet) {
        PENDING.remove(packet.sheetId());
        BY_ID.remove(packet.sheetId());
        if (packet.song() == null) {
            removeBindings(packet.sheetId(), -1);
            KNOWN_ABSENT.add(key(packet.sheetId(), packet.version()));
            trimKnownAbsent();
            return;
        }
        removeBindings(packet.sheetId(), packet.version());
        KNOWN_ABSENT.remove(key(packet.sheetId(), packet.version()));
        BY_ID.put(packet.sheetId(), new Entry(packet.version(), packet.song()));
        trimCache(packet.sheetId());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui != null
                && minecraft.gui.screen() instanceof PianoRollScreen screen) {
            screen.acceptImportedScore(packet.sheetId(), packet.version(), packet.song());
        }
    }

    public static MusicScore get(UUID id) {
        Entry entry = BY_ID.get(id);
        return entry == null ? null : entry.song;
    }

    public static int version(UUID id) {
        Entry entry = BY_ID.get(id);
        return entry == null ? -1 : entry.version();
    }

    public static @Nullable MusicScore resolve(ItemStack sheet) {
        UUID id = sheet.get(Items.SHEET_ID);
        if (id == null) return null;
        int version = sheet.getOrDefault(Items.SHEET_VERSION, -1);
        MusicScore embedded = sheet.get(MusicSheetComponents.SCORE);
        if (embedded != null) {
            receiveLocal(id, version, embedded);
            return embedded;
        }
        Entry entry = BY_ID.get(id);
        if (entry != null && entry.version == version) return entry.song;
        request(id, version);
        return null;
    }

    public static boolean contains(UUID id) {
        return BY_ID.containsKey(id);
    }

    public static @Nullable MusicScore updateTempo(UUID id, int version, int bpm) {
        Entry entry = BY_ID.get(id);
        if (entry == null || entry.version != version) return null;
        MusicScore updated = entry.song.withInitialBpm(bpm);
        BY_ID.put(id, new Entry(version, updated));
        return updated;
    }

    public static void bindCurrentLookup(List<xerca.xercamusic.common.NoteEvent> notes) {
        MusicSheetPlaybackContext.SheetKey current = MusicSheetPlaybackContext.current();
        if (current != null) NOTE_LIST_BINDINGS.put(notes, current);
    }

    public static MusicScore findBound(List<xerca.xercamusic.common.NoteEvent> notes, int bps) {
        MusicSheetPlaybackContext.SheetKey key = NOTE_LIST_BINDINGS.get(notes);
        if (key == null) return null;
        Entry entry = BY_ID.get(key.id());
        if (entry == null || entry.version != key.version()) return null;
        return entry.song;
    }

    public static boolean hasPendingRequest() {
        return !PENDING.isEmpty();
    }

    public static void clear() {
        BY_ID.clear();
        NOTE_LIST_BINDINGS.clear();
        PENDING.clear();
        KNOWN_ABSENT.clear();
    }

    private static void remove(UUID id) {
        BY_ID.remove(id);
        removeBindings(id, -1);
    }

    public static void receiveLocal(UUID id, int version, MusicScore song) {
        if (id == null || version < 0 || song == null) return;
        removeBindings(id, version);
        BY_ID.put(id, new Entry(version, song));
        KNOWN_ABSENT.remove(key(id, version));
        PENDING.remove(id);
        trimCache(id);
    }

    private static void trimCache(UUID retainedId) {
        while (BY_ID.size() > MAX_CACHED_SHEETS || cachedEvents() > MAX_CACHED_EVENTS) {
            UUID evicted = BY_ID.keySet().stream()
                    .filter(id -> !id.equals(retainedId))
                    .findFirst().orElse(null);
            if (evicted == null) return;
            remove(evicted);
        }
    }

    private static long cachedEvents() {
        long total = 0L;
        for (Entry entry : BY_ID.values()) {
            MusicScore song = entry.song();
            total += song.notes().size() + song.expressions().size()
                    + song.tempos().size() + song.timeSignatures().size();
        }
        return total;
    }

    private static void trimKnownAbsent() {
        while (KNOWN_ABSENT.size() > MAX_KNOWN_ABSENT) {
            String evicted = KNOWN_ABSENT.stream().findFirst().orElse(null);
            if (evicted == null) return;
            KNOWN_ABSENT.remove(evicted);
        }
    }

    private static void removeBindings(UUID id, int retainedVersion) {
        synchronized (NOTE_LIST_BINDINGS) {
            NOTE_LIST_BINDINGS.entrySet().removeIf(entry -> entry.getValue().id().equals(id)
                    && entry.getValue().version() != retainedVersion);
        }
    }

    private static String key(UUID id, int version) {
        return id + ":" + version;
    }
}
