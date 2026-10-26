package net.unfamily.colossal_reactors.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.blockentity.ReactorBuilderBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Menu for Reactor Builder GUI. Texture 230x250.
 */
public class ReactorBuilderMenu extends AbstractContainerMenu {

    private static final int BUFFER_ROWS = 3;
    private static final int BUFFER_COLS = 9;
    public static final int BUFFER_SLOTS = BUFFER_ROWS * BUFFER_COLS;

    /** Buffer slot positions (top-left of 9x3 grid). */
    private static final int BUFFER_X = 45;
    private static final int BUFFER_Y = 102;

    /** Player inventory (3x9) and hotbar (1x9). */
    private static final int PLAYER_X = 45;
    private static final int PLAYER_Y = 168;
    private static final int HOTBAR_Y = 226;

    private final ContainerLevelAccess levelAccess;
    private final ContainerData fluidData;
    private final ContainerData sizeData;

    /**
     * Client only: {@link net.unfamily.colossal_reactors.client.gui.ReactorBuilderScreen} sets this when
     * switching to simulation view so buffer + player slots are not drawn or interacted with.
     */
    private boolean hideAllSlotsForSimulationView;

    private final @Nullable ReactorBuilderBlockEntity blockEntity;

    /** Client: reads BlockPos from menu open payload and resolves {@link ReactorBuilderBlockEntity} from the level. */
    public ReactorBuilderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, playerInventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    /** Server: opened from {@link ReactorBuilderBlockEntity#createMenu}. */
    public ReactorBuilderMenu(int containerId, Inventory playerInventory, ReactorBuilderBlockEntity reactorBuilder) {
        this(containerId, playerInventory, (BlockEntity) reactorBuilder);
    }

    private ReactorBuilderMenu(int containerId, Inventory playerInventory, @Nullable BlockEntity entity) {
        super(ModMenuTypes.REACTOR_BUILDER_MENU.get(), containerId);
        if (entity instanceof ReactorBuilderBlockEntity rbe) {
            this.blockEntity = rbe;
            this.levelAccess = ContainerLevelAccess.create(rbe.getLevel(), rbe.getBlockPos());
            this.fluidData = rbe.getFluidData();
            this.sizeData = rbe.getSizeData();
        } else {
            this.blockEntity = null;
            this.levelAccess = ContainerLevelAccess.NULL;
            this.fluidData = new SimpleContainerData(3);
            this.sizeData = new SimpleContainerData(17);
        }
        addDataSlots(fluidData);
        addDataSlots(sizeData);
        if (blockEntity != null) {
            addBufferSlots(blockEntity.getBufferHandler());
        } else {
            addBufferSlots(new ItemStackHandler(BUFFER_SLOTS));
        }
        addPlayerInventorySlots(playerInventory);
    }

    /** Mark-input filter ghost for buffer slot index (0..26). */
    public ItemStack getMarkInputFilter(int slot) {
        return blockEntity != null ? blockEntity.getMarkInputFilter(slot) : ItemStack.EMPTY;
    }

    public boolean hasMarkInputFilter(int slot) {
        return blockEntity != null && blockEntity.hasMarkInputFilter(slot);
    }

    public @Nullable ReactorBuilderBlockEntity getBlockEntity() {
        return blockEntity;
    }

    private void addBufferSlots(IItemHandler handler) {
        for (int row = 0; row < BUFFER_ROWS; row++) {
            for (int col = 0; col < BUFFER_COLS; col++) {
                int index = col + row * BUFFER_COLS;
                addSlot(new SlotItemHandler(handler, index, BUFFER_X + col * 18, BUFFER_Y + row * 18) {
                    @Override
                    public boolean isActive() {
                        return !hideAllSlotsForSimulationView;
                    }
                });
            }
        }
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int invSlot = col + row * 9 + 9;
                addSlot(new Slot(playerInventory, invSlot, PLAYER_X + col * 18, PLAYER_Y + row * 18) {
                    @Override
                    public boolean isActive() {
                        return !hideAllSlotsForSimulationView;
                    }
                });
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, PLAYER_X + col * 18, HOTBAR_Y) {
                @Override
                public boolean isActive() {
                    return !hideAllSlotsForSimulationView;
                }
            });
        }
    }

    /** Client: hide buffer + player inventory + hotbar slots while the builder screen is in simulation view. */
    public void setHideAllSlotsForSimulationView(boolean hide) {
        this.hideAllSlotsForSimulationView = hide;
    }

    public boolean isHideAllSlotsForSimulationView() {
        return hideAllSlotsForSimulationView;
    }

    public int getFluidAmount() { return fluidData.get(0); }
    public int getFluidCapacity() { return fluidData.get(1); }
    /** Fluid registry id for GUI; -1 if empty. */
    public int getFluidId() { return fluidData.get(2); }

    /** Display "Left": shows sizeRight (L/R inverted for GUI). */
    public int getSizeLeft() { return sizeData.get(1); }
    /** Display "Right": shows sizeLeft (L/R inverted for GUI). */
    public int getSizeRight() { return sizeData.get(0); }
    public int getSizeH() { return sizeData.get(2); }
    public int getSizeD() { return sizeData.get(3); }
    /** Block position for C2S payloads; prefers live BE (same as Pattern Crafter). */
    public BlockPos getBlockPos() {
        if (blockEntity != null) {
            return blockEntity.getBlockPos();
        }
        return new BlockPos(sizeData.get(4), sizeData.get(5), sizeData.get(6));
    }
    /** Heat sink option index (0=Air, 1..=definition). Synced via sizeData index 7. */
    public int getHeatSinkIndex() { return sizeData.get(7); }
    /** Open top when built (index 8): 0=closed, 1=open. */
    public boolean isOpenTop() { return sizeData.get(8) != 0; }
    /** Rod pattern (index 9): 0=DOTS, 1=CHECKERBOARD, 2=EXPANSION. */
    public int getRodPattern() { return sizeData.get(9); }
    /** Pattern mode (index 10): 0=OPTIMIZED, 1=PRODUCTION, 2=ECONOMY. */
    public int getPatternMode() { return sizeData.get(10); }
    /** Building in progress (index 11): 0=Build, 1=Stop. */
    public boolean isBuilding() { return sizeData.get(11) != 0; }
    /** Invalid blocks detected during build (index 12). Show warning in GUI. */
    public boolean isInvalidBlocksDetected() { return sizeData.get(12) != 0; }

    /** Build progress percent (index 13). */
    public int getBuildProgressPercent() { return sizeData.get(13); }
    /** Whether progress should be shown (index 14). */
    public boolean isBuildProgressVisible() { return sizeData.get(14) != 0; }
    /** Simulation coolant index (index 15): 0 = None, 1.. = visible coolant. */
    public int getSimCoolantIndex() { return sizeData.get(15); }
    /** Simulation fuel index (index 16): index into visible fuel list. */
    public int getSimFuelIndex() { return sizeData.get(16); }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(levelAccess, player, ModBlocks.REACTOR_BUILDER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            stack = stackInSlot.copy();
            if (index < BUFFER_SLOTS) {
                if (!moveItemStackTo(stackInSlot, BUFFER_SLOTS, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stackInSlot, 0, BUFFER_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return stack;
    }
}
