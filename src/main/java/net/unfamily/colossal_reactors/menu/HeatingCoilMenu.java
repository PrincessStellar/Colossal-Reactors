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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.unfamily.colossal_reactors.block.HeatingCoilBlock;
import net.unfamily.colossal_reactors.blockentity.HeatingCoilBlockEntity;
import net.unfamily.colossal_reactors.client.gui.ResourcePortGuiLayout;
import net.unfamily.colossal_reactors.integration.mekanism.MekChemicalHelper;

import javax.annotation.Nullable;

/**
 * Menu for heating coil GUI (port-style layout, no mode toggles).
 * Client opens via {@link net.neoforged.neoforge.common.extensions.IMenuTypeExtension} with block pos sync.
 */
public class HeatingCoilMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess levelAccess;
    private final ContainerData data;
    @Nullable
    private final HeatingCoilBlockEntity blockEntity;
    @Nullable
    private final BlockPos menuBlockPos;
    /** Set at open from whether the machine item slot was added (not from synced GUI flags). */
    private final boolean hasMachineItemSlot;

    /** Client: block pos from open packet. */
    public HeatingCoilMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    private HeatingCoilMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModMenuTypes.HEATING_COIL_MENU.get(), containerId);
        Level level = playerInventory.player.level();
        BlockEntity entity = level.getBlockEntity(pos);
        this.blockEntity = entity instanceof HeatingCoilBlockEntity coil ? coil : null;
        this.menuBlockPos = pos;
        this.levelAccess = ContainerLevelAccess.create(level, pos);
        this.data = new SimpleContainerData(HeatingCoilBlockEntity.DATA_COUNT);
        addDataSlots(data);
        this.hasMachineItemSlot = addCoilSlots(blockEntity);
        addPlayerSlots(playerInventory);
    }

    /** Server: opened from block entity with live container data. */
    public HeatingCoilMenu(int containerId, Inventory playerInventory, HeatingCoilBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.HEATING_COIL_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.menuBlockPos = blockEntity.getBlockPos();
        this.levelAccess = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        this.data = data;
        addDataSlots(data);
        this.hasMachineItemSlot = addCoilSlots(blockEntity);
        addPlayerSlots(playerInventory);
    }

    /** @return true if the machine item slot was added */
    private boolean addCoilSlots(@Nullable HeatingCoilBlockEntity coil) {
        if (coil != null && coil.hasItemRequirement()) {
            addSlot(new SlotItemHandler(coil.getItemHandler(), 0,
                    ResourcePortGuiLayout.ITEM_SLOT_X, ResourcePortGuiLayout.ITEM_SLOT_Y));
            return true;
        }
        return false;
    }

    /** True if this menu is for the given block pos (used to close it when coil state changes). */
    public boolean isForPosition(BlockPos pos) {
        return menuBlockPos != null && menuBlockPos.equals(pos);
    }

    /** Block pos when opened from block entity (server), or from synced data (client). Used for redstone payload. */
    @Nullable
    public BlockPos getBlockPos() {
        if (menuBlockPos != null) return menuBlockPos;
        return new BlockPos(data.get(8), data.get(9), data.get(10));
    }

    private void addPlayerSlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 94 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 152));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return levelAccess.evaluate((level, pos) -> {
            if (!(level.getBlockState(pos).getBlock() instanceof HeatingCoilBlock)) return false;
            return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
        }).orElse(false);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // No machine item slot (energy/fluid-only coils): do not shift-move within player inv
        // (moveItemStackTo on the same 0..36 range duplicates stacks).
        if (!hasMachineItemSlot) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            stack = stackInSlot.copy();
            if (index == 0) {
                if (!moveItemStackTo(stackInSlot, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stackInSlot, 0, 1, false)) {
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

    /** Energy and other coils without item/burnable consume options have no item slot in the GUI. */
    public boolean showItemSlot() {
        if (data.get(13) != 0) {
            return true;
        }
        return blockEntity != null && blockEntity.hasItemRequirement();
    }

    public int getFluidAmount() { return data.get(0); }
    public int getFluidCapacity() { return data.get(1); }
    public int getFluidId() { return data.get(2); }
    public int getEnergy() { return data.get(3); }
    public int getEnergyCapacity() { return data.get(4); }
    public int getBurnableTicks() { return data.get(5); }
    public boolean showFluidInGui() { return data.get(11) != 0; }
    public boolean showEnergyInGui() { return data.get(12) != 0; }
    public boolean showItemInGui() { return showItemSlot(); }

    /** True when coil datapack declares a chemical consume option (client also requires Mek for the gas bar). */
    public boolean showChemicalInGui() {
        if (data.get(15) != 0) {
            return true;
        }
        return blockEntity != null && blockEntity.hasChemicalRequirement();
    }

    public int getRedstoneMode() { return data.get(14); }

    public long getGasAmountLong() {
        return combineLong(data.get(16), data.get(17));
    }

    public long getGasCapacityLong() {
        long synced = combineLong(data.get(18), data.get(19));
        if (synced > 0) {
            return synced;
        }
        return blockEntity != null ? blockEntity.getGasCapacityMbLong() : 0L;
    }

    private static long combineLong(int low, int high) {
        return (high & 0xFFFFFFFFL) << 32 | (low & 0xFFFFFFFFL);
    }

    /** True when the gas dump button must stay disabled (radioactive Mek gas in the tank). */
    public boolean isGasDumpBlockedByRadioactivity() {
        if (!MekChemicalHelper.isLoaded() || getGasAmountLong() <= 0) {
            return false;
        }
        return MekChemicalHelper.isRadioactiveGasId(getGasRegistryName());
    }

    @Nullable
    public String getGasRegistryName() {
        int len = data.get(20);
        if (len <= 0) return null;
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < 16; i++) {
            int packed = data.get(21 + i);
            for (int j = 0; j < 4 && sb.length() < len; j++) {
                sb.append((char) ((packed >> (j * 8)) & 0xFF));
            }
        }
        return sb.toString();
    }
}
