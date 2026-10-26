package net.unfamily.colossal_reactors.turbine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.block.TurbineControllerBlock;
import net.neoforged.neoforge.fluids.FluidStack;
import net.unfamily.colossal_reactors.blockentity.PortMode;
import net.unfamily.colossal_reactors.blockentity.ResourcePortBlockEntity;
import net.unfamily.colossal_reactors.blockentity.TurbineControllerBlockEntity;
import net.unfamily.colossal_reactors.blockentity.TurbinePowerPort;
import net.unfamily.colossal_reactors.integration.mekanism.MekChemicalHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Turbine RF/steam simulation for builder GUI and runtime estimates.
 */
public final class TurbineSimulation {

    public record SimulationResult(
            int bladeCount,
            int validBladeCount,
            int coilBlockCount,
            double steamMbPerTick,
            double rfPerTick,
            double coilEfficiency,
            double bladeEfficiency
    ) {
        /** Steam mB/t divided by RF/t (consumption / production). 0 if either side is zero. */
        public double consumptionProductionRatio() {
            if (rfPerTick <= 0 || steamMbPerTick <= 0) {
                return 0.0;
            }
            return steamMbPerTick / rfPerTick;
        }
    }

    private TurbineSimulation() {}

    public static SimulationResult simulateFromBuilderParams(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int rodPattern, int coilIndex, int coilLayerCount) {
        return simulateFromBuilderParams(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                TurbinePlacementAxis.DEFAULT_INDEX, rodPattern, coilIndex, coilLayerCount, null);
    }

    public static SimulationResult simulateFromBuilderParams(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern, int coilIndex, int storedCoilSetting,
            @Nullable Identifier generationId) {
        return simulateFromBuilderParams(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, generationId,
                null, false, Integer.MAX_VALUE);
    }

    public static SimulationResult simulateFromBuilderParams(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern, int coilIndex, int storedCoilSetting,
            @Nullable Identifier generationId,
            @Nullable java.util.function.BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile) {
        return simulateFromBuilderParams(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, generationId,
                websiteRodOverride, websiteClimbProfile, Integer.MAX_VALUE);
    }

    public static SimulationResult simulateFromBuilderParams(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern, int coilIndex, int storedCoilSetting,
            @Nullable Identifier generationId,
            @Nullable java.util.function.BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap) {
        return simulateFromBuilderParams(registryAccess, sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, generationId,
                websiteRodOverride, websiteClimbProfile, websiteBladeRingCap,
                TurbineRodPatternLogic.VARIANT_EFFICIENT);
    }

    public static SimulationResult simulateFromBuilderParams(
            RegistryAccess registryAccess,
            int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth,
            int placementAxisIndex,
            int rodPattern, int coilIndex, int storedCoilSetting,
            @Nullable Identifier generationId,
            @Nullable java.util.function.BiPredicate<Integer, Integer> websiteRodOverride,
            boolean websiteClimbProfile,
            int websiteBladeRingCap,
            int bladeVariant) {

        TurbineGenerationDefinition gen = generationForSimulation(registryAccess, generationId);
        TurbineProductionMath.ProductionEstimate production = TurbineProductionMath.fromBuilderParams(
                sizeLeft, sizeRight, sizeHeight, sizeDepth,
                placementAxisIndex, rodPattern, coilIndex, storedCoilSetting, gen,
                websiteRodOverride, websiteClimbProfile, websiteBladeRingCap, bladeVariant);

        if (Boolean.TRUE.equals(Config.TURBINE_SIMULATION_DEBUG.get())) {
            net.unfamily.colossal_reactors.ColossalReactors.LOGGER.info(
                    "[TurbineSim] blades={} validBlades={} steam={} rf={}",
                    production.bladeCount(), production.validBladeCount(),
                    production.maxSteamMbPerTick(), production.estimatedRfPerTick());
        }

        return new SimulationResult(
                production.bladeCount(),
                production.validBladeCount(),
                production.coilBlockCount(),
                production.maxSteamMbPerTick(),
                production.estimatedRfPerTick(),
                production.coilEfficiency(),
                production.bladeEfficiency());
    }

