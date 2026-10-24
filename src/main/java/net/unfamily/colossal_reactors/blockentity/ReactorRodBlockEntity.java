package net.unfamily.colossal_reactors.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.block.ReactorRodBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockEntity for reactor rod.
 * <ul>
 *   <li>Fuel: array of (fuelTypeId, units). Shared max capacity (e.g. 100 uranium + 900 X = 100%). Uranium = colossal_reactors:uranium.</li>
 *   <li>Coolant: up to 10,000 mB of liquid, mixed fluid types (same structure as other mixed buffers).</li>
 *   <li>Solid waste buffer: same structure (id + count), capacity in total count.</li>
 *   <li>Liquid waste buffer: same structure as coolant (fluid id + amount), capacity in mB.</li>
 * </ul>
 */
public class ReactorRodBlockEntity extends BlockEntity {

    /** Default uranium fuel type id (colossal_reactors:uranium). */
    public static final ResourceLocation URANIUM_FUEL_ID = ResourceLocation.fromNamespaceAndPath(ColossalReactors.MODID, "uranium");

    private static final String TAG_FUEL = "Fuel";
    private static final String TAG_FUEL_ID = "Id";
    private static final String TAG_FUEL_UNITS = "Units";
    private static final String TAG_COOLANT = "Coolant";
    private static final String TAG_FLUID_ID = "FluidId";
    private static final String TAG_AMOUNT = "Amount";
    private static final String TAG_SOLID_WASTE = "SolidWaste";
    private static final String TAG_COUNT = "Count";
    private static final String TAG_LIQUID_WASTE = "LiquidWaste";
    private static final String TAG_WASTE_ACCUMULATOR = "WasteAccumulator";

    private static final int COOLANT_CAPACITY_MB = 10_000;
    private static final int SOLID_WASTE_CAPACITY = 1000;
    private static final int LIQUID_WASTE_CAPACITY_MB = 10_000;

    /** Fuel by type: id -> units (float). Total units must be <= getMaxFuelUnits(). */
    private final List<FuelEntry> fuelEntries = new ArrayList<>();
    /** Coolant: mixed fluids, total mB <= COOLANT_CAPACITY_MB. */
    private final List<FluidEntry> coolantEntries = new ArrayList<>();
    /** Solid waste: id -> count. Total count <= SOLID_WASTE_CAPACITY. */
    private final List<SolidWasteEntry> solidWasteEntries = new ArrayList<>();
    /** Liquid waste: same structure as coolant, total <= LIQUID_WASTE_CAPACITY_MB. */
    private final List<FluidEntry> liquidWasteEntries = new ArrayList<>();
    /** Accumulated consumed units per waste type (float, for fractional waste: 1000 units -> 1 item). */
    private final java.util.Map<ResourceLocation, Float> wasteAccumulator = new java.util.HashMap<>();

    public record FuelEntry(ResourceLocation id, float units) {}
    public record FluidEntry(Fluid fluid, int amount) {}
    public record SolidWasteEntry(ResourceLocation id, int count) {}

