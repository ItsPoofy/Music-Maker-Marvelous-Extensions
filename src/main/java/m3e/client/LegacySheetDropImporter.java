package m3e.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import m3e.network.LegacySheetDropIntentPacket;
import m3e.sheet.MusicScore;
import m3e.sheet.SheetFileIO;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.packets.clientbound.NotesPartAckFromServerPacketHandler;
import xerca.xercamusic.common.packets.serverbound.ImportMusicSendPacket;
import xerca.xercamusic.common.packets.serverbound.SendNotesPartToServerPacket;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import static xerca.xercamusic.client.ModClient.sendToServer;
import static xerca.xercamusic.common.Mod.MAX_NOTES_IN_PACKET;
import static xerca.xercamusic.common.item.ItemMusicSheet.KEY_NOTES;
import static xerca.xercamusic.common.item.ItemMusicSheet.KEY_ID;
import static xerca.xercamusic.common.item.ItemMusicSheet.KEY_VERSION;
import static xerca.xercamusic.common.item.ItemMusicSheet.KEY_GENERATION;

/** Direct-file equivalent of Music Maker's /musicimport command. */
public final class LegacySheetDropImporter {
    private static UUID pendingSheetId;

    private LegacySheetDropImporter() { }

    static void importPath(Path path, BooleanSupplier beforeSend) {
        Minecraft minecraft = Minecraft.getInstance();
        long generation = AsyncMusicImport.begin();
        CompletableFuture.supplyAsync(() -> {
            try {
                return SheetFileIO.read(path);
            } catch (IOException | RuntimeException exception) {
                throw new SheetReadException(exception);
            }
        }).whenComplete((tag, error) -> minecraft.execute(() -> {
            if (!AsyncMusicImport.isCurrent(generation)) return;
            if (error != null) {
                reportFailure(error);
                return;
            }
            if (minecraft.player == null
                    || !beforeSend.getAsBoolean()) return;
            try {
                send(tag);
            } catch (IOException | ImportMusicSendPacket.NotesTooLargeException | RuntimeException exception) {
                reportFailure(exception);
            }
        }));
    }

    private static void send(CompoundTag tag)
            throws IOException, ImportMusicSendPacket.NotesTooLargeException {
        MusicScore canonical = SheetFileIO.score(tag);
        if (tag.contains(SheetFileIO.SCORE_KEY) && canonical != null) {
            if (Minecraft.getInstance().gui.screen() instanceof PianoRollScreen screen) {
                screen.importScore(canonical, List.of());
                return;
            }
            SheetEditController.importHeld(canonical, List.of(), result -> {
                if (result.success()) xerca.xercamusic.client.ModClient.showMusicGui();
            });
            return;
        }
        UUID sheetId = ensureSheetId(tag);
        MusicScore exact = canonical;
        pendingSheetId = sheetId;
        ClientPlayNetworking.send(new LegacySheetDropIntentPacket(sheetId, exact));
        sendExactlyAsMusicMaker(tag);
    }

    private static void reportFailure(Throwable failure) {
        Throwable cause = failure.getCause() instanceof SheetReadException wrapped
                ? wrapped.getCause() : failure;
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(
                            "Sheet import failed: " + safeMessage(cause))
                    .withStyle(ChatFormatting.RED));
        }
    }

    public static void reopenWhenNativeDataArrives(UUID sheetId) {
        if (!sheetId.equals(pendingSheetId)) return;
        pendingSheetId = null;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(xerca.xercamusic.client.ModClient::showMusicGui);
    }

    private static UUID ensureSheetId(CompoundTag tag) {
        UUID id = tag.read(KEY_ID, UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        tag.store(KEY_ID, UUIDUtil.CODEC, id);
        if (tag.getInt(KEY_VERSION).isEmpty()) tag.putInt(KEY_VERSION, 1);
        if (tag.getInt(KEY_GENERATION).isEmpty()) tag.putInt(KEY_GENERATION, 0);
        return id;
    }

    private static void sendExactlyAsMusicMaker(CompoundTag tag)
            throws ImportMusicSendPacket.NotesTooLargeException, IOException {
        try {
            sendToServer(ImportMusicSendPacket.create(tag));
        } catch (ImportMusicSendPacket.NotesTooLargeException large) {
            if (large.id == null) throw new IOException("Large sheet has no UUID");
            List<NoteEvent> notes = large.getNotes();
            int parts = (int) Math.ceil((double) notes.size() / MAX_NOTES_IN_PACKET);
            tag.remove(KEY_NOTES);
            ImportMusicSendPacket metadata = ImportMusicSendPacket.create(tag);
            NotesPartAckFromServerPacketHandler.addCallback(large.id, () -> sendToServer(metadata));
            for (int part = 0; part < parts; part++) {
                int from = part * MAX_NOTES_IN_PACKET;
                int to = Math.min((part + 1) * MAX_NOTES_IN_PACKET, notes.size());
                sendToServer(new SendNotesPartToServerPacket(large.id, parts, part, notes.subList(from, to)));
            }
        }
    }

    private static String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName()
                : message.replace('\n', ' ').substring(0, Math.min(160, message.length()));
    }

    private static final class SheetReadException extends RuntimeException {
        private SheetReadException(Throwable cause) { super(cause); }
    }
}
