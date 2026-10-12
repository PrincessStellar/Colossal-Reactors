package net.unfamily.colossal_reactors.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.unfamily.colossal_reactors.blockentity.PortFilter;
import net.unfamily.colossal_reactors.blockentity.PortMode;
import net.unfamily.colossal_reactors.blockentity.ResourcePortBlockEntity;
import net.unfamily.colossal_reactors.blockentity.ReactorControllerBlockEntity;
import net.unfamily.colossal_reactors.coolant.CoolantDefinition;
import net.unfamily.colossal_reactors.coolant.CoolantLoader;
import net.unfamily.colossal_reactors.fuel.FuelDefinition;
import net.unfamily.colossal_reactors.fuel.FuelLoader;
import net.unfamily.colossal_reactors.integration.mekanism.MaterialSelector;
import net.unfamily.colossal_reactors.integration.mekanism.MekChemicalHelper;
import net.unfamily.colossal_reactors.multiblock.MultiblockPortScaling;

import java.util.ArrayList;
import java.util.List;

/**
 * When the reactor controller sees the multiblock as valid (ON), it pulls fuel items from INSERT ports into rods.
 * Liquids (coolant, steam) use ports only; the simulation drains coolant from INSERT ports and pushes steam to EXTRACT/EJECT ports.
 */
public final class ReactorFiller {

    private ReactorFiller() {}

    /**
     * Called each tick (or every N ticks) when the reactor is ON and valid.
     * Pulls fuel items from INSERT ports into the controller-wide aggregated fuel buffer (no per-rod distribution).
     */
    public static void tickFill(ServerLevel level, ReactorControllerBlockEntity controller) {
        ReactorValidation.Result result = controller.getCachedResult();
        if (result == null || !result.valid()) return;

        List<ResourcePortBlockEntity> insertPorts = new ArrayList<>();
        long[] resourcePorts = controller.getCachedResourcePortPositions();
        if (resourcePorts.length == 0) {
            controller.rebuildPartCachesIfNeeded(level, result);
            resourcePorts = controller.getCachedResourcePortPositions();
        }
        for (long lp : resourcePorts) {
            BlockPos pos = BlockPos.of(lp);
            if (level.getBlockEntity(pos) instanceof ResourcePortBlockEntity port && port.getPortMode() == PortMode.INSERT) {
                insertPorts.add(port);
            }
        }

        RegistryAccess registryAccess = level.registryAccess();
        int coolantMoveBudgetMb = MultiblockPortScaling.estimateReactorCoolantMoveBudgetMb(controller, registryAccess);

        for (ResourcePortBlockEntity port : insertPorts) {
            if (portAcceptsFuel(port) && port.isAllowSolid()) {
                pullSolidFuel(port, controller, registryAccess);
            }

            if (MekChemicalHelper.isLoaded()) {
                coolantMoveBudgetMb = pullChemicals(port, controller, registryAccess, coolantMoveBudgetMb);
            }

            if (portAcceptsFuel(port) && port.isAllowLiquid()) {
                pullLiquidFuel(port, controller, registryAccess);
            }
            if (portAcceptsCoolant(port) && port.isAllowLiquid() && coolantMoveBudgetMb > 0) {
                coolantMoveBudgetMb = pullLiquidCoolant(port, controller, registryAccess, coolantMoveBudgetMb);
            }
        }
    }

    private static boolean portAcceptsFuel(ResourcePortBlockEntity port) {
        return port.getPortFilter().acceptsFuelRole();
    }

    private static boolean portAcceptsCoolant(ResourcePortBlockEntity port) {
        return port.getPortFilter().acceptsCoolantRole();
    }

