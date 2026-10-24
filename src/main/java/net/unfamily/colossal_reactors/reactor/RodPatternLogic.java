package net.unfamily.colossal_reactors.reactor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes whether a position in the reactor rod space is a rod (part of a rod column).
 * Patterns are 2D (top view): only (rx, rz) define rod columns; the same column extends for all Y.
 * Rod space: first -2 on X and Z for the frame (casing), then optional -2 on X and Z for mode.
 * On Y: -1 top and -1 bottom for the frame (rod controllers sit in the top frame; bottom is casing).
 * <p>
 * Used by preview ({@link net.unfamily.colossal_reactors.network.ReactorPreviewPayload}) and must be used
 * the same way by the future Reactor Builder build (place rods and rod controllers to match preview).
 */
public final class RodPatternLogic {

    /** Cache: for Frame pattern, which variant (rod at center vs heat sink at center) yields more rod columns per (rw, rd). */
    private static final Map<Long, Boolean> FRAME_ROD_AT_CENTER_CACHE = new ConcurrentHashMap<>();
    private static long frameCacheKey(int rw, int rd) {
        return (long) rw << 16 | (rd & 0xFFFF);
    }

    /** Inset on X/Z for frame (casing): 1 block each side = 2 total on width/depth. */
    private static final int FRAME_INSET_XZ = 1;
    /** Extra inset on X/Z for OPTIMIZED/ECONOMY mode: 1 more block each side. */
    private static final int MODE_INSET_XZ = 1;

    /** External website design seed; no built-in rod columns until a seed is applied. */
    public static final int PATTERN_WEBSITE = 0;
    public static final int PATTERN_DOTS = 1;
    public static final int PATTERN_CHECKERBOARD = 2;
    public static final int PATTERN_EXPANSION = 3;
    /** No rods; heat sinks / liquids fill the full interior (ignores economy adjacency). */
    public static final int PATTERN_CUSTOM = 4;
    /** No rods and no heat sinks placed (empty interior / frame only). */
    public static final int PATTERN_NONE = 5;

    public static final int PATTERN_COUNT = 6;

    public static final int MODE_OPTIMIZED = 0;
    public static final int MODE_PRODUCTION = 1;
    public static final int MODE_ECONOMY = 2;
    /** Like Economy but coolant/heat sink only in rod space (extra -2 on X and Z), only cells adjacent to a rod. */
    public static final int MODE_SUPER_ECONOMY = 3;

    private RodPatternLogic() {}

    /**
     * Rod space dimensions. X and Z: first -2 for frame (1 per side), then -2 for mode in OPTIMIZED/ECONOMY/SUPER_ECONOMY.
     * Y: -2 for frame (1 top, 1 bottom); rod controllers sit in the top frame. Reactor volume: w, h, d.
     */
    public static int rodSpaceWidth(int reactorWidth, int patternMode) {
        int afterFrame = Math.max(0, reactorWidth - 2 * FRAME_INSET_XZ);
        if (patternMode == MODE_PRODUCTION) return afterFrame;
        return Math.max(0, afterFrame - 2 * MODE_INSET_XZ);
    }

    public static int rodSpaceHeight(int reactorHeight, int patternMode) {
        return Math.max(0, reactorHeight - 2); // -1 Y top, -1 Y bottom (frame; rod controllers in top frame)
    }

    public static int rodSpaceDepth(int reactorDepth, int patternMode) {
        int afterFrame = Math.max(0, reactorDepth - 2 * FRAME_INSET_XZ);
        if (patternMode == MODE_PRODUCTION) return afterFrame;
        return Math.max(0, afterFrame - 2 * MODE_INSET_XZ);
    }

    /** Inset from reactor volume edge to rod space start (X and Z). PRODUCTION=1 (frame only), OPTIMIZED/ECONOMY/SUPER_ECONOMY=2 (frame+mode). */
    public static int rodSpaceInsetXZ(int patternMode) {
        return patternMode == MODE_PRODUCTION ? FRAME_INSET_XZ : FRAME_INSET_XZ + MODE_INSET_XZ;
    }