    @Nullable
    private static TurbineGenerationDefinition generationForSimulation(
            RegistryAccess registryAccess, @Nullable Identifier generationId) {
        if (generationId != null) {
            TurbineGenerationDefinition def = TurbineGenerationLoader.get(generationId);
            if (def != null) return def;
        }
        return TurbineGenerationLoader.getDefault();
    }

    public record RuntimeResult(long rfPerTick, double steamMbPerTick, double coilEfficiency, double bladeEfficiency) {}

    /** Runtime tick estimate from validated structure (steam cap and RF output). */
    public static RuntimeResult tickRuntime(ServerLevel level, TurbineValidation.Result result) {
        if (!result.valid()) {
            return new RuntimeResult(0, 0, 0, 0);
        }
        long rf = (long) Math.min(Long.MAX_VALUE, result.estimatedRfPerTick());
        return new RuntimeResult(rf, result.maxSteamMbPerTick(), result.coilEfficiency(), result.bladeEfficiency());
    }

    /**
     * One tick of turbine production: consume buffered steam, distribute RF to power ports, push output fluid.
     */
    public static void tick(ServerLevel level, TurbineControllerBlockEntity controller) {
        TurbineValidation.Result result = controller.getCachedResult();
        if (!result.valid()) {
            controller.setRuntimeStats(0, 0, false, false);
            return;
        }
        boolean gateOpen = TurbineControllerBlock.isRedstoneGateSatisfied(level, controller, result);
        if (!gateOpen) {
            controller.setRuntimeStats(0, 0, false, false);
            return;
        }

        long[] resourcePortPositions = controller.getCachedResourcePortPositions();
        long[] powerPortPositions = controller.getCachedPowerPortPositions();
        if (controller.getCachedRodPositions().length == 0
                || (resourcePortPositions.length == 0 && powerPortPositions.length == 0
                && (result.maxSteamMbPerTick() > 0 || result.estimatedRfPerTick() > 0))) {
            controller.rebuildPartCachesIfNeeded(level, result);
            resourcePortPositions = controller.getCachedResourcePortPositions();
            powerPortPositions = controller.getCachedPowerPortPositions();
        }

        TurbineGenerationDefinition gen = TurbineGenerationLoader.getDefault();
        Fluid outputFluid = TurbineGenerationLoader.getOutputFluid(gen, level.registryAccess());
        java.util.List<String> steamInputs = gen != null ? gen.inputs() : java.util.List.of("#c:steam");

        double steamDemand = result.maxSteamMbPerTick();
        int steamConsumed = 0;

        pushOutputReturnToResourcePorts(level, controller, resourcePortPositions, steamInputs, level.registryAccess());

        boolean hasEnergySpace = hasPowerPortSpace(level, powerPortPositions);
        int freeCondensate = controller.getOutputReturnFreeMb();
        boolean outputBufferFull = freeCondensate == 0;
        if (hasEnergySpace && !outputBufferFull && steamDemand > 0 && !steamInputs.isEmpty()) {
            int wanted = (int) Math.ceil(steamDemand);
            if (outputFluid != null && outputFluid != Fluids.EMPTY) {
                wanted = Math.min(wanted, freeCondensate);
            }
            if (wanted > 0) {
                steamConsumed = controller.consumeSteamInputMatching(steamInputs, wanted);
                if (steamConsumed > 0 && outputFluid != null && outputFluid != Fluids.EMPTY) {
                    int added = controller.addOutputReturn(outputFluid, steamConsumed);
                    if (added < steamConsumed) {
                        steamConsumed = added;
                    }
                }
            }
        }

        double rfScale = steamDemand > 0 ? Math.min(1.0, steamConsumed / steamDemand) : 0.0;
        long rfTarget = (long) Math.min(Long.MAX_VALUE, result.estimatedRfPerTick() * rfScale);
        long rfPushed = 0;
        if (rfTarget > 0 && powerPortPositions.length > 0) {
            long perPort = rfTarget / powerPortPositions.length;
            long remainder = rfTarget % powerPortPositions.length;
            for (int i = 0; i < powerPortPositions.length; i++) {
                long offer = perPort + (i < remainder ? 1 : 0);
                if (offer <= 0) continue;
                if (level.getBlockEntity(BlockPos.of(powerPortPositions[i])) instanceof TurbinePowerPort port) {
                    rfPushed += port.receiveEnergyFromTurbine(offer);
                }
            }
        }

        boolean running = rfPushed > 0;
        controller.setRuntimeStats(rfPushed, steamConsumed, running, gateOpen);
    }

