package m3e.sheet;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Registry;
import m3e.network.MusicScoreNetwork;

public final class MusicSheetComponents {
    public static final DataComponentType<MusicScore> SCORE =
            DataComponentType.<MusicScore>builder().persistent(MusicScore.CODEC)
                    .networkSynchronized(MusicScoreNetwork.STREAM_CODEC).build();

    public static final DataComponentType<Integer> SOURCE_BPM = DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(1, 60_000))
            .networkSynchronized(ByteBufCodecs.INT)
            .build();

    private MusicSheetComponents() { }

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath("m3e", "music_score"), SCORE);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath("m3e", "source_midi_bpm"), SOURCE_BPM);
    }
}
