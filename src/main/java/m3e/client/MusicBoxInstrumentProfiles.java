package m3e.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import xerca.xercamusic.client.NoteSound;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.instrument.M3EInstruments;
import m3e.instrument.InstrumentItemComponents;
import m3e.instrument.InstrumentVariations;
import m3e.musicbox.MusicBoxLibraryAccess;
import m3e.musicbox.MusicBoxInstrumentProfileAccess;
import m3e.musicbox.MusicBoxDexedPatchAccess;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;
import m3e.network.MusicBoxInstrumentProfileStatePacket;
import m3e.network.MusicBoxInstrumentProfileUpdatePacket;
import m3e.sound.InstrumentSoundProfile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Client cache for profiles attached to individual music boxes. */
public final class MusicBoxInstrumentProfiles {
    private static final Map<BlockPos, Entry> PROFILES = new HashMap<>();
    private static final Map<BlockPos, Set<NoteSound>> ACTIVE_SOUNDS =
            new HashMap<>();
    private static @Nullable Object lastLevel;

    private MusicBoxInstrumentProfiles() { }

    public static synchronized InstrumentSoundProfile profile(TileEntityMusicBox musicBox) {
        int instrumentId = instrumentId(musicBox.getInstrument());
        Entry cached = PROFILES.get(musicBox.getBlockPos());
        if (cached != null && cached.instrumentId() == instrumentId) {
            return cached.profile();
        }
        if (instrumentId >= 0 && musicBox instanceof MusicBoxInstrumentProfileAccess access
                && access.m3e$musicBoxProfileInstrumentId() == instrumentId) {
            return access.m3e$musicBoxProfile();
        }
        return cachedProfile(musicBox.getBlockPos(), instrumentId,
                InstrumentSoundProfile.DEFAULT);
    }

    public static synchronized DexedPatch dexedPatch(TileEntityMusicBox musicBox) {
        int instrumentId = instrumentId(musicBox.getInstrument());
        Entry cached = PROFILES.get(musicBox.getBlockPos());
        if (cached != null && cached.instrumentId() == instrumentId) {
            return cached.dexedPatch();
        }
        if (musicBox instanceof MusicBoxDexedPatchAccess access) {
            return access.m3e$musicBoxDexedPatch();
        }
        return cachedPatch(musicBox.getBlockPos(), instrumentId);
    }

    public static synchronized DexedPerformance dexedPerformance(TileEntityMusicBox musicBox) {
        int instrumentId = instrumentId(musicBox.getInstrument());
        Entry cached = PROFILES.get(musicBox.getBlockPos());
        if (cached != null && cached.instrumentId() == instrumentId) {
            return cached.dexedPerformance();
        }
        if (musicBox instanceof MusicBoxDexedPatchAccess access) {
            return access.m3e$musicBoxDexedPerformance();
        }
        return cachedPerformance(musicBox.getBlockPos(), instrumentId);
    }

    public static synchronized String variation(TileEntityMusicBox musicBox) {
        int instrumentId = instrumentId(musicBox.getInstrument());
        if (instrumentId < 0) return InstrumentVariations.DEFAULT_ID;
        Entry cached = PROFILES.get(musicBox.getBlockPos());
        if (cached != null && cached.instrumentId() == instrumentId) {
            return cached.variation();
        }
        if (musicBox instanceof MusicBoxInstrumentProfileAccess access
                && access.m3e$musicBoxProfileInstrumentId() == instrumentId) {
            return InstrumentVariations.normalize(instrumentId,
                    access.m3e$musicBoxVariation());
        }
        ItemStack stack = MusicBoxLibraryAccess.of(musicBox)
                .m3e$musicBoxInstrumentStack();
        if (!stack.isEmpty()) {
            return InstrumentVariations.normalize(instrumentId,
                    stack.getOrDefault(InstrumentItemComponents.VARIATION,
                            InstrumentVariations.defaultId(instrumentId)));
        }
        return cachedVariation(musicBox.getBlockPos(), instrumentId);
    }

