package m3e.musicbox;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import m3e.dexed.DexedComponents;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;
import m3e.sound.InstrumentSoundProfile;
import m3e.instrument.InstrumentItemComponents;
import m3e.instrument.InstrumentVariations;
import m3e.instrument.M3EInstruments;

/** Exact item state on the server; a separate lightweight snapshot on observing clients. */
public final class MusicBoxState {
    public static final String m3e$PROFILE_INSTRUMENT =
            "M3EMusicBoxProfileInstrument";
    public static final String m3e$PROFILE_VOLUME =
            "M3EMusicBoxProfileVolume";
    public static final String m3e$PROFILE_ATTACK =
            "M3EMusicBoxProfileAttack";
    public static final String m3e$PROFILE_DECAY =
            "M3EMusicBoxProfileDecay";
    public static final String m3e$PROFILE_SUSTAIN =
            "M3EMusicBoxProfileSustain";
    public static final String m3e$PROFILE_RELEASE =
            "M3EMusicBoxProfileRelease";
    public static final String m3e$PROFILE_TUNING =
            "M3EMusicBoxProfileTuning";
    public static final String m3e$VARIATION =
            "M3EMusicBoxVariation";
    public static final String m3e$INSTRUMENT_INSTANCE_ID =
            "M3EMusicBoxInstrumentInstanceId";
    public static final String m3e$PLAYBACK_LOOP_MODE =
            "M3EMusicBoxPlaybackLoopMode";
    public static final String m3e$PLAYBACK_SHUFFLE =
            "M3EMusicBoxPlaybackShuffle";
    public static final String m3e$PLAYBACK_SPEED =
            "M3EMusicBoxPlaybackSpeed";
    public static final String m3e$PLAYBACK_TEMPO =
            "M3EMusicBoxPlaybackTempo";
    public static final String m3e$PLAYBACK_TRANSPOSE =
            "M3EMusicBoxPlaybackTranspose";
    public static final String m3e$DEXED_PATCH =
            "M3EMusicBoxDexedPatch";
    public static final String m3e$DEXED_PERFORMANCE =
            "M3EMusicBoxDexedPerformance";
    public static final String m3e$SONGBOOK =
            "M3EMusicBoxSongbook";
    public static final String m3e$SONG_INDEX =
            "M3EMusicBoxSongIndex";
    public static final String m3e$INSTRUMENT_STACK =
            "M3EMusicBoxInstrumentStack";
    private record Instrument(int id, InstrumentSoundProfile profile, String variation,
                              String instanceId, DexedPatch patch, DexedPerformance performance) { }
    private static final Instrument EMPTY = new Instrument(-1, InstrumentSoundProfile.DEFAULT,
            InstrumentVariations.DEFAULT_ID, "", DexedPatch.initVoice(), DexedPerformance.DEFAULT);
    private Instrument snapshot = EMPTY;
    private ItemStack m3e$instrumentStack = ItemStack.EMPTY;
    private ItemStack m3e$songbook = ItemStack.EMPTY;
    private int m3e$songIndex;
    private int m3e$loopMode;
    private boolean m3e$shuffle;
    private float m3e$playbackSpeed = 1;
    private int m3e$playbackTempoBpm;
    private int m3e$playbackTranspose;

    private Instrument instrument() {
        if (m3e$instrumentStack.isEmpty()) return snapshot;
        int id = M3EInstruments.protocolId(m3e$instrumentStack.getItem());
        return new Instrument(id, m3e$instrumentStack.getOrDefault(InstrumentItemComponents.SOUND_PROFILE,
                InstrumentSoundProfile.DEFAULT), InstrumentVariations.normalize(id,
                m3e$instrumentStack.get(InstrumentItemComponents.VARIATION)),
                m3e$instrumentStack.getOrDefault(InstrumentItemComponents.INSTANCE_ID, ""),
                m3e$instrumentStack.getOrDefault(DexedComponents.PATCH, EMPTY.patch()),
                m3e$instrumentStack.getOrDefault(DexedComponents.PERFORMANCE, DexedPerformance.DEFAULT));
    }
    private void updateInstrument(Instrument value) {
        if (m3e$instrumentStack.isEmpty()) { snapshot = value; return; }
        m3e$instrumentStack.set(InstrumentItemComponents.SOUND_PROFILE, value.profile());
        m3e$instrumentStack.set(InstrumentItemComponents.VARIATION, value.variation());
        if (m3e$instrumentStack.getItem() == M3EInstruments.dexed()) {
            m3e$instrumentStack.set(DexedComponents.PATCH, value.patch());
            m3e$instrumentStack.set(DexedComponents.PERFORMANCE, value.performance());
        }
    }
    public InstrumentSoundProfile m3e$musicBoxProfile() {
        return instrument().profile();
    }