    private static boolean hasPowerPortSpace(ServerLevel level, long[] powerPortPositions) {
        if (powerPortPositions == null || powerPortPositions.length == 0) {
            return false;
        }
        for (long packed : powerPortPositions) {
            if (level.getBlockEntity(BlockPos.of(packed)) instanceof TurbinePowerPort port
                    && port.canAcceptMoreFromTurbine()) {
                return true;
            }
        }
        return false;
    }

    private static void pushOutputReturnToResourcePorts(
            ServerLevel level,
            TurbineControllerBlockEntity controller,
            long[] resourcePortPositions,
            List<String> steamInputs,
            net.minecraft.core.RegistryAccess registryAccess) {
        List<ResourcePortBlockEntity> outputPorts = collectOutputPorts(level, resourcePortPositions);
        for (var entry : controller.getOutputReturnEntries()) {
            if (entry.mb() <= 0) {
                continue;
            }
            Fluid fluid = BuiltInRegistries.FLUID.getValue(entry.fluidId());
            if (fluid == null || fluid == Fluids.EMPTY) {
                continue;
            }
            int remaining = entry.mb();
            int consumed = controller.consumeOutputReturn(fluid, remaining);
            if (consumed <= 0) {
                continue;
            }
            remaining = consumed;
            if (TurbineSteamMaterial.isSteamFluid(fluid, steamInputs)) {
                remaining = pushSteamToPorts(outputPorts, steamInputs, remaining, registryAccess);
            } else {
                remaining = pushFluidToOutputPorts(outputPorts, new FluidStack(fluid, remaining));
            }
            if (remaining > 0) {
                controller.addOutputReturn(fluid, remaining);
            }
        }
    }

    private static List<ResourcePortBlockEntity> collectOutputPorts(ServerLevel level, long[] resourcePortPositions) {
        List<ResourcePortBlockEntity> ports = new ArrayList<>();
        for (long p : resourcePortPositions) {
            if (!(level.getBlockEntity(BlockPos.of(p)) instanceof ResourcePortBlockEntity port)) {
                continue;
            }
            PortMode mode = port.getPortMode();
            if (mode == PortMode.EXTRACT || mode == PortMode.EJECT) {
                ports.add(port);
            }
        }
        return ports;
    }

    /** Push steam to gas ports first (Mek), then liquid ports; supports EXTRACT and EJECT. */
    private static int pushSteamToPorts(
            List<ResourcePortBlockEntity> ports,
            List<String> steamInputs,
            int amountMb,
            net.minecraft.core.RegistryAccess registryAccess) {
        if (amountMb <= 0) {
            return 0;
        }
        int remaining = amountMb;
        if (MekChemicalHelper.isLoaded()) {
            Object gasStack = TurbineSteamMaterial.createGasStack(remaining, steamInputs);
            if (gasStack != null) {
                for (ResourcePortBlockEntity port : ports) {
                    if (remaining <= 0) {
                        break;
                    }
                    if (!port.isAllowGas() || port.isAllowLiquid()) {
                        continue;
                    }
                    Object copy = MekChemicalHelper.copyStack(gasStack, remaining);
                    if (copy == null) {
                        break;
                    }
                    int filled = port.receiveGasFromReactor(copy);
                    remaining -= filled;
                }
            }
        }
        if (remaining > 0) {
            Fluid fluid = TurbineSteamMaterial.resolveBufferFluid(steamInputs, registryAccess);
            if (fluid != null && fluid != Fluids.EMPTY) {
                remaining = pushFluidToOutputPorts(ports, new FluidStack(fluid, remaining));
            }
        }
        return remaining;
    }

    private static int pushFluidToOutputPorts(List<ResourcePortBlockEntity> ports, FluidStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int remaining = stack.getAmount();
        for (ResourcePortBlockEntity port : ports) {
            if (remaining <= 0) {
                break;
            }
            if (!port.isAllowLiquid() || port.isAllowGas()) {
                continue;
            }
            int filled = port.receiveFluidFromTurbine(new FluidStack(stack.getFluid(), remaining));
            remaining -= filled;
        }
        return remaining;
    }

