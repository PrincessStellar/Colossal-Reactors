package net.unfamily.colossal_reactors.compat.emi.recipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.compat.emi.ColossalEmiRecipeLayout;
import net.unfamily.colossal_reactors.compat.emi.EmiCategories;
import net.unfamily.colossal_reactors.compat.ViewerRecipeIds;
import net.unfamily.colossal_reactors.compat.emi.EmiStackHelper;
import net.unfamily.colossal_reactors.compat.jei.FuelJeiRecipe;
import net.unfamily.colossal_reactors.compat.jei.JeiIngredientsHelper;
import net.unfamily.colossal_reactors.compat.RecipeViewerLayout;
import net.unfamily.colossal_reactors.fuel.FuelDefinition;
import net.unfamily.colossal_reactors.fuel.FuelMedium;

public final class EmiFuelRecipe implements EmiRecipe {
    private static final int W = 230;
    private static final int H = 106;

    private final FuelDefinition recipe;
    private final ResourceLocation id;
    private final EmiIngredient input;
    private final EmiStack output;

    public EmiFuelRecipe(FuelJeiRecipe wrapper) {
        this.recipe = wrapper.definition();
        this.id = ViewerRecipeIds.displayLocation(
                wrapper.recipeId(), "fuel", wrapper.definition().fuelId(), null);
        var reg = EmiStackHelper.registryOrThrow();
        this.input = buildInput(recipe, reg);
        this.output = buildOutput(recipe, reg);
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return EmiCategories.FUEL;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return input.isEmpty() ? List.of() : List.of(input);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return output.isEmpty() ? List.of() : List.of(output);
    }

    @Override
    public int getDisplayWidth() {
        return W;
    }

    @Override
    public int getDisplayHeight() {
        return H;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        boolean hasIn = !input.isEmpty();
        boolean hasOut = !output.isEmpty();
        ColossalEmiRecipeLayout.addStandardBackground(widgets, W, H, hasIn, hasOut);
        if (hasIn) {
            widgets.addSlot(input, ColossalEmiRecipeLayout.inSlotX(), ColossalEmiRecipeLayout.inSlotY());
        }
        if (hasOut) {
            widgets.addSlot(output, ColossalEmiRecipeLayout.outSlotX(), ColossalEmiRecipeLayout.outSlotY());
        }
        ColossalEmiRecipeLayout.addTextDrawer(widgets, W, H, this::drawTextForRei);
    }

    public void drawTextForRei(GuiGraphics guiGraphics) {
        var font = Minecraft.getInstance().font;
        int textY = RecipeViewerLayout.TEXT_Y;
        int lineHeight = RecipeViewerLayout.TEXT_LINE_HEIGHT;
        int margin = RecipeViewerLayout.TEXT_MARGIN;
        int color = 0xFF404040;
        int consume = recipe.consume();
        int produce = recipe.produce();
        guiGraphics.drawString(font, Component.translatable("jei.colossal_reactors.consume_fuel", consume), margin, textY, color, false);
        guiGraphics.drawString(font, Component.translatable("jei.colossal_reactors.produce_waste", produce), margin, textY + lineHeight, color, false);
        double fuelPower = recipe.baseRfPerTick() * Config.PRODUCTION_MULTIPLIER.get();
        guiGraphics.drawString(font,
                Component.translatable("jei.colossal_reactors.fuel.power", formatFuelPower(fuelPower)),
                margin, textY + lineHeight * 2, color, false);
        guiGraphics.drawString(font,
                Component.translatable("jei.colossal_reactors.fuel.consume_factor", formatConsumeFactor(recipe.baseFuelUnitsPerTick())),
                margin, textY + lineHeight * 3, color, false);
        guiGraphics.drawString(font,
                Component.translatable("jei.colossal_reactors.fuel.units_per_fuel", recipe.unitsPerFuel()),
                margin, textY + lineHeight * 4, color, false);
        guiGraphics.drawString(font,
                Component.translatable("jei.colossal_reactors.fuel.units_per_waste", recipe.unitsPerWaste()),
                margin, textY + lineHeight * 5, color, false);
        guiGraphics.drawString(font,
                Component.translatable("jei.colossal_reactors.fuel.burn_to_waste", recipe.unitsPerWaste(), produce),
                margin, textY + lineHeight * 6, color, false);
    }

    private static EmiIngredient buildInput(FuelDefinition recipe, net.minecraft.core.RegistryAccess reg) {
        return switch (recipe.inputMedium()) {
            case FLUID -> {
                List<FluidStack> fluids = new ArrayList<>();
                for (String selector : recipe.inputs()) {
                    fluids.addAll(JeiIngredientsHelper.getOutputFluidStacks(selector, reg));
                }
                yield EmiStackHelper.ingredientOfFluids(fluids);
            }
            case CHEMICAL -> EmiStackHelper.ingredientOfChemicalSelectors(recipe.inputs());
            case ITEM -> EmiStackHelper.ingredientOf(
                    JeiIngredientsHelper.withCount(JeiIngredientsHelper.getFuelInputStacks(recipe.inputs(), reg), recipe.consume()));
        };
    }

    private static EmiStack buildOutput(FuelDefinition recipe, net.minecraft.core.RegistryAccess reg) {
        String output = recipe.output();
        return switch (recipe.outputMedium()) {
            case FLUID -> EmiStackHelper.outputOf(JeiIngredientsHelper.getOutputFluidStacks(output, reg).stream().findFirst().orElse(FluidStack.EMPTY));
            case CHEMICAL -> {
                var ing = EmiStackHelper.ingredientOfChemicalSelectors(List.of(output));
                if (ing.isEmpty()) {
                    yield EmiStack.EMPTY;
                }
                yield ing.getEmiStacks().getFirst();
            }
            case ITEM -> {
                var stacks = JeiIngredientsHelper.withCount(
                        JeiIngredientsHelper.getWasteOutputStacks(output, reg), recipe.produce());
                yield stacks.isEmpty() ? EmiStack.EMPTY : EmiStackHelper.outputOf(stacks.get(0));
            }
        };
    }

    private static String formatFuelPower(double power) {
        if (power == (long) power) {
            return String.valueOf((long) power);
        }
        return String.format("%.1f", power);
    }

    private static String formatConsumeFactor(double value) {
        if (!Double.isFinite(value) || value == 0d) {
            return "0";
        }
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
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
        if (lastDigit < 0) {
            return "0";
        }
        String trimmed = s.substring(0, lastDigit + 1);
        if (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
