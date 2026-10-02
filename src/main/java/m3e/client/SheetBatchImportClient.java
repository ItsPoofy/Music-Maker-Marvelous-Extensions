package m3e.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import m3e.network.SheetBatchResultPacket;
import m3e.network.SheetBatchResultAckPacket;
import m3e.network.SheetBatchUploadCancelPacket;
import m3e.network.SheetBatchUploadChunkPacket;
import m3e.network.SheetBatchUploadFinishPacket;
import m3e.network.SheetBatchUploadStartPacket;
import m3e.sheet.SheetBatchCodec;
import m3e.songbook.SongbookHash;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Client-owned, cancellable sender for one atomic separate-import transaction. */
final class SheetBatchImportClient {
    interface Listener {
        void progress(int sentBytes, int totalBytes);
        void complete(boolean success, String message);
    }

    private static final int CHUNKS_PER_TICK = 32;
    private static final long CLIENT_TIMEOUT_NANOS = 20_000_000_000L;
    private static Upload active;

    private SheetBatchImportClient() { }

    static UUID start(List<SheetBatchCodec.Entry> entries,
                      SheetBatchUploadStartPacket.Destination destination,
                      UUID songbookId, int songbookRevision, Listener listener) {
        if (active != null || listener == null
                || !ClientPlayNetworking.canSend(SheetBatchUploadStartPacket.TYPE)
                || !ClientPlayNetworking.canSend(SheetBatchUploadChunkPacket.TYPE)
                || !ClientPlayNetworking.canSend(SheetBatchUploadFinishPacket.TYPE)) return null;
        byte[] bytes = SheetBatchCodec.encode(entries);
        UUID transactionId = UUID.randomUUID();
        active = new Upload(transactionId, bytes, listener, System.nanoTime());
        String hash = SongbookHash.sha256(bytes);
        SheetBatchUploadStartPacket start = destination
                == SheetBatchUploadStartPacket.Destination.SONGBOOK
                ? SheetBatchUploadStartPacket.songbook(transactionId, songbookId,
                songbookRevision, entries.size(), bytes.length, hash)
                : SheetBatchUploadStartPacket.sheets(transactionId, entries.size(),
                bytes.length, hash);
        ClientPlayNetworking.send(start);
        listener.progress(0, bytes.length);
        return transactionId;
    }

    static void tick() {
        Upload upload = active;
        if (upload == null) return;
        if (System.nanoTime() - upload.startedNanos > CLIENT_TIMEOUT_NANOS) {
            active = null;
            if (ClientPlayNetworking.canSend(SheetBatchUploadCancelPacket.TYPE)) {
                ClientPlayNetworking.send(new SheetBatchUploadCancelPacket(upload.transactionId));
            }
            upload.listener.complete(false, "Separate import timed out.");
            return;
        }
        if (upload.waitingForResult) return;
        int sentChunks = 0;
        while (upload.offset < upload.bytes.length && sentChunks++ < CHUNKS_PER_TICK) {
            int length = Math.min(SheetBatchUploadChunkPacket.MAX_CHUNK_BYTES,
                    upload.bytes.length - upload.offset);
            ClientPlayNetworking.send(new SheetBatchUploadChunkPacket(upload.transactionId,
                    upload.offset, Arrays.copyOfRange(upload.bytes, upload.offset,
                    upload.offset + length)));
            upload.offset += length;
        }
        upload.listener.progress(upload.offset, upload.bytes.length);
        if (upload.offset == upload.bytes.length) {
            ClientPlayNetworking.send(new SheetBatchUploadFinishPacket(upload.transactionId));
            upload.waitingForResult = true;
        }
    }

    static void receive(SheetBatchResultPacket result) {
        Upload upload = active;
        if (upload == null || !upload.transactionId.equals(result.transactionId())) return;
        active = null;
        upload.listener.complete(result.success(), result.message());
        if (result.success() && ClientPlayNetworking.canSend(SheetBatchResultAckPacket.TYPE)) {
            ClientPlayNetworking.send(new SheetBatchResultAckPacket(result.transactionId()));
        }
    }

    static void cancel(UUID transactionId) {
        Upload upload = active;
        if (upload == null || !upload.transactionId.equals(transactionId)) return;
        active = null;
        if (ClientPlayNetworking.canSend(SheetBatchUploadCancelPacket.TYPE)) {
            ClientPlayNetworking.send(new SheetBatchUploadCancelPacket(transactionId));
        }
    }

    static void clear() { active = null; }

    private static final class Upload {
        private final UUID transactionId;
        private final byte[] bytes;
        private final Listener listener;
        private final long startedNanos;
        private int offset;
        private boolean waitingForResult;

        private Upload(UUID transactionId, byte[] bytes,
                       Listener listener, long startedNanos) {
            this.transactionId = transactionId;
            this.bytes = bytes;
            this.listener = listener;
            this.startedNanos = startedNanos;
        }
    }
}
