package m3e.instrument;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import m3e.M3E;
import m3e.sound.InstrumentSoundProfile;

/** Settings that travel with an exact Music Maker instrument ItemStack. */
public final class InstrumentItemComponents {
    public static final DataComponentType<InstrumentSoundProfile> SOUND_PROFILE =
            DataComponentType.<InstrumentSoundProfile>builder()
                    .persistent(InstrumentSoundProfile.CODEC)
                    .networkSynchronized(InstrumentSoundProfile.STREAM_CODEC)
                    .build();
    public static final DataComponentType<String> VARIATION =
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build();
    public static final DataComponentType<String> INSTANCE_ID =
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build();

    private InstrumentItemComponents() { }

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                M3E.id("instrument_sound_profile"), SOUND_PROFILE);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                M3E.id("instrument_variation"), VARIATION);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                M3E.id("instrument_instance_id"), INSTANCE_ID);
    }
}
