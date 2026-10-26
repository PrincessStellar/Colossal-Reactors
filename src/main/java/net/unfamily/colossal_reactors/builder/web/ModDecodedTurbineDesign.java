package net.unfamily.colossal_reactors.builder.web;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decoded ModDecoded turbine design seed ({@code mdct1} / {@code mdct2}).
 * <pre>
 * mdct1.X.Z.Y.{f|c}.ring.coil.coilLayers.{a|o}.steam.rodsB64
 * mdct2.X.Z.Y.{f|c}.ring.coil.rodCoil.coilLayers.{a|o}.steam.rodsB64
 * </pre>
 * Profile {@code f}=full (same blade rings every layer), {@code c}=climb.
 * {@code rodCoil} (mdct2 only): coil material on rod-controller columns; omitted / same as {@code coil} → mdct1.
 * Coil fill {@code o} (one block) is decoded only; see {@link #coilFillOne}.
 * Steam is mB/t or {@code -} for uncapped.
 * Rods: 1 bit per interior cell {@code x*(Z-2)+z}, LSB first, base64url.
 */
public record ModDecodedTurbineDesign(
        int exteriorX,
        int exteriorZ,
        int exteriorY,
        boolean climbProfile,
        int ring,
        String coilId,
        /**
         * Rod-column coil website id when distinct from {@link #coilId()}; {@code null} means same as zone coil
         * (mdct1, or mdct2 with rodCoil equal to coil).
         */
        @Nullable String rodCoilId,
        int coilLayers,
        /**
         * Seed fill {@code o} (one coil block). The Turbine Builder always fills the whole coil zone
         * ({@code a}), matching ModDecoded's builder import path — this flag is not applied to build/sim.
         */
        boolean coilFillOne,
        @Nullable Long steamMbPerTick,
        boolean[] rodMask,
        String code
) {
    private static final Pattern CODE_IN_TEXT = Pattern.compile("mdct[12]\\.[^&\\s#]+");
    private static final int MIN_XZ = 3;
    private static final int MAX_EXT = 65;
    private static final int MIN_Y = 5;

    public ModDecodedTurbineDesign {
        coilId = coilId != null ? coilId : "air";
        if (rodCoilId != null && (rodCoilId.isEmpty() || rodCoilId.equals(coilId))) {
            rodCoilId = null;
        }
        rodMask = rodMask != null ? Arrays.copyOf(rodMask, rodMask.length) : new boolean[0];
        code = code != null ? code : "";
    }

    /** Effective rod-column coil id (falls back to zone {@link #coilId()}). */
    public String effectiveRodCoilId() {
        return rodCoilId != null ? rodCoilId : coilId;
    }

    public int interiorWidth() {
        return Math.max(0, exteriorX - 2);
    }

    public int interiorDepth() {
        return Math.max(0, exteriorZ - 2);
    }

    public boolean isRodColumn(int rx, int rz) {
        int iw = interiorWidth();
        int id = interiorDepth();
        if (rx < 0 || rz < 0 || rx >= iw || rz >= id) {
            return false;
        }
        int i = rx * id + rz;
        return i >= 0 && i < rodMask.length && rodMask[i];
    }

    /**
     * Extracts an {@code mdct1...} / {@code mdct2...} code from a bare code or pasted URL/hash, then decodes it.
     */
    public static Optional<ModDecodedTurbineDesign> decode(String raw) {
        String code = extractCode(raw);
        if (code == null) {
            return Optional.empty();
        }
        return decodeExact(code);
    }

    @Nullable
    public static String extractCode(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            s = java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // keep original
        }
        Matcher m = CODE_IN_TEXT.matcher(s);
        if (m.find()) {
            return m.group();
        }
        if (s.startsWith("mdct1.") || s.startsWith("mdct2.")) {
            return s.split("[\\s&#]", 2)[0];
        }
        return null;
    }

    private static Optional<ModDecodedTurbineDesign> decodeExact(String code) {
        String[] p = code.split("\\.", -1);
        String rodCoil = null;
        if (p.length == 12 && "mdct2".equals(p[0])) {
            rodCoil = p[7];
            if (!isCoilId(rodCoil)) {
                return Optional.empty();
            }
            // Normalize to mdct1 field layout for shared parsing.
            p = new String[] {
                    "mdct1", p[1], p[2], p[3], p[4], p[5], p[6], p[8], p[9], p[10], p[11]
            };
        }
        if (p.length != 11 || !"mdct1".equals(p[0])) {
            return Optional.empty();
        }
        if (!"f".equals(p[4]) && !"c".equals(p[4])) {
            return Optional.empty();
        }
        if (!"a".equals(p[8]) && !"o".equals(p[8])) {
            return Optional.empty();
        }
        if (!isCoilId(p[6])) {
            return Optional.empty();
        }
        int x = parseBoundedInt(p[1], MIN_XZ, MAX_EXT);
        int z = parseBoundedInt(p[2], MIN_XZ, MAX_EXT);
        int y = parseBoundedInt(p[3], MIN_Y, MAX_EXT);
        int ring = parseBoundedInt(p[5], 0, 64);
        int coilLayers = parseBoundedInt(p[7], 1, 63);
        if (x < 0 || z < 0 || y < 0 || ring < 0 || coilLayers < 0) {
            return Optional.empty();
        }
        Long steam;
        if ("-".equals(p[9])) {
            steam = null;
        } else {
            steam = parseSteam(p[9]);
            if (steam == null) {
                return Optional.empty();
            }
        }
        byte[] bytes = Base64Url.decode(p[10]);
        if (bytes == null) {
            return Optional.empty();
        }
        int nCells = (x - 2) * (z - 2);
        int[] bits = BitPack.unpackBits(bytes, nCells, 1);
        if (bits == null) {
            return Optional.empty();
        }
        boolean[] rods = new boolean[nCells];
        for (int i = 0; i < nCells; i++) {
            rods[i] = bits[i] != 0;
        }
        String coil = p[6];
        if (rodCoil != null && rodCoil.equals(coil)) {
            rodCoil = null;
        }
        String storedCode = encodeCode(
                x, z, y, "c".equals(p[4]), ring, coil, rodCoil, coilLayers, "o".equals(p[8]), steam, rods);
        return Optional.of(new ModDecodedTurbineDesign(
                x, z, y,
                "c".equals(p[4]),
                ring,
                coil,
                rodCoil,
                coilLayers,
                "o".equals(p[8]),
                steam,
                rods,
                storedCode
        ));
    }

    /**
     * Builds the canonical seed string (mdct2 when {@code rodCoilId} is set and distinct from {@code coilId}).
     */
    public static String encodeCode(
            int x, int z, int y,
            boolean climb,
            int ring,
            String coilId,
            @Nullable String rodCoilId,
            int coilLayers,
            boolean coilFillOne,
            @Nullable Long steam,
            boolean[] rods) {
        String coil = coilId != null && !coilId.isEmpty() ? coilId : "air";
        String rod = rodCoilId;
        if (rod != null && (rod.isEmpty() || rod.equals(coil))) {
            rod = null;
        }
        boolean two = rod != null;
        StringBuilder sb = new StringBuilder(two ? "mdct2" : "mdct1");
        sb.append('.').append(x).append('.').append(z).append('.').append(y);
        sb.append('.').append(climb ? "c" : "f");
        sb.append('.').append(ring);
        sb.append('.').append(coil);
        if (two) {
            sb.append('.').append(rod);
        }
        sb.append('.').append(Math.max(1, Math.min(63, coilLayers)));
        sb.append('.').append(coilFillOne ? "o" : "a");
        sb.append('.').append(steam == null ? "-" : Long.toString(steam));
        int n = Math.max(0, (x - 2) * (z - 2));
        int[] bits = new int[n];
        if (rods != null) {
            for (int i = 0; i < Math.min(n, rods.length); i++) {
                bits[i] = rods[i] ? 1 : 0;
            }
        }
        sb.append('.').append(Base64Url.encode(BitPack.packBits(bits, 1)));
        return sb.toString();
    }

    private String rebuildCode(
            String newCoilId, @Nullable String newRodCoilId, int newLayers) {
        return encodeCode(
                exteriorX, exteriorZ, exteriorY, climbProfile, ring,
                newCoilId, newRodCoilId, newLayers, coilFillOne, steamMbPerTick, rodMask);
    }

    private static boolean isCoilId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        if ("air".equals(id)) {
            return true;
        }
        return id.matches("[a-z][a-z0-9_]*");
    }

    @Nullable
    private static Long parseSteam(String s) {
        if ("-".equals(s)) {
            return null;
        }
        if (s == null || !s.matches("\\d+")) {
            return null;
        }
        try {
            long v = Long.parseLong(s);
            return v >= 0 && v <= 1_000_000_000_000L ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseBoundedInt(String s, int min, int max) {
        if (s == null || !s.matches("\\d+")) {
            return -1;
        }
        try {
            int v = Integer.parseInt(s);
            return v >= min && v <= max ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Returns a copy with a different zone coil id and rebuilt seed (mdct1/mdct2 as needed). */
    public ModDecodedTurbineDesign withCoilId(String newCoilId) {
        String id = newCoilId != null && !newCoilId.isEmpty() ? newCoilId : "air";
        String newCode = rebuildCode(id, rodCoilId, coilLayers);
        return new ModDecodedTurbineDesign(
                exteriorX, exteriorZ, exteriorY, climbProfile, ring, id, rodCoilId, coilLayers, coilFillOne,
                steamMbPerTick, rodMask, newCode);
    }

    /** Returns a copy with a different rod-column coil id ({@code null} = same as zone) and rebuilt seed. */
    public ModDecodedTurbineDesign withRodCoilId(@Nullable String newRodCoilId) {
        String rod = newRodCoilId;
        if (rod != null && (rod.isEmpty() || rod.equals(coilId))) {
            rod = null;
        }
        String newCode = rebuildCode(coilId, rod, coilLayers);
        return new ModDecodedTurbineDesign(
                exteriorX, exteriorZ, exteriorY, climbProfile, ring, coilId, rod, coilLayers, coilFillOne,
                steamMbPerTick, rodMask, newCode);
    }

    /** Returns a copy with different coil layer count and rebuilt seed. */
    public ModDecodedTurbineDesign withCoilLayers(int newLayers) {
        int layers = Math.max(1, Math.min(63, newLayers));
        String newCode = rebuildCode(coilId, rodCoilId, layers);
        return new ModDecodedTurbineDesign(
                exteriorX, exteriorZ, exteriorY, climbProfile, ring, coilId, rodCoilId, layers, coilFillOne,
                steamMbPerTick, rodMask, newCode);
    }

    @Override
    public String toString() {
        return code;
    }
}
