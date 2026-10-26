package net.unfamily.colossal_reactors.datapack;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.unfamily.colossal_reactors.coolant.CoolantDefinition;
import net.unfamily.colossal_reactors.fuel.FuelDefinition;
import net.unfamily.colossal_reactors.fuel.FuelSubType;
import net.unfamily.colossal_reactors.heatingcoil.ConsumeOption;
import net.unfamily.colossal_reactors.heatingcoil.HeatingCoilDefinition;
import net.unfamily.colossal_reactors.heatsink.HeatSinkDefinition;
import net.unfamily.colossal_reactors.turbine.ElecCoilDefinition;
import net.unfamily.colossal_reactors.turbine.TurbineGenerationDefinition;
import net.unfamily.colossal_reactors.integration.mekanism.MaterialSelector;
import net.unfamily.colossal_reactors.integration.mekanism.MekChemicalHelper;
import net.unfamily.colossal_reactors.melter.MelterHeatEntry;
import net.unfamily.colossal_reactors.melter.MelterRecipe;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates datapack selectors (item/block/fluid id or tag) against {@link BuiltInRegistries}.
 * Uses the same registry surface as gameplay/JEI (not {@code RegistryAccess.fromRegistryOfRegistries},
 * which may have no tag bindings during client resource reload).
 */
