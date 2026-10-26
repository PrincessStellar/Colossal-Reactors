package net.unfamily.colossal_reactors.datapack;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.unfamily.colossal_reactors.coolant.CoolantDefinition;
import net.unfamily.colossal_reactors.fuel.FuelDefinition;
import net.unfamily.colossal_reactors.fuel.FuelMedium;
import net.unfamily.colossal_reactors.fuel.FuelSubType;
import net.unfamily.colossal_reactors.heatingcoil.ConsumeOption;
import net.unfamily.colossal_reactors.heatingcoil.HeatingCoilDefinition;
import net.unfamily.colossal_reactors.integration.mekanism.MaterialSelector;
import net.unfamily.colossal_reactors.integration.mekanism.MekChemicalHelper;
import net.unfamily.colossal_reactors.heatsink.HeatSinkDefinition;
import net.unfamily.colossal_reactors.turbine.ElecCoilDefinition;
import net.unfamily.colossal_reactors.turbine.TurbineGenerationDefinition;
import net.unfamily.colossal_reactors.melter.MelterHeatEntry;
import net.unfamily.colossal_reactors.melter.MelterRecipe;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates datapack selectors (item/block/fluid id or tag). Empty or missing tags are dropped
 * selectively so entries like ruby/sapphire/peridot are skipped when the player has no gem blocks.
 */
