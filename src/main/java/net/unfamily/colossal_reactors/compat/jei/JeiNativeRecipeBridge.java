package net.unfamily.colossal_reactors.compat.jei;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.coolant.CoolantDefinition;
import net.unfamily.colossal_reactors.coolant.CoolantLoader;
import net.unfamily.colossal_reactors.crafting.ColossalJsonRecipe;
import net.unfamily.colossal_reactors.crafting.ColossalRecipeDatapackSync;
import net.unfamily.colossal_reactors.crafting.ModColossalRecipes;
import net.unfamily.colossal_reactors.datapack.DatapackSelectorValidator;
import net.unfamily.colossal_reactors.fuel.FuelDefinition;
import net.unfamily.colossal_reactors.fuel.FuelLoader;
import net.unfamily.colossal_reactors.heatingcoil.ConsumeOption;
import net.unfamily.colossal_reactors.heatingcoil.HeatingCoilDefinition;
import net.unfamily.colossal_reactors.heatingcoil.HeatingCoilLoader;
import net.unfamily.colossal_reactors.heatsink.HeatSinkDefinition;
import net.unfamily.colossal_reactors.heatsink.HeatSinkLoader;
import net.unfamily.colossal_reactors.melter.MelterHeatEntry;
import net.unfamily.colossal_reactors.melter.MelterHeatsLoader;
import net.unfamily.colossal_reactors.melter.MelterRecipe;
import net.unfamily.colossal_reactors.melter.MelterRecipesLoader;
import net.unfamily.colossal_reactors.turbine.ElecCoilDefinition;
import net.unfamily.colossal_reactors.turbine.ElecCoilLoader;
import net.unfamily.colossal_reactors.turbine.TurbineGenerationDefinition;
import net.unfamily.colossal_reactors.turbine.TurbineGenerationLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Builds JEI lists from RecipeManager / synced RecipeMap holders.
 * Expands unsplit parent {@code entries} arrays when ModifyRecipeJsonsEvent missed a bundle.
 * Applies the same {@link DatapackSelectorValidator} sanitize as machine loaders so invalid
 * (unresolved) recipes are never shown in recipe viewers.
 * Semantic last-wins (same keys as loaders) so addon packs replace base cards instead of mixing.
 */
public final class JeiNativeRecipeBridge {
    private JeiNativeRecipeBridge() {}