    private static void pullSolidFuel(
            ResourcePortBlockEntity port,
            ReactorControllerBlockEntity controller,
            RegistryAccess registryAccess) {
        ItemStack stack = port.getItemHandler().getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }
        FuelDefinition def = FuelLoader.getDefinitionForItem(stack, registryAccess);
        if (def == null) {
            return;
        }
        float unitsPerItem = def.fuelUnitsPerInputUnit();
        if (unitsPerItem <= 0f) {
            unitsPerItem = 1f;
        }
        float space = Math.max(0f, controller.getMaxFuelUnitsTotal() - controller.getTotalFuelUnits());
        int wantItems = def.inputAmountBatched(def.inputAmountForSpaceUnits(space));
        if (wantItems <= 0) {
            return;
        }
        int cap = Math.min(wantItems, 64);
        for (int i = 0; i < cap; i++) {
            ItemStack extracted = port.getItemHandler().extractItem(0, 1, false);
            if (extracted.isEmpty()) {
                break;
            }
            float grant = def.fuelUnitsFromInputAmount(1f);
            float added = controller.addFuel(def.fuelId(), grant);
            if (added <= 0.0001f) {
                port.getItemHandler().insertItem(0, extracted, false);
                break;
            }
            if (added + 0.001f < grant) {
                port.getItemHandler().insertItem(0, extracted, false);
                controller.consumeFuel(def.fuelId(), added);
                break;
            }
        }
    }

    private static int pullChemicals(
            ResourcePortBlockEntity port,
            ReactorControllerBlockEntity controller,
            RegistryAccess registryAccess,
            int coolantMoveBudgetMb) {
        if (!port.isAllowGas()) {
            return coolantMoveBudgetMb;
        }
        Object handler = port.getChemicalHandler();
        if (handler == null) {
            return coolantMoveBudgetMb;
        }
        try {
            Object inTank = handler.getClass().getMethod("getChemicalInTank", int.class).invoke(handler, 0);
            if (MekChemicalHelper.isEmpty(inTank)) {
                return coolantMoveBudgetMb;
            }

            int budget = coolantMoveBudgetMb;
            if (portAcceptsFuel(port)) {
                budget = pullChemicalFuel(port, controller, inTank, budget);
            }
            if (portAcceptsCoolant(port) && budget > 0) {
                budget = pullChemicalCoolant(port, controller, inTank, registryAccess, budget);
            }
            return budget;
        } catch (Throwable ignored) {
            return coolantMoveBudgetMb;
        }
    }

    private static int pullChemicalFuel(
            ResourcePortBlockEntity port,
            ReactorControllerBlockEntity controller,
            Object inTank,
            int budget) {
        FuelDefinition chemFuel = FuelLoader.getDefinitionForChemical(inTank);
        if (chemFuel == null) {
            return budget;
        }
        float spaceUnits = Math.max(0f, controller.getMaxFuelUnitsTotal() - controller.getTotalFuelAndWasteUnits());
        int maxMbInTank = (int) Math.min(MekChemicalHelper.getAmount(inTank), Integer.MAX_VALUE);
        int maxMbBySpace = chemFuel.inputAmountForSpaceUnits(spaceUnits);
        int wantMb = chemFuel.pullAmountMb(Math.min(maxMbBySpace, maxMbInTank));
        if (wantMb <= 0) {
            return budget;
        }
        Object template = null;
        for (String input : chemFuel.inputs()) {
            if (!MaterialSelector.isChemicalPrefix(input)) {
                continue;
            }
            ResourceLocation chemId = ResourceLocation.tryParse(input.substring(1));
            if (chemId == null) {
                continue;
            }
            template = MekChemicalHelper.createStack(chemId, 1);
            if (template != null) {
                break;
            }
        }
        if (template == null) {
            template = inTank;
        }
        int drained = port.takeGasForReactor(template, wantMb);
        if (drained > 0) {
            float wouldAdd = chemFuel.fuelUnitsFromInputAmount(drained);
            float added = controller.addFuel(chemFuel.fuelId(), wouldAdd);
            if (added + 0.001f < wouldAdd) {
                int usedMb = chemFuel.inputAmountForGrantedUnits(added);
                int leftoverMb = drained - usedMb;
                if (leftoverMb > 0) {
                    returnChemicalToPort(port, template, leftoverMb);
                }
            }
        }
        return budget;
    }

    private static int pullChemicalCoolant(
            ResourcePortBlockEntity port,
            ReactorControllerBlockEntity controller,
            Object inTank,
            RegistryAccess registryAccess,
            int budget) {
        CoolantDefinition coolantDef = CoolantLoader.getDefinitionForChemical(inTank, registryAccess);
        if (coolantDef == null) {
            return budget;
        }
        int space = Math.max(0, controller.getCoolantCapacityMbTotal() - controller.getTotalCoolantMb());
        int want = Math.min(space, Math.min(budget, (int) MekChemicalHelper.getAmount(inTank)));
        if (want <= 0) {
            return budget;
        }
        Object template = null;
        for (String input : coolantDef.inputs()) {
            if (!MaterialSelector.isChemicalPrefix(input)) {
                continue;
            }
            ResourceLocation chemId = ResourceLocation.tryParse(input.substring(1));
            if (chemId == null) {
                continue;
            }
            template = MekChemicalHelper.createStack(chemId, 1);
            if (template != null) {
                break;
            }
        }
        if (template == null) {
            template = inTank;
        }
        int drained = port.takeGasForReactor(template, want);
        if (drained <= 0) {
            return budget;
        }
        ResourceLocation bufferKey = resolveCoolantBufferKey(coolantDef, inTank, registryAccess);
        if (bufferKey == null) {
            return budget;
        }
        int added = controller.addCoolantByKey(bufferKey, drained);
        return budget - added;
    }

    private static ResourceLocation resolveCoolantBufferKey(
            CoolantDefinition def,
            Object chemicalStack,
            RegistryAccess registryAccess) {
        Fluid fluid = MaterialSelector.resolvePreferredBufferFluid(def.inputs(), registryAccess);
        if (fluid != null && fluid != Fluids.EMPTY) {
            return BuiltInRegistries.FLUID.getKey(fluid);
        }
        String chemName = MekChemicalHelper.getTypeRegistryName(chemicalStack);
        if (chemName != null) {
            ResourceLocation chemId = ResourceLocation.tryParse(chemName);
            if (chemId != null) {
                Fluid chemFluid = BuiltInRegistries.FLUID.get(chemId);
                if (chemFluid != null && chemFluid != Fluids.EMPTY) {
                    return chemId;
                }
            }
        }
        return def.coolantId();
    }

    private static void pullLiquidFuel(
            ResourcePortBlockEntity port,
            ReactorControllerBlockEntity controller,
            RegistryAccess registryAccess) {
        FluidStack stored = port.getStoredFluid();
        if (stored.isEmpty() || stored.getFluid() == Fluids.EMPTY) {
            return;
        }
        FuelDefinition fuelDef = FuelLoader.getDefinitionForFluid(stored.getFluid(), registryAccess);
        if (fuelDef == null) {
            return;
        }
        float spaceUnits = Math.max(0f, controller.getMaxFuelUnitsTotal() - controller.getTotalFuelAndWasteUnits());
        int maxMbInTank = stored.getAmount();
        int maxMbBySpace = fuelDef.inputAmountForSpaceUnits(spaceUnits);
        int wantMb = fuelDef.pullAmountMb(Math.min(maxMbBySpace, maxMbInTank));
        if (wantMb <= 0) {
            return;
        }
        int drained = port.takeFluidForReactor(stored.getFluid(), wantMb);
        if (drained <= 0) {
            return;
        }
        float wouldAdd = fuelDef.fuelUnitsFromInputAmount(drained);
        float added = controller.addFuel(fuelDef.fuelId(), wouldAdd);
        if (added + 0.001f < wouldAdd) {
            int usedMb = fuelDef.inputAmountForGrantedUnits(added);
            int leftover = drained - usedMb;
            if (leftover > 0) {
                port.getFluidHandler().fill(new FluidStack(stored.getFluid(), leftover), IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    private static int pullLiquidCoolant(
            ResourcePortBlockEntity port,
            ReactorControllerBlockEntity controller,
            RegistryAccess registryAccess,
            int budget) {
        FluidStack stored = port.getStoredFluid();
        if (stored.isEmpty() || stored.getFluid() == Fluids.EMPTY) {
            return budget;
        }
        CoolantDefinition coolantDef = CoolantLoader.getDefinitionForFluid(stored.getFluid(), registryAccess);
        if (coolantDef == null) {
            return budget;
        }
        int space = Math.max(0, controller.getCoolantCapacityMbTotal() - controller.getTotalCoolantMb());
        int toMove = Math.min(space, Math.min(stored.getAmount(), budget));
        if (toMove <= 0) {
            return budget;
        }
        int drained = port.takeFluidForReactor(stored.getFluid(), toMove);
        if (drained <= 0) {
            return budget;
        }
        int added = controller.addCoolant(stored.getFluid(), drained);
        int leftover = drained - added;
        if (leftover > 0) {
            port.getFluidHandler().fill(new FluidStack(stored.getFluid(), leftover), IFluidHandler.FluidAction.EXECUTE);
        }
        return budget - drained;
    }

    private static void returnChemicalToPort(ResourcePortBlockEntity port, Object typeTemplate, int amountMb) {
        if (amountMb <= 0 || !MekChemicalHelper.isLoaded()) {
            return;
        }
        Object handler = port.getChemicalHandler();
        if (handler == null) {
            return;
        }
        Object stack = MekChemicalHelper.copyStack(typeTemplate, amountMb);
        if (stack != null) {
            MekChemicalHelper.fill(handler, stack, false);
            port.setChanged();
        }
    }
}
