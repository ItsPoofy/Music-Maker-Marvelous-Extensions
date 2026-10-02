package m3e.client;

import m3e.network.InstrumentProfileStatePacket;
import m3e.network.InstrumentProfileUpdatePacket;
import m3e.instrument.InstrumentVariations;
import m3e.sound.InstrumentSoundProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.HashMap;
import java.util.Map;

/** Profiles published or received on the current connection, never persisted as preferences. */
public final class InstrumentProfileClientState {
    private InstrumentProfileClientState() { }
    private static final Map<Integer, InstrumentSoundProfile> PUBLISHED = new HashMap<>();
    private static final Map<Integer, InstrumentSoundProfile> PENDING = new HashMap<>();
    private static final Map<Integer, Long> LAST_ACTIVE_PUBLISH = new HashMap<>();
    private static final Map<Long, InstrumentSoundProfile> REMOTE = new HashMap<>();
    private static final Map<Integer, String> PUBLISHED_VARIATIONS = new HashMap<>();
    private static final Map<Long, String> REMOTE_VARIATIONS = new HashMap<>();
    private static long lastPublishNanos;

    static synchronized void queue(int instrumentId, InstrumentSoundProfile profile) {
        PENDING.put(instrumentId, profile);
    }

    public static synchronized void tick() {
        long now = System.nanoTime();
        if (!PENDING.isEmpty() && now - lastPublishNanos >= 50_000_000L) {
            var next = PENDING.entrySet().iterator().next();
            PENDING.remove(next.getKey());
            publish(next.getKey(), next.getValue());
            lastPublishNanos = now;
        }
    }
    public static synchronized void publish(int instrumentId) {
        publish(instrumentId, InstrumentOptions.playerDefaultProfile(instrumentId));
    }

    public static synchronized void activate(int instrumentId) {
        InstrumentSoundProfile profile = InstrumentOptions.playerDefaultProfile(instrumentId);
        String variation = InstrumentOptions.playerDefaultVariation(instrumentId);
        long now = System.nanoTime();
        if (!profile.equals(PUBLISHED.get(instrumentId))
                || !variation.equals(PUBLISHED_VARIATIONS.get(instrumentId))
                || now - LAST_ACTIVE_PUBLISH.getOrDefault(instrumentId, 0L) >= 1_000_000_000L) {
            PENDING.remove(instrumentId);
            publish(instrumentId, profile);
            LAST_ACTIVE_PUBLISH.put(instrumentId, now);
        }
    }

    private static void publish(int instrumentId, InstrumentSoundProfile profile) {
        publish(instrumentId, profile, InstrumentOptions.playerDefaultVariation(instrumentId));
    }

    private static void publish(int instrumentId, InstrumentSoundProfile profile, String variation) {
        if (ClientPlayNetworking.canSend(InstrumentProfileUpdatePacket.TYPE)) {
            ClientPlayNetworking.send(new InstrumentProfileUpdatePacket(
                    instrumentId, profile, variation));
            PUBLISHED.put(instrumentId, profile);
            PUBLISHED_VARIATIONS.put(instrumentId, variation);
        }
    }

    public static synchronized void activateExact(int instrumentId,
                                                  InstrumentSoundProfile profile,
                                                  String variation) {
        InstrumentSoundProfile safeProfile = profile == null
                ? InstrumentSoundProfile.DEFAULT : profile;
        String safeVariation = InstrumentVariations.normalize(instrumentId, variation);
        PENDING.remove(instrumentId);
        publish(instrumentId, safeProfile, safeVariation);
        LAST_ACTIVE_PUBLISH.put(instrumentId, System.nanoTime());
    }

    public static synchronized void receive(InstrumentProfileStatePacket packet) {
        REMOTE.put(key(packet.playerId(), packet.instrumentId()), packet.profile());
        REMOTE_VARIATIONS.put(key(packet.playerId(), packet.instrumentId()),
                InstrumentVariations.normalize(packet.instrumentId(), packet.variation()));
    }

    public static synchronized void clearRemote() {
        REMOTE.clear();
        PUBLISHED.clear();
        PUBLISHED_VARIATIONS.clear();
        PENDING.clear();
        REMOTE_VARIATIONS.clear();
        LAST_ACTIVE_PUBLISH.clear();
    }

    public static synchronized InstrumentSoundProfile remoteProfile(int playerId, int instrumentId) {
        return REMOTE.getOrDefault(key(playerId, instrumentId), InstrumentSoundProfile.DEFAULT);
    }

    public static synchronized String remoteVariation(int playerId, int instrumentId) {
        return InstrumentVariations.normalize(instrumentId,
                REMOTE_VARIATIONS.get(key(playerId, instrumentId)));
    }

    private static long key(int playerId, int instrumentId) {
        return ((long) playerId << 32) ^ (instrumentId & 0xFFFFFFFFL);
    }

}
