package m3e;

import m3e.instrument.M3EInstruments;
import m3e.instrument.InstrumentItemComponents;
import m3e.dexed.DexedComponents;
import m3e.dexed.DexedPatchServer;
import m3e.network.M3ENetwork;
import m3e.sheet.MusicSheetComponents;
import m3e.sheet.MusicSheetServer;
import m3e.sheet.LegacySheetDropImports;
import m3e.songbook.SongbookComponents;
import m3e.songbook.SongbookItems;
import m3e.songbook.SongbookServer;
import m3e.sound.InstrumentProfileServer;
import m3e.sound.InstrumentExpressionServer;
import m3e.sound.LiveInstrumentNoteServer;
import m3e.sound.MusicBoxInstrumentProfileServer;
import m3e.sound.MusicBoxPlaybackServer;
import m3e.sound.MusicBoxLibraryServer;
import m3e.sound.NetworkClockServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class M3E implements ModInitializer {
    public static final String MOD_ID = "m3e";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("m3e", path);
    }

    @Override public void onInitialize() {
        SongbookComponents.register();
        DexedComponents.register();
        InstrumentItemComponents.register();
        SongbookItems.register();
        M3EInstruments.register();
        MusicSheetComponents.register();
        M3ENetwork.registerServer();
        ServerTickEvents.END_SERVER_TICK.register(server -> SongbookServer.tick());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    InstrumentProfileServer.sendSnapshot(handler.player);
                    InstrumentExpressionServer.sendSnapshot(handler.player);
                    DexedPatchServer.sendSnapshot(handler.player);
                }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                server.execute(() -> {
                    LegacySheetDropImports.clear(handler.player);
                    MusicSheetServer.remove(handler.player);
                    InstrumentProfileServer.remove(handler.player);
                    InstrumentExpressionServer.remove(handler.player);
                    LiveInstrumentNoteServer.remove(handler.player);
                    NetworkClockServer.remove(handler.player);
                    DexedPatchServer.remove(handler.player);
                    MusicBoxInstrumentProfileServer.remove(handler.player);
                    MusicBoxPlaybackServer.remove(handler.player);
                    MusicBoxLibraryServer.remove(handler.player);
                    SongbookServer.remove(handler.player);
                }));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            InstrumentProfileServer.clear();
            MusicSheetServer.clear();
            InstrumentExpressionServer.clear();
            LiveInstrumentNoteServer.clear();
            NetworkClockServer.clear();
            DexedPatchServer.clear();
            MusicBoxInstrumentProfileServer.clear();
            MusicBoxPlaybackServer.clear();
            MusicBoxLibraryServer.clear();
            SongbookServer.clear();
        });
    }
}
