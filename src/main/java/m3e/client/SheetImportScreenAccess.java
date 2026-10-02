package m3e.client;

import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;

import java.util.List;
import java.util.UUID;

/**
 * Implemented by the Music Maker sheet mixin.
 *
 * <p>The native editor snapshots a sheet's UUID and note list in its
 * constructor.  A file-drop import deliberately replaces the held blank
 * sheet in-place, so the already-open editor must also adopt the replacement
 * data instead of waiting for a close/reopen cycle.</p>
 */
public interface SheetImportScreenAccess {
    void m3e$prepareForReplacementImport();

    /** Arms this particular editor for the next import response. */
    void m3e$beginReplacementImport();

    /**
     * Atomically adopts the native Music Maker response.  Returns false when
     * this editor was not waiting for an import, preventing unrelated sheet
     * data packets from altering an open editor.
     */
    boolean m3e$acceptReplacementImport(UUID id, int version,
                                                    List<NoteEvent> notes,
                                                    List<VolumeMarker> markers);
}
