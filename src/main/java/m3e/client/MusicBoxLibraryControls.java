package m3e.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import xerca.xercamusic.common.item.ItemMusicSheet;
import xerca.xercamusic.common.item.Items;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.sheet.SheetTiming;
import m3e.musicbox.MusicBoxLibraryIdentity;
import m3e.network.MusicBoxLibraryActionPacket;
import m3e.network.MusicBoxLibraryStatePacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Connection-local catalog cache for music-box Playback screens. */
public final class MusicBoxLibraryControls {
    private static final Map<BlockPos, State> STATES = new HashMap<>();
    private static @Nullable Object lastLevel;

    private MusicBoxLibraryControls() { }

    public static synchronized void receive(MusicBoxLibraryStatePacket packet) {
        List<SongbookClientState.SongEntry> entries = packet.entries().stream()
                .map(entry -> new SongbookClientState.SongEntry(entry.entryId(),
                        entry.displayName(), entry.tempoBpm(), entry.durationMicros(),
                        entry.timeSignatureNumerator(),
                        entry.timeSignatureDenominator()))
                .toList();
        STATES.put(packet.pos().immutable(), new State(packet.kind(), packet.libraryId(),
                packet.label(), packet.revision(), packet.capacity(),
                packet.selectedIndex(), entries));
    }

    public static synchronized State state(TileEntityMusicBox musicBox) {
        State cached = STATES.get(musicBox.getBlockPos());
        ItemStack sheet = musicBox.getSheetStack();
        if (sheet.isEmpty()) return State.empty();
        if (ItemMusicSheet.isEmptySheet(sheet)) {
            return cached != null && cached.songbook() ? cached : State.empty();
        }
        if (cached != null) {
            UUID sheetId = sheet.get(Items.SHEET_ID);
            boolean selectedMatches = cached.selectedIndex() >= 0
                    && cached.selectedIndex() < cached.entries().size()
                    && java.util.Objects.equals(sheetId,
                    MusicBoxLibraryIdentity.sheetId(cached.libraryId(),
                            cached.entries().get(cached.selectedIndex()).entryId()));
            if (!cached.songbook() || selectedMatches) return cached;
        }
        UUID id = sheet.get(Items.SHEET_ID);
        String title = sheet.getOrDefault(Items.SHEET_TITLE, "Music Sheet");
        int tempo = MusicBoxPlaybackControls.sourceTempoBpm(musicBox);
        long duration = SheetTiming.from(sheet).durationMicros();
        SongbookClientState.SongEntry entry = new SongbookClientState.SongEntry(
                id == null ? MusicBoxLibraryStatePacket.NO_LIBRARY_ID : id,
                title, tempo, duration, 4, 4);
        return new State(MusicBoxLibraryStatePacket.LOOSE_SHEET,
                MusicBoxLibraryStatePacket.NO_LIBRARY_ID, "None", 0, 1, 0,
                List.of(entry));
    }

    public static void select(BlockPos pos, int index, int revision) {
        send(pos, MusicBoxLibraryActionPacket.SELECT, index, revision, 0);
    }

    public static void delete(BlockPos pos, int index, int revision) {
        send(pos, MusicBoxLibraryActionPacket.DELETE, index, revision, 0);
    }

    public static void extract(BlockPos pos, int index, int revision) {
        send(pos, MusicBoxLibraryActionPacket.EXTRACT, index, revision, 0);
    }

    public static void setTempo(BlockPos pos, int index, int revision, int tempoBpm) {
        send(pos, MusicBoxLibraryActionPacket.SET_TEMPO, index, revision,
                Math.clamp(tempoBpm, 1, 3_000));
    }

    private static void send(BlockPos pos, byte action, int index, int revision,
                             int value) {
        if (ClientPlayNetworking.canSend(MusicBoxLibraryActionPacket.TYPE)) {
            ClientPlayNetworking.send(new MusicBoxLibraryActionPacket(
                    pos.immutable(), action, index, revision, value));
        }
    }

    public static synchronized void tick(Minecraft client) {
        if (lastLevel == client.level) return;
        lastLevel = client.level;
        STATES.clear();
    }

    public static synchronized void clear() {
        STATES.clear();
        lastLevel = null;
    }

    public record State(byte kind, UUID libraryId, String label, int revision,
                        int capacity, int selectedIndex,
                        List<SongbookClientState.SongEntry> entries) {
        public State {
            entries = List.copyOf(entries == null ? List.of() : entries);
        }

        public boolean songbook() {
            return kind == MusicBoxLibraryStatePacket.SONGBOOK;
        }

        public static State empty() {
            return new State(MusicBoxLibraryStatePacket.EMPTY,
                    MusicBoxLibraryStatePacket.NO_LIBRARY_ID, "None", 0, 1,
                    -1, List.of());
        }
    }
}
