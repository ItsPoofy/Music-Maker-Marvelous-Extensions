package m3e.client;

import org.jspecify.annotations.Nullable;
import m3e.sound.InstrumentSoundProfile;

/**
 * Screen-local source of truth for the regular held-instrument editor.
 * Server component synchronization may replace the visible ItemStack while
 * the screen is open; that replacement must not roll live controls backward.
 */
public final class HeldInstrumentEditSession {
    private @Nullable InstrumentSoundProfile profile;
    private @Nullable String variation;

    public void initialize(InstrumentSoundProfile initialProfile,
                           String initialVariation) {
        if (profile == null) {
            profile = initialProfile == null
                    ? InstrumentSoundProfile.DEFAULT : initialProfile;
        }
        if (variation == null) variation = initialVariation;
    }

    public InstrumentSoundProfile profile(InstrumentSoundProfile fallback) {
        return profile == null
                ? fallback == null ? InstrumentSoundProfile.DEFAULT : fallback
                : profile;
    }

    public void setProfile(InstrumentSoundProfile value) {
        profile = value == null ? InstrumentSoundProfile.DEFAULT : value;
    }

    public String variation(String fallback) {
        return variation == null ? fallback : variation;
    }

    public void setVariation(String value) { variation = value; }
}
