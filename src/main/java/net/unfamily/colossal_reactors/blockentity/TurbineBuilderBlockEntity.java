package net.unfamily.colossal_reactors.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.builder.web.ModDecodedTurbineDesign;
import net.unfamily.colossal_reactors.turbine.ElecCoilLoader;
import net.unfamily.colossal_reactors.menu.TurbineBuilderMenu;
import net.unfamily.colossal_reactors.turbine.TurbineBuildLogic;
import net.unfamily.colossal_reactors.turbine.TurbineBuildMaterialCounter;
import net.unfamily.colossal_reactors.turbine.TurbineFlowMath;
import net.unfamily.colossal_reactors.turbine.TurbineGenerationLoader;
import net.unfamily.colossal_reactors.turbine.TurbinePlacementAxis;
import net.unfamily.colossal_reactors.turbine.TurbineRodPatternLogic;
import net.unfamily.colossal_reactors.turbine.TurbineRodSpaceLayout;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * BlockEntity for Turbine Builder. Holds a 9x3 buffer inventory and a fluid tank for steam marking.
 */
public class TurbineBuilderBlockEntity extends BlockEntity implements MenuProvider {

    private static final String TAG_BUFFER = "Buffer";
    private static final String TAG_FLUID = "Fluid";
    private static final String TAG_FLUID_ID = "FluidId";
    private static final String TAG_FLUID_AMOUNT = "Amount";
    private static final String TAG_SIZE_L = "SizeL";
    private static final String TAG_SIZE_R = "SizeR";
    private static final String TAG_SIZE_H = "SizeH";
    private static final String TAG_SIZE_D = "SizeD";
    private static final String TAG_SIZE_W = "SizeW";
    private static final String TAG_COIL_IDX = "CoilIdx";
    private static final String TAG_ROD_COIL_IDX = "RodCoilIdx";
    private static final String TAG_SIM_GENERATION_IDX = "SimGenerationIdx";
    private static final String TAG_OPEN_TOP = "OpenTop";
    private static final String TAG_ROD_PATTERN = "RodPattern";
    private static final String TAG_BLADE_VARIANT = "BladeVariant";
    private static final String TAG_PATTERN_MODE = "PatternMode";
    private static final String TAG_BUILDING = "Building";
    private static final String TAG_INVALID_BLOCKS = "InvalidBlocks";
    private static final String TAG_BUILD_STAGE = "BuildStage";
    private static final String TAG_BUILD_FRAME_X = "BuildFrameX";
    private static final String TAG_BUILD_FRAME_Y = "BuildFrameY";
    private static final String TAG_BUILD_FRAME_Z = "BuildFrameZ";
    private static final String TAG_BUILD_RODCTRL_RX = "BuildRodCtrlRx";
    private static final String TAG_BUILD_RODCTRL_RZ = "BuildRodCtrlRz";
    private static final String TAG_BUILD_ROD_LX = "BuildRodLx";
    private static final String TAG_BUILD_ROD_LY = "BuildRodLy";
    private static final String TAG_BUILD_ROD_LZ = "BuildRodLz";
    private static final String TAG_BUILD_LIQUID_LX = "BuildLiquidLx";
    private static final String TAG_BUILD_LIQUID_LY = "BuildLiquidLy";
    private static final String TAG_BUILD_LIQUID_LZ = "BuildLiquidLz";
    private static final String TAG_BUILD_HEAT_LX = "BuildHeatLx";
    private static final String TAG_BUILD_HEAT_LY = "BuildHeatLy";
    private static final String TAG_BUILD_HEAT_LZ = "BuildHeatLz";
    private static final String TAG_BUILD_PROGRESS = "BuildProgress";
    private static final String TAG_BUILD_PROGRESS_VISIBLE = "BuildProgressVisible";
    private static final String TAG_PLACEMENT_AXIS = "PlacementAxis";
    private static final String TAG_MARK_INPUT_FILTERS = "MarkInputFilters";
    private static final String TAG_WEBSITE_DESIGN = "WebsiteDesign";
    private static final int BUFFER_SLOTS = 9 * 3;

    private final List<ItemStack> markInputFilters = new ArrayList<>();
    private static final int MIN_SIZE = 1;

    private static int getTankCapacityMb() {
        return Config.TURBINE_BUILDER_TANK_CAPACITY_MB.get();
    }

    private static int getMaxWidth() {
        // Internal builder sizes store "offsets" (e.g. width displayed = (L+R)+1).
        // Config MAX_REACTOR_WIDTH is the displayed block count, so clamp offsets to (max - 1).
        return Math.max(MIN_SIZE, Config.MAX_TURBINE_WIDTH.get() - 1);
    }

    private static int getMaxHeight() {
        return Math.max(MIN_SIZE, Config.MAX_TURBINE_HEIGHT.get() - 1);
    }

    private static int getMaxDepth() {
        return Math.max(MIN_SIZE, Config.MAX_TURBINE_LENGTH.get() - 1);
    }

