package net.unfamily.colossal_reactors.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.coolant.CoolantDefinition;
import org.jetbrains.annotations.Nullable;
import net.unfamily.colossal_reactors.compat.ViewerRecipeIds;

import java.util.ArrayList;
import java.util.List;

public class CoolantRecipeCategory implements IRecipeCategory<CoolantJeiRecipe> {

    public static final Identifier UID = Identifier.fromNamespaceAndPath(ColossalReactors.MODID, "reactor_coolant");
    private static final int WIDTH = 180;
    private static final int HEIGHT = 62;

    public static final IRecipeType<CoolantJeiRecipe> RECIPE_TYPE = IRecipeType.create(UID, CoolantJeiRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;

    public CoolantRecipeCategory(IGuiHelper helper) {
        this.background = new JeiRecipeBackgroundDrawable(WIDTH, HEIGHT, true);
        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModBlocks.RESOURCE_PORT.get()));
    }

    @Override
    public IRecipeType<CoolantJeiRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.colossal_reactors.reactor_coolant");
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CoolantJeiRecipe recipe, IFocusGroup focuses) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var registryAccess = level.registryAccess();

        // Same pattern as FuelRecipeCategory: fluid + chemical at identical coords → JEI cycles.
        List<FluidStack> inputFluids =
                JeiIngredientsHelper.getCoolantInputFluidStacks(recipe.liquidInputs(), registryAccess);
        if (!inputFluids.isEmpty()) {
            builder.addSlot(
                            RecipeIngredientRole.INPUT,
                            JeiRecipeBackgroundDrawable.SLOT_IN_X + JeiRecipeBackgroundDrawable.ITEM_OFFSET_X,
                            JeiRecipeBackgroundDrawable.SLOT_IN_Y + JeiRecipeBackgroundDrawable.ITEM_OFFSET_Y)
                    .addIngredients(NeoForgeTypes.FLUID_STACK, inputFluids);
        }
        JeiIngredientsHelper.addChemicalSlot(
                builder,
                RecipeIngredientRole.INPUT,
                JeiRecipeBackgroundDrawable.SLOT_IN_X,
                JeiRecipeBackgroundDrawable.SLOT_IN_Y,
                recipe.gasInputs());

        List<FluidStack> outputFluids = new ArrayList<>();
        for (String sel : recipe.liquidOutputs()) {
            outputFluids.addAll(JeiIngredientsHelper.getOutputFluidStacks(sel, registryAccess));
        }
        if (!outputFluids.isEmpty()) {
            builder.addSlot(
                            RecipeIngredientRole.OUTPUT,
                            JeiRecipeBackgroundDrawable.SLOT_OUT_X + JeiRecipeBackgroundDrawable.ITEM_OFFSET_X,
                            JeiRecipeBackgroundDrawable.SLOT_OUT_Y + JeiRecipeBackgroundDrawable.ITEM_OFFSET_Y)
                    .addIngredients(NeoForgeTypes.FLUID_STACK, outputFluids);
        }
        JeiIngredientsHelper.addChemicalSlot(
                builder,
                RecipeIngredientRole.OUTPUT,
                JeiRecipeBackgroundDrawable.SLOT_OUT_X,
                JeiRecipeBackgroundDrawable.SLOT_OUT_Y,
                recipe.gasOutputs());
    }

    @Override
    public void draw(CoolantJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics);
        CoolantDefinition def = recipe.definition();
        var font = Minecraft.getInstance().font;
        int textY = JeiRecipeBackgroundDrawable.TEXT_Y;
        int line2 = textY + JeiRecipeBackgroundDrawable.TEXT_LINE_HEIGHT;
        int margin = JeiRecipeBackgroundDrawable.TEXT_MARGIN;
        int color = 0xFF404040;

        String[] ratio = JeiIngredientsHelper.formatSimplifiedRatio(def.mbMultiplier(), def.steamPerCoolant());
        Component consumeCoolant = Component.translatable("jei.colossal_reactors.consume_coolant", ratio[1]);
        Component produceExhaust = Component.translatable("jei.colossal_reactors.produce_exhaust_coolant", ratio[0]);
        int line3 = line2 + JeiRecipeBackgroundDrawable.TEXT_LINE_HEIGHT;
        int line4 = line3 + JeiRecipeBackgroundDrawable.TEXT_LINE_HEIGHT;
        Component heatReduction = Component.translatable("jei.colossal_reactors.coolant.heat_reduction",
                formatMultiplier(def.overheatingMultiplier()));
        Component rfBehavior = def.reduceRfProduction()
                ? Component.translatable("jei.colossal_reactors.coolant.suppress_rf_steam")
                : Component.translatable("jei.colossal_reactors.coolant.suppress_rf_none", formatMultiplier(def.rfMultiplier()));
        guiGraphics.text(font, consumeCoolant, margin, textY, color, false);
        guiGraphics.text(font, produceExhaust, margin, line2, color, false);
        guiGraphics.text(font, heatReduction, margin, line3, color, false);
        guiGraphics.text(font, rfBehavior, margin, line4, color, false);
    }

    private static String formatMultiplier(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }

    @Override
    public @Nullable Identifier getIdentifier(CoolantJeiRecipe recipe) {
        return ViewerRecipeIds.registryName(recipe.recipeId());
    }
}
