package net.unfamily.colossal_reactors.compat.jei;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
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
 */
public final class JeiNativeRecipeBridge {
    private JeiNativeRecipeBridge() {}

    @Nullable
    private static RecipeManager recipeManager() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getRecipeManager() : null;
    }

    @SuppressWarnings("unchecked")
    private static List<RecipeHolder<ColossalJsonRecipe>> holders(RecipeType<ColossalJsonRecipe> type) {
        RecipeManager manager = recipeManager();
        if (manager != null) {
            return manager.getRecipes().stream()
                    .filter(holder -> holder.value().getType().equals(type))
                    .map(holder -> (RecipeHolder<ColossalJsonRecipe>) holder)
                    .toList();
        }
        return ColossalRecipeDatapackSync.clientHolders(type);
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
        List<FuelJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.FUEL.get())) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                FuelDefinition def = FuelLoader.parseEntry(entry, idOf(holder).toString(), true);
                FuelDefinition sanitized = def != null ? DatapackSelectorValidator.sanitizeFuel(def) : null;
                if (sanitized != null) {
                    out.add(FuelJeiRecipe.of(sanitized, childId(idOf(holder), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<CoolantJeiRecipe> coolants() {
        List<CoolantJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.COOLANT.get())) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                CoolantDefinition def = CoolantLoader.parseEntry(entry, idOf(holder).toString(), true);
                CoolantDefinition sanitized = def != null ? DatapackSelectorValidator.sanitizeCoolant(def) : null;
                if (sanitized != null) {
                    out.addAll(CoolantJeiRecipe.expand(sanitized, childId(idOf(holder), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<HeatSinkJeiRecipe> heatSinks() {
        List<HeatSinkJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.HEAT_SINKS.get())) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                HeatSinkDefinition def = HeatSinkLoader.parseEntry(entry, idOf(holder).toString());
                HeatSinkDefinition sanitized = def != null ? DatapackSelectorValidator.sanitizeHeatSink(def) : null;
                if (sanitized != null) {
                    out.add(HeatSinkJeiRecipe.of(sanitized, childId(idOf(holder), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<MelterJeiRecipe> melterRecipes() {
        List<MelterJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.MELTER_RECIPES.get())) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                MelterRecipe r = MelterRecipesLoader.parseEntry(entry, idOf(holder).toString());
                if (r != null && DatapackSelectorValidator.isMelterRecipeResolvable(r)) {
                    out.add(MelterJeiRecipe.of(r, childId(idOf(holder), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<MelterHeatJeiRecipe> melterHeats() {
        List<MelterHeatJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.MELTER_HEATS.get())) {
            Identifier holderId = idOf(holder);
            List<MelterHeatEntry> list = MelterHeatsLoader.parseFromRoot(holder.value().json(), holderId.toString());
            if (list == null || list.isEmpty()) {
                continue;
            }
            for (int i = 0; i < list.size(); i++) {
                MelterHeatEntry sanitized = DatapackSelectorValidator.sanitizeMelterHeat(list.get(i));
                if (sanitized != null) {
                    out.add(MelterHeatJeiRecipe.of(sanitized, holderId, i));
                }
            }
        }
        return out;
    }

    public static List<ElecCoilJeiRecipe> elecCoils() {
        List<ElecCoilJeiRecipe> out = new ArrayList<>();
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
                    out.add(ElecCoilJeiRecipe.of(sanitized, childId(idOf(holder), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<TurbineJeiRecipe> turbineGeneration() {
        List<TurbineJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.TURBINE_GENERATION.get())) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                TurbineGenerationDefinition def =
                        TurbineGenerationLoader.parseEntry(entry, idOf(holder).toString(), true);
                TurbineGenerationDefinition sanitized =
                        def != null ? DatapackSelectorValidator.sanitizeTurbineGeneration(def) : null;
                if (sanitized != null) {
                    out.addAll(TurbineJeiRecipe.expand(sanitized, childId(idOf(holder), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<HeatingCoilJeiRecipe> heatingCoils() {
        List<HeatingCoilJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.HEATING_COILS.get())) {
            Identifier id = idOf(holder);
            for (HeatingCoilDefinition def : HeatingCoilLoader.parseFromRoot(holder.value().json(), id.toString())) {
                HeatingCoilDefinition sanitized = DatapackSelectorValidator.sanitizeHeatingCoil(def);
                out.addAll(HeatingCoilJeiRecipe.expand(sanitized, id));
            }
        }
        return out.stream().filter(Objects::nonNull).toList();
    }
}
