package m3e.songbook;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import m3e.M3E;

/** Songbook bulk storage, lightweight inventory header, and physical extracted-page marker. */
public final class SongbookComponents {
    public static final DataComponentType<SongbookContents> CONTENTS = DataComponentType.<SongbookContents>builder()
            .persistent(SongbookContents.CODEC).build();
    public static final DataComponentType<SongbookHeader> HEADER = DataComponentType.<SongbookHeader>builder()
            .persistent(SongbookHeader.CODEC).networkSynchronized(SongbookHeader.STREAM_CODEC).build();
    public static final DataComponentType<Boolean> EXTRACTED_PAGE = DataComponentType.<Boolean>builder()
            .persistent(Codec.BOOL).build();

    private SongbookComponents() { }

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, M3E.id("songbook_contents"), CONTENTS);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, M3E.id("songbook_header"), HEADER);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, M3E.id("songbook_extracted_page"), EXTRACTED_PAGE);
    }
}