    private final ItemStackHandler bufferHandler = new ItemStackHandler(BUFFER_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot >= 0 && slot < markInputFilters.size()) {
                ItemStack filter = markInputFilters.get(slot);
                if (!filter.isEmpty()) {
                    return ItemStack.isSameItemSameComponents(stack, filter);
                }
            }
            return true;
        }
    };

    private final FluidTank fluidTank = new FluidTank(getTankCapacityMb()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private final ContainerData fluidData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> fluidTank.getFluidAmount();
                case 1 -> fluidTank.getCapacity();
                case 2 -> fluidTank.getFluid().isEmpty()
                        ? -1
                        : BuiltInRegistries.FLUID.getId(fluidTank.getFluid().getFluid());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                FluidStack current = fluidTank.getFluid();
                if (value <= 0) {
                    fluidTank.setFluid(FluidStack.EMPTY);
                } else if (!current.isEmpty()) {
                    fluidTank.setFluid(new FluidStack(current.getFluid(), Math.min(value, fluidTank.getCapacity())));
                }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    /** Default 7x6x6 centered on X: 3 left + 3 right = 7 width, height 6, depth 6. */
    private int sizeLeft = 3;
    private int sizeRight = 3;
    private int sizeHeight = 6;
    private int sizeDepth = 6;
    /** Heat sink option index for fill: 0 = Air, 1.. = ElecCoilLoader definition index. */
    private int selectedCoilIndex = 0;
    /** Coil material for rod-column cells in the coil zone (above rods). */
    private int selectedRodCoilIndex = 0;
    /** Simulation steam-generation recipe index among visible definitions. */
    private int simGenerationIndex = 0;
    /** When built: true = absolute top face open (no top casing); rod controller still placed. */
    private boolean openTop = false;
    private int coilLayerCount = Config.TURBINE_DEFAULT_COIL_LAYER_COUNT.get();
    /** Builder mode: Website / Flow / HyperFlow / Custom / None. */
    private int rodPattern = TurbineRodPatternLogic.PATTERN_WEBSITE;
    /** Efficient (climb) / Productive (full); used by Flow and HyperFlow. */
    private int bladeVariant = TurbineRodPatternLogic.VARIANT_EFFICIENT;
    /** Index into {@link TurbinePlacementAxis} (6 flow directions). */
    private int placementAxisIndex = TurbinePlacementAxis.DEFAULT_INDEX;
    /** Pattern mode: 0=OPTIMIZED (-2 inset), 1=PRODUCTION (no inset), 2=ECONOMY (like optimized, border fill differs). */
    /** True when build is in progress (button shows Stop). */
    private boolean building = false;
    /** True when build stopped because one or more invalid blocks were found (red zone). Cleared on start/stop. */
    private boolean invalidBlocksDetected = false;
    /** Last computed build progress (0-100). Kept visible after build completes/aborts until user stops or restarts. */
    private int buildProgressPercent = 0;
    private boolean buildProgressVisible = false;
    /** Raw ModDecoded {@code mdct1} seed (or pasted URL fragment); parsed on use. */
    private String websiteDesignCode = "";
    /** Cached rod mask from {@link #websiteDesignCode}; null when invalid/cleared. */
    @Nullable
    private transient boolean[] websiteRodMaskCache;
    private transient boolean websiteClimbProfile;
    /** Cached {@code mdct1} coil fill {@code o}; Builder still fills the whole coil zone. */
    private transient boolean websiteCoilFillOne;
    /** Cached {@code mdct1} ring cap; {@link Integer#MAX_VALUE} when no valid website design. */
    private transient int websiteBladeRing = Integer.MAX_VALUE;
    private transient int websiteCacheW = -1;
    private transient int websiteCacheD = -1;
    // Build progress cursors (NEXT position to process). These make building "forward-only" and avoid rescanning from start.
    private int buildStage = 0;
    private int buildFrameX = Integer.MIN_VALUE, buildFrameY = Integer.MIN_VALUE, buildFrameZ = Integer.MIN_VALUE;
    private int buildRodCtrlRx = Integer.MIN_VALUE, buildRodCtrlRz = Integer.MIN_VALUE;
    private int buildRodLx = Integer.MIN_VALUE, buildRodLy = Integer.MIN_VALUE, buildRodLz = Integer.MIN_VALUE;
    private int buildLiquidLx = Integer.MIN_VALUE, buildLiquidLy = Integer.MIN_VALUE, buildLiquidLz = Integer.MIN_VALUE;
    private int buildHeatLx = Integer.MIN_VALUE, buildHeatLy = Integer.MIN_VALUE, buildHeatLz = Integer.MIN_VALUE;

    private static final int ROD_PATTERN_COUNT = TurbineRodPatternLogic.PATTERN_COUNT;
    private static final int BLADE_VARIANT_COUNT = TurbineRodPatternLogic.VARIANT_COUNT;
    /** Stored GUI setting; displayed layer count is {@link TurbineRodSpaceLayout#appliedCoilLayerCount}. */
    private static final int COIL_LAYER_MIN = 1;
    private static final int COIL_LAYER_MAX = 32;

    public void resetBuildProgress() {
        buildStage = 0;
        buildFrameX = buildFrameY = buildFrameZ = Integer.MIN_VALUE;
        buildRodCtrlRx = buildRodCtrlRz = Integer.MIN_VALUE;
        buildRodLx = buildRodLy = buildRodLz = Integer.MIN_VALUE;
        buildLiquidLx = buildLiquidLy = buildLiquidLz = Integer.MIN_VALUE;
        buildHeatLx = buildHeatLy = buildHeatLz = Integer.MIN_VALUE;
    }

    public int getBuildStage() { return buildStage; }
    public void setBuildStage(int stage) { this.buildStage = stage; }

    public int getBuildFrameX() { return buildFrameX; }
    public int getBuildFrameY() { return buildFrameY; }
    public int getBuildFrameZ() { return buildFrameZ; }
    public void setBuildFrameCursor(int x, int y, int z) { buildFrameX = x; buildFrameY = y; buildFrameZ = z; }

    public int getBuildRodCtrlRx() { return buildRodCtrlRx; }
    public int getBuildRodCtrlRz() { return buildRodCtrlRz; }
    public void setBuildRodCtrlCursor(int rx, int rz) { buildRodCtrlRx = rx; buildRodCtrlRz = rz; }

    public int getBuildRodLx() { return buildRodLx; }
    public int getBuildRodLy() { return buildRodLy; }
    public int getBuildRodLz() { return buildRodLz; }
    public void setBuildRodCursor(int lx, int ly, int lz) { buildRodLx = lx; buildRodLy = ly; buildRodLz = lz; }

    public int getBuildLiquidLx() { return buildLiquidLx; }
    public int getBuildLiquidLy() { return buildLiquidLy; }
    public int getBuildLiquidLz() { return buildLiquidLz; }
    public void setBuildLiquidCursor(int lx, int ly, int lz) { buildLiquidLx = lx; buildLiquidLy = ly; buildLiquidLz = lz; }

    public int getBuildHeatLx() { return buildHeatLx; }
    public int getBuildHeatLy() { return buildHeatLy; }
    public int getBuildHeatLz() { return buildHeatLz; }
    public void setBuildHeatCursor(int lx, int ly, int lz) { buildHeatLx = lx; buildHeatLy = ly; buildHeatLz = lz; }

    private final ContainerData sizeData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> sizeLeft;
                case 1 -> sizeRight;
                case 2 -> sizeHeight;
                case 3 -> sizeDepth;
                case 4 -> worldPosition.getX();
                case 5 -> worldPosition.getY();
                case 6 -> worldPosition.getZ();
                case 7 -> selectedCoilIndex;
                case 8 -> coilLayerCount;
                case 9 -> rodPattern;
                case 10 -> openTop ? 1 : 0;
                case 11 -> building ? 1 : 0;
                case 12 -> invalidBlocksDetected ? 1 : 0;
                case 13 -> buildProgressPercent;
                case 14 -> buildProgressVisible ? 1 : 0;
                case 15 -> placementAxisIndex;
                case 16 -> getSimGenerationIndex();
                case 17 -> bladeVariant;
                case 18 -> selectedRodCoilIndex;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index >= 4 && index != 7 && index != 8 && index != 9 && index != 10 && index != 11
                    && index != 12 && index != 13 && index != 14 && index != 15 && index != 16
                    && index != 17 && index != 18) {
                return;
            }
            switch (index) {
                case 0 -> {
                    sizeLeft = Math.max(0, Math.min(getMaxWidth() - sizeRight, value));
                    if (sizeLeft + sizeRight < 1) sizeRight = 1 - sizeLeft;
                    invalidateWebsiteRodCache();
                }
                case 1 -> {
                    sizeRight = Math.max(0, Math.min(getMaxWidth() - sizeLeft, value));
                    if (sizeLeft + sizeRight < 1) sizeLeft = 1 - sizeRight;
                    invalidateWebsiteRodCache();
                }
                case 2 -> sizeHeight = Math.max(MIN_SIZE, Math.min(getMaxHeight(), value));
                case 3 -> {
                    sizeDepth = Math.max(MIN_SIZE, Math.min(getMaxDepth(), value));
                    invalidateWebsiteRodCache();
                }
                case 7 -> selectedCoilIndex = Math.max(0, Math.min(ElecCoilLoader.getCoilOptionCount() - 1, value));
                case 8 -> coilLayerCount = Math.max(COIL_LAYER_MIN, Math.min(COIL_LAYER_MAX, value));
                case 9 -> rodPattern = Math.max(0, Math.min(ROD_PATTERN_COUNT - 1, value));
                case 10 -> openTop = value != 0;
                case 11 -> building = value != 0;
                case 12 -> invalidBlocksDetected = value != 0;
                case 13 -> buildProgressPercent = Math.max(0, Math.min(100, value));
                case 14 -> buildProgressVisible = value != 0;
                case 15 -> {
                    if (value >= 0 && value < TurbinePlacementAxis.count()) {
                        placementAxisIndex = value;
                    }
                }
                case 16 -> simGenerationIndex = Math.max(0, value);
                case 17 -> bladeVariant = Math.max(0, Math.min(BLADE_VARIANT_COUNT - 1, value));
                case 18 -> selectedRodCoilIndex = Math.max(0, Math.min(ElecCoilLoader.getCoilOptionCount() - 1, value));
                default -> {}
            }
        }

        @Override
        public int getCount() {
            return 19;
        }
    };

    public TurbineBuilderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURBINE_BUILDER_BE.get(), pos, state);
        for (int i = 0; i < BUFFER_SLOTS; i++) {
            markInputFilters.add(ItemStack.EMPTY);
        }
    }

    public void applyMarkInputFromBuffer() {
        for (int slot = 0; slot < bufferHandler.getSlots(); slot++) {
            ItemStack current = bufferHandler.getStackInSlot(slot);
            if (!current.isEmpty()) {
                markInputFilters.set(slot, current.copyWithCount(1));
            }
        }
        setChanged();
    }

    public void clearAllMarkInputFilters() {
        for (int i = 0; i < markInputFilters.size(); i++) {
            markInputFilters.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    public void clearMarkInputFiltersWithoutMatchingStacks() {
        for (int slot = 0; slot < markInputFilters.size(); slot++) {
            ItemStack filter = markInputFilters.get(slot);
            if (filter.isEmpty()) continue;
            ItemStack current = bufferHandler.getStackInSlot(slot);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, filter)) {
                markInputFilters.set(slot, ItemStack.EMPTY);
            }
        }
        setChanged();
    }

    public ItemStack getMarkInputFilter(int slot) {
        if (slot >= 0 && slot < markInputFilters.size()) {
            return markInputFilters.get(slot);
        }
        return ItemStack.EMPTY;
    }

    public boolean hasMarkInputFilter(int slot) {
        return !getMarkInputFilter(slot).isEmpty();
    }

    /** Sets one mark-input filter (empty stack clears). Used by JEI ghost drop / double-click clear. */
    public void setMarkInputFilter(int slot, ItemStack stack) {
        if (slot < 0 || slot >= markInputFilters.size()) {
            return;
        }
        markInputFilters.set(slot, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        setChanged();
    }

    public IItemHandler getBufferHandler() {
        return bufferHandler;
    }

    /** Buffer exposed to automation (hoppers/pipes). */
    public IItemHandler getItemHandlerForCapability() {
        return bufferHandler;
    }

    public FluidTank getFluidTank() {
        return fluidTank;
    }

    /** Server: discard all fluid in the internal tank (GUI dump). @return true if any fluid was removed */
    public boolean dumpFluidTankContents() {
        if (level == null || level.isClientSide()) return false;
        if (fluidTank.getFluid().isEmpty()) return false;
        fluidTank.setFluid(FluidStack.EMPTY);
        setChanged();
        return true;
    }

    public ContainerData getFluidData() {
        return fluidData;
    }

    public ContainerData getSizeData() {
        return sizeData;
    }

    public int getSizeLeft() { return sizeLeft; }
    public int getSizeRight() { return sizeRight; }
    public int getSizeHeight() { return sizeHeight; }
    public int getSizeDepth() { return sizeDepth; }
    public int getExteriorWidth() { return sizeLeft + sizeRight + 1; }
    public int getExteriorHeight() { return sizeHeight + 1; }
    public int getExteriorDepth() { return sizeDepth + 1; }
    public int getSelectedCoilIndex() { return selectedCoilIndex; }
    public int getSelectedRodCoilIndex() { return selectedRodCoilIndex; }
    public int getCoilLayerCount() { return coilLayerCount; }

    public void setRodPattern(int pattern) {
        this.rodPattern = Math.max(0, Math.min(ROD_PATTERN_COUNT - 1, pattern));
        setChanged();
    }

    public int getBladeVariant() {
        return bladeVariant;
    }

    public void setBladeVariant(int variant) {
        this.bladeVariant = Math.max(0, Math.min(BLADE_VARIANT_COUNT - 1, variant));
        setChanged();
    }

    public void setSelectedCoilIndex(int index) {
        this.selectedCoilIndex = Math.max(0, Math.min(ElecCoilLoader.getCoilOptionCount() - 1, index));
        setChanged();
    }

    public void setSelectedRodCoilIndex(int index) {
        this.selectedRodCoilIndex = Math.max(0, Math.min(ElecCoilLoader.getCoilOptionCount() - 1, index));
        setChanged();
    }

    public void setCoilLayerCount(int layers) {
        this.coilLayerCount = Math.max(COIL_LAYER_MIN, Math.min(COIL_LAYER_MAX, layers));
        clampCoilLayerCountToFit();
        setChanged();
    }

    /**
     * Sets exterior W×D×H (blocks) centered on the builder: {@code sizeLeft=(W-1)/2},
     * {@code sizeRight=W-1-sizeLeft}, {@code sizeHeight=H-1}, {@code sizeDepth=D-1}.
     */
    public void setExteriorSizeCentered(int exteriorW, int exteriorD, int exteriorH) {
        int w = Math.max(MIN_SIZE + 1, Math.min(getMaxWidth() + 1, exteriorW));
        int d = Math.max(MIN_SIZE + 1, Math.min(getMaxDepth() + 1, exteriorD));
        int h = Math.max(MIN_SIZE + 1, Math.min(getMaxHeight() + 1, exteriorH));
        sizeLeft = (w - 1) / 2;
        sizeRight = (w - 1) - sizeLeft;
        sizeDepth = Math.max(MIN_SIZE, d - 1);
        sizeHeight = Math.max(MIN_SIZE, h - 1);
        clampCoilLayerCountToFit();
        invalidateWebsiteRodCache();
        setChanged();
    }

    public String getWebsiteDesign() {
        return websiteDesignCode != null ? websiteDesignCode : "";
    }

    /** Stores the raw seed (or pasted URL); refreshes the decoded rod mask when valid. */
    public void setWebsiteDesign(String code) {
        String raw = code != null ? code.trim() : "";
        Optional<ModDecodedTurbineDesign> decoded = ModDecodedTurbineDesign.decode(raw);
        if (decoded.isPresent()) {
            websiteDesignCode = decoded.get().code();
            refreshWebsiteRodCache(decoded.get());
        } else {
            String extracted = ModDecodedTurbineDesign.extractCode(raw);
            websiteDesignCode = extracted != null ? extracted : raw;
            invalidateWebsiteRodCache();
        }
        setChanged();
    }

    public boolean hasValidWebsiteDesign() {
        ensureWebsiteRodCache();
        return websiteRodMaskCache != null;
    }

    /** True when the website design uses climbing blade rings ({@code c} profile). */
    public boolean isWebsiteClimbProfile() {
        ensureWebsiteRodCache();
        return websiteClimbProfile;
    }

    /**
     * True when the seed asks for coil fill {@code o} (one block).
     * Build and Website sim still fill the whole coil zone; use for GUI hints only.
     */
    public boolean isWebsiteCoilFillOne() {
        ensureWebsiteRodCache();
        return websiteRodMaskCache != null && websiteCoilFillOne;
    }

    /**
     * Blade ring cap from the website design ({@code mdct1} ring field).
     * {@link Integer#MAX_VALUE} when no valid website design is loaded.
     */
    public int getWebsiteBladeRingCap() {
        ensureWebsiteRodCache();
        return websiteRodMaskCache != null ? websiteBladeRing : Integer.MAX_VALUE;
    }

    public boolean websiteIsRodColumn(int rx, int rz) {
        ensureWebsiteRodCache();
        if (websiteRodMaskCache == null) {
            return false;
        }
        int iw = getExteriorWidth() - 2;
        int id = getExteriorDepth() - 2;
        if (rx < 0 || rz < 0 || rx >= iw || rz >= id) {
            return false;
        }
        int i = rx * id + rz;
        return i >= 0 && i < websiteRodMaskCache.length && websiteRodMaskCache[i];
    }

    private void ensureWebsiteRodCache() {
        int w = getExteriorWidth();
        int d = getExteriorDepth();
        if (websiteRodMaskCache != null && websiteCacheW == w && websiteCacheD == d) {
            return;
        }
        Optional<ModDecodedTurbineDesign> decoded = ModDecodedTurbineDesign.decode(websiteDesignCode);
        if (decoded.isEmpty()) {
            invalidateWebsiteRodCache();
            return;
        }
        ModDecodedTurbineDesign design = decoded.get();
        if (design.exteriorX() != w || design.exteriorZ() != d) {
            invalidateWebsiteRodCache();
            return;
        }
        refreshWebsiteRodCache(design);
    }

    private void refreshWebsiteRodCache(ModDecodedTurbineDesign design) {
        websiteRodMaskCache = design.rodMask().clone();
        websiteClimbProfile = design.climbProfile();
        websiteCoilFillOne = design.coilFillOne();
        websiteBladeRing = Math.max(0, design.ring());
        websiteCacheW = design.exteriorX();
        websiteCacheD = design.exteriorZ();
    }

    private void invalidateWebsiteRodCache() {
        websiteRodMaskCache = null;
        websiteClimbProfile = false;
        websiteCoilFillOne = false;
        websiteBladeRing = Integer.MAX_VALUE;
        websiteCacheW = -1;
        websiteCacheD = -1;
    }

    /** Layers used by build/simulation (same as GUI setting, clamped to fit). */
    public int getAppliedCoilLayerCount() {
        return TurbineRodSpaceLayout.appliedCoilLayerCount(interiorExtentAlongPlacementAxis(), coilLayerCount);
    }

    private int interiorExtentAlongPlacementAxis() {
        int w = sizeLeft + sizeRight + 1;
        int h = sizeHeight + 1;
        int d = sizeDepth + 1;
        return switch (getPlacementAxis().getAxis()) {
            case Y -> TurbineRodSpaceLayout.interiorHeight(h);
            case Z -> TurbineRodSpaceLayout.interiorDepth(d);
            case X -> TurbineRodSpaceLayout.interiorWidth(w);
            default -> TurbineRodSpaceLayout.interiorHeight(h);
        };
    }

    private int maxCoilLayersForCurrentHeight() {
        int physical = Math.min(COIL_LAYER_MAX,
                TurbineRodSpaceLayout.maxCoilLayerSettingForInterior(interiorExtentAlongPlacementAxis()));
        // Website seeds keep full physical range (site rebalance is a follow-up).
        if (rodPattern == TurbineRodPatternLogic.PATTERN_WEBSITE) {
            return physical;
        }
        int w = sizeLeft + sizeRight + 1;
        int h = sizeHeight + 1;
        int d = sizeDepth + 1;
        int along = interiorExtentAlongPlacementAxis();
        int crossA;
        int crossB;
        switch (getPlacementAxis().getAxis()) {
            case Y -> {
                crossA = TurbineRodSpaceLayout.interiorWidth(w);
                crossB = TurbineRodSpaceLayout.interiorDepth(d);
            }
            case Z -> {
                crossA = TurbineRodSpaceLayout.interiorWidth(w);
                crossB = TurbineRodSpaceLayout.interiorHeight(h);
            }
            case X -> {
                crossA = TurbineRodSpaceLayout.interiorHeight(h);
                crossB = TurbineRodSpaceLayout.interiorDepth(d);
            }
            default -> {
                crossA = 0;
                crossB = 0;
            }
        }
        int useful = TurbineFlowMath.targetCoilLayers(along, crossA, crossB);
        return Math.max(COIL_LAYER_MIN, Math.min(physical, useful));
    }

    /** Lowers stored coil layers when the turbine is too short for the current setting. */
    private void clampCoilLayerCountToFit() {
        int max = maxCoilLayersForCurrentHeight();
        if (coilLayerCount > max) {
            coilLayerCount = Math.max(COIL_LAYER_MIN, max);
            setChanged();
        }
    }
    public int getRodPattern() { return rodPattern; }
    public int getPlacementAxisIndex() { return placementAxisIndex; }

    public Direction getPlacementAxis() {
        return TurbinePlacementAxis.fromIndex(placementAxisIndex).facing();
    }
    public boolean isBuilding() { return building; }
    public boolean isInvalidBlocksDetected() { return invalidBlocksDetected; }
    public int getBuildProgressPercent() { return buildProgressPercent; }
    public boolean isBuildProgressVisible() { return buildProgressVisible; }

    /** Start building: only if no red zone. Called from server when user presses Build. Sets invalidBlocksDetected when refusing so GUI shows warning. */
    public void startBuild() {
        if (level == null || level.isClientSide()) return;
        if (TurbineBuildLogic.hasRedZone((net.minecraft.server.level.ServerLevel) level, this)) {
            invalidBlocksDetected = true;
            setChanged();
            return;
        }
        building = true;
        buildProgressVisible = true;
        buildProgressPercent = 0;
        resetBuildProgress();
        invalidBlocksDetected = false;
        setChanged();
    }

    /** Stop building. Called when user presses Stop. Clears progress visibility. */
    public void stopBuild() {
        stopBuild(true);
    }

    /**
     * Stop building. When {@code resetProgress} is false, keep progress visible so the GUI can show 100% (or last %) after completion/abort.
     */
    public void stopBuild(boolean resetProgress) {
        building = false;
        if (resetProgress) {
            buildProgressVisible = false;
            buildProgressPercent = 0;
            resetBuildProgress();
        }
        setChanged();
    }

    /** Clear build warnings. Called when user presses Build (start) or Stop so the message is dismissed. */
    public void clearInvalidBlocksFlag() {
        if (invalidBlocksDetected) {
            invalidBlocksDetected = false;
            setChanged();
        }
    }

    /** Server tick: advance build one step. Called from block ticker. */
    public void serverTick() {
        if (!building || level == null || level.isClientSide()) return;
        int steps = Config.TURBINE_BUILDER_BUILD_STEPS_PER_TICK.get();
        TurbineBuildLogic.tick(this, steps);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            buildProgressPercent = computeBuildProgressPercent(serverLevel);
        }
        if (buildStage >= TurbineBuildLogic.STAGE_DONE) {
            buildProgressPercent = 100;
            stopBuild(false);
            return;
        }
        if (!building) {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && TurbineBuildLogic.hasRedZone(serverLevel, this)) {
                invalidBlocksDetected = true;
            }
            stopBuild(false);
        }
    }

    private int computeBuildProgressPercent(net.minecraft.server.level.ServerLevel serverLevel) {
        return TurbineBuildLogic.computeBuildProgressPercent(serverLevel, this);
    }

    public boolean isOpenTop() {
        return openTop;
    }

    /** Left click = open, right click = closed (same as reactor builder). */
    /** Both LMB and RMB toggle; {@code next} is ignored (only two states). */
    public void cycleOpenTop(boolean next) {
        openTop = !openTop;
        setChanged();
    }

    /** Cycle coil layer count (1 .. max for current turbine height). On Website, rewrites mdct1 layers. */
    public void cycleCoilLayerCount(boolean next) {
        int maxForSize = maxCoilLayersForCurrentHeight();
        if (next) {
            coilLayerCount = coilLayerCount >= maxForSize ? COIL_LAYER_MIN : coilLayerCount + 1;
        } else {
            coilLayerCount = coilLayerCount <= COIL_LAYER_MIN ? maxForSize : coilLayerCount - 1;
        }
        net.unfamily.colossal_reactors.builder.web.BuilderWebsiteDesigns.syncTurbineWebsiteSeedFromCoilSelection(this);
        setChanged();
    }

    /** Cycle builder mode: Website / Flow / HyperFlow / Custom / None. */
    public void cycleRodPattern(boolean next) {
        int previous = rodPattern;
        if (next) rodPattern = (rodPattern + 1) % ROD_PATTERN_COUNT;
        else rodPattern = rodPattern <= 0 ? ROD_PATTERN_COUNT - 1 : rodPattern - 1;
        // Default Efficient whenever entering Flow / HyperFlow.
        if (TurbineRodPatternLogic.usesBladeVariantToggle(rodPattern)) {
            bladeVariant = TurbineRodPatternLogic.VARIANT_EFFICIENT;
        }
        clampCoilLayerCountToFit();
        // Returning to Website: re-apply stored seed. In other modes the seed is kept but ignored.
        if (rodPattern == TurbineRodPatternLogic.PATTERN_WEBSITE
                && previous != TurbineRodPatternLogic.PATTERN_WEBSITE) {
            String code = getWebsiteDesign();
            if (code != null && !code.isEmpty()) {
                net.unfamily.colossal_reactors.builder.web.BuilderWebsiteDesigns.applyToTurbineBuilder(this, code);
                return;
            }
        }
        setChanged();
    }

    /** Cycle Efficient ↔ Productive (Flow / HyperFlow). */
    public void cycleBladeVariant(boolean next) {
        if (!TurbineRodPatternLogic.usesBladeVariantToggle(rodPattern)) {
            return;
        }
        if (next) {
            bladeVariant = (bladeVariant + 1) % BLADE_VARIANT_COUNT;
        } else {
            bladeVariant = bladeVariant <= 0 ? BLADE_VARIANT_COUNT - 1 : bladeVariant - 1;
        }
        setChanged();
    }

    /** Cycle placement axis (6 flow directions: D-U, U-D, N-S, …). */
    public void cyclePlacementAxis(boolean next) {
        int n = TurbinePlacementAxis.count();
        if (next) {
            placementAxisIndex = (placementAxisIndex + 1) % n;
        } else {
            placementAxisIndex = (placementAxisIndex + n - 1) % n;
        }
        if (building) {
            resetBuildProgress();
        }
        clampCoilLayerCountToFit();
        setChanged();
    }

    /** Cycle elec coil block type. On Website pattern, also rewrites the mdct1/mdct2 seed coil token. */
    public void cycleCoil(boolean next) {
        int max = ElecCoilLoader.getCoilOptionCount() - 1;
        if (next) selectedCoilIndex = selectedCoilIndex >= max ? 0 : selectedCoilIndex + 1;
        else selectedCoilIndex = selectedCoilIndex <= 0 ? max : selectedCoilIndex - 1;
        net.unfamily.colossal_reactors.builder.web.BuilderWebsiteDesigns.syncTurbineWebsiteSeedFromCoilSelection(this);
        setChanged();
    }

    /** Cycle coil material for rod-column cells; on Website pattern also rewrites mdct1/mdct2 rodCoil. */
    public void cycleRodCoil(boolean next) {
        int max = ElecCoilLoader.getCoilOptionCount() - 1;
        if (next) selectedRodCoilIndex = selectedRodCoilIndex >= max ? 0 : selectedRodCoilIndex + 1;
        else selectedRodCoilIndex = selectedRodCoilIndex <= 0 ? max : selectedRodCoilIndex - 1;
        net.unfamily.colossal_reactors.builder.web.BuilderWebsiteDesigns.syncTurbineWebsiteSeedFromCoilSelection(this);
        setChanged();
    }

    public int getSimGenerationIndex() {
        int count = TurbineGenerationLoader.getVisibleDefinitions().size();
        if (count <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(count - 1, simGenerationIndex));
    }

    /** Cycle simulation steam-generation recipe among visible definitions. */
    public void cycleSimGeneration(boolean next) {
        int count = TurbineGenerationLoader.getVisibleDefinitions().size();
        if (count <= 0) {
            return;
        }
        int current = getSimGenerationIndex();
        if (next) {
            simGenerationIndex = (current + 1) % count;
        } else {
            simGenerationIndex = current <= 0 ? count - 1 : current - 1;
        }
        setChanged();
    }

    /**
     * Returns the reactor volume AABB in world coordinates (block-aligned).
     * Reactor extends from one block behind the builder (opposite of facing), with left/right/up/depth from sizes.
     */
    public static AABB getTurbineVolumeAABB(BlockPos builderPos, Direction facing, int sizeLeft, int sizeRight, int sizeHeight, int sizeDepth) {
        Direction back = facing.getOpposite();
        Direction left = facing.getCounterClockWise(Direction.Axis.Y);
        Direction right = facing.getClockWise(Direction.Axis.Y);
        BlockPos base = builderPos.relative(back, 1);
        int minX = base.getX(), maxX = base.getX();
        int minY = base.getY(), maxY = base.getY();
        int minZ = base.getZ(), maxZ = base.getZ();
        for (int dl = 0; dl <= sizeLeft; dl++) {
            for (int dr = 0; dr <= sizeRight; dr++) {
                for (int dy = 0; dy <= sizeHeight; dy++) {
                    for (int dd = 0; dd <= sizeDepth; dd++) {
                        int x = base.getX() + left.getStepX() * dl + right.getStepX() * dr + back.getStepX() * dd;
                        int y = base.getY() + Direction.UP.getStepY() * dy + left.getStepY() * dl + right.getStepY() * dr + back.getStepY() * dd;
                        int z = base.getZ() + left.getStepZ() * dl + right.getStepZ() * dr + back.getStepZ() * dd;
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    }
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    /** Direction: 0=up, 1=left button (adjusts sizeRight), 2=right button (adjusts sizeLeft), 3=behind. amount=1,5,10. */
    /**
     * Direction: 0=up, 1=left (sizeRight), 2=right (sizeLeft), 3=behind,
     * 4=offset toward left («), 5=offset toward right (»), 6=center L/R.
     */
    public void adjustSize(int direction, boolean increment, int amount) {
        int delta = increment ? amount : -amount;
        switch (direction) {
            case 0 -> {
                sizeHeight = Math.max(MIN_SIZE, Math.min(getMaxHeight(), sizeHeight + delta));
                clampCoilLayerCountToFit();
            }
            case 1 -> {
                int newR = Math.max(0, Math.min(getMaxWidth() - sizeLeft, sizeRight + delta));
                sizeRight = newR;
                if (sizeLeft + sizeRight < 1) sizeLeft = 1 - sizeRight;
            }
            case 2 -> {
                int newL = Math.max(0, Math.min(getMaxWidth() - sizeRight, sizeLeft + delta));
                sizeLeft = newL;
                if (sizeLeft + sizeRight < 1) sizeRight = 1 - sizeLeft;
            }
            case 3 -> sizeDepth = Math.max(MIN_SIZE, Math.min(getMaxDepth(), sizeDepth + delta));
            case 4 -> offsetWidth(delta);
            case 5 -> offsetWidth(-delta);
            case 6 -> centerWidth();
            default -> {}
        }
        if (sizeLeft + sizeRight < 1) { sizeLeft = 1; sizeRight = 0; }
        invalidateWebsiteRodCache();
        setChanged();
    }

    public void offsetWidth(int amountTowardLeft) {
        if (amountTowardLeft == 0) {
            return;
        }
        int total = sizeLeft + sizeRight;
        int newLeft = Math.max(0, Math.min(total, sizeLeft + amountTowardLeft));
        sizeLeft = newLeft;
        sizeRight = total - sizeLeft;
        if (sizeLeft + sizeRight < 1) {
            sizeLeft = 1;
            sizeRight = 0;
        }
    }

    public void centerWidth() {
        int total = sizeLeft + sizeRight;
        sizeLeft = total / 2;
        sizeRight = total - sizeLeft;
    }

    /** Single step (backward compatible). */
    public void adjustSize(int direction, boolean increment) {
        adjustSize(direction, increment, 1);
    }

    /**
     * Fill tank from item or drain tank to item. Only accepts fluids that are valid reactor coolant (heat sink valid_liquids).
     */
    public boolean interactWithItemFluidHandler(IFluidHandlerItem itemHandler, Player player) {
        if (itemHandler.getTanks() == 0) return false;
        IFluidHandler blockHandler = fluidTank;
        FluidStack inItem = itemHandler.getFluidInTank(0);
        if (!inItem.isEmpty()) {
            if (getLevel() == null) return false;
            if (TurbineGenerationLoader.getDefinitionForFluid(
                    inItem.getFluid(), getLevel().registryAccess()) == null)
                return false;
            if (blockHandler.fill(inItem.copy(), IFluidHandler.FluidAction.SIMULATE) > 0) {
                int filled = blockHandler.fill(inItem.copy(), IFluidHandler.FluidAction.EXECUTE);
                if (filled > 0) {
                    itemHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                    inItem.getFluid().getPickupSound().ifPresent(player::playSound);
                    return true;
                }
            }
        } else {
            FluidStack inBlock = blockHandler.getFluidInTank(0);
            if (!inBlock.isEmpty() && itemHandler.isFluidValid(0, inBlock)) {
                int capacity = itemHandler.getTankCapacity(0);
                FluidStack toFill = inBlock.copy();
                toFill.setAmount(Math.min(inBlock.getAmount(), capacity));
                int filled = itemHandler.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
                if (filled > 0) {
                    blockHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                    var soundEvent = inBlock.getFluid().getFluidType().getSound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY);
                    if (soundEvent != null) player.playSound(soundEvent);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_BUFFER, bufferHandler.serializeNBT(registries));
        FluidStack stack = fluidTank.getFluid();
        if (!stack.isEmpty()) {
            CompoundTag fluidTag = new CompoundTag();
            fluidTag.putString(TAG_FLUID_ID, BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
            fluidTag.putInt(TAG_FLUID_AMOUNT, stack.getAmount());
            tag.put(TAG_FLUID, fluidTag);
        }
        tag.putInt(TAG_SIZE_L, sizeLeft);
        tag.putInt(TAG_SIZE_R, sizeRight);
        tag.putInt(TAG_SIZE_H, sizeHeight);
        tag.putInt(TAG_SIZE_D, sizeDepth);
        tag.putInt(TAG_COIL_IDX, selectedCoilIndex);
        tag.putInt(TAG_ROD_COIL_IDX, selectedRodCoilIndex);
        tag.putInt(TAG_SIM_GENERATION_IDX, getSimGenerationIndex());
        tag.putInt("CoilLayerCount", coilLayerCount);
        tag.putInt(TAG_ROD_PATTERN, rodPattern);
        tag.putInt(TAG_BLADE_VARIANT, bladeVariant);
        tag.putString(TAG_PLACEMENT_AXIS, TurbinePlacementAxis.fromIndex(placementAxisIndex).id());
        tag.putInt("PlacementAxisIndex", placementAxisIndex);
        tag.putBoolean(TAG_OPEN_TOP, openTop);
        tag.putBoolean(TAG_BUILDING, building);
        tag.putBoolean(TAG_INVALID_BLOCKS, invalidBlocksDetected);
        tag.putInt(TAG_BUILD_STAGE, buildStage);
        tag.putInt(TAG_BUILD_FRAME_X, buildFrameX);
        tag.putInt(TAG_BUILD_FRAME_Y, buildFrameY);
        tag.putInt(TAG_BUILD_FRAME_Z, buildFrameZ);
        tag.putInt(TAG_BUILD_RODCTRL_RX, buildRodCtrlRx);
        tag.putInt(TAG_BUILD_RODCTRL_RZ, buildRodCtrlRz);
        tag.putInt(TAG_BUILD_ROD_LX, buildRodLx);
        tag.putInt(TAG_BUILD_ROD_LY, buildRodLy);
        tag.putInt(TAG_BUILD_ROD_LZ, buildRodLz);
        tag.putInt(TAG_BUILD_LIQUID_LX, buildLiquidLx);
        tag.putInt(TAG_BUILD_LIQUID_LY, buildLiquidLy);
        tag.putInt(TAG_BUILD_LIQUID_LZ, buildLiquidLz);
        tag.putInt(TAG_BUILD_HEAT_LX, buildHeatLx);
        tag.putInt(TAG_BUILD_HEAT_LY, buildHeatLy);
        tag.putInt(TAG_BUILD_HEAT_LZ, buildHeatLz);
        tag.putInt(TAG_BUILD_PROGRESS, buildProgressPercent);
        tag.putBoolean(TAG_BUILD_PROGRESS_VISIBLE, buildProgressVisible);
        // Always write so client sync cannot treat a missing key as "clear design"
        // (that wiped the seed when cycling zone/rod coils to mdct2).
        tag.putString(TAG_WEBSITE_DESIGN, websiteDesignCode != null ? websiteDesignCode : "");
        CompoundTag markTag = new CompoundTag();
        for (int i = 0; i < markInputFilters.size(); i++) {
            final int slot = i;
            ItemStack filter = markInputFilters.get(i);
            if (!filter.isEmpty()) {
                ItemStack.OPTIONAL_CODEC
                        .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), filter)
                        .result()
                        .ifPresent(nbt -> markTag.put("slot" + slot, (CompoundTag) nbt));
            }
        }
        tag.put(TAG_MARK_INPUT_FILTERS, markTag);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_BUFFER)) {
            bufferHandler.deserializeNBT(registries, tag.getCompound(TAG_BUFFER));
        }
        fluidTank.setFluid(FluidStack.EMPTY);
        if (tag.contains(TAG_FLUID)) {
            CompoundTag fluidTag = tag.getCompound(TAG_FLUID);
            Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(fluidTag.getString(TAG_FLUID_ID)));
            int amount = fluidTag.getInt(TAG_FLUID_AMOUNT);
            if (fluid != null && fluid != Fluids.EMPTY && amount > 0) {
                fluidTank.setFluid(new FluidStack(fluid, amount));
            }
        }
        if (tag.contains(TAG_SIZE_L) || tag.contains(TAG_SIZE_R)) {
            sizeLeft = Math.max(0, Math.min(getMaxWidth(), tag.getInt(TAG_SIZE_L)));
            sizeRight = Math.max(0, Math.min(getMaxWidth(), tag.getInt(TAG_SIZE_R)));
            if (sizeLeft + sizeRight > getMaxWidth()) {
                int total = sizeLeft + sizeRight;
                sizeLeft = (sizeLeft * getMaxWidth()) / total;
                sizeRight = getMaxWidth() - sizeLeft;
            }
            if (sizeLeft + sizeRight < 1) { sizeLeft = 1; sizeRight = 0; }
        } else if (tag.contains(TAG_SIZE_W)) {
            int w = Math.max(MIN_SIZE, Math.min(getMaxWidth(), tag.getInt(TAG_SIZE_W)));
            sizeLeft = w / 2;
            sizeRight = w - sizeLeft;
        }
        if (tag.contains(TAG_SIZE_H)) sizeHeight = Math.max(MIN_SIZE, Math.min(getMaxHeight(), tag.getInt(TAG_SIZE_H)));
        if (tag.contains(TAG_SIZE_D)) sizeDepth = Math.max(MIN_SIZE, Math.min(getMaxDepth(), tag.getInt(TAG_SIZE_D)));
        if (tag.contains(TAG_COIL_IDX)) selectedCoilIndex = Math.max(0, Math.min(ElecCoilLoader.getCoilOptionCount() - 1, tag.getInt(TAG_COIL_IDX)));
        if (tag.contains(TAG_ROD_COIL_IDX)) {
            selectedRodCoilIndex = Math.max(0, Math.min(ElecCoilLoader.getCoilOptionCount() - 1, tag.getInt(TAG_ROD_COIL_IDX)));
        }
        if (tag.contains(TAG_SIM_GENERATION_IDX)) {
            simGenerationIndex = Math.max(0, tag.getInt(TAG_SIM_GENERATION_IDX));
        }
        if (tag.contains("CoilLayerCount")) coilLayerCount = Math.max(COIL_LAYER_MIN, Math.min(COIL_LAYER_MAX, tag.getInt("CoilLayerCount")));
        clampCoilLayerCountToFit();
        if (tag.contains(TAG_ROD_PATTERN)) {
            int raw = tag.getInt(TAG_ROD_PATTERN);
            if (tag.contains(TAG_BLADE_VARIANT)) {
                rodPattern = Math.max(0, Math.min(ROD_PATTERN_COUNT - 1, raw));
                bladeVariant = Math.max(0, Math.min(BLADE_VARIANT_COUNT - 1, tag.getInt(TAG_BLADE_VARIANT)));
            } else {
                int[] migrated = TurbineRodPatternLogic.migrateLegacyRodPattern(raw);
                rodPattern = migrated[0];
                bladeVariant = migrated[1];
            }
        }
        if (tag.contains("PlacementAxisIndex")) {
            placementAxisIndex = Math.max(0,
                    Math.min(TurbinePlacementAxis.count() - 1,
                            tag.getInt("PlacementAxisIndex")));
        } else if (tag.contains(TAG_PLACEMENT_AXIS)) {
            String raw = tag.getString(TAG_PLACEMENT_AXIS);
            TurbinePlacementAxis axis =
                    TurbinePlacementAxis.fromId(raw);
            if (axis == TurbinePlacementAxis.UP_DOWN
                    && !raw.contains("_")) {
                axis = TurbinePlacementAxis.fromLegacyDirectionName(raw);
            }
            placementAxisIndex = axis.ordinal();
        }
        if (tag.contains(TAG_OPEN_TOP)) openTop = tag.getBoolean(TAG_OPEN_TOP);
        if (tag.contains(TAG_BUILDING)) building = tag.getBoolean(TAG_BUILDING);
        if (tag.contains(TAG_INVALID_BLOCKS)) invalidBlocksDetected = tag.getBoolean(TAG_INVALID_BLOCKS);
        if (tag.contains(TAG_BUILD_PROGRESS)) buildProgressPercent = tag.getInt(TAG_BUILD_PROGRESS);
        if (tag.contains(TAG_BUILD_PROGRESS_VISIBLE)) buildProgressVisible = tag.getBoolean(TAG_BUILD_PROGRESS_VISIBLE);
        if (tag.contains(TAG_WEBSITE_DESIGN)) {
            websiteDesignCode = tag.getString(TAG_WEBSITE_DESIGN);
            invalidateWebsiteRodCache();
        }
        // Missing key: keep existing code (partial/legacy packets must not wipe a valid seed).
        if (tag.contains(TAG_BUILD_STAGE)) buildStage = tag.getInt(TAG_BUILD_STAGE);
        if (tag.contains(TAG_BUILD_FRAME_X)) buildFrameX = tag.getInt(TAG_BUILD_FRAME_X);
        if (tag.contains(TAG_BUILD_FRAME_Y)) buildFrameY = tag.getInt(TAG_BUILD_FRAME_Y);
        if (tag.contains(TAG_BUILD_FRAME_Z)) buildFrameZ = tag.getInt(TAG_BUILD_FRAME_Z);
        if (tag.contains(TAG_BUILD_RODCTRL_RX)) buildRodCtrlRx = tag.getInt(TAG_BUILD_RODCTRL_RX);
        if (tag.contains(TAG_BUILD_RODCTRL_RZ)) buildRodCtrlRz = tag.getInt(TAG_BUILD_RODCTRL_RZ);
        if (tag.contains(TAG_BUILD_ROD_LX)) buildRodLx = tag.getInt(TAG_BUILD_ROD_LX);
        if (tag.contains(TAG_BUILD_ROD_LY)) buildRodLy = tag.getInt(TAG_BUILD_ROD_LY);
        if (tag.contains(TAG_BUILD_ROD_LZ)) buildRodLz = tag.getInt(TAG_BUILD_ROD_LZ);
        if (tag.contains(TAG_BUILD_LIQUID_LX)) buildLiquidLx = tag.getInt(TAG_BUILD_LIQUID_LX);
        if (tag.contains(TAG_BUILD_LIQUID_LY)) buildLiquidLy = tag.getInt(TAG_BUILD_LIQUID_LY);
        if (tag.contains(TAG_BUILD_LIQUID_LZ)) buildLiquidLz = tag.getInt(TAG_BUILD_LIQUID_LZ);
        if (tag.contains(TAG_BUILD_HEAT_LX)) buildHeatLx = tag.getInt(TAG_BUILD_HEAT_LX);
        if (tag.contains(TAG_BUILD_HEAT_LY)) buildHeatLy = tag.getInt(TAG_BUILD_HEAT_LY);
        if (tag.contains(TAG_BUILD_HEAT_LZ)) buildHeatLz = tag.getInt(TAG_BUILD_HEAT_LZ);
        // Only apply mark-input when present so partial NBT sync does not clear filters.
        if (tag.contains(TAG_MARK_INPUT_FILTERS)) {
            CompoundTag markTag = tag.getCompound(TAG_MARK_INPUT_FILTERS);
            for (int i = 0; i < markInputFilters.size(); i++) {
                markInputFilters.set(i, ItemStack.EMPTY);
            }
            for (int slot = 0; slot < BUFFER_SLOTS; slot++) {
                String key = "slot" + slot;
                if (markTag.contains(key)) {
                    ItemStack filter = ItemStack.OPTIONAL_CODEC
                            .parse(registries.createSerializationContext(NbtOps.INSTANCE), markTag.get(key))
                            .result()
                            .orElse(ItemStack.EMPTY);
                    markInputFilters.set(slot, filter);
                }
            }
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.colossal_reactors.turbine_builder");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TurbineBuilderMenu(containerId, playerInventory, this);
    }

    /** Drops all buffer contents into the world when the block is removed. */
    public void dropAllContents() {
        if (level == null || level.isClientSide()) return;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        for (int i = 0; i < bufferHandler.getSlots(); i++) {
            var stack = bufferHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(level, center.x, center.y, center.z, stack);
                level.addFreshEntity(entity);
                bufferHandler.setStackInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
    }
}
