package m3e.dexed;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import m3e.M3E;

public final class DexedComponents {
    public static final DataComponentType<DexedPatch> PATCH = DataComponentType.<DexedPatch>builder()
            .persistent(DexedPatch.CODEC).networkSynchronized(DexedPatch.STREAM_CODEC).build();
    public static final DataComponentType<DexedPerformance> PERFORMANCE =
            DataComponentType.<DexedPerformance>builder().persistent(DexedPerformance.CODEC)
                    .networkSynchronized(DexedPerformance.STREAM_CODEC).build();

    private DexedComponents() { }

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, M3E.id("dexed_patch"), PATCH);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                M3E.id("dexed_performance"), PERFORMANCE);
    }
}