    public ReactorRodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_ROD_BE.get(), pos, state);
    }

    // ---------- Fuel (array, shared max capacity) ----------

    public List<FuelEntry> getFuelEntries() {
        return List.copyOf(fuelEntries);
    }

    public float getTotalFuelUnits() {
        return (float) fuelEntries.stream().mapToDouble(FuelEntry::units).sum();
    }

    public float getFuelUnits(ResourceLocation fuelId) {
        return (float) fuelEntries.stream()
                .filter(e -> e.id().equals(fuelId))
                .mapToDouble(FuelEntry::units)
                .sum();
    }

    /** Max total fuel units per rod (from config). */
    public static int getMaxFuelUnits() {
        return Config.ROD_MAX_FUEL_UNITS.get();
    }

    /** Add units of a fuel type. Clamps to max total capacity; returns actual amount added. */
    public float addFuel(ResourceLocation fuelId, float units) {
        if (units <= 0) return 0;
        float total = getTotalFuelUnits();
        int max = getMaxFuelUnits();
        float add = Math.min(units, max - total);
        if (add <= 0) return 0;
        mergeFuel(fuelId, add);
        setChanged();
        updateBlockState();
        return add;
    }

    /** Consumes up to `units` of the given fuel type. Returns the amount actually consumed. */
    public float consumeFuel(ResourceLocation fuelId, float units) {
        if (units <= 0) return 0;
        for (int i = 0; i < fuelEntries.size(); i++) {
            if (fuelEntries.get(i).id().equals(fuelId)) {
                FuelEntry e = fuelEntries.get(i);
                float take = Math.min(units, e.units());
                if (take <= 0) return 0;
                float newUnits = e.units() - take;
                if (newUnits <= 0.0001f) {
                    fuelEntries.remove(i);
                } else {
                    fuelEntries.set(i, new FuelEntry(fuelId, newUnits));
                }
                setChanged();
                updateBlockState();
                return take;
            }
        }
        return 0;
    }

    /** Set units for a fuel type (replaces that type). Total is clamped to max. */
    public void setFuel(ResourceLocation fuelId, float units) {
        fuelEntries.removeIf(e -> e.id().equals(fuelId));
        if (units > 0) {
            float totalOthers = getTotalFuelUnits();
            int max = getMaxFuelUnits();
            float allowed = Math.min(units, max - totalOthers);
            if (allowed > 0) {
                fuelEntries.add(new FuelEntry(fuelId, allowed));
            }
        }
        setChanged();
        updateBlockState();
    }

    private void mergeFuel(ResourceLocation fuelId, float add) {
        for (int i = 0; i < fuelEntries.size(); i++) {
            if (fuelEntries.get(i).id().equals(fuelId)) {
                FuelEntry e = fuelEntries.get(i);
                int max = getMaxFuelUnits();
                float totalOthers = getTotalFuelUnits() - e.units();
                float newUnits = Math.min(e.units() + add, max - totalOthers);
                fuelEntries.set(i, new FuelEntry(fuelId, newUnits));
                return;
            }
        }
        fuelEntries.add(new FuelEntry(fuelId, add));
    }

    /** Fill level 0..1 for rendering (1 = full). */
    public float getFillLevel() {
        int max = getMaxFuelUnits();
        return max <= 0 ? 0f : (float) getTotalFuelUnits() / max;
    }

    // ---------- Coolant (mixed, 10k mB) ----------

    public List<FluidEntry> getCoolantEntries() {
        return List.copyOf(coolantEntries);
    }

    public static int getCoolantCapacityMb() {
        return COOLANT_CAPACITY_MB;
    }

    public int getTotalCoolantMb() {
        return coolantEntries.stream().mapToInt(FluidEntry::amount).sum();
    }

    /** Add coolant (mixed). Returns amount actually added. */
    public int addCoolant(Fluid fluid, int amountMb) {
        if (amountMb <= 0 || fluid == null || fluid == Fluids.EMPTY) return 0;
        int total = getTotalCoolantMb();
        int add = Math.min(amountMb, COOLANT_CAPACITY_MB - total);
        if (add <= 0) return 0;
        mergeFluid(coolantEntries, fluid, add);
        setChanged();
        return add;
    }

    /** Drains up to amountMb of the given fluid from coolant. Returns amount actually drained. */
    public int drainCoolant(Fluid fluid, int amountMb) {
        if (amountMb <= 0 || fluid == null || fluid == Fluids.EMPTY) return 0;
        int drained = 0;
        for (int i = coolantEntries.size() - 1; i >= 0 && drained < amountMb; i--) {
            FluidEntry e = coolantEntries.get(i);
            if (e.fluid() != fluid) continue;
            int take = Math.min(amountMb - drained, e.amount());
            if (take <= 0) continue;
            drained += take;
            int remain = e.amount() - take;
            if (remain <= 0) coolantEntries.remove(i);
            else coolantEntries.set(i, new FluidEntry(fluid, remain));
        }
        if (drained > 0) {
            setChanged();
            updateBlockState();
        }
        return drained;
    }

    // ---------- Solid waste (same structure: id + count) ----------

    public List<SolidWasteEntry> getSolidWasteEntries() {
        return List.copyOf(solidWasteEntries);
    }

    public static int getSolidWasteCapacity() {
        return SOLID_WASTE_CAPACITY;
    }

    public int getTotalSolidWasteCount() {
        return solidWasteEntries.stream().mapToInt(SolidWasteEntry::count).sum();
    }

    public int addSolidWaste(ResourceLocation itemId, int count) {
        if (count <= 0) return 0;
        int total = getTotalSolidWasteCount();
        int add = Math.min(count, SOLID_WASTE_CAPACITY - total);
        if (add <= 0) return 0;
        mergeSolidWaste(itemId, add);
        setChanged();
        return add;
    }

    private void mergeSolidWaste(ResourceLocation id, int add) {
        for (int i = 0; i < solidWasteEntries.size(); i++) {
            if (solidWasteEntries.get(i).id().equals(id)) {
                SolidWasteEntry e = solidWasteEntries.get(i);
                solidWasteEntries.set(i, new SolidWasteEntry(id, e.count() + add));
                return;
            }
        }
        solidWasteEntries.add(new SolidWasteEntry(id, add));
    }

    /**
     * Accumulates waste buffer units and spits solid waste when a full {@code produce} grant is reached.
     */
    public void recordConsumedAndAddWaste(ResourceLocation wasteId, float wasteBufferUnits, int unitsPerWaste, int produce) {
        if (wasteBufferUnits <= 0 || unitsPerWaste <= 0 || produce <= 0) {
            return;
        }
        float total = wasteAccumulator.getOrDefault(wasteId, 0f) + wasteBufferUnits;
        int wasteCount = (int) Math.floor(total * produce / (float) unitsPerWaste);
        float unitsPerOutputGrant = (float) unitsPerWaste / (float) produce;
        wasteAccumulator.put(wasteId, total - wasteCount * unitsPerOutputGrant);
        if (wasteCount > 0) {
            addSolidWaste(wasteId, wasteCount);
        }
    }

    /** Takes up to count of the given waste item from this rod. Returns amount actually taken. */
    public int takeSolidWaste(ResourceLocation itemId, int count) {
        if (count <= 0) return 0;
        for (int i = 0; i < solidWasteEntries.size(); i++) {
            SolidWasteEntry e = solidWasteEntries.get(i);
            if (!e.id().equals(itemId)) continue;
            int take = Math.min(count, e.count());
            if (take <= 0) return 0;
            int remain = e.count() - take;
            if (remain <= 0) solidWasteEntries.remove(i);
            else solidWasteEntries.set(i, new SolidWasteEntry(itemId, remain));
            setChanged();
            updateBlockState();
            return take;
        }
        return 0;
    }

    // ---------- Liquid waste (same structure as coolant) ----------

    public List<FluidEntry> getLiquidWasteEntries() {
        return List.copyOf(liquidWasteEntries);
    }

    public static int getLiquidWasteCapacityMb() {
        return LIQUID_WASTE_CAPACITY_MB;
    }

    public int getTotalLiquidWasteMb() {
        return liquidWasteEntries.stream().mapToInt(FluidEntry::amount).sum();
    }

    public int addLiquidWaste(Fluid fluid, int amountMb) {
        if (amountMb <= 0 || fluid == null || fluid == Fluids.EMPTY) return 0;
        int total = getTotalLiquidWasteMb();
        int add = Math.min(amountMb, LIQUID_WASTE_CAPACITY_MB - total);
        if (add <= 0) return 0;
        mergeFluid(liquidWasteEntries, fluid, add);
        setChanged();
        return add;
    }

    /** Drains up to amountMb of the given fluid from liquid waste. Returns amount actually drained. */
    public int drainLiquidWaste(Fluid fluid, int amountMb) {
        if (amountMb <= 0 || fluid == null || fluid == Fluids.EMPTY) return 0;
        int drained = 0;
        for (int i = liquidWasteEntries.size() - 1; i >= 0 && drained < amountMb; i--) {
            FluidEntry e = liquidWasteEntries.get(i);
            if (e.fluid() != fluid) continue;
            int take = Math.min(amountMb - drained, e.amount());
            if (take <= 0) continue;
            drained += take;
            int remain = e.amount() - take;
            if (remain <= 0) liquidWasteEntries.remove(i);
            else liquidWasteEntries.set(i, new FluidEntry(fluid, remain));
        }
        if (drained > 0) setChanged();
        return drained;
    }

    private void mergeFluid(List<FluidEntry> list, Fluid fluid, int add) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).fluid() == fluid) {
                FluidEntry e = list.get(i);
                list.set(i, new FluidEntry(fluid, e.amount() + add));
                return;
            }
        }
        list.add(new FluidEntry(fluid, add));
    }

    // ---------- Block state (fill level for model) ----------

    private static int fillPercentToLevel(float fillPercent) {
        if (fillPercent <= 0f) return 0;
        if (fillPercent >= 1f) return 12;
        int p = (int) (fillPercent * 100);
        if (p <= 5) return 1;
        if (p <= 10) return 2;
        if (p <= 20) return 3;
        if (p <= 30) return 4;
        if (p <= 40) return 5;
        if (p <= 50) return 6;
        if (p <= 60) return 7;
        if (p <= 70) return 8;
        if (p <= 80) return 9;
        if (p <= 90) return 10;
        return 11;
    }

    /** Shared mapping for controller-driven visual fill. */
    public static int fillPercentToLevelForController(float fillPercent) {
        return fillPercentToLevel(fillPercent);
    }

    private void updateBlockState() {
        if (level == null) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof ReactorRodBlock)) return;
        int fillLevel = fillPercentToLevel(getFillLevel());
        BlockState newState = state;
        if (state.getValue(ReactorRodBlock.FILL) != fillLevel) {
            newState = state.setValue(ReactorRodBlock.FILL, fillLevel);
            level.setBlock(worldPosition, newState, Block.UPDATE_CLIENTS);
        }
        if (!level.isClientSide()) {
            // Always sync BE so client fuel (rod tint) updates even when FILL is unchanged.
            level.sendBlockUpdated(worldPosition, state, newState, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        super.onDataPacket(net, pkt, registries);
        if (pkt.getTag() != null && !pkt.getTag().isEmpty()) {
            loadAdditional(pkt.getTag(), registries);
        }
    }

    // ---------- NBT ----------

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag fuelList = new ListTag();
        for (FuelEntry e : fuelEntries) {
            CompoundTag c = new CompoundTag();
            c.putString(TAG_FUEL_ID, e.id().toString());
            c.putFloat(TAG_FUEL_UNITS, e.units());
            fuelList.add(c);
        }
        tag.put(TAG_FUEL, fuelList);

        saveFluidList(tag, TAG_COOLANT, coolantEntries);
        saveSolidWasteList(tag);
        saveFluidList(tag, TAG_LIQUID_WASTE, liquidWasteEntries);
        if (!wasteAccumulator.isEmpty()) {
            CompoundTag accTag = new CompoundTag();
            for (var e : wasteAccumulator.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    accTag.putFloat(e.getKey().toString(), e.getValue());
                }
            }
            if (!accTag.isEmpty()) tag.put(TAG_WASTE_ACCUMULATOR, accTag);
        }
    }

    private void saveFluidList(CompoundTag tag, String key, List<FluidEntry> list) {
        ListTag nbtList = new ListTag();
        for (FluidEntry e : list) {
            if (e.fluid() == null || e.fluid() == Fluids.EMPTY || e.amount() <= 0) continue;
            CompoundTag c = new CompoundTag();
            c.putString(TAG_FLUID_ID, BuiltInRegistries.FLUID.getKey(e.fluid()).toString());
            c.putInt(TAG_AMOUNT, e.amount());
            nbtList.add(c);
        }
        tag.put(key, nbtList);
    }

    private void saveSolidWasteList(CompoundTag tag) {
        ListTag nbtList = new ListTag();
        for (SolidWasteEntry e : solidWasteEntries) {
            CompoundTag c = new CompoundTag();
            c.putString(TAG_FUEL_ID, e.id().toString());
            c.putInt(TAG_COUNT, e.count());
            nbtList.add(c);
        }
        tag.put(TAG_SOLID_WASTE, nbtList);
    }

    private static final String TAG_FUEL_UNITS_LEGACY = "fuelUnits";

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuelEntries.clear();
        if (tag.contains(TAG_FUEL, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_FUEL, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(c.getString(TAG_FUEL_ID));
                float units = c.contains(TAG_FUEL_UNITS, Tag.TAG_FLOAT) ? c.getFloat(TAG_FUEL_UNITS) : c.getInt(TAG_FUEL_UNITS);
                if (id != null && units > 0) {
                    fuelEntries.add(new FuelEntry(id, units));
                }
            }
        }
        // Legacy: single fuelUnits int -> treat as uranium
        if (fuelEntries.isEmpty() && tag.contains(TAG_FUEL_UNITS_LEGACY)) {
            int legacy = tag.getInt(TAG_FUEL_UNITS_LEGACY);
            if (legacy > 0) {
                fuelEntries.add(new FuelEntry(URANIUM_FUEL_ID, Math.min((float) legacy, getMaxFuelUnits())));
            }
        }
        coolantEntries.clear();
        loadFluidList(tag, TAG_COOLANT, coolantEntries, registries);
        solidWasteEntries.clear();
        if (tag.contains(TAG_SOLID_WASTE, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_SOLID_WASTE, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(c.getString(TAG_FUEL_ID));
                int count = c.getInt(TAG_COUNT);
                if (id != null && count > 0) {
                    solidWasteEntries.add(new SolidWasteEntry(id, count));
                }
            }
        }
        liquidWasteEntries.clear();
        loadFluidList(tag, TAG_LIQUID_WASTE, liquidWasteEntries, registries);
        wasteAccumulator.clear();
        if (tag.contains(TAG_WASTE_ACCUMULATOR, Tag.TAG_COMPOUND)) {
            CompoundTag accTag = tag.getCompound(TAG_WASTE_ACCUMULATOR);
            for (String key : accTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                float val = accTag.getTagType(key) == Tag.TAG_FLOAT ? accTag.getFloat(key) : accTag.getInt(key);
                if (id != null && val > 0) wasteAccumulator.put(id, val);
            }
        }
        updateBlockState();
    }

    private void loadFluidList(CompoundTag tag, String key, List<FluidEntry> list, net.minecraft.core.HolderLookup.Provider registries) {
        if (!tag.contains(key, Tag.TAG_LIST)) return;
        ListTag nbtList = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < nbtList.size(); i++) {
            CompoundTag c = nbtList.getCompound(i);
            Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(c.getString(TAG_FLUID_ID)));
            int amount = c.getInt(TAG_AMOUNT);
            if (fluid != null && fluid != Fluids.EMPTY && amount > 0) {
                list.add(new FluidEntry(fluid, amount));
            }
        }
    }
}
