package net.unfamily.colossal_reactors.compat;

import net.minecraft.resources.Identifier;
import net.unfamily.colossal_reactors.ColossalReactors;
import org.jetbrains.annotations.Nullable;

/**
 * Datapack {@link Identifier} for JEI {@code getIdentifier} / EMI / REI / KubeJS.
 *
 * <p>Every Colossal RecipeManager id is shown as-is. Liquid/gas variants of one coolant or
 * turbine entry share that same id in one viewer card (slots cycle both media).
 *
 * <p>{@code collisionSuffix} applies only to synthetic fallbacks when {@code recipeId} is null.
 */
public final class ViewerRecipeIds {
    private ViewerRecipeIds() {}

    /** JEI copy-id: bare datapack id (KubeJS / RecipeManager key). */
    public static @Nullable Identifier registryName(@Nullable Identifier recipeId) {
        return recipeId;
    }

    /**
     * Location for EMI/REI. Bare progressive holder id when present.
     * @param collisionSuffix only used for synthetic fallbacks (no RecipeManager id)
     */
    public static Identifier displayLocation(
            @Nullable Identifier recipeId,
            String group,
            Identifier fallback,
            @Nullable String collisionSuffix) {
        if (recipeId != null) {
            return recipeId;
        }
        if (collisionSuffix != null && !collisionSuffix.isBlank() && fallback != null) {
            String path = fallback.getPath();
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return syntheticRecipeId(
                    group, Identifier.fromNamespaceAndPath(fallback.getNamespace(), path + "_" + collisionSuffix));
        }
        return syntheticRecipeId(group, fallback);
    }

    public static Identifier fallbackId(String path) {
        return Identifier.fromNamespaceAndPath(ColossalReactors.MODID, path);
    }

    public static Identifier heatingCoilDisplayLocation(
            @Nullable Identifier recipeId, Identifier coilId, int optionIndex) {
        if (recipeId != null) {
            return recipeId;
        }
        String ns = coilId != null ? coilId.getNamespace() : ColossalReactors.MODID;
        String coilPath = coilId != null ? coilId.getPath() : "unknown";
        while (coilPath.startsWith("/")) {
            coilPath = coilPath.substring(1);
        }
        coilPath = coilPath.replace('/', '_');
        return Identifier.fromNamespaceAndPath(
                ns, "heating_coils_" + coilPath + "_" + optionIndex);
    }

    /** Synthetic id (leading '/') so EMI does not look it up in RecipeManager. */
    public static Identifier syntheticRecipeId(String group, Identifier id) {
        String ns = id != null ? id.getNamespace() : ColossalReactors.MODID;
        String base = id != null ? id.getPath() : "unknown";
        while (base.startsWith("/")) {
            base = base.substring(1);
        }
        String safeGroup = (group == null || group.isBlank()) ? "recipe" : group;
        return Identifier.fromNamespaceAndPath(ns, "/" + safeGroup + "/" + base);
    }
}
