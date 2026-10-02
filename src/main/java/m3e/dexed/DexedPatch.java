package m3e.dexed;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/** Immutable, bounded representation of one editable DX7 voice. */
public final class DexedPatch {
    public static final int SIZE = 156;
    public static final Codec<DexedPatch> CODEC = Codec.STRING.comapFlatMap(
            DexedPatch::decodePersistent, patch -> Base64.getEncoder().encodeToString(patch.data));
    public static final StreamCodec<RegistryFriendlyByteBuf, DexedPatch> STREAM_CODEC =
            ByteBufCodecs.byteArray(SIZE).map(DexedPatch::new, DexedPatch::bytes).cast();

    private final byte[] data;

    public DexedPatch(byte[] source) {
        if (source.length != SIZE) throw new IllegalArgumentException("DX7 patch must contain 156 bytes");
        data = source.clone();
        normalize(data);
    }

    public static DexedPatch initVoice() {
        byte[] result = new byte[SIZE];
        int[] operator = {99,99,99,99,99,99,99,0,0,0,0,0,0,0,0,0,0,0,1,0,7};
        for (int op = 0; op < 6; op++) for (int i = 0; i < 21; i++) result[op * 21 + i] = (byte) operator[i];
        result[5 * 21 + 16] = 99;
        int[] global = {99,99,99,99,50,50,50,50,0,0,1,35,0,0,0,1,0,3,24};
        for (int i = 0; i < global.length; i++) result[126 + i] = (byte) global[i];
        byte[] name = "INIT VOICE".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, result, 145, name.length);
        result[155] = 0x3f;
        return new DexedPatch(result);
    }

    public byte[] bytes() { return data.clone(); }
    public int value(int index) { return data[index] & 0xFF; }
    public DexedPatch with(int index, int value) {
        byte[] copy = data.clone();
        copy[index] = (byte) value;
        return new DexedPatch(copy);
    }
    public DexedPatch withName(String name) {
        byte[] copy = data.clone();
        Arrays.fill(copy, 145, 155, (byte) ' ');
        byte[] ascii = name.toUpperCase().getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < Math.min(10, ascii.length); i++) copy[145 + i] = ascii[i];
        return new DexedPatch(copy);
    }
    public String name() {
        return new String(data, 145, 10, StandardCharsets.US_ASCII).stripTrailing();
    }

    private static DataResult<DexedPatch> decodePersistent(String encoded) {
        try { return DataResult.success(new DexedPatch(Base64.getDecoder().decode(encoded))); }
        catch (RuntimeException exception) { return DataResult.error(exception::getMessage); }
    }

    private static void normalize(byte[] values) {
        for (int op = 0; op < 6; op++) {
            int offset = op * 21;
            for (int i = 0; i <= 10; i++) values[offset + i] = clamp(values[offset + i], 99);
            values[offset + 11] = clamp(values[offset + 11], 3);
            values[offset + 12] = clamp(values[offset + 12], 3);
            values[offset + 13] = clamp(values[offset + 13], 7);
            values[offset + 14] = clamp(values[offset + 14], 3);
            values[offset + 15] = clamp(values[offset + 15], 7);
            values[offset + 16] = clamp(values[offset + 16], 99);
            values[offset + 17] = clamp(values[offset + 17], 1);
            values[offset + 18] = clamp(values[offset + 18], 31);
            values[offset + 19] = clamp(values[offset + 19], 99);
            values[offset + 20] = clamp(values[offset + 20], 14);
        }
        for (int i = 126; i <= 133; i++) values[i] = clamp(values[i], 99);
        values[134] = clamp(values[134], 31);
        values[135] = clamp(values[135], 7);
        values[136] = clamp(values[136], 1);
        for (int i = 137; i <= 140; i++) values[i] = clamp(values[i], 99);
        values[141] = clamp(values[141], 1);
        values[142] = clamp(values[142], 5);
        values[143] = clamp(values[143], 7);
        values[144] = clamp(values[144], 48);
        for (int i = 145; i < 155; i++) {
            int c = values[i] & 0x7f;
            values[i] = (byte) (c < 32 || c > 126 ? ' ' : c);
        }
        values[155] = (byte) (values[155] & 0x3f);
    }

    private static byte clamp(byte value, int maximum) {
        return (byte) Math.clamp(value & 0xFF, 0, maximum);
    }

    @Override public boolean equals(Object other) {
        return other instanceof DexedPatch patch && Arrays.equals(data, patch.data);
    }
    @Override public int hashCode() { return Arrays.hashCode(data); }
}
