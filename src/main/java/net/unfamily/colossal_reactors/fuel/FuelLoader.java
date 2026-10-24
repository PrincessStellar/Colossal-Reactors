package net.unfamily.colossal_reactors.fuel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.datapack.DatapackSelectorValidator;
import net.unfamily.colossal_reactors.integration.mekanism.MaterialSelector;
import net.unfamily.colossal_reactors.integration.mekanism.MekChemicalHelper;
import net.unfamily.colossal_reactors.blockentity.ReactorRodBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads fuel definitions from datapack JSON: data/colossal_reactors/reactor_fuel/*.json.
 * Each file is one definition (fuel_id, inputs, output, units_per_fuel, units_per_waste, etc.).
 * Internal defaults (uranium, azurite) are applied first; datapack entries override by fuel_id.
 */
public class FuelLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(FuelLoader.class);

    private static final String KEY_FUEL_ID = "fuel_id";
    private static final String KEY_WASTE_ID = "waste_id";
    private static final String KEY_INPUTS = "inputs";
    private static final String KEY_UNITS_PER_ITEM = "units_per_item";
    private static final String KEY_UNITS_PER_FUEL = "units_per_fuel";
    private static final String KEY_CONSUME = "consume";
    private static final String KEY_PRODUCE = "produce";
    private static final String KEY_UNITS_PER_WASTE = "units_per_waste";
    private static final String KEY_BASE_RF_PER_TICK = "base_rf_per_tick";
    private static final String KEY_BASE_FUEL_UNITS_PER_TICK = "base_fuel_units_per_tick";
    private static final String KEY_BASE_MB_PER_TICK_LEGACY = "base_mb_per_tick";
    private static final String KEY_OUTPUT = "output";
    private static final String KEY_SUB_TYPE = "sub_type";
    private static final String KEY_OVERWRITABLE = "overwritable";

    private static final Map<ResourceLocation, FuelDefinition> DEFINITIONS = new HashMap<>();

    /**
     * Applies loaded datapack data. Internal defaults only if datapack map is empty (no empty→builtin when recipes present).
     */
    public static void applyLoaded(Map<ResourceLocation, FuelDefinition> loaded) {
        DEFINITIONS.clear();
        if (loaded == null || loaded.isEmpty()) {
            registerInternalDefaults();
            return;
        }
        for (FuelDefinition def : loaded.values()) {
            FuelDefinition sanitized = DatapackSelectorValidator.sanitizeFuel(def);
            if (sanitized != null) {
                processEntry(sanitized);
            }
        }
    }

    private static void registerInternalDefaults() {
        ResourceLocation uraniumId = ReactorRodBlockEntity.URANIUM_FUEL_ID;
        int unitsPerFuel = 1000;
        int unitsPerWaste = 1000;
        double baseRf = 200.0;
        double baseFuelUnitsPerTick = 0.03;
        List<String> inputs = List.of("#c:ingots/uranium");
        String output = ColossalReactors.MODID + ":nuclear_waste";
        ResourceLocation nuclearWasteId = ResourceLocation.fromNamespaceAndPath(ColossalReactors.MODID, "nuclear_waste");
        DEFINITIONS.put(uraniumId, new FuelDefinition(uraniumId, nuclearWasteId, FuelDefinition.SUBTYPE_ITEM_ITEM,
                inputs, 1, output, 1, unitsPerFuel, unitsPerWaste, baseRf, baseFuelUnitsPerTick, true));

        // Azurite: 500 base RF, 500 fuel units per ingot, 1500 waste units per 1 waste item
        ResourceLocation azuriteId = ResourceLocation.fromNamespaceAndPath(ColossalReactors.MODID, "azurite");
        DEFINITIONS.put(azuriteId, new FuelDefinition(
                azuriteId,
                nuclearWasteId,
                FuelDefinition.SUBTYPE_ITEM_ITEM,
                List.of("#c:ingots/azurite"),
                1,
                ColossalReactors.MODID + ":nuclear_waste",
                1,
                500,
                1500,
                500.0,
                baseFuelUnitsPerTick,
                true
        ));
    }

    /** Parses a single fuel definition from JSON (one file = one entry). Used by datapack reload listener. */
    public static FuelDefinition parseEntry(JsonObject json, String sourcePath, boolean defaultOverwritable) {
        if (!json.has(KEY_FUEL_ID)) {
            LOGGER.warn("Fuel entry in {}: missing 'fuel_id'", sourcePath);
            return null;
        }
        ResourceLocation fuelId = ResourceLocation.tryParse(json.get(KEY_FUEL_ID).getAsString());
        if (fuelId == null) {
            LOGGER.warn("Fuel entry in {}: invalid fuel_id", sourcePath);
            return null;
        }
        List<String> inputs = new ArrayList<>();
        if (json.has(KEY_INPUTS) && json.get(KEY_INPUTS).isJsonArray()) {
            for (JsonElement i : json.getAsJsonArray(KEY_INPUTS)) {
                if (i.isJsonPrimitive()) inputs.add(i.getAsString());
            }
        }
        int consume = 1;
        if (json.has(KEY_CONSUME)) {
            consume = json.get(KEY_CONSUME).getAsInt();
        }
        String output = json.has(KEY_OUTPUT) ? json.get(KEY_OUTPUT).getAsString() : "";
        int produce = 1;
        if (json.has(KEY_PRODUCE)) {
            produce = json.get(KEY_PRODUCE).getAsInt();
        }
        int unitsPerFuel = 1000;
        int unitsPerWaste = 1000;
        if (json.has(KEY_UNITS_PER_FUEL)) unitsPerFuel = json.get(KEY_UNITS_PER_FUEL).getAsInt();
        else if (json.has(KEY_UNITS_PER_ITEM)) unitsPerFuel = json.get(KEY_UNITS_PER_ITEM).getAsInt();
        if (json.has(KEY_UNITS_PER_WASTE)) unitsPerWaste = json.get(KEY_UNITS_PER_WASTE).getAsInt();
        else if (json.has(KEY_UNITS_PER_ITEM)) unitsPerWaste = json.get(KEY_UNITS_PER_ITEM).getAsInt();
        double baseRf = json.has(KEY_BASE_RF_PER_TICK) ? json.get(KEY_BASE_RF_PER_TICK).getAsDouble() : 200.0;
        double baseFuelUnitsPerTick = json.has(KEY_BASE_FUEL_UNITS_PER_TICK) ? json.get(KEY_BASE_FUEL_UNITS_PER_TICK).getAsDouble()
                : json.has(KEY_BASE_MB_PER_TICK_LEGACY) ? json.get(KEY_BASE_MB_PER_TICK_LEGACY).getAsDouble()
                : 0.03;
        boolean overwritable = json.has(KEY_OVERWRITABLE) ? json.get(KEY_OVERWRITABLE).getAsBoolean() : defaultOverwritable;
        String rawSubType = json.has(KEY_SUB_TYPE) ? json.get(KEY_SUB_TYPE).getAsString() : FuelDefinition.SUBTYPE_ITEM_ITEM;
        String subType = FuelSubType.normalize(rawSubType);
        if (subType == null) {
            LOGGER.warn("Fuel entry in {}: invalid sub_type '{}'", sourcePath, rawSubType);
            subType = FuelDefinition.SUBTYPE_ITEM_ITEM;
        }
        ResourceLocation wasteId = fuelId;
        if (json.has(KEY_WASTE_ID)) {
            ResourceLocation parsed = ResourceLocation.tryParse(json.get(KEY_WASTE_ID).getAsString());
            if (parsed != null) {
                wasteId = parsed;
            }
        }
        Integer colorRgb = parseColorRgb(json);
        return new FuelDefinition(fuelId, wasteId, subType,
                inputs.isEmpty() ? List.of(fuelId.toString()) : List.copyOf(inputs),
                consume, output, produce, unitsPerFuel, unitsPerWaste, baseRf, baseFuelUnitsPerTick, overwritable, colorRgb);
    }

    private static final String KEY_COLOR = "color";

    /** Parses optional {@code "color": "#RRGGBB"} or {@code 0xRRGGBB}. */
    private static Integer parseColorRgb(JsonObject json) {
        if (!json.has(KEY_COLOR) || !json.get(KEY_COLOR).isJsonPrimitive()) {
            return null;
        }
        String raw = json.get(KEY_COLOR).getAsString().trim();
        if (raw.startsWith("#")) {
            raw = raw.substring(1);
        } else if (raw.startsWith("0x") || raw.startsWith("0X")) {
            raw = raw.substring(2);
        }
        try {
            int rgb = Integer.parseInt(raw, 16);
            return rgb & 0xFFFFFF;
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid fuel color '{}'", raw);
            return null;
        }
    }

    private static void processEntry(FuelDefinition def) {
        FuelDefinition existing = DEFINITIONS.get(def.fuelId());
        if (existing != null && !existing.overwritable()) {
            LOGGER.debug("Skipping fuel {}: existing definition is not overwritable", def.fuelId());
            return;
        }
        DEFINITIONS.put(def.fuelId(), def);
    }

    public static FuelDefinition get(ResourceLocation fuelId) {
        return DEFINITIONS.get(fuelId);
    }

    /**
     * Resolves a fuel definition for waste stored in the controller buffer under {@code wasteBufferId}.
     * Matches {@link FuelDefinition#wasteId()}. When {@code waste_id} was omitted in JSON, that equals
     * {@link FuelDefinition#fuelId()}. Does not remap stale buffer keys (e.g. old fuel_id waste after
     * {@code waste_id} was changed in datapack).
     */
    @Nullable
    public static FuelDefinition getDefinitionForWasteBuffer(ResourceLocation wasteBufferId) {
        if (wasteBufferId == null) {
            return null;
        }
        for (FuelDefinition def : DEFINITIONS.values()) {
            if (wasteBufferId.equals(def.wasteId())) {
                return def;
            }
        }
        for (FuelDefinition def : DEFINITIONS.values()) {
            if (def.outputMedium() != FuelMedium.CHEMICAL) {
                continue;
            }
            String output = def.output();
            if (output == null || !MaterialSelector.isChemicalPrefix(output)) {
                continue;
            }
            ResourceLocation outId = ResourceLocation.tryParse(output.substring(1));
            if (wasteBufferId.equals(outId)) {
                return def;
            }
        }
        FuelDefinition byKey = DEFINITIONS.get(wasteBufferId);
        if (byKey != null) {
            return byKey;
        }
        // Pre-fix mek_fissile used colossal_reactors:spent_nuclear_waste as buffer id (wrong Mek output).
        ResourceLocation oldMekWasteBuffer = ResourceLocation.fromNamespaceAndPath(
                ColossalReactors.MODID, "spent_nuclear_waste");
        if (wasteBufferId.equals(oldMekWasteBuffer)) {
            return DEFINITIONS.get(ResourceLocation.fromNamespaceAndPath(ColossalReactors.MODID, "mek_fissile"));
        }
        return null;
    }

    public static Map<ResourceLocation, FuelDefinition> getAll() {
        return new HashMap<>(DEFINITIONS);
    }

    /** Fuels with resolvable inputs and output (JEI + builder simulation). */
    public static List<FuelDefinition> getVisibleDefinitions() {
        return DEFINITIONS.values().stream()
                .filter(def -> DatapackSelectorValidator.sanitizeFuel(def) != null)
                .sorted(java.util.Comparator.comparing(d -> d.fuelId().toString()))
                .toList();
    }

    public static List<ResourceLocation> getVisibleFuelIds() {
        return getVisibleDefinitions().stream().map(FuelDefinition::fuelId).toList();
    }

    /** No longer used (disable removed); kept for API compatibility. */
    public static boolean isInputExcluded(String inputSelector) {
        return false;
    }

    /**
     * Finds the fuel definition that matches the given item (by item id or item tag). Returns null if excluded or no match.
     * Prefers exact item id match over tag match so e.g. uranium_ingot gets the uranium definition (with correct unitsPerFuel).
     */
    @Nullable
    public static FuelDefinition getDefinitionForItem(ItemStack stack, RegistryAccess registryAccess) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        FuelDefinition tagMatch = null;
        for (FuelDefinition def : DEFINITIONS.values()) {
            if (!def.acceptsInputMedium(FuelMedium.ITEM)) {
                continue;
            }
            for (String input : def.inputs()) {
                if (isInputExcluded(input)) continue;
                if (input.startsWith("#")) {
                    ResourceLocation tagId = ResourceLocation.tryParse(input.substring(1));
                    if (tagId == null) continue;
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                    var itemHolder = registryAccess.registryOrThrow(Registries.ITEM).getHolder(ResourceKey.create(Registries.ITEM, itemId)).orElse(null);
                    if (itemHolder == null) continue;
                    boolean inTag = registryAccess.lookup(Registries.ITEM)
                            .flatMap(l -> l.get(tagKey))
                            .map(holders -> holders.contains(itemHolder))
                            .orElse(false);
                    if (inTag) tagMatch = def;
                } else if (MaterialSelector.isChemicalPrefix(input)) {
                    continue;
                } else if (MaterialSelector.matchesItem(stack, input)) {
                    return def;
                }
            }
        }
        return tagMatch;
    }

    @Nullable
    public static FuelDefinition getDefinitionForChemical(Object chemicalStack) {
        if (!MekChemicalHelper.isLoaded() || chemicalStack == null || MekChemicalHelper.isEmpty(chemicalStack)) {
            return null;
        }
        for (FuelDefinition def : DEFINITIONS.values()) {
            if (!def.acceptsInputMedium(FuelMedium.CHEMICAL)) {
                continue;
            }
            for (String input : def.inputs()) {
                if (!MaterialSelector.isChemicalPrefix(input)) {
                    continue;
                }
                if (MaterialSelector.matchesChemical(chemicalStack, input)) {
                    return def;
                }
            }
        }
        return null;
    }

    /** True when the fluid matches any loaded fuel recipe's fluid {@code output} (waste). */
    public static boolean matchesAnyFluidFuelOutput(Fluid fluid, RegistryAccess registryAccess) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return false;
        }
        for (FuelDefinition def : DEFINITIONS.values()) {
            if (def.outputMedium() != FuelMedium.FLUID) {
                continue;
            }
            String output = def.output();
            if (output != null && MaterialSelector.matchesFluid(fluid, output)) {
                return true;
            }
        }
        return false;
    }

    /** True when the Mek chemical matches any loaded fuel recipe's chemical {@code output} (waste). */
    public static boolean matchesAnyChemicalFuelOutput(@Nullable Object chemicalStack) {
        if (!MekChemicalHelper.isLoaded() || MekChemicalHelper.isEmpty(chemicalStack)) {
            return false;
        }
        for (FuelDefinition def : DEFINITIONS.values()) {
            if (def.outputMedium() != FuelMedium.CHEMICAL) {
                continue;
            }
            String output = def.output();
            if (output != null && MaterialSelector.matchesChemical(chemicalStack, output)) {
                return true;
            }
        }
        return false;
    }

    /** Finds fuel that accepts this fluid (fluid-fluid / fluid tags). */
    @Nullable
    public static FuelDefinition getDefinitionForFluid(Fluid fluid, RegistryAccess registryAccess) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return null;
        }
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
        if (fluidId == null) {
            return null;
        }
        for (FuelDefinition def : DEFINITIONS.values()) {
            if (!def.acceptsInputMedium(FuelMedium.FLUID)) {
                continue;
            }
            for (String input : def.inputs()) {
                if (isInputExcluded(input)) {
                    continue;
                }
                if (input.startsWith("#")) {
                    ResourceLocation tagId = ResourceLocation.tryParse(input.substring(1));
                    if (tagId == null) {
                        continue;
                    }
                    TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, tagId);
                    var fluidHolder = registryAccess.registryOrThrow(Registries.FLUID)
                            .getHolder(ResourceKey.create(Registries.FLUID, fluidId))
                            .orElse(null);
                    if (fluidHolder == null) {
                        continue;
                    }
                    boolean inTag = registryAccess.lookup(Registries.FLUID)
                            .flatMap(l -> l.get(tagKey))
                            .map(holders -> holders.contains(fluidHolder))
                            .orElse(false);
                    if (inTag) {
                        return def;
                    }
                } else if (MaterialSelector.matchesFluid(fluid, input)) {
                    return def;
                }
            }
        }
        return null;
    }

    /** First chemical input selector for this fuel (for drain templates / JEI). */
    @Nullable
    public static String getFirstChemicalInputSelector(ResourceLocation fuelId) {
        FuelDefinition def = DEFINITIONS.get(fuelId);
        if (def == null) {
            return null;
        }
        for (String input : def.inputs()) {
            if (MaterialSelector.isChemicalPrefix(input)) {
                return input;
            }
        }
        return null;
    }

    /** True when this fuel ingests Mek gas ({@code sub_type} input medium chemical). */
    public static boolean hasChemicalInput(ResourceLocation fuelId) {
        FuelDefinition def = DEFINITIONS.get(fuelId);
        return def != null && def.acceptsInputMedium(FuelMedium.CHEMICAL) && getFirstChemicalInputSelector(fuelId) != null;
    }

    public static boolean hasFluidInput(ResourceLocation fuelId) {
        FuelDefinition def = DEFINITIONS.get(fuelId);
        return def != null && def.acceptsInputMedium(FuelMedium.FLUID);
    }

    /**
     * Returns a single item stack for the first valid input of this fuel type (for eject: convert fuel units back to items).
     * Caller must use definition's unitsPerFuel when converting fuel units back to item count.
     */
    public static ItemStack getFirstInputStack(ResourceLocation fuelId, RegistryAccess registryAccess) {
        FuelDefinition def = DEFINITIONS.get(fuelId);
        if (def == null || def.inputs().isEmpty()) return ItemStack.EMPTY;
        for (String input : def.inputs()) {
            if (isInputExcluded(input)) continue;
            if (input.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(input.substring(1));
                if (tagId == null) continue;
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                var optItem = registryAccess.lookup(Registries.ITEM)
                        .flatMap(l -> l.get(tagKey))
                        .stream()
                        .flatMap(holders -> holders.stream())
                        .findFirst()
                        .map(h -> h.value());
                if (optItem.isPresent()) return new ItemStack(optItem.get(), 1);
            } else if (!MaterialSelector.isChemicalPrefix(input)) {
                ResourceLocation id = ResourceLocation.tryParse(input);
                if (id != null) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    if (item != null && item != net.minecraft.world.item.Items.AIR) return new ItemStack(item, 1);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Returns a single item stack for the waste output of this fuel type (for tooltip display).
     * Output is either "#tag" (first item in tag) or "namespace:item_id".
     */
    public static ItemStack getFirstOutputStack(ResourceLocation fuelId, RegistryAccess registryAccess) {
        FuelDefinition def = DEFINITIONS.get(fuelId);
        if (def == null) return ItemStack.EMPTY;
        String output = def.output();
        if (output == null || output.isEmpty()) return ItemStack.EMPTY;
        if (output.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(output.substring(1));
            if (tagId == null) return ItemStack.EMPTY;
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
            var optItem = registryAccess.lookup(Registries.ITEM)
                    .flatMap(l -> l.get(tagKey))
                    .stream()
                    .flatMap(holders -> holders.stream())
                    .findFirst()
                    .map(h -> h.value());
            if (optItem.isPresent()) return new ItemStack(optItem.get(), 1);
        } else if (MaterialSelector.isChemicalPrefix(output)) {
            return ItemStack.EMPTY;
        } else {
            ResourceLocation id = ResourceLocation.tryParse(output);
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != null && item != net.minecraft.world.item.Items.AIR) return new ItemStack(item, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Display name for chemical fuel/waste (Mek), or null if not chemical. */
    @Nullable
    public static net.minecraft.network.chat.Component getChemicalDisplayName(String selector) {
        if (!MekChemicalHelper.isLoaded() || !MaterialSelector.isChemicalPrefix(selector)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(selector.substring(1));
        if (id == null) {
            return null;
        }
        Object stack = MekChemicalHelper.createStack(id, MekChemicalHelper.JEI_DISPLAY_AMOUNT_MB);
        if (stack == null) {
            return net.minecraft.network.chat.Component.literal(id.toString());
        }
        try {
            Object text = stack.getClass().getMethod("getTextComponent").invoke(stack);
            if (text instanceof net.minecraft.network.chat.Component c) {
                return c;
            }
        } catch (Throwable ignored) {
        }
        return net.minecraft.network.chat.Component.literal(id.toString());
    }
}
