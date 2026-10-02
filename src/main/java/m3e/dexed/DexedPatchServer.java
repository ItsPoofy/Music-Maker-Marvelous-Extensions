package m3e.dexed;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import m3e.instrument.M3EInstruments;
import m3e.network.DexedPatchStatePacket;
import m3e.network.DexedPatchUpdatePacket;
import m3e.sound.PlayerRateLimiter;

public final class DexedPatchServer {
    private static final PlayerRateLimiter RATE = new PlayerRateLimiter();

    private DexedPatchServer() { }

    public static void update(ServerPlayer player, DexedPatchUpdatePacket packet) {
        if (!RATE.allow(player.getUUID(), 120)) return;
        InteractionHand hand = packet.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != M3EInstruments.dexed()) return;
        stack.set(DexedComponents.PATCH, packet.patch());
        stack.set(DexedComponents.PERFORMANCE, packet.performance());
        broadcast(player, packet.patch(), packet.performance());
    }

    public static void sendSnapshot(ServerPlayer joining) {
        for (ServerPlayer owner : PlayerLookup.all(joining.level().getServer())) {
            HeldState state = heldState(owner);
            if (state != null) ServerPlayNetworking.send(joining,
                    new DexedPatchStatePacket(owner.getId(), state.patch(), state.performance()));
        }
    }

    private static void broadcast(ServerPlayer owner, DexedPatch patch,
                                  DexedPerformance performance) {
        DexedPatchStatePacket state = new DexedPatchStatePacket(
                owner.getId(), patch, performance);
        for (ServerPlayer recipient : PlayerLookup.all(owner.level().getServer())) {
            ServerPlayNetworking.send(recipient, state);
        }
    }

    private static HeldState heldState(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() == M3EInstruments.dexed())
                return new HeldState(
                        stack.getOrDefault(DexedComponents.PATCH, DexedPatch.initVoice()),
                        stack.getOrDefault(DexedComponents.PERFORMANCE, DexedPerformance.DEFAULT));
        }
        return null;
    }

    private record HeldState(DexedPatch patch, DexedPerformance performance) { }

    public static void remove(ServerPlayer player) { RATE.remove(player.getUUID()); }
    public static void clear() { RATE.clear(); }
}
