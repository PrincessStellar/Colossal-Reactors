package net.unfamily.colossal_reactors.heatsink;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.unfamily.colossal_reactors.datapack.DatapackSelectorValidator;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads heat sink definitions from datapack JSON: data/colossal_reactors/reactor_heat_sinks/*.json.
 * Each file is one entry (valid_blocks, valid_liquids, fuel, energy, overheating, must_source).
 * Interior air uses an entry with {@code valid_blocks: ["minecraft:air"]} (see builtin JSON). Unknown liquid / flowing treated as air uses the same multipliers, or a weak fallback if no air entry exists.
 * If no datapack entries are found, internal defaults are used.
 */
public final class HeatSinkLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeatSinkLoader.class);

    /** When no {@code minecraft:air} heat sink entry is loaded: weak overheating for stability. */
    private static final HeatSinkModifiers INTERIOR_AIR_FALLBACK = new HeatSinkModifiers(1.0, 1.0, 0.16);

    private static final String KEY_VALID_BLOCKS = "valid_blocks";
    private static final String KEY_VALID_LIQUIDS = "valid_liquids";
    private static final String KEY_FUEL = "fuel";
    private static final String KEY_ENERGY = "energy";
    private static final String KEY_OVERHEATING = "overheating";
    private static final String KEY_MUST_SOURCE = "must_source";

    /** Immutable snapshot; replaced atomically on reload so gameplay iteration cannot see ConcurrentModificationException. */
    private static volatile List<HeatSinkDefinition> DEFINITIONS = List.of();
    private static List<HeatSinkDefinition> rawDatapackEntries = List.of();

    /**
     * Block state lookup cache for {@link #isHeatSinkBlock} / {@link #getModifiersForBlockOrDefault} (reactor
     * validation and cache rebuilds walk every interior block). A new cache is started when the definitions,
     * the registry access or the tags change.
     */
    private static volatile BlockCache blockCache;
    private static volatile int tagGeneration;

    private record BlockCache(List<HeatSinkDefinition> definitions, RegistryAccess registryAccess, int tagGeneration,
                              Map<BlockState, Optional<HeatSinkModifiers>> entries) {}

    private HeatSinkLoader() {}

    /** Call when tags were (re)bound, e.g. on {@code TagsUpdatedEvent}: drops cached block lookups. */
    public static void onTagsUpdated() {
        tagGeneration++;
    }

    private static synchronized void rebuildDefinitions() {
        List<HeatSinkDefinition> next = new ArrayList<>();
        List<HeatSinkDefinition> source = rawDatapackEntries.isEmpty() ? buildInternalDefaults() : rawDatapackEntries;
        for (HeatSinkDefinition def : source) {
            HeatSinkDefinition sanitized = DatapackSelectorValidator.sanitizeHeatSink(def);
            if (sanitized != null) {
                next.add(sanitized);
            }
        }
        if (next.isEmpty()) {
            next.addAll(buildInternalDefaults());
        }
        DEFINITIONS = List.copyOf(next);
    }

    /**
     * Solid auto-placement tick skips synthetic air (index 0) and definitions whose blocks are only {@code minecraft:air} (no item to place).
     */
    public static boolean shouldSkipSolidHeatSinkAutoPlacement(int selectedHeatSinkIndex) {
        if (selectedHeatSinkIndex <= 0) return true;
        HeatSinkDefinition def = definitionForOptionIndex(selectedHeatSinkIndex);
        if (def == null || def.validBlocks().isEmpty()) return true;
        return def.validBlocks().stream().allMatch(HeatSinkLoader::isMinecraftAirInteriorSelector);
    }

    private static HeatSinkModifiers modifiersForInteriorAirLike() {
        for (HeatSinkDefinition def : DEFINITIONS) {
            for (String selector : def.validBlocks()) {
                if (isMinecraftAirInteriorSelector(selector)) {
                    return new HeatSinkModifiers(def.fuelMultiplier(), def.energyMultiplier(), def.overheatingMultiplier());
                }
            }
        }
        return INTERIOR_AIR_FALLBACK;
    }

    private static boolean isMinecraftAirInteriorSelector(String selector) {
        if (selector.startsWith("#")) return false;
        Identifier id = Identifier.tryParse(selector);
        return id != null && Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) && "air".equals(id.getPath());
    }

    /**
     * Air-only datapack entries stay in {@link #DEFINITIONS} for runtime modifiers, but are not separate
     * builder options (index 0 already is Air).
     */
    public static boolean isAirOnlyDefinition(HeatSinkDefinition def) {
        return def != null
                && def.validLiquids().isEmpty()
                && !def.validBlocks().isEmpty()
                && def.validBlocks().stream().allMatch(HeatSinkLoader::isMinecraftAirInteriorSelector);
    }

    /**
     * Maps builder option index to a non-air definition. Index 0 = synthetic Air (returns null).
     * Skips air-only datapack entries so they do not appear as a second "Air" option.
     */
    @Nullable
    public static HeatSinkDefinition definitionForOptionIndex(int optionIndex) {
        if (optionIndex <= 0) {
            return null;
        }
        int target = optionIndex - 1;
        int i = 0;
        for (HeatSinkDefinition def : DEFINITIONS) {
            if (isAirOnlyDefinition(def)) {
                continue;
            }
            if (i == target) {
                return def;
            }
            i++;
        }
        return null;
    }

    /**
     * Applies loaded datapack data: clears, then uses loaded list or internal defaults if empty.
     * Same {@code valid_blocks}+{@code valid_liquids} → last definition wins.
     */
    public static void applyLoaded(List<HeatSinkDefinition> loaded) {
        synchronized (HeatSinkLoader.class) {
            rawDatapackEntries = mergeLastWinsBySelectors(loaded);
            rebuildDefinitions();
        }
    }

    /** Semantic key for addon replace / JEI dedupe. */
    public static String mergeKey(HeatSinkDefinition def) {
        if (def == null) {
            return "";
        }
        return String.join("\u0001", def.validBlocks()) + "\u0002" + String.join("\u0001", def.validLiquids());
    }

    private static List<HeatSinkDefinition> mergeLastWinsBySelectors(@Nullable List<HeatSinkDefinition> loaded) {
        if (loaded == null || loaded.isEmpty()) {
            return List.of();
        }
        Map<String, HeatSinkDefinition> byKey = new LinkedHashMap<>();
        for (HeatSinkDefinition def : loaded) {
            if (def == null) {
                continue;
            }
            String key = mergeKey(def);
            if (key.isEmpty() || "\u0002".equals(key)) {
                continue;
            }
            byKey.put(key, def);
        }
        return List.copyOf(byKey.values());
    }

    private static List<HeatSinkDefinition> buildInternalDefaults() {
        List<HeatSinkDefinition> defaults = new ArrayList<>();
        defaults.add(new HeatSinkDefinition(
                List.of("minecraft:air"),
                List.of(),
                1.0, 1.0, 0.16, false));
        defaults.add(new HeatSinkDefinition(
                List.of(),
                List.of("#c:water"),
                1.05, 1.15, 1.05, false)); // water: source and flowing both valid
        defaults.add(new HeatSinkDefinition(
                List.of("#c:storage_blocks/diamond"),
                List.of(),
                1.8, 1.6, 1.8, true));
        defaults.add(new HeatSinkDefinition(
                List.of("#c:storage_blocks/emerald"),
                List.of(),
                1.8, 1.6, 1.8, true));
        defaults.add(new HeatSinkDefinition(
                List.of("#c:storage_blocks/netherite"),
                List.of(),
                1.7, 2.3, 1.7, true));
        defaults.add(new HeatSinkDefinition(
                List.of("#c:storage_blocks/gold"),
                List.of(),
                1.7, 1.5, 1.7, true));
        defaults.add(new HeatSinkDefinition(
                List.of("#c:storage_blocks/graphite", "#minecraft:ice"),
                List.of(),
                2.5, -5.0, 2.5, true));
        return defaults;
    }

    /** Parses a single heat sink definition from JSON (one file = one entry). Used by datapack reload listener. */
    public static HeatSinkDefinition parseEntry(JsonObject json, String sourcePath) {
        List<String> validBlocks = new ArrayList<>();
        if (json.has(KEY_VALID_BLOCKS) && json.get(KEY_VALID_BLOCKS).isJsonArray()) {
            for (JsonElement i : json.getAsJsonArray(KEY_VALID_BLOCKS)) {
                if (i.isJsonPrimitive()) validBlocks.add(i.getAsString());
            }
        }
        List<String> validLiquids = new ArrayList<>();
        if (json.has(KEY_VALID_LIQUIDS) && json.get(KEY_VALID_LIQUIDS).isJsonArray()) {
            for (JsonElement i : json.getAsJsonArray(KEY_VALID_LIQUIDS)) {
                if (i.isJsonPrimitive()) validLiquids.add(i.getAsString());
            }
        }
        if (validBlocks.isEmpty() && validLiquids.isEmpty()) {
            LOGGER.debug("Heat sink entry in {}: skipped (no valid_blocks or valid_liquids)", sourcePath);
            return null;
        }
        double fuel = json.has(KEY_FUEL) ? json.get(KEY_FUEL).getAsDouble() : 1.0;
        double energy = json.has(KEY_ENERGY) ? json.get(KEY_ENERGY).getAsDouble() : 1.0;
        // overheating: used only when config REACTOR_UNSTABILITY is enabled; default = fuel
        double overheating = json.has(KEY_OVERHEATING) ? json.get(KEY_OVERHEATING).getAsDouble() : fuel;
        boolean mustSource = !json.has(KEY_MUST_SOURCE) || json.get(KEY_MUST_SOURCE).getAsBoolean();
        return new HeatSinkDefinition(validBlocks, validLiquids, fuel, energy, overheating, mustSource);
    }

    /**
     * True if this block state is allowed as interior coolant: air, any block matching valid_blocks, or a liquid block (using fluid type/source identity for valid_liquids). Flowing or unknown liquid is allowed and treated as air (does not block the reactor).
     */
    public static boolean isHeatSinkBlock(BlockState state, RegistryAccess registryAccess) {
        if (state.isAir()) return true;
        return getModifiersForBlockCached(state, registryAccess) != null;
    }

    /** {@link #getModifiersForBlock} through {@link #blockCache}. */
    private static HeatSinkModifiers getModifiersForBlockCached(BlockState state, RegistryAccess registryAccess) {
        List<HeatSinkDefinition> definitions = DEFINITIONS;
        int generation = tagGeneration;
        BlockCache cache = blockCache;
        if (cache == null || cache.definitions() != definitions || cache.registryAccess() != registryAccess
                || cache.tagGeneration() != generation) {
            cache = new BlockCache(definitions, registryAccess, generation, new ConcurrentHashMap<>());
            blockCache = cache;
        }
        return cache.entries().computeIfAbsent(state, s -> Optional.ofNullable(getModifiersForBlock(s, registryAccess))).orElse(null);
    }

    /**
     * Returns fuel and energy multipliers for this block (air = 1.0, 1.0). Checks valid_blocks first, then if the state is a liquid block uses the fluid type (source identity) for valid_liquids/tag checks; flowing blocks use that same identity. When must_source is true, flowing is not counted as heat sink but is allowed and treated as air (1.0, 1.0, 1.0). Non-source that cannot be counted as valid heat sink (unknown liquid or flowing when must_source) does not block the reactor and is treated as air.
     */
    public static HeatSinkModifiers getModifiersForBlock(BlockState state, RegistryAccess registryAccess) {
        for (HeatSinkDefinition def : DEFINITIONS) {
            for (String selector : def.validBlocks()) {
                if (blockMatches(state, selector, registryAccess)) {
                    return new HeatSinkModifiers(def.fuelMultiplier(), def.energyMultiplier(), def.overheatingMultiplier());
                }
            }
        }
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty()) {
            // Always resolve to source for id/tag checks (flowing -> getSource())
            Fluid fluid = resolveToSource(fluidState.getType());
            for (HeatSinkDefinition def : DEFINITIONS) {
                for (String selector : def.validLiquids()) {
                    if (fluidMatches(fluid, selector, registryAccess)) {
                        // must_source and flowing: do not count as heat sink, but allow block (treat as air)
                        if (def.mustSource() && !fluidState.isSource()) {
                            return modifiersForInteriorAirLike();
                        }
                        return new HeatSinkModifiers(def.fuelMultiplier(), def.energyMultiplier(), def.overheatingMultiplier());
                    }
                }
            }
            // Unknown liquid (no valid_liquids match): do not block reactor, treat as air
            return modifiersForInteriorAirLike();
        }
        if (state.isAir()) {
            return modifiersForInteriorAirLike();
        }
        return null;
    }

    /**
     * Same as getModifiersForBlock but returns (1.0, 1.0) for unknown blocks (e.g. when only used after validation).
     */
    public static HeatSinkModifiers getModifiersForBlockOrDefault(BlockState state, RegistryAccess registryAccess) {
        HeatSinkModifiers m = getModifiersForBlockCached(state, registryAccess);
        return m != null ? m : new HeatSinkModifiers(1.0, 1.0, 1.0);
    }

    /**
     * Returns fuel, energy and overheating multipliers for this fluid from valid_liquids entries, or null if no match.
     */
    public static HeatSinkModifiers getModifiersForFluid(Fluid fluid, RegistryAccess registryAccess) {
        if (fluid == null || fluid == Fluids.EMPTY) return null;
        for (HeatSinkDefinition def : DEFINITIONS) {
            for (String selector : def.validLiquids()) {
                if (fluidMatches(fluid, selector, registryAccess)) {
                    if (def.mustSource() && !fluid.defaultFluidState().isSource()) {
                        continue; // flowing fluid not valid when must_source is true
                    }
                    return new HeatSinkModifiers(def.fuelMultiplier(), def.energyMultiplier(), def.overheatingMultiplier());
                }
            }
        }
        return null;
    }

    /** Same as getModifiersForFluid but returns (1.0, 1.0, 1.0) when fluid has no heat sink entry. */
    public static HeatSinkModifiers getModifiersForFluidOrDefault(Fluid fluid, RegistryAccess registryAccess) {
        HeatSinkModifiers m = getModifiersForFluid(fluid, registryAccess);
        return m != null ? m : new HeatSinkModifiers(1.0, 1.0, 1.0);
    }

    /**
     * Returns modifiers for the builder heat sink option index (0 = Air; 1.. = non-air definitions).
     * Used by GUI simulation when all heat sink positions are the same selected type.
     */
    public static HeatSinkModifiers getModifiersForHeatSinkIndex(RegistryAccess registryAccess, int index) {
        if (index <= 0) return modifiersForInteriorAirLike();
        HeatSinkDefinition def = definitionForOptionIndex(index);
        if (def == null) return new HeatSinkModifiers(1.0, 1.0, 1.0);
        if (!def.validBlocks().isEmpty()) {
            BlockState state = getFirstBlockStateFromSelector(def.validBlocks().getFirst(), registryAccess);
            return state != null ? getModifiersForBlockOrDefault(state, registryAccess) : new HeatSinkModifiers(1.0, 1.0, 1.0);
        }
        if (!def.validLiquids().isEmpty()) {
            Fluid fluid = getFirstFluidFromSelector(def.validLiquids().getFirst(), registryAccess);
            return getModifiersForFluidOrDefault(fluid, registryAccess);
        }
        return new HeatSinkModifiers(1.0, 1.0, 1.0);
    }

    @Nullable
    private static BlockState getFirstBlockStateFromSelector(String selector, RegistryAccess registryAccess) {
        var blockReg = registryAccess.lookupOrThrow(Registries.BLOCK);
        if (selector.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(selector.substring(1));
            if (tagId == null) return null;
            TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagId);
            return blockReg.get(tagKey)
                    .flatMap(holders -> holders.stream().findFirst())
                    .map(h -> h.value().defaultBlockState())
                    .orElse(null);
        }
        Identifier id = Identifier.tryParse(selector);
        if (id == null) return null;
        return blockReg.get(ResourceKey.create(Registries.BLOCK, id)).map(h -> h.value().defaultBlockState()).orElse(null);
    }

    @Nullable
    private static Fluid getFirstFluidFromSelector(String selector, RegistryAccess registryAccess) {
        if (selector.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(selector.substring(1));
            if (tagId == null) return null;
            TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, tagId);
            return registryAccess.lookup(Registries.FLUID)
                    .flatMap(l -> l.get(tagKey))
                    .flatMap(holders -> holders.stream().findFirst())
                    .map(h -> h.value())
                    .orElse(null);
        }
        Identifier id = Identifier.tryParse(selector);
        return id != null ? BuiltInRegistries.FLUID.getValue(id) : null;
    }

    /**
     * True if the builder's selected heat sink filter (index 1..) is a liquid type and the given fluid matches it.
     * Used by reactor builder: place liquid from tank only when the coolant filter is set to that liquid.
     */
    public static boolean isFluidMatchingSelectedHeatSink(RegistryAccess registryAccess, int selectedHeatSinkIndex, Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) return false;
        HeatSinkDefinition def = definitionForOptionIndex(selectedHeatSinkIndex);
        if (def == null || def.validLiquids().isEmpty()) return false;
        for (String selector : def.validLiquids()) {
            if (fluidMatches(fluid, selector, registryAccess)) {
                if (def.mustSource() && !fluid.defaultFluidState().isSource()) continue;
                return true;
            }
        }
        return false;
    }

    /**
     * True if the given block state matches the valid_blocks of the selected heat sink definition (index 1..).
     * Used by reactor builder to place only the chosen block type.
     */
    public static boolean isBlockMatchingSelectedHeatSink(BlockState state, int selectedHeatSinkIndex, RegistryAccess registryAccess) {
        HeatSinkDefinition def = definitionForOptionIndex(selectedHeatSinkIndex);
        if (def == null) return false;
        for (String selector : def.validBlocks()) {
            if (blockMatches(state, selector, registryAccess)) return true;
        }
        return false;
    }

    /** Returns a copy of all heat sink definitions (e.g. for JEI or other display). */
    public static List<HeatSinkDefinition> getAllDefinitions() {
        return DEFINITIONS;
    }

    /** Number of heat sink options for builder GUI: 1 (Air) + one per non-air definition. */
    public static int getHeatSinkOptionCount() {
        int nonAir = 0;
        for (HeatSinkDefinition def : DEFINITIONS) {
            if (!isAirOnlyDefinition(def)) {
                nonAir++;
            }
        }
        return 1 + nonAir;
    }

    /** True when the builder heat-sink option places interior liquid (valid_liquids non-empty). */
    public static boolean requiresLiquidPlacement(int selectedHeatSinkIndex) {
        HeatSinkDefinition def = definitionForOptionIndex(selectedHeatSinkIndex);
        return def != null && !def.validLiquids().isEmpty();
    }

    /** Display name for option index (0 = Air, 1.. = first block/fluid name from that definition). */
    public static Component getOptionDisplayName(RegistryAccess registryAccess, int index) {
        if (index <= 0) return Component.translatable("block.minecraft.air");
        HeatSinkDefinition def = definitionForOptionIndex(index);
        if (def == null) return Component.literal("?");
        if (!def.validBlocks().isEmpty()) {
            return net.unfamily.colossal_reactors.client.SelectorDisplayNames.fromFirstSelector(def.validBlocks(), registryAccess);
        }
        if (!def.validLiquids().isEmpty()) {
            return net.unfamily.colossal_reactors.client.SelectorDisplayNames.fromFirstSelector(def.validLiquids(), registryAccess);
        }
        return Component.literal("?");
    }

    /** Resolves flowing fluid to its source for id/tag lookup. For safety, identity checks always use the source. */
    private static Fluid resolveToSource(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) return fluid;
        if (fluid instanceof FlowingFluid flowing) return flowing.getSource();
        return fluid;
    }

    private static boolean fluidMatches(Fluid fluid, String selector, RegistryAccess registryAccess) {
        Fluid source = resolveToSource(fluid);
        Identifier fluidId = BuiltInRegistries.FLUID.getKey(source);
        if (fluidId == null) return false;
        if (selector.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(selector.substring(1));
            if (tagId == null) return false;
            TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, tagId);
            Optional<Holder.Reference<Fluid>> holder = registryAccess.lookupOrThrow(Registries.FLUID)
                    .get(ResourceKey.create(Registries.FLUID, fluidId));
            if (holder.isEmpty()) return false;
            return registryAccess.lookupOrThrow(Registries.FLUID)
                    .get(tagKey)
                    .map(holders -> holders.contains(holder.get()))
                    .orElse(false);
        }
        Identifier id = Identifier.tryParse(selector);
        return id != null && id.equals(fluidId);
    }

    /**
     * Block matches selector: tag (#namespace:path) or direct id.
     * Uses RegistryAccess so datapack/c block tags are resolved from the level's registry, not only built-in.
     */
    private static boolean blockMatches(BlockState state, String selector, RegistryAccess registryAccess) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) return false;
        if (selector.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(selector.substring(1));
            if (tagId == null) return false;
            TagKey<net.minecraft.world.level.block.Block> tagKey = TagKey.create(Registries.BLOCK, tagId);
            Optional<Holder.Reference<Block>> holder = registryAccess.lookupOrThrow(Registries.BLOCK)
                    .get(ResourceKey.create(Registries.BLOCK, blockId));
            if (holder.isEmpty()) return false;
            return registryAccess.lookupOrThrow(Registries.BLOCK)
                    .get(tagKey)
                    .map(holders -> holders.contains(holder.get()))
                    .orElse(false);
        }
        Identifier id = Identifier.tryParse(selector);
        if (id == null) return false;
        if (isMinecraftAirInteriorSelector(selector)) {
            return state.isAir();
        }
        return blockId.equals(id);
    }

    public record HeatSinkModifiers(double fuelMultiplier, double energyMultiplier, double overheatingMultiplier) {}

    /**
     * Result for simulation: weighted averages plus adjacent/non-adjacent energy, fuel and overheating sums.
     * RF = Base * (sumEnergyAdj + countNon*rodCount) * efficiencyFactor / rodCount.
     * sumOverheatingAdj + sumOverheatingNon are used for stability (surriscaldamento); default in JSON = same as fuel.
     */
    public record HeatSinkModifiersResult(
            double fuelMultiplier,
            double energyMultiplier,
            double sumEnergyAdj,
            double sumFuelAdj,
            double sumEnergyNon,
            double sumFuelNon,
            double sumOverheatingAdj,
            double sumOverheatingNon,
            int countAdj,
            int countNon
    ) {}
}
