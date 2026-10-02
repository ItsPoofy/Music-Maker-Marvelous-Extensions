package m3e.client;

import m3e.network.MusicSheetTempoUpdatePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/** Coalesces rapid tempo-button presses before they reach the server. */
public final class SheetTempoUpdateController {
    private static final long SEND_DELAY_NANOS = 150_000_000L;

    private static UUID sheetId;
    private static int version;
    private static int bpm;
    private static long sendAfterNanos;

    private SheetTempoUpdateController() { }

    public static void queue(UUID id, int sheetVersion, int targetBpm) {
        sheetId = id;
        version = sheetVersion;
        bpm = targetBpm;
        sendAfterNanos = System.nanoTime() + SEND_DELAY_NANOS;
    }

    public static void tick(Minecraft client) {
        if (sheetId == null || System.nanoTime() < sendAfterNanos) return;
        UUID pendingId = sheetId;
        int pendingVersion = version;
        int pendingBpm = bpm;
        clear();
        if (client.getConnection() != null
                && ClientPlayNetworking.canSend(MusicSheetTempoUpdatePacket.TYPE)) {
            ClientPlayNetworking.send(new MusicSheetTempoUpdatePacket(
                    pendingId, pendingVersion, pendingBpm));
        }
    }

    public static void clear() {
        sheetId = null;
        version = 0;
        bpm = 0;
        sendAfterNanos = 0L;
    }
}
