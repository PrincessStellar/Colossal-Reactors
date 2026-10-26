package net.unfamily.colossal_reactors.turbine;

import net.unfamily.colossal_reactors.Config;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Rod/blade/coil estimates from builder shell sizes. Uses {@link TurbineRotorLayout#rodExtent()}
 * so simulation and material counts match {@link TurbineBuildLogic}.
 */
public final class TurbineBuilderMetrics {

    public record Estimate(
            int rodBlocks,
            int bladeItems,
            int coilBlocks,
            int rodExtent,
            List<Integer> layerBladeCounts
    ) {}

    /** Blade/rod totals for one virtual build (matches {@link TurbineBuildLogic} placement). */
    public record RodBladeCounts(
            int rodBlocks,
            int bladeItems,
            int validBladeItems,
            List<Integer> layerMaxBladesPerRod
    ) {}

    /** Per-cell coil zone counts and efficiencies (zone vs rod-column materials). */
    public record CoilZoneFillMetrics(int solidCellCount, double averageCoilEfficiency, double rodColumnFlowEfficiency) {}

    private TurbineBuilderMetrics() {}

    public static int coilOptionForCrossCell(
            int crossA, int crossB, int crossSizeA, int crossSizeB,
            int rodPattern,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            int zoneCoilIndex,
            int rodCoilIndex) {
        if (TurbineRodPatternLogic.isRodColumn(crossA, crossB, crossSizeA, crossSizeB, rodPattern, websiteRodOverride)) {
            return rodCoilIndex;
        }
        return zoneCoilIndex;
    }

    public static CoilZoneFillMetrics coilZoneFillMetrics(
            TurbineRotorLayout layout,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            int rodPattern,
            int zoneCoilIndex,
            int rodCoilIndex,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride) {
        if (TurbineRodPatternLogic.skipsCoils(rodPattern)) {
            return new CoilZoneFillMetrics(0, Config.TURBINE_EMPTY_COIL_EFFICIENCY.get(), 1.0);
        }
        // Same as TurbineValidation / ModDecoded: sum coe & max over cells, then min(avg, avg).
        // Air cells count at emptyCoil (unless legacyIgnoreAir); rod-column flow stays 1.0 for air.
        boolean legacyAir = Boolean.TRUE.equals(Config.TURBINE_LEGACY_IGNORE_AIR_IN_COIL_AVERAGE.get());
        double empty = Config.TURBINE_EMPTY_COIL_EFFICIENCY.get();
        int cellCount = 0;
        double sumCoe = 0;
        double sumMax = 0;
        double sumFlowCoe = 0;
        double sumFlowMax = 0;
        int rodFlowCells = 0;
        int solidCount = 0;
        int rw = layout.crossSizeA();
        int rd = layout.crossSizeB();
        List<ElecCoilDefinition> defs = ElecCoilLoader.getAllDefinitions();
        for (int xx = minX + 1; xx < maxX; xx++) {
            for (int yy = minY + 1; yy < maxY; yy++) {
                for (int zz = minZ + 1; zz < maxZ; zz++) {
                    if (!layout.isCoilZoneWorld(xx, yy, zz)) {
                        continue;
                    }
                    int ca = layout.crossAFromWorld(xx, yy, zz);
                    int cb = layout.crossBFromWorld(xx, yy, zz);
                    int idx = coilOptionForCrossCell(
                            ca, cb, rw, rd, rodPattern, websiteRodOverride, zoneCoilIndex, rodCoilIndex);
                    boolean air = ElecCoilLoader.shouldSkipSolidCoilAutoPlacement(idx);
                    boolean onRod = TurbineRodPatternLogic.isRodColumn(
                            ca, cb, rw, rd, rodPattern, websiteRodOverride);
                    if (air) {
                        if (!legacyAir) {
                            cellCount++;
                            sumCoe += empty;
                            sumMax += empty;
                        }
                        if (onRod) {
                            rodFlowCells++;
                            sumFlowCoe += 1.0;
                            sumFlowMax += 1.0;
                        }
                        continue;
                    }
                    cellCount++;
                    solidCount++;
                    if (idx >= 0 && idx < defs.size()) {
                        ElecCoilDefinition def = defs.get(idx);
                        sumCoe += def.effCoe();
                        sumMax += def.effMax();
                        if (onRod) {
                            rodFlowCells++;
                            sumFlowCoe += def.flowCoe();
                            sumFlowMax += def.flowMax();
                        }
                    }
                }
            }
        }
        double coilEff = cellCount > 0
                ? Math.min(sumCoe / cellCount, sumMax / cellCount)
                : empty;
        double flowEff = rodFlowCells > 0
                ? Math.min(sumFlowCoe / rodFlowCells, sumFlowMax / rodFlowCells)
                : 1.0;
        return new CoilZoneFillMetrics(solidCount, coilEff, flowEff);
    }

    public static Estimate fromShellSizes(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex) {
        return fromShellSizes(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, storedCoilSetting, rodPattern, coilIndex, null, false,
                Integer.MAX_VALUE, TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static Estimate fromShellSizes(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile) {
        return fromShellSizes(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, storedCoilSetting, rodPattern, coilIndex,
                websiteRodOverride, websiteClimbProfile, Integer.MAX_VALUE,
                TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static Estimate fromShellSizes(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap) {
        return fromShellSizes(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, storedCoilSetting, rodPattern, coilIndex,
                websiteRodOverride, websiteClimbProfile, websiteBladeRingCap,
                TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static Estimate fromShellSizes(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap,
            int bladeVariant) {
        return fromShellSizes(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, storedCoilSetting, rodPattern, coilIndex, coilIndex,
                websiteRodOverride, websiteClimbProfile, websiteBladeRingCap, bladeVariant);
    }

    public static Estimate fromShellSizes(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex,
            int rodCoilIndex,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap,
            int bladeVariant) {

        int w = sizeLeft + sizeRight + 1;
        int h = sizeHeight + 1;
        int d = sizeDepth + 1;
        TurbinePlacementAxis placement = TurbinePlacementAxis.fromIndex(placementAxisIndex);
        int interiorAlong = interiorAlongPlacement(w, h, d, placement);
        int appliedCoils = TurbineRodSpaceLayout.appliedCoilLayerCount(interiorAlong, storedCoilSetting);
        TurbineRotorLayout layout = TurbineRotorLayout.from(
                0, 0, 0, w - 1, h - 1, d - 1, w, h, d, appliedCoils, placement.facing());
        RodBladeCounts blades = countRodsAndBlades(
                layout, rodPattern, websiteRodOverride, websiteClimbProfile, websiteBladeRingCap, bladeVariant);

        CoilZoneFillMetrics coils = coilZoneFillMetrics(
                layout, 0, 0, 0, w - 1, h - 1, d - 1,
                rodPattern, coilIndex, rodCoilIndex, websiteRodOverride);

        return new Estimate(
                blades.rodBlocks(),
                blades.bladeItems(),
                coils.solidCellCount(),
                layout.rodExtent(),
                blades.layerMaxBladesPerRod());
    }

    public static int balancedBladesForSteam(int bladeItems) {
        if (!Boolean.TRUE.equals(Config.TURBINE_REQUIRE_BALANCED_BLADE_RINGS.get())) {
            return bladeItems;
        }
        return (bladeItems / 4) * 4;
    }

    /**
     * Metrics for a turbine built exactly as the builder would place it (aligned with {@link TurbineValidation}).
     */
    public record IdealBuildMetrics(
            int bladeCount,
            int validBladeCount,
            int coilBlockCount,
            double coilEfficiency,
            double bladeEfficiency,
            double flowEfficiency,
            TurbineRotorLayout layout
    ) {}

    public static IdealBuildMetrics idealBuildMetrics(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex) {
        return idealBuildMetrics(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, storedCoilSetting, rodPattern, coilIndex, null, false,
                Integer.MAX_VALUE, TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static IdealBuildMetrics idealBuildMetrics(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile) {
        return idealBuildMetrics(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, storedCoilSetting, rodPattern, coilIndex,
                websiteRodOverride, websiteClimbProfile, Integer.MAX_VALUE,
                TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static IdealBuildMetrics idealBuildMetrics(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap) {
        return idealBuildMetrics(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, storedCoilSetting, rodPattern, coilIndex,
                websiteRodOverride, websiteClimbProfile, websiteBladeRingCap,
                TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static IdealBuildMetrics idealBuildMetrics(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap,
            int bladeVariant) {
        return idealBuildMetrics(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, storedCoilSetting, rodPattern, coilIndex, coilIndex,
                websiteRodOverride, websiteClimbProfile, websiteBladeRingCap, bladeVariant);
    }

    public static IdealBuildMetrics idealBuildMetrics(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex,
            int rodCoilIndex,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap,
            int bladeVariant) {

        int w = sizeLeft + sizeRight + 1;
        int h = sizeHeight + 1;
        int d = sizeDepth + 1;
        TurbinePlacementAxis placement = TurbinePlacementAxis.fromIndex(placementAxisIndex);
        int interiorAlong = interiorAlongPlacement(w, h, d, placement);
        int appliedCoils = TurbineRodSpaceLayout.appliedCoilLayerCount(interiorAlong, storedCoilSetting);
        TurbineRotorLayout layout = TurbineRotorLayout.from(
                0, 0, 0, w - 1, h - 1, d - 1, w, h, d, appliedCoils, placement.facing());
        RodBladeCounts blades = countRodsAndBlades(
                layout, rodPattern, websiteRodOverride, websiteClimbProfile, websiteBladeRingCap, bladeVariant);

        CoilZoneFillMetrics coils = coilZoneFillMetrics(
                layout, 0, 0, 0, w - 1, h - 1, d - 1,
                rodPattern, coilIndex, rodCoilIndex, websiteRodOverride);

        double bladeEff = TurbineBladeEfficiency.computeMultiplier(blades.layerMaxBladesPerRod());
        double flowEff = TurbineFlowMath.effectiveFlowEfficiency(
                coils.rodColumnFlowEfficiency(),
                appliedCoils,
                interiorAlong,
                layout.crossSizeA(),
                layout.crossSizeB(),
                blades.validBladeItems());

        return new IdealBuildMetrics(
                blades.bladeItems(),
                blades.validBladeItems(),
                coils.solidCellCount(),
                coils.averageCoilEfficiency(),
                bladeEff,
                flowEff,
                layout);
    }

    /**
     * Counts rods and blades like {@link TurbineBuildLogic} / ModDecoded {@code bladeLayer(..., builder=2)}:
     * shell + design ring only (no Chebyshev neighbor pre-cap), with a per-layer occupancy dry-run so
     * dense grids do not over-count colliding blades.
     */
    public static RodBladeCounts countRodsAndBlades(TurbineRotorLayout layout, int rodPattern) {
        return countRodsAndBlades(layout, rodPattern, null, false, Integer.MAX_VALUE,
                TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static RodBladeCounts countRodsAndBlades(
            TurbineRotorLayout layout, int rodPattern,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile) {
        return countRodsAndBlades(layout, rodPattern, websiteRodOverride, websiteClimbProfile,
                Integer.MAX_VALUE, TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static RodBladeCounts countRodsAndBlades(
            TurbineRotorLayout layout, int rodPattern,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap) {
        return countRodsAndBlades(layout, rodPattern, websiteRodOverride, websiteClimbProfile,
                websiteBladeRingCap, TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static RodBladeCounts countRodsAndBlades(
            TurbineRotorLayout layout, int rodPattern,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap,
            int bladeVariant) {
        int rodBlocks = 0;
        int bladeItems = 0;
        int validBladeItems = 0;
        List<Integer> layerMaxBladesPerRod = new ArrayList<>();
        boolean requireBalanced = Boolean.TRUE.equals(
                Config.TURBINE_REQUIRE_BALANCED_BLADE_RINGS.get());
        int rw = layout.crossSizeA();
        int rd = layout.crossSizeB();
        boolean hasRodColumn = rw > 0 && rd > 0;

        int growth = TurbineRodPatternLogic.resolveBladeGrowth(rodPattern, bladeVariant, websiteClimbProfile);
        List<TurbineRodControllerLayout.Center> columns = hasRodColumn
                ? TurbineRodPatternLogic.rodColumns(rw, rd, rodPattern, websiteRodOverride)
                : List.of();
        int rodsOnLayer = columns.size();
        int ringCap = TurbineRodPatternLogic.designBladeRingCap(
                rodPattern, rw, rd, websiteBladeRingCap < 0 ? Integer.MAX_VALUE : websiteBladeRingCap);
        int maxRing = TurbineBladePlacement.maxRing();
        for (int t = 0; t < layout.rodExtent(); t++) {
            int[] targets = new int[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                TurbineRodControllerLayout.Center col = columns.get(i);
                // Empty neighbor list = builder=2 / TurbineBuildLogic (no Chebyshev pre-cap).
                targets[i] = TurbineBladePlacement.effectiveTargetBladeRing(
                        layout, t, growth, col.rx(), col.rz(), List.of(), ringCap);
            }
            int[] placedPerRod = dryRunBuilderBlades(rw, rd, columns, targets, maxRing);
            int maxBladesThisLayer = 0;
            int layerBlades = 0;
            for (int i = 0; i < placedPerRod.length; i++) {
                int bladesPerRod = placedPerRod[i];
                maxBladesThisLayer = Math.max(maxBladesThisLayer, bladesPerRod);
                layerBlades += bladesPerRod;
                if (!requireBalanced || bladesPerRod % 4 == 0) {
                    validBladeItems += bladesPerRod;
                }
            }
            rodBlocks += rodsOnLayer;
            bladeItems += layerBlades;
            layerMaxBladesPerRod.add(maxBladesThisLayer);
        }

        return new RodBladeCounts(rodBlocks, bladeItems, validBladeItems, layerMaxBladesPerRod);
    }

    /**
     * Geometric dry-run of ModDecoded {@code bladeLayer(..., builder=2)}:
     * shared cross-section occupancy, wall-only ring cap, place until each rod hits {@code target*4} or stalls.
     * Grid values match the site: {@code -1} empty, {@code -2} rod, else owning rod cell index.
     *
     * @return blades placed per rod column (same order as {@code columns})
     */
    private static int[] dryRunBuilderBlades(
            int iw, int id,
            List<TurbineRodControllerLayout.Center> columns,
            int[] targets,
            int maxRing) {
        int n = columns.size();
        int[] placed = new int[n];
        if (n == 0 || iw <= 0 || id <= 0) {
            return placed;
        }
        int[] grid = new int[iw * id];
        java.util.Arrays.fill(grid, -1);
        int[] rodCell = new int[n];
        for (int i = 0; i < n; i++) {
            TurbineRodControllerLayout.Center c = columns.get(i);
            int cell = c.rx() * id + c.rz();
            rodCell[i] = cell;
            if (cell >= 0 && cell < grid.length) {
                grid[cell] = -2;
            }
        }
        // N,S,W,E — ModDecoded DIRS
        final int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int i = 0; i < n; i++) {
            int target = Math.max(0, Math.min(targets[i], maxRing));
            if (target <= 0) {
                continue;
            }
            int want = target * 4;
            int guard = want * 4 + 8;
            int rod = rodCell[i];
            while (placed[i] < want && guard-- > 0) {
                int cell = dryRunClick(grid, iw, id, rod, maxRing, dirs);
                if (cell < 0) {
                    break;
                }
                grid[cell] = rod;
                placed[i]++;
            }
        }
        return placed;
    }

    private static int dryRunClick(int[] grid, int iw, int id, int rod, int maxRing, int[][] dirs) {
        int ring = dryRunRingOf(grid, iw, id, rod, maxRing, dirs);
        if (ring > maxRing) {
            return -1;
        }
        int first = dryRunChoose(grid, iw, id, rod, maxRing, dirs);
        int[] tries = new int[5];
        int tryN = 0;
        if (first >= 0) {
            tries[tryN++] = first;
        }
        for (int d = 0; d < 4; d++) {
            if (d == first) {
                continue;
            }
            int cCheck = dryRunCellAt(rod, d, ring, iw, id, dirs);
            if (cCheck < 0 || dryRunIsBlade(grid, iw, id, cCheck, rod, d, dirs)) {
                continue;
            }
            if (ring == 1 || dryRunDepth(grid, iw, id, rod, d, maxRing, dirs) == ring - 1) {
                tries[tryN++] = d;
            }
        }
        for (int ti = 0; ti < tryN; ti++) {
            int c = dryRunCellAt(rod, tries[ti], ring, iw, id, dirs);
            if (c >= 0 && grid[c] == -1) {
                return c;
            }
        }
        return -1;
    }

    private static int dryRunChoose(int[] grid, int iw, int id, int rod, int maxRing, int[][] dirs) {
        int tot = dryRunTotal(grid, iw, id, rod, maxRing, dirs);
        if (tot >= maxRing * 4) {
            return -1;
        }
        int ring = dryRunRingOf(grid, iw, id, rod, maxRing, dirs);
        if (ring > maxRing) {
            return -1;
        }
        int[] missing = new int[4];
        int missingN = 0;
        for (int d = 0; d < 4; d++) {
            int c = dryRunCellAt(rod, d, ring, iw, id, dirs);
            if (!dryRunIsBlade(grid, iw, id, c, rod, d, dirs)) {
                missing[missingN++] = d;
            }
        }
        if (missingN == 0) {
            return -1;
        }
        if (tot == 1) {
            for (int d = 0; d < 4; d++) {
                if (dryRunDepth(grid, iw, id, rod, d, maxRing, dirs) == 1) {
                    int o = d ^ 1;
                    for (int mi = 0; mi < missingN; mi++) {
                        if (missing[mi] == o) {
                            return o;
                        }
                    }
                    break;
                }
            }
        }
        int min = Integer.MAX_VALUE;
        for (int mi = 0; mi < missingN; mi++) {
            min = Math.min(min, dryRunDepth(grid, iw, id, rod, missing[mi], maxRing, dirs));
        }
        int[] cand = new int[4];
        int candN = 0;
        for (int mi = 0; mi < missingN; mi++) {
            int d = missing[mi];
            if (dryRunDepth(grid, iw, id, rod, d, maxRing, dirs) == min) {
                cand[candN++] = d;
            }
        }
        return candN == 0 ? missing[0] : cand[tot % candN];
    }

    private static int dryRunRingOf(int[] grid, int iw, int id, int rod, int maxRing, int[][] dirs) {
        for (int k = 1; k <= maxRing; k++) {
            for (int d = 0; d < 4; d++) {
                int c = dryRunCellAt(rod, d, k, iw, id, dirs);
                if (!dryRunIsBlade(grid, iw, id, c, rod, d, dirs)) {
                    return k;
                }
            }
        }
        return maxRing + 1;
    }

    private static int dryRunTotal(int[] grid, int iw, int id, int rod, int maxRing, int[][] dirs) {
        int n = 0;
        for (int d = 0; d < 4; d++) {
            n += dryRunDepth(grid, iw, id, rod, d, maxRing, dirs);
        }
        return n;
    }

    private static int dryRunDepth(int[] grid, int iw, int id, int rod, int d, int maxRing, int[][] dirs) {
        int n = 0;
        while (n < maxRing) {
            int c = dryRunCellAt(rod, d, n + 1, iw, id, dirs);
            if (!dryRunIsBlade(grid, iw, id, c, rod, d, dirs)) {
                break;
            }
            n++;
        }
        return n;
    }

    /** Site owned-blade: facing from owner rod cell toward {@code cell} matches {@code d} (or opposite + aligned). */
    private static boolean dryRunIsBlade(int[] grid, int iw, int id, int cell, int rod, int d, int[][] dirs) {
        if (cell < 0 || cell >= grid.length || grid[cell] < 0) {
            return false;
        }
        int ownerCell = grid[cell];
        int dx = (cell / id) - (ownerCell / id);
        int f = dx != 0 ? (dx < 0 ? 2 : 3) : (cell % id < ownerCell % id ? 0 : 1);
        if (f == d) {
            return true;
        }
        return f == (d ^ 1) && dryRunAlignedRod(grid, iw, id, rod, d, dirs);
    }

    private static boolean dryRunAlignedRod(int[] grid, int iw, int id, int rod, int d, int[][] dirs) {
        int cap = Math.max(TurbineBladePlacement.maxRing() + 2, 8);
        for (int k = 1; k <= cap; k++) {
            int c = dryRunCellAt(rod, d, k, iw, id, dirs);
            if (c < 0) {
                return false;
            }
            if (grid[c] == -2) {
                return true;
            }
            // blade or air — keep scanning
        }
        return false;
    }

    private static int dryRunCellAt(int rod, int d, int k, int iw, int id, int[][] dirs) {
        int x = rod / id + dirs[d][0] * k;
        int z = rod % id + dirs[d][1] * k;
        if (x < 0 || z < 0 || x >= iw || z >= id) {
            return -1;
        }
        return x * id + z;
    }

    private static int interiorAlongPlacement(int w, int h, int d, TurbinePlacementAxis placement) {
        return switch (placement.facing().getAxis()) {
            case Y -> TurbineRodSpaceLayout.interiorHeight(h);
            case Z -> TurbineRodSpaceLayout.interiorDepth(d);
            case X -> TurbineRodSpaceLayout.interiorWidth(w);
            default -> TurbineRodSpaceLayout.interiorHeight(h);
        };
    }
}
