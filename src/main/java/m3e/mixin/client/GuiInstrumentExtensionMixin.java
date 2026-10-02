package m3e.mixin.client;

import m3e.client.InstrumentProfileClientState;
import m3e.client.InstrumentMidiDropController;
import m3e.client.SongbookClientState;
import m3e.client.InstrumentMidiScreenAccess;
import m3e.client.InstrumentOptions;
import m3e.client.HeldInstrumentEditSession;
import m3e.client.InstrumentPlaybackContext;
import m3e.client.InstrumentPerformanceControls;
import m3e.client.InstrumentEnvelopeAccess;
import m3e.client.InstrumentExpressionController;
import m3e.client.InstrumentExpressionTarget;
import m3e.client.RemoteEnvelopeContext;
import m3e.client.InstrumentStopAccess;
import m3e.client.MidiNoteVolume;
import m3e.client.MidiInputRefreshAccess;
import m3e.client.DexedAudioManager;
import m3e.client.DexedSongbookNoteRelay;
import m3e.client.LiveInstrumentNoteRelay;
import m3e.client.DexedPatchClient;
import m3e.dexed.DexedComponents;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;
import m3e.instrument.M3EInstruments;
import m3e.instrument.InstrumentVariations;
import m3e.sound.InstrumentSoundProfile;
import m3e.client.SmartPlayRuntime;
import m3e.api.client.M3EClientIntegration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xerca.xercamusic.client.GuiInstrument;
import xerca.xercamusic.client.MidiHandler;
import xerca.xercamusic.client.ModClient;
import xerca.xercamusic.client.NoteSound;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.block.BlockInstrument;
import xerca.xercamusic.common.packets.serverbound.SingleNotePacket;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/** Generic Music Maker MIDI player, velocity, and chord-display integration. */
@Mixin(value = GuiInstrument.class, priority = 1100)
abstract class GuiInstrumentExtensionMixin extends Screen implements InstrumentStopAccess,
        InstrumentMidiScreenAccess, InstrumentExpressionTarget {
    @Unique private final Set<Integer> m3e$performanceHeld = new HashSet<>();
    @Unique private final Map<Integer, Integer> m3e$songbookChordCounts = new HashMap<>();
    @Unique private final Map<Integer, Integer> m3e$dexedSongbookKeyCounts = new HashMap<>();
    @Unique private final Map<Integer, Long> m3e$dexedLiveEvents = new HashMap<>();
    @Unique private final Map<Integer, Long> m3e$sampledLiveEvents =
            new HashMap<>();
    @Unique private final Set<Integer> m3e$dexedEnsembleNotes = new HashSet<>();
    @Unique private final Set<Integer> m3e$dexedEnsembleControls = new HashSet<>();
    @Unique private SmartPlayRuntime m3e$smartPlay;
    @Unique private boolean m3e$smartPlayDispatch;
    @Unique private ItemStack m3e$sourceStack = ItemStack.EMPTY;
    @Unique private final HeldInstrumentEditSession m3e$heldEditSession =
            new HeldInstrumentEditSession();
    @Shadow @Final private boolean[] buttonPushStates;
    @Shadow @Final private NoteSound[] noteSounds;
    @Shadow @Final private Player player;
    @Shadow @Final private IItemInstrument instrument;
    @Shadow @Final private @Nullable BlockPos blockInsPos;
    @Shadow @Final private MidiHandler midiHandler;

    protected GuiInstrumentExtensionMixin(Component title) { super(title); }

    @org.spongepowered.asm.mixin.gen.Invoker("stopAllSounds")
    protected abstract void m3e$stopAllSounds();

    @org.spongepowered.asm.mixin.gen.Invoker("playSound")
    protected abstract void m3e$playWithVelocity(MidiHandler.MidiData data);

    @org.spongepowered.asm.mixin.gen.Invoker("stopSound")
    protected abstract void m3e$stopNote(int noteId);

    @Override public void m3e$stopAllPlayerNotes() { m3e$stopAllSounds(); }

    @Override public boolean playMidiNote(int midiNote, int velocity) {
        int noteId = IItemInstrument.noteToId(midiNote);
        boolean layeredDexed = M3EInstruments.isDexed(instrument)
                && InstrumentPlaybackContext.songbookEventId() != 0L;
        if (noteId < 0 || noteId >= noteSounds.length
                || buttonPushStates[noteId] && !layeredDexed) return false;
        m3e$playWithVelocity(new MidiHandler.MidiData(
                noteId, MidiNoteVolume.normalizedLive(velocity)));
        // A Left Split chord-control note is intentionally silent, but it is
        // still an acquired held note that must receive a balanced release.
        return buttonPushStates[noteId];
    }

    @Override public void stopMidiNote(int midiNote) {
        int noteId = IItemInstrument.noteToId(midiNote);
        if (noteId >= 0 && noteId < noteSounds.length) m3e$stopNote(noteId);
    }

    @Override public void stopMidiNoteImmediately(int midiNote) {
        int noteId = IItemInstrument.noteToId(midiNote);
        if (noteId < 0 || noteId >= noteSounds.length) return;
        NoteSound sound = noteSounds[noteId];
        m3e$stopNote(noteId);
        if (sound instanceof InstrumentEnvelopeAccess access) access.m3e$stopImmediately();
    }

    @Override public void applyMidiGlissando(int midiNote, byte[] semitoneWaypoints,
                                              @Nullable byte[] waypointPositions,
                                              int lengthTicks) {
        int noteId = IItemInstrument.noteToId(midiNote);
        if (noteId < 0 || noteId >= noteSounds.length || noteSounds[noteId] == null
                || semitoneWaypoints == null || semitoneWaypoints.length == 0) return;
        IItemInstrument.InsSound source = m3e$resolveLocal(midiNote);
        if (source == null) return;
        float[] pitches = new float[semitoneWaypoints.length];
        for (int index = 0; index < pitches.length; index++) {
            pitches[index] = source.pitch() * (float) Math.pow(2.0,
                    semitoneWaypoints[index] / 12.0);
        }
        if (waypointPositions != null && waypointPositions.length == pitches.length) {
            float[] positions = new float[waypointPositions.length];
            for (int index = 0; index < positions.length; index++) {
                positions[index] = (waypointPositions[index] & 0xFF) / 100.0F;
            }
            noteSounds[noteId].setGlissando(pitches, positions, Math.max(1, lengthTicks));
        } else {
            noteSounds[noteId].setGlissando(pitches, Math.max(1, lengthTicks));
        }
    }

    @Override public void applyMidiGlissando(long eventId, int midiNote,
                                              byte[] semitoneWaypoints,
                                              @Nullable byte[] waypointPositions,
                                              long durationMicros) {
        if (!M3EInstruments.isDexed(instrument)) {
            InstrumentMidiScreenAccess.super.applyMidiGlissando(eventId, midiNote,
                    semitoneWaypoints, waypointPositions, durationMicros);
            return;
        }
        float[] waypoints = new float[semitoneWaypoints.length];
        float[] positions = new float[semitoneWaypoints.length];
        for (int index = 0; index < waypoints.length; index++) {
            waypoints[index] = semitoneWaypoints[index];
            positions[index] = waypointPositions != null
                    && waypointPositions.length == waypoints.length
                    ? (waypointPositions[index] & 0xFF) / 100.0F
                    : (index + 1.0F) / waypoints.length;
        }
        DexedAudioManager.songbookVoiceGlissando(player.getId(), eventId,
                waypoints, positions, durationMicros);
        DexedSongbookNoteRelay.glissando(eventId, waypoints, positions, durationMicros);
    }

    @Override public void setMidiDynamicVolume(int midiNote, float volume) {
        int noteId = IItemInstrument.noteToId(midiNote);
        if (noteId >= 0 && noteId < noteSounds.length && noteSounds[noteId] != null) {
            noteSounds[noteId].setDynamicVolume(Math.clamp(volume, 0.0F, 1.0F));
        }
    }

    @Override public @Nullable NoteSound takeMidiNote(int midiNote) {
        int noteId = IItemInstrument.noteToId(midiNote);
        if (noteId < 0 || noteId >= noteSounds.length) return null;
        NoteSound sound = noteSounds[noteId];
        noteSounds[noteId] = null;
        buttonPushStates[noteId] = false;
        return sound;
    }

    @Override public void adoptMidiNote(int midiNote, @Nullable NoteSound sound) {
        int noteId = IItemInstrument.noteToId(midiNote);
        if (noteId < 0 || noteId >= noteSounds.length || sound == null) return;
        noteSounds[noteId] = sound;
        buttonPushStates[noteId] = true;
    }

    @Override public int instrumentId() { return instrument.getInstrumentId(); }

    @Override public void applyMidiExpression(float bendSemitones, float vibratoDepth) {
        if (M3EInstruments.isDexed(instrument)) {
            DexedAudioManager.expressionSemitones(player.getId(), bendSemitones,
                    InstrumentExpressionController.modulationForDepth(vibratoDepth));
        }
        for (NoteSound sound : noteSounds) {
            if (sound instanceof InstrumentEnvelopeAccess access && !sound.isStopped()) {
                access.m3e$setMidiExpression(bendSemitones, vibratoDepth);
            }
        }
    }

    @Override public boolean playbackInstrumentPresent() {
        if (blockInsPos != null) {
            return player.level().getBlockState(blockInsPos).getBlock() instanceof BlockInstrument block
                    && Objects.equals(block.getItemInstrument(), instrument);
        }
        if (m3e$sourceStack.isEmpty()) return false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (!candidate.isEmpty()
                    && candidate.getItem() == m3e$sourceStack.getItem()) return true;
        }
        return false;
    }

    @Override public void setMidiDynamicVolume(long eventId, int midiNote, float volume) {
        if (M3EInstruments.isDexed(instrument)) {
            DexedAudioManager.songbookVoiceGain(player.getId(), eventId, volume);
            DexedSongbookNoteRelay.gain(eventId, volume);
            return;
        }
        setMidiDynamicVolume(midiNote, volume);
    }

    @Override public void reopenInstrumentScreen() { ModClient.showInstrumentGui(instrument, blockInsPos); }

    @Override public HeldInstrumentEditSession heldInstrumentEditSession() {
        return m3e$heldEditSession;
    }

    @Override public void onFilesDrop(List<Path> paths) {
        if (!InstrumentMidiDropController.handle(this, paths)) super.onFilesDrop(paths);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void m3e$attachMidiTransport(CallbackInfo ci) {
        if (blockInsPos == null && m3e$sourceStack.isEmpty()) {
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            if (main.getItem() == instrument) m3e$sourceStack = main;
            else if (off.getItem() == instrument) m3e$sourceStack = off;
        }
        if (!m3e$sourceStack.isEmpty()) {
            int instrumentId = instrument.getInstrumentId();
            m3e$heldEditSession.initialize(
                    InstrumentOptions.playerDefaultProfile(instrumentId),
                    InstrumentOptions.playerDefaultVariation(instrumentId));
            InstrumentProfileClientState.activate(instrumentId);
        }
        SongbookClientState.attach(this);
        InstrumentExpressionController.attach(this);
        ((MidiInputRefreshAccess) midiHandler)
                .m3e$refreshMidiInputs(true);
    }

    @Override public void setMidiNoteExpression(int midiNote, float bendSemitones,
                                                float vibratoDepth) {
        if (M3EInstruments.isDexed(instrument)) {
            DexedAudioManager.expressionSemitones(player.getId(), bendSemitones,
                    InstrumentExpressionController.modulationForDepth(vibratoDepth));
        }
        int noteId = IItemInstrument.noteToId(midiNote);
        if (noteId >= 0 && noteId < noteSounds.length
                && noteSounds[noteId] instanceof InstrumentEnvelopeAccess access
                && !noteSounds[noteId].isStopped()) {
            access.m3e$setMidiExpression(bendSemitones, vibratoDepth);
        }
    }

    @Override public void setMidiNoteExpression(long eventId, int midiNote,
                                                 float bendSemitones,
                                                 float vibratoDepth) {
        if (M3EInstruments.isDexed(instrument)) {
            DexedAudioManager.songbookVoiceExpression(player.getId(), eventId,
                    bendSemitones,
                    InstrumentExpressionController.modulationForDepth(vibratoDepth));
            DexedSongbookNoteRelay.expression(eventId, bendSemitones,
                    InstrumentExpressionController.modulationForDepth(vibratoDepth));
            return;
        }
        setMidiNoteExpression(midiNote, bendSemitones, vibratoDepth);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void m3e$refreshMidiInputs(CallbackInfo ci) {
        ((MidiInputRefreshAccess) midiHandler)
                .m3e$refreshMidiInputs(false);
    }

    @Inject(method = "playSound(I)V", at = @At("HEAD"), cancellable = true)
    private void m3e$keyboardVelocity(int noteId, CallbackInfo ci) {
        float volume = InstrumentPerformanceControls.keyboardVelocity() / 127.0F;
        if (!m3e$redirectLiveNote(noteId, volume)) {
            m3e$playWithVelocity(new MidiHandler.MidiData(noteId, volume));
        }
        ci.cancel();
    }

    @Inject(method = "playSound(Lxerca/xercamusic/client/MidiHandler$MidiData;)V",
            at = @At("HEAD"), cancellable = true)
    private void m3e$trackChordInput(MidiHandler.MidiData data, CallbackInfo ci) {
        if (!m3e$smartPlayDispatch
                && !InstrumentPlaybackContext.isSongbookPlayback()
                && m3e$redirectLiveNote(data.noteId(), data.volume())) {
            ci.cancel();
            return;
        }
        int noteId = data.noteId();
        int note = IItemInstrument.idToNote(noteId);
        boolean chordInput = M3EClientIntegration.instrumentUiEnabled()
                && InstrumentPlaybackContext.useForChordInput()
                && noteId >= 0 && noteId < noteSounds.length
                && m3e$resolveLocal(note) != null
                && M3EClientIntegration.useForChordDisplay(note);
        if (chordInput) {
            boolean songbook = InstrumentPlaybackContext.songbookEventId() != 0L;
            boolean first = songbook
                    ? m3e$songbookChordCounts.merge(noteId, 1, Integer::sum) == 1
                    : m3e$performanceHeld.add(noteId);
            if (first) {
                InstrumentPerformanceControls.noteOn(note,
                        Math.clamp(Math.round(data.volume() * 127.0F), 1, 127));
            }
        }
        if (!InstrumentPlaybackContext.localPreview()) {
            InstrumentProfileClientState.activate(instrument.getInstrumentId());
        }
        long songbookEvent = InstrumentPlaybackContext.songbookEventId();
        if (M3EInstruments.isDexed(instrument) && songbookEvent == 0L
                && M3EClientIntegration.splitControlEnabled() && chordInput) {
            // Redstone Synth replaces Music Maker's method at this injector,
            // so its silent extension-provided chord-control path must live here as well.
            // Sampled instruments continue through the extension observer.
            if (!buttonPushStates[noteId]) {
                buttonPushStates[noteId] = true;
                m3e$dexedEnsembleControls.add(noteId);
                M3EClientIntegration.sendControlNote(note,
                        Math.clamp(Math.round(data.volume() * 127.0F), 1, 127), false);
            }
            ci.cancel();
            return;
        }
        // The active extension intentionally cancels left-split chord-control notes before
        // Music Maker creates a sound. They still belong to chord recognition,
        // but must not leave an unbalanced local envelope frame behind.
        if (M3EClientIntegration.splitControlEnabled() && chordInput) return;
        if (M3EInstruments.isDexed(instrument)) {
            // Music Maker deliberately forwards -1 when a click lands outside
            // the piano. Its normal path rejects that sentinel before touching
            // the key arrays, so preserve the same boundary here.
            if (noteId < 0 || noteId >= buttonPushStates.length) return;
            DexedPatchClient.Held held = DexedPatchClient.held();
            DexedPatch patch;
            DexedPerformance performance;
            if (held != null) {
                patch = held.patch();
                performance = held.performance();
                if (InstrumentPlaybackContext.localPreview()) {
                    DexedAudioManager.receivePatch(player.getId(), patch, performance);
                } else {
                    DexedPatchClient.apply(held, patch, performance, false);
                }
            } else if (songbookEvent != 0L
                    && m3e$sourceStack.getItem() == instrument) {
                // Songbook transport retains the patch of the instrument that
                // opened it. The player may scroll to another hotbar slot
                // without breaking a song already in progress.
                patch = m3e$sourceStack.getOrDefault(
                        DexedComponents.PATCH, DexedPatch.initVoice());
                performance = m3e$sourceStack.getOrDefault(
                        DexedComponents.PERFORMANCE, DexedPerformance.DEFAULT);
            } else {
                ci.cancel();
                return;
            }
            boolean firstLivePress = !buttonPushStates[noteId];
            buttonPushStates[noteId] = true;
            int velocity = Math.clamp(Math.round(data.volume() * 127.0F), 1, 127);
            if (songbookEvent != 0L) {
                m3e$dexedSongbookKeyCounts.merge(noteId, 1, Integer::sum);
                DexedAudioManager.songbookNoteOn(player.getId(), player.position(), patch,
                        performance, songbookEvent, note, velocity);
                if (!InstrumentPlaybackContext.localPreview()) {
                    DexedSongbookNoteRelay.noteOn(songbookEvent, note, velocity);
                }
            } else {
                if (firstLivePress && M3EClientIntegration.ensembleActive()
                        && m3e$dexedEnsembleNotes.add(noteId)) {
                    M3EClientIntegration.sendEnsembleNote(note, velocity, false, instrument);
                }
                DexedAudioManager.noteOn(player.getId(), player.position(), patch,
                        performance, note, velocity);
                Long previousEvent = m3e$dexedLiveEvents.put(noteId,
                        DexedSongbookNoteRelay.liveNoteOn(note, velocity));
                if (previousEvent != null && previousEvent > 0L) {
                    DexedSongbookNoteRelay.liveNoteOff(previousEvent, note);
                }
            }
            player.level().addParticle(ParticleTypes.NOTE,
                    player.getX(), player.getY() + 2.2D, player.getZ(),
                    note / 24.0D, 0.0D, 0.0D);
            ci.cancel();
            return;
        }
        RemoteEnvelopeContext.begin(InstrumentPlaybackContext.apply(
                m3e$playbackProfile()));
    }

    @Inject(method = "playSound(Lxerca/xercamusic/client/MidiHandler$MidiData;)V", at = @At("RETURN"))
    private void m3e$finishLocalEnvelope(MidiHandler.MidiData data, CallbackInfo ci) {
        RemoteEnvelopeContext.end();
    }

    @Redirect(method = "playSound(Lxerca/xercamusic/client/MidiHandler$MidiData;)V",
            at = @At(value = "INVOKE", target =
                    "Lxerca/xercamusic/common/item/IItemInstrument;getSound(I)Lxerca/xercamusic/common/item/IItemInstrument$InsSound;"))
    private IItemInstrument.InsSound m3e$variationSound(
            IItemInstrument target, int note) {
        return InstrumentVariations.resolve(target, note,
                m3e$playbackVariation(target.getInstrumentId()));
    }

    @Unique
    private InstrumentSoundProfile m3e$playbackProfile() {
        if (InstrumentPlaybackContext.localPreview()) return InstrumentSoundProfile.DEFAULT;
        int instrumentId = instrument.getInstrumentId();
        return m3e$heldEditSession.profile(InstrumentOptions.playerDefaultProfile(instrumentId));
    }

    @Unique
    private String m3e$playbackVariation(int instrumentId) {
        return m3e$heldEditSession.variation(InstrumentOptions.playerDefaultVariation(instrumentId));
    }

    @Inject(method = "stopSound(I)V", at = @At("HEAD"), cancellable = true)
    private void m3e$releaseChordInput(int noteId, CallbackInfo ci) {
        if (!m3e$smartPlayDispatch
                && !InstrumentPlaybackContext.isSongbookPlayback()
                && m3e$releaseSmartPlayNote(noteId)) {
            ci.cancel();
            return;
        }
        if (InstrumentPlaybackContext.songbookEventId() != 0L
                && m3e$songbookChordCounts.containsKey(noteId)) {
            int remaining = m3e$songbookChordCounts.getOrDefault(noteId, 0) - 1;
            if (remaining <= 0) {
                m3e$songbookChordCounts.remove(noteId);
                InstrumentPerformanceControls.noteOff(IItemInstrument.idToNote(noteId));
            } else {
                m3e$songbookChordCounts.put(noteId, remaining);
            }
        } else if (m3e$performanceHeld.remove(noteId)) {
            InstrumentPerformanceControls.noteOff(IItemInstrument.idToNote(noteId));
        }
        long songbookEvent = InstrumentPlaybackContext.songbookEventId();
        if (M3EInstruments.isDexed(instrument) && noteId >= 0
                && noteId < buttonPushStates.length
                && m3e$dexedEnsembleControls.remove(noteId)) {
            int note = IItemInstrument.idToNote(noteId);
            buttonPushStates[noteId] = false;
            M3EClientIntegration.sendControlNote(note, 127, true);
            ci.cancel();
            return;
        }
        if (M3EInstruments.isDexed(instrument) && noteId >= 0
                && noteId < buttonPushStates.length
                && (buttonPushStates[noteId] || songbookEvent != 0L)) {
            int note = IItemInstrument.idToNote(noteId);
            if (songbookEvent != 0L) {
                DexedAudioManager.songbookNoteOff(player.getId(), songbookEvent);
                if (!InstrumentPlaybackContext.localPreview()) {
                    DexedSongbookNoteRelay.noteOff(songbookEvent, note);
                }
                int remaining = m3e$dexedSongbookKeyCounts.getOrDefault(noteId, 0) - 1;
                if (remaining <= 0) {
                    m3e$dexedSongbookKeyCounts.remove(noteId);
                    buttonPushStates[noteId] = false;
                } else {
                    m3e$dexedSongbookKeyCounts.put(noteId, remaining);
                }
            } else {
                buttonPushStates[noteId] = false;
                if (m3e$dexedEnsembleNotes.remove(noteId)) {
                    M3EClientIntegration.sendEnsembleNote(note, 127, true, instrument);
                }
                DexedAudioManager.noteOff(player.getId(), note);
                Long liveEvent = m3e$dexedLiveEvents.remove(noteId);
                if (liveEvent != null) DexedSongbookNoteRelay.liveNoteOff(liveEvent, note);
            }
            ci.cancel();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void m3e$detachMidiTransport(CallbackInfo ci) {
        SongbookClientState.detach(this);
        InstrumentExpressionController.detach(this);
        m3e$smartPlayDispatch = true;
        try {
            for (int noteId : Set.copyOf(m3e$performanceHeld)) {
                m3e$stopNote(noteId);
            }
        } finally {
            m3e$smartPlayDispatch = false;
        }
        m3e$performanceHeld.clear();
        for (Map.Entry<Integer, Long> entry : Map.copyOf(
                m3e$dexedLiveEvents).entrySet()) {
            DexedSongbookNoteRelay.liveNoteOff(entry.getValue(),
                    IItemInstrument.idToNote(entry.getKey()));
        }
        m3e$dexedLiveEvents.clear();
        for (int noteId : Set.copyOf(m3e$dexedEnsembleNotes)) {
            M3EClientIntegration.sendEnsembleNote(IItemInstrument.idToNote(noteId), 127,
                    true, instrument);
        }
        m3e$dexedEnsembleNotes.clear();
        for (int noteId : Set.copyOf(m3e$dexedEnsembleControls)) {
            M3EClientIntegration.sendControlNote(IItemInstrument.idToNote(noteId), 127, true);
            if (noteId >= 0 && noteId < buttonPushStates.length) {
                buttonPushStates[noteId] = false;
            }
        }
        m3e$dexedEnsembleControls.clear();
        for (Map.Entry<Integer, Long> entry : Map.copyOf(
                m3e$sampledLiveEvents).entrySet()) {
            LiveInstrumentNoteRelay.noteOff(entry.getValue(), instrument.getInstrumentId(),
                    IItemInstrument.idToNote(entry.getKey()));
        }
        m3e$sampledLiveEvents.clear();
        if (m3e$smartPlay != null) m3e$smartPlay.reset();
        // A running Songbook continues to use this screen's playback bridge
        // after the player scrolls away from the instrument. Keep its held
        // chord state until the transport sends the matching releases.
        if (!SongbookClientState.playbackRunning()) {
            InstrumentPerformanceControls.clearInput();
        }
    }

    @Redirect(method = {
            "playSound(Lxerca/xercamusic/client/MidiHandler$MidiData;)V",
            "stopSound(I)V"
    }, at = @At(value = "INVOKE", target =
            "Lxerca/xercamusic/client/ModClient;sendToServer(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
    private void m3e$suppressReplacedLegacyNote(
            CustomPacketPayload payload) {
        if (InstrumentPlaybackContext.localPreview()) return;
        if (payload instanceof SingleNotePacket packet) {
            int noteId = IItemInstrument.noteToId(packet.note());
            if (packet.isStop()) {
                Long eventId = m3e$sampledLiveEvents.remove(noteId);
                if (eventId != null) {
                    LiveInstrumentNoteRelay.noteOff(eventId,
                            packet.instrumentItem().getInstrumentId(), packet.note());
                    return;
                }
            } else {
                long eventId = LiveInstrumentNoteRelay.noteOn(
                        packet.instrumentItem().getInstrumentId(), packet.note(),
                        Math.clamp(Math.round(packet.volume() * 127.0F), 1, 127));
                if (eventId > 0L) {
                    m3e$sampledLiveEvents.put(noteId, eventId);
                    return;
                }
            }
        }
        ModClient.sendToServer(payload);
    }

    @Unique
    private boolean m3e$redirectLiveNote(int sourceNoteId, float volume) {
        if (sourceNoteId < 0 || sourceNoteId >= noteSounds.length) return false;
        return m3e$smartPlay().noteOn(
                IItemInstrument.idToNote(sourceNoteId), volume,
                InstrumentOptions.smartPlay());
    }

    @Unique
    private boolean m3e$releaseSmartPlayNote(int sourceNoteId) {
        if (m3e$smartPlay == null) return false;
        return m3e$smartPlay.noteOff(
                IItemInstrument.idToNote(sourceNoteId), InstrumentOptions.smartPlay());
    }

    @Unique
    private SmartPlayRuntime m3e$smartPlay() {
        if (m3e$smartPlay == null) {
            m3e$smartPlay = new SmartPlayRuntime(new SmartPlayRuntime.Output() {
                @Override public void play(int midiNote, float volume) {
                    int noteId = IItemInstrument.noteToId(midiNote);
                    if (noteId < 0 || noteId >= noteSounds.length) return;
                    m3e$smartPlayDispatch = true;
                    try {
                        m3e$playWithVelocity(new MidiHandler.MidiData(
                                noteId, Math.clamp(volume, 0.0F, 1.0F)));
                    } finally {
                        m3e$smartPlayDispatch = false;
                    }
                }

                @Override public void stop(int midiNote) {
                    int noteId = IItemInstrument.noteToId(midiNote);
                    if (noteId < 0 || noteId >= noteSounds.length) return;
                    m3e$smartPlayDispatch = true;
                    try {
                        m3e$stopNote(noteId);
                    } finally {
                        m3e$smartPlayDispatch = false;
                    }
                }

                @Override public boolean playable(int midiNote) {
                    return m3e$resolveLocal(midiNote) != null;
                }
            }, IItemInstrument.MIN_NOTE, IItemInstrument.MAX_NOTE);
        }
        return m3e$smartPlay;
    }

    @Unique
    private IItemInstrument.InsSound m3e$resolveLocal(int note) {
        int instrumentId = instrument.getInstrumentId();
        return InstrumentVariations.resolve(instrument, note,
                m3e$heldEditSession.variation(
                        InstrumentOptions.playerDefaultVariation(instrumentId)));
    }
}
