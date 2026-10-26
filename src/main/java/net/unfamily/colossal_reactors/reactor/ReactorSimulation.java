package net.unfamily.colossal_reactors.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.blockentity.HighCondPowerPortBlockEntity;
import net.unfamily.colossal_reactors.blockentity.PowerPortBlockEntity;
import net.unfamily.colossal_reactors.blockentity.ReactorPowerPort;
import net.unfamily.colossal_reactors.integration.mekanism.MekChemicalHelper;
import net.unfamily.colossal_reactors.blockentity.ReactorControllerBlockEntity;
import net.unfamily.colossal_reactors.blockentity.ReactorRodBlockEntity;
import net.unfamily.colossal_reactors.blockentity.ResourcePortBlockEntity;
import net.unfamily.colossal_reactors.blockentity.PortMode;
import net.unfamily.colossal_reactors.coolant.CoolantDefinition;
import net.unfamily.colossal_reactors.coolant.CoolantLoader;
import net.unfamily.colossal_reactors.heatsink.HeatSinkLoader;
import net.unfamily.colossal_reactors.integration.ReactorMeltdownIntegrations;
import net.unfamily.colossal_reactors.fuel.FuelDefinition;
import net.unfamily.colossal_reactors.fuel.FuelIo;
import net.unfamily.colossal_reactors.fuel.FuelLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import org.jetbrains.annotations.Nullable;

/**
 * Consumption and production cycle when reactor is ON: consume fuel from rods (produce solid waste),
 * produce RF and/or steam depending on coolant mode.
 * Formulas follow big_reactor style: base RF/MB, rod count, coolant modifiers, efficiency.
 *
 * <p><b>Water mode</b> (coolant = water / reduce_rf_production): RF converted to steam when fluid is sufficient
 * for full conversion to steam; only the unconverted part (insufficient water) is output as RF.
 * Example: 5x5 interior with 5 rods, water in rods → only steam (no RF). If water runs out → RF for the remainder.
 *
 * <p><b>Normal mode</b> (other coolants): RF to power ports only; steam conversion is defined per coolant in scripts (rf_to_coolant_factor, steam_per_coolant).
 *
 * <p><b>GUI simulation</b>: {@link #simulateFromBuilderParams} runs the same formulas without a level, assuming
 * a virtual reactor with the builder's dimensions/pattern and a single heat sink type everywhere. No consumption.
 */
public final class ReactorSimulation {

    private ReactorSimulation() {}

    /** Converts simulated RF/t to a non-negative long, saturating at {@link Long#MAX_VALUE}. */
    private static long doubleToPositiveLongRf(double rfProduced) {
        if (!Double.isFinite(rfProduced) || rfProduced <= 0) {
            return 0;
        }
        if (rfProduced >= (double) Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) rfProduced;
    }

    /** Normal (RF) mode: at least one power port has buffer space (no simulate insert). */
    private static boolean canAcceptRfFromReactor(List<ReactorPowerPort> powerPorts) {
        if (powerPorts.isEmpty()) {
            return false;
        }
        for (ReactorPowerPort port : powerPorts) {
            if (port.canAcceptMoreFromReactor()) {
                return true;
            }
        }
        return false;
    }

    private static long distributeRfToPowerPorts(long rfToPush, List<ReactorPowerPort> powerPorts) {
        if (rfToPush <= 0 || powerPorts.isEmpty()) {
            return 0L;
        }
        int n = powerPorts.size();
        long perPort = rfToPush / n;
        long remainder = rfToPush % n;
        long pushed = 0L;
        for (int i = 0; i < n; i++) {
            long offer = perPort + (i < remainder ? 1L : 0L);
            if (offer > 0) {
                pushed += powerPorts.get(i).receiveEnergyFromReactor(offer);
            }
        }
        return pushed;
    }

    private static double rodEnergyScaling(double effectiveRodCount) {
        double n = Math.max(0.0, effectiveRodCount);
        int mode = Config.ROD_ENERGY_SCALING_MODE.get();
        if (mode == 2) {
            double k = Math.max(1.0, Config.ROD_ENERGY_SCALING_SATURATION_K.get());
            return n / (1.0 + (n / k));
        }
        if (mode == 1) {
            double exp = Config.ROD_ENERGY_SCALING_EXPONENT.get();
            return Math.pow(n, exp);
        }
        return n * (Math.log(n + 1.0) / 2.3);
    }

    private static double rodFuelScaling(double effectiveRodCount) {
        double n = Math.max(0.0, effectiveRodCount);
        int mode = Config.ROD_FUEL_SCALING_MODE.get();
        if (mode == 2) {
            double k = Math.max(1.0, Config.ROD_FUEL_SCALING_SATURATION_K.get());
            return n / (1.0 + (n / k));
        }
        if (mode == 1) {
            double exp = Config.ROD_FUEL_SCALING_EXPONENT.get();
            return Math.pow(n, exp);
        }
        return n * (Math.log(n + 1.0) / 2.3);
    }

    /**
     * Base ratio of effective cooling to RF produced; large interiors require a higher ratio via {@link #stabilityRequiredCoolingRatio}.
     */
    private static final double STABILITY_COOLING_RATIO_THRESHOLD = 0.85;

    private static double stabilityRequiredCoolingRatio(int interiorBlockCount) {
        int v0 = Config.STABILITY_INTERIOR_VOLUME_BASELINE.get();
        int v1 = Config.STABILITY_INTERIOR_VOLUME_STRESS.get();
        if (interiorBlockCount <= v0 || v1 <= v0) {
            return STABILITY_COOLING_RATIO_THRESHOLD;
        }
        double t = (Math.log((double) interiorBlockCount) - Math.log((double) v0))
                / (Math.log((double) v1) - Math.log((double) v0));
        t = Mth.clamp(t, 0.0, 1.0);
        double maxExtra = Config.STABILITY_MAX_EXTRA_REQUIRED_RATIO.get();
        return Math.min(0.99, STABILITY_COOLING_RATIO_THRESHOLD + t * maxExtra);
    }

    /**
     * Longest interior edge in blocks (exclusive of 1-block shell), matching heat sink scan bounds [min+1, max-1).
     */
    private static int interiorMaxSpanBlocks(ReactorValidation.Result result) {
        int sx = result.maxX() - result.minX() - 1;
        int sy = result.maxY() - result.minY() - 1;
        int sz = result.maxZ() - result.minZ() - 1;
        return Math.max(1, Math.max(sx, Math.max(sy, sz)));
    }

    /**
     * Thermal stress vs interior span: {@code 1 + alpha * ((max(L,Lmin)/Lref)^gamma - 1)}.
     * Applied as a divisor on overheating-based cooling: larger reactors need higher datapack overheating to stay stable.
     */
    private static double thermalStressSizeFactor(double interiorMaxSpanBlocks) {
        if (!Boolean.TRUE.equals(Config.THERMAL_STRESS_FROM_SIZE_ENABLED.get())) return 1.0;
        double lRef = Config.THERMAL_STRESS_L_REF.get();
        double lMin = Config.THERMAL_STRESS_L_MIN.get();
        double gamma = Config.THERMAL_STRESS_GAMMA.get();
        double alpha = Config.THERMAL_STRESS_ALPHA.get();
        if (lRef <= 1e-9 || alpha <= 1e-12) return 1.0;
        double L = Math.max(interiorMaxSpanBlocks, lMin);
        double phi = Math.pow(L / lRef, gamma);
        return Math.max(1e-6, 1.0 + alpha * (phi - 1.0));
    }

    /** Max non-adjacent heat-sink cells that count toward modifiers: {@code 4 * rodCount}. */
    public static final int NON_ADJACENT_HEAT_SINK_PER_ROD = 4;

    /** Result of GUI simulation: stats that would be shown per tick (no actual consumption). */
    public record SimulationResult(
            int rodCount,
            int rodColumns,
            int coolantBlockCount,
            long rfPerTick,
            int steamPerTick,
            int coolantConsumedPerTick,
            int fuelPerTickHundredths,
            /** When instability is enabled in config: true if simulated cooling meets production (same threshold as runtime). */
            boolean stabilityCoolingSufficient
    ) {
        /** Fuel units/t divided by RF/t (consumption / production). 0 if either side is zero. */
        public double consumptionProductionRatio() {
            if (rfPerTick <= 0 || fuelPerTickHundredths <= 0) {
                return 0.0;
            }
            return (fuelPerTickHundredths / 100.0) / (double) rfPerTick;
        }
    }

