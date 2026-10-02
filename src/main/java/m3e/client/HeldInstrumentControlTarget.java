package m3e.client;

import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import m3e.instrument.M3EInstruments;
import m3e.instrument.InstrumentVariations;
import m3e.sound.InstrumentSoundProfile;

/** Exact held-stack target used by the regular Instrument tab. */
final class HeldInstrumentControlTarget implements InstrumentControlTarget {
    private final IntSupplier instrumentId;
    private final Supplier<@Nullable HeldInstrumentEditSession> editSession;

    HeldInstrumentControlTarget(IntSupplier instrumentId) {
        this(instrumentId, () -> null);
    }

    HeldInstrumentControlTarget(IntSupplier instrumentId,
                                Supplier<@Nullable HeldInstrumentEditSession> editSession) {
        this.instrumentId = instrumentId;
        this.editSession = editSession;
    }

    @Override public int instrumentId() { return instrumentId.getAsInt(); }

    @Override public InstrumentSoundProfile profile() {
        InstrumentSoundProfile held = HeldInstrumentSettings.profile(instrumentId());
        HeldInstrumentEditSession session = editSession.get();
        return session == null ? held : session.profile(held);
    }

    @Override public void setProfile(InstrumentSoundProfile profile) {
        HeldInstrumentEditSession session = editSession.get();
        if (session != null) session.setProfile(profile);
        HeldInstrumentSettings.setProfile(instrumentId(), profile);
    }

    @Override public String variation() {
        String held = HeldInstrumentSettings.variation(instrumentId());
        HeldInstrumentEditSession session = editSession.get();
        return session == null ? held : session.variation(held);
    }

    @Override public void setVariation(String variation) {
        String normalized = InstrumentVariations.normalize(instrumentId(), variation);
        HeldInstrumentEditSession session = editSession.get();
        if (session != null) session.setVariation(normalized);
        HeldInstrumentSettings.setVariation(instrumentId(), normalized);
    }

    @Override public @Nullable DexedEditorTarget dexed() {
        return instrumentId() == M3EInstruments.dexed().getInstrumentId()
                ? DexedPatchClient.editorTarget() : null;
    }
}