    public static synchronized void publish(BlockPos pos, int instrumentId,
                                            InstrumentSoundProfile profile) {
        InstrumentSoundProfile safe = profile == null ? InstrumentSoundProfile.DEFAULT : profile;
        BlockPos immutable = pos.immutable();
        apply(immutable, instrumentId, safe, cachedVariation(immutable, instrumentId),
                cachedPatch(immutable, instrumentId),
                cachedPerformance(immutable, instrumentId));
        if (ClientPlayNetworking.canSend(MusicBoxInstrumentProfileUpdatePacket.TYPE)) {
            ClientPlayNetworking.send(new MusicBoxInstrumentProfileUpdatePacket(
                    immutable, instrumentId, safe,
                    cachedVariation(immutable, instrumentId),
                    cachedPatch(immutable, instrumentId),
                    cachedPerformance(immutable, instrumentId)));
        }
    }

    public static synchronized void publish(BlockPos pos, int instrumentId,
                                            InstrumentSoundProfile profile,
                                            String variation, DexedPatch patch,
                                            DexedPerformance performance) {
        BlockPos immutable = pos.immutable();
        InstrumentSoundProfile safe = profile == null
                ? InstrumentSoundProfile.DEFAULT : profile;
        String safeVariation = InstrumentVariations.normalize(instrumentId, variation);
        DexedPatch safePatch = patch == null ? DexedPatch.initVoice() : patch;
        DexedPerformance safePerformance = performance == null
                ? DexedPerformance.DEFAULT : performance;
        apply(immutable, instrumentId, safe, safeVariation, safePatch, safePerformance);
        if (ClientPlayNetworking.canSend(MusicBoxInstrumentProfileUpdatePacket.TYPE)) {
            ClientPlayNetworking.send(new MusicBoxInstrumentProfileUpdatePacket(
                    immutable, instrumentId, safe, safeVariation,
                    safePatch, safePerformance));
        }
    }

    public static synchronized void receive(MusicBoxInstrumentProfileStatePacket packet) {
        apply(packet.pos(), packet.instrumentId(), packet.profile(), packet.variation(),
                packet.dexedPatch(),
                packet.dexedPerformance());
    }