    /**
     * Caps non-adjacent heat-sink contribution at {@code 4 * rodCount}, scaling sums proportionally.
     * @return capped {@code countNon}
     */
    public static int capNonAdjacentHeatSinks(
            int rodCount,
            int countNon,
            double[] sumFuelNon,
            double[] sumEnergyNon,
            double[] sumOverheatingNon) {
        int maxNon = Math.max(0, rodCount) * NON_ADJACENT_HEAT_SINK_PER_ROD;
        if (countNon <= maxNon || countNon <= 0) {
            return countNon;
        }
        double scale = (double) maxNon / (double) countNon;
        sumFuelNon[0] *= scale;
        sumEnergyNon[0] *= scale;
        sumOverheatingNon[0] *= scale;
        return maxNon;
    }

    /**
     * Runs one tick of consumption and production. Call when reactor is ON and valid.
     */
    public static void tick(ServerLevel level, ReactorControllerBlockEntity controller) {
        ReactorValidation.Result result = controller.getCachedResult();
        if (result == null || !result.valid()) return;

        if ((level.getGameTime() & 19L) == 0L) {
            net.unfamily.colossal_reactors.multiblock.MultiblockPortScaling.scaleReactorPorts(level, controller);
        }

        List<ReactorRodBlockEntity> rods = new ArrayList<>();
        List<ReactorPowerPort> powerPorts = new ArrayList<>();
        List<ResourcePortBlockEntity> resourcePorts = new ArrayList<>();

        // Use cached part positions to avoid scanning the whole reactor volume each tick.
        long[] rodPositions = controller.getCachedRodPositions();
        long[] powerPortPositions = controller.getCachedPowerPortPositions();
        long[] resourcePortPositions = controller.getCachedResourcePortPositions();
        if (rodPositions.length == 0 && (result.rodCount() > 0 || result.rodColumns() > 0)) {
            controller.rebuildPartCachesIfNeeded(level, result);
            rodPositions = controller.getCachedRodPositions();
            powerPortPositions = controller.getCachedPowerPortPositions();
            resourcePortPositions = controller.getCachedResourcePortPositions();
        }
        if (rodPositions.length == 0) {
            // Fallback (should be rare): rebuild from bounds.
            controller.rebuildPartCachesIfNeeded(level, result);
            rodPositions = controller.getCachedRodPositions();
            powerPortPositions = controller.getCachedPowerPortPositions();
            resourcePortPositions = controller.getCachedResourcePortPositions();
        }

        for (long p : rodPositions) {
            if (level.getBlockEntity(BlockPos.of(p)) instanceof ReactorRodBlockEntity rod) {
                rods.add(rod);
            }
        }
        for (long p : powerPortPositions) {
            if (level.getBlockEntity(BlockPos.of(p)) instanceof ReactorPowerPort port) {
                powerPorts.add(port);
            }
        }
        for (long p : resourcePortPositions) {
            if (level.getBlockEntity(BlockPos.of(p)) instanceof ResourcePortBlockEntity port) {
                resourcePorts.add(port);
            }
        }

        if (rods.isEmpty()) return;

        int rodCount = rods.size();
        double effectiveRodCount = controller.getCachedEffectiveRodCount();
        if (effectiveRodCount <= 1e-9) {
            effectiveRodCount = computeEffectiveRodCount(level, rods);
        }
        double totalFuelUnits = controller.getTotalFuelUnits();
        if (totalFuelUnits <= 0) return;

        double[] base = computeEffectiveBaseFromControllerFuel(controller);
        double baseRf = base[0];
        double baseFuelUnitsPerTick = base[1];
        double rfEfficiency = 1.0 - Config.RF_EFFICIENCY_LOSS.get();
        double fuelEfficiency = Config.FUEL_EFFICIENCY_LOSS.get();
        double productionMult = Config.PRODUCTION_MULTIPLIER.get();
        double consumptionMult = Config.CONSUMPTION_MULTIPLIER.get();

        CoolantDefinition coolantDef = controller.getCoolantDefinition(level.registryAccess());
        double rfMultiplier = coolantDef != null ? coolantDef.rfMultiplier() : 1.0;
        double mbMultiplier = coolantDef != null ? coolantDef.mbMultiplier() : 1.0;
        Fluid coolantFluidFromPorts = (coolantDef != null) ? CoolantLoader.getFirstFluidFromDefinition(coolantDef, level.registryAccess()) : null;

        ReactorControllerBlockEntity.HeatSinkStaticCache staticHeatSink = controller.getCachedHeatSinkStaticCache();
        HeatSinkLoader.HeatSinkModifiersResult heatSink = (staticHeatSink != null)
                ? computeHeatSinkModifiersFromStaticCache(level, staticHeatSink, rods.size(), coolantFluidFromPorts)
                : computeHeatSinkModifiers(level, result, rods.size(), rods, coolantFluidFromPorts);
        double heatSinkFuelMult = heatSink.fuelMultiplier();
        int countAdj = heatSink.countAdj();
        int countNon = heatSink.countNon();

        double decayRods = Math.max(0.0, Config.CONSUMPTION_CURVE_DECAY_RODS.get());
        double curveStrength = (decayRods <= 0) ? 1.0 : decayRods / (effectiveRodCount + decayRods);
        double curveStrengthAdjusted = 0.4 + 0.50 * curveStrength; // decay effect reduced by 50%
        double consumptionScale = Config.CONSUMPTION_SCALE.get() / Math.pow(effectiveRodCount + 1, 0.5 * curveStrengthAdjusted);
        double mbDivisor = (coolantDef != null && coolantDef.mbMultiplier() > 0) ? coolantDef.mbMultiplier() : 1.0;
        double consumptionDivisor = Math.max(0.1, Config.HEAT_SINK_CONSUMPTION_DIVISOR.get());
        double fuelRodFactor = rodFuelScaling(effectiveRodCount);
        double fuelConsumptionRate = baseFuelUnitsPerTick * consumptionMult * fuelEfficiency * fuelRodFactor * consumptionScale / mbDivisor / heatSinkFuelMult / consumptionDivisor;
        if (countAdj + countNon > 0) {
            fuelConsumptionRate *= Math.max(0.1, Config.HEAT_SINK_FUEL_UNITS_MULTIPLIER.get());
        }
        fuelConsumptionRate = Math.max(fuelConsumptionRate, Config.MIN_FUEL_UNITS_PER_TICK.get());

        double rfProduced = computeRfProducedDouble(level, controller, result, rodCount, effectiveRodCount, baseRf,
                productionMult, rfEfficiency, rfMultiplier, coolantFluidFromPorts, heatSink);

        // Water mode: coolant consumed for steam; RF reduced (only unconverted part). Active if def is water (by id) or has reduce_rf_production.
        boolean waterMode = coolantDef != null
                && (coolantDef.reduceRfProduction() || CoolantLoader.WATER_COOLANT_ID.equals(coolantDef.coolantId()));

        long rfPushedThisTick = 0;
        int steamProducedThisTick = 0;
        int waterConsumedThisTick = 0;
        double fuelUnitsConsumedThisTick = 0.0;

        List<ResourcePortBlockEntity> extractPorts = resourcePorts.stream()
                .filter(p -> p.getPortMode() == PortMode.EXTRACT)
                .toList();

        // Steam/coolant mode: run when we can export steam OR when we can fall back to RF.
        // Normal mode: run only when a power port can accept RF.
        boolean canRun;
        if (waterMode) {
            boolean canExportSteam = ResourcePortOutputRouter.canExportCoolantOutput(extractPorts, coolantDef, level.registryAccess());
            boolean canExportRf = canAcceptRfFromReactor(powerPorts);
            boolean canConsumeCoolant = controller.getTotalCoolantMb() > 0
                    && coolantDef != null
                    && coolantDef.inputs() != null
                    && !coolantDef.inputs().isEmpty();
            canRun = canExportRf || (canExportSteam && canConsumeCoolant);
        } else {
            canRun = canAcceptRfFromReactor(powerPorts);
        }
        if (!canRun) {
            controller.setLastTickStats(0, 0, 0, 0);
            return;
        }

        if (waterMode) {
            // Water mode: attempt steam conversion. RF is produced ONLY when steam conversion is limited by coolant shortage,
            // not when limited by steam output capacity (output space should throttle the reactor instead).
            List<String> coolantInputs = coolantDef.inputs();
            int steamOutputSpace = (int) Math.min(Integer.MAX_VALUE, ResourcePortOutputRouter.availableCoolantOutputSpaceMb(
                    extractPorts, coolantDef, level.registryAccess()));

            int desiredCoolantToConsumeMb = (int) (rfProduced * coolantDef.rfToCoolantFactor());
            long rfExportCapacity = availableRfExportCapacity(powerPorts);
            boolean canConsumeCoolant = controller.getTotalCoolantMb() > 0
                    && coolantInputs != null
                    && !coolantInputs.isEmpty();
            int effectiveDesiredCoolantMb = (desiredCoolantToConsumeMb > 0 && canConsumeCoolant)
                    ? Math.min(desiredCoolantToConsumeMb, Math.max(0, steamOutputSpace))
                    : 0;
            double utilization = (desiredCoolantToConsumeMb > 0)
                    ? Math.min(1.0, (double) effectiveDesiredCoolantMb / (double) desiredCoolantToConsumeMb)
                    : 0.0;

            double fuelUnitsToConsume = Math.min(fuelConsumptionRate * utilization, totalFuelUnits);
            if (fuelUnitsToConsume > 0) {
                consumeFuelFromController(controller, fuelUnitsToConsume, level.registryAccess());
                fuelUnitsConsumedThisTick = fuelUnitsToConsume;
            } else {
                controller.setLastTickStats(0, 0, 0, 0);
                return;
            }

            int maxConvertibleMb = effectiveDesiredCoolantMb;

            int totalDrained = 0;
            if (maxConvertibleMb > 0 && coolantInputs != null && !coolantInputs.isEmpty()) {
                totalDrained = controller.consumeCoolantMatching(coolantInputs, maxConvertibleMb);
            }
            waterConsumedThisTick = totalDrained;

            double steamMb = totalDrained * coolantDef.steamPerCoolant();
            int steamPerTick = (int) steamMb;
            steamProducedThisTick = steamPerTick;
            if (steamPerTick > 0) {
                String liquidOut = coolantDef.liquidOutputSelector();
                Fluid steamFluid = liquidOut.startsWith("#")
                        ? CoolantLoader.getFirstFluidFromTag(liquidOut, level.registryAccess())
                        : BuiltInRegistries.FLUID.get(ResourceLocation.tryParse(liquidOut));
                if (steamFluid != null && steamFluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                    ResourcePortOutputRouter.pushFluid(extractPorts, new FluidStack(steamFluid, steamPerTick), level.registryAccess());
                }
                String gasOut = coolantDef.gasOutputSelector();
                if (gasOut != null && MekChemicalHelper.isLoaded()) {
                    ResourceLocation chemId = ResourceLocation.tryParse(gasOut.startsWith("%") ? gasOut.substring(1) : gasOut);
                    if (chemId != null) {
                        Object gasStack = MekChemicalHelper.createStack(chemId, steamPerTick);
                        if (gasStack != null) {
                            ResourcePortOutputRouter.pushGas(extractPorts, gasStack, level.registryAccess());
                        }
                    }
                }
            }

            // RF fallback is only for coolant shortage against the EFFECTIVE desired amount (already capped by output space).
            double fractionNotConverted = (maxConvertibleMb > 0)
                    ? (1.0 - (double) totalDrained / (double) maxConvertibleMb)
                    : 1.0;
            long rfToPush = (long) (rfProduced * fractionNotConverted);
            if (rfToPush > rfExportCapacity) {
                rfToPush = rfExportCapacity;
            }
            if (rfToPush > 0 && !powerPorts.isEmpty()) {
                rfPushedThisTick = distributeRfToPowerPorts(rfToPush, powerPorts);
            }
        } else {
            // Normal mode: only RF (no steam). Steam is only produced in water mode from consumed coolant.
            long rfPerTick = doubleToPositiveLongRf(rfProduced);
            long rfExportCapacity = availableRfExportCapacity(powerPorts);
            long rfToPush = Math.min(rfPerTick, rfExportCapacity);
            double utilization = (rfPerTick > 0) ? ((double) rfToPush / (double) rfPerTick) : 0.0;

            double fuelUnitsToConsume = Math.min(fuelConsumptionRate * utilization, totalFuelUnits);
            if (fuelUnitsToConsume > 0) {
                consumeFuelFromController(controller, fuelUnitsToConsume, level.registryAccess());
                fuelUnitsConsumedThisTick = fuelUnitsToConsume;
            } else {
                controller.setLastTickStats(0, 0, 0, 0);
                return;
            }

            if (rfToPush > 0 && !powerPorts.isEmpty()) {
                rfPushedThisTick = distributeRfToPowerPorts(rfToPush, powerPorts);
            }
        }

        int fuelHundredths = (int) Math.round(fuelUnitsConsumedThisTick * 100);
        controller.setLastTickStats(rfPushedThisTick, steamProducedThisTick, waterConsumedThisTick, fuelHundredths);

        if (Config.REACTOR_UNSTABILITY.get()) {
            updateStability(level, controller, result, rfProduced, waterMode, waterConsumedThisTick,
                    coolantDef, heatSink.sumOverheatingAdj(), heatSink.sumOverheatingNon(), baseRf);
        }
    }

