package m3e.network;

import m3e.sound.InstrumentSoundProfile;
import m3e.M3E;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record InstrumentProfileStatePacket(int playerId, int instrumentId,
                                           InstrumentSoundProfile profile, String variation)
        implements CustomPacketPayload {
    public static final Type<InstrumentProfileStatePacket> TYPE =
            new Type<>(M3E.id("instrument_profile_state"));
    public static final StreamCodec<FriendlyByteBuf, InstrumentProfileStatePacket> CODEC =
            StreamCodec.ofMember(InstrumentProfileStatePacket::write, InstrumentProfileStatePacket::read);

    private static InstrumentProfileStatePacket read(FriendlyByteBuf buf) {
        return new InstrumentProfileStatePacket(buf.readVarInt(), buf.readVarInt(),
                InstrumentProfileUpdatePacket.readProfile(buf), buf.readUtf(16));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeVarInt(instrumentId);
        InstrumentProfileUpdatePacket.writeProfile(buf, profile);
        buf.writeUtf(variation, 16);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