    private static void apply(BlockPos pos, int instrumentId, InstrumentSoundProfile profile,
                              String variation,
                              DexedPatch dexedPatch, DexedPerformance dexedPerformance) {
        BlockPos immutable = pos.immutable();
        PROFILES.put(immutable, new Entry(instrumentId, profile, variation,
                dexedPatch, dexedPerformance));
        applyVolumeToActive(immutable, profile.volume());
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            Object blockEntity = client.level.getBlockEntity(immutable);
            if (blockEntity instanceof MusicBoxInstrumentProfileAccess access) {
                access.m3e$setMusicBoxProfile(instrumentId, profile);
                access.m3e$setMusicBoxVariation(variation);
            }
            if (blockEntity instanceof MusicBoxDexedPatchAccess access) {
                access.m3e$setMusicBoxDexedPatch(dexedPatch);
                access.m3e$setMusicBoxDexedPerformance(dexedPerformance);
            }
            if (blockEntity instanceof TileEntityMusicBox musicBox) {
                ItemStack stack = MusicBoxLibraryAccess.of(musicBox)
                        .m3e$musicBoxInstrumentStack();
                if (!stack.isEmpty()) {
                    stack.set(InstrumentItemComponents.SOUND_PROFILE, profile);
                    stack.set(InstrumentItemComponents.VARIATION, variation);
                    if (stack.getItem() == M3EInstruments.dexed()) {
                        stack.set(m3e.dexed.DexedComponents.PATCH, dexedPatch);
                        stack.set(m3e.dexed.DexedComponents.PERFORMANCE,
                                dexedPerformance);
                    }
                }
            }
        }
        if (instrumentId == M3EInstruments.dexed().getInstrumentId()) {
            DexedAudioManager.updateMusicBox(immutable, dexedPatch, dexedPerformance);
        }
    }

    /**
     * SoundController keeps the block-entity instance it started with. Retain
     * the position profile while a client level is active so a refreshed block
     * entity cannot make that running controller fall back to default ADSR.
     */
    public static synchronized void tick(Minecraft client) {
        ACTIVE_SOUNDS.values().removeIf(sounds -> {
            sounds.removeIf(NoteSound::isStopped);
            return sounds.isEmpty();
        });
        if (lastLevel == null) {
            lastLevel = client.level;
            return;
        }
        if (lastLevel == client.level) return;
        lastLevel = client.level;
        PROFILES.clear();
        ACTIVE_SOUNDS.clear();
    }

    public static synchronized void clear() {
        PROFILES.clear();
        ACTIVE_SOUNDS.clear();
        lastLevel = null;
    }

    static synchronized void registerActiveSound(BlockPos pos, NoteSound sound) {
        if (sound == null) return;
        BlockPos immutable = pos.immutable();
        ACTIVE_SOUNDS.computeIfAbsent(immutable, ignored -> new HashSet<>()).add(sound);
        Entry entry = PROFILES.get(immutable);
        if (entry != null && sound instanceof InstrumentEnvelopeAccess access) {
            access.m3e$setProfileVolume(entry.profile().volume());
        }
    }

    private static void applyVolumeToActive(BlockPos pos, float volume) {
        Set<NoteSound> sounds = ACTIVE_SOUNDS.get(pos);
        if (sounds == null) return;
        sounds.removeIf(sound -> sound == null || sound.isStopped());
        for (NoteSound sound : sounds) {
            if (sound instanceof InstrumentEnvelopeAccess access) {
                access.m3e$setProfileVolume(volume);
            }
        }
        if (sounds.isEmpty()) ACTIVE_SOUNDS.remove(pos);
    }

    static synchronized void remember(BlockPos pos, int instrumentId,
                                      InstrumentSoundProfile profile) {
        BlockPos immutable = pos.immutable();
        PROFILES.put(immutable, new Entry(instrumentId, profile,
                cachedVariation(immutable, instrumentId),
                cachedPatch(immutable, instrumentId),
                cachedPerformance(immutable, instrumentId)));
    }

    static synchronized InstrumentSoundProfile cachedProfile(
            BlockPos pos, int instrumentId, InstrumentSoundProfile fallback) {
        Entry cached = PROFILES.get(pos);
        return cached != null && cached.instrumentId() == instrumentId
                ? cached.profile() : fallback;
    }

    private static DexedPatch cachedPatch(BlockPos pos, int instrumentId) {
        Entry cached = PROFILES.get(pos);
        return cached != null && cached.instrumentId() == instrumentId
                ? cached.dexedPatch() : DexedPatch.initVoice();
    }

    private static String cachedVariation(BlockPos pos, int instrumentId) {
        Entry cached = PROFILES.get(pos);
        return cached != null && cached.instrumentId() == instrumentId
                ? cached.variation() : InstrumentVariations.defaultId(instrumentId);
    }

    private static DexedPerformance cachedPerformance(BlockPos pos, int instrumentId) {
        Entry cached = PROFILES.get(pos);
        return cached != null && cached.instrumentId() == instrumentId
                ? cached.dexedPerformance() : DexedPerformance.DEFAULT;
    }

    private static int instrumentId(@Nullable IItemInstrument instrument) {
        return instrument instanceof Item item ? M3EInstruments.protocolId(item) : -1;
    }

    private record Entry(int instrumentId, InstrumentSoundProfile profile,
                         String variation,
                         DexedPatch dexedPatch,
                         DexedPerformance dexedPerformance) { }
}
