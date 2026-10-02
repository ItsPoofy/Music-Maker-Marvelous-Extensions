package m3e.dexed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exact Yamaha DX7 single-voice and 32-voice cartridge import/export. */
public final class DexedSysex {
    public static final int CARTRIDGE_SIZE = 4104;
    public static final int SINGLE_VOICE_SIZE = 163;

    private DexedSysex() { }

    public static List<DexedPatch> importVoices(byte[] input) {
        if (input.length == CARTRIDGE_SIZE && cartridgeHeader(input)) return unpackCartridge(input);
        if (input.length == SINGLE_VOICE_SIZE && singleHeader(input)) return List.of(unpackSingle(input));
        throw new IllegalArgumentException("Expected a Yamaha DX7 4104-byte bank or 163-byte voice SysEx file");
    }

    public static byte[] exportSingle(DexedPatch patch) {
        byte[] result = new byte[SINGLE_VOICE_SIZE];
        byte[] header = {(byte) 0xf0, 0x43, 0, 0, 1, 0x1b};
        System.arraycopy(header, 0, result, 0, header.length);
        byte[] data = patch.bytes();
        System.arraycopy(data, 0, result, 6, 155);
        result[161] = checksum(data, 0, 155);
        result[162] = (byte) 0xf7;
        return result;
    }

    public static byte[] exportCartridge(List<DexedPatch> voices) {
        if (voices.size() != 32) throw new IllegalArgumentException("A DX7 cartridge contains exactly 32 voices");
        byte[] result = new byte[CARTRIDGE_SIZE];
        byte[] header = {(byte) 0xf0, 0x43, 0, 0x09, 0x20, 0};
        System.arraycopy(header, 0, result, 0, header.length);
        for (int i = 0; i < 32; i++) pack(voices.get(i), result, 6 + i * 128);
        result[4102] = checksum(result, 6, 4096);
        result[4103] = (byte) 0xf7;
        return result;
    }

    /** Replaces one voice while preserving every untouched byte in an imported cartridge. */
    public static byte[] replaceVoice(byte[] cartridge, int index, DexedPatch patch) {
        if (cartridge.length != CARTRIDGE_SIZE || !cartridgeHeader(cartridge))
            throw new IllegalArgumentException("Expected a Yamaha DX7 cartridge");
        if (index < 0 || index >= 32) throw new IllegalArgumentException("Voice index must be 0..31");
        requireChecksum(cartridge, 6, 4096, cartridge[4102]);
        byte[] result = cartridge.clone();
        pack(patch, result, 6 + index * 128);
        result[4102] = checksum(result, 6, 4096);
        return result;
    }

    private static List<DexedPatch> unpackCartridge(byte[] input) {
        requireChecksum(input, 6, 4096, input[4102]);
        List<DexedPatch> result = new ArrayList<>(32);
        for (int voice = 0; voice < 32; voice++) result.add(unpack(input, 6 + voice * 128));
        return List.copyOf(result);
    }

    private static DexedPatch unpackSingle(byte[] input) {
        requireChecksum(input, 6, 155, input[161]);
        byte[] data = Arrays.copyOfRange(input, 6, 162);
        data[155] = 0x3f;
        return new DexedPatch(data);
    }

