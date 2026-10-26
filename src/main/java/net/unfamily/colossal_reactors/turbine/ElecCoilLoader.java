package net.unfamily.colossal_reactors.turbine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.datapack.DatapackSelectorValidator;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads elec coil block definitions for turbine coil zone matching.
 */
public final class ElecCoilLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElecCoilLoader.class);

    private static final String KEY_VALID_BLOCKS = "valid_blocks";
    private static final String KEY_EFF_COE = "eff_coe";
    private static final String KEY_EFF_MAX = "eff_max";
    private static final String KEY_FLOW_COE = "flow_coe";
    private static final String KEY_FLOW_MAX = "flow_max";

    private static final List<ElecCoilDefinition> DEFINITIONS = new ArrayList<>();
    /** Parsed datapack entries; sanitized when registries/tags are ready (see {@link #rebuildDefinitions()}). */
    private static List<ElecCoilDefinition> rawDatapackCoils = List.of();

    private ElecCoilLoader() {}

    public static void applyLoaded(List<ElecCoilDefinition> loaded) {
        synchronized (ElecCoilLoader.class) {
            rawDatapackCoils = mergeLastWinsByValidBlocks(loaded);
            if (DatapackSelectorValidator.registriesReady()) {
                rebuildDefinitions();
            }
        }
    }

    /**
     * Same {@code valid_blocks} list → last definition wins (addon packs can replace a CR coil).
     * Distinct selector lists stay additive.
     */
    private static List<ElecCoilDefinition> mergeLastWinsByValidBlocks(@Nullable List<ElecCoilDefinition> loaded) {
        if (loaded == null || loaded.isEmpty()) {
            return List.of();
        }
        Map<String, ElecCoilDefinition> byBlocks = new LinkedHashMap<>();
        for (ElecCoilDefinition def : loaded) {
            if (def == null || def.validBlocks().isEmpty()) {
                continue;
            }
            byBlocks.put(mergeKey(def), def);
        }
        return List.copyOf(byBlocks.values());
    }

    /** Semantic key for addon replace / JEI dedupe. */
    public static String mergeKey(ElecCoilDefinition def) {
        if (def == null || def.validBlocks().isEmpty()) {
            return "";
        }
        return String.join("\u0001", def.validBlocks());
    }

    public static void rebuildDefinitions() {
        synchronized (ElecCoilLoader.class) {
            rebuildDefinitionsInner();
        }
    }

    private static void rebuildDefinitionsInner() {
        DEFINITIONS.clear();
        double empty = Config.TURBINE_EMPTY_COIL_EFFICIENCY.get();
        // Air: RF efficiency from config; flow stays neutral 1.0 (controller-column average).
        DEFINITIONS.add(new ElecCoilDefinition(List.of("minecraft:air"), empty, empty, 1.0, 1.0));
        int rawCount = rawDatapackCoils.size();
        for (ElecCoilDefinition def : rawDatapackCoils) {
            ElecCoilDefinition sanitized = DatapackSelectorValidator.sanitizeElecCoil(def);
            if (sanitized == null || isAirOnlyDefinition(sanitized)) {
                continue;
            }
            DEFINITIONS.add(sanitized);
        }
        if (rawCount > 0 && DEFINITIONS.size() <= 1) {
            LOGGER.warn("Elec coil datapack had {} entries but none applied after sanitize (check valid_blocks / tags)", rawCount);
        }
    }

    /** All loaded entries (includes air-only / unresolved). JEI uses {@link #getVisibleDefinitions()} only. */
    public static List<ElecCoilDefinition> getJeIDefinitions() {
        return List.copyOf(DEFINITIONS);
    }

    /** True when this coil has resolvable blocks for the current registries (JEI + builder; includes air). */
    public static boolean isVisibleInJei(ElecCoilDefinition def) {
        return def != null && DatapackSelectorValidator.sanitizeElecCoil(def) != null;
    }

    /** Builder coil options with same visibility rules as JEI (includes air; excludes unresolved selectors). */
    public static List<ElecCoilDefinition> getVisibleDefinitions() {
        return getJeIDefinitions().stream()
                .filter(ElecCoilLoader::isVisibleInJei)
                .toList();
    }

    public static boolean isAirOnlyDefinition(ElecCoilDefinition def) {
        return def != null && !def.validBlocks().isEmpty()
                && def.validBlocks().stream().allMatch(ElecCoilLoader::isMinecraftAirSelector);
    }

    @Nullable
    public static ElecCoilDefinition parseEntry(JsonObject json, String sourcePath) {
        List<String> validBlocks = new ArrayList<>();
        if (json.has(KEY_VALID_BLOCKS) && json.get(KEY_VALID_BLOCKS).isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray(KEY_VALID_BLOCKS)) {
                if (el.isJsonPrimitive()) validBlocks.add(el.getAsString());
            }
        }
        if (validBlocks.isEmpty()) {
            LOGGER.debug("Elec coil entry in {}: no valid_blocks", sourcePath);
            return null;
        }
        double effCoe = json.has(KEY_EFF_COE) ? json.get(KEY_EFF_COE).getAsDouble() : 1.0;
        double effMax = json.has(KEY_EFF_MAX) ? json.get(KEY_EFF_MAX).getAsDouble() : effCoe;
        double flowCoe = json.has(KEY_FLOW_COE) ? json.get(KEY_FLOW_COE).getAsDouble() : 1.0;
        double flowMax = json.has(KEY_FLOW_MAX) ? json.get(KEY_FLOW_MAX).getAsDouble() : flowCoe;
        return new ElecCoilDefinition(validBlocks, effCoe, effMax, flowCoe, flowMax);
    }

    @Nullable
    public static ElecCoilModifiers getModifiersForBlock(BlockState state, RegistryAccess registryAccess) {
        for (ElecCoilDefinition def : DEFINITIONS) {
            for (String selector : def.validBlocks()) {
                if (blockMatches(state, selector, registryAccess)) {
                    return new ElecCoilModifiers(def.effCoe(), def.effMax(), def.flowCoe(), def.flowMax());
                }
            }
        }
        if (state.isAir()) {
            double empty = Config.TURBINE_EMPTY_COIL_EFFICIENCY.get();
            return new ElecCoilModifiers(empty, empty, 1.0, 1.0);
        }
        return null;
    }

    public static ElecCoilModifiers getModifiersForBlockOrDefault(BlockState state, RegistryAccess registryAccess) {
        ElecCoilModifiers m = getModifiersForBlock(state, registryAccess);
        if (m != null) return m;
        double empty = Config.TURBINE_EMPTY_COIL_EFFICIENCY.get();
        return new ElecCoilModifiers(empty, empty, 1.0, 1.0);
    }

    public static boolean isCoilBlock(BlockState state, RegistryAccess registryAccess) {
        return getModifiersForBlock(state, registryAccess) != null;
    }

    public static List<ElecCoilDefinition> getAllDefinitions() {
        return List.copyOf(DEFINITIONS);
    }

    public static int getCoilOptionCount() {
        return DEFINITIONS.size();
    }

    public static boolean shouldSkipSolidCoilAutoPlacement(int selectedCoilIndex) {
        if (selectedCoilIndex < 0 || selectedCoilIndex >= DEFINITIONS.size()) return true;
        ElecCoilDefinition def = DEFINITIONS.get(selectedCoilIndex);
        if (def.validBlocks().isEmpty()) return true;
        return def.validBlocks().stream().allMatch(ElecCoilLoader::isMinecraftAirSelector);
    }

    /** First placeable block state for builder auto-fill (supports tags). */
    @Nullable
    public static BlockState placementStateForOption(int selectedCoilIndex, RegistryAccess registryAccess) {
        if (shouldSkipSolidCoilAutoPlacement(selectedCoilIndex)) {
            return null;
        }
        for (String selector : DEFINITIONS.get(selectedCoilIndex).validBlocks()) {
            BlockState state = getFirstBlockStateFromSelector(selector, registryAccess);
            if (state != null && !state.isAir()) {
                return state;
            }
        }
        return null;
    }

    public static boolean itemMatchesSelectedCoil(ItemStack stack, int selectedCoilIndex, RegistryAccess registryAccess) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        return isBlockMatchingSelectedCoil(blockItem.getBlock().defaultBlockState(),
                selectedCoilIndex, registryAccess);
    }

    public static boolean isBlockMatchingSelectedCoil(BlockState state, int selectedCoilIndex, RegistryAccess registryAccess) {
        if (selectedCoilIndex < 0 || selectedCoilIndex >= DEFINITIONS.size()) return false;
        for (String selector : DEFINITIONS.get(selectedCoilIndex).validBlocks()) {
            if (blockMatches(state, selector, registryAccess)) return true;
        }
        return false;
    }

    /** Display name for option index (0 = Air, 1.. = first block name from that definition). */
    public static Component getOptionDisplayName(RegistryAccess registryAccess, int index) {
        if (index < 0 || index >= DEFINITIONS.size()) return Component.literal("?");
        ElecCoilDefinition def = DEFINITIONS.get(index);
        if (isAirOnlyDefinition(def)) {
            return Component.translatable("block.minecraft.air");
        }
        if (!def.validBlocks().isEmpty()) {
            return net.unfamily.colossal_reactors.client.SelectorDisplayNames.fromFirstSelector(def.validBlocks(), registryAccess);
        }
        return Component.literal("?");
    }

    @Nullable
    private static BlockState getFirstBlockStateFromSelector(String selector, RegistryAccess registryAccess) {
        Registry<Block> blockReg = registryAccess.lookupOrThrow(Registries.BLOCK);
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
        return blockReg.get(id).map(h -> h.value().defaultBlockState()).orElse(null);
    }

    private static boolean isMinecraftAirSelector(String selector) {
        if (selector.startsWith("#")) return false;
        Identifier id = Identifier.tryParse(selector);
        return id != null && Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) && "air".equals(id.getPath());
    }

    private static boolean blockMatches(BlockState state, String selector, RegistryAccess registryAccess) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) return false;
        if (selector.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(selector.substring(1));
            if (tagId == null) return false;
            TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagId);
            Registry<Block> blocks = registryAccess.lookupOrThrow(Registries.BLOCK);
            var holder = blocks.get(ResourceKey.create(Registries.BLOCK, blockId));
            if (holder.isEmpty()) return false;
            return blocks.get(tagKey).map(tag -> tag.contains(holder.get())).orElse(false);
        }
        Identifier id = Identifier.tryParse(selector);
        if (id == null) return false;
        if (isMinecraftAirSelector(selector)) {
            return state.isAir();
        }
        return blockId.equals(id);
    }

    public record ElecCoilModifiers(double effCoe, double effMax, double flowCoe, double flowMax) {
        public ElecCoilModifiers(double effCoe, double effMax) {
            this(effCoe, effMax, 1.0, 1.0);
        }
    }
}