    /**
     * Returns true if the position (rx, ry, rz) in rod space should be a rod.
     * For placement/build: Frame uses the variant (rod at center vs heat sink at center) that yields more rods.
     */
    public static boolean isRod(int rx, int ry, int rz, int rw, int rh, int rd, int pattern) {
        return isRod(rx, ry, rz, rw, rh, rd, pattern, false);
    }

    /**
     * Same as {@link #isRod(int, int, int, int, int, int, int)} with preview flag.
     * Patterns are 2D (top view): same column (rx, rz) is rod for all ry.
     */
    public static boolean isRod(int rx, int ry, int rz, int rw, int rh, int rd, int pattern, boolean forPreview) {
        if (rw <= 0 || rh <= 0 || rd <= 0) return false;
        if (rx < 0 || rx >= rw || ry < 0 || ry >= rh || rz < 0 || rz >= rd) return false;
        return isRodColumn(rx, rz, rw, rd, pattern, forPreview);
    }

    /**
     * True if the column at (rx, rz) is a rod column (pattern is 2D, viewed from above).
     * The whole column from y=0 to rh-1 is rod; rh is not used in the pattern.
     */
    public static boolean isRodColumn(int rx, int rz, int rw, int rd, int pattern, boolean forPreview) {
        if (rw <= 0 || rd <= 0) return false;
        if (rx < 0 || rx >= rw || rz < 0 || rz >= rd) return false;
        return switch (pattern) {
            case PATTERN_DOTS -> isRodDotsColumn(rx, rz, rw, rd);
            case PATTERN_CHECKERBOARD -> isRodCheckerboardColumn(rx, rz, rw, rd);
            case PATTERN_EXPANSION -> isRodExpansionColumn(rx, rz, rw, rd, forPreview);
            case PATTERN_WEBSITE, PATTERN_CUSTOM, PATTERN_NONE -> false;
            default -> false;
        };
    }

    /**
     * Dots (2D): rod columns on a 2-step grid ({@code rx % 2 == 0 && rz % 2 == 0}).
     * For even×even rod footprints the geometric center is a 2×2 of cells, but only
     * {@code (rw/2 - 1, rd/2 - 1)} lies on the dots grid — that central column must stay a rod.
     */
    private static boolean isRodDotsColumn(int rx, int rz, int rw, int rd) {
        return (rx % 2 == 0) && (rz % 2 == 0);
    }

    /** Checkerboard (2D): alternating columns so no two rod columns touch. */
    private static boolean isRodCheckerboardColumn(int rx, int rz, int rw, int rd) {
        return (rx + rz) % 2 == 0;
    }

    /**
     * Frame (2D): perimeter + inner frame edges + center. Center can be 1, 1x2, 2x1, or 2x2 columns
     * when one of (rw, rd) is odd and the other even (1x2 or 2x1) or both even (2x2).
     */
    private static boolean isRodExpansionColumn(int rx, int rz, int rw, int rd, boolean forPreview) {
        int minDim = Math.min(rw, rd);
        if (minDim < 3) return false;
        int layers = expansionLayerCount(rw, rd);
        boolean rodAtCenter = forPreview
                || FRAME_ROD_AT_CENTER_CACHE.computeIfAbsent(frameCacheKey(rw, rd),
                        k -> countExpansionColumns(rw, rd, layers, true) >= countExpansionColumns(rw, rd, layers, false));
        return isRodExpansionColumnWithLayers(rx, rz, rw, rd, layers, rodAtCenter);
    }