    private static DexedPatch unpack(byte[] bulk, int start) {
        byte[] patch = new byte[DexedPatch.SIZE];
        for (int op = 0; op < 6; op++) {
            int packed = start + op * 17;
            int expanded = op * 21;
            for (int i = 0; i < 11; i++) patch[expanded + i] = (byte) (bulk[packed + i] & 0x7f);
            int curves = bulk[packed + 11] & 0x0f;
            patch[expanded + 11] = (byte) (curves & 3);
            patch[expanded + 12] = (byte) ((curves >> 2) & 3);
            int detuneRate = bulk[packed + 12] & 0x7f;
            patch[expanded + 13] = (byte) (detuneRate & 7);
            patch[expanded + 20] = (byte) ((detuneRate >> 3) & 15);
            int velocityAmp = bulk[packed + 13] & 0x1f;
            patch[expanded + 14] = (byte) (velocityAmp & 3);
            patch[expanded + 15] = (byte) ((velocityAmp >> 2) & 7);
            patch[expanded + 16] = (byte) (bulk[packed + 14] & 0x7f);
            int coarseMode = bulk[packed + 15] & 0x3f;
            patch[expanded + 17] = (byte) (coarseMode & 1);
            patch[expanded + 18] = (byte) ((coarseMode >> 1) & 31);
            patch[expanded + 19] = (byte) (bulk[packed + 16] & 0x7f);
        }
        System.arraycopy(bulk, start + 102, patch, 126, 8);
        patch[134] = (byte) (bulk[start + 110] & 31);
        int feedbackSync = bulk[start + 111] & 15;
        patch[135] = (byte) (feedbackSync & 7);
        patch[136] = (byte) ((feedbackSync >> 3) & 1);
        System.arraycopy(bulk, start + 112, patch, 137, 4);
        int lfo = bulk[start + 116] & 0x7f;
        patch[141] = (byte) (lfo & 1);
        patch[142] = (byte) ((lfo >> 1) & 7);
        patch[143] = (byte) ((lfo >> 4) & 7);
        patch[144] = (byte) (bulk[start + 117] & 0x7f);
        System.arraycopy(bulk, start + 118, patch, 145, 10);
        patch[155] = 0x3f;
        return new DexedPatch(patch);
    }

    private static void pack(DexedPatch voice, byte[] bulk, int start) {
        byte[] patch = voice.bytes();
        for (int op = 0; op < 6; op++) {
            int expanded = op * 21;
            int packed = start + op * 17;
            System.arraycopy(patch, expanded, bulk, packed, 11);
            bulk[packed + 11] = (byte) ((patch[expanded + 11] & 3) | ((patch[expanded + 12] & 3) << 2));
            bulk[packed + 12] = (byte) ((patch[expanded + 13] & 7) | ((patch[expanded + 20] & 15) << 3));
            bulk[packed + 13] = (byte) ((patch[expanded + 14] & 3) | ((patch[expanded + 15] & 7) << 2));
            // Operator audition/mute is a Dexed editor extension and has no
            // field in Yamaha's 128-byte packed voice. Keep the real output
            // level intact; local banks persist the six-bit mask separately.
            bulk[packed + 14] = patch[expanded + 16];
            bulk[packed + 15] = (byte) ((patch[expanded + 17] & 1) | ((patch[expanded + 18] & 31) << 1));
            bulk[packed + 16] = patch[expanded + 19];
        }
        System.arraycopy(patch, 126, bulk, start + 102, 9);
        bulk[start + 111] = (byte) ((patch[135] & 7) | ((patch[136] & 1) << 3));
        System.arraycopy(patch, 137, bulk, start + 112, 4);
        bulk[start + 116] = (byte) ((patch[141] & 1) | ((patch[142] & 7) << 1) | ((patch[143] & 7) << 4));
        bulk[start + 117] = patch[144];
        System.arraycopy(patch, 145, bulk, start + 118, 10);
    }

    private static boolean cartridgeHeader(byte[] b) {
        return (b[0] & 0xff) == 0xf0 && (b[1] & 0xff) == 0x43 && b[3] == 0x09
                && b[4] == 0x20 && b[5] == 0 && (b[4103] & 0xff) == 0xf7;
    }
    private static boolean singleHeader(byte[] b) {
        return (b[0] & 0xff) == 0xf0 && (b[1] & 0xff) == 0x43 && b[4] == 1
                && b[5] == 0x1b && (b[162] & 0xff) == 0xf7;
    }
    private static void requireChecksum(byte[] data, int offset, int size, byte expected) {
        if (checksum(data, offset, size) != expected)
            throw new IllegalArgumentException("DX7 SysEx checksum mismatch");
    }
    private static byte checksum(byte[] data, int offset, int size) {
        int sum = 0;
        for (int i = 0; i < size; i++) sum -= data[offset + i] & 0xff;
        return (byte) (sum & 0x7f);
    }
}
