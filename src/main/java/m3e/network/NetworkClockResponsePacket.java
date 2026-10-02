package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

public record NetworkClockResponsePacket(long clientSentNanos,
                                         long serverReceivedNanos,
                                         long serverSentNanos)
        implements CustomPacketPayload {
    public static final Type<NetworkClockResponsePacket> TYPE =
            new Type<>(M3E.id("network_clock_response"));
    public static final StreamCodec<FriendlyByteBuf, NetworkClockResponsePacket> CODEC =
            StreamCodec.ofMember(NetworkClockResponsePacket::write,
                    NetworkClockResponsePacket::read);

    private static NetworkClockResponsePacket read(FriendlyByteBuf buf) {
        long clientSent = buf.readLong();
        long serverReceived = buf.readLong();
        long serverSent = buf.readLong();
        if (clientSent <= 0L || serverReceived <= 0L
                || serverSent < serverReceived) {
            throw new IllegalArgumentException("invalid network clock response");
        }
        return new NetworkClockResponsePacket(clientSent, serverReceived, serverSent);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeLong(clientSentNanos);
        buf.writeLong(serverReceivedNanos);
        buf.writeLong(serverSentNanos);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
