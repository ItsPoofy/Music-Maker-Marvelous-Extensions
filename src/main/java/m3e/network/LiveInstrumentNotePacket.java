package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

/** Event-owned live note captured beside the performer's immediate monitor. */
public record LiveInstrumentNotePacket(long eventId, int note, int velocity, boolean stop,
                                       int instrumentId)
        implements CustomPacketPayload {
    public static final Type<LiveInstrumentNotePacket> TYPE =
            new Type<>(M3E.id("live_instrument_note"));
    public static final StreamCodec<FriendlyByteBuf, LiveInstrumentNotePacket> CODEC =
            StreamCodec.ofMember(LiveInstrumentNotePacket::write,
                    LiveInstrumentNotePacket::read);

    private static LiveInstrumentNotePacket read(FriendlyByteBuf buf) {
        LiveInstrumentNotePacket packet = new LiveInstrumentNotePacket(
                buf.readVarLong(), buf.readUnsignedByte(),
                buf.readUnsignedByte(), buf.readBoolean(), buf.readVarInt());
        if (packet.eventId <= 0L || packet.note > 127 || packet.velocity > 127
                || packet.instrumentId < 0) {
            throw new IllegalArgumentException("invalid live instrument note");
        }
        return packet;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarLong(eventId);
        buf.writeByte(note);
        buf.writeByte(velocity);
        buf.writeBoolean(stop);
        buf.writeVarInt(instrumentId);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
