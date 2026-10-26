package net.unfamily.colossal_reactors.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.unfamily.colossal_reactors.compat.HeatingCoilViewerHelper;
import net.unfamily.colossal_reactors.compat.ViewerRecipeIds;
import net.unfamily.colossal_reactors.compat.jei.ElecCoilJeiRecipe;
import net.unfamily.colossal_reactors.compat.jei.HeatSinkJeiRecipe;
import net.unfamily.colossal_reactors.compat.jei.MelterHeatJeiRecipe;
import net.unfamily.colossal_reactors.compat.jei.MelterJeiRecipe;
import net.unfamily.colossal_reactors.compat.RecipeViewerHeatingCoilLayout;
import net.unfamily.colossal_reactors.compat.RecipeViewerLayout;
import net.unfamily.colossal_reactors.compat.jei.CoolantJeiRecipe;
import net.unfamily.colossal_reactors.compat.jei.HeatingCoilJeiRecipe;
import net.unfamily.colossal_reactors.compat.jei.JeiIngredientsHelper;
import net.unfamily.colossal_reactors.compat.jei.TurbineJeiRecipe;
import net.unfamily.colossal_reactors.heatingcoil.ConsumeOption;
import net.unfamily.colossal_reactors.heatsink.HeatSinkDefinition;
import net.unfamily.colossal_reactors.integration.mekanism.MekChemicalHelper;
import net.unfamily.colossal_reactors.melter.MelterHeatEntry;
import net.unfamily.colossal_reactors.melter.MelterRecipe;
import net.unfamily.colossal_reactors.melter.MelterRecipesLoader;
import net.unfamily.colossal_reactors.turbine.ElecCoilDefinition;
import net.unfamily.colossal_reactors.turbine.TurbineGenerationDefinition;
import net.unfamily.colossal_reactors.turbine.TurbineGenerationLoader;

/** EMI recipe factories aligned with JEI categories. */
public final class ColossalEmiRecipes {
    private ColossalEmiRecipes() {}

    public static EmiRecipe coolant(CoolantJeiRecipe recipe) {
        Identifier id = ViewerRecipeIds.displayLocation(
                recipe.recipeId(), "coolant", recipe.jeiId(), null);
        return new SimpleEmiRecipe(
                EmiCategories.COOLANT, id, 180, 62, true) {
            @Override
            protected void resolveStacks() {
                var reg = EmiStackHelper.registryOrThrow();
                addIn(EmiStackHelper.combine(
                        EmiStackHelper.ingredientOfFluids(
                                JeiIngredientsHelper.getCoolantInputFluidStacks(recipe.liquidInputs(), reg)),
                        EmiStackHelper.ingredientOfChemicalSelectors(recipe.gasInputs())));
                List<FluidStack> out = new ArrayList<>();
                for (String sel : recipe.liquidOutputs()) {
                    out.addAll(JeiIngredientsHelper.getOutputFluidStacks(sel, reg));
                }
                addOut(EmiStackHelper.combine(
                        EmiStackHelper.ingredientOfFluids(out),
                        EmiStackHelper.ingredientOfChemicalSelectors(recipe.gasOutputs())));
            }

            @Override
            protected void drawText(GuiGraphics g) {
                var def = recipe.definition();
                var font = Minecraft.getInstance().font;
                int textY = RecipeViewerLayout.TEXT_Y;
                int line2 = textY + RecipeViewerLayout.TEXT_LINE_HEIGHT;
                int margin = RecipeViewerLayout.TEXT_MARGIN;
                int color = 0xFF404040;
                String[] ratio = JeiIngredientsHelper.formatSimplifiedRatio(def.mbMultiplier(), def.steamPerCoolant());
                g.drawString(font, Component.translatable("jei.colossal_reactors.consume_coolant", ratio[1]), margin, textY, color, false);
                g.drawString(font, Component.translatable("jei.colossal_reactors.produce_exhaust_coolant", ratio[0]), margin, line2, color, false);
                int line3 = line2 + RecipeViewerLayout.TEXT_LINE_HEIGHT;
                int line4 = line3 + RecipeViewerLayout.TEXT_LINE_HEIGHT;
                g.drawString(font, Component.translatable("jei.colossal_reactors.coolant.heat_reduction", fmt(def.overheatingMultiplier())), margin, line3, color, false);
                Component rf = def.reduceRfProduction()
                        ? Component.translatable("jei.colossal_reactors.coolant.suppress_rf_steam")
                        : Component.translatable("jei.colossal_reactors.coolant.suppress_rf_none", fmt(def.rfMultiplier()));
                g.drawString(font, rf, margin, line4, color, false);
            }
        };
    }

