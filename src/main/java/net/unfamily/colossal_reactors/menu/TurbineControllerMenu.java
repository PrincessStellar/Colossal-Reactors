package net.unfamily.colossal_reactors.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.block.TurbineControllerBlock;
import net.unfamily.colossal_reactors.block.TurbineVisualState;
import net.unfamily.colossal_reactors.blockentity.TurbineControllerBlockEntity;

/** Menu for turbine controller GUI stats sync. */
public class TurbineControllerMenu extends AbstractContainerMenu {

    private static final int INDEX_VALID = 0;
    private static final int INDEX_BLADES = 1;
    private static final int INDEX_RF = 2;
    private static final int INDEX_STEAM = 3;
    private static final int INDEX_COIL_EFF = 4;
    private static final int INDEX_BLADE_EFF = 5;
    private static final int INDEX_FAILURE = 6;
    private static final int INDEX_VISUAL = 7;
    private static final int INDEX_HAS_REDSTONE_PORT = 8;
    private static final int INDEX_REDSTONE_GATE_SATISFIED = 9;
    private static final int INDEX_STEAM_STORED_MB = 10;
    private static final int INDEX_STEAM_CAPACITY_MB = 11;
    private static final int INDEX_POS_X = 12;
    private static final int INDEX_POS_Y = 13;
    private static final int INDEX_POS_Z = 14;
    private static final int DATA_COUNT = 15;

    private final ContainerLevelAccess levelAccess;
    private final ContainerData data;

    public TurbineControllerMenu(int containerId, Inventory playerInventory, TurbineControllerBlockEntity be) {
        super(ModMenuTypes.TURBINE_CONTROLLER_MENU.get(), containerId);
        this.levelAccess = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                var r = be.getCachedResult();
                var level = be.getLevel();
                var blockState = level != null ? level.getBlockState(be.getBlockPos()) : null;
                var visual = (blockState != null && blockState.is(ModBlocks.TURBINE_CONTROLLER.get()))
                        ? blockState.getValue(TurbineControllerBlock.VISUAL)
                        : TurbineVisualState.OFF;
                return switch (index) {
                    case INDEX_VALID -> r.valid() ? 1 : 0;
                    case INDEX_BLADES -> r.validBladeCount();
                    case INDEX_RF -> (int) Math.min(Integer.MAX_VALUE, be.getLastRfPerTick());
                    case INDEX_STEAM -> (int) Math.min(Integer.MAX_VALUE, be.getLastSteamPerTick());
                    case INDEX_COIL_EFF -> (int) (be.getLastCoilEff() * 1000);
                    case INDEX_BLADE_EFF -> (int) (be.getLastBladeEff() * 1000);
                    case INDEX_FAILURE -> {
                        var failure = r.failure();
                        yield failure == null ? -1 : failure.ordinal();
                    }
                    case INDEX_VISUAL -> visual.ordinal();
                    case INDEX_HAS_REDSTONE_PORT -> be.getCachedRedstonePortPositions().length > 0 ? 1 : 0;
                    case INDEX_REDSTONE_GATE_SATISFIED -> be.isRedstoneGateOpen() ? 1 : 0;
                    case INDEX_STEAM_STORED_MB -> Math.max(0, be.getTotalSteamInputMb());
                    case INDEX_STEAM_CAPACITY_MB -> Math.max(0, be.getSteamInputCapacityMb());
                    case INDEX_POS_X -> be.getBlockPos().getX();
                    case INDEX_POS_Y -> be.getBlockPos().getY();
                    case INDEX_POS_Z -> be.getBlockPos().getZ();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addDataSlots(data);
    }

    public TurbineControllerMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.TURBINE_CONTROLLER_MENU.get(), containerId);
        this.levelAccess = ContainerLevelAccess.NULL;
        this.data = new SimpleContainerData(DATA_COUNT);
        addDataSlots(data);
    }

    /** Synced block pos for reboot button (client). */
    public BlockPos getControllerBlockPos() {
        return new BlockPos(data.get(INDEX_POS_X), data.get(INDEX_POS_Y), data.get(INDEX_POS_Z));
    }

    public boolean isValid() { return data.get(INDEX_VALID) != 0; }
    public int getBladeCount() { return data.get(INDEX_BLADES); }
    public long getRfPerTick() { return data.get(INDEX_RF) & 0xFFFFFFFFL; }
    public int getSteamPerTick() { return data.get(INDEX_STEAM); }
    public double getCoilEff() { return data.get(INDEX_COIL_EFF) / 1000.0; }
    public double getBladeEff() { return data.get(INDEX_BLADE_EFF) / 1000.0; }

    /** {@link net.unfamily.colossal_reactors.turbine.TurbineValidation.FailureCode#ordinal()}, or -1 if valid. */
    public int getFailureOrdinal() { return data.get(INDEX_FAILURE); }

    /** {@link TurbineVisualState#ordinal()}. */
    public int getVisualStateId() { return data.get(INDEX_VISUAL); }

    public boolean hasRedstonePort() { return data.get(INDEX_HAS_REDSTONE_PORT) != 0; }

    public boolean isRedstoneGateSatisfied() { return data.get(INDEX_REDSTONE_GATE_SATISFIED) != 0; }

    public int getSteamStoredMb() { return data.get(INDEX_STEAM_STORED_MB); }

    public int getSteamCapacityMb() { return data.get(INDEX_STEAM_CAPACITY_MB); }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(levelAccess, player, ModBlocks.TURBINE_CONTROLLER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
