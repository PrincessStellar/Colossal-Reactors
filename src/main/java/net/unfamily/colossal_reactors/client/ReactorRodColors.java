package net.unfamily.colossal_reactors.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.unfamily.colossal_reactors.ClientConfig;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.block.ReactorRodBlock;
import net.unfamily.colossal_reactors.blockentity.ReactorRodBlockEntity;
import net.unfamily.colossal_reactors.fuel.FuelDefinition;
import net.unfamily.colossal_reactors.fuel.FuelLoader;
import net.unfamily.colossal_reactors.item.ModItems;

/**
 * Tints reactor rod inside fill ({@code tintindex 0}) from dominant fuel color.
 * Item form always uses the datapack uranium green.
 */
public final class ReactorRodColors {

    /** Datapack uranium fuel color ({@code reactor_fuel.json} {@code #45c159}). */
    public static final int URANIUM_INSIDE_RGB = 0x45C159;

    private ReactorRodColors() {}

    public static void register(RegisterColorHandlersEvent.Block event) {
        event.register(ReactorRodColors::rodColor, ModBlocks.REACTOR_ROD.get());
    }

    public static void registerItem(RegisterColorHandlersEvent.Item event) {
        event.register(ReactorRodColors::itemColor, ModItems.REACTOR_ROD.get());
    }

    private static int itemColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | URANIUM_INSIDE_RGB;
    }

    private static int rodColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        if (tintIndex != 0) {
            return 0xFFFFFFFF;
        }
        if (state.hasProperty(ReactorRodBlock.FILL) && state.getValue(ReactorRodBlock.FILL) == 0) {
            return 0xFF000000 | ClientConfig.getRodInsideFallbackColorRgb();
        }
        int rgb = ClientConfig.getRodInsideFallbackColorRgb();
        if (level != null && pos != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ReactorRodBlockEntity rod) {
                ResourceLocation fuelId = dominantFuelId(rod);
                if (fuelId != null) {
                    FuelDefinition def = FuelLoader.get(fuelId);
                    if (def != null && def.colorRgb() != null) {
                        rgb = def.colorRgb();
                    }
                }
            }
        }
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static ResourceLocation dominantFuelId(ReactorRodBlockEntity rod) {
        ResourceLocation best = null;
        float bestUnits = 0f;
        for (ReactorRodBlockEntity.FuelEntry e : rod.getFuelEntries()) {
            if (e.units() > bestUnits) {
                bestUnits = e.units();
                best = e.id();
            }
        }
        return best;
    }
}
