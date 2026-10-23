package net.unfamily.colossal_reactors.compat.rei;

import java.util.ArrayList;
import java.util.List;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.compat.HeatingCoilViewerHelper;
import net.unfamily.colossal_reactors.compat.RecipeViewerHeatingCoilLayout;
import net.unfamily.colossal_reactors.compat.RecipeViewerIds;
import net.unfamily.colossal_reactors.compat.RecipeViewerLayout;

public final class ColossalReiCategories {
    /** Inset inside {@link Widgets#createRecipeBase} so slots clear the panel border. */
    private static final int RECIPE_PAD = 6;
    /**
     * Extra width on the right of the recipe base so the move-items "+" stays inside the panel
     * (default ButtonArea uses {@code maxX + 2}, outside the card).
     */
    private static final int PLUS_STRIP = 14;

    public static final CategoryIdentifier<ColossalReiDisplay> COOLANT = id(RecipeViewerIds.REACTOR_COOLANT);
    public static final CategoryIdentifier<ColossalReiDisplay> FUEL = id(RecipeViewerIds.REACTOR_FUEL);
    public static final CategoryIdentifier<ColossalReiDisplay> HEAT_SINK = id(RecipeViewerIds.REACTOR_HEAT_SINK);
    public static final CategoryIdentifier<ColossalReiDisplay> MELTER = id(RecipeViewerIds.MELTER);
    public static final CategoryIdentifier<ColossalReiDisplay> MELTER_HEAT = id(RecipeViewerIds.MELTER_HEAT_SOURCE);
    public static final CategoryIdentifier<ColossalReiDisplay> HEATING_COIL = id(RecipeViewerIds.HEATING_COIL);
    public static final CategoryIdentifier<ColossalReiDisplay> ELEC_COIL = id(RecipeViewerIds.ELEC_COIL);
    public static final CategoryIdentifier<ColossalReiDisplay> TURBINE = id(RecipeViewerIds.TURBINE_GENERATION);

    private static final DisplayCategory<ColossalReiDisplay>[] STANDARD = new DisplayCategory[] {
            category(COOLANT, "jei.colossal_reactors.reactor_coolant", ModBlocks.RESOURCE_PORT.get().asItem().getDefaultInstance(), 180, 62, true),
            category(FUEL, "jei.colossal_reactors.reactor_fuel", ModBlocks.REACTOR_ROD.get().asItem().getDefaultInstance(), 230, 106, true),
            category(HEAT_SINK, "jei.colossal_reactors.reactor_heat_sink", ModBlocks.REACTOR_GLASS.get().asItem().getDefaultInstance(), 180, 54, false),
            category(MELTER, "jei.colossal_reactors.melter", ModBlocks.MELTER.get().asItem().getDefaultInstance(), 180, 78, true),
            category(MELTER_HEAT, "jei.colossal_reactors.melter_heat_source", ModBlocks.MELTER.get().asItem().getDefaultInstance(), 180, 52, false),
            category(ELEC_COIL, "jei.colossal_reactors.elec_coil", ModBlocks.TURBINE_CASING.get().asItem().getDefaultInstance(), 180, 54, false),
            category(TURBINE, "jei.colossal_reactors.turbine_generation", ModBlocks.TURBINE_CONTROLLER.get().asItem().getDefaultInstance(), 180, 54, true),
    };

    private ColossalReiCategories() {}

    static void register(CategoryRegistry registry) {
        for (DisplayCategory<ColossalReiDisplay> cat : STANDARD) {
            registry.add(cat);
        }
        registry.add(heatingCoilCategory());
        pinPlusInside(registry, COOLANT);
        pinPlusInside(registry, FUEL);
        pinPlusInside(registry, HEAT_SINK);
        pinPlusInside(registry, MELTER);
        pinPlusInside(registry, MELTER_HEAT);
        pinPlusInside(registry, HEATING_COIL);
        pinPlusInside(registry, ELEC_COIL);
        pinPlusInside(registry, TURBINE);

        registry.addWorkstations(COOLANT, EntryStacks.of(new ItemStack(ModBlocks.REACTOR_CONTROLLER.get())));
        registry.addWorkstations(FUEL, EntryStacks.of(new ItemStack(ModBlocks.REACTOR_CONTROLLER.get())));
        registry.addWorkstations(HEAT_SINK, EntryStacks.of(new ItemStack(ModBlocks.REACTOR_CONTROLLER.get())));
        registry.addWorkstations(MELTER, EntryStacks.of(new ItemStack(ModBlocks.MELTER.get())));
        registry.addWorkstations(MELTER_HEAT, EntryStacks.of(new ItemStack(ModBlocks.MELTER.get())));
        for (ItemStack coil : HeatingCoilViewerHelper.offCoilStacks()) {
            registry.addWorkstations(HEATING_COIL, EntryStacks.of(coil));
        }
        registry.addWorkstations(ELEC_COIL, EntryStacks.of(new ItemStack(ModBlocks.TURBINE_CONTROLLER.get())));
        registry.addWorkstations(TURBINE, EntryStacks.of(new ItemStack(ModBlocks.TURBINE_CONTROLLER.get())));
    }

    /** Place move-items "+" inside the widened recipe base, next to the top slot row. */
    private static void pinPlusInside(CategoryRegistry registry, CategoryIdentifier<ColossalReiDisplay> category) {
        registry.setPlusButtonArea(
                category, bounds -> new Rectangle(bounds.getMaxX() - 12, bounds.y + RECIPE_PAD + 4, 10, 10));
    }