    public int m3e$musicBoxProfileInstrumentId() {
        return instrument().id();
    }

    public void m3e$setMusicBoxProfile(int instrumentId,
                                       InstrumentSoundProfile profile) {
        Instrument old = instrument();
        updateInstrument(new Instrument(instrumentId, profile == null ? InstrumentSoundProfile.DEFAULT : profile,
                old.variation(), old.instanceId(), old.patch(), old.performance()));
    }

    public int m3e$musicBoxLoopMode() {
        return m3e$loopMode;
    }

    public String m3e$musicBoxVariation() {
        return instrument().variation();
    }

    public void m3e$setMusicBoxVariation(String variation) {
        Instrument old = instrument();
        updateInstrument(new Instrument(old.id(), old.profile(), InstrumentVariations.normalize(old.id(), variation),
                old.instanceId(), old.patch(), old.performance()));
    }

    public String m3e$musicBoxInstrumentInstanceId() {
        return instrument().instanceId();
    }

    public void m3e$setMusicBoxLoopMode(int mode) {
        m3e$loopMode = MusicBoxLoopMode.byId(mode).ordinal();
    }

    public boolean m3e$isMusicBoxShuffle() {
        return m3e$shuffle;
    }

    public void m3e$setMusicBoxShuffle(boolean shuffle) {
        m3e$shuffle = shuffle;
    }

    public float m3e$musicBoxPlaybackSpeed() {
        return m3e$playbackSpeed;
    }

    public void m3e$setMusicBoxPlaybackSpeed(float speed) {
        m3e$playbackSpeed = Math.clamp(speed, 0.25F, 2.0F);
    }

    public int m3e$musicBoxPlaybackTempoBpm() {
        return m3e$playbackTempoBpm;
    }

    public void m3e$setMusicBoxPlaybackTempoBpm(int tempoBpm) {
        m3e$playbackTempoBpm = Math.clamp(tempoBpm, 0, 3_000);
    }

    public int m3e$musicBoxPlaybackTranspose() {
        return m3e$playbackTranspose;
    }

    public void m3e$setMusicBoxPlaybackTranspose(int semitones) {
        m3e$playbackTranspose = Math.clamp(semitones, -48, 48);
    }

    public DexedPatch m3e$musicBoxDexedPatch() {
        return instrument().patch();
    }

    public void m3e$setMusicBoxDexedPatch(DexedPatch patch) {
        Instrument old = instrument();
        updateInstrument(new Instrument(old.id(), old.profile(), old.variation(), old.instanceId(),
                patch == null ? DexedPatch.initVoice() : patch, old.performance()));
    }

    public DexedPerformance m3e$musicBoxDexedPerformance() {
        return instrument().performance();
    }

    public void m3e$setMusicBoxDexedPerformance(
            DexedPerformance performance) {
        Instrument old = instrument();
        updateInstrument(new Instrument(old.id(), old.profile(), old.variation(), old.instanceId(), old.patch(),
                performance == null ? DexedPerformance.DEFAULT : performance));
    }

    public ItemStack m3e$musicBoxSongbook() {
        return m3e$songbook;
    }

    public void m3e$setMusicBoxSongbook(ItemStack stack) {
        m3e$songbook = stack == null || stack.isEmpty()
                ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (m3e$songbook.isEmpty()) m3e$songIndex = 0;
    }

    public int m3e$musicBoxSongIndex() {
        return m3e$songIndex;
    }

    public void m3e$setMusicBoxSongIndex(int index) {
        m3e$songIndex = Math.max(0, index);
    }

    public ItemStack m3e$musicBoxInstrumentStack() {
        return m3e$instrumentStack;
    }

    public void m3e$setMusicBoxInstrumentStack(ItemStack stack) {
        m3e$instrumentStack = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        snapshot = EMPTY;
    }

