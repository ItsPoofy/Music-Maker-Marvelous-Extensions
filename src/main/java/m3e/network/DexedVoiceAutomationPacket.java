package m3e.network;

import m3e.M3E;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DexedVoiceAutomationPacket(int playerId, long eventId, int kind,
                                         float bendSemitones, int modulation,
                                         float gain, float[] waypoints,
                                         float[] positions, long durationMicros)
        implements CustomPacketPayload {
    public static final int EXPRESSION = 0;
    public static final int GAIN = 1;
    public static final int GLISSANDO = 2;
    public static final Type<DexedVoiceAutomationPacket> TYPE =
            new Type<>(M3E.id("dexed_voice_automation"));
    public static final StreamCodec<FriendlyByteBuf, DexedVoiceAutomationPacket> CODEC =
            StreamCodec.ofMember(DexedVoiceAutomationPacket::write,
                    DexedVoiceAutomationPacket::read);

    public DexedVoiceAutomationPacket {
        waypoints = waypoints == null ? new float[0] : waypoints.clone();
        if (positions == null || positions.length != waypoints.length) {
            positions = new float[waypoints.length];
            for (int index = 0; index < positions.length; index++) {
                positions[index] = (index + 1.0F) / positions.length;
            }
        } else {
            positions = positions.clone();
        }
    }

    private static DexedVoiceAutomationPacket read(FriendlyByteBuf buf) {
        int playerId = buf.readVarInt();
        long eventId = buf.readVarLong();
        int kind = buf.readUnsignedByte();
        float bend = buf.readFloat();
        int modulation = buf.readUnsignedByte();
        float gain = buf.readFloat();
        int count = buf.readUnsignedByte();
        if (count > 8) throw new IllegalArgumentException("too many glissando points");
        float[] waypoints = new float[count];
        float[] positions = new float[count];
        for (int index = 0; index < count; index++) {
            waypoints[index] = buf.readFloat();
            positions[index] = buf.readFloat();
        }
        long duration = buf.readVarLong();
        DexedVoiceAutomationPacket packet = new DexedVoiceAutomationPacket(playerId,
                eventId, kind, bend, modulation, gain, waypoints, positions, duration);
        if (playerId < 0 || eventId <= 0L || kind < EXPRESSION || kind > GLISSANDO
                || !Float.isFinite(bend) || bend < -96.0F || bend > 96.0F
                || modulation > 127 || !Float.isFinite(gain) || gain < 0.0F || gain > 4.0F
                || duration < 0L || duration > 86_400_000_000L) {
            throw new IllegalArgumentException("invalid Dexed automation");
        }
        for (int index = 0; index < count; index++) {
            if (!Float.isFinite(waypoints[index]) || waypoints[index] < -96.0F
                    || waypoints[index] > 96.0F || !Float.isFinite(positions[index])
                    || positions[index] < 0.0F || positions[index] > 1.0F) {
                throw new IllegalArgumentException("invalid Dexed glissando");
            }
        }
        return packet;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeVarLong(eventId);
        buf.writeByte(kind);
        buf.writeFloat(bendSemitones);
        buf.writeByte(modulation);
        buf.writeFloat(gain);
        buf.writeByte(waypoints.length);
        for (int index = 0; index < waypoints.length; index++) {
            buf.writeFloat(waypoints[index]);
            buf.writeFloat(positions[index]);
        }
        buf.writeVarLong(durationMicros);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
