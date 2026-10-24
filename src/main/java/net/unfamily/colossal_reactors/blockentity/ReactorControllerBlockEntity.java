package net.unfamily.colossal_reactors.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.block.ReactorRodBlock;
import net.unfamily.colossal_reactors.coolant.CoolantDefinition;
import net.unfamily.colossal_reactors.coolant.CoolantLoader;
import net.unfamily.colossal_reactors.heatsink.HeatSinkLoader;
import net.unfamily.colossal_reactors.menu.ReactorControllerMenu;
import net.unfamily.colossal_reactors.reactor.ReactorValidation;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockEntity for the reactor controller. Caches multiblock validation result.
 * State (OFF/VALIDATING/ON) and tick logic are in ReactorControllerBlock.
 * When ON, right-click opens the Reactor OS GUI.
 */
public class ReactorControllerBlockEntity extends BlockEntity implements MenuProvider {

    private ReactorValidation.Result cachedResult;
    private ServerPlayer lastInteractingPlayer;

    /**
     * Runtime caches of reactor parts (positions as {@link BlockPos#asLong()}), rebuilt on validate/revalidate.
     * This avoids full-volume scans every tick for large reactors.
     */
    private long[] cachedRodPositions = new long[0];
    private long[] cachedPowerPortPositions = new long[0];
    private long[] cachedResourcePortPositions = new long[0];
    private long[] cachedRedstonePortPositions = new long[0];
    /** Validation result the part caches were last built for (see {@link #rebuildPartCachesIfNeeded}). */
    @Nullable
    private ReactorValidation.Result partCachesResult;

    /**
     * Cached reactor-wide computations that previously scanned the whole interior each tick.
     * Rebuilt on validate/revalidate together with part caches.
     */
    private double cachedEffectiveRodCount = 0.0;
    @Nullable
    private HeatSinkStaticCache cachedHeatSinkStaticCache;

    public record HeatSinkStaticCache(
            int countRod,
            int countAdj,
            int countNon,
            double sumFuelAdj,
            double sumEnergyAdj,
            double sumOverheatingAdj,
            double sumFuelNon,
            double sumEnergyNon,
            double sumOverheatingNon
    ) {}

    // ---- Aggregated fuel + waste (performance: avoid per-rod updates) ----
    private static final String TAG_FUEL_AGG = "FuelAgg";
    private static final String TAG_WASTE_AGG = "WasteAgg";
    private static final String TAG_COOLANT_AGG = "CoolantAgg";
    private static final String TAG_ID = "Id";
    private static final String TAG_UNITS = "Units";
    private static final String TAG_MB = "Mb";
    private static final String TAG_WASTE_UNITS = "WasteUnits";

    public record FuelEntry(ResourceLocation id, float units) {}
    public record WasteEntry(ResourceLocation id, float units) {}
    public record CoolantEntry(ResourceLocation fluidId, int mb) {}

    private final List<FuelEntry> fuelEntries = new ArrayList<>();
    private final List<WasteEntry> wasteEntries = new ArrayList<>();
    private final List<CoolantEntry> coolantEntries = new ArrayList<>();

    private int cachedRodFillLevel = 0;
    /** Dominant-fuel inside tint pushed to rod BEs (controller holds shared fuel). */
    private int cachedRodInsideTintRgb = ReactorRodBlockEntity.DEFAULT_INSIDE_TINT_RGB;
    private int rodVisualUpdateCursor = 0;

    /** Last tick stats for GUI (updated by ReactorSimulation.tick). Fuel is ingots/tick * 100 (e.g. 26 = 0.26). */
    private long lastRfPerTick;
    private int lastSteamPerTick;
    private int lastWaterPerTick;
    private int lastFuelPerTickHundredths;

    /** Reactor stability 0–1000 permille (0.0%–100.0%) when reactor unstability is enabled. Default 1000 = 100%. */
    private int stabilityPermille = 1000;

