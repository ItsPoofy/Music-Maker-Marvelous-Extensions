package m3e.client;

import java.util.UUID;

/** Carries the Music Maker UUID through its cache callback without altering its API. */
public final class MusicSheetPlaybackContext {
    public record SheetKey(UUID id, int version) { }
    private static final ThreadLocal<SheetKey> CURRENT = new ThreadLocal<>();

    private MusicSheetPlaybackContext() { }

    public static Runnable wrap(UUID id, int version, Runnable task) {
        return () -> {
            SheetKey previous = CURRENT.get();
            CURRENT.set(new SheetKey(id, version));
            try {
                task.run();
            } finally {
                if (previous == null) CURRENT.remove();
                else CURRENT.set(previous);
            }
        };
    }

    public static SheetKey current() {
        return CURRENT.get();
    }
}
