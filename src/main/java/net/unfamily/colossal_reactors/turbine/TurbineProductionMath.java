package net.unfamily.colossal_reactors.turbine;

import net.unfamily.colossal_reactors.Config;
import org.jetbrains.annotations.Nullable;

/**
 * Shared steam/RF production formulas for validation, builder simulation, and runtime caps.
 */
public final class TurbineProductionMath {

    public record ProductionEstimate(
            int bladeCount,
            int validBladeCount,
            int coilBlockCount,
            double coilEfficiency,
            double bladeEfficiency,
            double maxSteamMbPerTick,
            double estimatedRfPerTick
    ) {}

    private TurbineProductionMath() {}

    public static ProductionEstimate compute(
            int bladeCount,
            int validBladeCount,
            int coilBlockCount,
            double coilEfficiency,
            double bladeEfficiency,
            @Nullable TurbineGenerationDefinition generation) {
        return compute(bladeCount, validBladeCount, coilBlockCount, coilEfficiency, bladeEfficiency, 1.0, generation);
    }

    public static ProductionEstimate compute(
            int bladeCount,
            int validBladeCount,
            int coilBlockCount,
            double coilEfficiency,
            double bladeEfficiency,
            double flowEfficiency,
            @Nullable TurbineGenerationDefinition generation) {
        double steam = validBladeCount * Config.TURBINE_STEAM_MB_PER_BLADE_PER_TICK.get()
                * Config.TURBINE_CONSUMPTION_MULTIPLIER.get()
                * Math.max(0.0, flowEfficiency);
        double rfPerMb = generation != null
                ? TurbineGenerationLoader.rfPerSteamMb(generation.rfProduction())
                : Config.TURBINE_DEFAULT_RF_PER_STEAM_MB.get();
        double rf = steam * rfPerMb * coilEfficiency * bladeEfficiency * Config.TURBINE_PRODUCTION_MULTIPLIER.get();
        rf = Math.max(rf, Config.TURBINE_MIN_RF_PER_TICK.get());
        return new ProductionEstimate(
                bladeCount,
                validBladeCount,
                coilBlockCount,
                coilEfficiency,
                bladeEfficiency,
                steam,
                rf);
    }

    /** Ideal multiblock from builder menu parameters (matches a correctly built turbine). */
    public static ProductionEstimate fromBuilderParams(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern,
            int coilIndex,
            int storedCoilSetting,
            @Nullable TurbineGenerationDefinition generation) {
        return fromBuilderParams(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, generation,
                null, false, Integer.MAX_VALUE);
    }

    public static ProductionEstimate fromBuilderParams(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern,
            int coilIndex,
            int storedCoilSetting,
            @Nullable TurbineGenerationDefinition generation,
            @Nullable java.util.function.BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile) {
        return fromBuilderParams(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, generation,
                websiteRodOverride, websiteClimbProfile, Integer.MAX_VALUE,
                TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static ProductionEstimate fromBuilderParams(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern,
            int coilIndex,
            int storedCoilSetting,
            @Nullable TurbineGenerationDefinition generation,
            @Nullable java.util.function.BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap) {
        return fromBuilderParams(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, generation,
                websiteRodOverride, websiteClimbProfile, websiteBladeRingCap,
                TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static ProductionEstimate fromBuilderParams(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern,
            int coilIndex,
            int storedCoilSetting,
            @Nullable TurbineGenerationDefinition generation,
            @Nullable java.util.function.BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap,
            int bladeVariant) {
        return fromBuilderParams(sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, coilIndex, storedCoilSetting, generation,
                websiteRodOverride, websiteClimbProfile, websiteBladeRingCap, bladeVariant);
    }

    public static ProductionEstimate fromBuilderParams(
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern,
            int coilIndex,
            int rodCoilIndex,
            int storedCoilSetting,
            @Nullable TurbineGenerationDefinition generation,
            @Nullable java.util.function.BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap,
            int bladeVariant) {
        TurbineBuilderMetrics.IdealBuildMetrics ideal = TurbineBuilderMetrics.idealBuildMetrics(
                sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, storedCoilSetting, rodPattern, coilIndex, rodCoilIndex,
                websiteRodOverride, websiteClimbProfile, websiteBladeRingCap, bladeVariant);
        TurbineGenerationDefinition gen = generation != null ? generation : TurbineGenerationLoader.getDefault();
        return compute(
                ideal.bladeCount(),
                ideal.validBladeCount(),
                ideal.coilBlockCount(),
                ideal.coilEfficiency(),
                ideal.bladeEfficiency(),
                ideal.flowEfficiency(),
                gen);
    }
}
