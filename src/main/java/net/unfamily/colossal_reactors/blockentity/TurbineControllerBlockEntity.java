package net.unfamily.colossal_reactors.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.block.TurbineRodBlock;
import net.unfamily.colossal_reactors.client.turbine.TurbineRotorClientRegistry;
import net.unfamily.colossal_reactors.client.turbine.TurbineRotorSimulationSource;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.unfamily.colossal_reactors.block.TurbineControllerBlock;
import net.unfamily.colossal_reactors.block.TurbineVisualState;
import net.unfamily.colossal_reactors.multiblock.PortScalingConstants;
import net.unfamily.colossal_reactors.menu.TurbineControllerMenu;
import net.unfamily.colossal_reactors.turbine.TurbineFiller;
import net.unfamily.colossal_reactors.turbine.TurbineGenerationLoader;
import net.unfamily.colossal_reactors.turbine.TurbineSimulation;
import net.unfamily.colossal_reactors.turbine.TurbineValidation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Turbine controller: caches validation and runtime stats for GUI.
 */
public class TurbineControllerBlockEntity extends BlockEntity implements MenuProvider {

    private static final String TAG_STEAM_BUFFER = "SteamBuffer";
    private static final String TAG_OUTPUT_BUFFER = "OutputBuffer";
    private static final String TAG_FLUID_ID = "FluidId";
    private static final String TAG_MB = "Mb";
    /** Lightweight client sync (Mekanism-style rotation packet), not a full chunk save. */
    private static final String TAG_RUNTIME_ONLY = "RuntimeOnly";
    /** Rod/port geometry sync when {@link #rebuildPartCaches} changes structure. */
    private static final String TAG_STRUCTURE_ONLY = "StructureOnly";
    private static final String TAG_STRUCTURE_REVISION = "StructureRevision";

    /** When true, {@link #getUpdateTag} sends only runtime/rotation fields. */
    private transient boolean syncRuntimeOnly;
    /** When true, next {@link #getUpdateTag} sends rod positions + revision. */
    private transient boolean syncStructureOnly;

    /** Incremented on each {@link #rebuildPartCaches}; client invalidates rotor geometry when it changes. */
    private int structureRevision;

    public record FluidBufferEntry(ResourceLocation fluidId, int mb) {}

    private TurbineValidation.Result cachedResult = TurbineValidation.Result.invalid();
    /** Validation result the part caches were last built for (see {@link #rebuildPartCachesIfNeeded}). */
    @Nullable
    private TurbineValidation.Result partCachesResult;
    private long[] cachedPowerPortPositions = new long[0];
    private long[] cachedResourcePortPositions = new long[0];
    private long[] cachedRedstonePortPositions = new long[0];
    private long[] cachedRodPositions = new long[0];
    private byte[] cachedRodFacings = new byte[0];
    private boolean powered;
    private boolean redstoneGateOpen;
    private long lastRfPerTick;
    private double lastSteamPerTick;
    /** 0..1 production factor synced to client for rotor speed (RF or steam vs estimated max). */
    private float rotorLoadFactor;
    private double lastCoilEff;
    private double lastBladeEff;
    @Nullable
    private Player lastInteractingPlayer;

    /** Steam waiting to be consumed (one tick of demand). */
    private final List<FluidBufferEntry> steamInputEntries = new ArrayList<>();
    /** Condensate / output fluid from datapack {@code output} (one tick, returned via EJECT). */
    private final List<FluidBufferEntry> outputReturnEntries = new ArrayList<>();
    private int cachedSteamConsumeMbPerTick = 1;
    private boolean cachedOutputReturnBuffer;

