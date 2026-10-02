package m3e.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import m3e.M3E;

public record NetworkClockRequestPacket(long clientSentNanos,
                                        long measuredRttNanos,
                                        long measuredJitterNanos)
        implements CustomPacketPayload {
    public static final Type<NetworkClockRequestPacket> TYPE =
            new Type<>(M3E.id("network_clock_request"));
    public static final StreamCodec<FriendlyByteBuf, NetworkClockRequestPacket> CODEC =
            StreamCodec.ofMember(NetworkClockRequestPacket::write,
                    NetworkClockRequestPacket::read);

    private static NetworkClockRequestPacket read(FriendlyByteBuf buf) {
        long sent = buf.readLong();
        long rtt = buf.readLong();
        long jitter = buf.readLong();
        if (sent <= 0L || rtt < 0L || rtt > 5_000_000_000L
                || jitter < 0L || jitter > 2_000_000_000L) {
            throw new IllegalArgumentException("invalid network clock sample");
        }
        return new NetworkClockRequestPacket(sent, rtt, jitter);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeLong(clientSentNanos);
        buf.writeLong(measuredRttNanos);
        buf.writeLong(measuredJitterNanos);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