    public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_CONTROLLER_BE.get(), pos, state);
    }

    public void setLastInteractingPlayer(Player player) {
        this.lastInteractingPlayer = player instanceof ServerPlayer sp ? sp : null;
    }

    /** Called from block tick when VALIDATING -> ON/OFF; shows message in action bar. */
    public void notifyValidationResult() {
        if (lastInteractingPlayer != null && cachedResult != null) {
            lastInteractingPlayer.displayClientMessage(ReactorValidation.failureMessage(cachedResult), true);
            if (level != null) {
                ReactorValidation.sendFailureMarkers(lastInteractingPlayer, level, cachedResult);
            }
            lastInteractingPlayer = null;
        }
    }

    public ReactorValidation.Result getCachedResult() {
        return cachedResult;
    }

    /** Set from block when validation is run (e.g. on player click). */
    public void setCachedResult(ReactorValidation.Result result) {
        this.cachedResult = result;
    }

    public long[] getCachedRodPositions() { return cachedRodPositions; }
    public long[] getCachedPowerPortPositions() { return cachedPowerPortPositions; }
    public long[] getCachedResourcePortPositions() { return cachedResourcePortPositions; }
    public long[] getCachedRedstonePortPositions() { return cachedRedstonePortPositions; }
    public double getCachedEffectiveRodCount() { return cachedEffectiveRodCount; }
    @Nullable
    public HeatSinkStaticCache getCachedHeatSinkStaticCache() { return cachedHeatSinkStaticCache; }

    public List<FuelEntry> getFuelEntries() { return List.copyOf(fuelEntries); }
    public float getTotalFuelUnits() { return (float) fuelEntries.stream().mapToDouble(FuelEntry::units).sum(); }

    public List<WasteEntry> getWasteEntries() { return List.copyOf(wasteEntries); }
    public float getTotalWasteUnits() { return (float) wasteEntries.stream().mapToDouble(WasteEntry::units).sum(); }
    public float getTotalFuelAndWasteUnits() { return getTotalFuelUnits() + getTotalWasteUnits(); }

    public List<CoolantEntry> getCoolantEntries() { return List.copyOf(coolantEntries); }

    public int getTotalCoolantMb() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, coolantEntries.stream().mapToLong(CoolantEntry::mb).sum()));
    }

    public int getCoolantCapacityMbTotal() {
        int perRod = ReactorRodBlockEntity.getCoolantCapacityMb();
        long rods = (cachedRodPositions != null && cachedRodPositions.length > 0)
                ? cachedRodPositions.length
                : (cachedResult != null && cachedResult.valid() ? cachedResult.rodCount() : 0);
        long total = rods * (long) Math.max(0, perRod);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, total));
    }

    /** Adds coolant stored under a registry key (fluid id or {@link CoolantDefinition#coolantId()} for gas-only coolants). */
    public int addCoolantByKey(ResourceLocation key, int amountMb) {
        if (amountMb <= 0 || key == null) return 0;
        int total = getTotalCoolantMb();
        int max = getCoolantCapacityMbTotal();
        int add = Math.min(amountMb, Math.max(0, max - total));
        if (add <= 0) return 0;
        for (int i = 0; i < coolantEntries.size(); i++) {
            CoolantEntry e = coolantEntries.get(i);
            if (key.equals(e.fluidId())) {
                coolantEntries.set(i, new CoolantEntry(key, e.mb() + add));
                setChanged();
                return add;
            }
        }
        coolantEntries.add(new CoolantEntry(key, add));
        setChanged();
        return add;
    }

    public int addCoolant(Fluid fluid, int amountMb) {
        if (amountMb <= 0 || fluid == null || fluid == Fluids.EMPTY) return 0;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) return 0;
        return addCoolantByKey(id, amountMb);
    }

    private int consumeCoolantByKey(ResourceLocation key, int amountMb) {
        if (amountMb <= 0 || key == null) return 0;
        int remaining = amountMb;
        int consumed = 0;
        for (int i = 0; i < coolantEntries.size() && remaining > 0; i++) {
            CoolantEntry e = coolantEntries.get(i);
            if (!key.equals(e.fluidId())) continue;
            int take = Math.min(remaining, Math.max(0, e.mb()));
            if (take <= 0) continue;
            int left = e.mb() - take;
            consumed += take;
            remaining -= take;
            if (left <= 0) {
                coolantEntries.remove(i);
                i--;
            } else {
                coolantEntries.set(i, new CoolantEntry(key, left));
            }
        }
        if (consumed > 0) setChanged();
        return consumed;
    }

    public int consumeCoolant(Fluid fluid, int amountMb) {
        if (amountMb <= 0 || fluid == null || fluid == Fluids.EMPTY) return 0;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) return 0;
        return consumeCoolantByKey(id, amountMb);
    }

    /**
     * Consumes coolant from the aggregated buffer for any fluid matching {@code inputSelectors}
     * (fluid ids and tags from coolant JSON).
     */
    public int consumeCoolantMatching(List<String> inputSelectors, int amountMb) {
        if (amountMb <= 0 || inputSelectors == null || inputSelectors.isEmpty()) {
            return 0;
        }
        int remaining = amountMb;
        int consumed = 0;
        int i = 0;
        while (remaining > 0 && i < coolantEntries.size()) {
            CoolantEntry e = coolantEntries.get(i);
            boolean matches = false;
            Fluid fluid = BuiltInRegistries.FLUID.get(e.fluidId());
            if (fluid != null && fluid != Fluids.EMPTY
                    && net.unfamily.colossal_reactors.integration.mekanism.MaterialSelector.matchesAnyFluidInput(
                    fluid, inputSelectors)) {
                matches = true;
            } else if (net.unfamily.colossal_reactors.coolant.CoolantLoader.get(e.fluidId()) != null
                    && net.unfamily.colossal_reactors.integration.mekanism.MaterialSelector.coolantInputsMatchSelectors(
                    net.unfamily.colossal_reactors.coolant.CoolantLoader.get(e.fluidId()).inputs(), inputSelectors)) {
                matches = true;
            }
            if (!matches) {
                i++;
                continue;
            }
            int got = consumeCoolantByKey(e.fluidId(), remaining);
            if (got <= 0) {
                i++;
                continue;
            }
            consumed += got;
            remaining -= got;
        }
        return consumed;
    }

    /** Adds waste units under {@code wasteId} (see {@link net.unfamily.colossal_reactors.fuel.FuelDefinition#wasteId()}). */
    public float addWasteUnits(ResourceLocation wasteId, float units) {
        if (units <= 0) return 0;
        float total = getTotalFuelAndWasteUnits();
        int max = getMaxFuelUnitsTotal();
        float add = Math.min(units, Math.max(0, max - total));
        if (add <= 0) return 0;
        for (int i = 0; i < wasteEntries.size(); i++) {
            WasteEntry e = wasteEntries.get(i);
            if (e.id().equals(wasteId)) {
                wasteEntries.set(i, new WasteEntry(wasteId, e.units() + add));
                setChanged();
                return add;
            }
        }
        wasteEntries.add(new WasteEntry(wasteId, add));
        setChanged();
        return add;
    }

    public float consumeWasteUnits(ResourceLocation wasteId, float units) {
        if (units <= 0) return 0;
        for (int i = 0; i < wasteEntries.size(); i++) {
            WasteEntry e = wasteEntries.get(i);
            if (!e.id().equals(wasteId)) continue;
            float take = Math.min(units, e.units());
            float remain = e.units() - take;
            if (remain <= 0.0001f) wasteEntries.remove(i);
            else wasteEntries.set(i, new WasteEntry(wasteId, remain));
            if (take > 0) setChanged();
            return take;
        }
        return 0;
    }

    /** Coolant definition from stored coolant fluids; null when buffer is empty (RF-only mode). */
    public CoolantDefinition getCoolantDefinition(net.minecraft.core.RegistryAccess registryAccess) {
        if (coolantEntries.isEmpty() || getTotalCoolantMb() <= 0) {
            return null;
        }
        CoolantDefinition waterDef =
                CoolantLoader.get(CoolantLoader.WATER_COOLANT_ID);
        for (CoolantEntry e : coolantEntries) {
            var def = CoolantLoader.get(e.fluidId());
            if (def != null && CoolantLoader.WATER_COOLANT_ID.equals(def.coolantId())) {
                return waterDef != null ? waterDef : def;
            }
            var fluid = BuiltInRegistries.FLUID.get(e.fluidId());
            if (fluid == null || fluid == Fluids.EMPTY) continue;
            def = CoolantLoader.getDefinitionForFluid(fluid, registryAccess);
            if (def != null && CoolantLoader.WATER_COOLANT_ID.equals(def.coolantId())) {
                return waterDef != null ? waterDef : def;
            }
        }
        for (CoolantEntry e : coolantEntries) {
            var def = CoolantLoader.get(e.fluidId());
            if (def != null) return def;
            var fluid = BuiltInRegistries.FLUID.get(e.fluidId());
            if (fluid == null || fluid == Fluids.EMPTY) continue;
            def = CoolantLoader.getDefinitionForFluid(fluid, registryAccess);
            if (def != null) return def;
        }
        return null;
    }

    public int getMaxFuelUnitsTotal() {
        int perRod = ReactorRodBlockEntity.getMaxFuelUnits();
        long rods = (cachedRodPositions != null && cachedRodPositions.length > 0)
                ? cachedRodPositions.length
                : (cachedResult != null && cachedResult.valid() ? cachedResult.rodCount() : 0);
        long total = rods * (long) Math.max(0, perRod);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, total));
    }

    public float addFuel(ResourceLocation fuelId, float units) {
        if (units <= 0) return 0;
        float total = getTotalFuelAndWasteUnits();
        int max = getMaxFuelUnitsTotal();
        float add = Math.min(units, Math.max(0, max - total));
        if (add <= 0) return 0;
        mergeFuel(fuelId, add);
        setChanged();
        updateRodVisualFillIfNeeded();
        return add;
    }

    public float consumeFuel(ResourceLocation fuelId, float units) {
        if (units <= 0) return 0;
        for (int i = 0; i < fuelEntries.size(); i++) {
            FuelEntry e = fuelEntries.get(i);
            if (!e.id().equals(fuelId)) continue;
            float take = Math.min(units, e.units());
            if (take <= 0) return 0;
            float remain = e.units() - take;
            if (remain <= 0.0001f) fuelEntries.remove(i);
            else fuelEntries.set(i, new FuelEntry(fuelId, remain));
            setChanged();
            updateRodVisualFillIfNeeded();
            return take;
        }
        return 0;
    }

    private void mergeFuel(ResourceLocation fuelId, float add) {
        for (int i = 0; i < fuelEntries.size(); i++) {
            FuelEntry e = fuelEntries.get(i);
            if (e.id().equals(fuelId)) {
                fuelEntries.set(i, new FuelEntry(fuelId, e.units() + add));
                return;
            }
        }
        fuelEntries.add(new FuelEntry(fuelId, add));
    }

    public void tickRodVisuals(ServerLevel level) {
        if (cachedRodPositions == null || cachedRodPositions.length == 0) return;
        int maxPerTick = 256;
        int n = Math.min(maxPerTick, cachedRodPositions.length);
        for (int i = 0; i < n; i++) {
            long lp = cachedRodPositions[(rodVisualUpdateCursor++) % cachedRodPositions.length];
            BlockPos pos = BlockPos.of(lp);
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModBlocks.REACTOR_ROD.get())) continue;
            if (!state.hasProperty(ReactorRodBlock.FILL)) continue;
            int cur = state.getValue(ReactorRodBlock.FILL);
            if (cur != cachedRodFillLevel) {
                level.setBlock(pos, state.setValue(ReactorRodBlock.FILL, cachedRodFillLevel), Block.UPDATE_CLIENTS);
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ReactorRodBlockEntity rod) {
                rod.applyControllerInsideTint(cachedRodInsideTintRgb);
            }
        }
    }

    private void updateRodVisualFillIfNeeded() {
        int max = getMaxFuelUnitsTotal();
        float pct = max <= 0 ? 0f : Math.max(0f, Math.min(1f, getTotalFuelAndWasteUnits() / (float) max));
        int fillLevel = ReactorRodBlockEntity.fillPercentToLevelForController(pct);
        int tint = resolveDominantFuelInsideTint();
        boolean changed = false;
        if (cachedRodFillLevel != fillLevel) {
            cachedRodFillLevel = fillLevel;
            changed = true;
        }
        if (cachedRodInsideTintRgb != tint) {
            cachedRodInsideTintRgb = tint;
            changed = true;
            pushInsideTintToAllRods();
        }
        if (changed) {
            rodVisualUpdateCursor = 0;
        }
    }

    private int resolveDominantFuelInsideTint() {
        ResourceLocation best = null;
        float bestUnits = 0f;
        for (FuelEntry e : fuelEntries) {
            if (e.units() > bestUnits) {
                bestUnits = e.units();
                best = e.id();
            }
        }
        return ReactorRodBlockEntity.resolveInsideTintRgb(best);
    }

    private void pushInsideTintToAllRods() {
        if (!(level instanceof ServerLevel sl) || cachedRodPositions == null || cachedRodPositions.length == 0) {
            return;
        }
        for (long lp : cachedRodPositions) {
            BlockPos pos = BlockPos.of(lp);
            BlockEntity be = sl.getBlockEntity(pos);
            if (be instanceof ReactorRodBlockEntity rod) {
                rod.applyControllerInsideTint(cachedRodInsideTintRgb);
            }
        }
    }

    private void clampToNewCapacities() {
        clampSharedFuelAndWasteToCapacity();
        clampCoolantToCapacity();
        updateRodVisualFillIfNeeded();
    }

    /** Clamp shared (fuel + waste) units to capacity; drops the most voluminous entry first. */
    private void clampSharedFuelAndWasteToCapacity() {
        int max = getMaxFuelUnitsTotal();
        float excess = getTotalFuelAndWasteUnits() - max;
        if (excess <= 0.0001f) return;
        while (excess > 0.0001f) {
            int fuelIdx = -1;
            float fuelMax = 0;
            for (int i = 0; i < fuelEntries.size(); i++) {
                float u = fuelEntries.get(i).units();
                if (u > fuelMax) { fuelMax = u; fuelIdx = i; }
            }
            int wasteIdx = -1;
            float wasteMax = 0;
            for (int i = 0; i < wasteEntries.size(); i++) {
                float u = wasteEntries.get(i).units();
                if (u > wasteMax) { wasteMax = u; wasteIdx = i; }
            }
            if (fuelIdx < 0 && wasteIdx < 0) break;
            boolean dropWaste = (wasteIdx >= 0) && (fuelIdx < 0 || wasteMax >= fuelMax);
            if (dropWaste) {
                WasteEntry e = wasteEntries.get(wasteIdx);
                float take = Math.min(e.units(), excess);
                float left = e.units() - take;
                excess -= take;
                if (left <= 0.0001f) wasteEntries.remove(wasteIdx);
                else wasteEntries.set(wasteIdx, new WasteEntry(e.id(), left));
            } else {
                FuelEntry e = fuelEntries.get(fuelIdx);
                float take = Math.min(e.units(), excess);
                float left = e.units() - take;
                excess -= take;
                if (left <= 0.0001f) fuelEntries.remove(fuelIdx);
                else fuelEntries.set(fuelIdx, new FuelEntry(e.id(), left));
            }
        }
        setChanged();
    }

    private void clampCoolantToCapacity() {
        int max = getCoolantCapacityMbTotal();
        int total = getTotalCoolantMb();
        int excess = total - max;
        if (excess <= 0) return;
        for (int i = coolantEntries.size() - 1; i >= 0 && excess > 0; i--) {
            CoolantEntry e = coolantEntries.get(i);
            int take = Math.min(e.mb(), excess);
            int left = e.mb() - take;
            excess -= take;
            if (left <= 0) coolantEntries.remove(i);
            else coolantEntries.set(i, new CoolantEntry(e.fluidId(), left));
        }
        setChanged();
    }

    // waste clamp handled by clampSharedFuelAndWasteToCapacity()

    /**
     * Per-tick callers: rebuild only if the caches were not built for this validation result yet.
     * A reactor without rods or resource ports keeps empty arrays, so rebuilding on "empty" rescanned every tick.
     * New parts are picked up by the next re-validation.
     */
    public void rebuildPartCachesIfNeeded(ServerLevel level, ReactorValidation.Result result) {
        if (result == null || result != partCachesResult) {
            rebuildPartCaches(level, result);
        }
    }

    public void rebuildPartCaches(ServerLevel level, ReactorValidation.Result result) {
        partCachesResult = level != null ? result : null;
        if (level == null || result == null || !result.valid()) {
            cachedRodPositions = new long[0];
            cachedPowerPortPositions = new long[0];
            cachedResourcePortPositions = new long[0];
            cachedRedstonePortPositions = new long[0];
            cachedEffectiveRodCount = 0.0;
            cachedHeatSinkStaticCache = null;
            cachedRodFillLevel = 0;
            cachedRodInsideTintRgb = ReactorRodBlockEntity.DEFAULT_INSIDE_TINT_RGB;
            rodVisualUpdateCursor = 0;
            return;
        }

        int minX = result.minX();
        int minY = result.minY();
        int minZ = result.minZ();
        int maxX = result.maxX();
        int maxY = result.maxY();
        int maxZ = result.maxZ();

        LongArrayList rods = new LongArrayList();
        LongArrayList powerPorts = new LongArrayList();
        LongArrayList resourcePorts = new LongArrayList();
        LongArrayList redstonePorts = new LongArrayList();

        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    p.set(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (state.is(ModBlocks.REACTOR_ROD.get())) {
                        rods.add(p.asLong());
                    } else if (ModBlocks.isPowerPort(state)) {
                        powerPorts.add(p.asLong());
                    } else if (state.is(ModBlocks.RESOURCE_PORT.get())) {
                        resourcePorts.add(p.asLong());
                    } else if (state.is(ModBlocks.REDSTONE_PORT.get())) {
                        redstonePorts.add(p.asLong());
                    }
                }
            }
        }

        cachedRodPositions = rods.toLongArray();
        cachedPowerPortPositions = powerPorts.toLongArray();
        cachedResourcePortPositions = resourcePorts.toLongArray();
        cachedRedstonePortPositions = redstonePorts.toLongArray();

        rebuildSimulationCaches(level, result, cachedRodPositions);
        updateRodVisualFillIfNeeded();
        // Rods are newly cached: push current tint even if RGB unchanged since last fuel edit.
        pushInsideTintToAllRods();
        net.unfamily.colossal_reactors.multiblock.MultiblockPortScaling.scaleReactorPorts(level, this);
    }

    private void rebuildSimulationCaches(ServerLevel level, ReactorValidation.Result result, long[] rodPositions) {
        LongOpenHashSet rodSet = new LongOpenHashSet(Math.max(16, rodPositions.length * 2));
        for (long p : rodPositions) rodSet.add(p);

        double penalty = Config.ROD_ADJACENCY_PENALTY.get();
        double effective = 0.0;
        for (long p : rodPositions) {
            BlockPos pos = BlockPos.of(p);
            int adjacentCount = 0;
            for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = pos.relative(dir);
                long nl = neighbor.asLong();
                if (rodSet.contains(nl) || ReactorValidation.isShellBlock(level.getBlockState(neighbor))) {
                    adjacentCount++;
                }
            }
            effective += Math.max(0.0, 1.0 - penalty * adjacentCount);
        }
        cachedEffectiveRodCount = effective;

        int minX = result.minX(), minY = result.minY(), minZ = result.minZ();
        int maxX = result.maxX(), maxY = result.maxY(), maxZ = result.maxZ();
        var reg = level.registryAccess();
        int countRod = 0, countAdj = 0, countNon = 0;
        double sumFuelAdj = 0, sumEnergyAdj = 0, sumOverheatingAdj = 0;
        double sumFuelNon = 0, sumEnergyNon = 0, sumOverheatingNon = 0;

        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = minY + 1; y < maxY; y++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    mp.set(x, y, z);
                    BlockState state = level.getBlockState(mp);
                    if (state.is(ModBlocks.REACTOR_ROD.get())) {
                        countRod++;
                        continue;
                    }
                    HeatSinkLoader.HeatSinkModifiers m = HeatSinkLoader.getModifiersForBlockOrDefault(state, reg);
                    boolean adjacentToRod = false;
                    for (var d : net.minecraft.core.Direction.values()) {
                        int nx = x + d.getStepX();
                        int ny = y + d.getStepY();
                        int nz = z + d.getStepZ();
                        if (rodSet.contains(BlockPos.asLong(nx, ny, nz))) {
                            adjacentToRod = true;
                            break;
                        }
                    }
                    if (adjacentToRod) {
                        sumFuelAdj += m.fuelMultiplier();
                        sumEnergyAdj += m.energyMultiplier();
                        sumOverheatingAdj += m.overheatingMultiplier();
                        countAdj++;
                    } else {
                        sumFuelNon += m.fuelMultiplier();
                        sumEnergyNon += m.energyMultiplier();
                        sumOverheatingNon += m.overheatingMultiplier();
                        countNon++;
                    }
                }
            }
        }

        cachedHeatSinkStaticCache = new HeatSinkStaticCache(
                countRod, countAdj, countNon,
                sumFuelAdj, sumEnergyAdj, sumOverheatingAdj,
                sumFuelNon, sumEnergyNon, sumOverheatingNon
        );

        // Reactor shape may have changed (e.g. resized): keep contents but clamp to new capacities.
        clampToNewCapacities();
    }

    public void invalidateCache() {
        cachedResult = null;
        cachedRodPositions = new long[0];
        cachedPowerPortPositions = new long[0];
        cachedResourcePortPositions = new long[0];
        cachedRedstonePortPositions = new long[0];
        cachedEffectiveRodCount = 0.0;
        cachedHeatSinkStaticCache = null;
        cachedRodFillLevel = 0;
        cachedRodInsideTintRgb = ReactorRodBlockEntity.DEFAULT_INSIDE_TINT_RGB;
        rodVisualUpdateCursor = 0;
        if (level != null && !level.isClientSide()) {
            setChanged();
        }
    }

    /** Called by ReactorSimulation.tick at end of each tick. Fuel hundredths = fuel units/tick * 100 (e.g. 0.26 -> 26). */
    public void setLastTickStats(long rfPerTick, int steamPerTick, int waterPerTick, int fuelPerTickHundredths) {
        this.lastRfPerTick = rfPerTick;
        this.lastSteamPerTick = steamPerTick;
        this.lastWaterPerTick = waterPerTick;
        this.lastFuelPerTickHundredths = fuelPerTickHundredths;
    }

    public long getLastRfPerTick() { return lastRfPerTick; }
    public int getLastSteamPerTick() { return lastSteamPerTick; }
    public int getLastWaterPerTick() { return lastWaterPerTick; }
    public int getLastFuelPerTickHundredths() { return lastFuelPerTickHundredths; }

    /** Stability in permille 0–1000 (display as 0.0%–100.0%). */
    public int getStabilityPermille() { return stabilityPermille; }
    public void setStabilityPermille(int permille) {
        this.stabilityPermille = Math.max(0, Math.min(1000, permille));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.colossal_reactors.reactor_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ReactorControllerMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (cachedResult != null && cachedResult.valid()) {
            tag.putInt("val_minX", cachedResult.minX());
            tag.putInt("val_minY", cachedResult.minY());
            tag.putInt("val_minZ", cachedResult.minZ());
            tag.putInt("val_maxX", cachedResult.maxX());
            tag.putInt("val_maxY", cachedResult.maxY());
            tag.putInt("val_maxZ", cachedResult.maxZ());
            tag.putInt("val_rodCount", cachedResult.rodCount());
            tag.putInt("val_rodColumns", cachedResult.rodColumns());
            tag.putInt("val_coolantCount", cachedResult.coolantCount());
        }
        tag.putInt("stability", stabilityPermille);

        ListTag fuelList = new ListTag();
        for (FuelEntry e : fuelEntries) {
            CompoundTag c = new CompoundTag();
            c.putString(TAG_ID, e.id().toString());
            c.putFloat(TAG_UNITS, e.units());
            fuelList.add(c);
        }
        tag.put(TAG_FUEL_AGG, fuelList);

        ListTag wasteList = new ListTag();
        for (WasteEntry e : wasteEntries) {
            if (e.units() <= 0) continue;
            CompoundTag c = new CompoundTag();
            c.putString(TAG_ID, e.id().toString());
            c.putFloat(TAG_WASTE_UNITS, e.units());
            wasteList.add(c);
        }
        tag.put(TAG_WASTE_AGG, wasteList);

        ListTag coolantList = new ListTag();
        for (CoolantEntry e : coolantEntries) {
            if (e.mb() <= 0) continue;
            CompoundTag c = new CompoundTag();
            c.putString(TAG_ID, e.fluidId().toString());
            c.putInt(TAG_MB, e.mb());
            coolantList.add(c);
        }
        tag.put(TAG_COOLANT_AGG, coolantList);
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("val_minX")) {
            int minX = tag.getInt("val_minX");
            int minY = tag.getInt("val_minY");
            int minZ = tag.getInt("val_minZ");
            int maxX = tag.getInt("val_maxX");
            int maxY = tag.getInt("val_maxY");
            int maxZ = tag.getInt("val_maxZ");
            int rodColumns = tag.getInt("val_rodColumns");
            ReactorValidation.ValidationReport report = new ReactorValidation.ValidationReport(
                    minX, minY, minZ, maxX, maxY, maxZ,
                    maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1,
                    0, rodColumns, 0, new long[0]);
            cachedResult = new ReactorValidation.Result(
                    true, null, null, report,
                    minX, minY, minZ, maxX, maxY, maxZ,
                    tag.getInt("val_rodCount"), rodColumns, tag.getInt("val_coolantCount"));
        } else {
            cachedResult = null;
        }
        if (tag.contains("stability")) {
            int v = tag.getInt("stability");
            // Backward compat: old saves used 0-100 percent
            stabilityPermille = (v <= 100) ? Math.min(1000, v * 10) : Math.max(0, Math.min(1000, v));
        }

        fuelEntries.clear();
        if (tag.contains(TAG_FUEL_AGG, Tag.TAG_LIST)) {
            ListTag fuelList = tag.getList(TAG_FUEL_AGG, Tag.TAG_COMPOUND);
            for (int i = 0; i < fuelList.size(); i++) {
                CompoundTag c = fuelList.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(c.getString(TAG_ID));
                float u = c.getFloat(TAG_UNITS);
                if (id != null && u > 0) fuelEntries.add(new FuelEntry(id, u));
            }
        }
        wasteEntries.clear();
        if (tag.contains(TAG_WASTE_AGG, Tag.TAG_LIST)) {
            ListTag wasteList = tag.getList(TAG_WASTE_AGG, Tag.TAG_COMPOUND);
            for (int i = 0; i < wasteList.size(); i++) {
                CompoundTag c = wasteList.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(c.getString(TAG_ID));
                float u = c.getFloat(TAG_WASTE_UNITS);
                if (id != null && u > 0) wasteEntries.add(new WasteEntry(id, u));
            }
        }
        coolantEntries.clear();
        if (tag.contains(TAG_COOLANT_AGG, Tag.TAG_LIST)) {
            ListTag coolantList = tag.getList(TAG_COOLANT_AGG, Tag.TAG_COMPOUND);
            for (int i = 0; i < coolantList.size(); i++) {
                CompoundTag c = coolantList.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(c.getString(TAG_ID));
                int mb = c.getInt(TAG_MB);
                if (id != null && mb > 0) coolantEntries.add(new CoolantEntry(id, mb));
            }
        }
        clampToNewCapacities();
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
