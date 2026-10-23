package net.unfamily.colossal_reactors.compat.jei;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.coolant.CoolantDefinition;
import net.unfamily.colossal_reactors.coolant.CoolantLoader;
import net.unfamily.colossal_reactors.crafting.ColossalJsonRecipe;
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
 * Builds JEI/EMI/REI recipe lists from RecipeManager holders.
 * Expands unsplit parent {@code entries} arrays so viewers stay filled if the virtual pack
 * missed a bundle (e.g. Gradle classes-only mod path).
 * Applies the same {@link DatapackSelectorValidator} sanitize as machine loaders so invalid
 * (unresolved) recipes are never shown in recipe viewers.
 */
public final class JeiNativeRecipeBridge {
    private JeiNativeRecipeBridge() {}

    @Nullable
    private static RecipeManager recipeManager() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level.getRecipeManager();
        }
        if (mc.getConnection() != null) {
            return mc.getConnection().getRecipeManager();
        }
        return null;
    }

    private static List<RecipeHolder<ColossalJsonRecipe>> holders(RecipeType<ColossalJsonRecipe> type) {
        RecipeManager manager = recipeManager();
        if (manager == null) {
            return List.of();
        }
        return List.copyOf(manager.getAllRecipesFor(type));
    }

    /** Prefer an explicit RecipeManager (EMI registry) when Minecraft connection is not ready. */
    private static List<RecipeHolder<ColossalJsonRecipe>> holders(
            RecipeType<ColossalJsonRecipe> type, @Nullable RecipeManager manager) {
        if (manager != null) {
            return List.copyOf(manager.getAllRecipesFor(type));
        }
        return holders(type);
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

    public static List<FuelJeiRecipe> fuels() {
        return fuels(null);
    }

    public static List<FuelJeiRecipe> fuels(@Nullable RecipeManager manager) {
        List<FuelJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.FUEL.get(), manager)) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                FuelDefinition def = FuelLoader.parseEntry(entry, holder.id().toString(), true);
                FuelDefinition sanitized = def != null ? DatapackSelectorValidator.sanitizeFuel(def) : null;
                if (sanitized != null) {
                    out.add(FuelJeiRecipe.of(sanitized, childId(holder.id(), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<CoolantJeiRecipe> coolants() {
        return coolants(null);
    }

    public static List<CoolantJeiRecipe> coolants(@Nullable RecipeManager manager) {
        List<CoolantJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.COOLANT.get(), manager)) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                CoolantDefinition def = CoolantLoader.parseEntry(entry, holder.id().toString(), true);
                CoolantDefinition sanitized = def != null ? DatapackSelectorValidator.sanitizeCoolant(def) : null;
                if (sanitized != null) {
                    out.addAll(CoolantJeiRecipe.expand(sanitized, childId(holder.id(), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<HeatSinkJeiRecipe> heatSinks() {
        return heatSinks(null);
    }

    public static List<HeatSinkJeiRecipe> heatSinks(@Nullable RecipeManager manager) {
        List<HeatSinkJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.HEAT_SINKS.get(), manager)) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                HeatSinkDefinition def = HeatSinkLoader.parseEntry(entry, holder.id().toString());
                HeatSinkDefinition sanitized = def != null ? DatapackSelectorValidator.sanitizeHeatSink(def) : null;
                if (sanitized != null) {
                    out.add(HeatSinkJeiRecipe.of(sanitized, childId(holder.id(), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<MelterJeiRecipe> melterRecipes() {
        return melterRecipes(null);
    }

    public static List<MelterJeiRecipe> melterRecipes(@Nullable RecipeManager manager) {
        List<MelterJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.MELTER_RECIPES.get(), manager)) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                MelterRecipe r = MelterRecipesLoader.parseEntry(entry, holder.id().toString());
                if (r != null && DatapackSelectorValidator.isMelterRecipeResolvable(r)) {
                    out.add(MelterJeiRecipe.of(r, childId(holder.id(), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<MelterHeatJeiRecipe> melterHeats() {
        return melterHeats(null);
    }

    public static List<MelterHeatJeiRecipe> melterHeats(@Nullable RecipeManager manager) {
        List<MelterHeatJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.MELTER_HEATS.get(), manager)) {
            ResourceLocation holderId = holder.id();
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
        return elecCoils(null);
    }

    public static List<ElecCoilJeiRecipe> elecCoils(@Nullable RecipeManager manager) {
        List<ElecCoilJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.ELEC_COILS.get(), manager)) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                ElecCoilDefinition def = ElecCoilLoader.parseEntry(entry, holder.id().toString());
                if (ElecCoilLoader.isVisibleInJei(def)) {
                    ElecCoilDefinition sanitized = DatapackSelectorValidator.sanitizeElecCoil(def);
                    if (ElecCoilLoader.isAirOnlyDefinition(sanitized)) {
                        double empty = Config.TURBINE_EMPTY_COIL_EFFICIENCY.get();
                        sanitized = new ElecCoilDefinition(sanitized.validBlocks(), empty, empty);
                    }
                    out.add(ElecCoilJeiRecipe.of(sanitized, childId(holder.id(), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<TurbineJeiRecipe> turbineGeneration() {
        return turbineGeneration(null);
    }

    public static List<TurbineJeiRecipe> turbineGeneration(@Nullable RecipeManager manager) {
        List<TurbineJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.TURBINE_GENERATION.get(), manager)) {
            int i = 0;
            List<JsonObject> entries = expandEntries(holder.value().json());
            for (JsonObject entry : entries) {
                TurbineGenerationDefinition def =
                        TurbineGenerationLoader.parseEntry(entry, holder.id().toString(), true);
                TurbineGenerationDefinition sanitized =
                        def != null ? DatapackSelectorValidator.sanitizeTurbineGeneration(def) : null;
                if (sanitized != null) {
                    out.addAll(TurbineJeiRecipe.expand(sanitized, childId(holder.id(), i, entries.size())));
                }
                i++;
            }
        }
        return out;
    }

    public static List<HeatingCoilJeiRecipe> heatingCoils() {
        return heatingCoils(null);
    }

    public static List<HeatingCoilJeiRecipe> heatingCoils(@Nullable RecipeManager manager) {
        List<HeatingCoilJeiRecipe> out = new ArrayList<>();
        for (RecipeHolder<ColossalJsonRecipe> holder : holders(ModColossalRecipes.HEATING_COILS.get(), manager)) {
            ResourceLocation id = holder.id();
            for (HeatingCoilDefinition def :
                    HeatingCoilLoader.parseFromRoot(holder.value().json(), id.toString())) {
                HeatingCoilDefinition sanitized = DatapackSelectorValidator.sanitizeHeatingCoil(def);
                out.addAll(HeatingCoilJeiRecipe.expand(sanitized, id));
            }
        }
        return out.stream().filter(Objects::nonNull).toList();
    }

    private static ResourceLocation childId(ResourceLocation holderId, int index, int total) {
        if (total <= 1) {
            return holderId;
        }
        return ResourceLocation.fromNamespaceAndPath(holderId.getNamespace(), holderId.getPath() + "_" + index);
    }
}
