package net.unfamily.colossal_reactors.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.fuel.FuelDefinition;
import net.unfamily.colossal_reactors.compat.ViewerRecipeIds;
import net.unfamily.colossal_reactors.fuel.FuelMedium;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.unfamily.colossal_reactors.compat.RecipeViewerIds;

public class FuelRecipeCategory implements IRecipeCategory<FuelJeiRecipe> {

    public static final ResourceLocation UID = RecipeViewerIds.REACTOR_FUEL;
    private static final int WIDTH = 230;
    private static final int HEIGHT = 106;

    public static final RecipeType<FuelJeiRecipe> RECIPE_TYPE = new RecipeType<>(UID, FuelJeiRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;

    public FuelRecipeCategory(IGuiHelper helper) {
        this.background = new JeiRecipeBackgroundDrawable(WIDTH, HEIGHT, true);
        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModBlocks.REACTOR_ROD.get()));
    }

    @Override
    public RecipeType<FuelJeiRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.colossal_reactors.reactor_fuel");
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FuelJeiRecipe recipe, IFocusGroup focuses) {
        FuelDefinition def = recipe.definition();
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var registryAccess = level.registryAccess();

        switch (def.inputMedium()) {
            case FLUID -> {
                List<FluidStack> inputFluids = new ArrayList<>();
                for (String selector : def.inputs()) {
                    inputFluids.addAll(JeiIngredientsHelper.getOutputFluidStacks(selector, registryAccess));
                }
                if (!inputFluids.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.INPUT,
                                    JeiRecipeBackgroundDrawable.SLOT_IN_X + JeiRecipeBackgroundDrawable.ITEM_OFFSET_X,
                                    JeiRecipeBackgroundDrawable.SLOT_IN_Y + JeiRecipeBackgroundDrawable.ITEM_OFFSET_Y)
                            .addIngredients(NeoForgeTypes.FLUID_STACK, inputFluids);
                }
            }
            case CHEMICAL -> JeiChemicalSlots.addChemicalSlot(builder, RecipeIngredientRole.INPUT,
                    JeiRecipeBackgroundDrawable.SLOT_IN_X, JeiRecipeBackgroundDrawable.SLOT_IN_Y, def.inputs());
            case ITEM -> {
                List<ItemStack> inputs = JeiIngredientsHelper.withCount(
                        JeiIngredientsHelper.getFuelInputStacks(def.inputs(), registryAccess), def.consume());
                if (!inputs.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.INPUT,
                            JeiRecipeBackgroundDrawable.SLOT_IN_X + JeiRecipeBackgroundDrawable.ITEM_OFFSET_X,
                            JeiRecipeBackgroundDrawable.SLOT_IN_Y + JeiRecipeBackgroundDrawable.ITEM_OFFSET_Y).addItemStacks(inputs);
                }
            }
        }

        String output = def.output();
        switch (def.outputMedium()) {
            case FLUID -> {
                List<FluidStack> outputFluids = JeiIngredientsHelper.getOutputFluidStacks(output, registryAccess);
                if (!outputFluids.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.OUTPUT,
                                    JeiRecipeBackgroundDrawable.SLOT_OUT_X + JeiRecipeBackgroundDrawable.ITEM_OFFSET_X,
                                    JeiRecipeBackgroundDrawable.SLOT_OUT_Y + JeiRecipeBackgroundDrawable.ITEM_OFFSET_Y)
                            .addIngredients(NeoForgeTypes.FLUID_STACK, outputFluids);
                }
            }
            case CHEMICAL -> JeiChemicalSlots.addChemicalSlot(builder, RecipeIngredientRole.OUTPUT,
                    JeiRecipeBackgroundDrawable.SLOT_OUT_X, JeiRecipeBackgroundDrawable.SLOT_OUT_Y, List.of(output));
            case ITEM -> {
                List<ItemStack> outputs = JeiIngredientsHelper.withCount(
                        JeiIngredientsHelper.getWasteOutputStacks(output, registryAccess), def.produce());
                if (!outputs.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.OUTPUT,
                            JeiRecipeBackgroundDrawable.SLOT_OUT_X + JeiRecipeBackgroundDrawable.ITEM_OFFSET_X,
                            JeiRecipeBackgroundDrawable.SLOT_OUT_Y + JeiRecipeBackgroundDrawable.ITEM_OFFSET_Y).addItemStacks(outputs);
                }
            }
        }
    }

    @Override
    public void draw(FuelJeiRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        FuelDefinition def = recipe.definition();
        var font = Minecraft.getInstance().font;
        int textY = JeiRecipeBackgroundDrawable.TEXT_Y;
        int lineHeight = JeiRecipeBackgroundDrawable.TEXT_LINE_HEIGHT;
        int margin = JeiRecipeBackgroundDrawable.TEXT_MARGIN;
        int color = 0xFF404040;

        int consume = def.consume();
        int produce = def.produce();
        Component consumeFuel = Component.translatable("jei.colossal_reactors.consume_fuel", consume);
        Component produceWaste = Component.translatable("jei.colossal_reactors.produce_waste", produce);
        guiGraphics.drawString(font, consumeFuel, margin, textY, color, false);
        guiGraphics.drawString(font, produceWaste, margin, textY + lineHeight, color, false);

        double fuelPower = def.baseRfPerTick() * Config.PRODUCTION_MULTIPLIER.get();
        guiGraphics.drawString(font,
                Component.translatable("jei.colossal_reactors.fuel.power", formatFuelPower(fuelPower)),
                margin, textY + lineHeight * 2, color, false);
        guiGraphics.drawString(font,
                Component.translatable("jei.colossal_reactors.fuel.consume_factor",
                        formatConsumeFactor(def.baseFuelUnitsPerTick())),
                margin, textY + lineHeight * 3, color, false);
        guiGraphics.drawString(font,
                Component.translatable("jei.colossal_reactors.fuel.units_per_fuel", def.unitsPerFuel()),
                margin, textY + lineHeight * 4, color, false);
        guiGraphics.drawString(font,
                Component.translatable("jei.colossal_reactors.fuel.units_per_waste", def.unitsPerWaste()),
                margin, textY + lineHeight * 5, color, false);
        guiGraphics.drawString(font,
                Component.translatable("jei.colossal_reactors.fuel.burn_to_waste", def.unitsPerWaste(), produce),
                margin, textY + lineHeight * 6, color, false);
    }

    @Override
    public @Nullable ResourceLocation getRegistryName(FuelJeiRecipe recipe) {
        return ViewerRecipeIds.registryName(recipe.recipeId());
    }

    private static String formatFuelPower(double power) {
        if (power == (long) power) return String.valueOf((long) power);
        return String.format("%.1f", power);
    }

    private static String formatConsumeFactor(double value) {
        if (!Double.isFinite(value)) return "0";
        if (value == 0d) return "0";
        if (value == (long) value) return String.valueOf((long) value);
        String s = Double.toString(value);
        if (s.indexOf('E') >= 0 || s.indexOf('e') >= 0) {
            s = java.math.BigDecimal.valueOf(value).toPlainString();
        }
        int lastDigit = -1;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c >= '1' && c <= '9') {
                lastDigit = i;
                break;
            }
        }
        if (lastDigit < 0) return "0";
        String trimmed = s.substring(0, lastDigit + 1);
        if (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
