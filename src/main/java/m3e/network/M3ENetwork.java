package m3e.network;

import m3e.sheet.MusicSheetServer;
import m3e.sheet.LegacySheetDropImports;
import m3e.sound.InstrumentProfileServer;
import m3e.sound.InstrumentExpressionServer;
import m3e.sound.LiveInstrumentNoteServer;
import m3e.sound.MusicBoxInstrumentProfileServer;
import m3e.sound.MusicBoxPlaybackServer;
import m3e.sound.MusicBoxLibraryServer;
import m3e.sound.NetworkClockServer;
import m3e.songbook.SongbookServer;
import m3e.dexed.DexedPatchServer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class M3ENetwork {
    private M3ENetwork() { }

    public static void registerServer() {
        PayloadTypeRegistry.clientboundPlay().register(MusicSheetSaveResultPacket.TYPE, MusicSheetSaveResultPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(InstrumentProfileStatePacket.TYPE, InstrumentProfileStatePacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DexedPatchStatePacket.TYPE, DexedPatchStatePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DexedPatchUpdatePacket.TYPE, DexedPatchUpdatePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(InstrumentProfileUpdatePacket.TYPE, InstrumentProfileUpdatePacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MusicBoxInstrumentProfileStatePacket.TYPE,
                MusicBoxInstrumentProfileStatePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MusicBoxInstrumentProfileUpdatePacket.TYPE,
                MusicBoxInstrumentProfileUpdatePacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MusicBoxPlaybackStatePacket.TYPE,
                MusicBoxPlaybackStatePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MusicBoxPlaybackControlPacket.TYPE,
                MusicBoxPlaybackControlPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MusicBoxLibraryStatePacket.TYPE,
                MusicBoxLibraryStatePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MusicBoxLibraryActionPacket.TYPE,
                MusicBoxLibraryActionPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(InstrumentExpressionUpdatePacket.TYPE,
                InstrumentExpressionUpdatePacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(InstrumentExpressionStatePacket.TYPE,
                InstrumentExpressionStatePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(NetworkClockRequestPacket.TYPE,
                NetworkClockRequestPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NetworkClockResponsePacket.TYPE,
                NetworkClockResponsePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LiveInstrumentNotePacket.TYPE,
                LiveInstrumentNotePacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LiveInstrumentNoteStatePacket.TYPE,
                LiveInstrumentNoteStatePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DexedVoiceAutomationPacket.TYPE,
                DexedVoiceAutomationPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DexedVoiceAutomationPacket.TYPE,
                DexedVoiceAutomationPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MusicBoxLabelPacket.TYPE, MusicBoxLabelPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MusicSheetDataPacket.TYPE, MusicSheetDataPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MusicSheetImportPacket.TYPE, MusicSheetImportPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MusicSheetEditPacket.TYPE, MusicSheetEditPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MusicSheetRequestPacket.TYPE, MusicSheetRequestPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MusicSheetTempoUpdatePacket.TYPE,
                MusicSheetTempoUpdatePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SheetBatchUploadStartPacket.TYPE,
                SheetBatchUploadStartPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SheetBatchUploadChunkPacket.TYPE,
                SheetBatchUploadChunkPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SheetBatchUploadFinishPacket.TYPE,
                SheetBatchUploadFinishPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SheetBatchUploadCancelPacket.TYPE,
                SheetBatchUploadCancelPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SheetBatchResultPacket.TYPE,
                SheetBatchResultPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SheetBatchResultAckPacket.TYPE,
                SheetBatchResultAckPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LegacySheetDropIntentPacket.TYPE, LegacySheetDropIntentPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SongbookCatalogRequestPacket.TYPE, SongbookCatalogRequestPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SongbookCatalogPacket.TYPE, SongbookCatalogPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SongbookSongRequestPacket.TYPE, SongbookSongRequestPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SongbookSongStartPacket.TYPE, SongbookSongStartPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SongbookSongChunkPacket.TYPE, SongbookSongChunkPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SongbookSongFinishPacket.TYPE, SongbookSongFinishPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SongbookUploadStartPacket.TYPE, SongbookUploadStartPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SongbookUploadChunkPacket.TYPE, SongbookUploadChunkPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SongbookUploadFinishPacket.TYPE, SongbookUploadFinishPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SongbookActionPacket.TYPE, SongbookActionPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SongbookResultPacket.TYPE, SongbookResultPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SongbookOpenPacket.TYPE, SongbookOpenPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MusicSheetImportPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> {
                if (MusicSheetServer.allowImport(context.player())) MusicSheetServer.importSong(context.player(), packet);
                else MusicSheetServer.rejectSave(context.player(), packet.requestId(), null, "Please wait before saving another sheet.");
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(MusicSheetEditPacket.TYPE, (packet, context) ->
                context.server().execute(() -> MusicSheetServer.editSong(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(MusicSheetRequestPacket.TYPE, (packet, context) -> {
            if (MusicSheetServer.allowRequest(context.player())) {
                context.server().execute(() -> MusicSheetServer.request(
                        context.player(), packet.sheetId(), packet.version()));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(MusicSheetTempoUpdatePacket.TYPE, (packet, context) ->
                context.server().execute(() -> MusicSheetServer.updateTempo(
                        context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(SheetBatchUploadStartPacket.TYPE, (packet, context) ->
                context.server().execute(() -> MusicSheetServer.beginBatch(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(SheetBatchUploadChunkPacket.TYPE, (packet, context) ->
                context.server().execute(() -> MusicSheetServer.batchChunk(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(SheetBatchUploadFinishPacket.TYPE, (packet, context) ->
                context.server().execute(() -> MusicSheetServer.finishBatch(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(SheetBatchUploadCancelPacket.TYPE, (packet, context) ->
                context.server().execute(() -> MusicSheetServer.cancelBatch(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(SheetBatchResultAckPacket.TYPE, (packet, context) ->
                context.server().execute(() -> MusicSheetServer.acknowledgeBatchResult(
                        context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(LegacySheetDropIntentPacket.TYPE, (packet, context) ->
                context.server().execute(() -> LegacySheetDropImports.mark(
                        context.player(), packet.sheetId(), packet.exactSong())));
        ServerPlayNetworking.registerGlobalReceiver(SongbookCatalogRequestPacket.TYPE, (packet, context) ->
                context.server().execute(() -> SongbookServer.sendCatalog(context.player(), packet.bookId())));
        ServerPlayNetworking.registerGlobalReceiver(SongbookSongRequestPacket.TYPE, (packet, context) ->
                context.server().execute(() -> SongbookServer.sendSong(context.player(), packet.bookId(),
                        packet.revision(), packet.entryId())));
        ServerPlayNetworking.registerGlobalReceiver(SongbookUploadStartPacket.TYPE, (packet, context) ->
                context.server().execute(() -> SongbookServer.beginUpload(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(SongbookUploadChunkPacket.TYPE, (packet, context) ->
                context.server().execute(() -> SongbookServer.uploadChunk(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(SongbookUploadFinishPacket.TYPE, (packet, context) ->
                context.server().execute(() -> SongbookServer.finishUpload(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(SongbookActionPacket.TYPE, (packet, context) ->
                context.server().execute(() -> SongbookServer.action(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(InstrumentProfileUpdatePacket.TYPE, (packet, context) -> {
            if (InstrumentProfileServer.allow(context.player())) {
                context.server().execute(() -> InstrumentProfileServer.update(
                        context.server(), context.player(), packet));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(DexedPatchUpdatePacket.TYPE, (packet, context) ->
                context.server().execute(() -> DexedPatchServer.update(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(MusicBoxInstrumentProfileUpdatePacket.TYPE, (packet, context) -> {
            if (MusicBoxInstrumentProfileServer.allow(context.player())) {
                context.server().execute(() -> MusicBoxInstrumentProfileServer.update(
                        context.player(), packet));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(MusicBoxPlaybackControlPacket.TYPE, (packet, context) -> {
            if (MusicBoxPlaybackServer.allow(context.player())) {
                context.server().execute(() -> MusicBoxPlaybackServer.control(
                        context.player(), packet));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(MusicBoxLibraryActionPacket.TYPE,
                (packet, context) -> context.server().execute(() ->
                        MusicBoxLibraryServer.action(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(InstrumentExpressionUpdatePacket.TYPE, (packet, context) ->
                context.server().execute(() -> InstrumentExpressionServer.update(
                        context.server(), context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(NetworkClockRequestPacket.TYPE, (packet, context) -> {
            if (NetworkClockServer.allow(context.player())) {
                context.server().execute(() -> NetworkClockServer.receive(
                        context.player(), packet));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(LiveInstrumentNotePacket.TYPE, (packet, context) -> {
            if (LiveInstrumentNoteServer.allow(context.player(), packet.stop())) {
                context.server().execute(() -> LiveInstrumentNoteServer.receive(
                        context.player(), packet));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(DexedVoiceAutomationPacket.TYPE,
                (packet, context) -> {
                    if (LiveInstrumentNoteServer.allowAutomation(context.player())) {
                        context.server().execute(() -> LiveInstrumentNoteServer.receiveAutomation(
                                context.player(), packet));
                    }
                });
    }
}
