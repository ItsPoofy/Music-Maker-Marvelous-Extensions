package m3e.sheet;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import xerca.xercamusic.common.item.ItemMusicSheet;
import xerca.xercamusic.common.item.Items;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import m3e.network.MusicSheetDataPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import static xerca.xercamusic.common.item.ItemMusicSheet.KEY_ID;

/** Bounded one-shot authorization for a native Music Maker import from our file-drop UI. */
public final class LegacySheetDropImports {
    private static final long EXPIRY_NANOS = 30_000_000_000L;
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private LegacySheetDropImports() { }

    public static void mark(ServerPlayer player, UUID sheetId, @Nullable MusicScore exactSong) {
        PENDING.put(player.getUUID(), new Pending(sheetId, exactSong, System.nanoTime()));
    }

    public static @Nullable PendingImport consumeIfMatches(ServerPlayer player, CompoundTag tag) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) return null;
        if (System.nanoTime() - pending.createdNanos > EXPIRY_NANOS) {
            PENDING.remove(player.getUUID(), pending);
            return null;
        }
        UUID id = tag.read(KEY_ID, UUIDUtil.CODEC).orElse(null);
        if (!pending.sheetId.equals(id)) return null;
        if (!(player.getMainHandItem().getItem() instanceof ItemMusicSheet)) return null;
        PENDING.remove(player.getUUID(), pending);
        return new PendingImport(pending.sheetId, pending.exactSong);
    }

    public static void installExact(ServerPlayer player, CompoundTag tag, PendingImport pending) {
        MusicScore score = pending.exactSong() == null
                ? m3e.songbook.NativeSheetSnapshot.fromSheetFile(
                tag, player.level().getServer()).toPlaybackSong()
                : pending.exactSong();
        int version = tag.getIntOr(ItemMusicSheet.KEY_VERSION, 1);
        var held = player.getMainHandItem();
        held.set(Items.SHEET_ID, pending.sheetId());
        held.set(Items.SHEET_VERSION, version);
        held.set(Items.SHEET_GENERATION, tag.getIntOr(ItemMusicSheet.KEY_GENERATION, 0));
        held.set(Items.SHEET_TITLE, tag.getStringOr(ItemMusicSheet.KEY_TITLE, score.sourceName()));
        String author = tag.getStringOr(ItemMusicSheet.KEY_AUTHOR, "").strip();
        if (author.isEmpty()) held.remove(Items.SHEET_AUTHOR);
        else held.set(Items.SHEET_AUTHOR, author);
        if (tag.contains(ItemMusicSheet.KEY_PREV_INSTRUMENT)) {
            held.set(Items.SHEET_PREV_INSTRUMENT,
                    tag.getByteOr(ItemMusicSheet.KEY_PREV_INSTRUMENT, (byte) -1));
        } else held.remove(Items.SHEET_PREV_INSTRUMENT);
        held.set(Items.SHEET_PREV_INSTRUMENT_LOCKED,
                tag.getBooleanOr(ItemMusicSheet.KEY_PREV_INSTRUMENT_LOCKED, false));
        held.set(MusicSheetComponents.SCORE, score);
        held.set(Items.SHEET_VOLUME, 1.0F);
        held.set(MusicSheetComponents.SOURCE_BPM, score.initialBpm());
        SheetTiming.clearLegacyComponents(held);
        held.set(Items.SHEET_HIGHLIGHT_INTERVAL, (byte) 12);
        ItemMusicSheet.updateStackSize(held);
        ServerPlayNetworking.send(player, new MusicSheetDataPacket(
                pending.sheetId(), version, score));
    }

    public static void clear(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    public record PendingImport(UUID sheetId, @Nullable MusicScore exactSong) { }
    private record Pending(UUID sheetId, @Nullable MusicScore exactSong, long createdNanos) { }
}