    public static EmiRecipe heatSink(HeatSinkJeiRecipe wrapper) {
        HeatSinkDefinition recipe = wrapper.definition();
        Identifier id = ViewerRecipeIds.displayLocation(
                wrapper.recipeId(),
                "heat_sink",
                ViewerRecipeIds.fallbackId("heat_sink/" + Integer.toHexString(recipe.hashCode())),
                null);
        return new SimpleEmiRecipe(EmiCategories.HEAT_SINK, id, 180, 54, false) {
            @Override
            protected void resolveStacks() {
                var reg = EmiStackHelper.registryOrThrow();
                List<ItemStack> blocks = JeiIngredientsHelper.getBlockStacks(recipe.validBlocks(), reg);
                List<FluidStack> fluids = JeiIngredientsHelper.getLiquidFluidStacks(recipe.validLiquids(), reg);
                if (!blocks.isEmpty()) {
                    addIn(EmiStackHelper.ingredientOf(blocks));
                } else if (!fluids.isEmpty()) {
                    addIn(EmiStackHelper.ingredientOfFluids(fluids));
                }
            }

            @Override
            protected void drawText(GuiGraphics g) {
                var font = Minecraft.getInstance().font;
                int textY = RecipeViewerLayout.TEXT_Y;
                int margin = RecipeViewerLayout.TEXT_MARGIN;
                int lh = RecipeViewerLayout.TEXT_LINE_HEIGHT;
                int color = 0xFF404040;
                g.drawString(font, Component.translatable("jei.colossal_reactors.heat_sink.fuel_reduction", fmt(recipe.fuelMultiplier())), margin, textY, color, false);
                g.drawString(font, Component.translatable("jei.colossal_reactors.heat_sink.rf_increment", fmt(recipe.energyMultiplier())), margin, textY + lh, color, false);
                g.drawString(font, Component.translatable("jei.colossal_reactors.heat_sink.heat_reduction", fmt(recipe.overheatingMultiplier())), margin, textY + 2 * lh, color, false);
            }
        };
    }

