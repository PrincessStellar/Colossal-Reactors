package net.unfamily.colossal_reactors.compat.rei;

import java.util.ArrayList;
import java.util.List;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.util.ClientEntryStacks;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.compat.jei.CoolantRecipeCategory;
import net.unfamily.colossal_reactors.compat.jei.ElecCoilRecipeCategory;
import net.unfamily.colossal_reactors.compat.jei.FuelRecipeCategory;
import net.unfamily.colossal_reactors.compat.jei.HeatSinkRecipeCategory;
import net.unfamily.colossal_reactors.compat.jei.HeatingCoilRecipeCategory;
import net.unfamily.colossal_reactors.compat.jei.JeiRecipeBackgroundDrawable;
import net.unfamily.colossal_reactors.compat.jei.MelterHeatSourceRecipeCategory;
import net.unfamily.colossal_reactors.compat.jei.MelterRecipeCategory;
import net.unfamily.colossal_reactors.compat.jei.TurbineGenerationRecipeCategory;

public final class ColossalReiCategories {
    public static final CategoryIdentifier<ColossalReiDisplay> COOLANT = id(CoolantRecipeCategory.UID);
    public static final CategoryIdentifier<ColossalReiDisplay> FUEL = id(FuelRecipeCategory.UID);
    public static final CategoryIdentifier<ColossalReiDisplay> HEAT_SINK = id(HeatSinkRecipeCategory.UID);
    public static final CategoryIdentifier<ColossalReiDisplay> MELTER = id(MelterRecipeCategory.UID);
    public static final CategoryIdentifier<ColossalReiDisplay> MELTER_HEAT = id(MelterHeatSourceRecipeCategory.UID);
    public static final CategoryIdentifier<ColossalReiDisplay> HEATING_COIL = id(HeatingCoilRecipeCategory.UID);
    public static final CategoryIdentifier<ColossalReiDisplay> ELEC_COIL = id(ElecCoilRecipeCategory.UID);
    public static final CategoryIdentifier<ColossalReiDisplay> TURBINE = id(TurbineGenerationRecipeCategory.UID);

    private static final DisplayCategory<ColossalReiDisplay>[] ALL = new DisplayCategory[] {
            category(COOLANT, "jei.colossal_reactors.reactor_coolant", ModBlocks.RESOURCE_PORT.get().asItem().getDefaultInstance(), 180, 62, true),
            category(FUEL, "jei.colossal_reactors.reactor_fuel", ModBlocks.REACTOR_ROD.get().asItem().getDefaultInstance(), 230, 106, true),
            category(HEAT_SINK, "jei.colossal_reactors.reactor_heat_sink", ModBlocks.REACTOR_GLASS.get().asItem().getDefaultInstance(), 180, 54, false),
            category(MELTER, "jei.colossal_reactors.melter", ModBlocks.MELTER.get().asItem().getDefaultInstance(), 180, 78, true),
            category(MELTER_HEAT, "jei.colossal_reactors.melter_heat_source", ModBlocks.MELTER.get().asItem().getDefaultInstance(), 180, 52, false),
            category(HEATING_COIL, "jei.colossal_reactors.heating_coil", ModBlocks.MELTER.get().asItem().getDefaultInstance(), 170, 112, false),
            category(ELEC_COIL, "jei.colossal_reactors.elec_coil", ModBlocks.TURBINE_CASING.get().asItem().getDefaultInstance(), 180, 78, false),
            category(TURBINE, "jei.colossal_reactors.turbine_generation", ModBlocks.TURBINE_CONTROLLER.get().asItem().getDefaultInstance(), 180, 54, true),
    };

    private ColossalReiCategories() {}

    static void register(CategoryRegistry registry) {
        for (DisplayCategory<ColossalReiDisplay> cat : ALL) {
            registry.add(cat);
        }
        registry.addWorkstations(COOLANT, EntryStacks.of(new ItemStack(ModBlocks.REACTOR_CONTROLLER.get())));
        registry.addWorkstations(FUEL, EntryStacks.of(new ItemStack(ModBlocks.REACTOR_CONTROLLER.get())));
        registry.addWorkstations(HEAT_SINK, EntryStacks.of(new ItemStack(ModBlocks.REACTOR_CONTROLLER.get())));
        registry.addWorkstations(MELTER, EntryStacks.of(new ItemStack(ModBlocks.MELTER.get())));
        registry.addWorkstations(MELTER_HEAT, EntryStacks.of(new ItemStack(ModBlocks.MELTER.get())));
        registry.addWorkstations(HEATING_COIL, EntryStacks.of(new ItemStack(ModBlocks.MELTER.get())));
        registry.addWorkstations(ELEC_COIL, EntryStacks.of(new ItemStack(ModBlocks.TURBINE_CONTROLLER.get())));
        registry.addWorkstations(TURBINE, EntryStacks.of(new ItemStack(ModBlocks.TURBINE_CONTROLLER.get())));
    }

    private static CategoryIdentifier<ColossalReiDisplay> id(Identifier uid) {
        return CategoryIdentifier.of(uid);
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
                return width;
            }

            @Override
            public int getDisplayHeight() {
                return height;
            }

            @Override
            public List<Widget> setupDisplay(ColossalReiDisplay display, Rectangle bounds) {
                List<Widget> widgets = new ArrayList<>();
                JeiRecipeBackgroundDrawable bg = new JeiRecipeBackgroundDrawable(width, height, twoSlots);
                widgets.add(Widgets.createDrawableWidget((g, mX, mY, delta) -> bg.draw(g, bounds.x, bounds.y)));
                int x = bounds.x;
                int y = bounds.y;
                if (!display.getInputEntries().isEmpty()) {
                    widgets.add(Widgets.createSlot(new Rectangle(x + JeiRecipeBackgroundDrawable.SLOT_IN_X,
                            y + JeiRecipeBackgroundDrawable.SLOT_IN_Y, 18, 18)).entries(display.getInputEntries().get(0)));
                }
                if (twoSlots && display.getOutputEntries().size() > 0) {
                    widgets.add(Widgets.createSlot(new Rectangle(x + JeiRecipeBackgroundDrawable.SLOT_OUT_X,
                            y + JeiRecipeBackgroundDrawable.SLOT_OUT_Y, 18, 18)).entries(display.getOutputEntries().get(0)));
                }
                widgets.add(Widgets.createDrawableWidget((GuiGraphics g, int mX, int mY, float delta) ->
                        display.drawText(g, x, y)));
                return widgets;
            }
        };
    }

    public static EntryIngredient ingredient(net.minecraft.world.item.ItemStack stack) {
        return EntryIngredients.of(stack);
    }
}
