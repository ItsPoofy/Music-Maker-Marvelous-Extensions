package m3e.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import m3e.dexed.DexedComponents;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;
import m3e.instrument.M3EInstruments;
import m3e.network.DexedPatchUpdatePacket;

public final class DexedPatchClient {
    private static DexedPatch lastPublished;

    private DexedPatchClient() { }

    public static Held held() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (stack.getItem() == M3EInstruments.dexed()) return new Held(hand,
                    HeldInstrumentSettings.identity(
                            M3EInstruments.dexed().getInstrumentId(), stack),
                    stack.getOrDefault(DexedComponents.PATCH, DexedPatch.initVoice()),
                    stack.getOrDefault(DexedComponents.PERFORMANCE, DexedPerformance.DEFAULT));
        }
        return null;
    }

    static DexedEditorTarget editorTarget() {
        Held held = held();
        return held == null ? null : new HeldEditorTarget(held.hand(), held.stackIdentity());
    }

    public static void apply(Held held, DexedPatch patch, DexedPerformance performance,
                             boolean force) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        ItemStack stack = minecraft.player.getItemInHand(held.hand());
        if (stack.getItem() != M3EInstruments.dexed()) return;
        stack.set(DexedComponents.PATCH, patch);
        stack.set(DexedComponents.PERFORMANCE, performance);
        if (force || !patch.equals(lastPublished)
                || !performance.equals(lastPublishedPerformance)) {
            lastPublished = patch;
            lastPublishedPerformance = performance;
            ClientPlayNetworking.send(new DexedPatchUpdatePacket(
                    held.hand() == InteractionHand.OFF_HAND, patch, performance));
        }
        DexedAudioManager.receivePatch(minecraft.player.getId(), patch, performance);
    }

    private static DexedPerformance lastPublishedPerformance;

    static void clear() {
        lastPublished = null;
        lastPublishedPerformance = null;
    }

    public record Held(InteractionHand hand, long stackIdentity, DexedPatch patch,
                       DexedPerformance performance) { }

    private record HeldEditorTarget(InteractionHand hand, long identity)
            implements DexedEditorTarget {
        private Held current() {
            Held held = DexedPatchClient.held();
            return held != null && held.hand() == hand && held.stackIdentity() == identity
                    ? held : null;
        }

        @Override public DexedPatch patch() {
            Held held = current();
            return held == null ? DexedPatch.initVoice() : held.patch();
        }

        @Override public DexedPerformance performance() {
            Held held = current();
            return held == null ? DexedPerformance.DEFAULT : held.performance();
        }

        @Override public m3e.sound.InstrumentSoundProfile profile() {
            return HeldInstrumentSettings.profile(M3EInstruments.dexed().getInstrumentId());
        }

        @Override public void apply(DexedPatch patch, DexedPerformance performance,
                                    boolean force) {
            Held held = current();
            if (held != null) DexedPatchClient.apply(held, patch, performance, force);
        }

        @Override public void setProfile(
                m3e.sound.InstrumentSoundProfile profile) {
            int instrumentId = M3EInstruments.dexed().getInstrumentId();
            HeldInstrumentSettings.setProfile(instrumentId, profile);
        }
    }
}