    /**
     * Prefer synced client {@link net.minecraft.world.item.crafting.RecipeMap} (same ids JEI sees),
     * then dedicated/integrated server RecipeManager. Level/connection no longer expose a full
     * RecipeManager on 26.x — {@link ColossalRecipeDatapackSync#clientHolders} is the client path.
     */
    @Nullable
    private static RecipeManager serverRecipeManager() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getRecipeManager() : null;
    }

    @SuppressWarnings("unchecked")
    private static List<RecipeHolder<ColossalJsonRecipe>> holders(RecipeType<ColossalJsonRecipe> type) {
        List<RecipeHolder<ColossalJsonRecipe>> client = ColossalRecipeDatapackSync.clientHolders(type);
        if (!client.isEmpty()) {
            return client;
        }
        // Integrated / early JEI: server may already have recipes while client RecipeMap is pending.
        RecipeManager manager = serverRecipeManager();
        if (manager != null) {
            return manager.getRecipes().stream()
                    .filter(holder -> holder.value().getType().equals(type))
                    .map(holder -> (RecipeHolder<ColossalJsonRecipe>) holder)
                    .toList();
        }
        return List.of();
    }

    private static Identifier idOf(RecipeHolder<?> holder) {
        return holder.id().identifier();
    }

    private static List<JsonObject> expandEntries(JsonObject json) {
        if (json != null && json.has("entries") && json.get("entries").isJsonArray()) {
            List<JsonObject> out = new ArrayList<>();
            for (JsonElement el : json.getAsJsonArray("entries")) {
                if (el != null && el.isJsonObject()) {
                    out.add(el.getAsJsonObject());
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return json == null ? List.of() : List.of(json);
    }

    private static Identifier childId(Identifier holderId, int index, int total) {
        if (total <= 1) {
            return holderId;
        }
        return Identifier.fromNamespaceAndPath(holderId.getNamespace(), holderId.getPath() + "_" + index);
    }

    public static List<FuelJeiRecipe> fuels() {
        Map<String, FuelJeiRecipe> byKey = new LinkedHashMap<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.FUEL.get())) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                FuelDefinition def = FuelLoader.parseEntry(entry, idOf(holder).toString(), true);
                FuelDefinition sanitized = def != null ? DatapackSelectorValidator.sanitizeFuel(def) : null;
                if (sanitized != null) {
                    byKey.put(
                            sanitized.fuelId().toString(),
                            FuelJeiRecipe.of(sanitized, childId(idOf(holder), i, entries.size())));
                }
                i++;
            }
        }
        return new ArrayList<>(byKey.values());
    }

    public static List<CoolantJeiRecipe> coolants() {
        Map<String, CoolantJeiRecipe> byKey = new LinkedHashMap<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.COOLANT.get())) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                CoolantDefinition def = CoolantLoader.parseEntry(entry, idOf(holder).toString(), true);
                CoolantDefinition sanitized = def != null ? DatapackSelectorValidator.sanitizeCoolant(def) : null;
                if (sanitized != null) {
                    Identifier id = childId(idOf(holder), i, entries.size());
                    for (CoolantJeiRecipe card : CoolantJeiRecipe.expand(sanitized, id)) {
                        byKey.put(sanitized.coolantId().toString(), card);
                    }
                }
                i++;
            }
        }
        return new ArrayList<>(byKey.values());
    }

    public static List<HeatSinkJeiRecipe> heatSinks() {
        Map<String, HeatSinkJeiRecipe> byKey = new LinkedHashMap<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.HEAT_SINKS.get())) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                HeatSinkDefinition def = HeatSinkLoader.parseEntry(entry, idOf(holder).toString());
                HeatSinkDefinition sanitized = def != null ? DatapackSelectorValidator.sanitizeHeatSink(def) : null;
                if (sanitized != null) {
                    String key = HeatSinkLoader.mergeKey(sanitized);
                    if (!key.isEmpty() && !"\u0002".equals(key)) {
                        byKey.put(key, HeatSinkJeiRecipe.of(sanitized, childId(idOf(holder), i, entries.size())));
                    }
                }
                i++;
            }
        }
        return new ArrayList<>(byKey.values());
    }

    public static List<MelterJeiRecipe> melterRecipes() {
        Map<String, MelterJeiRecipe> byKey = new LinkedHashMap<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.MELTER_RECIPES.get())) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                MelterRecipe r = MelterRecipesLoader.parseEntry(entry, idOf(holder).toString());
                if (r != null && DatapackSelectorValidator.isMelterRecipeResolvable(r)) {
                    String key = MelterRecipesLoader.mergeKey(r);
                    if (!key.isEmpty()) {
                        byKey.put(key, MelterJeiRecipe.of(r, childId(idOf(holder), i, entries.size())));
                    }
                }
                i++;
            }
        }
        return new ArrayList<>(byKey.values());
    }

    public static List<MelterHeatJeiRecipe> melterHeats() {
        Map<String, MelterHeatJeiRecipe> byKey = new LinkedHashMap<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.MELTER_HEATS.get())) {
            Identifier holderId = idOf(holder);
            List<MelterHeatEntry> list = MelterHeatsLoader.parseFromRoot(holder.value().json(), holderId.toString());
            if (list == null || list.isEmpty()) {
                continue;
            }
            for (int i = 0; i < list.size(); i++) {
                MelterHeatEntry sanitized = DatapackSelectorValidator.sanitizeMelterHeat(list.get(i));
                if (sanitized != null) {
                    String key = MelterHeatsLoader.mergeKey(sanitized);
                    if (!key.isEmpty() && !"\u0002".equals(key)) {
                        byKey.put(key, MelterHeatJeiRecipe.of(sanitized, holderId, i));
                    }
                }
            }
        }
        return new ArrayList<>(byKey.values());
    }

    public static List<ElecCoilJeiRecipe> elecCoils() {
        Map<String, ElecCoilJeiRecipe> byKey = new LinkedHashMap<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.ELEC_COILS.get())) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                ElecCoilDefinition def = ElecCoilLoader.parseEntry(entry, idOf(holder).toString());
                if (ElecCoilLoader.isVisibleInJei(def)) {
                    ElecCoilDefinition sanitized = DatapackSelectorValidator.sanitizeElecCoil(def);
                    if (ElecCoilLoader.isAirOnlyDefinition(sanitized)) {
                        double empty = Config.TURBINE_EMPTY_COIL_EFFICIENCY.get();
                        sanitized = new ElecCoilDefinition(sanitized.validBlocks(), empty, empty, 1.0, 1.0);
                    }
                    String key = ElecCoilLoader.mergeKey(sanitized);
                    if (!key.isEmpty()) {
                        byKey.put(key, ElecCoilJeiRecipe.of(sanitized, childId(idOf(holder), i, entries.size())));
                    }
                }
                i++;
            }
        }
        return new ArrayList<>(byKey.values());
    }

    public static List<TurbineJeiRecipe> turbineGeneration() {
        Map<String, TurbineJeiRecipe> byKey = new LinkedHashMap<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.TURBINE_GENERATION.get())) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                TurbineGenerationDefinition def =
                        TurbineGenerationLoader.parseEntry(entry, idOf(holder).toString(), true);
                TurbineGenerationDefinition sanitized =
                        def != null ? DatapackSelectorValidator.sanitizeTurbineGeneration(def) : null;
                if (sanitized != null) {
                    Identifier id = childId(idOf(holder), i, entries.size());
                    for (TurbineJeiRecipe card : TurbineJeiRecipe.expand(sanitized, id)) {
                        byKey.put(sanitized.generationId().toString(), card);
                    }
                }
                i++;
            }
        }
        return new ArrayList<>(byKey.values());
    }

    public static List<HeatingCoilJeiRecipe> heatingCoils() {
        Map<Identifier, HeatingCoilDefinition> coils = new LinkedHashMap<>();
        Map<Identifier, Identifier> lastHolderId = new LinkedHashMap<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.HEATING_COILS.get())) {
            Identifier id = idOf(holder);
            for (HeatingCoilDefinition def : HeatingCoilLoader.parseFromRoot(holder.value().json(), id.toString())) {
                HeatingCoilDefinition sanitized = DatapackSelectorValidator.sanitizeHeatingCoil(def);
                if (sanitized == null) {
                    continue;
                }
                coils.merge(sanitized.id(), sanitized, JeiNativeRecipeBridge::mergeHeatingCoil);
                lastHolderId.put(sanitized.id(), id);
            }
        }
        List<HeatingCoilJeiRecipe> out = new ArrayList<>();
        for (Map.Entry<Identifier, HeatingCoilDefinition> e : coils.entrySet()) {
            out.addAll(HeatingCoilJeiRecipe.expand(e.getValue(), lastHolderId.get(e.getKey())));
        }
        return out.stream().filter(Objects::nonNull).toList();
    }

    private static HeatingCoilDefinition mergeHeatingCoil(HeatingCoilDefinition a, HeatingCoilDefinition b) {
        List<ConsumeOption> merged = new ArrayList<>(a.consume() != null ? a.consume() : List.of());
        if (b.consume() != null) {
            merged.addAll(b.consume());
        }
        return new HeatingCoilDefinition(
                a.id(), a.duration(), List.copyOf(merged), a.allSides(), a.noItem(), a.noFluid(), a.noEnergy());
    }
}