public final class DatapackSelectorValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatapackSelectorValidator.class);

    private DatapackSelectorValidator() {}

    /** True when block/fluid/item tags can be resolved against a loaded world or server. */
    public static boolean registriesReady() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return true;
        }
        return clientLevelRegistryAccess() != null;
    }

    /**
     * Skip selector filtering when registries/tags are not ready (e.g. early client resource reload).
     * Matches 1.21.1: without this, fuel JSON (e.g. uranium 400 RF/t) is dropped and internal defaults (200) remain.
     */
    private static boolean validationEnabled() {
        Identifier logs = Identifier.parse("minecraft:logs");
        Identifier iron = Identifier.parse("minecraft:iron_ingot");
        return itemTagHasEntries(TagKey.create(Registries.ITEM, logs)) || itemExists(iron);
    }

    @Nullable
    private static RegistryAccess activeRegistryAccess() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        return clientLevelRegistryAccess();
    }

    /**
     * Client-only registry access without referencing {@code Minecraft} in this class file
     * (dedicated server class loading would pull {@code IntegratedServer} otherwise).
     */
    @Nullable
    private static RegistryAccess clientLevelRegistryAccess() {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return null;
        }
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            if (mc == null) {
                return null;
            }
            Object level = mcClass.getField("level").get(mc);
            if (level != null) {
                return (RegistryAccess) level.getClass().getMethod("registryAccess").invoke(level);
            }
            Object sp = mcClass.getMethod("getSingleplayerServer").invoke(mc);
            if (sp != null) {
                return (RegistryAccess) sp.getClass().getMethod("registryAccess").invoke(sp);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Client classes unavailable or world not loaded yet.
        }
        return null;
    }

    private static Registry<Item> gameItemRegistry() {
        RegistryAccess access = activeRegistryAccess();
        if (access != null) {
            return access.lookupOrThrow(Registries.ITEM);
        }
        return BuiltInRegistries.ITEM;
    }

    private static Registry<Block> gameBlockRegistry() {
        RegistryAccess access = activeRegistryAccess();
        if (access != null) {
            return access.lookupOrThrow(Registries.BLOCK);
        }
        return BuiltInRegistries.BLOCK;
    }

    private static Registry<Fluid> gameFluidRegistry() {
        RegistryAccess access = activeRegistryAccess();
        if (access != null) {
            return access.lookupOrThrow(Registries.FLUID);
        }
        return BuiltInRegistries.FLUID;
    }

    public static boolean isResolvableItemSelector(String selector) {
        if (selector == null || selector.isBlank()) return false;
        if (selector.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(selector.substring(1));
            if (tagId == null) return false;
            return itemTagHasEntries(TagKey.create(Registries.ITEM, tagId));
        }
        return itemExists(Identifier.tryParse(selector));
    }

    public static boolean isResolvableBlockSelector(String selector) {
        if (selector == null || selector.isBlank()) return false;
        if (selector.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(selector.substring(1));
            if (tagId == null) return false;
            return blockTagHasEntries(TagKey.create(Registries.BLOCK, tagId));
        }
        return blockExists(Identifier.tryParse(selector));
    }

    public static boolean isResolvableFluidSelector(String selector) {
        if (selector == null || selector.isBlank()) return false;
        if (selector.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(selector.substring(1));
            if (tagId == null) return false;
            return fluidTagHasEntries(TagKey.create(Registries.FLUID, tagId));
        }
        return fluidExists(Identifier.tryParse(selector));
    }

    /** Mek gas/chemical: {@code %namespace:id} or {@code %mekanism:tag_name}. */
    public static boolean isResolvableChemicalSelector(String selector) {
        if (!MekChemicalHelper.isLoaded() || selector == null || !selector.startsWith("%")) return false;
        String raw = selector.substring(1);
        Identifier id = Identifier.tryParse(raw);
        if (id == null) return false;
        if (MekChemicalHelper.createStack(id, 1) != null) return true;
        return MekChemicalHelper.chemicalTagExists(id);
    }

    /** Tag must have at least one member in game registry or built-in registry. */
    private static boolean itemTagHasEntries(TagKey<Item> tagKey) {
        return tagHasEntries(tagKey, gameItemRegistry()) || tagHasEntries(tagKey, BuiltInRegistries.ITEM);
    }

    private static boolean blockTagHasEntries(TagKey<Block> tagKey) {
        return tagHasEntries(tagKey, gameBlockRegistry()) || tagHasEntries(tagKey, BuiltInRegistries.BLOCK);
    }

    private static boolean fluidTagHasEntries(TagKey<Fluid> tagKey) {
        return tagHasEntries(tagKey, gameFluidRegistry()) || tagHasEntries(tagKey, BuiltInRegistries.FLUID);
    }

    private static <T> boolean tagHasEntries(TagKey<T> tagKey, Registry<T> registry) {
        return registry.get(tagKey)
                .map(tag -> tag.stream().anyMatch(h -> {
                    Object value = h.value();
                    return !(value instanceof Fluid fluid && fluid == Fluids.EMPTY);
                }))
                .orElse(false);
    }

    private static boolean itemExists(@Nullable Identifier id) {
        if (id == null) return false;
        if (itemExistsIn(id, gameItemRegistry())) return true;
        return activeRegistryAccess() != null && itemExistsIn(id, BuiltInRegistries.ITEM);
    }

    private static boolean itemExistsIn(Identifier id, Registry<Item> registry) {
        if (!registry.containsKey(id)) return false;
        Item item = registry.getValue(id);
        return item != null && item != Items.AIR;
    }

    private static boolean blockExists(@Nullable Identifier id) {
        if (id == null) return false;
        if ("air".equals(id.getPath()) && "minecraft".equals(id.getNamespace())) {
            return blockExistsIn(id, gameBlockRegistry()) || blockExistsIn(id, BuiltInRegistries.BLOCK);
        }
        if (blockExistsIn(id, gameBlockRegistry())) return true;
        return activeRegistryAccess() != null && blockExistsIn(id, BuiltInRegistries.BLOCK);
    }

    private static boolean blockExistsIn(Identifier id, Registry<Block> registry) {
        if (!registry.containsKey(id)) return false;
        Block block = registry.getValue(id);
        return block != null && block != Blocks.AIR;
    }

    private static boolean fluidExists(@Nullable Identifier id) {
        if (id == null) return false;
        if (fluidExistsIn(id, gameFluidRegistry())) return true;
        return activeRegistryAccess() != null && fluidExistsIn(id, BuiltInRegistries.FLUID);
    }

    private static boolean fluidExistsIn(Identifier id, Registry<Fluid> registry) {
        if (!registry.containsKey(id)) return false;
        Fluid fluid = registry.getValue(id);
        return fluid != null && fluid != Fluids.EMPTY;
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

    public static List<String> filterChemicalSelectors(List<String> selectors) {
        if (!validationEnabled()) return List.copyOf(selectors);
        List<String> out = new ArrayList<>();
        for (String selector : selectors) {
            if (isResolvableChemicalSelector(selector)) {
                out.add(selector);
            } else {
                LOGGER.debug("Dropped unresolved chemical selector: {}", selector);
            }
        }
        return out;
    }

    /** Fluid, item, or Mek chemical ({@code %}) selectors — drops unresolved entries. */
    public static List<String> filterMaterialSelectors(List<String> selectors) {
        if (!validationEnabled()) return List.copyOf(selectors);
        List<String> out = new ArrayList<>();
        for (String selector : selectors) {
            boolean ok = MaterialSelector.isChemicalPrefix(selector)
                    ? isResolvableChemicalSelector(selector)
                    : (selector != null && (selector.startsWith("#") || selector.contains(":")))
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

    @Nullable
    public static FuelDefinition sanitizeFuel(FuelDefinition def) {
        if (!validationEnabled()) return def;
        String normalizedSub = FuelSubType.normalize(def.subType());
        if (normalizedSub == null) {
            LOGGER.debug("Skipped fuel {}: invalid sub_type '{}'", def.fuelId(), def.subType());
            return null;
        }
        FuelMedium inputMedium = FuelSubType.parseInput(normalizedSub);
        FuelMedium outputMedium = FuelSubType.parseOutput(normalizedSub);
        if ((inputMedium == FuelMedium.CHEMICAL || outputMedium == FuelMedium.CHEMICAL)
                && !MekChemicalHelper.isLoaded()) {
            LOGGER.debug("Skipped fuel {}: chemical sub_type requires Mekanism", def.fuelId());
            return null;
        }
        List<String> inputs = switch (inputMedium) {
            case FLUID -> filterFluidSelectors(def.inputs());
            case CHEMICAL -> filterChemicalSelectors(def.inputs());
            case ITEM -> filterItemSelectors(def.inputs());
        };
        if (inputs.isEmpty()) {
            String fallback = def.fuelId().toString();
            boolean ok = switch (inputMedium) {
                case FLUID -> isResolvableFluidSelector(fallback);
                case CHEMICAL -> isResolvableChemicalSelector("%" + fallback);
                case ITEM -> isResolvableItemSelector(fallback);
            };
            if (ok) {
                inputs = List.of(inputMedium == FuelMedium.CHEMICAL ? "%" + fallback : fallback);
            } else {
                LOGGER.debug("Skipped fuel {}: no resolvable inputs", def.fuelId());
                return null;
            }
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
        boolean outOk = switch (outputMedium) {
            case FLUID -> isResolvableFluidSelector(output);
            case CHEMICAL -> isResolvableChemicalSelector(output);
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
            if (isResolvableFluidSelector(legacy)
                    || isResolvableChemicalSelector(legacy.startsWith("%") ? legacy : "%" + legacy)) {
                outputs = List.of(legacy);
            }
        }
        if (outputs.isEmpty()) {
            LOGGER.debug("Skipped coolant {}: no resolvable outputs", def.coolantId());
            return null;
        }
        CoolantDefinition built = new CoolantDefinition(def.coolantId(), inputs, def.output(), outputs,
                def.rfIncrementPercent(), def.mbDecrementPercent(),
                def.reduceRfProduction(), def.rfToCoolantFactor(), def.steamPerCoolant(), def.overheatingMultiplier(),
                def.fluidColor(), def.outputColor(), def.overwritable());
        String liquidOut = built.liquidOutputSelector();
        String gasOut = built.gasOutputSelector();
        boolean liquidOk = liquidOut != null && !liquidOut.isBlank() && isResolvableFluidSelector(liquidOut);
        boolean gasOk = gasOut != null && isResolvableChemicalSelector(gasOut);
        if (!liquidOk && !gasOk) {
            LOGGER.debug("Skipped coolant {}: no resolvable liquid or chemical output", def.coolantId());
            return null;
        }
        return built;
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
            if (isResolvableFluidSelector(legacy)
                    || isResolvableChemicalSelector(legacy.startsWith("%") ? legacy : "%" + legacy)) {
                outputs = List.of(legacy);
            }
        }
        if (outputs.isEmpty()) {
            LOGGER.debug("Skipped turbine generation {}: no resolvable outputs", def.generationId());
            return null;
        }
        return new TurbineGenerationDefinition(def.generationId(), inputs, def.output(), outputs,
                def.rfProduction(), def.overwritable());
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
        List<Identifier> blockIds = new ArrayList<>();
        List<Boolean> blockIdIsTag = new ArrayList<>();
        for (int i = 0; i < entry.blockIds().size(); i++) {
            Identifier id = entry.blockIds().get(i);
            boolean isTag = i < entry.blockIdIsTag().size() && entry.blockIdIsTag().get(i);
            String selector = isTag ? "#" + id : id.toString();
            if (isResolvableBlockSelector(selector)) {
                blockIds.add(id);
                blockIdIsTag.add(isTag);
            } else {
                LOGGER.debug("Dropped unresolved melter heat block selector: {}", selector);
            }
        }
        List<Identifier> fluidIds = new ArrayList<>();
        List<Boolean> fluidIdIsTag = new ArrayList<>();
        for (int i = 0; i < entry.fluidIds().size(); i++) {
            Identifier id = entry.fluidIds().get(i);
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
            } else if (!MekChemicalHelper.isLoaded() || !isResolvableChemicalSelector(selector)) {
                chemical = null;
            }
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
