package m3e.client;

import m3e.network.InstrumentExpressionStatePacket;
import m3e.network.InstrumentExpressionUpdatePacket;
import m3e.debug.MusicDebugTelemetry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import xerca.xercamusic.client.NoteSound;
import m3e.instrument.M3EInstruments;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Traditional channel-wide MIDI expression for Music Maker instruments:
 * 14-bit pitch bend uses the configured semitone range and CC1
 * controls a 5.5 Hz vibrato up to half a semitone.
 */
public final class InstrumentExpressionController {
    private static final float MAX_VIBRATO_DEPTH = 0.5F;
    private static final Map<Long, Expression> REMOTE = new HashMap<>();
    private static final Map<Long, List<WeakReference<NoteSound>>> REMOTE_SOUNDS = new HashMap<>();
    private static InstrumentExpressionTarget activeTarget;
    private static int bend14 = 8192;
    private static int modulation;
    /**
     * Songbook automation is deliberately separate from a physical MIDI
     * controller. Each wheel can be taken over manually until the next real
     * Songbook expression event, without rewriting the retained controller
     * position for the other wheel.
     */
    private static boolean songbookExpressionActive;
    private static float songbookBendSemitones;
    private static int songbookModulation;
    /** A player can take one wheel back until the next Songbook automation event. */
    private static boolean songbookBendOverridden;
    private static boolean songbookModulationOverridden;
    private static final AtomicInteger PENDING_MODULATION = new AtomicInteger(-1);
    private static final AtomicBoolean MODULATION_QUEUED = new AtomicBoolean();
    private static final AtomicLong MODULATION_GENERATION = new AtomicLong();

    private InstrumentExpressionController() { }

    public static synchronized void attach(InstrumentExpressionTarget target) {
        activeTarget = target;
        resetModulationQueue();
        bend14 = 8192;
        modulation = 0;
        applyEffectiveTarget();
        publishEffective();
        MusicDebugTelemetry.event("midi_input", "expression_target_attached",
                "instrument", target.instrumentId(), "target", target.getClass().getName());
    }

    public static synchronized void detach(InstrumentExpressionTarget target) {
        if (activeTarget != target) return;
        // A physical mod wheel may be left above zero when the instrument UI
        // closes. Neutralize local voices and publish that state before the
        // only route to the server is detached.
        resetModulationQueue();
        bend14 = 8192;
        modulation = 0;
        clearSongbookExpression();
        MusicDebugTelemetry.event("midi_input", "expression_target_detached",
                "instrument", target.instrumentId(), "target", target.getClass().getName());
        activeTarget = null;
    }

    public static void acceptPitchBend(int value14) {
        Minecraft.getInstance().execute(() -> update(Math.clamp(value14, 0, 16_383), null));
    }

    public static void acceptModulation(int value) {
        int clamped = Math.clamp(value, 0, 127);
        if (clamped == 0) {
            // A wheel release is semantically more important than the dense
            // sweep events before it. Invalidate any queued sweep task and
            // enqueue neutral directly so a stale queue latch cannot strand
            // the UI above zero.
            long generation = resetModulationQueue();
            Minecraft.getInstance().execute(() -> {
                if (MODULATION_GENERATION.get() == generation) update(null, 0);
            });
            return;
        }
        PENDING_MODULATION.set(clamped);
        scheduleModulationUpdate();
    }

    public static void resetControllers() {
        long generation = resetModulationQueue();
        Minecraft.getInstance().execute(() -> {
            if (MODULATION_GENERATION.get() == generation) update(8192, 0);
        });
    }

    public static synchronized int bend14() { return bend14; }
    public static synchronized int modulation() { return modulation; }

    public static synchronized float bendSemitones() {
        return expression(bend14, modulation).bendSemitones();
    }

