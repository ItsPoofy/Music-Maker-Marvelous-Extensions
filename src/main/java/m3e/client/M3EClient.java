package m3e.client;

import m3e.network.MusicSheetDataPacket;
import m3e.network.InstrumentProfileStatePacket;
import m3e.network.InstrumentExpressionStatePacket;
import m3e.network.MusicBoxLabelPacket;
import m3e.network.MusicBoxInstrumentProfileStatePacket;
import m3e.network.MusicBoxPlaybackStatePacket;
import m3e.network.MusicBoxLibraryStatePacket;
import m3e.network.SongbookCatalogPacket;
import m3e.network.SongbookResultPacket;
import m3e.network.SongbookSongChunkPacket;
import m3e.network.SongbookSongFinishPacket;
import m3e.network.SongbookSongStartPacket;
import m3e.network.SongbookOpenPacket;
import m3e.network.DexedPatchStatePacket;
import m3e.network.DexedVoiceAutomationPacket;
import m3e.network.LiveInstrumentNoteStatePacket;
import m3e.network.NetworkClockResponsePacket;
import m3e.network.SheetBatchResultPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class M3EClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        verifyCriticalMixinTargets();
        InstrumentOptions.load();
        M3EEditBox.registerFocusDismissal();
        InstrumentExtensionPanel.register();
        MusicBoxContentsLabelRenderer.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SheetEditController.tick();
            InstrumentPerformanceControls.tick();
            InstrumentOptions.tick();
            InstrumentProfileClientState.tick();
            SongbookClientState.tick();
            InstrumentExtensionPanel.tick(client);
            MusicBoxInstrumentProfiles.tick(client);
            MusicBoxPlaybackControls.tick(client);
            MusicBoxLibraryControls.tick(client);
            SheetExportController.tick(client);
            SheetTempoUpdateController.tick(client);
            DexedAudioManager.tick(client);
            NetworkClockClient.tick();
            LiveInstrumentNoteRelay.tick();
            SheetBatchImportClient.tick();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(M3EClient::clearConnectionState));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(M3EClient::clearConnectionState));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            clearConnectionState();
            InstrumentOptions.shutdown();
        });
        ClientPlayNetworking.registerGlobalReceiver(m3e.network.MusicSheetSaveResultPacket.TYPE, (result, context) ->
                context.client().execute(() -> SheetEditController.receive(result)));
        ClientPlayNetworking.registerGlobalReceiver(MusicSheetDataPacket.TYPE, (data, context) ->
                context.client().execute(() -> MusicSheetClientCache.receive(data)));
        ClientPlayNetworking.registerGlobalReceiver(InstrumentProfileStatePacket.TYPE, (profile, context) ->
                context.client().execute(() -> InstrumentProfileClientState.receive(profile)));
        ClientPlayNetworking.registerGlobalReceiver(DexedPatchStatePacket.TYPE, (state, context) ->
                context.client().execute(() -> DexedAudioManager.receivePatch(
                        state.entityId(), state.patch(), state.performance())));
        ClientPlayNetworking.registerGlobalReceiver(InstrumentExpressionStatePacket.TYPE, (expression, context) ->
                context.client().execute(() -> InstrumentExpressionController.receive(expression)));
        ClientPlayNetworking.registerGlobalReceiver(NetworkClockResponsePacket.TYPE,
                (response, context) -> context.client().execute(() ->
                        NetworkClockClient.receive(response)));
        ClientPlayNetworking.registerGlobalReceiver(LiveInstrumentNoteStatePacket.TYPE,
                (note, context) -> context.client().execute(() ->
                        LiveInstrumentNoteRelay.receive(note)));
        ClientPlayNetworking.registerGlobalReceiver(DexedVoiceAutomationPacket.TYPE,
                (automation, context) -> context.client().execute(() -> {
                    if (automation.kind() == DexedVoiceAutomationPacket.EXPRESSION) {
                        DexedAudioManager.songbookVoiceExpression(automation.playerId(),
                                automation.eventId(), automation.bendSemitones(),
                                automation.modulation());
                    } else if (automation.kind() == DexedVoiceAutomationPacket.GAIN) {
                        DexedAudioManager.songbookVoiceGain(automation.playerId(),
                                automation.eventId(), automation.gain());
                    } else {
                        DexedAudioManager.songbookVoiceGlissando(automation.playerId(),
                                automation.eventId(), automation.waypoints(),
                                automation.positions(), automation.durationMicros());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(MusicBoxLabelPacket.TYPE, (label, context) ->
                context.client().execute(() -> MusicBoxContentsLabelCache.receive(label)));
        ClientPlayNetworking.registerGlobalReceiver(MusicBoxInstrumentProfileStatePacket.TYPE, (profile, context) ->
                context.client().execute(() -> MusicBoxInstrumentProfiles.receive(profile)));
        ClientPlayNetworking.registerGlobalReceiver(MusicBoxPlaybackStatePacket.TYPE, (state, context) ->
                context.client().execute(() -> MusicBoxPlaybackControls.receive(state)));
        ClientPlayNetworking.registerGlobalReceiver(MusicBoxLibraryStatePacket.TYPE, (state, context) ->
                context.client().execute(() -> MusicBoxLibraryControls.receive(state)));
        ClientPlayNetworking.registerGlobalReceiver(SongbookCatalogPacket.TYPE, (catalog, context) ->
                context.client().execute(() -> SongbookClientState.receiveCatalog(catalog)));
        ClientPlayNetworking.registerGlobalReceiver(SongbookSongStartPacket.TYPE, (start, context) ->
                context.client().execute(() -> SongbookClientState.beginSong(start)));
        ClientPlayNetworking.registerGlobalReceiver(SongbookSongChunkPacket.TYPE, (chunk, context) ->
                context.client().execute(() -> SongbookClientState.receiveSongChunk(chunk)));
        ClientPlayNetworking.registerGlobalReceiver(SongbookSongFinishPacket.TYPE, (finish, context) ->
                context.client().execute(() -> SongbookClientState.finishSong(finish)));
        ClientPlayNetworking.registerGlobalReceiver(SongbookResultPacket.TYPE, (result, context) ->
                context.client().execute(() -> SongbookClientState.receiveResult(result)));
        ClientPlayNetworking.registerGlobalReceiver(SongbookOpenPacket.TYPE, (open, context) ->
                context.client().execute(() -> SongbookManagementScreen.open(open.bookId())));
        ClientPlayNetworking.registerGlobalReceiver(SheetBatchResultPacket.TYPE, (result, context) ->
                context.client().execute(() -> SheetBatchImportClient.receive(result)));
    }

    /**
     * Music Maker loads several audio classes only when the first instrument
     * opens. Resolve them during client startup so an incompatible dependency
     * or mixin fails in the smoke test rather than in the middle of a world.
     */
    private static void clearConnectionState() {
        MusicSheetClientCache.clear();
        SheetEditController.clear();
        InstrumentProfileClientState.clearRemote();
        InstrumentExpressionController.clearRemote();
        SongbookClientState.clear();
        MusicBoxContentsLabelCache.clear();
        MusicBoxInstrumentProfiles.clear();
        MusicBoxPlaybackControls.clear();
        MusicBoxLibraryControls.clear();
        SheetExportController.clear();
        SheetTempoUpdateController.clear();
        DexedPatchClient.clear();
        DexedAudioManager.clear();
        DexedSongbookNoteRelay.clear();
        LiveInstrumentNoteRelay.clear();
        NetworkClockClient.clear();
        SheetBatchImportClient.clear();
    }

    private static void verifyCriticalMixinTargets() {
        String[] targets = {
                "xerca.xercamusic.client.NoteSound",
                "xerca.xercamusic.client.GuiInstrument",
                "xerca.xercamusic.client.ModClient",
                "xerca.xercamusic.client.SoundController",
                "xerca.xercamusic.common.packets.clientbound.SingleNoteClientPacketHandler"
        };
        try {
            ClassLoader loader = M3EClient.class.getClassLoader();
            for (String target : targets) Class.forName(target, true, loader);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Music Maker’s Marvelous Extensions dependency is incomplete", exception);
        }
    }
}