    public TurbineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURBINE_CONTROLLER_BE.get(), pos, state);
    }

    public TurbineValidation.Result getCachedResult() {
        return cachedResult != null ? cachedResult : TurbineValidation.Result.invalid();
    }

    public boolean isPowered() { return powered; }
    public boolean isRedstoneGateOpen() { return redstoneGateOpen; }

    public long[] getCachedRodPositions() { return cachedRodPositions; }
    public byte[] getCachedRodFacings() { return cachedRodFacings; }
    public int getStructureRevision() { return structureRevision; }
    public long getLastRfPerTick() { return lastRfPerTick; }
    public double getLastSteamPerTick() { return lastSteamPerTick; }
    public float getRotorLoadFactor() { return rotorLoadFactor; }

    /** Copies synced runtime fields from another controller (e.g. server BE in integrated SP). Does not mark dirty. */
    public void mirrorRuntimeStatsFrom(TurbineControllerBlockEntity other) {
        if (other == this) {
            return;
        }
        lastRfPerTick = other.lastRfPerTick;
        lastSteamPerTick = other.lastSteamPerTick;
        powered = other.powered;
        redstoneGateOpen = other.redstoneGateOpen;
        rotorLoadFactor = other.rotorLoadFactor;
        cachedOutputReturnBuffer = other.cachedOutputReturnBuffer;
    }

    /**
     * Copies structure fields used for client rotor geometry (integrated SP server authority).
     * Does not mark dirty or rebuild caches on the client.
     */
    public void mirrorRotorStructureFrom(TurbineControllerBlockEntity other) {
        if (other == this) {
            return;
        }
        cachedResult = other.cachedResult;
        cachedRodPositions = other.cachedRodPositions.clone();
        cachedRodFacings = other.cachedRodFacings.clone();
        structureRevision = other.structureRevision;
    }
    public double getLastCoilEff() { return lastCoilEff; }
    public double getLastBladeEff() { return lastBladeEff; }

    public void setLastInteractingPlayer(@Nullable Player player) {
        this.lastInteractingPlayer = player;
    }

    public void setCachedResult(TurbineValidation.Result result) {
        this.cachedResult = result != null ? result : TurbineValidation.Result.invalid();
        if (level != null) {
            rebuildPartCaches(level, cachedResult);
        } else if (!this.cachedResult.valid()) {
            cachedRodPositions = new long[0];
            cachedRodFacings = new byte[0];
            cachedPowerPortPositions = new long[0];
            cachedResourcePortPositions = new long[0];
            cachedRedstonePortPositions = new long[0];
        }
        setChanged();
    }

    /** Client-only validation cache fill without sending a server update. */
    public void applyClientValidationResult(Level level, TurbineValidation.Result result) {
        this.cachedResult = result != null ? result : TurbineValidation.Result.invalid();
        if (!this.cachedResult.valid()) {
            cachedRodPositions = new long[0];
            cachedRodFacings = new byte[0];
            return;
        }
        rebuildPartCaches(level, result);
    }

    /**
     * Client-only validation update without bumping {@link #structureRevision}
     * (avoids spurious geometry resets on other turbines).
     */
    public void applyClientValidationResultQuiet(Level level, TurbineValidation.Result result) {
        this.cachedResult = result != null ? result : TurbineValidation.Result.invalid();
        if (!this.cachedResult.valid()) {
            cachedRodPositions = new long[0];
            cachedRodFacings = new byte[0];
            return;
        }
        rebuildPartCachesQuiet(level, result);
    }

    public int getCachedSteamConsumeMbPerTick() {
        return Math.max(1, cachedSteamConsumeMbPerTick);
    }

    /**
     * Aggregated steam input buffer in the controller (like reactor coolant): valid blades × config mB per blade.
     */
    public int getSteamInputCapacityMb() {
        TurbineValidation.Result result = getCachedResult();
        if (!result.valid() || result.validBladeCount() <= 0) {
            return 0;
        }
        long cap = (long) result.validBladeCount() * Config.TURBINE_STEAM_INPUT_MB_PER_BLADE.get();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, cap));
    }

    /**
     * Condensate (liquid water) buffer when generation defines a liquid output.
     * Sized to several ticks of steam demand so EXTRACT lag does not hard-stop production.
     */
    public int getOutputReturnCapacityMb() {
        if (!cachedOutputReturnBuffer) {
            return 0;
        }
        int oneTick = getCachedSteamConsumeMbPerTick();
        if (oneTick <= 0) {
            return 0;
        }
        long scaled = (long) oneTick * PortScalingConstants.DEMAND_MULTIPLIER;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(oneTick, scaled));
    }

    /** Free condensate space in mB; {@link Integer#MAX_VALUE} when no condensate buffer is required. */
    public int getOutputReturnFreeMb() {
        int cap = getOutputReturnCapacityMb();
        if (cap <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, cap - getTotalOutputReturnMb());
    }

    public int getTotalFluidBufferCapacityMb() {
        return getSteamInputCapacityMb() + getOutputReturnCapacityMb();
    }

    public int getTotalSteamInputMb() {
        return totalMbInList(steamInputEntries);
    }

    public int getTotalOutputReturnMb() {
        return totalMbInList(outputReturnEntries);
    }

    /** True when the condensate buffer (e.g. water) cannot accept more fluid this tick. */
    public boolean isOutputReturnBufferFull() {
        int cap = getOutputReturnCapacityMb();
        return cap > 0 && getTotalOutputReturnMb() >= cap;
    }

    public int addSteamInput(Fluid fluid, int amountMb) {
        return addFluidToList(steamInputEntries, getSteamInputCapacityMb(), fluid, amountMb);
    }

    public int consumeSteamInput(Fluid fluid, int amountMb) {
        return consumeFluidFromList(steamInputEntries, fluid, amountMb);
    }

    /**
     * Consumes steam from the input buffer for any fluid matching {@code inputSelectors}
     * (fluid ids and tags from turbine generation JSON).
     */
    public int consumeSteamInputMatching(java.util.List<String> inputSelectors, int amountMb) {
        if (amountMb <= 0 || inputSelectors == null || inputSelectors.isEmpty()) {
            return 0;
        }
        int remaining = amountMb;
        int consumed = 0;
        int i = 0;
        while (remaining > 0 && i < steamInputEntries.size()) {
            FluidBufferEntry e = steamInputEntries.get(i);
            Fluid fluid = BuiltInRegistries.FLUID.get(e.fluidId());
            if (fluid == null || fluid == Fluids.EMPTY
                    || !net.unfamily.colossal_reactors.integration.mekanism.MaterialSelector.matchesAnyFluidInput(
                    fluid, inputSelectors)) {
                i++;
                continue;
            }
            int got = consumeFluidFromList(steamInputEntries, fluid, remaining);
            if (got <= 0) {
                i++;
                continue;
            }
            consumed += got;
            remaining -= got;
        }
        return consumed;
    }

    public java.util.List<FluidBufferEntry> getSteamInputEntriesCopy() {
        return java.util.List.copyOf(steamInputEntries);
    }

    public int addOutputReturn(Fluid fluid, int amountMb) {
        if (!cachedOutputReturnBuffer) return 0;
        return addFluidToList(outputReturnEntries, getOutputReturnCapacityMb(), fluid, amountMb);
    }

    public int consumeOutputReturn(Fluid fluid, int amountMb) {
        return consumeFluidFromList(outputReturnEntries, fluid, amountMb);
    }

    public List<FluidBufferEntry> getOutputReturnEntries() {
        return List.copyOf(outputReturnEntries);
    }

    /** Sizes steam/output buffers from validated steam cap and active generation {@code output}. */
    public void updateFluidBufferCapacities(ServerLevel level, TurbineValidation.Result result) {
        if (result == null || !result.valid()) {
            cachedSteamConsumeMbPerTick = 1;
            cachedOutputReturnBuffer = false;
            return;
        }
        cachedSteamConsumeMbPerTick = Math.max(1, (int) Math.ceil(result.maxSteamMbPerTick()));
        var gen = TurbineGenerationLoader.getDefault();
        Fluid outputFluid = TurbineGenerationLoader.getOutputFluid(gen, level.registryAccess());
        cachedOutputReturnBuffer = outputFluid != null && outputFluid != Fluids.EMPTY;
        clampFluidBuffers();
    }

    private void clampFluidBuffers() {
        clampFluidListToCapacity(steamInputEntries, getSteamInputCapacityMb());
        clampFluidListToCapacity(outputReturnEntries, getOutputReturnCapacityMb());
    }

    private static int totalMbInList(List<FluidBufferEntry> list) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, list.stream().mapToLong(FluidBufferEntry::mb).sum()));
    }

    private int addFluidToList(List<FluidBufferEntry> list, int maxTotalMb, Fluid fluid, int amountMb) {
        if (amountMb <= 0 || fluid == null || fluid == Fluids.EMPTY || maxTotalMb <= 0) return 0;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) return 0;
        int total = totalMbInList(list);
        int add = Math.min(amountMb, Math.max(0, maxTotalMb - total));
        if (add <= 0) return 0;
        for (int i = 0; i < list.size(); i++) {
            FluidBufferEntry e = list.get(i);
            if (id.equals(e.fluidId())) {
                list.set(i, new FluidBufferEntry(id, e.mb() + add));
                setChanged();
                return add;
            }
        }
        list.add(new FluidBufferEntry(id, add));
        setChanged();
        return add;
    }

    private int consumeFluidFromList(List<FluidBufferEntry> list, Fluid fluid, int amountMb) {
        if (amountMb <= 0 || fluid == null || fluid == Fluids.EMPTY) return 0;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) return 0;
        int remaining = amountMb;
        int consumed = 0;
        for (int i = 0; i < list.size() && remaining > 0; i++) {
            FluidBufferEntry e = list.get(i);
            if (!id.equals(e.fluidId())) continue;
            int take = Math.min(remaining, Math.max(0, e.mb()));
            if (take <= 0) continue;
            int left = e.mb() - take;
            consumed += take;
            remaining -= take;
            if (left <= 0) {
                list.remove(i);
                i--;
            } else {
                list.set(i, new FluidBufferEntry(id, left));
            }
        }
        if (consumed > 0) setChanged();
        return consumed;
    }

    private void clampFluidListToCapacity(List<FluidBufferEntry> list, int maxTotalMb) {
        int excess = totalMbInList(list) - maxTotalMb;
        if (excess <= 0) return;
        for (int i = list.size() - 1; i >= 0 && excess > 0; i--) {
            FluidBufferEntry e = list.get(i);
            int take = Math.min(e.mb(), excess);
            int left = e.mb() - take;
            excess -= take;
            if (left <= 0) list.remove(i);
            else list.set(i, new FluidBufferEntry(e.fluidId(), left));
        }
        setChanged();
    }

    private static void saveFluidList(CompoundTag tag, String key, List<FluidBufferEntry> list) {
        ListTag fluidList = new ListTag();
        for (FluidBufferEntry e : list) {
            if (e.mb() <= 0) continue;
            CompoundTag c = new CompoundTag();
            c.putString(TAG_FLUID_ID, e.fluidId().toString());
            c.putInt(TAG_MB, e.mb());
            fluidList.add(c);
        }
        if (!fluidList.isEmpty()) {
            tag.put(key, fluidList);
        }
    }

    private static void loadFluidList(CompoundTag tag, String key, List<FluidBufferEntry> list) {
        list.clear();
        if (!tag.contains(key, Tag.TAG_LIST)) return;
        ListTag fluidList = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < fluidList.size(); i++) {
            CompoundTag c = fluidList.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(c.getString(TAG_FLUID_ID));
            int mb = c.getInt(TAG_MB);
            if (id != null && mb > 0) list.add(new FluidBufferEntry(id, mb));
        }
    }

    /** Called from block tick when VALIDATING -> ON/OFF; shows message in action bar. */
    public void notifyValidationResult() {
        if (lastInteractingPlayer instanceof ServerPlayer sp) {
            TurbineValidation.Result result = getCachedResult();
            sp.displayClientMessage(TurbineValidation.failureMessage(result), true);
            if (level != null) {
                TurbineValidation.sendFailureMarkers(sp, level, result);
            }
        }
        lastInteractingPlayer = null;
    }

    public long[] getCachedPowerPortPositions() {
        return cachedPowerPortPositions;
    }

    public long[] getCachedResourcePortPositions() {
        return cachedResourcePortPositions;
    }

    public long[] getCachedRedstonePortPositions() {
        return cachedRedstonePortPositions;
    }

    public void setRuntimeStats(long rfPerTick, double steamPerTick, boolean powered, boolean redstoneGateOpen) {
        float newLoad = computeRotorLoadFactor(rfPerTick, steamPerTick);
        boolean statsChanged = lastRfPerTick != rfPerTick
                || lastSteamPerTick != steamPerTick
                || this.powered != powered
                || this.redstoneGateOpen != redstoneGateOpen
                || rotorLoadFactor != newLoad;
        this.lastRfPerTick = rfPerTick;
        this.lastSteamPerTick = steamPerTick;
        this.powered = powered;
        this.redstoneGateOpen = redstoneGateOpen;
        this.rotorLoadFactor = newLoad;

        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        boolean turbineOn = state.is(ModBlocks.TURBINE_CONTROLLER.get())
                && state.getValue(TurbineControllerBlock.VISUAL) == TurbineVisualState.ON;
        if (!statsChanged && !turbineOn) {
            return;
        }
        syncRuntimeOnly = turbineOn;
        setChanged();
        if (turbineOn) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        syncRuntimeOnly = false;
    }

    private CompoundTag buildStructureSyncTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_STRUCTURE_ONLY, true);
        tag.putInt(TAG_STRUCTURE_REVISION, structureRevision);
        if (cachedResult.valid()) {
            tag.putBoolean("val_valid", true);
            tag.putInt("val_minX", cachedResult.minX());
            tag.putInt("val_minY", cachedResult.minY());
            tag.putInt("val_minZ", cachedResult.minZ());
            tag.putInt("val_maxX", cachedResult.maxX());
            tag.putInt("val_maxY", cachedResult.maxY());
            tag.putInt("val_maxZ", cachedResult.maxZ());
        } else {
            tag.putBoolean("val_valid", false);
        }
        if (cachedRodPositions.length > 0) {
            tag.putLongArray("RodPos", cachedRodPositions);
            tag.putByteArray("RodFacing", cachedRodFacings);
        }
        return tag;
    }

    private void applyStructureSyncTag(CompoundTag tag) {
        if (tag.contains(TAG_STRUCTURE_REVISION)) {
            structureRevision = tag.getInt(TAG_STRUCTURE_REVISION);
        }
        if (tag.contains("val_valid") && tag.getBoolean("val_valid")) {
            cachedResult = new TurbineValidation.Result(
                    true,
                    null,
                    null,
                    TurbineValidation.ValidationReport.empty(),
                    tag.getInt("val_minX"),
                    tag.getInt("val_minY"),
                    tag.getInt("val_minZ"),
                    tag.getInt("val_maxX"),
                    tag.getInt("val_maxY"),
                    tag.getInt("val_maxZ"),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0);
        } else if (tag.contains("val_valid")) {
            cachedResult = TurbineValidation.Result.invalid();
        }
        if (tag.contains("RodPos")) {
            cachedRodPositions = tag.getLongArray("RodPos");
            cachedRodFacings = tag.contains("RodFacing") ? tag.getByteArray("RodFacing") : new byte[0];
        } else {
            cachedRodPositions = new long[0];
            cachedRodFacings = new byte[0];
        }
    }

    private CompoundTag buildRuntimeSyncTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_RUNTIME_ONLY, true);
        tag.putInt(TAG_STRUCTURE_REVISION, structureRevision);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("RedstoneGateOpen", redstoneGateOpen);
        tag.putFloat("RotorLoad", rotorLoadFactor);
        tag.putLong("LastRf", lastRfPerTick);
        tag.putDouble("LastSteam", lastSteamPerTick);
        tag.putBoolean("OutputReturnBuf", cachedOutputReturnBuffer);
        tag.putInt("OutRetMb", getTotalOutputReturnMb());
        return tag;
    }

    private void applyRuntimeSyncTag(CompoundTag tag) {
        if (tag.contains(TAG_STRUCTURE_REVISION)) {
            structureRevision = tag.getInt(TAG_STRUCTURE_REVISION);
        }
        if (tag.contains("Powered")) {
            powered = tag.getBoolean("Powered");
        }
        if (tag.contains("RedstoneGateOpen")) {
            redstoneGateOpen = tag.getBoolean("RedstoneGateOpen");
        }
        if (tag.contains("RotorLoad")) {
            rotorLoadFactor = tag.getFloat("RotorLoad");
        }
        if (tag.contains("LastRf")) {
            lastRfPerTick = tag.getLong("LastRf");
        }
        if (tag.contains("LastSteam")) {
            lastSteamPerTick = tag.getDouble("LastSteam");
        }
        if (tag.contains("OutputReturnBuf")) {
            cachedOutputReturnBuffer = tag.getBoolean("OutputReturnBuf");
        }
    }

    /**
     * Mekanism-style flow factor 0..1: actual RF output this tick vs estimated max RF (from steam/input capacity).
     */
    private float computeRotorLoadFactor(long rfPerTick, double steamPerTick) {
        TurbineValidation.Result result = getCachedResult();
        if (result == null || !result.valid()) {
            return 0f;
        }
        double maxRf = result.estimatedRfPerTick();
        if (maxRf > 0) {
            return (float) Math.min(1.0, rfPerTick / maxRf);
        }
        return 0f;
    }

    /** True when the turbine pushed RF this tick (not merely consumed steam). */
    public boolean isProducingEnergy() {
        return lastRfPerTick > 0;
    }

    /** Recomputes gate + runtime and syncs clients (call when a turbine redstone port signal changes). */
    public void refreshGateAndRuntime(ServerLevel level) {
        if (!cachedResult.valid()) {
            setRuntimeStats(0, 0, false, false);
            return;
        }
        updateFluidBufferCapacities(level, cachedResult);
        TurbineFiller.tickFill(level, this);
        boolean gateOpen = TurbineControllerBlock.isRedstoneGateSatisfied(level, this, cachedResult);
        if (!gateOpen) {
            setRuntimeStats(0, 0, false, gateOpen);
            return;
        }
        TurbineSimulation.tick(level, this);
    }

    /**
     * Per-tick callers: rebuild only if the caches were not built for this validation result yet.
     * A turbine without rods or ports keeps empty arrays, so rebuilding on "empty" rescanned (and re-synced) every tick.
     * New parts are picked up by the next re-validation.
     */
    public void rebuildPartCachesIfNeeded(ServerLevel level, TurbineValidation.Result result) {
        if (result == null || result != partCachesResult) {
            rebuildPartCaches(level, result);
        }
    }

    public void rebuildPartCaches(ServerLevel level, TurbineValidation.Result result) {
        rebuildPartCaches((Level) level, result);
    }

    public void rebuildPartCaches(Level level, TurbineValidation.Result result) {
        partCachesResult = level != null ? result : null;
        if (level == null || result == null || !result.valid()) {
            cachedPowerPortPositions = new long[0];
            cachedResourcePortPositions = new long[0];
            cachedRedstonePortPositions = new long[0];
            cachedRodPositions = new long[0];
            cachedRodFacings = new byte[0];
            return;
        }
        int minX = result.minX();
        int minY = result.minY();
        int minZ = result.minZ();
        int maxX = result.maxX();
        int maxY = result.maxY();
        int maxZ = result.maxZ();

        LongArrayList powerPorts = new LongArrayList();
        LongArrayList resourcePorts = new LongArrayList();
        LongArrayList redstonePorts = new LongArrayList();
        LongArrayList rods = new LongArrayList();
        it.unimi.dsi.fastutil.bytes.ByteArrayList rodFacings = new it.unimi.dsi.fastutil.bytes.ByteArrayList();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    p.set(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (TurbineValidation.isTurbinePowerPort(state)) {
                        powerPorts.add(p.asLong());
                    } else if (state.is(ModBlocks.TURBINE_RESOURCE_PORT.get())) {
                        resourcePorts.add(p.asLong());
                    } else if (state.is(ModBlocks.TURBINE_REDSTONE_PORT.get())) {
                        redstonePorts.add(p.asLong());
                    } else if (state.is(ModBlocks.TURBINE_ROD.get())) {
                        rods.add(p.asLong());
                        rodFacings.add((byte) state.getValue(TurbineRodBlock.FACING).ordinal());
                    }
                }
            }
        }
        cachedPowerPortPositions = powerPorts.toLongArray();
        cachedResourcePortPositions = resourcePorts.toLongArray();
        cachedRedstonePortPositions = redstonePorts.toLongArray();
        cachedRodPositions = rods.toLongArray();
        cachedRodFacings = rodFacings.toByteArray();
        structureRevision++;
        if (level != null && !level.isClientSide()) {
            syncStructureOnly = true;
        }
        if (level instanceof ServerLevel serverLevel) {
            updateFluidBufferCapacities(serverLevel, result);
            net.unfamily.colossal_reactors.multiblock.MultiblockPortScaling.scaleTurbinePorts(serverLevel, this);
        }
    }

    /** Rebuilds rod/port caches without incrementing {@link #structureRevision} (client-only validation). */
    private void rebuildPartCachesQuiet(Level level, TurbineValidation.Result result) {
        if (level == null || result == null || !result.valid()) {
            cachedPowerPortPositions = new long[0];
            cachedResourcePortPositions = new long[0];
            cachedRedstonePortPositions = new long[0];
            cachedRodPositions = new long[0];
            cachedRodFacings = new byte[0];
            return;
        }
        int minX = result.minX();
        int minY = result.minY();
        int minZ = result.minZ();
        int maxX = result.maxX();
        int maxY = result.maxY();
        int maxZ = result.maxZ();

        LongArrayList powerPorts = new LongArrayList();
        LongArrayList resourcePorts = new LongArrayList();
        LongArrayList redstonePorts = new LongArrayList();
        LongArrayList rods = new LongArrayList();
        it.unimi.dsi.fastutil.bytes.ByteArrayList rodFacings = new it.unimi.dsi.fastutil.bytes.ByteArrayList();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    p.set(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (TurbineValidation.isTurbinePowerPort(state)) {
                        powerPorts.add(p.asLong());
                    } else if (state.is(ModBlocks.TURBINE_RESOURCE_PORT.get())) {
                        resourcePorts.add(p.asLong());
                    } else if (state.is(ModBlocks.TURBINE_REDSTONE_PORT.get())) {
                        redstonePorts.add(p.asLong());
                    } else if (state.is(ModBlocks.TURBINE_ROD.get())) {
                        rods.add(p.asLong());
                        rodFacings.add((byte) state.getValue(TurbineRodBlock.FACING).ordinal());
                    }
                }
            }
        }
        cachedPowerPortPositions = powerPorts.toLongArray();
        cachedResourcePortPositions = resourcePorts.toLongArray();
        cachedRedstonePortPositions = redstonePorts.toLongArray();
        cachedRodPositions = rods.toLongArray();
        cachedRodFacings = rodFacings.toByteArray();
    }

    /** Fills steam buffer from INSERT ports; production runs only when the redstone gate allows. */
    public void tickSimulation(ServerLevel level) {
        if (!cachedResult.valid()) {
            setRuntimeStats(0, 0, false, false);
            return;
        }
        updateFluidBufferCapacities(level, cachedResult);
        net.unfamily.colossal_reactors.multiblock.MultiblockPortScaling.scaleTurbinePorts(level, this);
        TurbineFiller.tickFill(level, this);
        lastCoilEff = cachedResult.coilEfficiency();
        lastBladeEff = cachedResult.bladeEfficiency();
        boolean gateOpen = TurbineControllerBlock.isRedstoneGateSatisfied(level, this, cachedResult);
        if (!gateOpen) {
            setRuntimeStats(0, 0, false, gateOpen);
            return;
        }
        TurbineSimulation.tick(level, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.colossal_reactors.turbine_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new TurbineControllerMenu(id, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("RedstoneGateOpen", redstoneGateOpen);
        tag.putLong("LastRf", lastRfPerTick);
        tag.putDouble("LastSteam", lastSteamPerTick);
        tag.putFloat("RotorLoad", rotorLoadFactor);
        tag.putInt("LastCoilEffMilli", (int) (lastCoilEff * 1000));
        tag.putInt("LastBladeEffMilli", (int) (lastBladeEff * 1000));
        if (cachedResult.valid()) {
            tag.putBoolean("val_valid", true);
            tag.putInt("val_minX", cachedResult.minX());
            tag.putInt("val_minY", cachedResult.minY());
            tag.putInt("val_minZ", cachedResult.minZ());
            tag.putInt("val_maxX", cachedResult.maxX());
            tag.putInt("val_maxY", cachedResult.maxY());
            tag.putInt("val_maxZ", cachedResult.maxZ());
            tag.putInt("val_bladeCount", cachedResult.bladeCount());
            tag.putInt("val_validBladeCount", cachedResult.validBladeCount());
            tag.putInt("val_coilBlockCount", cachedResult.coilBlockCount());
            tag.putInt("val_coilEffMilli", (int) (cachedResult.coilEfficiency() * 1000));
            tag.putInt("val_bladeEffMilli", (int) (cachedResult.bladeEfficiency() * 1000));
            tag.putDouble("val_steamCap", cachedResult.maxSteamMbPerTick());
            tag.putDouble("val_rfEst", cachedResult.estimatedRfPerTick());
        }
        tag.putInt("SteamConsumeMb", cachedSteamConsumeMbPerTick);
        tag.putBoolean("OutputReturnBuf", cachedOutputReturnBuffer);
        tag.putInt("OutRetMb", getTotalOutputReturnMb());
        tag.putInt("OutRetCap", getOutputReturnCapacityMb());
        saveFluidList(tag, TAG_STEAM_BUFFER, steamInputEntries);
        saveFluidList(tag, TAG_OUTPUT_BUFFER, outputReturnEntries);
        tag.putInt(TAG_STRUCTURE_REVISION, structureRevision);
        if (cachedRodPositions.length > 0) {
            tag.putLongArray("RodPos", cachedRodPositions);
            tag.putByteArray("RodFacing", cachedRodFacings);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        powered = tag.getBoolean("Powered");
        redstoneGateOpen = tag.getBoolean("RedstoneGateOpen");
        lastRfPerTick = tag.getLong("LastRf");
        lastSteamPerTick = tag.getDouble("LastSteam");
        rotorLoadFactor = tag.contains("RotorLoad") ? tag.getFloat("RotorLoad") : 0f;
        if (tag.contains("LastCoilEffMilli")) {
            lastCoilEff = tag.getInt("LastCoilEffMilli") / 1000.0;
        }
        if (tag.contains("LastBladeEffMilli")) {
            lastBladeEff = tag.getInt("LastBladeEffMilli") / 1000.0;
        }
        if (tag.getBoolean("val_valid")) {
            cachedResult = new TurbineValidation.Result(
                    true,
                    null,
                    null,
                    TurbineValidation.ValidationReport.empty(),
                    tag.getInt("val_minX"),
                    tag.getInt("val_minY"),
                    tag.getInt("val_minZ"),
                    tag.getInt("val_maxX"),
                    tag.getInt("val_maxY"),
                    tag.getInt("val_maxZ"),
                    tag.getInt("val_bladeCount"),
                    tag.getInt("val_validBladeCount"),
                    tag.getInt("val_coilBlockCount"),
                    tag.getInt("val_coilEffMilli") / 1000.0,
                    tag.getInt("val_bladeEffMilli") / 1000.0,
                    tag.getDouble("val_steamCap"),
                    tag.getDouble("val_rfEst"));
        } else {
            cachedResult = TurbineValidation.Result.invalid();
        }
        if (tag.contains("SteamConsumeMb")) {
            cachedSteamConsumeMbPerTick = Math.max(1, tag.getInt("SteamConsumeMb"));
        }
        cachedOutputReturnBuffer = tag.getBoolean("OutputReturnBuf");
        loadFluidList(tag, TAG_STEAM_BUFFER, steamInputEntries);
        loadFluidList(tag, TAG_OUTPUT_BUFFER, outputReturnEntries);
        if (tag.contains("RodPos")) {
            cachedRodPositions = tag.getLongArray("RodPos");
            cachedRodFacings = tag.contains("RodFacing") ? tag.getByteArray("RodFacing") : new byte[0];
        } else {
            cachedRodPositions = new long[0];
            cachedRodFacings = new byte[0];
        }
        if (level instanceof ServerLevel sl) {
            clampFluidBuffers();
            if (cachedResult.valid()) {
                updateFluidBufferCapacities(sl, cachedResult);
            }
        }
        if (level != null && level.isClientSide()) {
            TurbineRotorClientRegistry.onBlockEntityLoad(this);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        if (tag.getBoolean(TAG_STRUCTURE_ONLY)) {
            applyStructureSyncTag(tag);
        } else if (tag.getBoolean(TAG_RUNTIME_ONLY)) {
            applyRuntimeSyncTag(tag);
        } else {
            loadAdditional(tag, registries);
        }
        if (level != null && level.isClientSide()) {
            dispatchClientCacheReset(tag);
        }
    }

    private void dispatchClientCacheReset(CompoundTag tag) {
        if (tag.getBoolean(TAG_STRUCTURE_ONLY)) {
            TurbineRotorSimulationSource.mirrorStructureFromServer(this);
            TurbineRotorClientRegistry.onStructureSyncPacket(this);
            return;
        }
        if (tag.getBoolean(TAG_RUNTIME_ONLY)) {
            int previousRevision = TurbineRotorClientRegistry.getAppliedStructureRevision(getBlockPos());
            TurbineRotorClientRegistry.resetRuntimeCache(this);
            if (getStructureRevision() != previousRevision) {
                TurbineRotorSimulationSource.mirrorStructureFromServer(this);
                TurbineRotorClientRegistry.resetGeometryCache(this, false, true);
            }
            return;
        }
        TurbineRotorClientRegistry.onBlockEntityLoad(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            TurbineRotorClientRegistry.onBlockEntityLoad(this);
            return;
        }
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.is(ModBlocks.TURBINE_CONTROLLER.get())) {
            return;
        }
        if (state.getValue(TurbineControllerBlock.VISUAL) == TurbineVisualState.ON && !getCachedResult().valid()) {
            level.scheduleTick(worldPosition, state.getBlock(), 1);
        }
    }

    /** Refreshes validation when the multiblock cache was lost (e.g. chunk reload). Returns the new result. */
    public TurbineValidation.Result refreshValidationCache(ServerLevel level, Direction intoTurbine) {
        BlockPos start = worldPosition.relative(intoTurbine);
        TurbineValidation.Result result = TurbineValidation.validateWithRodAlignment(level, start, intoTurbine, -1);
        setCachedResult(result);
        return result;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        if (syncStructureOnly) {
            CompoundTag tag = buildStructureSyncTag();
            syncStructureOnly = false;
            return tag;
        }
        if (syncRuntimeOnly) {
            return buildRuntimeSyncTag();
        }
        return saveWithoutMetadata(registries);
    }
}
