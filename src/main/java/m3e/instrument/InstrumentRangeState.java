package m3e.instrument;

import xerca.xercamusic.common.item.IItemInstrument;

/**
 * Side-neutral bridge for the local full-range preference.
 *
 * <p>The exact-sample-range mixin runs on both sides, while the option itself
 * belongs to the client UI. Keeping this tiny state holder common lets the
 * client opt out of its local endpoint clamp without making a dedicated
 * server load client configuration classes.</p>
 */
public final class InstrumentRangeState {
    private static volatile boolean fullKeyboardRange;
    private static final ThreadLocal<Integer> REMOTE_PLAYBACK_DEPTH = ThreadLocal.withInitial(() -> 0);

    private InstrumentRangeState() { }

    public static boolean extendsRange(IItemInstrument instrument) {
        return (fullKeyboardRange || REMOTE_PLAYBACK_DEPTH.get() > 0)
                && InstrumentRangeSupport.supportsExtension(instrument);
    }

    public static void setFullKeyboardRange(boolean enabled) {
        fullKeyboardRange = enabled;
    }

    /** Lets receivers reproduce a remote player's extended notes without sharing local UI preferences. */
    public static void beginRemotePlayback() {
        REMOTE_PLAYBACK_DEPTH.set(REMOTE_PLAYBACK_DEPTH.get() + 1);
    }

    public static void endRemotePlayback() {
        int remaining = REMOTE_PLAYBACK_DEPTH.get() - 1;
        if (remaining <= 0) REMOTE_PLAYBACK_DEPTH.remove();
        else REMOTE_PLAYBACK_DEPTH.set(remaining);
    }
}
