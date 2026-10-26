package net.unfamily.colossal_reactors.client.gui;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.unfamily.colossal_reactors.turbine.TurbineRodPatternLogic;
import net.unfamily.colossal_reactors.turbine.TurbineSimulation;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/** Builder GUI simulation delegate. */
public final class TurbineBuilderSimulation {

    private TurbineBuilderSimulation() {}

    public static TurbineSimulation.SimulationResult run(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern, int coilIndex, int storedCoilSetting) {
        return run(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, null, null, false);
    }

    public static TurbineSimulation.SimulationResult run(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern, int coilIndex, int storedCoilSetting,
            @Nullable Identifier generationId) {
        return run(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, generationId,
                null, false, Integer.MAX_VALUE);
    }

    public static TurbineSimulation.SimulationResult run(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern, int coilIndex, int storedCoilSetting,
            @Nullable Identifier generationId,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile) {
        return run(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, generationId,
                websiteRodOverride, websiteClimbProfile, Integer.MAX_VALUE);
    }

    public static TurbineSimulation.SimulationResult run(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern, int coilIndex, int storedCoilSetting,
            @Nullable Identifier generationId,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap) {
        return run(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, generationId,
                websiteRodOverride, websiteClimbProfile, websiteBladeRingCap,
                TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static TurbineSimulation.SimulationResult run(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern, int coilIndex, int storedCoilSetting,
            @Nullable Identifier generationId,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap,
            int bladeVariant) {
        return TurbineSimulation.simulateFromBuilderParams(
                registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, generationId,
                websiteRodOverride, websiteClimbProfile, websiteBladeRingCap, bladeVariant);
    }
}