    private static CategoryIdentifier<ColossalReiDisplay> id(ResourceLocation uid) {
        return CategoryIdentifier.of(uid);
    }

    private static DisplayCategory<ColossalReiDisplay> heatingCoilCategory() {
        ItemStack icon = ModBlocks.MELTER.get().asItem().getDefaultInstance();
        return new DisplayCategory<>() {
            @Override
            public CategoryIdentifier<? extends ColossalReiDisplay> getCategoryIdentifier() {
                return HEATING_COIL;
            }

            @Override
            public Component getTitle() {
                return Component.translatable("jei.colossal_reactors.heating_coil");
            }

            @Override
            public me.shedaniel.rei.api.client.gui.Renderer getIcon() {
                return EntryStacks.of(icon);
            }

            @Override
            public int getDisplayWidth(ColossalReiDisplay display) {
                return RecipeViewerHeatingCoilLayout.WIDTH + RECIPE_PAD * 2 + PLUS_STRIP;
            }

            @Override
            public int getDisplayHeight() {
                return RecipeViewerHeatingCoilLayout.HEIGHT + RECIPE_PAD * 2;
            }

            @Override
            public List<Widget> setupDisplay(ColossalReiDisplay display, Rectangle bounds) {
                List<Widget> widgets = new ArrayList<>();
                widgets.add(Widgets.createRecipeBase(bounds));
                int x = bounds.x + RECIPE_PAD;
                int y = bounds.y + RECIPE_PAD;
                widgets.add(Widgets.createDrawableWidget(
                        (g, mX, mY, delta) -> RecipeViewerHeatingCoilLayout.draw(g, x, y)));

                List<EntryIngredient> inputs = display.getInputEntries();
                List<EntryIngredient> outputs = display.getOutputEntries();
                if (!inputs.isEmpty()) {
                    widgets.add(Widgets.createSlot(new Rectangle(
                                    x + RecipeViewerHeatingCoilLayout.OFF_X,
                                    y + RecipeViewerHeatingCoilLayout.OFF_Y,
                                    18,
                                    18))
                            .disableBackground()
                            .entries(inputs.get(0)));
                }
                int consumeIdx = 0;
                for (int i = 1; i < inputs.size() && consumeIdx < 3; i++) {
                    widgets.add(Widgets.createSlot(new Rectangle(
                                    x + RecipeViewerHeatingCoilLayout.inputSlotX(consumeIdx),
                                    y + RecipeViewerHeatingCoilLayout.IN_Y,
                                    18,
                                    18))
                            .disableBackground()
                            .entries(inputs.get(i)));
                    consumeIdx++;
                }
                if (!outputs.isEmpty()) {
                    widgets.add(Widgets.createSlot(new Rectangle(
                                    x + RecipeViewerHeatingCoilLayout.ON_X,
                                    y + RecipeViewerHeatingCoilLayout.ON_Y,
                                    18,
                                    18))
                            .disableBackground()
                            .entries(outputs.get(0)));
                }
                widgets.add(Widgets.createDrawableWidget(
                        (GuiGraphics g, int mX, int mY, float delta) -> display.drawText(g, x, y)));
                return widgets;
            }
        };
    }

    private static DisplayCategory<ColossalReiDisplay> category(
            CategoryIdentifier<ColossalReiDisplay> id,
            String titleKey,
            ItemStack icon,
            int width,
            int height,
            boolean twoSlots) {
        return new DisplayCategory<>() {
            @Override
            public CategoryIdentifier<? extends ColossalReiDisplay> getCategoryIdentifier() {
                return id;
            }

            @Override
            public Component getTitle() {
                return Component.translatable(titleKey);
            }

            @Override
            public me.shedaniel.rei.api.client.gui.Renderer getIcon() {
                return EntryStacks.of(icon);
            }

            @Override
            public int getDisplayWidth(ColossalReiDisplay display) {
                return width + RECIPE_PAD * 2 + PLUS_STRIP;
            }

            @Override
            public int getDisplayHeight() {
                return height + RECIPE_PAD * 2;
            }

            @Override
            public List<Widget> setupDisplay(ColossalReiDisplay display, Rectangle bounds) {
                List<Widget> widgets = new ArrayList<>();
                widgets.add(Widgets.createRecipeBase(bounds));
                int x = bounds.x + RECIPE_PAD;
                int y = bounds.y + RECIPE_PAD;
                widgets.add(Widgets.createDrawableWidget(
                        (g, mX, mY, delta) -> RecipeViewerLayout.draw(g, x, y, twoSlots)));
                if (!display.getInputEntries().isEmpty()) {
                    widgets.add(Widgets.createSlot(new Rectangle(
                                    x + RecipeViewerLayout.SLOT_IN_X, y + RecipeViewerLayout.SLOT_IN_Y, 18, 18))
                            .disableBackground()
                            .entries(display.getInputEntries().get(0)));
                }
                if (twoSlots && !display.getOutputEntries().isEmpty()) {
                    widgets.add(Widgets.createSlot(new Rectangle(
                                    x + RecipeViewerLayout.SLOT_OUT_X, y + RecipeViewerLayout.SLOT_OUT_Y, 18, 18))
                            .disableBackground()
                            .entries(display.getOutputEntries().get(0)));
                }
                widgets.add(Widgets.createDrawableWidget(
                        (GuiGraphics g, int mX, int mY, float delta) -> display.drawText(g, x, y)));
                return widgets;
            }
        };
    }

    public static EntryIngredient ingredient(ItemStack stack) {
        return EntryIngredients.of(stack);
    }
}
