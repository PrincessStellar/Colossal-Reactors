package net.unfamily.colossal_reactors.builder.web;

/**
 * Packs/unpacks fixed-width bit fields (LSB first), matching ModDecoded {@code share.js}.
 */
public final class BitPack {

    private BitPack() {}

    /**
     * Packs {@code vals.length} values of {@code bits} bits each into {@code ceil(n*bits/8)} bytes.
     * Least significant bit of each value is written first.
     */
    public static byte[] packBits(int[] vals, int bits) {
        if (vals == null) {
            throw new IllegalArgumentException("vals");
        }
        if (bits < 1 || bits > 31) {
            throw new IllegalArgumentException("bits");
        }
        byte[] out = new byte[(vals.length * bits + 7) / 8];
        for (int i = 0; i < vals.length; i++) {
            int v = vals[i];
            for (int b = 0; b < bits; b++) {
                if (((v >> b) & 1) != 0) {
                    int p = i * bits + b;
                    out[p >> 3] |= (byte) (1 << (p & 7));
                }
            }
        }
        return out;
    }

    /**
     * Unpacks {@code n} values of {@code bits} bits each. Returns null if {@code bytes} length
     * is not exactly {@code ceil(n*bits/8)}.
     */
    public static int[] unpackBits(byte[] bytes, int n, int bits) {
        if (bytes == null || n < 0 || bits < 1 || bits > 31) {
            return null;
        }
        int expected = (n * bits + 7) / 8;
        if (bytes.length != expected) {
            return null;
        }
        int[] vals = new int[n];
        for (int i = 0; i < n; i++) {
            int v = 0;
            for (int b = 0; b < bits; b++) {
                int p = i * bits + b;
                if (((bytes[p >> 3] >> (p & 7)) & 1) != 0) {
                    v |= 1 << b;
                }
            }
            vals[i] = v;
        }
        return vals;
    }

    /** Bits needed to index a palette of size {@code n} (at least 1). */
    public static int paletteBits(int paletteSize) {
        if (paletteSize <= 1) {
            return 1;
        }
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
    }
}
