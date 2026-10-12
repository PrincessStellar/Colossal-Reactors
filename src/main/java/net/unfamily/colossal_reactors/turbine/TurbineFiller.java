package net.unfamily.colossal_reactors.turbine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.unfamily.colossal_reactors.blockentity.PortMode;
import net.unfamily.colossal_reactors.blockentity.ResourcePortBlockEntity;
import net.unfamily.colossal_reactors.blockentity.TurbineControllerBlockEntity;
import net.unfamily.colossal_reactors.integration.mekanism.MaterialSelector;
import net.unfamily.colossal_reactors.integration.mekanism.MekChemicalHelper;

import java.util.List;

/**
 * Pulls steam from INSERT resource ports into the controller steam input buffer (up to free capacity).
 * {@link net.unfamily.colossal_reactors.turbine.TurbineSimulation} consumes from that buffer each tick.
 */
public final class TurbineFiller {

    private TurbineFiller() {}

    public static void tickFill(ServerLevel level, TurbineControllerBlockEntity controller) {
        var result = controller.getCachedResult();
        if (!result.valid()) return;

        TurbineGenerationDefinition gen = TurbineGenerationLoader.getDefault();
        if (gen == null) return;

        List<String> inputs = gen.inputs();
        if (inputs.isEmpty()) return;

        int budget = Math.max(0, controller.getSteamInputCapacityMb() - controller.getTotalSteamInputMb());
        if (budget <= 0) return;

        long[] resourcePorts = controller.getCachedResourcePortPositions();
        if (resourcePorts.length == 0) {
            controller.rebuildPartCachesIfNeeded(level, result);
            resourcePorts = controller.getCachedResourcePortPositions();
        }

        RegistryAccess registryAccess = level.registryAccess();
        for (long lp : resourcePorts) {
            if (budget <= 0) break;
            if (!(level.getBlockEntity(BlockPos.of(lp)) instanceof ResourcePortBlockEntity port)) continue;
            if (port.getPortMode() != PortMode.INSERT) continue;

            budget -= pullLiquidSteamInputs(port, inputs, controller, budget, registryAccess);

            if (budget > 0 && MekChemicalHelper.isLoaded()) {
                budget -= pullChemicalSteamInputs(port, inputs, controller, budget, registryAccess);
            }
        }
    }

    private static int pullLiquidSteamInputs(
            ResourcePortBlockEntity port,
            List<String> inputs,
            TurbineControllerBlockEntity controller,
            int budget,
            RegistryAccess registryAccess) {
        FluidStack stored = port.getStoredFluid();
        if (stored.isEmpty() || stored.getFluid() == Fluids.EMPTY) {
            return 0;
        }
        if (!MaterialSelector.matchesAnyFluidInput(stored.getFluid(), inputs)) {
            return 0;
        }
        int toMove = Math.min(budget, stored.getAmount());
        if (toMove <= 0) {
            return 0;
        }
        return drainFluidIntoSteamBuffer(port, stored.getFluid(), controller, toMove);
    }

    private static int drainFluidIntoSteamBuffer(
            ResourcePortBlockEntity port,
            Fluid fluid,
            TurbineControllerBlockEntity controller,
            int budget) {
        if (budget <= 0 || fluid == null || fluid == Fluids.EMPTY) {
            return 0;
        }
        int drained = port.takeFluidForReactor(fluid, budget);
        if (drained <= 0) {
            return 0;
        }
        int added = controller.addSteamInput(fluid, drained);
        int leftover = drained - added;
        if (leftover > 0) {
            port.getFluidHandler().fill(new FluidStack(fluid, leftover), IFluidHandler.FluidAction.EXECUTE);
        }
        return added;
    }

    private static int pullChemicalSteamInputs(
            ResourcePortBlockEntity port,
            List<String> inputs,
            TurbineControllerBlockEntity controller,
            int budget,
            RegistryAccess registryAccess) {
        Fluid bufferFluid = MaterialSelector.resolvePreferredBufferFluid(inputs, registryAccess);
        if (bufferFluid == null || bufferFluid == Fluids.EMPTY) {
            bufferFluid = TurbineSimulation.resolveInputSteamFluid(registryAccess);
        }
        if (bufferFluid == null || bufferFluid == Fluids.EMPTY) {
            return 0;
        }
        int total = 0;
        for (String input : inputs) {
            if (total >= budget) break;
            if (!MaterialSelector.isChemicalPrefix(input)) continue;
            Object template = MekChemicalHelper.createStack(
                    ResourceLocation.tryParse(input.substring(1)), 1);
            if (template == null) continue;
            int drained = port.takeGasForReactor(template, budget - total);
            if (drained > 0) {
                total += controller.addSteamInput(bufferFluid, drained);
            }
            break;
        }
        return total;
    }
}
