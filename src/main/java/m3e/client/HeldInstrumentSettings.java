package m3e.client;

import net.minecraft.world.item.ItemStack;
import m3e.instrument.InstrumentItemComponents;
import m3e.sound.InstrumentSoundProfile;

import java.util.UUID;

public final class HeldInstrumentSettings {
    private HeldInstrumentSettings() { }

    static InstrumentSoundProfile profile(int instrumentId) {
        return InstrumentOptions.playerDefaultProfile(instrumentId);
    }

    static void setProfile(int instrumentId, InstrumentSoundProfile profile) {
        InstrumentOptions.setPlayerDefaultProfile(instrumentId, profile);
        InstrumentProfileClientState.activate(instrumentId);
    }

    static String variation(int instrumentId) {
        return InstrumentOptions.playerDefaultVariation(instrumentId);
    }

    static void setVariation(int instrumentId, String variation) {
        InstrumentOptions.setPlayerDefaultVariation(instrumentId, variation);
        InstrumentProfileClientState.activate(instrumentId);
    }

    public static void activate(int instrumentId) {
        InstrumentProfileClientState.activate(instrumentId);
    }

    static long identity(int instrumentId, ItemStack stack) {
        String raw = stack.getOrDefault(InstrumentItemComponents.INSTANCE_ID, "");
        try {
            UUID id = UUID.fromString(raw);
            return id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        } catch (IllegalArgumentException ignored) {
            return ((long) System.identityHashCode(stack) << 32) ^ instrumentId;
        }
    }

}
