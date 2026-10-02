package m3e.network;

import m3e.sound.InstrumentSoundProfile;
import m3e.M3E;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record InstrumentProfileUpdatePacket(int instrumentId, InstrumentSoundProfile profile,
                                            String variation)
        implements CustomPacketPayload {
    public static final Type<InstrumentProfileUpdatePacket> TYPE =
            new Type<>(M3E.id("instrument_profile_update"));
    public static final StreamCodec<FriendlyByteBuf, InstrumentProfileUpdatePacket> CODEC =
            StreamCodec.ofMember(InstrumentProfileUpdatePacket::write, InstrumentProfileUpdatePacket::read);

    private static InstrumentProfileUpdatePacket read(FriendlyByteBuf buf) {
        return new InstrumentProfileUpdatePacket(buf.readVarInt(), readProfile(buf), buf.readUtf(16));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(instrumentId);
        writeProfile(buf, profile);
        buf.writeUtf(variation, 16);
    }

    public static InstrumentSoundProfile readProfile(FriendlyByteBuf buf) {
        return InstrumentSoundProfile.STREAM_CODEC.decode(buf);
    }

    public static void writeProfile(FriendlyByteBuf buf, InstrumentSoundProfile profile) {
        InstrumentSoundProfile.STREAM_CODEC.encode(buf, profile);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
