package m3e.sheet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import m3e.M3E;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MusicSheetData extends SavedData {
    public record Entry(int version, MusicScore song, long lastAccessMillis) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("version").forGetter(Entry::version),
                MusicScore.CODEC.fieldOf("song").forGetter(Entry::song),
                Codec.LONG.optionalFieldOf("last_access", 0L).forGetter(Entry::lastAccessMillis)
        ).apply(instance, Entry::new));

        public Entry {
            if (version < 0 || song == null) throw new IllegalArgumentException("invalid legacy sheet entry");
        }
    }

    private static final Codec<Map<UUID, Entry>> MAP_CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC);
    private static final Codec<MusicSheetData> CODEC = MAP_CODEC.fieldOf("sheets")
            .xmap(MusicSheetData::new, data -> Map.copyOf(data.sheets)).codec();
    private static final SavedDataType<MusicSheetData> TYPE = new SavedDataType<>(
            M3E.id("music_sheets"), MusicSheetData::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Entry> sheets = new HashMap<>();

    private MusicSheetData() { }

    private MusicSheetData(Map<UUID, Entry> decoded) {
        sheets.putAll(decoded);
    }

    public static @Nullable Entry get(MinecraftServer server, UUID id, int requestedVersion) {
        Entry entry = server.overworld().getDataStorage().computeIfAbsent(TYPE).sheets.get(id);
        return entry != null && entry.version() == requestedVersion ? entry : null;
    }
}
