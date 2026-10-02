package m3e.api.client;

import m3e.debug.MusicDebugTelemetry;
import xerca.xercamusic.common.item.IItemInstrument;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

/**
 * Optional integration points for mods which build on M3E. M3E remains fully
 * usable with only Music Maker installed.
 */
public final class M3EClientIntegration {
    private static BooleanSupplier ensembleActive = () -> false;
    private static TransportObserver transportObserver =
            (bpm, numerator, denominator, playheadMicros, playing, clientSampleNanos) -> { };
    private static BooleanSupplier instrumentUiEnabled = () -> true;
    private static IntPredicate chordDisplayNote = ignored -> true;
    private static BooleanSupplier splitControl = () -> false;
    private static ControlNoteSender controlNotes = (note, velocity, stop) -> { };
    private static LiveNoteSender ensembleNotes = (note, velocity, stop, instrument) -> { };
    private static final List<InstrumentMenuExtension> MENU_EXTENSIONS = new ArrayList<>();

    private M3EClientIntegration() { }

    public static synchronized void registerMusicContext(
            BooleanSupplier active,
            TransportObserver transport,
            BooleanSupplier uiEnabled,
            IntPredicate displayNote,
            BooleanSupplier splitControlEnabled,
            ControlNoteSender controlNoteSender,
            LiveNoteSender ensembleNoteSender) {
        ensembleActive = active == null ? () -> false : active;
        transportObserver = transport == null
                ? (bpm, numerator, denominator, playheadMicros, playing, clientSampleNanos) -> { }
                : transport;
        instrumentUiEnabled = uiEnabled == null ? () -> true : uiEnabled;
        chordDisplayNote = displayNote == null ? ignored -> true : displayNote;
        splitControl = splitControlEnabled == null ? () -> false : splitControlEnabled;
        controlNotes = controlNoteSender == null
                ? (note, velocity, stop) -> { } : controlNoteSender;
        ensembleNotes = ensembleNoteSender == null
                ? (note, velocity, stop, instrument) -> { } : ensembleNoteSender;
    }

    public static synchronized boolean ensembleActive() {
        return ensembleActive.getAsBoolean();
    }

    public static synchronized void observeTransport(int bpm, int numerator, int denominator,
                                                     long playheadMicros,
                                                     boolean playing, long clientSampleNanos) {
        MusicDebugTelemetry.sampled("midi_sync_send", 250_000_000L,
                "midi_sync", "sent", "bpm", bpm,
                "meter", numerator + "/" + denominator,
                "playhead_us", playheadMicros, "playing", playing,
                "sample_ns", clientSampleNanos);
        transportObserver.observe(bpm, numerator, denominator, playheadMicros, playing,
                clientSampleNanos);
    }

    public static synchronized boolean instrumentUiEnabled() {
        return instrumentUiEnabled.getAsBoolean();
    }

    public static synchronized boolean useForChordDisplay(int note) {
        return chordDisplayNote.test(note);
    }

    public static synchronized boolean splitControlEnabled() {
        return splitControl.getAsBoolean();
    }

    public static synchronized void sendControlNote(int note, int velocity, boolean stop) {
        controlNotes.send(note, velocity, stop);
    }

    public static synchronized void sendEnsembleNote(int note, int velocity, boolean stop,
                                                     IItemInstrument instrument) {
        ensembleNotes.send(note, velocity, stop, instrument);
    }

    public static synchronized void registerMenuExtension(InstrumentMenuExtension extension) {
        if (extension == null) return;
        MENU_EXTENSIONS.removeIf(existing -> existing.id().equals(extension.id()));
        MENU_EXTENSIONS.add(extension);
    }

    public static synchronized List<InstrumentMenuExtension> menuExtensions() {
        return List.copyOf(MENU_EXTENSIONS);
    }

    public static synchronized void unregisterMenuExtension(String id) {
        if (id != null) MENU_EXTENSIONS.removeIf(extension -> extension.id().equals(id));
    }

    @FunctionalInterface
    public interface ControlNoteSender {
        void send(int note, int velocity, boolean stop);
    }

    @FunctionalInterface
    public interface LiveNoteSender {
        void send(int note, int velocity, boolean stop, IItemInstrument instrument);
    }

    @FunctionalInterface
    public interface TransportObserver {
        void observe(int bpm, int numerator, int denominator, long playheadMicros,
                     boolean playing, long clientSampleNanos);
    }
}
