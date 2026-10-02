package m3e.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import xerca.xercamusic.client.ModClient;
import xerca.xercamusic.client.NoteSound;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.item.Items;
import m3e.api.client.M3EClientIntegration;
import m3e.debug.MusicDebugTelemetry;
import m3e.instrument.InstrumentRangeState;
import m3e.instrument.M3EInstruments;
import m3e.instrument.InstrumentVariations;
import m3e.network.LiveInstrumentNotePacket;
import m3e.network.LiveInstrumentNoteStatePacket;
import m3e.network.InstrumentExpressionStatePacket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Immediate local monitor plus event-owned remote playback on packet arrival. */
public final class LiveInstrumentNoteRelay {
    private static final AtomicLong NEXT_EVENT = new AtomicLong();
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable,
                        "Music Maker live-note scheduler");
                thread.setDaemon(true);
                return thread;
            });
    private static final Map<EventKey, ActiveVoice> ACTIVE =
            new ConcurrentHashMap<>();
    private static final Set<PacketKey> SEEN = ConcurrentHashMap.newKeySet();

    private LiveInstrumentNoteRelay() { }

    public static long noteOn(int instrumentId, int note, int velocity) {
        if (M3EClientIntegration.ensembleActive()
                || !ClientPlayNetworking.canSend(LiveInstrumentNotePacket.TYPE)) return 0L;
        long eventId = nextEvent();
        ClientPlayNetworking.send(new LiveInstrumentNotePacket(eventId,
                note, Math.clamp(velocity, 1, 127), false,
                instrumentId));
        return eventId;
    }

    public static void noteOff(long eventId, int instrumentId, int note) {
        if (eventId <= 0L
                || !ClientPlayNetworking.canSend(LiveInstrumentNotePacket.TYPE)) return;
        ClientPlayNetworking.send(new LiveInstrumentNotePacket(eventId,
                note, 0, true, instrumentId));
    }

    public static void receive(LiveInstrumentNoteStatePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.getId() == packet.playerId()) return;
        PacketKey packetKey = new PacketKey(packet.playerId(), packet.eventId(),
                packet.stop());
        if (!SEEN.add(packetKey)) return;
        perform(packet);
        MusicDebugTelemetry.sampled("live_note_receive_" + packet.playerId(),
                100_000_000L, "live_note", "received",
                "player", packet.playerId(), "event", packet.eventId(),
                "note", packet.note(), "off", packet.stop());
        if (SEEN.size() > 16_384) SEEN.clear();
    }

    public static void clear() {
        for (ActiveVoice voice : ACTIVE.values()) voice.stop();
        ACTIVE.clear();
        SEEN.clear();
    }

    /** Stops remote voices as soon as their performer leaves this client level. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        for (Map.Entry<EventKey, ActiveVoice> entry : ACTIVE.entrySet()) {
            if (minecraft.level.getEntity(entry.getKey().playerId()) == null
                    && ACTIVE.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().stop();
            }
        }
    }

    private static void perform(LiveInstrumentNoteStatePacket packet) {
        EventKey eventKey = new EventKey(packet.playerId(), packet.eventId());
        if (packet.stop()) {
            ActiveVoice voice = ACTIVE.remove(eventKey);
            if (voice != null) voice.stop();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || packet.instrumentId() < 0
                || packet.instrumentId() >= Items.INSTRUMENTS.size()) return;
        Entity entity = minecraft.level.getEntity(packet.playerId());
        if (!(entity instanceof Player player)) return;
        IItemInstrument instrument = Items.INSTRUMENTS.get(packet.instrumentId());
        IItemInstrument.InsSound source = InstrumentVariations.resolve(
                instrument, packet.note(), packet.variation());
        if (source == null) return;
        InstrumentExpressionStatePacket expression = new InstrumentExpressionStatePacket(
                packet.playerId(), packet.instrumentId(), packet.bendSemitones(),
                packet.modulation());
        InstrumentExpressionController.receive(expression);
        ActiveVoice old = ACTIVE.remove(eventKey);
        if (old != null) old.stop();
        ActiveVoice voice;
        if (M3EInstruments.isDexed(instrument)) {
            DexedAudioManager.remoteSongbookNoteOn(packet.playerId(),
                    player.position(), packet.eventId(), packet.note(), packet.velocity());
            DexedAudioManager.songbookVoiceExpression(packet.playerId(), packet.eventId(),
                    packet.bendSemitones(), packet.modulation());
            voice = new ActiveVoice(() -> DexedAudioManager.songbookNoteOff(
                    packet.playerId(), packet.eventId()));
        } else {
            NoteSound sound;
            RemoteEnvelopeContext.beginRemote(packet.playerId(), packet.instrumentId(),
                    packet.profile(), packet.variation());
            InstrumentRangeState.beginRemotePlayback();
            try {
                sound = ModClient.playNote(source.sound(), player.getX(), player.getY(),
                        player.getZ(), SoundSource.PLAYERS,
                        packet.velocity() / 127.0F * 1.5F, source.pitch(), (byte) -1);
            } finally {
                InstrumentRangeState.endRemotePlayback();
                RemoteEnvelopeContext.end();
            }
            if (sound == null) return;
            voice = new ActiveVoice(() -> {
                if (!sound.isStopped()) sound.stopSound();
            });
        }
        ACTIVE.put(eventKey, voice);
        voice.safetyStop = SCHEDULER.schedule(() -> minecraft.execute(() -> {
            if (ACTIVE.remove(eventKey, voice)) voice.stop();
        }), 120L, TimeUnit.SECONDS);
        minecraft.level.addParticle(ParticleTypes.NOTE,
                player.getX(), player.getY() + 2.2D, player.getZ(),
                packet.note() / 24.0D, 0.0D, 0.0D);
    }

    private static long nextEvent() {
        long next = NEXT_EVENT.incrementAndGet();
        if (next > 0L) return next;
        NEXT_EVENT.set(1L);
        return 1L;
    }

    private record EventKey(int playerId, long eventId) { }
    private record PacketKey(int playerId, long eventId, boolean stop) { }

    private static final class ActiveVoice {
        private final Runnable stopAction;
        private ScheduledFuture<?> safetyStop;

        private ActiveVoice(Runnable stopAction) { this.stopAction = stopAction; }

        private void stop() {
            if (safetyStop != null) safetyStop.cancel(false);
            stopAction.run();
        }
    }
}