public final class DatapackSelectorValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatapackSelectorValidator.class);

    private DatapackSelectorValidator() {}

    /** True when tag validation should use live server registries instead of bootstrap-only checks. */
    public static boolean registriesReady() {
        return ServerLifecycleHooks.getCurrentServer() != null;
    }

    private static boolean validationEnabled() {
        return tagHasEntries(TagKey.create(Registries.ITEM, ResourceLocation.parse("minecraft:logs")), BuiltInRegistries.ITEM)
                || itemExists(ResourceLocation.parse("minecraft:iron_ingot"));
    }

    public static boolean isResolvableItemSelector(String selector) {
        if (selector == null || selector.isBlank()) return false;
        if (selector.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(selector.substring(1));
            if (tagId == null) return false;
            return tagHasEntries(TagKey.create(Registries.ITEM, tagId), BuiltInRegistries.ITEM);
        }
        return itemExists(ResourceLocation.tryParse(selector));
    }

    public static boolean isResolvableBlockSelector(String selector) {
        if (selector == null || selector.isBlank()) return false;
        if (selector.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(selector.substring(1));
            if (tagId == null) return false;
            return tagHasEntries(TagKey.create(Registries.BLOCK, tagId), BuiltInRegistries.BLOCK);
        }
        return blockExists(ResourceLocation.tryParse(selector));
    }

    public static boolean isResolvableFluidSelector(String selector) {
        if (selector == null || selector.isBlank()) return false;
        if (MaterialSelector.isChemicalPrefix(selector)) {
            return isResolvableChemicalSelector(selector);
        }
        if (selector.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(selector.substring(1));
            if (tagId == null) return false;
            return tagHasEntries(TagKey.create(Registries.FLUID, tagId), BuiltInRegistries.FLUID);
        }
        return fluidExists(ResourceLocation.tryParse(selector));
    }

    /** Mek gas/chemical: {@code %namespace:id} or {@code %mekanism:tag_name} (mekanism:chemical/* only). */
    public static boolean isResolvableChemicalSelector(String selector) {
        if (!MekChemicalHelper.isLoaded() || selector == null || !selector.startsWith("%")) return false;
        ResourceLocation id = ResourceLocation.tryParse(selector.substring(1));
        if (id == null) return false;
        if (MekChemicalHelper.createStack(id, 1) != null) return true;
        return MekChemicalHelper.chemicalTagExists(id);
    }

    public static List<String> filterMaterialSelectors(List<String> selectors) {
        if (!validationEnabled()) return List.copyOf(selectors);
        List<String> out = new ArrayList<>();
        for (String selector : selectors) {
            boolean ok = MaterialSelector.isChemicalPrefix(selector)
                    ? isResolvableChemicalSelector(selector)
                    : (selector.startsWith("#") || selector.contains(":"))
                    ? isResolvableFluidSelector(selector) || isResolvableItemSelector(selector)
                    : isResolvableItemSelector(selector) || isResolvableFluidSelector(selector);
            if (ok) {
                out.add(selector);
            } else {
                LOGGER.debug("Dropped unresolved material selector: {}", selector);
            }
        }
        return out;
    }

    private static <T> boolean tagHasEntries(TagKey<T> tagKey, Registry<T> registry) {
        return registry.getTag(tagKey)
                .map(tag -> tag.stream().anyMatch(h -> {
                    Object value = h.value();
                    return !(value instanceof Fluid fluid && fluid == Fluids.EMPTY);
                }))
                .orElse(false);
    }

    private static boolean itemExists(@Nullable ResourceLocation id) {
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item != Items.AIR;
    }

    private static boolean blockExists(@Nullable ResourceLocation id) {
        if (id == null) return false;
        if ("air".equals(id.getPath()) && "minecraft".equals(id.getNamespace())) {
            return BuiltInRegistries.BLOCK.containsKey(id);
        }
        if (!BuiltInRegistries.BLOCK.containsKey(id)) return false;
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block != Blocks.AIR;
    }

    private static boolean fluidExists(@Nullable ResourceLocation id) {
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return false;
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        return fluid != Fluids.EMPTY;
    }

    public static List<String> filterItemSelectors(List<String> selectors) {
        if (!validationEnabled()) return List.copyOf(selectors);
        List<String> out = new ArrayList<>();
        for (String selector : selectors) {
            if (isResolvableItemSelector(selector)) {
                out.add(selector);
            } else {
                LOGGER.debug("Dropped unresolved item selector: {}", selector);
            }
        }
        return out;
    }

    public static List<String> filterBlockSelectors(List<String> selectors) {
        if (!validationEnabled()) return List.copyOf(selectors);
        List<String> out = new ArrayList<>();
        for (String selector : selectors) {
            if (isResolvableBlockSelector(selector)) {
                out.add(selector);
            } else {
                LOGGER.debug("Dropped unresolved block selector: {}", selector);
            }
        }
        return out;
    }

    public static List<String> filterFluidSelectors(List<String> selectors) {
        if (!validationEnabled()) return List.copyOf(selectors);
        List<String> out = new ArrayList<>();
        for (String selector : selectors) {
            if (isResolvableFluidSelector(selector)) {
                out.add(selector);
            } else {
                LOGGER.debug("Dropped unresolved fluid selector: {}", selector);
            }
        }
        return out;
    }

    @Nullable
    public static FuelDefinition sanitizeFuel(FuelDefinition def) {
        if (!validationEnabled()) return def;
        List<String> inputs = filterMaterialSelectors(def.inputs());
        if (inputs.isEmpty()) {
            String fallback = def.fuelId().toString();
            if (isResolvableItemSelector(fallback) || isResolvableChemicalSelector("%" + fallback)
                    || isResolvableFluidSelector(fallback)) {
                inputs = List.of(fallback);
            } else {
                LOGGER.debug("Skipped fuel {}: no resolvable inputs", def.fuelId());
                return null;
            }
        }
        String normalizedSub = FuelSubType.normalize(def.subType());
        if (normalizedSub == null) {
            LOGGER.debug("Skipped fuel {}: invalid sub_type '{}'", def.fuelId(), def.subType());
            return null;
        }
        if (!FuelSubType.inputsMatchSubType(normalizedSub, inputs)) {
            LOGGER.debug("Skipped fuel {}: inputs do not match sub_type {}", def.fuelId(), normalizedSub);
            return null;
        }
        String output = def.output();
        if (!FuelSubType.outputMatchesSubType(normalizedSub, output)) {
            LOGGER.debug("Skipped fuel {}: output '{}' does not match sub_type {}", def.fuelId(), output, normalizedSub);
            return null;
        }
        boolean outOk = switch (FuelSubType.parseOutput(normalizedSub)) {
            case CHEMICAL -> isResolvableChemicalSelector(output);
            case FLUID -> isResolvableFluidSelector(output);
            case ITEM -> isResolvableItemSelector(output);
        };
        if (!outOk) {
            LOGGER.debug("Skipped fuel {}: unresolved output '{}'", def.fuelId(), output);
            return null;
        }
        return new FuelDefinition(def.fuelId(), def.wasteId(), normalizedSub, inputs,
                def.consume(), output, def.produce(), def.unitsPerFuel(), def.unitsPerWaste(),
                def.baseRfPerTick(), def.baseFuelUnitsPerTick(), def.overwritable(), def.colorRgb());
    }

    @Nullable
    public static CoolantDefinition sanitizeCoolant(CoolantDefinition def) {
        if (!validationEnabled()) return def;
        List<String> inputs = filterMaterialSelectors(def.inputs());
        if (inputs.isEmpty()) {
            String fallback = def.coolantId().toString();
            if (isResolvableFluidSelector(fallback) || isResolvableChemicalSelector("%" + fallback)) {
                inputs = List.of(fallback);
            } else {
                LOGGER.debug("Skipped coolant {}: no resolvable inputs", def.coolantId());
                return null;
            }
        }
        List<String> outputs = filterMaterialSelectors(def.outputs());
        if (outputs.isEmpty() && def.output() != null && !def.output().isBlank()) {
            String legacy = def.output();
            if (isResolvableFluidSelector(legacy) || isResolvableChemicalSelector(legacy.startsWith("%") ? legacy : "%" + legacy)) {
                outputs = List.of(legacy);
            }
        }
        if (outputs.isEmpty()) {
            LOGGER.debug("Skipped coolant {}: no resolvable outputs", def.coolantId());
            return null;
        }
        return new CoolantDefinition(def.coolantId(), inputs, def.output(), outputs, def.rfIncrementPercent(), def.mbDecrementPercent(),
                def.reduceRfProduction(), def.rfToCoolantFactor(), def.steamPerCoolant(), def.overheatingMultiplier(),
                def.fluidColor(), def.outputColor(), def.overwritable());
    }

    @Nullable
    public static HeatSinkDefinition sanitizeHeatSink(HeatSinkDefinition def) {
        if (!validationEnabled()) return def;
        List<String> blocks = filterBlockSelectors(def.validBlocks());
        List<String> liquids = filterFluidSelectors(def.validLiquids());
        if (blocks.isEmpty() && liquids.isEmpty()) {
            LOGGER.debug("Skipped heat sink entry: no resolvable valid_blocks or valid_liquids");
            return null;
        }
        return new HeatSinkDefinition(blocks, liquids, def.fuelMultiplier(), def.energyMultiplier(),
                def.overheatingMultiplier(), def.mustSource());
    }

    @Nullable
    public static TurbineGenerationDefinition sanitizeTurbineGeneration(TurbineGenerationDefinition def) {
        if (!validationEnabled()) return def;
        List<String> inputs = filterMaterialSelectors(def.inputs());
        if (inputs.isEmpty()) {
            LOGGER.debug("Skipped turbine generation {}: no resolvable inputs", def.generationId());
            return null;
        }
        List<String> outputs = filterMaterialSelectors(def.outputs());
        if (outputs.isEmpty() && def.output() != null && !def.output().isBlank()) {
            String legacy = def.output();
            if (isResolvableFluidSelector(legacy) || isResolvableChemicalSelector(legacy.startsWith("%") ? legacy : "%" + legacy)) {
                outputs = List.of(legacy);
            }
        }
        if (outputs.isEmpty()) {
            LOGGER.debug("Skipped turbine generation {}: no resolvable outputs", def.generationId());
            return null;
        }
        return new TurbineGenerationDefinition(def.generationId(), inputs, def.output(), outputs, def.rfProduction(), def.overwritable());
    }

    @Nullable
    public static ElecCoilDefinition sanitizeElecCoil(ElecCoilDefinition def) {
        if (!validationEnabled()) return def;
        List<String> blocks = filterBlockSelectors(def.validBlocks());
        if (blocks.isEmpty()) {
            LOGGER.debug("Skipped elec coil entry: no resolvable valid_blocks");
            return null;
        }
        return new ElecCoilDefinition(blocks, def.effCoe(), def.effMax(), def.flowCoe(), def.flowMax());
    }

    public static boolean isMelterRecipeResolvable(MelterRecipe recipe) {
        if (!validationEnabled()) return true;
        boolean inputOk = recipe.inputIsTag()
                ? isResolvableItemSelector("#" + recipe.inputId())
                : isResolvableItemSelector(recipe.inputId().toString());
        boolean outputOk = recipe.outputIsTag()
                ? isResolvableFluidSelector("#" + recipe.outputFluidId())
                : isResolvableFluidSelector(recipe.outputFluidId().toString());
        if (!inputOk) LOGGER.debug("Skipped melter recipe: unresolved input {}", recipe.inputId());
        if (!outputOk) LOGGER.debug("Skipped melter recipe: unresolved output {}", recipe.outputFluidId());
        return inputOk && outputOk;
    }

    @Nullable
    public static MelterHeatEntry sanitizeMelterHeat(MelterHeatEntry entry) {
        if (!validationEnabled()) return entry;
        List<ResourceLocation> blockIds = new ArrayList<>();
        List<Boolean> blockIdIsTag = new ArrayList<>();
        for (int i = 0; i < entry.blockIds().size(); i++) {
            ResourceLocation id = entry.blockIds().get(i);
            boolean isTag = i < entry.blockIdIsTag().size() && entry.blockIdIsTag().get(i);
            String selector = isTag ? "#" + id : id.toString();
            if (isResolvableBlockSelector(selector)) {
                blockIds.add(id);
                blockIdIsTag.add(isTag);
            } else {
                LOGGER.debug("Dropped unresolved melter heat block selector: {}", selector);
            }
        }
        List<ResourceLocation> fluidIds = new ArrayList<>();
        List<Boolean> fluidIdIsTag = new ArrayList<>();
        for (int i = 0; i < entry.fluidIds().size(); i++) {
            ResourceLocation id = entry.fluidIds().get(i);
            boolean isTag = i < entry.fluidIdIsTag().size() && entry.fluidIdIsTag().get(i);
            String selector = isTag ? "#" + id : id.toString();
            if (isResolvableFluidSelector(selector)) {
                fluidIds.add(id);
                fluidIdIsTag.add(isTag);
            } else {
                LOGGER.debug("Dropped unresolved melter heat fluid selector: {}", selector);
            }
        }
        if (blockIds.isEmpty() && fluidIds.isEmpty()) {
            LOGGER.debug("Skipped melter heat entry: no resolvable blocks or fluids");
            return null;
        }
        return new MelterHeatEntry(blockIds, blockIdIsTag, fluidIds, fluidIdIsTag, entry.factor(), entry.notValid());
    }

    public static List<String> filterCatalystSelectors(List<String> selectors) {
        return filterItemSelectors(selectors);
    }

    @Nullable
    public static ConsumeOption sanitizeConsumeOption(ConsumeOption opt) {
        if (!validationEnabled()) return opt;
        ConsumeOption.FluidRequirement fluid = opt.fluid();
        if (fluid != null) {
            String selector = fluid.isTag() ? "#" + fluid.tagOrId() : fluid.tagOrId().toString();
            if (!isResolvableFluidSelector(selector)) {
                LOGGER.debug("Dropped unresolved heating coil fluid selector: {}", selector);
                fluid = null;
            }
        }
        ConsumeOption.ChemicalRequirement chemical = opt.chemical();
        if (chemical != null) {
            String selector = chemical.selector();
            if (!MaterialSelector.isChemicalPrefix(selector)) {
                LOGGER.debug("Dropped heating coil chemical: invalid selector {}", selector);
                chemical = null;
            } else if (!MekChemicalHelper.isGasSupportEnabled()) {
                chemical = null;
            }
            // Keep valid % selectors when gas support is enabled; Mek may not be loaded yet at reload time.
        }
        ConsumeOption.ItemRequirement item = opt.item();
        if (item != null) {
            String selector = item.isTag() ? "#" + item.tagOrId() : item.tagOrId().toString();
            if (!isResolvableItemSelector(selector)) {
                LOGGER.debug("Dropped unresolved heating coil item selector: {}", selector);
                item = null;
            }
        }
        if (fluid == null && chemical == null && item == null && opt.energy() == null && opt.burnable() == null) {
            return null;
        }
        return new ConsumeOption(fluid, chemical, item, opt.energy(), opt.burnable());
    }

    public static HeatingCoilDefinition sanitizeHeatingCoil(HeatingCoilDefinition def) {
        if (!validationEnabled()) return def;
        List<ConsumeOption> consume = new ArrayList<>();
        for (ConsumeOption opt : def.consume()) {
            ConsumeOption sanitized = sanitizeConsumeOption(opt);
            if (sanitized != null && !sanitized.isEmpty()) {
                consume.add(sanitized);
            }
        }
        return new HeatingCoilDefinition(def.id(), def.duration(), consume, def.allSides(),
                def.noItem(), def.noFluid(), def.noEnergy());
    }
}
