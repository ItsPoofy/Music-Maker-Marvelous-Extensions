package m3e.dexed;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Persisted performance controls that are independent of the selected DX7 voice. */
public record DexedPerformance(int coarseSemitones, int fineCents, boolean mono,
                               boolean portamentoEnabled, MonoPortamento monoPortamento,
                               PolyPortamento polyPortamento, int portamentoTime,
                               boolean glissando) {
    public static final int MIN_COARSE = -24;
    public static final int MAX_COARSE = 24;
    public static final int MIN_FINE = -100;
    public static final int MAX_FINE = 100;
    public static final int MIN_PORTAMENTO_TIME = 0;
    public static final int MAX_PORTAMENTO_TIME = 99;
    public static final DexedPerformance DEFAULT = new DexedPerformance(
            0, 0, false, false, MonoPortamento.LEGATO,
            PolyPortamento.RETAIN, 50, false);
    public static final Codec<DexedPerformance> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("coarse_semitones", 0)
                            .forGetter(DexedPerformance::coarseSemitones),
                    Codec.INT.optionalFieldOf("fine_cents", 0)
                            .forGetter(DexedPerformance::fineCents),
                    Codec.BOOL.optionalFieldOf("mono", false)
                            .forGetter(DexedPerformance::mono),
                    Codec.BOOL.optionalFieldOf("portamento_enabled", false)
                            .forGetter(DexedPerformance::portamentoEnabled),
                    MonoPortamento.CODEC.optionalFieldOf("mono_portamento",
                                    MonoPortamento.LEGATO)
                            .forGetter(DexedPerformance::monoPortamento),
                    PolyPortamento.CODEC.optionalFieldOf("poly_portamento",
                                    PolyPortamento.RETAIN)
                            .forGetter(DexedPerformance::polyPortamento),
                    Codec.INT.optionalFieldOf("portamento_time", 50)
                            .forGetter(DexedPerformance::portamentoTime),
                    Codec.BOOL.optionalFieldOf("glissando", false)
                            .forGetter(DexedPerformance::glissando)
            ).apply(instance, DexedPerformance::new));
    public static final StreamCodec<FriendlyByteBuf, DexedPerformance> STREAM_CODEC =
            StreamCodec.ofMember(DexedPerformance::write, DexedPerformance::read);

    public DexedPerformance {
        coarseSemitones = Math.clamp(coarseSemitones, MIN_COARSE, MAX_COARSE);
        fineCents = Math.clamp(fineCents, MIN_FINE, MAX_FINE);
        monoPortamento = monoPortamento == null ? MonoPortamento.LEGATO : monoPortamento;
        polyPortamento = polyPortamento == null ? PolyPortamento.RETAIN : polyPortamento;
        portamentoTime = Math.clamp(portamentoTime,
                MIN_PORTAMENTO_TIME, MAX_PORTAMENTO_TIME);
    }

    /** Compatibility constructor for items saved before portamento was exposed. */
    public DexedPerformance(int coarseSemitones, int fineCents, boolean mono) {
        this(coarseSemitones, fineCents, mono, false, MonoPortamento.LEGATO,
                PolyPortamento.RETAIN, 50, false);
    }

    private static DexedPerformance read(FriendlyByteBuf buffer) {
        return new DexedPerformance(buffer.readByte(), buffer.readByte(),
                buffer.readBoolean(), buffer.readBoolean(),
                MonoPortamento.byId(buffer.readUnsignedByte()),
                PolyPortamento.byId(buffer.readUnsignedByte()),
                buffer.readUnsignedByte(), buffer.readBoolean());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeByte(coarseSemitones);
        buffer.writeByte(fineCents);
        buffer.writeBoolean(mono);
        buffer.writeBoolean(portamentoEnabled);
        buffer.writeByte(monoPortamento.ordinal());
        buffer.writeByte(polyPortamento.ordinal());
        buffer.writeByte(portamentoTime);
        buffer.writeBoolean(glissando);
    }

    public DexedPerformance withCoarse(int value) {
        return copy(value, fineCents, mono, portamentoEnabled, monoPortamento,
                polyPortamento, portamentoTime, glissando);
    }

    public DexedPerformance withFine(int value) {
        return copy(coarseSemitones, value, mono, portamentoEnabled, monoPortamento,
                polyPortamento, portamentoTime, glissando);
    }

    public DexedPerformance withMono(boolean value) {
        return copy(coarseSemitones, fineCents, value, portamentoEnabled,
                monoPortamento, polyPortamento, portamentoTime, glissando);
    }

    public DexedPerformance withPortamentoEnabled(boolean value) {
        return copy(coarseSemitones, fineCents, mono, value, monoPortamento,
                polyPortamento, portamentoTime, glissando);
    }

    public DexedPerformance withMonoPortamento(MonoPortamento value) {
        return copy(coarseSemitones, fineCents, mono, portamentoEnabled, value,
                polyPortamento, portamentoTime, glissando);
    }

    public DexedPerformance withPolyPortamento(PolyPortamento value) {
        return copy(coarseSemitones, fineCents, mono, portamentoEnabled,
                monoPortamento, value, portamentoTime, glissando);
    }

    public DexedPerformance withPortamentoTime(int value) {
        return copy(coarseSemitones, fineCents, mono, portamentoEnabled,
                monoPortamento, polyPortamento, value, glissando);
    }

    public DexedPerformance withGlissando(boolean value) {
        return copy(coarseSemitones, fineCents, mono, portamentoEnabled,
                monoPortamento, polyPortamento, portamentoTime, value);
    }

    private static DexedPerformance copy(int coarse, int fine, boolean mono,
                                         boolean enabled, MonoPortamento monoMode,
                                         PolyPortamento polyMode, int time,
                                         boolean glissando) {
        return new DexedPerformance(coarse, fine, mono, enabled, monoMode,
                polyMode, time, glissando);
    }

    public enum MonoPortamento {
        LEGATO, ALWAYS;

        private static final Codec<MonoPortamento> CODEC = Codec.STRING.xmap(
                value -> "always".equalsIgnoreCase(value) ? ALWAYS : LEGATO,
                value -> value.name().toLowerCase(java.util.Locale.ROOT));

        static MonoPortamento byId(int id) {
            return id == ALWAYS.ordinal() ? ALWAYS : LEGATO;
        }
    }

    public enum PolyPortamento {
        RETAIN, FOLLOW;

        private static final Codec<PolyPortamento> CODEC = Codec.STRING.xmap(
                value -> "follow".equalsIgnoreCase(value) ? FOLLOW : RETAIN,
                value -> value.name().toLowerCase(java.util.Locale.ROOT));

        static PolyPortamento byId(int id) {
            return id == FOLLOW.ordinal() ? FOLLOW : RETAIN;
        }
    }
}