    public void save(ValueOutput output) {
        output.putInt(m3e$PLAYBACK_LOOP_MODE, m3e$loopMode);
        output.putBoolean(m3e$PLAYBACK_SHUFFLE, m3e$shuffle);
        output.putFloat(m3e$PLAYBACK_SPEED, m3e$playbackSpeed);
        output.putInt(m3e$PLAYBACK_TEMPO, m3e$playbackTempoBpm);
        output.putInt(m3e$PLAYBACK_TRANSPOSE,
                m3e$playbackTranspose);
        if (!m3e$songbook.isEmpty()) {
            output.store(m3e$SONGBOOK, ItemStack.CODEC,
                    m3e$songbook);
            output.putInt(m3e$SONG_INDEX, m3e$songIndex);
        }
        if (!m3e$instrumentStack.isEmpty()) {
            output.store(m3e$INSTRUMENT_STACK, ItemStack.CODEC,
                    m3e$instrumentStack);
        }
    }
    public void load(ValueInput input) {
        m3e$loopMode = MusicBoxLoopMode.byId(input.getIntOr(m3e$PLAYBACK_LOOP_MODE, 0)).ordinal();
        m3e$shuffle = input.getBooleanOr(m3e$PLAYBACK_SHUFFLE, false);
        m3e$setMusicBoxPlaybackSpeed(input.getFloatOr(m3e$PLAYBACK_SPEED, 1));
        m3e$setMusicBoxPlaybackTempoBpm(input.getIntOr(m3e$PLAYBACK_TEMPO, 0));
        m3e$setMusicBoxPlaybackTranspose(input.getIntOr(m3e$PLAYBACK_TRANSPOSE, 0));
        m3e$songbook = input.read(m3e$SONGBOOK, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        m3e$songIndex = Math.max(0, input.getIntOr(m3e$SONG_INDEX, 0));
        m3e$setMusicBoxInstrumentStack(input.read(m3e$INSTRUMENT_STACK, ItemStack.CODEC).orElse(ItemStack.EMPTY));
        if (m3e$instrumentStack.isEmpty()) snapshot = new Instrument(input.getIntOr(m3e$PROFILE_INSTRUMENT, -1),
                new InstrumentSoundProfile(input.getFloatOr(m3e$PROFILE_VOLUME, 1),
                        input.getFloatOr(m3e$PROFILE_ATTACK, InstrumentSoundProfile.DEFAULT_SLIDER),
                        input.getFloatOr(m3e$PROFILE_DECAY, InstrumentSoundProfile.DEFAULT_SLIDER),
                        input.getFloatOr(m3e$PROFILE_SUSTAIN, InstrumentSoundProfile.DEFAULT_SLIDER),
                        input.getFloatOr(m3e$PROFILE_RELEASE, InstrumentSoundProfile.DEFAULT_SLIDER),
                        input.getFloatOr(m3e$PROFILE_TUNING, InstrumentSoundProfile.DEFAULT_TUNING_HZ)),
                input.getStringOr(m3e$VARIATION, InstrumentVariations.DEFAULT_ID),
                input.getStringOr(m3e$INSTRUMENT_INSTANCE_ID, ""),
                input.read(m3e$DEXED_PATCH, DexedPatch.CODEC).orElse(EMPTY.patch()),
                input.read(m3e$DEXED_PERFORMANCE, DexedPerformance.CODEC).orElse(DexedPerformance.DEFAULT));
    }
    public void clearLibrary() {
        m3e$songbook = ItemStack.EMPTY;
        m3e$songIndex = 0;
        m3e$loopMode = MusicBoxLoopMode.NO_LOOP.ordinal();
        m3e$shuffle = false;
    }
    public void writeSnapshot(CompoundTag tag, HolderLookup.Provider registries) {
        Instrument value = instrument();
        InstrumentSoundProfile profile = value.profile();
        tag.putInt(m3e$PROFILE_INSTRUMENT, value.id());
        tag.putFloat(m3e$PROFILE_VOLUME, profile.volume());
        tag.putFloat(m3e$PROFILE_ATTACK, profile.attack());
        tag.putFloat(m3e$PROFILE_DECAY, profile.decay());
        tag.putFloat(m3e$PROFILE_SUSTAIN, profile.sustain());
        tag.putFloat(m3e$PROFILE_RELEASE, profile.release());
        tag.putFloat(m3e$PROFILE_TUNING, profile.tuningHz());
        tag.putString(m3e$VARIATION, value.variation());
        tag.putString(m3e$INSTRUMENT_INSTANCE_ID, value.instanceId());
        var ops = registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        tag.put(m3e$DEXED_PATCH, DexedPatch.CODEC.encodeStart(ops, value.patch()).getOrThrow());
        tag.put(m3e$DEXED_PERFORMANCE, DexedPerformance.CODEC.encodeStart(ops, value.performance()).getOrThrow());
    }
}
