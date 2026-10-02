package m3e.musicbox;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable sheet identity used when a Songbook entry is materialized in a Music Box. */
public final class MusicBoxLibraryIdentity {
    private MusicBoxLibraryIdentity() { }

    public static UUID sheetId(UUID bookId, UUID entryId) {
        return UUID.nameUUIDFromBytes(("music-box:" + bookId + ":" + entryId)
                .getBytes(StandardCharsets.UTF_8));
    }
}