    /** The pitch-wheel position currently shown by the Visual Piano. */
    public static synchronized int displayedBend14() {
        if (!songbookExpressionActive || songbookBendOverridden) return bend14;
        float range = Math.max(0.01F, InstrumentOptions.pitchBendRangeSemitones());
        float normalized = Math.clamp(songbookBendSemitones / range, -1.0F, 1.0F);
        return Math.clamp(Math.round(8192.0F + normalized * 8192.0F), 0, 16_383);
    }

    /** The CC1-wheel position currently shown by the Visual Piano. */
    public static synchronized int displayedModulation() {
        return songbookExpressionActive && !songbookModulationOverridden
                ? songbookModulation : modulation;
    }

    /** The exact semitone bend currently shown by the Visual Piano status strip. */
    public static synchronized float displayedBendSemitones() {
        return songbookExpressionActive && !songbookBendOverridden
                ? songbookBendSemitones : expression(bend14, modulation).bendSemitones();
    }

    /**
     * Applies one actual Songbook automation event. Note-on actions must not
     * call this method because their expression snapshot must not cancel a
     * player's manual wheel takeover.
     */
    static synchronized void showSongbookExpression(float bendSemitones, int nextModulation) {
        songbookExpressionActive = true;
        songbookBendSemitones = Math.clamp(bendSemitones, -96.0F, 96.0F);
        songbookModulation = Math.clamp(nextModulation, 0, 127);
        songbookBendOverridden = false;
        songbookModulationOverridden = false;
        applyEffectiveTarget();
        publishEffective();
    }

    /** Returns the Visual Piano to its live MIDI-controller positions. */
    static synchronized void clearSongbookExpression() {
        songbookExpressionActive = false;
        songbookBendSemitones = 0.0F;
        songbookModulation = 0;
        songbookBendOverridden = false;
        songbookModulationOverridden = false;
        applyEffectiveTarget();
        publishEffective();
    }

    static synchronized void refreshRange() {
        applyEffectiveTarget();
        publishEffective();
    }

    private static synchronized void update(Integer nextBend, Integer nextModulation) {
        if (nextBend != null) bend14 = nextBend;
        if (nextModulation != null) modulation = nextModulation;
        if (songbookExpressionActive) {
            if (nextBend != null) songbookBendOverridden = true;
            if (nextModulation != null) songbookModulationOverridden = true;
        }
        applyEffectiveTarget();
        publishEffective();
        if (nextModulation != null && modulation == 0) {
            MusicDebugTelemetry.event("midi_input", "expression_neutral",
                    "bend14", bend14, "cc1", modulation,
                    "target_attached", activeTarget != null);
        } else {
            MusicDebugTelemetry.sampled("midi_expression", 100_000_000L,
                    "midi_input", "expression", "bend14", bend14,
                    "bend_semitones", expression(bend14, modulation).bendSemitones(),
                    "cc1", modulation, "target_attached", activeTarget != null);
        }
    }

    /** Coalesces dense mod-wheel sweeps so the final CC1=0 cannot sit behind a task backlog. */
    private static void scheduleModulationUpdate() {
        if (!MODULATION_QUEUED.compareAndSet(false, true)) return;
        long generation = MODULATION_GENERATION.get();
        try {
            Minecraft.getInstance().execute(() -> {
                try {
                    if (generation != MODULATION_GENERATION.get()) return;
                    int latest = PENDING_MODULATION.getAndSet(-1);
                    if (latest >= 0) update(null, latest);
                } finally {
                    if (generation == MODULATION_GENERATION.get()) {
                        MODULATION_QUEUED.set(false);
                        if (PENDING_MODULATION.get() >= 0) scheduleModulationUpdate();
                    }
                }
            });
        } catch (RuntimeException exception) {
            if (generation == MODULATION_GENERATION.get()) MODULATION_QUEUED.set(false);
            throw exception;
        }
    }

    private static long resetModulationQueue() {
        long generation = MODULATION_GENERATION.incrementAndGet();
        PENDING_MODULATION.set(-1);
        MODULATION_QUEUED.set(false);
        return generation;
    }

