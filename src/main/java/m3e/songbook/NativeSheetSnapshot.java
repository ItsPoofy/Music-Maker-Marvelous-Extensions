package m3e.songbook;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import xerca.xercamusic.common.MusicManager;
import xerca.xercamusic.common.NoteEvent;
import xerca.xercamusic.common.VolumeMarker;
import xerca.xercamusic.common.item.ItemMusicSheet;
import xerca.xercamusic.common.item.Items;
import m3e.sheet.MusicSheetComponents;
import m3e.sheet.MusicScore;
import m3e.sheet.SheetTiming;
import m3e.sheet.MusicMakerSheetCompatibility;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record NativeSheetSnapshot(CompoundTag metadata, CompoundTag music) {

    public NativeSheetSnapshot {
        metadata = metadata == null ? new CompoundTag() : metadata.copy();
        music = music == null ? new CompoundTag() : music.copy();
        if (!metadata.contains("id") || !metadata.contains("version")) {
            throw new IllegalArgumentException("native sheet snapshot has no identity");
        }
    }

    @Override public CompoundTag metadata() { return metadata.copy(); }
    @Override public CompoundTag music() { return music.copy(); }

    public String title() {
        return metadata.getStringOr("title", "Music Sheet");
    }

    public String author() {
        return metadata.getStringOr("author", "").strip();
    }

    public boolean signed() {
        return metadata.getIntOr("generation", 0) > 0;
    }

    public int bps() {
        return Math.clamp(metadata.getByteOr("bps", (byte) 8), 1, 50);
    }

    public float volume() {
        return Math.clamp(metadata.getFloatOr("volume", 1.0F), 0.0F, 1.0F);
    }

    public int lockedPreviewInstrument() {
        if (!metadata.getBooleanOr("preview_locked", false)
                || !metadata.contains("preview_instrument")) return -1;
        return Byte.toUnsignedInt(metadata.getByteOr("preview_instrument", (byte) -1));
    }

    public String instrumentId() {
        int index = lockedPreviewInstrument();
        if (index < 0 || index >= Items.INSTRUMENTS.size()
                || !(Items.INSTRUMENTS.get(index) instanceof net.minecraft.world.item.Item item)) return "";
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public String instrumentName() {
        int index = lockedPreviewInstrument();
        if (index < 0 || index >= Items.INSTRUMENTS.size()
                || !(Items.INSTRUMENTS.get(index) instanceof net.minecraft.world.item.Item item)) return "";
        return new ItemStack(item).getHoverName().getString();
    }

    public int playbackSourceBpm() {
        Integer stored = metadata.contains("source_bpm")
                ? metadata.getIntOr("source_bpm", SheetTiming.DEFAULT_TEMPO_BPM)
                : null;
        return SheetTiming.resolveMusicalTempo(stored, bps());
    }

    public List<NoteEvent> notes() {
        ArrayList<NoteEvent> notes = new ArrayList<>();
        NoteEvent.fillArrayFromNBT(notes, music);
        return notes.stream().map(NoteEvent::new).toList();
    }

    public List<VolumeMarker> volumeMarkers() {
        ArrayList<VolumeMarker> markers = new ArrayList<>();
        VolumeMarker.fillArrayFromNBT(markers, music);
        return markers.stream().map(VolumeMarker::new).toList();
    }

    public static NativeSheetSnapshot fromSheetFile(CompoundTag source, MinecraftServer server) {
        if (source == null) throw new IllegalArgumentException("sheet file is empty");
        CompoundTag tag = source.copy();
        ArrayList<NoteEvent> notes = new ArrayList<>();
        NoteEvent.fillArrayFromNBT(notes, tag);
        if (notes.isEmpty() && tag.contains(ItemMusicSheet.KEY_MUSIC_OLD)) {
            notes.addAll(ItemMusicSheet.convertFromOld(tag, server));
        }
        if (notes.isEmpty()) throw new IllegalArgumentException("sheet file contains no playable notes");
        validateNativeData(notes);
        ArrayList<VolumeMarker> markers = new ArrayList<>();
        VolumeMarker.fillArrayFromNBT(markers, tag);
        validateVolumeMarkers(markers);

        CompoundTag metadata = new CompoundTag();
        UUID id = tag.read(ItemMusicSheet.KEY_ID, UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        metadata.store("id", UUIDUtil.CODEC, id);
        metadata.putInt("version", tag.getIntOr(ItemMusicSheet.KEY_VERSION, 1));
        metadata.putInt("generation", tag.getIntOr(ItemMusicSheet.KEY_GENERATION, 0));
        int maximumEnd = notes.stream().mapToInt(note -> note.time + Byte.toUnsignedInt(note.length))
                .max().orElse(1);
        if (maximumEnd > 32_000) throw new IllegalArgumentException("sheet exceeds Music Maker's maximum length");
        metadata.putInt("length", tag.getIntOr(ItemMusicSheet.KEY_LENGTH, maximumEnd));
        metadata.putByte("bps", tag.getByteOr(ItemMusicSheet.KEY_BPS, (byte) 8));
        if (tag.contains(ItemMusicSheet.KEY_PREV_INSTRUMENT)) {
            metadata.putByte("preview_instrument", tag.getByteOr(ItemMusicSheet.KEY_PREV_INSTRUMENT, (byte) 0));
        }
        if (tag.contains(ItemMusicSheet.KEY_PREV_INSTRUMENT_LOCKED)) {
            metadata.putBoolean("preview_locked", tag.getBooleanOr(ItemMusicSheet.KEY_PREV_INSTRUMENT_LOCKED, false));
        }
        if (tag.contains(ItemMusicSheet.KEY_HIGHLIGHT_INTERVAL)) {
            metadata.putByte("highlight", tag.getByteOr(ItemMusicSheet.KEY_HIGHLIGHT_INTERVAL, (byte) 12));
        }
        metadata.putFloat("volume", tag.getFloatOr(ItemMusicSheet.KEY_VOLUME, 1.0F));
        if (tag.contains(ItemMusicSheet.KEY_TITLE)) {
            metadata.putString("title", tag.getStringOr(ItemMusicSheet.KEY_TITLE, ""));
        }
        if (tag.contains(ItemMusicSheet.KEY_AUTHOR)) {
            metadata.putString("author", tag.getStringOr(ItemMusicSheet.KEY_AUTHOR, ""));
        }

        CompoundTag music = new CompoundTag();
        NoteEvent.fillNBTFromArray(notes, music);
        if (!markers.isEmpty()) VolumeMarker.fillNBTFromArray(markers, music);
        return new NativeSheetSnapshot(metadata, music);
    }

    private static void validateNativeData(List<NoteEvent> notes) {
        for (NoteEvent note : notes) {
            int length = Byte.toUnsignedInt(note.length);
            byte[] waypoints = note.getEffectiveWaypoints();
            if (waypoints != null && waypoints.length > length) {
                throw new IllegalArgumentException("sheet has more glissando points than note length");
            }
        }
    }

    private static void validateVolumeMarkers(List<VolumeMarker> markers) {
        for (int first = 0; first < markers.size(); first++) {
            VolumeMarker marker = markers.get(first);
            if (!marker.isValid()) throw new IllegalArgumentException("sheet has an invalid volume marker");
            for (int second = first + 1; second < markers.size(); second++) {
                if (marker.overlaps(markers.get(second))) {
                    throw new IllegalArgumentException("sheet has overlapping volume markers");
                }
            }
        }
    }

    public static NativeSheetSnapshot capture(ItemStack sheet, MinecraftServer server) {
        if (!(sheet.getItem() instanceof ItemMusicSheet) || ItemMusicSheet.isEmptySheet(sheet)) {
            throw new IllegalArgumentException("sheet is completely blank");
        }
        UUID id = sheet.get(Items.SHEET_ID);
        int version = sheet.getOrDefault(Items.SHEET_VERSION, -1);
        if (id == null || version < 0) throw new IllegalArgumentException("sheet has no music identity");
        MusicManager.MusicData data = MusicManager.getMusicData(id, version, server);
        if (data == null || data.notes().isEmpty()) throw new IllegalArgumentException("sheet music data is unavailable");

        CompoundTag metadata = new CompoundTag();
        metadata.store("id", UUIDUtil.CODEC, id);
        copyInt(sheet, Items.SHEET_VERSION, metadata, "version");
        copyInt(sheet, Items.SHEET_GENERATION, metadata, "generation");
        copyInt(sheet, Items.SHEET_LENGTH, metadata, "length");
        copyByte(sheet, Items.SHEET_BPS, metadata, "bps");
        copyByte(sheet, Items.SHEET_PREV_INSTRUMENT, metadata, "preview_instrument");
        copyBoolean(sheet, Items.SHEET_PREV_INSTRUMENT_LOCKED, metadata, "preview_locked");
        copyByte(sheet, Items.SHEET_HIGHLIGHT_INTERVAL, metadata, "highlight");
        copyFloat(sheet, Items.SHEET_VOLUME, metadata, "volume");
        copyString(sheet, Items.SHEET_TITLE, metadata, "title");
        copyString(sheet, Items.SHEET_AUTHOR, metadata, "author");
        copyInt(sheet, MusicSheetComponents.SOURCE_BPM, metadata, "source_bpm");

        CompoundTag music = new CompoundTag();
        NoteEvent.fillNBTFromArray(data.notes(), music);
        if (data.volumeMarkers() != null) VolumeMarker.fillNBTFromArray(data.volumeMarkers(), music);
        return new NativeSheetSnapshot(metadata, music);
    }

    public MusicScore toPlaybackSong() {
        ArrayList<NoteEvent> nativeNotes = new ArrayList<>();
        NoteEvent.fillArrayFromNBT(nativeNotes, music);
        if (nativeNotes.isEmpty()) throw new IllegalArgumentException("sheet contains no playable notes");
        ArrayList<VolumeMarker> markers = new ArrayList<>();
        VolumeMarker.fillArrayFromNBT(markers, music);
        int bps = bps();
        ArrayList<MusicScore.MidiNote> notes = new ArrayList<>(nativeNotes.size());
        ArrayList<MusicScore.Glissando> glissandos = new ArrayList<>();
        long maximumEnd = 1;
        for (NoteEvent note : nativeNotes) {
            int start = Math.max(0, note.time);
            int length = Math.max(1, Byte.toUnsignedInt(note.length));
            int end = start + length;
            int velocity = Math.clamp(Math.round(
                    MusicMakerSheetCompatibility.midiVelocity(Byte.toUnsignedInt(note.volume))
                            * volume()), 1, 127);
            notes.add(new MusicScore.MidiNote(Byte.toUnsignedInt(note.note), velocity, start, end, end));
            byte[] waypoints = note.getEffectiveWaypoints();
            byte[] positions = note.getEffectivePositions();
            if (waypoints != null && waypoints.length > 0) {
                ArrayList<MusicScore.GlissandoPoint> points = new ArrayList<>(waypoints.length);
                for (int index = 0; index < waypoints.length; index++) {
                    int position = positions != null && positions.length == waypoints.length
                            ? Byte.toUnsignedInt(positions[index])
                            : Math.max(1, Math.round((index + 1) * 100.0F / waypoints.length));
                    points.add(new MusicScore.GlissandoPoint(position, waypoints[index]));
                }
                glissandos.add(new MusicScore.Glissando(0, start,
                        Byte.toUnsignedInt(note.note), points));
            }
            maximumEnd = Math.max(maximumEnd, end);
        }
        ArrayList<MusicScore.VolumeRamp> ramps = new ArrayList<>(markers.size());
        for (VolumeMarker marker : markers) {
            ramps.add(new MusicScore.VolumeRamp(0, marker.startTime, marker.endTime,
                    Math.clamp(Math.round(Byte.toUnsignedInt(marker.startVolume) * volume()), 1, 127),
                    Math.clamp(Math.round(Byte.toUnsignedInt(marker.endVolume) * volume()), 1, 127),
                    Byte.toUnsignedInt(marker.lowNote), Byte.toUnsignedInt(marker.highNote)));
            maximumEnd = Math.max(maximumEnd, marker.endTime);
        }
        long length = Math.max(maximumEnd, metadata.getIntOr("length", 1));
        return new MusicScore(bps, false, title(), length, 440.0F,
                List.of(new MusicScore.TempoChange(0, 1_000_000)),
                List.of(new MusicScore.TimeSignature(0, 4, 4)), List.of(), notes,
                new MusicScore.AutomationData(ramps, glissandos, playbackSourceBpm()),
                List.of(), author(), signed(), instrumentId(), false, false);
    }

    private static <T> void copy(ItemStack stack, net.minecraft.core.component.DataComponentType<T> type,
                                 java.util.function.Consumer<T> consumer) {
        T value = stack.get(type);
        if (value != null) consumer.accept(value);
    }
    private static void copyInt(ItemStack s, net.minecraft.core.component.DataComponentType<Integer> t, CompoundTag n, String k) { copy(s, t, v -> n.putInt(k, v)); }
    private static void copyByte(ItemStack s, net.minecraft.core.component.DataComponentType<Byte> t, CompoundTag n, String k) { copy(s, t, v -> n.putByte(k, v)); }
    private static void copyBoolean(ItemStack s, net.minecraft.core.component.DataComponentType<Boolean> t, CompoundTag n, String k) { copy(s, t, v -> n.putBoolean(k, v)); }
    private static void copyFloat(ItemStack s, net.minecraft.core.component.DataComponentType<Float> t, CompoundTag n, String k) { copy(s, t, v -> n.putFloat(k, v)); }
    private static void copyString(ItemStack s, net.minecraft.core.component.DataComponentType<String> t, CompoundTag n, String k) { copy(s, t, v -> n.putString(k, v)); }
}