    @Nullable
    public static Fluid resolveInputSteamFluid(RegistryAccess registryAccess) {
        TurbineGenerationDefinition def = TurbineGenerationLoader.getDefault();
        if (def == null || def.inputs().isEmpty()) {
            return TurbineGenerationLoader.getFirstFluidFromTag("#c:steam", registryAccess);
        }
        for (String input : def.inputs()) {
            if (input.startsWith("#")) {
                Fluid fluid = TurbineGenerationLoader.getFirstFluidFromTag(input, registryAccess);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    return fluid;
                }
            } else {
                Identifier id = Identifier.tryParse(input);
                if (id != null) {
                    Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
                    if (fluid != Fluids.EMPTY) {
                        return fluid;
                    }
                }
            }
        }
        Fluid internalSteam = BuiltInRegistries.FLUID.getValue(
                Identifier.fromNamespaceAndPath(ColossalReactors.MODID, "gas_fluid_steam"));
        if (internalSteam != null && internalSteam != Fluids.EMPTY) {
            return internalSteam;
        }
        return TurbineGenerationLoader.getFirstFluidFromTag("#c:steam", registryAccess);
    }

    public static void flushFluidBuffersToEject(ServerLevel level, TurbineControllerBlockEntity controller) {
        long[] resourcePortPositions = controller.getCachedResourcePortPositions();
        if (resourcePortPositions.length == 0 && controller.getCachedResult().valid()) {
            controller.rebuildPartCachesIfNeeded(level, controller.getCachedResult());
            resourcePortPositions = controller.getCachedResourcePortPositions();
        }
        TurbineGenerationDefinition gen = TurbineGenerationLoader.getDefault();
        java.util.List<String> steamInputs = gen != null ? gen.inputs() : java.util.List.of("#c:steam");
        for (TurbineControllerBlockEntity.FluidBufferEntry entry : controller.getSteamInputEntriesCopy()) {
            Fluid fluid = BuiltInRegistries.FLUID.getValue(entry.fluidId());
            if (fluid == null || fluid == Fluids.EMPTY) {
                continue;
            }
            if (net.unfamily.colossal_reactors.util.FluidInputMatcher.matchesAnyFluidInput(fluid, steamInputs)) {
                pushFluidBufferToEject(level, controller, resourcePortPositions, fluid, true);
            }
        }
        for (var entry : controller.getOutputReturnEntries()) {
            if (entry.mb() <= 0) continue;
            Fluid fluid = BuiltInRegistries.FLUID.getValue(entry.fluidId());
            if (fluid == null || fluid == Fluids.EMPTY) continue;
            pushFluidBufferToEject(level, controller, resourcePortPositions, fluid, false);
        }
    }

    private static void pushFluidBufferToEject(
            ServerLevel level,
            TurbineControllerBlockEntity controller,
            long[] resourcePortPositions,
            Fluid fluid,
            boolean steamInput) {
        int total = steamInput ? controller.getTotalSteamInputMb() : controller.getTotalOutputReturnMb();
        if (total <= 0) {
            return;
        }
        int remaining = steamInput
                ? controller.consumeSteamInput(fluid, total)
                : controller.consumeOutputReturn(fluid, total);
        TurbineGenerationDefinition gen = TurbineGenerationLoader.getDefault();
        List<String> steamInputs = gen != null ? gen.inputs() : java.util.List.of("#c:steam");
        List<ResourcePortBlockEntity> outputPorts = collectOutputPorts(level, resourcePortPositions);
        while (remaining > 0) {
            int before = remaining;
            if (steamInput && TurbineSteamMaterial.isSteamFluid(fluid, steamInputs)) {
                remaining = pushSteamToPorts(outputPorts, steamInputs, remaining, level.registryAccess());
            } else {
                remaining = pushFluidToOutputPorts(outputPorts, new FluidStack(fluid, remaining));
            }
            if (remaining >= before) {
                if (steamInput) {
                    controller.addSteamInput(fluid, remaining);
                } else {
                    controller.addOutputReturn(fluid, remaining);
                }
                break;
            }
        }
    }
}