    public static EmiRecipe melter(MelterJeiRecipe wrapper) {
        MelterRecipe recipe = wrapper.definition();
        Identifier id = ViewerRecipeIds.displayLocation(
                wrapper.recipeId(), "melter", ViewerRecipeIds.fallbackId("melter/" + recipe.inputId().getPath()), null);
        return new SimpleEmiRecipe(EmiCategories.MELTER, id, 180, 78, true) {
            @Override
            protected void resolveStacks() {
                var reg = EmiStackHelper.registryOrThrow();
                List<ItemStack> inputs = MelterRecipeCategoryHelper.resolveInputs(recipe, reg);
                addIn(EmiStackHelper.ingredientOf(inputs));
                var fluid = MelterRecipesLoader.getOutputFluid(recipe, reg);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    addOut(EmiStackHelper.outputOf(new FluidStack(fluid, 1000)));
                }
            }

            @Override
            protected void drawText(GuiGraphics g) {
                var font = Minecraft.getInstance().font;
                int textY = RecipeViewerLayout.TEXT_Y;
                int margin = RecipeViewerLayout.TEXT_MARGIN;
                int lh = RecipeViewerLayout.TEXT_LINE_HEIGHT;
                int color = 0xFF404040;
                g.drawString(font, Component.translatable("jei.colossal_reactors.melter.amount", recipe.amountMb()), margin, textY, color, false);
                g.drawString(font, Component.translatable("jei.colossal_reactors.melter.default_time", JeiIngredientsHelper.formatDefaultDuration(recipe.timeTicks())), margin, textY + lh, color, false);
                g.drawString(font, Component.translatable("jei.colossal_reactors.melter.heat_required_1"), margin, textY + lh * 2, color, false);
                g.drawString(font, Component.translatable("jei.colossal_reactors.melter.heat_required_2"), margin, textY + lh * 3, color, false);
                g.drawString(font, Component.translatable("jei.colossal_reactors.melter.heat_required_3"), margin, textY + lh * 4, color, false);
            }
        };
    }

    public static EmiRecipe melterHeat(MelterHeatJeiRecipe wrapper) {
        MelterHeatEntry entry = wrapper.definition();
        Identifier id = ViewerRecipeIds.displayLocation(
                wrapper.recipeId(),
                "melter_heat",
                ViewerRecipeIds.fallbackId("melter_heat/" + Integer.toHexString(entry.hashCode())),
                Integer.toString(wrapper.entryIndex()));
        return new SimpleEmiRecipe(EmiCategories.MELTER_HEAT, id, 180, 52, false) {
            @Override
            protected void resolveStacks() {
                var reg = EmiStackHelper.registryOrThrow();
                List<ItemStack> blocks = JeiIngredientsHelper.getBlockStacksFromMelterEntry(entry, reg);
                List<FluidStack> fluids = JeiIngredientsHelper.getFluidStacksFromMelterEntry(entry, reg);
                if (!blocks.isEmpty()) {
                    addIn(EmiStackHelper.ingredientOf(blocks));
                } else if (!fluids.isEmpty()) {
                    addIn(EmiStackHelper.ingredientOfFluids(fluids));
                }
            }

            @Override
            protected void drawText(GuiGraphics g) {
                var font = Minecraft.getInstance().font;
                int textY = RecipeViewerLayout.TEXT_Y;
                int margin = RecipeViewerLayout.TEXT_MARGIN;
                int color = 0xFF404040;
                String factorStr = entry.factor() == (long) entry.factor() ? String.valueOf((long) entry.factor()) : String.format("%.2f", entry.factor());
                g.drawString(font, Component.translatable("jei.colossal_reactors.melter_heat_source.factor", factorStr), margin, textY, color, false);
                if (entry.notValid()) {
                    g.drawString(font, Component.translatable("jei.colossal_reactors.melter_heat_source.not_valid"), margin, textY + RecipeViewerLayout.TEXT_LINE_HEIGHT, 0xFF808080, false);
                }
            }
        };
    }

    public static EmiRecipe elecCoil(ElecCoilJeiRecipe wrapper) {
        ElecCoilDefinition recipe = wrapper.definition();
        Identifier id = ViewerRecipeIds.displayLocation(
                wrapper.recipeId(),
                "elec_coil",
                ViewerRecipeIds.fallbackId("elec_coil/" + Integer.toHexString(recipe.hashCode())),
                null);
        return new SimpleEmiRecipe(EmiCategories.ELEC_COIL, id, 180, 78, false) {
            @Override
            protected void resolveStacks() {
                var reg = EmiStackHelper.registryOrThrow();
                addIn(EmiStackHelper.ingredientOf(JeiIngredientsHelper.getElecCoilDisplayStacks(recipe.validBlocks(), reg)));
            }

            @Override
            protected void drawText(GuiGraphics g) {
                var font = Minecraft.getInstance().font;
                int textY = RecipeViewerLayout.TEXT_Y;
                int margin = RecipeViewerLayout.TEXT_MARGIN;
                int color = 0xFF404040;
                g.drawString(font, Component.translatable("jei.colossal_reactors.elec_coil.eff_coe", fmt(recipe.effCoe())), margin, textY, color, false);
                int lh = RecipeViewerLayout.TEXT_LINE_HEIGHT;
                g.drawString(font, Component.translatable("jei.colossal_reactors.elec_coil.eff_max", fmt(recipe.effMax())), margin, textY + lh, color, false);
                g.drawString(font, Component.translatable("jei.colossal_reactors.elec_coil.flow_coe", fmt(recipe.flowCoe())), margin, textY + lh * 2, color, false);
                g.drawString(font, Component.translatable("jei.colossal_reactors.elec_coil.flow_max", fmt(recipe.flowMax())), margin, textY + lh * 3, color, false);
            }
        };
    }

    public static EmiRecipe turbineGeneration(TurbineJeiRecipe recipe) {
        Identifier id = ViewerRecipeIds.displayLocation(
                recipe.recipeId(), "turbine_generation", recipe.jeiId(), null);
        return new SimpleEmiRecipe(
                EmiCategories.TURBINE_GENERATION,
                id,
                180,
                54,
                true) {
            @Override
            protected void resolveStacks() {
                var reg = EmiStackHelper.registryOrThrow();
                TurbineGenerationDefinition def = recipe.definition();
                addIn(EmiStackHelper.combine(
                        EmiStackHelper.ingredientOfFluids(
                                JeiIngredientsHelper.getTurbineGenerationInputFluids(recipe.liquidInputs(), reg)),
                        EmiStackHelper.ingredientOfChemicalSelectors(recipe.gasInputs())));
                List<FluidStack> out = new ArrayList<>();
                for (String sel : recipe.liquidOutputs()) {
                    out.addAll(JeiIngredientsHelper.getOutputFluidStacks(sel, reg));
                }
                var chemOut = EmiStackHelper.ingredientOfChemicalSelectors(recipe.gasOutputs());
                if (out.isEmpty() && chemOut.isEmpty() && def.liquidOutputSelector() != null) {
                    out.addAll(JeiIngredientsHelper.getOutputFluidStacks(def.liquidOutputSelector(), reg));
                }
                addOut(EmiStackHelper.combine(EmiStackHelper.ingredientOfFluids(out), chemOut));
            }

            @Override
            protected void drawText(GuiGraphics g) {
                TurbineGenerationDefinition def = recipe.definition();
                var font = Minecraft.getInstance().font;
                int margin = RecipeViewerLayout.TEXT_MARGIN;
                int color = 0xFF404040;
                g.drawString(font, Component.translatable("jei.colossal_reactors.turbine_generation.rf_per_bucket",
                        TurbineGenerationLoader.formatRfPerSteamBucket(def.rfProduction())), margin, RecipeViewerLayout.TEXT_Y, color, false);
            }
        };
    }

    public static EmiRecipe heatingCoil(HeatingCoilJeiRecipe recipe) {
        Identifier id = ViewerRecipeIds.heatingCoilDisplayLocation(
                recipe.recipeId(), recipe.coilId(), recipe.optionIndex());
        return new EmiRecipe() {
            private final List<EmiIngredient> inputs = buildInputs(recipe);
            private final EmiStack output = buildOutput(recipe);

            @Override
            public EmiRecipeCategory getCategory() { return EmiCategories.HEATING_COIL; }
            @Override
            public Identifier getId() { return id; }
            @Override
            public List<EmiIngredient> getInputs() { return inputs; }
            @Override
            public List<EmiStack> getOutputs() { return output.isEmpty() ? List.of() : List.of(output); }
            @Override
            public int getDisplayWidth() { return RecipeViewerHeatingCoilLayout.WIDTH; }
            @Override
            public int getDisplayHeight() { return RecipeViewerHeatingCoilLayout.HEIGHT; }

            @Override
            public void addWidgets(WidgetHolder widgets) {
                widgets.addDrawable(
                        0,
                        0,
                        RecipeViewerHeatingCoilLayout.WIDTH,
                        RecipeViewerHeatingCoilLayout.HEIGHT,
                        (g, mx, my, d) -> RecipeViewerHeatingCoilLayout.draw(g, 0, 0));
                ItemStack off = HeatingCoilViewerHelper.coilStack(recipe.coilId(), false);
                if (!off.isEmpty()) {
                    widgets.addSlot(
                            EmiStack.of(off),
                            RecipeViewerHeatingCoilLayout.OFF_X,
                            RecipeViewerHeatingCoilLayout.OFF_Y);
                }
                int slotIdx = 0;
                ConsumeOption opt = recipe.option();
                var reg = EmiStackHelper.registryOrThrow();
                var level = Minecraft.getInstance().level;
                if (opt.fluid() != null) {
                    var ing = EmiStackHelper.ingredientOfFluids(HeatingCoilViewerHelper.fluidStacks(opt.fluid(), reg));
                    if (!ing.isEmpty()) {
                        widgets.addSlot(
                                ing,
                                RecipeViewerHeatingCoilLayout.inputSlotX(slotIdx++),
                                RecipeViewerHeatingCoilLayout.IN_Y);
                    }
                }
                if (opt.chemical() != null && MekChemicalHelper.isGasSupportEnabled()) {
                    var chem = EmiStackHelper.ingredientOfChemicalSelectors(List.of(opt.chemical().selector()));
                    if (!chem.isEmpty()) {
                        widgets.addSlot(
                                chem,
                                RecipeViewerHeatingCoilLayout.inputSlotX(slotIdx++),
                                RecipeViewerHeatingCoilLayout.IN_Y);
                    }
                }
                if (opt.item() != null) {
                    var ing = EmiStackHelper.ingredientOf(HeatingCoilViewerHelper.itemStacks(opt.item(), reg));
                    if (!ing.isEmpty()) {
                        widgets.addSlot(
                                ing,
                                RecipeViewerHeatingCoilLayout.inputSlotX(slotIdx++),
                                RecipeViewerHeatingCoilLayout.IN_Y);
                    }
                }
                if (opt.burnable() != null && level != null) {
                    var ing = EmiStackHelper.ingredientOf(HeatingCoilViewerHelper.burnables(level));
                    if (!ing.isEmpty()) {
                        widgets.addSlot(
                                ing,
                                RecipeViewerHeatingCoilLayout.inputSlotX(slotIdx),
                                RecipeViewerHeatingCoilLayout.IN_Y);
                    }
                }
                if (!output.isEmpty()) {
                    widgets.addSlot(
                            output,
                            RecipeViewerHeatingCoilLayout.ON_X,
                            RecipeViewerHeatingCoilLayout.ON_Y);
                }
                widgets.addDrawable(
                        0,
                        0,
                        RecipeViewerHeatingCoilLayout.WIDTH,
                        RecipeViewerHeatingCoilLayout.HEIGHT,
                        (g, mx, my, d) -> drawCoilText(g, recipe));
            }

            private static List<EmiIngredient> buildInputs(HeatingCoilJeiRecipe recipe) {
                List<EmiIngredient> list = new ArrayList<>();
                ItemStack off = HeatingCoilViewerHelper.coilStack(recipe.coilId(), false);
                if (!off.isEmpty()) {
                    list.add(EmiStack.of(off));
                }
                ConsumeOption opt = recipe.option();
                var reg = EmiStackHelper.registryOrThrow();
                var level = Minecraft.getInstance().level;
                if (opt.fluid() != null) {
                    var ing = EmiStackHelper.ingredientOfFluids(HeatingCoilViewerHelper.fluidStacks(opt.fluid(), reg));
                    if (!ing.isEmpty()) {
                        list.add(ing);
                    }
                }
                if (opt.chemical() != null && MekChemicalHelper.isGasSupportEnabled()) {
                    var chem = EmiStackHelper.ingredientOfChemicalSelectors(List.of(opt.chemical().selector()));
                    if (!chem.isEmpty()) {
                        list.add(chem);
                    }
                }
                if (opt.item() != null) {
                    var ing = EmiStackHelper.ingredientOf(HeatingCoilViewerHelper.itemStacks(opt.item(), reg));
                    if (!ing.isEmpty()) {
                        list.add(ing);
                    }
                }
                if (opt.burnable() != null && level != null) {
                    var ing = EmiStackHelper.ingredientOf(HeatingCoilViewerHelper.burnables(level));
                    if (!ing.isEmpty()) {
                        list.add(ing);
                    }
                }
                return List.copyOf(list);
            }

            private static EmiStack buildOutput(HeatingCoilJeiRecipe recipe) {
                ItemStack on = HeatingCoilViewerHelper.coilStack(recipe.coilId(), true);
                return on.isEmpty() ? EmiStack.EMPTY : EmiStack.of(on);
            }

            private static void drawCoilText(GuiGraphics g, HeatingCoilJeiRecipe recipe) {
                var font = Minecraft.getInstance().font;
                int color = 0xFF404040;
                g.drawString(
                        font,
                        "+",
                        RecipeViewerHeatingCoilLayout.PLUS_X,
                        RecipeViewerHeatingCoilLayout.PLUS_Y,
                        color,
                        false);
                if (recipe.option().energy() != null) {
                    g.drawString(
                            font,
                            "+ RF",
                            RecipeViewerHeatingCoilLayout.RF_X,
                            RecipeViewerHeatingCoilLayout.RF_Y,
                            color,
                            false);
                }
                int textY = RecipeViewerHeatingCoilLayout.TEXT_Y;
                int margin = RecipeViewerHeatingCoilLayout.TEXT_MARGIN;
                int line = 0;
                int lineH = RecipeViewerHeatingCoilLayout.TEXT_LINE_HEIGHT;
                g.drawString(
                        font,
                        Component.translatable("jei.colossal_reactors.coil.duration", recipe.durationTicks()),
                        margin,
                        textY + line++ * lineH,
                        color,
                        false);
                ConsumeOption opt = recipe.option();
                if (opt.fluid() != null) {
                    g.drawString(
                            font,
                            Component.translatable(
                                    "jei.colossal_reactors.coil.activate", opt.fluid().activation() + " mB"),
                            margin,
                            textY + line++ * lineH,
                            color,
                            false);
                    g.drawString(
                            font,
                            Component.translatable(
                                    "jei.colossal_reactors.coil.substain", opt.fluid().substain() + " mB"),
                            margin,
                            textY + line++ * lineH,
                            color,
                            false);
                }
                if (opt.chemical() != null && MekChemicalHelper.isGasSupportEnabled()) {
                    g.drawString(
                            font,
                            Component.translatable(
                                    "jei.colossal_reactors.coil.activate", opt.chemical().activation() + " mB"),
                            margin,
                            textY + line++ * lineH,
                            color,
                            false);
                    g.drawString(
                            font,
                            Component.translatable(
                                    "jei.colossal_reactors.coil.substain", opt.chemical().substain() + " mB"),
                            margin,
                            textY + line++ * lineH,
                            color,
                            false);
                }
                if (opt.item() != null) {
                    g.drawString(
                            font,
                            Component.translatable(
                                    "jei.colossal_reactors.coil.activate", opt.item().activation()),
                            margin,
                            textY + line++ * lineH,
                            color,
                            false);
                    g.drawString(
                            font,
                            Component.translatable(
                                    "jei.colossal_reactors.coil.substain", opt.item().substain()),
                            margin,
                            textY + line++ * lineH,
                            color,
                            false);
                }
                if (opt.burnable() != null) {
                    g.drawString(
                            font,
                            Component.translatable(
                                    "jei.colossal_reactors.coil.activate", opt.burnable().activation() + " t"),
                            margin,
                            textY + line++ * lineH,
                            color,
                            false);
                    g.drawString(
                            font,
                            Component.translatable(
                                    "jei.colossal_reactors.coil.substain", opt.burnable().substain() + " t"),
                            margin,
                            textY + line++ * lineH,
                            color,
                            false);
                }
                if (opt.energy() != null) {
                    g.drawString(
                            font,
                            Component.translatable(
                                    "jei.colossal_reactors.coil.activate", opt.energy().activation() + " RF"),
                            margin,
                            textY + line++ * lineH,
                            color,
                            false);
                    g.drawString(
                            font,
                            Component.translatable(
                                    "jei.colossal_reactors.coil.substain", opt.energy().substain() + " RF"),
                            margin,
                            textY + line++ * lineH,
                            color,
                            false);
                }
            }
        };
    }

    private static String fmt(double value) {
        if (value == (long) value) return String.valueOf((long) value);
        return String.format("%.2f", value);
    }

    private abstract static class SimpleEmiRecipe implements EmiRecipe {
        private final EmiRecipeCategory category;
        private final Identifier id;
        private final int w;
        private final int h;
        /** When false, never draw an output slot (single-slot categories). */
        private final boolean allowOutputSlot;
        private EmiIngredient input = EmiStack.EMPTY;
        private EmiIngredient output = EmiStack.EMPTY;

        protected SimpleEmiRecipe(EmiRecipeCategory category, Identifier id, int w, int h, boolean allowOutputSlot) {
            this.category = category;
            this.id = id;
            this.w = w;
            this.h = h;
            this.allowOutputSlot = allowOutputSlot;
            // EMI indexes getInputs/getOutputs at register/bake time — must resolve before addWidgets.
            resolveStacks();
        }

        protected void addIn(EmiIngredient ing) {
            if (ing != null && !ing.isEmpty()) {
                this.input = ing;
            }
        }

        protected void addOut(EmiIngredient ing) {
            if (ing != null && !ing.isEmpty()) {
                this.output = ing;
            }
        }

        protected abstract void resolveStacks();
        protected abstract void drawText(GuiGraphics g);

        @Override
        public EmiRecipeCategory getCategory() { return category; }
        @Override
        public Identifier getId() { return id; }
        @Override
        public List<EmiIngredient> getInputs() { return input.isEmpty() ? List.of() : List.of(input); }
        @Override
        public List<EmiStack> getOutputs() {
            return output.isEmpty() ? List.of() : output.getEmiStacks();
        }
        @Override
        public int getDisplayWidth() { return w; }
        @Override
        public int getDisplayHeight() { return h; }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            boolean hasIn = !input.isEmpty();
            boolean hasOut = allowOutputSlot && !output.isEmpty();
            ColossalEmiRecipeLayout.addStandardBackground(widgets, w, h, hasIn, hasOut);
            if (hasIn) {
                widgets.addSlot(input, ColossalEmiRecipeLayout.inSlotX(), ColossalEmiRecipeLayout.inSlotY());
            }
            if (hasOut) {
                widgets.addSlot(output, ColossalEmiRecipeLayout.outSlotX(), ColossalEmiRecipeLayout.outSlotY());
            }
            ColossalEmiRecipeLayout.addTextDrawer(widgets, w, h, this::drawText);
        }
    }

    public static void drawCoolant(GuiGraphics g, CoolantJeiRecipe recipe) {
        CoolantJeiRecipe r = recipe;
        var def = r.definition();
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textY = RecipeViewerLayout.TEXT_Y;
        int line2 = textY + RecipeViewerLayout.TEXT_LINE_HEIGHT;
        int margin = RecipeViewerLayout.TEXT_MARGIN;
        int color = 0xFF404040;
        String[] ratio = JeiIngredientsHelper.formatSimplifiedRatio(def.mbMultiplier(), def.steamPerCoolant());
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.consume_coolant", ratio[1]), margin, textY, color, false);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.produce_exhaust_coolant", ratio[0]), margin, line2, color, false);
    }

    public static void drawHeatSink(GuiGraphics g, HeatSinkDefinition recipe) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textY = RecipeViewerLayout.TEXT_Y;
        int margin = RecipeViewerLayout.TEXT_MARGIN;
        int lh = RecipeViewerLayout.TEXT_LINE_HEIGHT;
        int color = 0xFF404040;
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.heat_sink.fuel_reduction", fmt(recipe.fuelMultiplier())), margin, textY, color, false);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.heat_sink.rf_increment", fmt(recipe.energyMultiplier())), margin, textY + lh, color, false);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.heat_sink.heat_reduction", fmt(recipe.overheatingMultiplier())), margin, textY + 2 * lh, color, false);
    }

    public static void drawMelter(GuiGraphics g, MelterRecipe recipe) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textY = RecipeViewerLayout.TEXT_Y;
        int margin = RecipeViewerLayout.TEXT_MARGIN;
        int lh = RecipeViewerLayout.TEXT_LINE_HEIGHT;
        int color = 0xFF404040;
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.melter.amount", recipe.amountMb()), margin, textY, color, false);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.melter.default_time", JeiIngredientsHelper.formatDefaultDuration(recipe.timeTicks())), margin, textY + lh, color, false);
    }

    public static void drawMelterHeat(GuiGraphics g, MelterHeatEntry entry) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textY = RecipeViewerLayout.TEXT_Y;
        int margin = RecipeViewerLayout.TEXT_MARGIN;
        int color = 0xFF404040;
        String factorStr = entry.factor() == (long) entry.factor() ? String.valueOf((long) entry.factor()) : String.format("%.2f", entry.factor());
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.melter_heat_source.factor", factorStr), margin, textY, color, false);
        if (entry.notValid()) {
            g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.melter_heat_source.not_valid"), margin, textY + RecipeViewerLayout.TEXT_LINE_HEIGHT, 0xFF808080, false);
        }
    }

    public static void drawElecCoil(GuiGraphics g, ElecCoilDefinition recipe) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textY = RecipeViewerLayout.TEXT_Y;
        int margin = RecipeViewerLayout.TEXT_MARGIN;
        int color = 0xFF404040;
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.elec_coil.eff_coe", fmt(recipe.effCoe())), margin, textY, color, false);
        int lh = RecipeViewerLayout.TEXT_LINE_HEIGHT;
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.elec_coil.eff_max", fmt(recipe.effMax())), margin, textY + lh, color, false);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.elec_coil.flow_coe", fmt(recipe.flowCoe())), margin, textY + lh * 2, color, false);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.elec_coil.flow_max", fmt(recipe.flowMax())), margin, textY + lh * 3, color, false);
    }

    public static void drawTurbine(GuiGraphics g, TurbineJeiRecipe recipe) {
        var def = recipe.definition();
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int margin = RecipeViewerLayout.TEXT_MARGIN;
        int color = 0xFF404040;
        g.drawString(font, net.minecraft.network.chat.Component.translatable("jei.colossal_reactors.turbine_generation.rf_per_bucket",
                TurbineGenerationLoader.formatRfPerSteamBucket(def.rfProduction())), margin, RecipeViewerLayout.TEXT_Y, color, false);
    }
}
