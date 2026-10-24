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

    private TurbineBuilderMetrics() {}

    public static Estimate fromShellSizes(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex) {
        return fromShellSizes(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, storedCoilSetting, rodPattern, coilIndex, null, false, Integer.MAX_VALUE);
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
                websiteRodOverride, websiteClimbProfile, Integer.MAX_VALUE);
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

        int w = sizeLeft + sizeRight + 1;
        int h = sizeHeight + 1;
        int d = sizeDepth + 1;
        TurbinePlacementAxis placement = TurbinePlacementAxis.fromIndex(placementAxisIndex);
        int interiorAlong = interiorAlongPlacement(w, h, d, placement);
        int appliedCoils = TurbineRodSpaceLayout.appliedCoilLayerCount(interiorAlong, storedCoilSetting);
        TurbineRotorLayout layout = TurbineRotorLayout.from(
                0, 0, 0, w - 1, h - 1, d - 1, w, h, d, appliedCoils, placement.facing());
        RodBladeCounts blades = countRodsAndBlades(
                layout, rodPattern, websiteRodOverride, websiteClimbProfile, websiteBladeRingCap);

        int coilBlocks = 0;
        if (!TurbineRodPatternLogic.skipsCoils(rodPattern)
                && !ElecCoilLoader.shouldSkipSolidCoilAutoPlacement(coilIndex)) {
            coilBlocks = layout.crossSizeA() * layout.crossSizeB() * appliedCoils;
        }

        return new Estimate(
                blades.rodBlocks(),
                blades.bladeItems(),
                coilBlocks,
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
            TurbineRotorLayout layout
    ) {}

    public static IdealBuildMetrics idealBuildMetrics(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int storedCoilSetting,
            int rodPattern,
            int coilIndex) {
        return idealBuildMetrics(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, storedCoilSetting, rodPattern, coilIndex, null, false, Integer.MAX_VALUE);
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
                websiteRodOverride, websiteClimbProfile, Integer.MAX_VALUE);
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

        int w = sizeLeft + sizeRight + 1;
        int h = sizeHeight + 1;
        int d = sizeDepth + 1;
        TurbinePlacementAxis placement = TurbinePlacementAxis.fromIndex(placementAxisIndex);
        int interiorAlong = interiorAlongPlacement(w, h, d, placement);
        int appliedCoils = TurbineRodSpaceLayout.appliedCoilLayerCount(interiorAlong, storedCoilSetting);
        TurbineRotorLayout layout = TurbineRotorLayout.from(
                0, 0, 0, w - 1, h - 1, d - 1, w, h, d, appliedCoils, placement.facing());
        RodBladeCounts blades = countRodsAndBlades(
                layout, rodPattern, websiteRodOverride, websiteClimbProfile, websiteBladeRingCap);

        int coilBlocks = 0;
        if (!TurbineRodPatternLogic.skipsCoils(rodPattern)
                && !ElecCoilLoader.shouldSkipSolidCoilAutoPlacement(coilIndex)) {
            coilBlocks = layout.crossSizeA() * layout.crossSizeB() * appliedCoils;
        }

        double coilEff;
        if (coilBlocks > 0 && coilIndex >= 0 && coilIndex < ElecCoilLoader.getAllDefinitions().size()) {
            ElecCoilDefinition coilDef = ElecCoilLoader.getAllDefinitions().get(coilIndex);
            coilEff = Math.min(coilDef.effCoe(), coilDef.effMax());
        } else {
            coilEff = Config.TURBINE_EMPTY_COIL_EFFICIENCY.get();
        }

        double bladeEff = TurbineBladeEfficiency.computeMultiplier(blades.layerMaxBladesPerRod());

        return new IdealBuildMetrics(
                blades.bladeItems(),
                blades.validBladeItems(),
                coilBlocks,
                coilEff,
                bladeEff,
                layout);
    }

    /**
     * Counts rods and blades like {@link TurbineBuildLogic}: one rod stack at the primary center column,
     * {@link TurbineBladePlacement#effectiveTargetBladeRing} × 4 blades on that rod per layer {@code t}.
     */
    public static RodBladeCounts countRodsAndBlades(TurbineRotorLayout layout, int rodPattern) {
        return countRodsAndBlades(layout, rodPattern, null, false, Integer.MAX_VALUE);
    }

    public static RodBladeCounts countRodsAndBlades(
            TurbineRotorLayout layout, int rodPattern,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile) {
        return countRodsAndBlades(layout, rodPattern, websiteRodOverride, websiteClimbProfile, Integer.MAX_VALUE);
    }

    public static RodBladeCounts countRodsAndBlades(
            TurbineRotorLayout layout, int rodPattern,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap) {
        int rodBlocks = 0;
        int bladeItems = 0;
        int validBladeItems = 0;
        List<Integer> layerMaxBladesPerRod = new ArrayList<>();
        boolean requireBalanced = Boolean.TRUE.equals(
                Config.TURBINE_REQUIRE_BALANCED_BLADE_RINGS.get());
        int rw = layout.crossSizeA();
        int rd = layout.crossSizeB();
        boolean hasRodColumn = rw > 0 && rd > 0;

        int bladePattern = TurbineRodPatternLogic.bladeGrowthPattern(rodPattern, websiteClimbProfile);
        List<TurbineRodControllerLayout.Center> columns = hasRodColumn
                ? TurbineRodPatternLogic.rodColumns(rw, rd, rodPattern, websiteRodOverride)
                : List.of();
        int rodsOnLayer = columns.size();
        int ringCap = websiteBladeRingCap < 0 ? Integer.MAX_VALUE : websiteBladeRingCap;
        for (int t = 0; t < layout.rodExtent(); t++) {
            int maxBladesThisLayer = 0;
            int layerBlades = 0;
            for (TurbineRodControllerLayout.Center col : columns) {
                int ring = TurbineBladePlacement.effectiveTargetBladeRing(
                        layout, t, bladePattern, col.rx(), col.rz(), columns, ringCap);
                int bladesPerRod = ring * 4;
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

    private static int interiorAlongPlacement(int w, int h, int d, TurbinePlacementAxis placement) {
        return switch (placement.facing().getAxis()) {
            case Y -> TurbineRodSpaceLayout.interiorHeight(h);
            case Z -> TurbineRodSpaceLayout.interiorDepth(d);
            case X -> TurbineRodSpaceLayout.interiorWidth(w);
            default -> TurbineRodSpaceLayout.interiorHeight(h);
        };
    }
}
