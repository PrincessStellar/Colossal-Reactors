package net.unfamily.colossal_reactors.client.gui;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.unfamily.colossal_reactors.coolant.CoolantDefinition;
import net.unfamily.colossal_reactors.reactor.ReactorSimulation;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Builder GUI simulation: same layout and formulas as {@link ReactorSimulation#simulateFromBuilderParams}
 * (identical RF and fuel formulas so GUI preview matches the real reactor).
 */
public final class ReactorBuilderSimulation {

    private ReactorBuilderSimulation() {}

    /**
     * Runs builder simulation with the same RF and fuel formulas as the real reactor.
     * Parameters must match ReactorBuilderBlockEntity (sizeLeft, sizeRight, sizeHeight, sizeDepth, rodPattern, patternMode, heatSinkIndex).
     */
    public static ReactorSimulation.SimulationResult run(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int rodPattern, int patternMode, int heatSinkIndex,
            @Nullable Identifier simulationFuelId,
            @Nullable CoolantDefinition coolantDef) {
        return run(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                rodPattern, patternMode, heatSinkIndex, simulationFuelId, coolantDef, null, null, null);
    }

    public static ReactorSimulation.SimulationResult run(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int rodPattern, int patternMode, int heatSinkIndex,
            @Nullable Identifier simulationFuelId,
            @Nullable CoolantDefinition coolantDef,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            @Nullable BiPredicate<Integer, Integer> websiteHeatSinkCell) {
        return run(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                rodPattern, patternMode, heatSinkIndex, simulationFuelId, coolantDef,
                websiteRodOverride, websiteHeatSinkCell, null);
    }

    public static ReactorSimulation.SimulationResult run(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int rodPattern, int patternMode, int heatSinkIndex,
            @Nullable Identifier simulationFuelId,
            @Nullable CoolantDefinition coolantDef,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            @Nullable BiPredicate<Integer, Integer> websiteHeatSinkCell,
            @Nullable BiFunction<Integer, Integer, Integer> websiteHeatSinkOption) {
        // Keep a single source of truth for formulas to avoid drift between GUI and real runtime.
        return ReactorSimulation.simulateFromBuilderParams(
                registryAccess,
                sizeLeft, sizeRight, sizeHeight, sizeDepth,
                rodPattern, patternMode, heatSinkIndex,
                simulationFuelId, coolantDef,
                websiteRodOverride, websiteHeatSinkCell, websiteHeatSinkOption
        );
    }
}
