package m3e.client;

import m3e.sound.InstrumentSoundProfile;

import java.util.function.Supplier;

/**
 * Scoped metadata for direct imported-MIDI playback. It lets the existing
 * GuiInstrument note path keep networking and particles while separating
 * Songbook voices from live held-note ownership and applying MIDI tuning.
 */
public final class InstrumentPlaybackContext {
    private static final ThreadLocal<Float> MIDI_TUNING = new ThreadLocal<>();
    private static final ThreadLocal<Integer> MIDI_CHANNEL = new ThreadLocal<>();
    private static final ThreadLocal<Long> MIDI_EVENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> LOCAL_PREVIEW = new ThreadLocal<>();

    private InstrumentPlaybackContext() { }

    static void withTuning(float tuningHz, Runnable action) {
        withTuning(tuningHz, () -> {
            action.run();
            return null;
        });
    }

    static <T> T withTuning(float tuningHz, Supplier<T> action) {
        return withPlayback(tuningHz, null, action);
    }

    static <T> T withPlayback(float tuningHz, int channel, Supplier<T> action) {
        return withPlayback(tuningHz, channel, 0L, action);
    }

    static <T> T withPlayback(float tuningHz, int channel, long eventId, Supplier<T> action) {
        return withPlayback(tuningHz, Integer.valueOf(channel), eventId, false, action);
    }

    static <T> T withLocalPreview(float tuningHz, int channel, long eventId,
                                  Supplier<T> action) {
        return withPlayback(tuningHz, Integer.valueOf(channel), eventId, true, action);
    }

    private static <T> T withPlayback(float tuningHz, Integer channel, Supplier<T> action) {
        return withPlayback(tuningHz, channel, 0L, false, action);
    }

    private static <T> T withPlayback(float tuningHz, Integer channel, long eventId,
                                      boolean localPreview, Supplier<T> action) {
        Float previous = MIDI_TUNING.get();
        Integer previousChannel = MIDI_CHANNEL.get();
        Long previousEvent = MIDI_EVENT.get();
        Boolean previousPreview = LOCAL_PREVIEW.get();
        MIDI_TUNING.set(tuningHz);
        if (channel == null) MIDI_CHANNEL.remove();
        else MIDI_CHANNEL.set(channel);
        if (eventId == 0L) MIDI_EVENT.remove();
        else MIDI_EVENT.set(eventId);
        if (localPreview) LOCAL_PREVIEW.set(true);
        else LOCAL_PREVIEW.remove();
        try {
            return action.get();
        } finally {
            if (previous == null) MIDI_TUNING.remove();
            else MIDI_TUNING.set(previous);
            if (previousChannel == null) MIDI_CHANNEL.remove();
            else MIDI_CHANNEL.set(previousChannel);
            if (previousEvent == null) MIDI_EVENT.remove();
            else MIDI_EVENT.set(previousEvent);
            if (previousPreview == null) LOCAL_PREVIEW.remove();
            else LOCAL_PREVIEW.set(previousPreview);
        }
    }

    public static InstrumentSoundProfile apply(InstrumentSoundProfile profile) {
        Float tuning = MIDI_TUNING.get();
        return tuning == null ? profile : profile.withTuning(tuning);
    }

    public static boolean isSongbookPlayback() { return MIDI_TUNING.get() != null; }

    public static boolean localPreview() { return Boolean.TRUE.equals(LOCAL_PREVIEW.get()); }

    /** Unique active-note id supplied by songbook playback, or zero for ordinary live input. */
    public static long songbookEventId() {
        Long event = MIDI_EVENT.get();
        return event == null ? 0L : event;
    }

    /** Melodic Songbook notes share the normal chord path; GM percussion does not. */
    public static boolean useForChordInput() {
        Integer channel = MIDI_CHANNEL.get();
        return !localPreview() && (channel == null || channel != 9);
    }
}
