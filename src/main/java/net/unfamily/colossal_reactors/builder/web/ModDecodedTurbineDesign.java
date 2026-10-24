package net.unfamily.colossal_reactors.builder.web;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decoded ModDecoded turbine design seed ({@code mdct1}).
 * <pre>
 * mdct1.X.Z.Y.{f|c}.ring.coil.coilLayers.{a|o}.steam.rodsB64
 * </pre>
 * Profile {@code f}=full (same blade rings every layer), {@code c}=climb.
 * Coil fill {@code a}=all zone, {@code o}=one block. Steam is mB/t or {@code -} for uncapped.
 * Rods: 1 bit per interior cell {@code x*(Z-2)+z}, LSB first, base64url.
 */
public record ModDecodedTurbineDesign(
        int exteriorX,
        int exteriorZ,
        int exteriorY,
        boolean climbProfile,
        int ring,
        String coilId,
        int coilLayers,
        boolean coilFillOne,
        @Nullable Long steamMbPerTick,
        boolean[] rodMask,
        String code
) {
    private static final Pattern CODE_IN_TEXT = Pattern.compile("mdct1\\.[^&\\s#]+");
    private static final int MIN_XZ = 3;
    private static final int MAX_EXT = 65;
    private static final int MIN_Y = 5;

    public ModDecodedTurbineDesign {
        coilId = coilId != null ? coilId : "air";
        rodMask = rodMask != null ? Arrays.copyOf(rodMask, rodMask.length) : new boolean[0];
        code = code != null ? code : "";
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
     * Extracts an {@code mdct1...} code from a bare code or pasted URL/hash, then decodes it.
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
        if (s.startsWith("mdct1.")) {
            return s.split("[\\s&#]", 2)[0];
        }
        return null;
    }

    private static Optional<ModDecodedTurbineDesign> decodeExact(String code) {
        String[] p = code.split("\\.", -1);
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
        return Optional.of(new ModDecodedTurbineDesign(
                x, z, y,
                "c".equals(p[4]),
                ring,
                p[6],
                coilLayers,
                "o".equals(p[8]),
                "-".equals(p[9]) ? null : steam,
                rods,
                code
        ));
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

    /** Returns a copy with a different homogeneous coil id and rebuilt {@code mdct1} seed. */
    public ModDecodedTurbineDesign withCoilId(String newCoilId) {
        String id = newCoilId != null && !newCoilId.isEmpty() ? newCoilId : "air";
        String[] p = splitCodeParts();
        if (p == null) {
            return this;
        }
        p[6] = id;
        String newCode = String.join(".", p);
        return new ModDecodedTurbineDesign(
                exteriorX, exteriorZ, exteriorY, climbProfile, ring, id, coilLayers, coilFillOne,
                steamMbPerTick, rodMask, newCode);
    }

    /** Returns a copy with different coil layer count and rebuilt {@code mdct1} seed. */
    public ModDecodedTurbineDesign withCoilLayers(int newLayers) {
        int layers = Math.max(1, Math.min(63, newLayers));
        String[] p = splitCodeParts();
        if (p == null) {
            return this;
        }
        p[7] = Integer.toString(layers);
        String newCode = String.join(".", p);
        return new ModDecodedTurbineDesign(
                exteriorX, exteriorZ, exteriorY, climbProfile, ring, coilId, layers, coilFillOne,
                steamMbPerTick, rodMask, newCode);
    }

    @Nullable
    private String[] splitCodeParts() {
        if (code == null || code.isEmpty()) {
            return null;
        }
        String[] p = code.split("\\.", -1);
        return p.length == 11 ? p : null;
    }

    @Override
    public String toString() {
        return code;
    }
}
