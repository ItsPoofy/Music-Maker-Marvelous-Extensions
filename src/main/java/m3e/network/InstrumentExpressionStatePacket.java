package m3e.network;

import m3e.M3E;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record InstrumentExpressionStatePacket(int playerId, int instrumentId, float bendSemitones,
                                               int modulation) implements CustomPacketPayload {
    public static final Type<InstrumentExpressionStatePacket> TYPE =
            new Type<>(M3E.id("instrument_expression_state"));
    public static final StreamCodec<FriendlyByteBuf, InstrumentExpressionStatePacket> CODEC =
            StreamCodec.ofMember(InstrumentExpressionStatePacket::write, InstrumentExpressionStatePacket::read);

    private static InstrumentExpressionStatePacket read(FriendlyByteBuf buf) {
        int player = buf.readVarInt();
        int instrument = buf.readVarInt();
        float bend = buf.readFloat();
        int modulation = buf.readUnsignedByte();
        if (player < 0 || !Float.isFinite(bend) || bend < -96.0F || bend > 96.0F
                || modulation > 127) {
            throw new IllegalArgumentException("invalid remote MIDI expression");
        }
        return new InstrumentExpressionStatePacket(player, instrument, bend, modulation);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeVarInt(instrumentId);
        buf.writeFloat(bendSemitones);
        buf.writeByte(modulation);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
