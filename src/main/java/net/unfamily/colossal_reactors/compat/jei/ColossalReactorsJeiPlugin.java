package net.unfamily.colossal_reactors.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.unfamily.colossal_reactors.client.gui.MelterScreen;
import net.unfamily.colossal_reactors.client.gui.ReactorBuilderScreen;
import net.unfamily.colossal_reactors.client.gui.TurbineBuilderScreen;
import net.minecraft.world.item.ItemStack;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.compat.jei.ghost.ColossalGhostIngredientHandler;
import net.unfamily.colossal_reactors.heatingcoil.HeatingCoilRegistry;

@JeiPlugin
public class ColossalReactorsJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(ColossalReactors.MODID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new CoolantRecipeCategory(helper),
                new FuelRecipeCategory(helper),
                new HeatSinkRecipeCategory(helper),
                new MelterRecipeCategory(helper),
                new MelterHeatSourceRecipeCategory(helper),
                new HeatingCoilRecipeCategory(helper),
                new ElecCoilRecipeCategory(helper),
                new TurbineGenerationRecipeCategory(helper)
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // RecipeManager holders only (KubeJS removals / missing-mod skips). Sync fills after world load.
        registration.addRecipes(CoolantRecipeCategory.RECIPE_TYPE, JeiNativeRecipeBridge.coolants());
        registration.addRecipes(FuelRecipeCategory.RECIPE_TYPE, JeiNativeRecipeBridge.fuels());
        registration.addRecipes(HeatSinkRecipeCategory.RECIPE_TYPE, JeiNativeRecipeBridge.heatSinks());
        registration.addRecipes(MelterRecipeCategory.RECIPE_TYPE, JeiNativeRecipeBridge.melterRecipes());
        registration.addRecipes(MelterHeatSourceRecipeCategory.RECIPE_TYPE, JeiNativeRecipeBridge.melterHeats());
        registration.addRecipes(HeatingCoilRecipeCategory.RECIPE_TYPE, JeiNativeRecipeBridge.heatingCoils());
        registration.addRecipes(ElecCoilRecipeCategory.RECIPE_TYPE, JeiNativeRecipeBridge.elecCoils());
        registration.addRecipes(TurbineGenerationRecipeCategory.RECIPE_TYPE, JeiNativeRecipeBridge.turbineGeneration());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiDatapackRecipeSync.onRuntimeAvailable(jeiRuntime);
    }

    public static java.util.List<HeatingCoilJeiRecipe> buildHeatingCoilJeiRecipes() {
        return HeatingCoilRegistry.getAll().values().stream()
                .flatMap(def -> HeatingCoilJeiRecipe.expand(def).stream())
                .toList();
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Click progress bar → Melter melting recipes only (not heat sources).
        registration.addRecipeClickArea(
                MelterScreen.class,
                MelterScreen.getProgressBarX(),
                MelterScreen.getProgressBarY(),
                MelterScreen.getProgressBarWidth(),
                MelterScreen.getProgressBarHeight(),
                MelterRecipeCategory.RECIPE_TYPE);
        registration.addGhostIngredientHandler(ReactorBuilderScreen.class, new ColossalGhostIngredientHandler<>());
        registration.addGhostIngredientHandler(TurbineBuilderScreen.class, new ColossalGhostIngredientHandler<>());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        ItemStack controller = new ItemStack(ModBlocks.REACTOR_CONTROLLER.get());
        registration.addRecipeCatalyst(controller, CoolantRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(controller, FuelRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(controller, HeatSinkRecipeCategory.RECIPE_TYPE);

        registration.addRecipeCatalyst(new ItemStack(ModBlocks.MELTER.get()), MelterRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.MELTER.get()), MelterHeatSourceRecipeCategory.RECIPE_TYPE);
        for (var coil : net.unfamily.colossal_reactors.compat.HeatingCoilViewerHelper.offCoilStacks()) {
            registration.addRecipeCatalyst(coil, HeatingCoilRecipeCategory.RECIPE_TYPE);
        }

        ItemStack turbine = new ItemStack(ModBlocks.TURBINE_CONTROLLER.get());
        registration.addRecipeCatalyst(turbine, ElecCoilRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(turbine, TurbineGenerationRecipeCategory.RECIPE_TYPE);
    }
}