    public static synchronized void applyLocal(NoteSound sound) {
        apply(sound, effectiveExpression());
    }

    public static synchronized void registerRemote(int playerId, int instrumentId, NoteSound sound) {
        long key = key(playerId, instrumentId);
        REMOTE_SOUNDS.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new WeakReference<>(sound));
        apply(sound, REMOTE.getOrDefault(key, Expression.CENTERED));
    }

    public static synchronized void receive(InstrumentExpressionStatePacket packet) {
        if (packet.instrumentId() == M3EInstruments.dexed().getInstrumentId()) {
            DexedAudioManager.expressionSemitones(packet.playerId(),
                    packet.bendSemitones(), packet.modulation());
        }
        long key = key(packet.playerId(), packet.instrumentId());
        Expression expression = new Expression(packet.bendSemitones(),
                vibratoDepth(packet.modulation()));
        REMOTE.put(key, expression);
        List<WeakReference<NoteSound>> sounds = REMOTE_SOUNDS.get(key);
        if (sounds == null) return;
        Iterator<WeakReference<NoteSound>> iterator = sounds.iterator();
        while (iterator.hasNext()) {
            NoteSound sound = iterator.next().get();
            if (sound == null || sound.isStopped()) iterator.remove();
            else apply(sound, expression);
        }
        if (sounds.isEmpty()) REMOTE_SOUNDS.remove(key);
    }

    public static synchronized void clearRemote() {
        REMOTE.clear();
        REMOTE_SOUNDS.clear();
        resetModulationQueue();
        bend14 = 8192;
        modulation = 0;
        clearSongbookExpression();
        applyEffectiveTarget();
    }

    private static void applyEffectiveTarget() {
        if (activeTarget != null) {
            Expression expression = effectiveExpression();
            activeTarget.applyMidiExpression(expression.bendSemitones(), expression.vibratoDepth());
        }
    }

    private static void publishEffective() {
        if (activeTarget != null && ClientPlayNetworking.canSend(InstrumentExpressionUpdatePacket.TYPE)) {
            Expression expression = effectiveExpression();
            ClientPlayNetworking.send(new InstrumentExpressionUpdatePacket(
                    activeTarget.instrumentId(), expression.bendSemitones(),
                    effectiveModulation()));
        }
    }

    private static Expression effectiveExpression() {
        float bend = songbookExpressionActive && !songbookBendOverridden
                ? songbookBendSemitones : expression(bend14, modulation).bendSemitones();
        return new Expression(bend, vibratoDepth(effectiveModulation()));
    }

    private static int effectiveModulation() {
        return songbookExpressionActive && !songbookModulationOverridden
                ? songbookModulation : modulation;
    }

    private static void apply(NoteSound sound, Expression expression) {
        if (sound instanceof InstrumentEnvelopeAccess access) {
            access.m3e$setMidiExpression(
                    expression.bendSemitones(), expression.vibratoDepth());
        }
    }

    private static Expression expression(int bend, int mod) {
        float normalizedBend = (Math.clamp(bend, 0, 16_383) - 8192) / 8192.0F;
        return new Expression(normalizedBend * InstrumentOptions.pitchBendRangeSemitones(),
                vibratoDepth(mod));
    }

    public static float vibratoDepth(int modulation) {
        return Math.clamp(modulation, 0, 127) / 127.0F * MAX_VIBRATO_DEPTH;
    }

    public static int modulationForDepth(float vibratoDepth) {
        return Math.clamp(Math.round(vibratoDepth / MAX_VIBRATO_DEPTH * 127.0F), 0, 127);
    }

    private static long key(int playerId, int instrumentId) {
        return ((long) playerId << 32) ^ (instrumentId & 0xFFFFFFFFL);
    }

    private record Expression(float bendSemitones, float vibratoDepth) {
        private static final Expression CENTERED = new Expression(0.0F, 0.0F);
    }
}
