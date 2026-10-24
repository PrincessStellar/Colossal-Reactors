package net.unfamily.colossal_reactors.builder.web;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decoded ModDecoded reactor design seed ({@code mdcr1}).
 * <pre>
 * mdcr1.{n|w}.{u|a|r}.X.Z.Y.palette.cellsB64
 * </pre>
 * Coolant {@code n}=none, {@code w}=water; fuel {@code u}=uranium, {@code a}=azurite, {@code r}=red_cake.
 * Palette entries are {@code rod} or heat-sink ids; cells are palette indices for each interior
 * column {@code x*(Z-2)+z}, bit-packed LSB-first then base64url.
 */
public record ModDecodedReactorDesign(
        String coolant,
        String fuel,
        int exteriorX,
        int exteriorZ,
        int exteriorY,
        String[] palette,
        String[] cells,
        String code
) {
    private static final Pattern CODE_IN_TEXT = Pattern.compile("mdcr1\\.[^&\\s#]+");
    private static final Map<String, String> FUEL_OF = Map.of(
            "u", "uranium",
            "a", "azurite",
            "r", "red_cake"
    );
    private static final int MIN_EXT = 3;
    private static final int MAX_EXT = 65;

    public ModDecodedReactorDesign {
        palette = palette != null ? Arrays.copyOf(palette, palette.length) : new String[0];
        cells = cells != null ? Arrays.copyOf(cells, cells.length) : new String[0];
        code = code != null ? code : "";
    }

    public int interiorWidth() {
        return Math.max(0, exteriorX - 2);
    }

    public int interiorDepth() {
        return Math.max(0, exteriorZ - 2);
    }

    /** Cell id at interior (rx, rz), or null if out of range. */
    @Nullable
    public String cellAt(int rx, int rz) {
        int iw = interiorWidth();
        int id = interiorDepth();
        if (rx < 0 || rz < 0 || rx >= iw || rz >= id) {
            return null;
        }
        int i = rx * id + rz;
        if (i < 0 || i >= cells.length) {
            return null;
        }
        return cells[i];
    }

    public boolean isRodColumn(int rx, int rz) {
        return "rod".equals(cellAt(rx, rz));
    }

    /**
     * Extracts an {@code mdcr1...} code from a bare code or a pasted URL/hash, then decodes it.
     */
    public static Optional<ModDecodedReactorDesign> decode(String raw) {
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
        if (s.startsWith("mdcr1.")) {
            return s.split("[\\s&#]", 2)[0];
        }
        return null;
    }

    private static Optional<ModDecodedReactorDesign> decodeExact(String code) {
        String[] p = code.split("\\.", -1);
        if (p.length != 8 || !"mdcr1".equals(p[0])) {
            return Optional.empty();
        }
        if (!"n".equals(p[1]) && !"w".equals(p[1])) {
            return Optional.empty();
        }
        String fuel = FUEL_OF.get(p[2]);
        if (fuel == null) {
            return Optional.empty();
        }
        int x = parseBoundedInt(p[3], MIN_EXT, MAX_EXT);
        int z = parseBoundedInt(p[4], MIN_EXT, MAX_EXT);
        int y = parseBoundedInt(p[5], MIN_EXT, MAX_EXT);
        if (x < 0 || z < 0 || y < 0) {
            return Optional.empty();
        }
        String[] pal = p[6].isEmpty() ? new String[0] : p[6].split(",", -1);
        if (pal.length == 0) {
            return Optional.empty();
        }
        Set<String> seen = new HashSet<>();
        for (String id : pal) {
            if (!isPaletteId(id) || !seen.add(id)) {
                return Optional.empty();
            }
        }
        byte[] bytes = Base64Url.decode(p[7]);
        if (bytes == null) {
            return Optional.empty();
        }
        int nCells = (x - 2) * (z - 2);
        int[] vals = BitPack.unpackBits(bytes, nCells, BitPack.paletteBits(pal.length));
        if (vals == null) {
            return Optional.empty();
        }
        String[] cells = new String[nCells];
        for (int i = 0; i < nCells; i++) {
            int v = vals[i];
            if (v < 0 || v >= pal.length) {
                return Optional.empty();
            }
            cells[i] = pal[v];
        }
        String coolant = "w".equals(p[1]) ? "water" : "none";
        return Optional.of(new ModDecodedReactorDesign(coolant, fuel, x, z, y, pal, cells, code));
    }

    private static boolean isPaletteId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        if ("rod".equals(id)) {
            return true;
        }
        return id.matches("[a-z][a-z0-9_]*");
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

    @Override
    public String toString() {
        return code;
    }
}