    /**
     * Pushes EJECT fuel/coolant and EXTRACT waste to resource ports. Runs every controller tick while the
     * multiblock is valid, independent of fuel level, RF export capacity, or redstone gate.
     */
    public static void flushResourcePorts(ServerLevel level, ReactorControllerBlockEntity controller) {
        ReactorValidation.Result result = controller.getCachedResult();
        if (result == null || !result.valid()) {
            return;
        }

        long[] resourcePortPositions = controller.getCachedResourcePortPositions();
        if (resourcePortPositions.length == 0) {
            controller.rebuildPartCachesIfNeeded(level, result);
            resourcePortPositions = controller.getCachedResourcePortPositions();
        }

        List<ResourcePortBlockEntity> resourcePorts = new ArrayList<>();
        for (long p : resourcePortPositions) {
            if (level.getBlockEntity(BlockPos.of(p)) instanceof ResourcePortBlockEntity port) {
                resourcePorts.add(port);
            }
        }
        if (resourcePorts.isEmpty()) {
            return;
        }

        pushEjectToPorts(controller, resourcePorts, level.registryAccess());
        pushWasteToPorts(controller, resourcePorts, level.registryAccess());
    }

    /** Gross RF/t the reactor would produce (matches {@link #tick} formulas). Used for port scaling. */
    public static long computeExpectedRfPerTick(
            ServerLevel level,
            ReactorControllerBlockEntity controller,
            ReactorValidation.Result result
    ) {
        if (level == null || result == null || !result.valid()) {
            return 0L;
        }
        long[] rodPositions = controller.getCachedRodPositions();
        if (rodPositions == null || rodPositions.length == 0) {
            return 0L;
        }
        double effectiveRodCount = controller.getCachedEffectiveRodCount();
        if (effectiveRodCount <= 1e-9) {
            return 0L;
        }
        double[] base = computeEffectiveBaseFromControllerFuel(controller);
        CoolantDefinition coolantDef = controller.getCoolantDefinition(level.registryAccess());
        double rfMultiplier = coolantDef != null ? coolantDef.rfMultiplier() : 1.0;
        Fluid coolantFluidFromPorts = (coolantDef != null)
                ? CoolantLoader.getFirstFluidFromDefinition(coolantDef, level.registryAccess()) : null;
        ReactorControllerBlockEntity.HeatSinkStaticCache staticHeatSink = controller.getCachedHeatSinkStaticCache();
        HeatSinkLoader.HeatSinkModifiersResult heatSink = (staticHeatSink != null)
                ? computeHeatSinkModifiersFromStaticCache(level, staticHeatSink, rodPositions.length, coolantFluidFromPorts)
                : new HeatSinkLoader.HeatSinkModifiersResult(1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0);
        double rfProduced = computeRfProducedDouble(level, controller, result, rodPositions.length, effectiveRodCount,
                base[0], Config.PRODUCTION_MULTIPLIER.get(), 1.0 - Config.RF_EFFICIENCY_LOSS.get(), rfMultiplier,
                coolantFluidFromPorts, heatSink);
        return doubleToPositiveLongRf(rfProduced);
    }

