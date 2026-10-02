package m3e.network;

import m3e.M3E;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record InstrumentExpressionUpdatePacket(int instrumentId, float bendSemitones, int modulation)
        implements CustomPacketPayload {
    public static final Type<InstrumentExpressionUpdatePacket> TYPE =
            new Type<>(M3E.id("instrument_expression_update"));
    public static final StreamCodec<FriendlyByteBuf, InstrumentExpressionUpdatePacket> CODEC =
            StreamCodec.ofMember(InstrumentExpressionUpdatePacket::write, InstrumentExpressionUpdatePacket::read);

    private static InstrumentExpressionUpdatePacket read(FriendlyByteBuf buf) {
        int instrument = buf.readVarInt();
        float bend = buf.readFloat();
        int modulation = buf.readUnsignedByte();
        if (!Float.isFinite(bend) || bend < -96.0F || bend > 96.0F || modulation > 127) {
            throw new IllegalArgumentException("invalid MIDI expression");
        }
        return new InstrumentExpressionUpdatePacket(instrument, bend, modulation);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(instrumentId);
        buf.writeFloat(bendSemitones);
        buf.writeByte(modulation);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
