package net.unfamily.colossal_reactors.fuel;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One fuel type: {@code sub_type} is {@code input-output} (e.g. {@code item-item}, {@code fluid-chemical}, {@code chemical-chemical}).
 */
public record FuelDefinition(
        ResourceLocation fuelId,
        ResourceLocation wasteId,
        String subType,
        List<String> inputs,
        int consume,
        String output,
        int produce,
        int unitsPerFuel,
        int unitsPerWaste,
        double baseRfPerTick,
        double baseFuelUnitsPerTick,
        boolean overwritable,
        /** Optional RGB fill tint (0xRRGGBB); null uses client fallback. */
        Integer colorRgb
) {
    public FuelDefinition(
            ResourceLocation fuelId,
            ResourceLocation wasteId,
            String subType,
            List<String> inputs,
            int consume,
            String output,
            int produce,
            int unitsPerFuel,
            int unitsPerWaste,
            double baseRfPerTick,
            double baseFuelUnitsPerTick,
            boolean overwritable) {
        this(fuelId, wasteId, subType, inputs, consume, output, produce, unitsPerFuel, unitsPerWaste,
                baseRfPerTick, baseFuelUnitsPerTick, overwritable, null);
    }
    public static final String SUBTYPE_ITEM_ITEM = "item-item";
    public static final String SUBTYPE_FLUID_FLUID = "fluid-fluid";
    public static final String SUBTYPE_CHEMICAL_CHEMICAL = "chemical-chemical";

    public FuelDefinition {
        if (wasteId == null) {
            wasteId = fuelId;
        }
        if (subType == null || subType.isBlank()) {
            subType = SUBTYPE_ITEM_ITEM;
        }
        inputs = inputs != null ? List.copyOf(inputs) : List.of();
        if (consume <= 0) {
            consume = 1;
        }
        if (produce <= 0) {
            produce = 1;
        }
    }

    public FuelMedium inputMedium() {
        return FuelSubType.parseInput(subType);
    }

    public FuelMedium outputMedium() {
        return FuelSubType.parseOutput(subType);
    }

    public boolean acceptsInputMedium(FuelMedium medium) {
        return inputMedium() == medium;
    }

    public boolean producesOutputMedium(FuelMedium medium) {
        return outputMedium() == medium;
    }

    public float fuelUnitsFromInputAmount(float inputAmount) {
        return inputAmount * unitsPerFuel / (float) consume;
    }

    public float fuelUnitsPerInputUnit() {
        return fuelUnitsFromInputAmount(1f);
    }

    public int inputAmountForSpaceUnits(float spaceUnits) {
        float per = fuelUnitsPerInputUnit();
        if (per <= 0f) {
            return 0;
        }
        return (int) Math.floor(spaceUnits / per);
    }

    public int pullAmountMb(int maxMbAvailable) {
        if (maxMbAvailable <= 0) {
            return 0;
        }
        int batch = consume;
        if (maxMbAvailable >= batch) {
            return (maxMbAvailable / batch) * batch;
        }
        return maxMbAvailable;
    }

    public int inputAmountForGrantedUnits(float grantedUnits) {
        float per = fuelUnitsPerInputUnit();
        if (per <= 0f) {
            return 0;
        }
        return (int) Math.ceil(grantedUnits / per - 1e-6f);
    }

    /** Fuel units per one item / 1 mB / 1 mB gas (one {@code consume} step when {@code consume == 1}). */
    public float fuelUnitsPerItemStack() {
        return fuelUnitsPerInputUnit();
    }

    /** Fuel buffer units granted for one full {@link #consume()} batch (e.g. 500 mB → 50000 units). */
    public float fuelUnitsPerConsumeBatch() {
        return fuelUnitsFromInputAmount(consume);
    }

    /** Align item/mB count down to whole {@link #consume()} batches (same rule as {@link #pullAmountMb(int)}). */
    public int inputAmountBatched(int maxInputAmount) {
        return pullAmountMb(maxInputAmount);
    }

    /**
     * Waste buffer units from burned fuel units.
     * {@code inputAmount = fuel_units × consume / units_per_fuel}.
     * Item output: {@code waste_units = inputAmount × units_per_waste} ({@code produce} is item count at eject).
     * Fluid/chemical output: {@code waste_units = inputAmount × units_per_waste / produce} (mB scaling).
     */
    public float wasteUnitsFromFuelConsumed(float fuelUnitsConsumed) {
        if (unitsPerFuel <= 0 || produce <= 0) {
            return fuelUnitsConsumed;
        }
        float inputAmount = fuelUnitsConsumed * (float) consume / (float) unitsPerFuel;
        if (outputMedium() == FuelMedium.ITEM) {
            return inputAmount * (float) unitsPerWaste;
        }
        return inputAmount * (float) unitsPerWaste / (float) produce;
    }

    public int wasteOutputAmountFromWasteUnits(float wasteUnits) {
        if (unitsPerWaste <= 0) {
            return 0;
        }
        return (int) Math.floor(wasteUnits * produce / (float) unitsPerWaste);
    }

    /**
     * Output to push on EXTRACT: only whole {@code produce} grants (e.g. 500 mB gas, not 1 mB at 100 waste units).
     */
    public int wasteEjectAmountFromWasteUnits(float wasteUnits) {
        int raw = wasteOutputAmountFromWasteUnits(wasteUnits);
        if (raw < produce) {
            return 0;
        }
        return (raw / produce) * produce;
    }

    public float wasteUnitsCostForOutputAmount(int outputAmount) {
        if (outputAmount <= 0 || produce <= 0) {
            return 0f;
        }
        return outputAmount * (float) unitsPerWaste / (float) produce;
    }
}
