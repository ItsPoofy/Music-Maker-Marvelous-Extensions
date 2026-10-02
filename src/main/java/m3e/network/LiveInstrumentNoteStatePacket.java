package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;
import m3e.sound.InstrumentSoundProfile;

/** Server-authorized live note played by remote clients on arrival. */
public record LiveInstrumentNoteStatePacket(int playerId, long eventId, int note,
                                             int velocity, boolean stop,
                                             int instrumentId,
                                             InstrumentSoundProfile profile,
                                             String variation,
                                             float bendSemitones, int modulation)
        implements CustomPacketPayload {
    public static final Type<LiveInstrumentNoteStatePacket> TYPE =
            new Type<>(M3E.id("live_instrument_note_state"));
    public static final StreamCodec<FriendlyByteBuf, LiveInstrumentNoteStatePacket> CODEC =
            StreamCodec.ofMember(LiveInstrumentNoteStatePacket::write,
                    LiveInstrumentNoteStatePacket::read);

    private static LiveInstrumentNoteStatePacket read(FriendlyByteBuf buf) {
        LiveInstrumentNoteStatePacket packet = new LiveInstrumentNoteStatePacket(
                buf.readVarInt(), buf.readVarLong(), buf.readUnsignedByte(),
                buf.readUnsignedByte(), buf.readBoolean(),
                buf.readVarInt(), InstrumentProfileUpdatePacket.readProfile(buf),
                buf.readUtf(16), buf.readFloat(), buf.readUnsignedByte());
        if (packet.playerId < 0 || packet.eventId <= 0L
                || packet.note > 127 || packet.velocity > 127
                || packet.instrumentId < 0 || packet.profile == null
                || packet.variation == null
                || !Float.isFinite(packet.bendSemitones)
                || packet.bendSemitones < -96.0F || packet.bendSemitones > 96.0F
                || packet.modulation > 127) {
            throw new IllegalArgumentException("invalid live instrument note");
        }
        return packet;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeVarLong(eventId);
        buf.writeByte(note);
        buf.writeByte(velocity);
        buf.writeBoolean(stop);
        buf.writeVarInt(instrumentId);
        InstrumentProfileUpdatePacket.writeProfile(buf, profile);
        buf.writeUtf(variation, 16);
        buf.writeFloat(bendSemitones);
        buf.writeByte(modulation);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