    private static double computeRfProducedDouble(
            ServerLevel level,
            ReactorControllerBlockEntity controller,
            ReactorValidation.Result result,
            int rodCount,
            double effectiveRodCount,
            double baseRf,
            double productionMult,
            double rfEfficiency,
            double rfMultiplier,
            @Nullable Fluid coolantFluidFromPorts,
            HeatSinkLoader.HeatSinkModifiersResult heatSink
    ) {
        double heatSinkEnergyMult = heatSink.energyMultiplier();
        int countAdj = heatSink.countAdj();
        int countNon = heatSink.countNon();
        double sumEnergyAdj = heatSink.sumEnergyAdj();
        double energyRodFactor = rodEnergyScaling(effectiveRodCount);

        double rfProduced;
        if (countAdj + countNon > 0 && effectiveRodCount > 0) {
            double adjEnergyAvg = (countAdj > 0) ? (sumEnergyAdj / (double) countAdj) : 1.0;
            double adjCoverage = Math.min(1.0, (double) countAdj / Math.max(1.0, (double) rodCount));
            double heatSinkRfFactor = adjEnergyAvg * adjCoverage;
            rfProduced = baseRf * productionMult * rfEfficiency * energyRodFactor * heatSinkRfFactor * rfMultiplier
                    * Math.max(0.1, Config.HEAT_SINK_RF_MULTIPLIER.get());
        } else {
            rfProduced = baseRf * productionMult * rfEfficiency * energyRodFactor * rfMultiplier * heatSinkEnergyMult;
        }
        return Math.max(rfProduced, Config.MIN_RF_PER_TICK.get());
    }

    private static long availableRfExportCapacity(List<ReactorPowerPort> powerPorts) {
        if (powerPorts == null || powerPorts.isEmpty()) {
            return 0L;
        }
        long cap = 0L;
        for (ReactorPowerPort port : powerPorts) {
            cap += port.availableSpaceLong();
        }
        return cap;
    }

    /** Approximate center of reactor interior: for even dimensions (e.g. 10 blocks) picks one of the central blocks (2x2 or 2x1). */
    private static BlockPos reactorInteriorCenter(ReactorValidation.Result result) {
        int cx = result.minX() + (result.maxX() - result.minX()) / 2;
        int cy = result.minY() + (result.maxY() - result.minY()) / 2;
        int cz = result.minZ() + (result.maxZ() - result.minZ()) / 2;
        return new BlockPos(cx, cy, cz);
    }

    /**
     * Overheating sums weighted like heat sink fuel/energy ({@link Config#HEAT_SINK_ADJACENT_WEIGHT} /
     * {@link Config#HEAT_SINK_NON_ADJACENT_WEIGHT}). Raw sums must not be added unweighted: non-adjacent cells
     * do not drive RF the same way as adjacent cells, and counting both at full strength made large reactors
     * falsely stable when the interior was filled with non-adjacent coolant.
     */
    private static double weightedOverheatingForStability(double sumOverheatingAdj, double sumOverheatingNon) {
        double wAdj = Config.HEAT_SINK_ADJACENT_WEIGHT.get();
        double wNon = Config.HEAT_SINK_NON_ADJACENT_WEIGHT.get();
        return sumOverheatingAdj * wAdj + sumOverheatingNon * wNon;
    }

    /**
     * Updates controller stability when reactor unstability is enabled.
     * Cooling uses overheating multipliers with adjacent/non-adjacent weights (same idea as heat sink averages).
     * Stability drops when cooling is insufficient; small reactors lose stability more slowly.
     */
    private static void updateStability(ServerLevel level, ReactorControllerBlockEntity controller, ReactorValidation.Result result,
            double rfProduced, boolean waterMode, int waterConsumedThisTick, CoolantDefinition coolantDef,
            double sumOverheatingAdj, double sumOverheatingNon, double baseRf) {
        int volume = (result.maxX() - result.minX() + 1) * (result.maxY() - result.minY() + 1) * (result.maxZ() - result.minZ() + 1);
        double thermalStress = thermalStressSizeFactor(interiorMaxSpanBlocks(result));
        double weightedOverheat = weightedOverheatingForStability(sumOverheatingAdj, sumOverheatingNon) / thermalStress;
        double heatSinkCoolingRF = baseRf * 0.5 * weightedOverheat;
        double fluidCoolingRF = 0;
        if (waterMode && coolantDef != null && coolantDef.rfToCoolantFactor() > 0) {
            fluidCoolingRF = (waterConsumedThisTick / coolantDef.rfToCoolantFactor()) * coolantDef.overheatingMultiplier();
            if (Boolean.TRUE.equals(Config.THERMAL_STRESS_SCALE_FLUID_COOLING.get())) {
                fluidCoolingRF /= thermalStress;
            }
        }
        double totalCooling = heatSinkCoolingRF + fluidCoolingRF;
        double ratio = (rfProduced > 0) ? (totalCooling / rfProduced) : 1.0;

        int current = controller.getStabilityPermille();
        double sizeFactor = Math.sqrt(volume) / 250.0 * 10.0;
        double requiredRatio = stabilityRequiredCoolingRatio(volume);
        if (ratio < requiredRatio) {
            // Undercooled: stability drops
            double drop = (requiredRatio - ratio) * sizeFactor;
            int dropPermille = Math.max(1, (int) Math.ceil(drop));
            int newVal = current - dropPermille;
            if (newVal <= 0) {
                // Meltdown: trigger at effective reactor center (approximate: for even dimensions picks one of the central blocks, e.g. 2x2 -> one block)
                BlockPos center = reactorInteriorCenter(result);
                int sizeX = result.maxX() - result.minX() + 1;
                int sizeY = result.maxY() - result.minY() + 1;
                int sizeZ = result.maxZ() - result.minZ() + 1;
                ReactorMeltdownIntegrations.triggerMeltdown(level, center, volume, sizeX, sizeY, sizeZ);
                newVal = 0;
            }
            controller.setStabilityPermille(Math.max(0, newVal));
        } else {
            // Recover at same scale as drop (so increase speed matches decrease speed)
            int addPermille = Math.max(1, (int) Math.ceil(sizeFactor));
            controller.setStabilityPermille(Math.min(Config.REACTOR_UNSTABILITY_MAX_STABILITY_PERMILLE.get(), current + addPermille));
        }
    }

    /**
     * Pushes input material back out to EJECT ports: fuel (from rod fuel units, converted at unitsPerFuel) and coolant.
     */
    private static void pushEjectToPorts(ReactorControllerBlockEntity controller, List<ResourcePortBlockEntity> resourcePorts, RegistryAccess registryAccess) {
        List<ResourcePortBlockEntity> ejectPorts = resourcePorts.stream()
                .filter(p -> p.getPortMode() == PortMode.EJECT)
                .toList();
        if (ejectPorts.isEmpty()) return;

        FuelIo.ejectFuelToPorts(controller, ejectPorts, registryAccess);
        // Eject coolant: move stored coolant back into EJECT ports.
        for (var entry : controller.getCoolantEntries()) {
            if (entry.mb() <= 0) continue;
            var fluid = BuiltInRegistries.FLUID.get(entry.fluidId());
            if (fluid == null || fluid == Fluids.EMPTY) continue;
            int toMove = entry.mb();
            int consumed = controller.consumeCoolant(fluid, toMove);
            if (consumed <= 0) continue;
            int remaining = consumed;
            for (ResourcePortBlockEntity port : ejectPorts) {
                if (remaining <= 0) break;
                if (!port.getPortFilter().acceptsCoolantRole()) continue;
                if (!port.isAllowLiquid() || port.isAllowGas()) continue;
                int filled = port.receiveFluidFromReactor(new FluidStack(fluid, remaining));
                remaining -= filled;
            }
            if (remaining > 0) {
                controller.addCoolant(fluid, remaining);
            }
        }
    }

    /**
     * Pushes solid waste and liquid waste (steam) from rods to resource ports in EXTRACT mode.
     */
    private static void pushWasteToPorts(ReactorControllerBlockEntity controller, List<ResourcePortBlockEntity> resourcePorts, RegistryAccess registryAccess) {
        if (resourcePorts.isEmpty()) return;

        List<ResourcePortBlockEntity> extractPorts = resourcePorts.stream()
                .filter(p -> p.getPortMode() == PortMode.EXTRACT)
                .toList();
        if (extractPorts.isEmpty()) return;

        FuelIo.pushWasteToExtractPorts(controller, extractPorts, registryAccess);
        // Liquid waste (steam) is pushed directly to EXTRACT/EJECT ports in water mode; no rod liquid waste.
    }

