package m3e.client;

import net.minecraft.client.Minecraft;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;

import java.util.List;
import java.util.UUID;

/** Bridges Music Maker's native data response back into the still-open editor. */
public final class SheetEditorImportRefresh {
    private SheetEditorImportRefresh() { }

    /** Runs on the client thread after Music Maker has updated its local cache. */
    public static void acceptNativeData(UUID id, int version, List<NoteEvent> notes,
                                        List<VolumeMarker> markers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof SheetImportScreenAccess editor) {
            editor.m3e$acceptReplacementImport(id, version, notes, markers);
        }
    }
}
