package m3e.client;

import m3e.sound.InstrumentSoundProfile;
import m3e.instrument.InstrumentVariations;
import xerca.xercamusic.client.NoteSound;
import xerca.xercamusic.common.packets.clientbound.SingleNoteClientPacket;

import java.util.ArrayDeque;

/** Scoped context that configures a NoteSound before SoundManager receives it. */
public final class RemoteEnvelopeContext {
    private static final ThreadLocal<ArrayDeque<Frame>> CURRENT =
            new ThreadLocal<>();

    private RemoteEnvelopeContext() { }

    public static void begin(SingleNoteClientPacket packet) {
        int instrumentId = packet.instrumentItem().getInstrumentId();
        beginRemote(packet.playerId(), instrumentId,
                InstrumentProfileClientState.remoteProfile(packet.playerId(), instrumentId),
                InstrumentProfileClientState.remoteVariation(packet.playerId(), instrumentId));
    }

    public static void beginRemote(int playerId, int instrumentId,
                                   InstrumentSoundProfile profile) {
        beginRemote(playerId, instrumentId, profile,
                InstrumentProfileClientState.remoteVariation(playerId, instrumentId));
    }

    public static void beginRemote(int playerId, int instrumentId,
                                   InstrumentSoundProfile profile, String variation) {
        push(new Frame(profile == null ? InstrumentSoundProfile.DEFAULT : profile,
                InstrumentVariations.normalize(instrumentId, variation),
                playerId, instrumentId, false));
    }

    public static void begin(InstrumentSoundProfile profile) {
        push(new Frame(profile == null ? InstrumentSoundProfile.DEFAULT : profile,
                InstrumentVariations.DEFAULT_ID, -1, -1, true));
    }

    /** Remote or sequenced playback uses the profile without the live pitch wheel. */
    public static void beginProfileOnly(InstrumentSoundProfile profile) {
        push(new Frame(profile == null ? InstrumentSoundProfile.DEFAULT : profile,
                InstrumentVariations.DEFAULT_ID, -1, -1, false));
    }

    private static void push(Frame frame) {
        ArrayDeque<Frame> frames = CURRENT.get();
        if (frames == null) {
            frames = new ArrayDeque<>();
            CURRENT.set(frames);
        }
        frames.push(frame);
    }

    public static void end() {
        ArrayDeque<Frame> frames = CURRENT.get();
        if (frames == null) return;
        if (!frames.isEmpty()) frames.pop();
        if (frames.isEmpty()) CURRENT.remove();
    }

    public static void apply(NoteSound sound) {
        ArrayDeque<Frame> frames = CURRENT.get();
        Frame frame = frames == null ? null : frames.peek();
        if (frame == null) return;
        InstrumentOptions.apply(sound, frame.profile());
        if (frame.playerId() >= 0) {
            InstrumentExpressionController.registerRemote(
                    frame.playerId(), frame.instrumentId(), sound);
        } else if (frame.localExpression()) {
            InstrumentExpressionController.applyLocal(sound);
        }
    }

    static InstrumentSoundProfile currentProfile() {
        ArrayDeque<Frame> frames = CURRENT.get();
        Frame frame = frames == null ? null : frames.peek();
        return frame == null ? InstrumentSoundProfile.DEFAULT : frame.profile();
    }

    public static String currentVariation(int instrumentId) {
        ArrayDeque<Frame> frames = CURRENT.get();
        Frame frame = frames == null ? null : frames.peek();
        return frame == null ? InstrumentVariations.defaultId(instrumentId)
                : InstrumentVariations.normalize(instrumentId, frame.variation());
    }

    private record Frame(InstrumentSoundProfile profile, String variation,
                         int playerId, int instrumentId,
                         boolean localExpression) { }
}