    private static boolean isRodExpansionColumnWithLayers(int rx, int rz, int rw, int rd, int layers, boolean rodAtCenter) {
        // L=0: outer perimeter (2D)
        if (rx == 0 || rx == rw - 1 || rz == 0 || rz == rd - 1) return true;
        // L>=1: boundary of inner rectangle [L, rw-1-L] x [L, rd-1-L]
        for (int L = 1; L < layers; L++) {
            if (rx == L || rx == rw - 1 - L || rz == L || rz == rd - 1 - L) return true;
        }
        if (!rodAtCenter) return false;
        int cx = rw / 2, cz = rd / 2;
        boolean oddW = (rw & 1) == 1, oddD = (rd & 1) == 1;
        // Center: 1 column (both odd), 1x2 or 2x1 (one odd one even), 2x2 (both even)
        boolean inCenterX = oddW ? (rx == cx) : (rx >= cx - 1 && rx <= cx);
        boolean inCenterZ = oddD ? (rz == cz) : (rz >= cz - 1 && rz <= cz);
        return inCenterX && inCenterZ;
    }

    /** Layer count for Frame (2D: uses only rw, rd). */
    public static int expansionLayerCount(int rw, int rd) {
        int minDim = Math.min(rw, rd);
        if (minDim < 5) return 1;
        if (minDim < 7) return 2;
        int count2 = countExpansionColumns(rw, rd, 2, true);
        int count3 = countExpansionColumns(rw, rd, 3, true);
        return count3 >= count2 ? 3 : 2;
    }

    /** Backward compatibility: 3D signature delegates to 2D. */
    public static int expansionLayerCount(int rw, int rh, int rd) {
        return expansionLayerCount(rw, rd);
    }

    private static int countExpansionColumns(int rw, int rd, int layers, boolean rodAtCenter) {
        int count = 0;
        for (int rx = 0; rx < rw; rx++) {
            for (int rz = 0; rz < rd; rz++) {
                if (isRodExpansionColumnWithLayers(rx, rz, rw, rd, layers, rodAtCenter)) count++;
            }
        }
        return count;
    }

    /**
     * For Frame (EXPANSION) pattern: compute both variants (rod at center vs heat sink at center)
     * and return the one that places more rod columns. Used by preview so it shows the same
     * variant that build would use (the one with more rods).
     */
    public static boolean getExpansionRodAtCenterForPreview(int rw, int rd) {
        int minDim = Math.min(rw, rd);
        if (minDim < 3) return true;
        int layers = expansionLayerCount(rw, rd);
        int countWithRod = countExpansionColumns(rw, rd, layers, true);
        int countWithHeatSink = countExpansionColumns(rw, rd, layers, false);
        return countWithRod >= countWithHeatSink;
    }

    /**
     * Preview API: like {@link #isRodColumn(int, int, int, int, int, boolean)} but for Frame pattern
     * uses the given variant (rod at center vs not) instead of cache. For other patterns, expansionRodAtCenter is ignored.
     */
    public static boolean isRodColumnForPreview(int rx, int rz, int rw, int rd, int pattern, boolean expansionRodAtCenter) {
        if (rw <= 0 || rd <= 0) return false;
        if (rx < 0 || rx >= rw || rz < 0 || rz >= rd) return false;
        if (pattern == PATTERN_EXPANSION) {
            int minDim = Math.min(rw, rd);
            if (minDim < 3) return false;
            int layers = expansionLayerCount(rw, rd);
            return isRodExpansionColumnWithLayers(rx, rz, rw, rd, layers, expansionRodAtCenter);
        }
        return isRodColumn(rx, rz, rw, rd, pattern, false);
    }

    /**
     * Preview API: like {@link #isRod(int, int, int, int, int, int, int, boolean)} but for Frame
     * uses the given variant. For other patterns, expansionRodAtCenter is ignored.
     */
    public static boolean isRodForPreview(int rx, int ry, int rz, int rw, int rh, int rd, int pattern, boolean expansionRodAtCenter) {
        if (rw <= 0 || rh <= 0 || rd <= 0) return false;
        if (rx < 0 || rx >= rw || ry < 0 || ry >= rh || rz < 0 || rz >= rd) return false;
        return isRodColumnForPreview(rx, rz, rw, rd, pattern, expansionRodAtCenter);
    }
}