    /**
     * Fuel/energy multipliers from interior with adjacency influence: coolant adjacent to a rod (Refrigerante 0)
     * and coolant not adjacent (Refrigerante 1) are weighted separately; each cell counted once (no double-count).
     * Rod cells use their coolant fluid modifier; adjacent/non-adjacent coolant use block modifier with weights wAdj/wNon.
     */
    private static HeatSinkLoader.HeatSinkModifiersResult computeHeatSinkModifiers(ServerLevel level, ReactorValidation.Result result, int rodsFromList, List<ReactorRodBlockEntity> rods, Fluid coolantFluidFromPorts) {
        int minX = result.minX(), minY = result.minY(), minZ = result.minZ();
        int maxX = result.maxX(), maxY = result.maxY(), maxZ = result.maxZ();
        Set<BlockPos> rodPositions = new HashSet<>();
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = minY + 1; y < maxY; y++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(ModBlocks.REACTOR_ROD.get())) {
                        rodPositions.add(pos);
                    }
                }
            }
        }
        double sumFuelRod = 0, sumEnergyRod = 0;
        int countRod = 0;
        double sumFuelAdj = 0, sumEnergyAdj = 0, sumOverheatingAdj = 0;
        int countAdj = 0;
        double sumFuelNon = 0, sumEnergyNon = 0, sumOverheatingNon = 0;
        int countNon = 0;
        var reg = level.registryAccess();
        double wAdj = Config.HEAT_SINK_ADJACENT_WEIGHT.get();
        double wNon = Config.HEAT_SINK_NON_ADJACENT_WEIGHT.get();
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = minY + 1; y < maxY; y++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(ModBlocks.REACTOR_ROD.get())) {
                        HeatSinkLoader.HeatSinkModifiers m = HeatSinkLoader.getModifiersForFluidOrDefault(coolantFluidFromPorts, reg);
                        sumFuelRod += m.fuelMultiplier();
                        sumEnergyRod += m.energyMultiplier();
                        countRod++;
                    } else {
                        HeatSinkLoader.HeatSinkModifiers m = HeatSinkLoader.getModifiersForBlockOrDefault(level.getBlockState(pos), reg);
                        boolean adjacentToRod = false;
                        for (Direction d : Direction.values()) {
                            if (rodPositions.contains(pos.relative(d))) {
                                adjacentToRod = true;
                                break;
                            }
                        }
                        if (adjacentToRod) {
                            sumFuelAdj += m.fuelMultiplier();
                            sumEnergyAdj += m.energyMultiplier();
                            sumOverheatingAdj += m.overheatingMultiplier();
                            countAdj++;
                        } else {
                            sumFuelNon += m.fuelMultiplier();
                            sumEnergyNon += m.energyMultiplier();
                            sumOverheatingNon += m.overheatingMultiplier();
                            countNon++;
                        }
                    }
                }
            }
        }
        double[] fuelNon = {sumFuelNon};
        double[] energyNon = {sumEnergyNon};
        double[] overheatNon = {sumOverheatingNon};
        countNon = capNonAdjacentHeatSinks(countRod, countNon, fuelNon, energyNon, overheatNon);
        sumFuelNon = fuelNon[0];
        sumEnergyNon = energyNon[0];
        sumOverheatingNon = overheatNon[0];
        double totalWeightedFuel = sumFuelRod + sumFuelAdj * wAdj + sumFuelNon * wNon;
        double totalWeightedEnergy = sumEnergyRod + sumEnergyAdj * wAdj + sumEnergyNon * wNon;
        double totalWeight = countRod + countAdj * wAdj + countNon * wNon;
        if (totalWeight <= 0) return new HeatSinkLoader.HeatSinkModifiersResult(1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0);
        double effFuel = totalWeightedFuel / totalWeight;
        double effEnergy = totalWeightedEnergy / totalWeight;
        if (Boolean.TRUE.equals(Config.REACTOR_SIMULATION_DEBUG.get())) {
            String coolantStr = (coolantFluidFromPorts != null && coolantFluidFromPorts != Fluids.EMPTY) ? BuiltInRegistries.FLUID.getKey(coolantFluidFromPorts).toString() : "none";
            ColossalReactors.LOGGER.info("[ReactorSimulation] Heat sink: rod(scan)={} rod(list)={} coolantFromPorts={} adj={} nonAdj={} sumEnergyAdj={} sumFuelAdj={} wAdj={} wNon={} => fuelMult={} energyMult={}",
                    countRod, rodsFromList, coolantStr, countAdj, countNon, sumEnergyAdj, sumFuelAdj, wAdj, wNon, effFuel, effEnergy);
        }
        return new HeatSinkLoader.HeatSinkModifiersResult(effFuel, effEnergy, sumEnergyAdj, sumFuelAdj, sumEnergyNon, sumFuelNon, sumOverheatingAdj, sumOverheatingNon, countAdj, countNon);
    }

    /**
     * Same as {@link #computeHeatSinkModifiers(ServerLevel, ReactorValidation.Result, int, List, Fluid)} but uses
     * controller-cached interior scan results (block sums + adjacency counts). Only the rod-fluid modifier depends
     * on the current coolant fluid from ports.
     */
    private static HeatSinkLoader.HeatSinkModifiersResult computeHeatSinkModifiersFromStaticCache(
            ServerLevel level,
            ReactorControllerBlockEntity.HeatSinkStaticCache cache,
            int rodsFromList,
            @Nullable Fluid coolantFluidFromPorts
    ) {
        var reg = level.registryAccess();
        double wAdj = Config.HEAT_SINK_ADJACENT_WEIGHT.get();
        double wNon = Config.HEAT_SINK_NON_ADJACENT_WEIGHT.get();

        HeatSinkLoader.HeatSinkModifiers rodM = HeatSinkLoader.getModifiersForFluidOrDefault(
                coolantFluidFromPorts != null ? coolantFluidFromPorts : Fluids.EMPTY,
                reg
        );
        double sumFuelRod = (double) cache.countRod() * rodM.fuelMultiplier();
        double sumEnergyRod = (double) cache.countRod() * rodM.energyMultiplier();

        double[] fuelNon = {cache.sumFuelNon()};
        double[] energyNon = {cache.sumEnergyNon()};
        double[] overheatNon = {cache.sumOverheatingNon()};
        int countNon = capNonAdjacentHeatSinks(cache.countRod(), cache.countNon(), fuelNon, energyNon, overheatNon);

        double totalWeightedFuel = sumFuelRod + cache.sumFuelAdj() * wAdj + fuelNon[0] * wNon;
        double totalWeightedEnergy = sumEnergyRod + cache.sumEnergyAdj() * wAdj + energyNon[0] * wNon;
        double totalWeight = cache.countRod() + cache.countAdj() * wAdj + countNon * wNon;
        if (totalWeight <= 0) {
            return new HeatSinkLoader.HeatSinkModifiersResult(1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0);
        }
        double effFuel = totalWeightedFuel / totalWeight;
        double effEnergy = totalWeightedEnergy / totalWeight;

        if (Boolean.TRUE.equals(Config.REACTOR_SIMULATION_DEBUG.get())) {
            String coolantStr = (coolantFluidFromPorts != null && coolantFluidFromPorts != Fluids.EMPTY)
                    ? BuiltInRegistries.FLUID.getKey(coolantFluidFromPorts).toString()
                    : "none";
            ColossalReactors.LOGGER.info("[ReactorSimulation] Heat sink (cached): rod(scan)={} rod(list)={} coolantFromPorts={} adj={} nonAdj={} sumEnergyAdj={} sumFuelAdj={} wAdj={} wNon={} => fuelMult={} energyMult={}",
                    cache.countRod(), rodsFromList, coolantStr, cache.countAdj(), countNon,
                    cache.sumEnergyAdj(), cache.sumFuelAdj(), wAdj, wNon, effFuel, effEnergy);
        }

        return new HeatSinkLoader.HeatSinkModifiersResult(
                effFuel,
                effEnergy,
                cache.sumEnergyAdj(),
                cache.sumFuelAdj(),
                energyNon[0],
                fuelNon[0],
                cache.sumOverheatingAdj(),
                overheatNon[0],
                cache.countAdj(),
                countNon
        );
    }

    /**
     * Effective rod count for production/consumption: each rod contributes less when it has horizontal
     * neighbors that are another rod or a border block (casing, glass, ports). Only horizontal (same Y) is checked.
     */
    private static double computeEffectiveRodCount(ServerLevel level, List<ReactorRodBlockEntity> rods) {
        if (rods.isEmpty()) return 0;
        Set<BlockPos> rodPositions = new HashSet<>();
        for (ReactorRodBlockEntity rod : rods) {
            rodPositions.add(rod.getBlockPos());
        }
        double penalty = Config.ROD_ADJACENCY_PENALTY.get();
        double effective = 0;
        for (ReactorRodBlockEntity rod : rods) {
            BlockPos pos = rod.getBlockPos();
            int adjacentCount = 0;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = pos.relative(dir);
                if (rodPositions.contains(neighbor) || ReactorValidation.isShellBlock(level.getBlockState(neighbor))) {
                    adjacentCount++;
                }
            }
            double mult = Math.max(0.0, 1.0 - penalty * adjacentCount);
            effective += mult;
        }
        return effective;
    }

    /** Coolant definition from INSERT ports (fluid in tank). Prefers water when present. */
    private static CoolantDefinition getCoolantModifierFromPorts(List<ResourcePortBlockEntity> resourcePorts, net.minecraft.core.RegistryAccess registryAccess) {
        CoolantDefinition waterDef = CoolantLoader.get(CoolantLoader.WATER_COOLANT_ID);
        for (ResourcePortBlockEntity port : resourcePorts) {
            if (port.getPortMode() != PortMode.INSERT) continue;
            var stack = port.getFluidTank().getFluid();
            if (stack.isEmpty()) continue;
            CoolantDefinition def = CoolantLoader.getDefinitionForFluid(stack.getFluid(), registryAccess);
            if (def != null) {
                if (CoolantLoader.WATER_COOLANT_ID.equals(def.coolantId())) return waterDef != null ? waterDef : def;
            }
        }
        for (ResourcePortBlockEntity port : resourcePorts) {
            if (port.getPortMode() != PortMode.INSERT) continue;
            var stack = port.getFluidTank().getFluid();
            if (stack.isEmpty()) continue;
            CoolantDefinition def = CoolantLoader.getDefinitionForFluid(stack.getFluid(), registryAccess);
            if (def != null) return def;
        }
        return CoolantLoader.get(CoolantLoader.WATER_COOLANT_ID);
    }

    /** Weighted average of base_rf_per_tick and base_fuel_units_per_tick from fuel definitions in rods (by fuel units). */
    private static double[] computeEffectiveBaseFromRods(List<ReactorRodBlockEntity> rods) {
        double sumRf = 0;
        double sumFuel = 0;
        double totalUnits = 0;
        for (ReactorRodBlockEntity rod : rods) {
            for (ReactorRodBlockEntity.FuelEntry e : rod.getFuelEntries()) {
                FuelDefinition def = FuelLoader.get(e.id());
                if (def == null) continue;
                double u = e.units();
                sumRf += u * def.baseRfPerTick();
                sumFuel += u * def.baseFuelUnitsPerTick();
                totalUnits += u;
            }
        }
        if (totalUnits <= 0) return new double[] { 200.0, 0.03 };
        return new double[] { sumRf / totalUnits, sumFuel / totalUnits };
    }

    /** Weighted average of base_rf_per_tick and base_fuel_units_per_tick from controller fuel entries (by fuel units). */
    private static double[] computeEffectiveBaseFromControllerFuel(ReactorControllerBlockEntity controller) {
        double sumRf = 0;
        double sumFuel = 0;
        double totalUnits = 0;
        for (var e : controller.getFuelEntries()) {
            FuelDefinition def = FuelLoader.get(e.id());
            if (def == null) continue;
            double u = e.units();
            sumRf += u * def.baseRfPerTick();
            sumFuel += u * def.baseFuelUnitsPerTick();
            totalUnits += u;
        }
        if (totalUnits <= 0) return new double[] { 200.0, 0.03 };
        return new double[] { sumRf / totalUnits, sumFuel / totalUnits };
    }

    private static void consumeFuelFromRods(List<ReactorRodBlockEntity> rods, double totalUnitsToConsume, net.minecraft.core.RegistryAccess registryAccess) {
        double remaining = totalUnitsToConsume;
        int rodIndex = 0;
        while (remaining > 1e-6 && rodIndex < rods.size() * 2) {
            ReactorRodBlockEntity rod = rods.get(rodIndex % rods.size());
            var entries = rod.getFuelEntries();
            if (entries.isEmpty()) {
                rodIndex++;
                continue;
            }
            ReactorRodBlockEntity.FuelEntry first = entries.get(0);
            FuelDefinition def = FuelLoader.get(first.id());
            if (def == null) {
                rodIndex++;
                continue;
            }
            float take = (float) Math.min(remaining, first.units());
            float consumed = rod.consumeFuel(first.id(), take);
            remaining -= consumed;
            if (consumed > 0) {
                float wasteUnits = def.wasteUnitsFromFuelConsumed(consumed);
                rod.recordConsumedAndAddWaste(def.wasteId(), wasteUnits, def.unitsPerWaste(), def.produce());
            }
            rodIndex++;
        }
    }

    private static void consumeFuelFromController(ReactorControllerBlockEntity controller, double totalUnitsToConsume, net.minecraft.core.RegistryAccess registryAccess) {
        double remaining = totalUnitsToConsume;
        int guard = 0;
        while (remaining > 1e-6 && guard++ < 64) {
            var entries = controller.getFuelEntries();
            if (entries.isEmpty()) break;
            ReactorControllerBlockEntity.FuelEntry first = entries.get(0);
            FuelDefinition def = FuelLoader.get(first.id());
            if (def == null) {
                remaining -= Math.min(remaining, first.units());
                continue;
            }
            float take = (float) Math.min(remaining, first.units());
            float consumed = controller.consumeFuel(first.id(), take);
            remaining -= consumed;
            if (consumed > 0) {
                controller.addWasteUnits(def.wasteId(), def.wasteUnitsFromFuelConsumed(consumed));
            }
        }
    }

    /**
     * Simulates one tick of reactor stats from builder parameters only (no level, no consumption).
     * Parameters must match ReactorBuilderBlockEntity (sizeLeft, sizeRight, sizeHeight, sizeDepth, rodPattern, patternMode, selectedHeatSinkIndex)
     * and the same layout as ReactorBuildLogic / RodPatternLogic so the simulated reactor is exactly what would be built.
     * Assumes all heat sink positions use the same heat sink type (heatSinkIndex). Coolant = selected simulation coolant or null for "none".
     */
    public static SimulationResult simulateFromBuilderParams(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int rodPattern, int patternMode, int heatSinkIndex,
            @Nullable ResourceLocation simulationFuelId,
            @Nullable CoolantDefinition coolantDef) {
        return simulateFromBuilderParams(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                rodPattern, patternMode, heatSinkIndex, simulationFuelId, coolantDef, null, null, null);
    }

    /**
     * Like {@link #simulateFromBuilderParams(RegistryAccess, int, int, int, int, int, int, int, ResourceLocation, CoolantDefinition)}
     * with optional Website rod/heat-sink masks from a decoded design seed.
     */
    public static SimulationResult simulateFromBuilderParams(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int rodPattern, int patternMode, int heatSinkIndex,
            @Nullable ResourceLocation simulationFuelId,
            @Nullable CoolantDefinition coolantDef,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            @Nullable BiPredicate<Integer, Integer> websiteHeatSinkCell) {
        return simulateFromBuilderParams(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                rodPattern, patternMode, heatSinkIndex, simulationFuelId, coolantDef,
                websiteRodOverride, websiteHeatSinkCell, null);
    }

    /**
     * Website simulation with optional per-cell heat-sink option indices (air = 0, gold = …).
     * When {@code websiteHeatSinkOption} is set, modifiers are accumulated per cell like runtime /
     * moddecoded; otherwise all counted sinks use {@code heatSinkIndex}.
     */
    public static SimulationResult simulateFromBuilderParams(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int rodPattern, int patternMode, int heatSinkIndex,
            @Nullable ResourceLocation simulationFuelId,
            @Nullable CoolantDefinition coolantDef,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride,
            @Nullable BiPredicate<Integer, Integer> websiteHeatSinkCell,
            @Nullable BiFunction<Integer, Integer, Integer> websiteHeatSinkOption) {
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

        Set<Long> rodSet = new HashSet<>();
        Set<Long> rodColumnSet = new HashSet<>();
        int rodCount = 0;
        for (int rx = 0; rx < rw; rx++) {
            for (int ry = 0; ry < rh; ry++) {
                for (int rz = 0; rz < rd; rz++) {
                    if (!isSimRodForPreview(rx, ry, rz, rw, rh, rd, rodPattern, expansionRodAtCenter, websiteRodOverride)) {
                        continue;
                    }
                    rodSet.add(key(rx, ry, rz));
                    rodColumnSet.add(((long) rx << 8) | (rz & 0xFF));
                    rodCount++;
                }
            }
        }
        int rodColumns = rodColumnSet.size();

        // Heat sink counted over FULL interior (1..w-2, 1..h-2, 1..d-2), matching ReactorBuildLogic coolant placement.
        boolean perCellWebsite = websiteHeatSinkOption != null;
        int countAdj = 0, countNon = 0;
        double sumFuelAdjAcc = 0, sumEnergyAdjAcc = 0, sumOverheatingAdjAcc = 0;
        double sumFuelNonAcc = 0, sumEnergyNonAcc = 0, sumOverheatingNonAcc = 0;
        if (rodPattern != RodPatternLogic.PATTERN_NONE) {
            for (int lx = 1; lx < w - 1; lx++) {
                for (int ly = 1; ly < h - 1; ly++) {
                    for (int lz = 1; lz < d - 1; lz++) {
                        if (isSimInteriorCellRod(lx, ly, lz, w, h, d, insetXZ, rodSet)) continue;
                        boolean adjacentToRod;
                        int cellHsOption = heatSinkIndex;
                        if (rodPattern == RodPatternLogic.PATTERN_WEBSITE) {
                            if (websiteHeatSinkCell == null) continue;
                            if (!isSimInRodSpace(lx, ly, lz, w, h, d, insetXZ)) continue;
                            int rx = lx - insetXZ;
                            int rz = lz - insetXZ;
                            if (!websiteHeatSinkCell.test(rx, rz)) continue;
                            adjacentToRod = isSimInteriorCellAdjacentToRod(lx, ly, lz, w, h, d, insetXZ, rodSet);
                            if (perCellWebsite) {
                                Integer opt = websiteHeatSinkOption.apply(rx, rz);
                                cellHsOption = opt != null ? opt : 0;
                            }
                        } else if (rodPattern == RodPatternLogic.PATTERN_CUSTOM) {
                            adjacentToRod = false;
                        } else if (patternMode == RodPatternLogic.MODE_SUPER_ECONOMY) {
                            if (!isSimInRodSpace(lx, ly, lz, w, h, d, insetXZ)) continue;
                            adjacentToRod = isSimRodSpaceCellAdjacentToRod(lx, ly, lz, w, h, d, insetXZ, rodSet);
                            if (!adjacentToRod) continue;
                        } else {
                            adjacentToRod = isSimInteriorCellAdjacentToRod(lx, ly, lz, w, h, d, insetXZ, rodSet);
                            if (patternMode == RodPatternLogic.MODE_ECONOMY && !adjacentToRod) continue;
                        }
                        if (perCellWebsite) {
                            HeatSinkLoader.HeatSinkModifiers cellMod =
                                    HeatSinkLoader.getModifiersForHeatSinkIndex(registryAccess, cellHsOption);
                            if (adjacentToRod) {
                                countAdj++;
                                sumFuelAdjAcc += cellMod.fuelMultiplier();
                                sumEnergyAdjAcc += cellMod.energyMultiplier();
                                sumOverheatingAdjAcc += cellMod.overheatingMultiplier();
                            } else {
                                countNon++;
                                sumFuelNonAcc += cellMod.fuelMultiplier();
                                sumEnergyNonAcc += cellMod.energyMultiplier();
                                sumOverheatingNonAcc += cellMod.overheatingMultiplier();
                            }
                        } else if (adjacentToRod) {
                            countAdj++;
                        } else {
                            countNon++;
                        }
                    }
                }
            }
        }
        {
            double[] fuelNon = {sumFuelNonAcc};
            double[] energyNon = {sumEnergyNonAcc};
            double[] overheatNon = {sumOverheatingNonAcc};
            countNon = capNonAdjacentHeatSinks(rodCount, countNon, fuelNon, energyNon, overheatNon);
            sumFuelNonAcc = fuelNon[0];
            sumEnergyNonAcc = energyNon[0];
            sumOverheatingNonAcc = overheatNon[0];
        }
        int coolantBlockCount = countAdj + countNon;

        // Match runtime computeEffectiveRodCount(): horizontal neighbors are rods OR shell blocks.
        // In simulation we have no world blocks, so shell adjacency is derived from interior boundaries.
        double effectiveRodCount = 0;
        double penalty = Config.ROD_ADJACENCY_PENALTY.get();
        for (int rx = 0; rx < rw; rx++) {
            for (int ry = 0; ry < rh; ry++) {
                for (int rz = 0; rz < rd; rz++) {
                    if (!rodSet.contains(key(rx, ry, rz))) continue;
                    int adjacentCount = 0;

                    // Rod-space coords -> interior coords used by runtime logic.
                    int lx = rx + insetXZ;
                    int lz = rz + insetXZ;

                    // West
                    if (rx > 0 && rodSet.contains(key(rx - 1, ry, rz))) {
                        adjacentCount++;
                    } else if (lx - 1 < 1) {
                        adjacentCount++; // shell block
                    }
                    // East
                    if (rx < rw - 1 && rodSet.contains(key(rx + 1, ry, rz))) {
                        adjacentCount++;
                    } else if (lx + 1 > w - 2) {
                        adjacentCount++; // shell block
                    }
                    // North
                    if (rz > 0 && rodSet.contains(key(rx, ry, rz - 1))) {
                        adjacentCount++;
                    } else if (lz - 1 < 1) {
                        adjacentCount++; // shell block
                    }
                    // South
                    if (rz < rd - 1 && rodSet.contains(key(rx, ry, rz + 1))) {
                        adjacentCount++;
                    } else if (lz + 1 > d - 2) {
                        adjacentCount++; // shell block
                    }

                    effectiveRodCount += Math.max(0.0, 1.0 - penalty * adjacentCount);
                }
            }
        }

        if (rodCount == 0) {
            return new SimulationResult(0, 0, coolantBlockCount, 0, 0, 0, 0, true);
        }

        Fluid coolantFluidFromPorts = (coolantDef != null)
                ? CoolantLoader.getFirstFluidFromDefinition(coolantDef, registryAccess) : null;
        HeatSinkLoader.HeatSinkModifiers rodMod = HeatSinkLoader.getModifiersForFluidOrDefault(coolantFluidFromPorts, registryAccess);
        HeatSinkLoader.HeatSinkModifiers heatSinkMod = HeatSinkLoader.getModifiersForHeatSinkIndex(registryAccess, heatSinkIndex);
        double wAdj = Config.HEAT_SINK_ADJACENT_WEIGHT.get();
        double wNon = Config.HEAT_SINK_NON_ADJACENT_WEIGHT.get();
        double sumFuelRod = rodCount * rodMod.fuelMultiplier();
        double sumEnergyRod = rodCount * rodMod.energyMultiplier();
        double sumFuelAdj = perCellWebsite ? sumFuelAdjAcc : countAdj * heatSinkMod.fuelMultiplier();
        double sumEnergyAdj = perCellWebsite ? sumEnergyAdjAcc : countAdj * heatSinkMod.energyMultiplier();
        double sumFuelNon = perCellWebsite ? sumFuelNonAcc : countNon * heatSinkMod.fuelMultiplier();
        double sumEnergyNon = perCellWebsite ? sumEnergyNonAcc : countNon * heatSinkMod.energyMultiplier();
        double totalWeightedFuel = sumFuelRod + sumFuelAdj * wAdj + sumFuelNon * wNon;
        double totalWeightedEnergy = sumEnergyRod + sumEnergyAdj * wAdj + sumEnergyNon * wNon;
        double totalWeight = rodCount + countAdj * wAdj + countNon * wNon;
        double heatSinkFuelMult = (totalWeight > 0) ? (totalWeightedFuel / totalWeight) : 1.0;
        double heatSinkEnergyMult = (totalWeight > 0) ? (totalWeightedEnergy / totalWeight) : 1.0;
        double sumOverheatingAdj = perCellWebsite ? sumOverheatingAdjAcc : countAdj * heatSinkMod.overheatingMultiplier();
        double sumOverheatingNon = perCellWebsite ? sumOverheatingNonAcc : countNon * heatSinkMod.overheatingMultiplier();

        double rfMultiplier = coolantDef != null ? coolantDef.rfMultiplier() : 1.0;
        double mbMultiplier = coolantDef != null && coolantDef.mbMultiplier() > 0 ? coolantDef.mbMultiplier() : 1.0;
        ResourceLocation fuelId = simulationFuelId != null ? simulationFuelId : ReactorRodBlockEntity.URANIUM_FUEL_ID;
        FuelDefinition fuelDef = FuelLoader.get(fuelId);
        double baseRf = fuelDef != null ? fuelDef.baseRfPerTick() : 200.0;
        double baseFuelUnitsPerTick = fuelDef != null ? fuelDef.baseFuelUnitsPerTick() : 0.03;
        double rfEfficiency = 1.0 - Config.RF_EFFICIENCY_LOSS.get();
        double fuelEfficiency = Config.FUEL_EFFICIENCY_LOSS.get();
        double productionMult = Config.PRODUCTION_MULTIPLIER.get();
        double consumptionMult = Config.CONSUMPTION_MULTIPLIER.get();
        double energyRodFactor = rodEnergyScaling(effectiveRodCount);
        double decayRods = Math.max(0.0, Config.CONSUMPTION_CURVE_DECAY_RODS.get());
        double curveStrength = (decayRods <= 0) ? 1.0 : decayRods / (effectiveRodCount + decayRods);
        double curveStrengthAdjusted = 0.4 + 0.50 * curveStrength;
        double consumptionScale = Config.CONSUMPTION_SCALE.get() / Math.pow(effectiveRodCount + 1, 0.5 * curveStrengthAdjusted);
        double consumptionDivisor = Math.max(0.1, Config.HEAT_SINK_CONSUMPTION_DIVISOR.get());
        double fuelRodFactor = rodFuelScaling(effectiveRodCount);
        double fuelConsumptionRate = baseFuelUnitsPerTick * consumptionMult * fuelEfficiency * fuelRodFactor * consumptionScale / mbMultiplier / heatSinkFuelMult / consumptionDivisor;
        if (countAdj + countNon > 0) {
            fuelConsumptionRate *= Math.max(0.1, Config.HEAT_SINK_FUEL_UNITS_MULTIPLIER.get());
        }
        fuelConsumptionRate = Math.max(fuelConsumptionRate, Config.MIN_FUEL_UNITS_PER_TICK.get());

        double rfProduced;
        if (countAdj + countNon > 0 && effectiveRodCount > 0) {
            double adjEnergyAvg = (countAdj > 0) ? (sumEnergyAdj / (double) countAdj) : 1.0;
            double adjCoverage = Math.min(1.0, (double) countAdj / Math.max(1.0, (double) rodCount));
            double heatSinkRfFactor = adjEnergyAvg * adjCoverage;
            rfProduced = baseRf * productionMult * rfEfficiency * energyRodFactor * heatSinkRfFactor * rfMultiplier
                    * Math.max(0.1, Config.HEAT_SINK_RF_MULTIPLIER.get());
        } else {
            rfProduced = baseRf * productionMult * rfEfficiency * energyRodFactor * rfMultiplier * heatSinkEnergyMult;
        }
        rfProduced = Math.max(rfProduced, Config.MIN_RF_PER_TICK.get());

        boolean waterMode = coolantDef != null
                && (coolantDef.reduceRfProduction() || CoolantLoader.WATER_COOLANT_ID.equals(coolantDef.coolantId()));
        int steamPerTick = 0;
        int coolantConsumedPerTick = 0;
        long rfPerTick;
        if (waterMode && coolantDef != null) {
            int coolantToConsumeMb = (int) (rfProduced * coolantDef.rfToCoolantFactor());
            coolantConsumedPerTick = coolantToConsumeMb;
            steamPerTick = (int) (coolantToConsumeMb * coolantDef.steamPerCoolant());
            rfPerTick = 0;
        } else {
            rfPerTick = doubleToPositiveLongRf(rfProduced);
        }

        int fuelHundredths = (int) Math.round(fuelConsumptionRate * 100);
        int interiorVolume = Math.max(1, (w - 2) * (h - 2) * (d - 2));
        int interiorMaxSpan = Math.max(1, Math.max(w - 2, Math.max(h - 2, d - 2)));
        boolean stabilityOk = computeSimStabilityCoolingSufficient(
                rfProduced, waterMode, coolantConsumedPerTick, coolantDef, baseRf,
                sumOverheatingAdj, sumOverheatingNon, interiorVolume, interiorMaxSpan);
        return new SimulationResult(rodCount, rodColumns, coolantBlockCount, rfPerTick, steamPerTick, coolantConsumedPerTick, fuelHundredths, stabilityOk);
    }

    /** Same cooling/production ratio as {@link #updateStability}. */
    private static boolean computeSimStabilityCoolingSufficient(
            double rfProduced, boolean waterMode, int coolantConsumedPerTick,
            @Nullable CoolantDefinition coolantDef, double baseRf,
            double sumOverheatingAdj, double sumOverheatingNon,
            int interiorBlockCount,
            int interiorMaxSpanBlocks) {
        double thermalStress = thermalStressSizeFactor(interiorMaxSpanBlocks);
        double weightedOverheat = weightedOverheatingForStability(sumOverheatingAdj, sumOverheatingNon) / thermalStress;
        double heatSinkCoolingRF = baseRf * 0.5 * weightedOverheat;
        double fluidCoolingRF = 0;
        if (waterMode && coolantDef != null && coolantDef.rfToCoolantFactor() > 0) {
            fluidCoolingRF = (coolantConsumedPerTick / coolantDef.rfToCoolantFactor()) * coolantDef.overheatingMultiplier();
            if (Boolean.TRUE.equals(Config.THERMAL_STRESS_SCALE_FLUID_COOLING.get())) {
                fluidCoolingRF /= thermalStress;
            }
        }
        double totalCooling = heatSinkCoolingRF + fluidCoolingRF;
        double ratio = (rfProduced > 0) ? (totalCooling / rfProduced) : 1.0;
        return ratio >= stabilityRequiredCoolingRatio(interiorBlockCount);
    }

    private static long key(int rx, int ry, int rz) {
        return ((long) rx << 16) | ((ry & 0xFF) << 8) | (rz & 0xFF);
    }

    /** True if interior cell (lx, ly, lz) is inside rod space (the -2 X/Z area). */
    private static boolean isSimRodForPreview(
            int rx, int ry, int rz, int rw, int rh, int rd,
            int rodPattern, boolean expansionRodAtCenter,
            @Nullable BiPredicate<Integer, Integer> websiteRodOverride) {
        if (rw <= 0 || rh <= 0 || rd <= 0) return false;
        if (rx < 0 || rx >= rw || ry < 0 || ry >= rh || rz < 0 || rz >= rd) return false;
        if (rodPattern == RodPatternLogic.PATTERN_WEBSITE) {
            return websiteRodOverride != null && websiteRodOverride.test(rx, rz);
        }
        return RodPatternLogic.isRodForPreview(rx, ry, rz, rw, rh, rd, rodPattern, expansionRodAtCenter);
    }

    private static boolean isSimInRodSpace(int lx, int ly, int lz, int w, int h, int d, int insetXZ) {
        return lx >= insetXZ && lx < w - insetXZ && ly >= 1 && ly < h - 1 && lz >= insetXZ && lz < d - insetXZ;
    }

    /** True if interior cell (lx, ly, lz) is inside rod space and is a rod. Used when counting heat sink over full interior. */
    private static boolean isSimInteriorCellRod(int lx, int ly, int lz, int w, int h, int d, int insetXZ, Set<Long> rodSet) {
        if (!isSimInRodSpace(lx, ly, lz, w, h, d, insetXZ)) return false;
        return rodSet.contains(key(lx - insetXZ, ly - 1, lz - insetXZ));
    }

    /** True if cell (lx, ly, lz) has at least one neighbor in rod space (6 directions) that is a rod. Used for Super Economy. */
    private static boolean isSimRodSpaceCellAdjacentToRod(int lx, int ly, int lz, int w, int h, int d, int insetXZ, Set<Long> rodSet) {
        for (int dx = -1; dx <= 1; dx += 2)
            if (lx + dx >= insetXZ && lx + dx < w - insetXZ && isSimInteriorCellRod(lx + dx, ly, lz, w, h, d, insetXZ, rodSet)) return true;
        for (int dy = -1; dy <= 1; dy += 2)
            if (ly + dy >= 1 && ly + dy < h - 1 && isSimInteriorCellRod(lx, ly + dy, lz, w, h, d, insetXZ, rodSet)) return true;
        for (int dz = -1; dz <= 1; dz += 2)
            if (lz + dz >= insetXZ && lz + dz < d - insetXZ && isSimInteriorCellRod(lx, ly, lz + dz, w, h, d, insetXZ, rodSet)) return true;
        return false;
    }

    /** True if interior cell (lx, ly, lz) has at least one neighbor (6 directions) that is a rod. Used for Economy mode over full interior. */
    private static boolean isSimInteriorCellAdjacentToRod(int lx, int ly, int lz, int w, int h, int d, int insetXZ, Set<Long> rodSet) {
        for (int dx = -1; dx <= 1; dx += 2)
            if (lx + dx >= 1 && lx + dx < w - 1 && isSimInteriorCellRod(lx + dx, ly, lz, w, h, d, insetXZ, rodSet)) return true;
        for (int dy = -1; dy <= 1; dy += 2)
            if (ly + dy >= 1 && ly + dy < h - 1 && isSimInteriorCellRod(lx, ly + dy, lz, w, h, d, insetXZ, rodSet)) return true;
        for (int dz = -1; dz <= 1; dz += 2)
            if (lz + dz >= 1 && lz + dz < d - 1 && isSimInteriorCellRod(lx, ly, lz + dz, w, h, d, insetXZ, rodSet)) return true;
        return false;
    }
}