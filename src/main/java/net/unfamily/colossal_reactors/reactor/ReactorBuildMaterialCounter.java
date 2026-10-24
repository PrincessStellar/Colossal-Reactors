package net.unfamily.colossal_reactors.reactor;

import net.unfamily.colossal_reactors.heatsink.HeatSinkLoader;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * Client-safe material estimate for the Reactor Builder. Logic mirrors {@link ReactorBuildLogic}
 * (frame shell, rod controllers, rods, liquid/solid heat sinks) without world placement.
 */
public final class ReactorBuildMaterialCounter {

    private static final int MB_PER_LIQUID_BLOCK = 1000;

    private ReactorBuildMaterialCounter() {}

    public record BuildMaterialCounts(
            int frameCasings,
            int faceCasings,
            int rods,
            int rodControllers,
            int heatSinkCells
    ) {
        /** Estimated fluid volume in mB when the selected heat sink uses liquid placement. */
        public int estimatedFluidMb() {
            return heatSinkCells * MB_PER_LIQUID_BLOCK;
        }
    }

    /**
     * @param sizeLeft   entity sizeLeft (same order as {@link ReactorBuildLogic} / simulation)
     * @param sizeRight  entity sizeRight
     * @param sizeHeight entity sizeHeight (block count minus one in GUI)
     * @param sizeDepth  entity sizeDepth
     */
    public static BuildMaterialCounts estimate(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int rodPattern, int patternMode, int heatSinkIndex, boolean openTop) {
        return estimate(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                rodPattern, patternMode, heatSinkIndex, openTop, null, null);
    }

    /**
     * Like {@link #estimate(int, int, int, int, int, int, int, boolean)} with optional Website
     * rod / heat-sink masks from a decoded design seed.
     */
    public static BuildMaterialCounts estimate(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int rodPattern, int patternMode, int heatSinkIndex, boolean openTop,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            @Nullable BiPredicate<Integer, Integer> websiteHeatSinkCell) {
        int w = sizeLeft + sizeRight + 1;
        int h = sizeHeight + 1;
        int d = sizeDepth + 1;
        int rw = RodPatternLogic.rodSpaceWidth(w, patternMode);
        int rh = RodPatternLogic.rodSpaceHeight(h, patternMode);
        int rd = RodPatternLogic.rodSpaceDepth(d, patternMode);
        int insetXZ = RodPatternLogic.rodSpaceInsetXZ(patternMode);
        boolean expansionRodAtCenter = (rodPattern == RodPatternLogic.PATTERN_EXPANSION)
                ? RodPatternLogic.getExpansionRodAtCenterForPreview(rw, rd)
                : false;

        int frameCasings = 0;
        int faceCasings = 0;
        int maxY = h - 1;
        for (int lx = 0; lx < w; lx++) {
            for (int ly = 0; ly < h; ly++) {
                for (int lz = 0; lz < d; lz++) {
                    if (ly == maxY && openTop) continue;
                    boolean onBorder = (lx == 0 || lx == w - 1 || ly == 0 || ly == h - 1 || lz == 0 || lz == d - 1);
                    if (!onBorder) continue;
                    if (ly == maxY && isRodControllerLocal(lx, lz, insetXZ, rw, rd, rodPattern, expansionRodAtCenter, websiteRodOverride)) {
                        continue;
                    }
                    boolean edgeOrCorner = isEdgeOrCorner(lx, ly, lz, 0, 0, 0, w - 1, maxY, d - 1);
                    boolean topOrBottomFace = (ly == 0 || ly == maxY);
                    if (edgeOrCorner || topOrBottomFace) frameCasings++;
                    else faceCasings++;
                }
            }
        }

        int rodControllers = 0;
        for (int rx = 0; rx < rw; rx++) {
            for (int rz = 0; rz < rd; rz++) {
                if (isRodColumn(rx, rz, rw, rd, rodPattern, expansionRodAtCenter, websiteRodOverride)) {
                    rodControllers++;
                }
            }
        }

        int rods = 0;
        for (int ly = 1; ly < h - 1; ly++) {
            for (int lx = insetXZ; lx < w - insetXZ; lx++) {
                for (int lz = insetXZ; lz < d - insetXZ; lz++) {
                    int rx = lx - insetXZ;
                    int ry = ly - 1;
                    int rz = lz - insetXZ;
                    if (isRod(rx, ry, rz, rw, rh, rd, rodPattern, expansionRodAtCenter, websiteRodOverride)) {
                        rods++;
                    }
                }
            }
        }

        int heatSinkCells = rodPattern == RodPatternLogic.PATTERN_NONE
                ? 0
                : countHeatSinkCells(w, h, d, insetXZ, rw, rh, rd, rodPattern, patternMode, heatSinkIndex,
                        expansionRodAtCenter, websiteRodOverride, websiteHeatSinkCell);

        return new BuildMaterialCounts(frameCasings, faceCasings, rods, rodControllers, heatSinkCells);
    }

