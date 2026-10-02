package m3e.client;

import m3e.network.MusicSheetEditPacket;
import m3e.network.MusicSheetImportPacket;
import m3e.network.MusicSheetSaveResultPacket;
import m3e.sheet.MusicScore;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.IdentityHashMap;
import net.minecraft.world.item.ItemStack;
import xerca.xercamusic.common.item.Items;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Connection-owned drafts and correlated saves, independent of screen lifetime. */
final class SheetEditController {
    private static final SheetDraftStore DRAFTS = new SheetDraftStore();
    private static final Map<ItemStack, UUID> BLANK_BINDINGS = new IdentityHashMap<>();
    private static final Map<UUID, Consumer<MusicSheetSaveResultPacket>> CALLBACKS = new HashMap<>();
    private static final long TIMEOUT_NANOS = 15_000_000_000L;

    private SheetEditController() { }

    static UUID key(ItemStack sheet) {
        UUID id = sheet.get(Items.SHEET_ID);
        return id != null ? id : BLANK_BINDINGS.computeIfAbsent(sheet, ignored -> UUID.randomUUID());
    }
    static SheetDraftStore.Draft draft(UUID key) { return DRAFTS.get(key); }
    static void discard(UUID key) { DRAFTS.discard(key); }
    static boolean pending(UUID key) { return DRAFTS.pending(key); }

    static boolean save(UUID draftKey, UUID sheetId, int version, ScoreEditSession session, boolean importing,
                        boolean sign, int highlight, int instrument, boolean locked,
                        Consumer<MusicSheetSaveResultPacket> callback) {
        SheetDraftStore.Draft draft = new SheetDraftStore.Draft(session.song(), version, importing, sign, highlight, instrument, locked);
        if (pending(draftKey)) return false;
        DRAFTS.retain(draftKey, draft);
        if (importing ? !ClientPlayNetworking.canSend(MusicSheetImportPacket.TYPE)
                : !ClientPlayNetworking.canSend(MusicSheetEditPacket.TYPE)) {
            report("Sheet saving is unavailable. Your draft is retained.");
            return false;
        }
        SheetDraftStore.Pending save = DRAFTS.begin(draftKey, sheetId, session, draft, System.nanoTime());
        if (save == null) return false;
        UUID requestId = save.requestId();
        CALLBACKS.put(requestId, callback);
        try {
            if (importing) {
                MusicScore song = draft.song();
                Minecraft client = Minecraft.getInstance();
                if (sign && client.player != null) song = song.withMetadata(
                        client.player.getName().getString(), true, song.instrumentId());
                ClientPlayNetworking.send(new MusicSheetImportPacket(requestId, sheetId, version, song, List.of(),
                        highlight, instrument, locked));
            } else {
                ClientPlayNetworking.send(new MusicSheetEditPacket(requestId, sheetId, version,
                        draft.song(), highlight, sign, instrument, locked));
            }
        } catch (RuntimeException failure) {
            receive(new MusicSheetSaveResultPacket(requestId, false,
                    sheetId == null ? new UUID(0, 0) : sheetId, 0,
                    "Sheet save failed. Your draft is retained."));
            return false;
        }
        return true;
    }

    static boolean importHeld(MusicScore song, List<Integer> drumMap,
                              Consumer<MusicSheetSaveResultPacket> callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        ItemStack held = client.player.getMainHandItem();
        ScoreEditSession session = new ScoreEditSession(song.withDrumMap(drumMap));
        return save(key(held), held.get(Items.SHEET_ID), held.getOrDefault(Items.SHEET_VERSION, -1),
                session, true, false, 12, -1, false, callback);
    }

    static void receive(MusicSheetSaveResultPacket result) {
        SheetDraftStore.Pending save = DRAFTS.finish(result.requestId(), result.success(), result.sheetId(), result.version());
        if (save == null) return;
        if (!result.success()) report(result.message());
        Consumer<MusicSheetSaveResultPacket> callback = CALLBACKS.remove(result.requestId());
        if (callback != null) callback.accept(result);
    }

    static void tick() {
        for (SheetDraftStore.Pending save : DRAFTS.expired(System.nanoTime(), TIMEOUT_NANOS)) {
            receive(new MusicSheetSaveResultPacket(save.requestId(), false,
                    save.sheetId() == null ? new UUID(0, 0) : save.sheetId(), 0,
                    "Sheet save timed out. Your draft is retained; reopen the sheet to retry."));
        }
    }

    static void clear() { DRAFTS.clear(); CALLBACKS.clear(); BLANK_BINDINGS.clear(); }

    private static void report(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.player.sendSystemMessage(
                Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
