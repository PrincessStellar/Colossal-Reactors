package net.unfamily.colossal_reactors.melter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.unfamily.colossal_reactors.datapack.DatapackSelectorValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads melter heat definitions from datapack (type colossal_reactors:melter_heats).
 * valid_blocks: array of { "blocks": ["id"] or ["#tag"], "fluids": ["#tag"], "factor": number }.
 */
public final class MelterHeatsLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(MelterHeatsLoader.class);
    private static final String KEY_BLOCKS = "blocks";
    private static final String KEY_FLUIDS = "fluids";
    private static final String KEY_FACTOR = "factor";
    private static final String KEY_NOT_VALID = "not_valid";

    private static final List<MelterHeatEntry> ENTRIES = new ArrayList<>();

    public static void applyLoaded(List<MelterHeatEntry> loaded) {
        ENTRIES.clear();
        for (MelterHeatEntry entry : mergeLastWinsBySelectors(loaded)) {
            MelterHeatEntry sanitized = DatapackSelectorValidator.sanitizeMelterHeat(entry);
            if (sanitized != null) {
                ENTRIES.add(sanitized);
            }
        }
    }

    /** Same block/fluid selector sets → last entry wins (addon replace). */
    public static String mergeKey(MelterHeatEntry entry) {
        if (entry == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entry.blockIds().size(); i++) {
            if (i > 0) {
                sb.append('\u0001');
            }
            if (Boolean.TRUE.equals(entry.blockIdIsTag().get(i))) {
                sb.append('#');
            }
            sb.append(entry.blockIds().get(i));
        }
        sb.append('\u0002');
        for (int i = 0; i < entry.fluidIds().size(); i++) {
            if (i > 0) {
                sb.append('\u0001');
            }
            if (Boolean.TRUE.equals(entry.fluidIdIsTag().get(i))) {
                sb.append('#');
            }
            sb.append(entry.fluidIds().get(i));
        }
        return sb.toString();
    }

    private static List<MelterHeatEntry> mergeLastWinsBySelectors(@Nullable List<MelterHeatEntry> loaded) {
        if (loaded == null || loaded.isEmpty()) {
            return List.of();
        }
        Map<String, MelterHeatEntry> byKey = new LinkedHashMap<>();
        for (MelterHeatEntry entry : loaded) {
            if (entry == null) {
                continue;
            }
            String key = mergeKey(entry);
            if (!key.isEmpty() && !"\u0002".equals(key)) {
                byKey.put(key, entry);
            }
        }
        return List.copyOf(byKey.values());
    }

    public static List<MelterHeatEntry> getAll() {
        return Collections.unmodifiableList(ENTRIES);
    }

    /**
     * True if the adjacent block/fluid in the given direction matches any heat entry (regardless of factor value).
     * Used to require at least one heating source for the melter to run.
     */
    public static boolean hasHeatingSource(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos melterPos, net.minecraft.core.Direction direction) {
        net.minecraft.core.BlockPos adjacent = melterPos.relative(direction);
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(adjacent);
        net.minecraft.world.level.block.Block block = state.getBlock();
        ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);

        // Check block matches first; only count as valid if entry does not have not_valid: true
        for (MelterHeatEntry e : ENTRIES) {
            if (e.notValid()) continue;
            if (!e.blockIds().isEmpty()) {
                for (int i = 0; i < e.blockIds().size(); i++) {
                    if (e.blockIdIsTag().get(i)) {
                        var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, e.blockIds().get(i));
                        if (state.is(tagKey)) return true;
                    } else {
                        if (e.blockIds().get(i).equals(blockId)) return true;
                    }
                }
            }
        }

        // If no block match, check fluid matches
        net.minecraft.world.level.material.FluidState fluidState = level.getFluidState(adjacent);
        if (fluidState.isEmpty()) return false;
        net.minecraft.world.level.material.Fluid fluid = fluidState.getType();
        ResourceLocation fluidId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        for (MelterHeatEntry e : ENTRIES) {
            if (e.notValid()) continue;
            if (e.fluidIds().isEmpty()) continue;
            for (int i = 0; i < e.fluidIds().size(); i++) {
                if (e.fluidIdIsTag().get(i)) {
                    var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, e.fluidIds().get(i));
                    if (fluid.is(tagKey)) return true;
                } else {
                    if (e.fluidIds().get(i).equals(fluidId)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the heat factor for the block at the given position in the given direction.
     * Direction is from melter to the adjacent block (e.g. Direction.UP = block above melter).
     * Returns 1.0 if no matching entry (sides without heating source).
     */
    public static double getFactorForBlock(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos melterPos, net.minecraft.core.Direction direction) {
        net.minecraft.core.BlockPos adjacent = melterPos.relative(direction);
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(adjacent);
        net.minecraft.world.level.block.Block block = state.getBlock();
        ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        for (MelterHeatEntry e : ENTRIES) {
            if (e.blockIds().isEmpty()) continue;
            for (int i = 0; i < e.blockIds().size(); i++) {
                if (e.blockIdIsTag().get(i)) {
                    var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, e.blockIds().get(i));
                    if (state.is(tagKey)) return e.factor();
                } else {
                    if (e.blockIds().get(i).equals(blockId)) return e.factor();
                }
            }
        }
        return 1.0;
    }

    /**
     * Returns the heat factor for the fluid at the given position (adjacent block may be fluid).
     * Checks fluid state of the adjacent block. Returns 1.0 if no matching entry.
     */
    public static double getFactorForFluid(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos melterPos, net.minecraft.core.Direction direction) {
        net.minecraft.core.BlockPos adjacent = melterPos.relative(direction);
        net.minecraft.world.level.material.FluidState fluidState = level.getFluidState(adjacent);
        if (fluidState.isEmpty()) return 1.0;
        net.minecraft.world.level.material.Fluid fluid = fluidState.getType();
        ResourceLocation fluidId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        for (MelterHeatEntry e : ENTRIES) {
            if (e.fluidIds().isEmpty()) continue;
            for (int i = 0; i < e.fluidIds().size(); i++) {
                if (e.fluidIdIsTag().get(i)) {
                    var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, e.fluidIds().get(i));
                    if (fluid.is(tagKey)) return e.factor();
                } else {
                    if (e.fluidIds().get(i).equals(fluidId)) return e.factor();
                }
            }
        }
        return 1.0;
    }

    /**
     * Combined factor for one direction: check block first, then fluid (e.g. fluid in same block as liquid block).
     * If block matches, use block factor; else if fluid matches, use fluid factor; else 1.0.
     */
    public static double getFactorFor(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos melterPos, net.minecraft.core.Direction direction) {
        double blockFactor = getFactorForBlock(level, melterPos, direction);
        if (blockFactor != 1.0) return blockFactor;
        return getFactorForFluid(level, melterPos, direction);
    }

    @Nullable
    public static List<MelterHeatEntry> parseFromRoot(JsonObject root, String source) {
        // After RecipeBundleSplitter, each holder is one heat object (blocks/fluids/factor).
        if (root.has(KEY_FACTOR) && (root.has(KEY_BLOCKS) || root.has(KEY_FLUIDS))) {
            MelterHeatEntry single = parseOne(root, source);
            return single != null ? List.of(single) : null;
        }
        if (!root.has("valid_blocks") || !root.get("valid_blocks").isJsonArray()) {
            LOGGER.warn("Melter heats in {}: missing valid_blocks array", source);
            return null;
        }
        List<MelterHeatEntry> out = new ArrayList<>();
        JsonArray arr = root.getAsJsonArray("valid_blocks");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            MelterHeatEntry entry = parseOne(el.getAsJsonObject(), source);
            if (entry != null) out.add(entry);
        }
        return out;
    }

    @Nullable
    private static MelterHeatEntry parseOne(JsonObject o, String source) {
        if (!o.has(KEY_FACTOR)) return null;
        double factor = o.get(KEY_FACTOR).getAsDouble();
        List<ResourceLocation> blockIds = new ArrayList<>();
        List<Boolean> blockIdIsTag = new ArrayList<>();
        if (o.has(KEY_BLOCKS) && o.get(KEY_BLOCKS).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(KEY_BLOCKS)) {
                String s = e.getAsString();
                boolean isTag = s.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(isTag ? s.substring(1) : s);
                if (id != null) {
                    blockIds.add(id);
                    blockIdIsTag.add(isTag);
                }
            }
        }
        List<ResourceLocation> fluidIds = new ArrayList<>();
        List<Boolean> fluidIdIsTag = new ArrayList<>();
        if (o.has(KEY_FLUIDS) && o.get(KEY_FLUIDS).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(KEY_FLUIDS)) {
                String s = e.getAsString();
                boolean isTag = s.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(isTag ? s.substring(1) : s);
                if (id != null) {
                    fluidIds.add(id);
                    fluidIdIsTag.add(isTag);
                }
            }
        }
        if (blockIds.isEmpty() && fluidIds.isEmpty()) return null;
        boolean notValid = o.has(KEY_NOT_VALID) && o.get(KEY_NOT_VALID).getAsBoolean();
        return new MelterHeatEntry(blockIds, blockIdIsTag, fluidIds, fluidIdIsTag, factor, notValid);
    }
}