    private static int countHeatSinkCells(
            int w, int h, int d, int insetXZ, int rw, int rh, int rd,
            int pattern, int patternMode, int heatSinkIndex, boolean expansionRodAtCenter,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            @Nullable BiPredicate<Integer, Integer> websiteHeatSinkCell) {
        if (heatSinkIndex <= 0) return 0;
        boolean liquid = HeatSinkLoader.requiresLiquidPlacement(heatSinkIndex);
        boolean solid = !liquid && !HeatSinkLoader.shouldSkipSolidHeatSinkAutoPlacement(heatSinkIndex);
        if (!liquid && !solid) return 0;

        int count = 0;
        for (int ly = 1; ly < h - 1; ly++) {
            for (int lx = 1; lx < w - 1; lx++) {
                for (int lz = 1; lz < d - 1; lz++) {
                    if (isInteriorCellRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter, websiteRodOverride)) {
                        continue;
                    }
                    if (pattern == RodPatternLogic.PATTERN_WEBSITE) {
                        if (websiteHeatSinkCell == null) continue;
                        if (!isInRodSpace(lx, ly, lz, w, h, d, insetXZ)) continue;
                        int rx = lx - insetXZ;
                        int rz = lz - insetXZ;
                        if (!websiteHeatSinkCell.test(rx, rz)) continue;
                    } else if (pattern != RodPatternLogic.PATTERN_CUSTOM) {
                        if (patternMode == RodPatternLogic.MODE_SUPER_ECONOMY) {
                            if (!isInRodSpace(lx, ly, lz, w, h, d, insetXZ)
                                    || !isRodSpaceCellAdjacentToRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter, websiteRodOverride)) {
                                continue;
                            }
                        } else if (patternMode == RodPatternLogic.MODE_ECONOMY
                                && !isInteriorCellAdjacentToRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter, websiteRodOverride)) {
                            continue;
                        }
                    }
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isRodColumn(
            int rx, int rz, int rw, int rd, int pattern, boolean expansionRodAtCenter,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride) {
        if (pattern == RodPatternLogic.PATTERN_WEBSITE) {
            return websiteRodOverride != null && websiteRodOverride.test(rx, rz);
        }
        return RodPatternLogic.isRodColumnForPreview(rx, rz, rw, rd, pattern, expansionRodAtCenter);
    }

    private static boolean isRod(
            int rx, int ry, int rz, int rw, int rh, int rd, int pattern, boolean expansionRodAtCenter,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride) {
        if (rw <= 0 || rh <= 0 || rd <= 0) return false;
        if (rx < 0 || rx >= rw || ry < 0 || ry >= rh || rz < 0 || rz >= rd) return false;
        return isRodColumn(rx, rz, rw, rd, pattern, expansionRodAtCenter, websiteRodOverride);
    }

    private static boolean isRodControllerLocal(
            int lx, int lz, int insetXZ, int rw, int rd, int pattern, boolean expansionRodAtCenter,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride) {
        int rx = lx - insetXZ;
        int rz = lz - insetXZ;
        if (rx < 0 || rx >= rw || rz < 0 || rz >= rd) return false;
        return isRodColumn(rx, rz, rw, rd, pattern, expansionRodAtCenter, websiteRodOverride);
    }

    private static boolean isEdgeOrCorner(int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int onBoundary = 0;
        if (x == minX || x == maxX) onBoundary++;
        if (y == minY || y == maxY) onBoundary++;
        if (z == minZ || z == maxZ) onBoundary++;
        return onBoundary >= 2;
    }

    private static boolean isInRodSpace(int lx, int ly, int lz, int w, int h, int d, int insetXZ) {
        return lx >= insetXZ && lx < w - insetXZ && ly >= 1 && ly < h - 1 && lz >= insetXZ && lz < d - insetXZ;
    }

    private static boolean isInteriorCellRod(
            int lx, int ly, int lz, int w, int h, int d, int insetXZ, int rw, int rh, int rd,
            int pattern, boolean expansionRodAtCenter,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride) {
        if (!isInRodSpace(lx, ly, lz, w, h, d, insetXZ)) return false;
        int rx = lx - insetXZ;
        int ry = ly - 1;
        int rz = lz - insetXZ;
        return isRod(rx, ry, rz, rw, rh, rd, pattern, expansionRodAtCenter, websiteRodOverride);
    }

    private static boolean isRodSpaceCellAdjacentToRod(
            int lx, int ly, int lz, int w, int h, int d, int insetXZ, int rw, int rh, int rd,
            int pattern, boolean expansionRodAtCenter,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride) {
        for (int dx = -1; dx <= 1; dx += 2) {
            int nx = lx + dx;
            if (nx >= insetXZ && nx < w - insetXZ && isInteriorCellRod(nx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter, websiteRodOverride)) {
                return true;
            }
        }
        for (int dy = -1; dy <= 1; dy += 2) {
            int ny = ly + dy;
            if (ny >= 1 && ny < h - 1 && isInteriorCellRod(lx, ny, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter, websiteRodOverride)) {
                return true;
            }
        }
        for (int dz = -1; dz <= 1; dz += 2) {
            int nz = lz + dz;
            if (nz >= insetXZ && nz < d - insetXZ && isInteriorCellRod(lx, ly, nz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter, websiteRodOverride)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInteriorCellAdjacentToRod(
            int lx, int ly, int lz, int w, int h, int d, int insetXZ, int rw, int rh, int rd,
            int pattern, boolean expansionRodAtCenter,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride) {
        for (int dx = -1; dx <= 1; dx += 2) {
            int nx = lx + dx;
            if (nx >= 1 && nx < w - 1 && isInteriorCellRod(nx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter, websiteRodOverride)) {
                return true;
            }
        }
        for (int dy = -1; dy <= 1; dy += 2) {
            int ny = ly + dy;
            if (ny >= 1 && ny < h - 1 && isInteriorCellRod(lx, ny, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter, websiteRodOverride)) {
                return true;
            }
        }
        for (int dz = -1; dz <= 1; dz += 2) {
            int nz = lz + dz;
            if (nz >= 1 && nz < d - 1 && isInteriorCellRod(lx, ly, nz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter, websiteRodOverride)) {
                return true;
            }
        }
        return false;
    }
}
