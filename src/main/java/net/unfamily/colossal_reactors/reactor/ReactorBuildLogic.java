package net.unfamily.colossal_reactors.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.tags.ModBlockTags;
import net.unfamily.colossal_reactors.block.ReactorBuilderBlock;
import net.unfamily.colossal_reactors.blockentity.ReactorBuilderBlockEntity;
import net.unfamily.colossal_reactors.heatsink.HeatSinkLoader;
import net.unfamily.colossal_reactors.reactor.ReactorValidation;

/**
 * Server-side reactor build: checks red zones and places blocks from builder inventory/tank.
 * Order: frame (except top if openTop), rod controllers, interior rods, interior liquids (1000mb each), interior heat sink blocks.
 * Cannot replace existing blocks. When no materials available, waits. On red zone or stop, returns to Build state.
 */
public final class ReactorBuildLogic {

    private static final int MB_PER_LIQUID_BLOCK = 1000;
    private static final int STAGE_FRAME = 0;
    private static final int STAGE_ROD_CONTROLLERS = 1;
    private static final int STAGE_RODS = 2;
    private static final int STAGE_LIQUIDS = 3;
    private static final int STAGE_HEAT_SINKS = 4;
    private static final int STAGE_DONE = 5;

    private ReactorBuildLogic() {}

    /**
     * Returns true if the reactor volume has any "red zone" (block that doesn't belong there).
     * Same logic as preview red markers: frame must be shell or rod controller on top; interior rod = rod block; interior else = air or heat sink.
     */
    public static boolean hasRedZone(ServerLevel level, ReactorBuilderBlockEntity builder) {
        if (level == null || builder == null || builder.getLevel() != level) return true;
        BlockState builderState = level.getBlockState(builder.getBlockPos());
        if (!(builderState.getBlock() instanceof ReactorBuilderBlock)) return true;
        var facing = builderState.getValue(ReactorBuilderBlock.FACING);
        var aabb = ReactorBuilderBlockEntity.getReactorVolumeAABB(
                builder.getBlockPos(), facing,
                builder.getSizeLeft(), builder.getSizeRight(),
                builder.getSizeHeight(), builder.getSizeDepth());
        int minX = (int) Math.floor(aabb.minX);
        int minY = (int) Math.floor(aabb.minY);
        int minZ = (int) Math.floor(aabb.minZ);
        int maxX = (int) Math.floor(aabb.maxX - 1e-6);
        int maxY = (int) Math.floor(aabb.maxY - 1e-6);
        int maxZ = (int) Math.floor(aabb.maxZ - 1e-6);
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;
        int rw = RodPatternLogic.rodSpaceWidth(w, builder.getPatternMode());
        int rd = RodPatternLogic.rodSpaceDepth(d, builder.getPatternMode());
        int insetXZ = RodPatternLogic.rodSpaceInsetXZ(builder.getPatternMode());
        int pattern = builder.getRodPattern();
        boolean expansionRodAtCenter = (pattern == RodPatternLogic.PATTERN_EXPANSION)
                ? RodPatternLogic.getExpansionRodAtCenterForPreview(rw, rd)
                : false;
        var registryAccess = level.registryAccess();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState blockState = level.getBlockState(new BlockPos(x, y, z));
                    int lx = x - minX, ly = y - minY, lz = z - minZ;
                    boolean onBorder = (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ);
                    boolean hasBlock = !blockState.isAir() && !blockState.canBeReplaced();

                    if (onBorder) {
                        boolean validFrame = ReactorValidation.isShellBlock(blockState)
                                || (blockState.is(ModBlocks.ROD_CONTROLLER.get()) && y == maxY && isRodControllerPos(x, z, minX, minZ, maxY, insetXZ, rw, rd, pattern, expansionRodAtCenter));
                        if (hasBlock && !validFrame) return true;
                    } else {
                        boolean isRodPos = (lx >= insetXZ && lx < w - insetXZ && ly >= 1 && ly < h - 1 && lz >= insetXZ && lz < d - insetXZ)
                                && RodPatternLogic.isRodForPreview(lx - insetXZ, ly - 1, lz - insetXZ,
                                rw, RodPatternLogic.rodSpaceHeight(h, builder.getPatternMode()), rd, pattern, expansionRodAtCenter);
                        if (isRodPos) {
                            if (hasBlock && !blockState.is(ModBlocks.REACTOR_ROD.get())) return true;
                        } else {
                            if (hasBlock && !HeatSinkLoader.isHeatSinkBlock(blockState, registryAccess)) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isRodControllerPos(int x, int z, int minX, int minZ, int maxY, int insetXZ, int rw, int rd, int pattern, boolean expansionRodAtCenter) {
        int rx = x - minX - insetXZ;
        int rz = z - minZ - insetXZ;
        if (rx < 0 || rx >= rw || rz < 0 || rz >= rd) return false;
        return RodPatternLogic.isRodColumnForPreview(rx, rz, rw, rd, pattern, expansionRodAtCenter);
    }

    /**
     * One build step. Returns true if building should continue, false if done or aborted (caller should call stopBuild).
     */
    public static boolean tick(ReactorBuilderBlockEntity builder) {
        return tick(builder, 1);
    }

    /**
     * Executes up to {@code steps} build steps in a single tick.
     * Returns true if building should continue, false if done or aborted.
     */
    public static boolean tick(ReactorBuilderBlockEntity builder, int steps) {
        int s = Math.max(1, steps);
        for (int i = 0; i < s; i++) {
            if (!tickSingle(builder)) {
                return false;
            }
        }
        return true;
    }

    private static boolean tickSingle(ReactorBuilderBlockEntity builder) {
        if (builder.getLevel() == null || builder.getLevel().isClientSide()) return false;
        ServerLevel level = (ServerLevel) builder.getLevel();
        BlockState builderState = level.getBlockState(builder.getBlockPos());
        if (!(builderState.getBlock() instanceof ReactorBuilderBlock)) return false;
        var facing = builderState.getValue(ReactorBuilderBlock.FACING);
        var aabb = ReactorBuilderBlockEntity.getReactorVolumeAABB(
                builder.getBlockPos(), facing,
                builder.getSizeLeft(), builder.getSizeRight(),
                builder.getSizeHeight(), builder.getSizeDepth());
        int minX = (int) Math.floor(aabb.minX);
        int minY = (int) Math.floor(aabb.minY);
        int minZ = (int) Math.floor(aabb.minZ);
        int maxX = (int) Math.floor(aabb.maxX - 1e-6);
        int maxY = (int) Math.floor(aabb.maxY - 1e-6);
        int maxZ = (int) Math.floor(aabb.maxZ - 1e-6);
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;
        int rw = RodPatternLogic.rodSpaceWidth(w, builder.getPatternMode());
        int rh = RodPatternLogic.rodSpaceHeight(h, builder.getPatternMode());
        int rd = RodPatternLogic.rodSpaceDepth(d, builder.getPatternMode());
        int insetXZ = RodPatternLogic.rodSpaceInsetXZ(builder.getPatternMode());
        int pattern = builder.getRodPattern();
        boolean expansionRodAtCenter = (pattern == RodPatternLogic.PATTERN_EXPANSION)
                ? RodPatternLogic.getExpansionRodAtCenterForPreview(rw, rd)
                : false;
        boolean openTop = builder.isOpenTop();

        if (hasRedZone(level, builder)) return false;

        // Forward-only build using cursors stored in the BE.
        // Each call places at most one block (or waits for materials), and never rescans from the beginning.
        for (int guard = 0; guard < 8; guard++) {
            int stage = builder.getBuildStage();
            if (stage == STAGE_FRAME) {
                if (tickFrame(builder, level, minX, minY, minZ, maxX, maxY, maxZ, insetXZ, rw, rd, pattern, expansionRodAtCenter, openTop)) return true;
                builder.setBuildStage(STAGE_ROD_CONTROLLERS);
                builder.setBuildRodCtrlCursor(Integer.MIN_VALUE, Integer.MIN_VALUE);
                builder.setChanged();
                continue;
            }
            if (stage == STAGE_ROD_CONTROLLERS) {
                if (pattern == RodPatternLogic.PATTERN_NONE || pattern == RodPatternLogic.PATTERN_CUSTOM) {
                    builder.setBuildStage(STAGE_LIQUIDS);
                    builder.setBuildLiquidCursor(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
                    builder.setChanged();
                    continue;
                }
                if (tickRodControllers(builder, level, minX, minY, minZ, maxY, insetXZ, rw, rd, pattern, expansionRodAtCenter)) return true;
                builder.setBuildStage(STAGE_RODS);
                builder.setBuildRodCursor(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
                builder.setChanged();
                continue;
            }
            if (stage == STAGE_RODS) {
                if (pattern == RodPatternLogic.PATTERN_NONE || pattern == RodPatternLogic.PATTERN_CUSTOM) {
                    builder.setBuildStage(STAGE_LIQUIDS);
                    builder.setBuildLiquidCursor(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
                    builder.setChanged();
                    continue;
                }
                if (tickRods(builder, level, minX, minY, minZ, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) return true;
                builder.setBuildStage(STAGE_LIQUIDS);
                builder.setBuildLiquidCursor(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
                builder.setChanged();
                continue;
            }
            if (stage == STAGE_LIQUIDS) {
                if (pattern == RodPatternLogic.PATTERN_NONE
                        || !HeatSinkLoader.requiresLiquidPlacement(builder.getSelectedHeatSinkIndex())) {
                    builder.setBuildStage(STAGE_HEAT_SINKS);
                    builder.setBuildHeatCursor(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
                    builder.setChanged();
                    continue;
                }
                if (tickLiquids(builder, level, minX, minY, minZ, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) return true;
                builder.setBuildStage(STAGE_HEAT_SINKS);
                builder.setBuildHeatCursor(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
                builder.setChanged();
                continue;
            }
            if (stage == STAGE_HEAT_SINKS) {
                if (pattern == RodPatternLogic.PATTERN_NONE) {
                    builder.setBuildStage(STAGE_DONE);
                    builder.setChanged();
                    return false;
                }
                if (tickHeatSinks(builder, level, minX, minY, minZ, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) return true;
                builder.setBuildStage(STAGE_DONE);
                builder.setChanged();
                return false;
            }
            return false;
        }
        return true;
    }

    /** Frame shell: complete each absolute Y layer (bottom → top) before the next. */
    private static boolean tickFrame(ReactorBuilderBlockEntity builder, ServerLevel level,
                                     int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                     int insetXZ, int rw, int rd, int pattern, boolean expansionRodAtCenter, boolean openTop) {
        int x = builder.getBuildFrameX();
        int y = builder.getBuildFrameY();
        int z = builder.getBuildFrameZ();
        if (y == Integer.MIN_VALUE) {
            y = minY;
            x = minX;
            z = minZ;
        }
        for (int yy = y; yy <= maxY; yy++) {
            int xx0 = (yy == y) ? x : minX;
            for (int xx = xx0; xx <= maxX; xx++) {
                int zz0 = (yy == y && xx == xx0) ? z : minZ;
                for (int zz = zz0; zz <= maxZ; zz++) {
                    if (yy == maxY && openTop) {
                        builder.setBuildFrameCursor(xx, yy, zz + 1);
                        continue;
                    }
                    boolean onBorder = (xx == minX || xx == maxX || yy == minY || yy == maxY || zz == minZ || zz == maxZ);
                    if (!onBorder) {
                        builder.setBuildFrameCursor(xx, yy, zz + 1);
                        continue;
                    }
                    if (yy == maxY && isRodControllerPos(xx, zz, minX, minZ, maxY, insetXZ, rw, rd, pattern, expansionRodAtCenter)) {
                        builder.setBuildFrameCursor(xx, yy, zz + 1);
                        continue; // reserve for rod controller
                    }
                    BlockPos pos = new BlockPos(xx, yy, zz);
                    if (!canReplace(level, pos)) {
                        builder.setBuildFrameCursor(xx, yy, zz + 1);
                        continue;
                    }
                    boolean edgeOrCorner = isEdgeOrCorner(xx, yy, zz, minX, minY, minZ, maxX, maxY, maxZ);
                    boolean topOrBottomFace = (yy == minY || yy == maxY);
                    boolean preferCasing = edgeOrCorner || topOrBottomFace;
                    ItemStack frame = resolveFrameStack(builder, preferCasing);
                    if (frame.isEmpty()) {
                        builder.setBuildFrameCursor(xx, yy, zz);
                        return true; // wait until any shell block is available
                    }
                    if (tryPlaceFrame(builder, level, pos, frame)) {
                        builder.setBuildFrameCursor(xx, yy, zz + 1);
                        return true;
                    }
                    builder.setBuildFrameCursor(xx, yy, zz + 1);
                }
                builder.setBuildFrameCursor(xx + 1, yy, minZ);
            }
            builder.setBuildFrameCursor(minX, yy + 1, minZ);
        }
        return false;
    }

    private static boolean tickRodControllers(ReactorBuilderBlockEntity builder, ServerLevel level,
                                              int minX, int minY, int minZ, int maxY,
                                              int insetXZ, int rw, int rd, int pattern, boolean expansionRodAtCenter) {
        int rx0 = builder.getBuildRodCtrlRx();
        int rz0 = builder.getBuildRodCtrlRz();
        if (rx0 == Integer.MIN_VALUE) {
            rx0 = 0; rz0 = 0;
        }
        int rodControllerY = maxY;
        for (int rx = rx0; rx < rw; rx++) {
            int rzStart = (rx == rx0) ? rz0 : 0;
            for (int rz = rzStart; rz < rd; rz++) {
                builder.setBuildRodCtrlCursor(rx, rz + 1);
                if (!RodPatternLogic.isRodColumnForPreview(rx, rz, rw, rd, pattern, expansionRodAtCenter)) continue;
                BlockPos pos = new BlockPos(minX + insetXZ + rx, rodControllerY, minZ + insetXZ + rz);
                if (!canReplace(level, pos)) continue;
                ItemStack rodCtrl = findRodController(builder);
                if (rodCtrl.isEmpty()) {
                    builder.setBuildRodCtrlCursor(rx, rz);
                    return true;
                }
                if (tryPlaceRodController(builder, level, pos, rodCtrl)) return true;
            }
            builder.setBuildRodCtrlCursor(rx + 1, 0);
        }
        return false;
    }

    private static boolean tickRods(ReactorBuilderBlockEntity builder, ServerLevel level,
                                    int minX, int minY, int minZ,
                                    int w, int h, int d,
                                    int insetXZ, int rw, int rh, int rd, int pattern, boolean expansionRodAtCenter) {
        int ly0 = builder.getBuildRodLy();
        int lx0 = builder.getBuildRodLx();
        int lz0 = builder.getBuildRodLz();
        if (ly0 == Integer.MIN_VALUE) {
            ly0 = 1; lx0 = insetXZ; lz0 = insetXZ;
        }
        for (int ly = ly0; ly < h - 1; ly++) {
            int lxStart = (ly == ly0) ? lx0 : insetXZ;
            for (int lx = lxStart; lx < w - insetXZ; lx++) {
                int lzStart = (ly == ly0 && lx == lxStart) ? lz0 : insetXZ;
                for (int lz = lzStart; lz < d - insetXZ; lz++) {
                    builder.setBuildRodCursor(lx, ly, lz + 1);
                    int rx = lx - insetXZ, ry = ly - 1, rz = lz - insetXZ;
                    if (!RodPatternLogic.isRodForPreview(rx, ry, rz, rw, rh, rd, pattern, expansionRodAtCenter)) continue;
                    BlockPos pos = new BlockPos(minX + lx, minY + ly, minZ + lz);
                    if (!canReplace(level, pos)) continue;
                    ItemStack rod = findRod(builder);
                    if (rod.isEmpty()) {
                        builder.setBuildRodCursor(lx, ly, lz);
                        return true;
                    }
                    if (tryPlaceRod(builder, level, pos, rod)) return true;
                }
                builder.setBuildRodCursor(lx + 1, ly, insetXZ);
            }
            builder.setBuildRodCursor(insetXZ, ly + 1, insetXZ);
        }
        return false;
    }

    private static boolean tickLiquids(ReactorBuilderBlockEntity builder, ServerLevel level,
                                       int minX, int minY, int minZ,
                                       int w, int h, int d,
                                       int insetXZ, int rw, int rh, int rd, int pattern, boolean expansionRodAtCenter) {
        int patternMode = builder.getPatternMode();

        int ly0 = builder.getBuildLiquidLy();
        int lx0 = builder.getBuildLiquidLx();
        int lz0 = builder.getBuildLiquidLz();
        if (ly0 == Integer.MIN_VALUE) {
            ly0 = 1; lx0 = 1; lz0 = 1;
        }

        for (int ly = ly0; ly < h - 1; ly++) {
            int lxStart = (ly == ly0) ? lx0 : 1;
            for (int lx = lxStart; lx < w - 1; lx++) {
                int lzStart = (ly == ly0 && lx == lxStart) ? lz0 : 1;
                for (int lz = lzStart; lz < d - 1; lz++) {
                    builder.setBuildLiquidCursor(lx, ly, lz + 1);
                    if (isInteriorCellRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) continue;
                    if (pattern != RodPatternLogic.PATTERN_CUSTOM) {
                        if (patternMode == RodPatternLogic.MODE_SUPER_ECONOMY) {
                            if (!isInRodSpace(lx, ly, lz, w, h, d, insetXZ) || !isRodSpaceCellAdjacentToRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) continue;
                        } else if (patternMode == RodPatternLogic.MODE_ECONOMY && !isInteriorCellAdjacentToRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) continue;
                    }
                    Fluid fluid = builder.getFluidTank().getFluid().getFluid();
                    if (fluid == null || fluid == Fluids.EMPTY
                            || !HeatSinkLoader.isFluidMatchingSelectedHeatSink(level.registryAccess(), builder.getSelectedHeatSinkIndex(), fluid)) {
                        builder.setBuildLiquidCursor(lx, ly, lz);
                        return true;
                    }
                    BlockPos pos = new BlockPos(minX + lx, minY + ly, minZ + lz);
                    if (!canReplaceForLiquid(level, pos, fluid)) continue;
                    BlockState liquidBlock = fluid.defaultFluidState().createLegacyBlock();
                    if (liquidBlock.isAir()) continue;
                    if (builder.getFluidTank().getFluidAmount() < MB_PER_LIQUID_BLOCK) {
                        builder.setBuildLiquidCursor(lx, ly, lz);
                        return true;
                    }
                    level.setBlock(pos, liquidBlock, 3);
                    var drained = builder.getFluidTank().drain(MB_PER_LIQUID_BLOCK, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    if (drained.getAmount() < MB_PER_LIQUID_BLOCK) {
                        builder.setBuildLiquidCursor(lx, ly, lz);
                        return true;
                    }
                    builder.setChanged();
                    return true;
                }
                builder.setBuildLiquidCursor(lx + 1, ly, 1);
            }
            builder.setBuildLiquidCursor(1, ly + 1, 1);
        }
        return false;
    }

    private static boolean tickHeatSinks(ReactorBuilderBlockEntity builder, ServerLevel level,
                                         int minX, int minY, int minZ,
                                         int w, int h, int d,
                                         int insetXZ, int rw, int rh, int rd, int pattern, boolean expansionRodAtCenter) {
        int patternMode = builder.getPatternMode();
        if (HeatSinkLoader.shouldSkipSolidHeatSinkAutoPlacement(builder.getSelectedHeatSinkIndex())) return false;

        int ly0 = builder.getBuildHeatLy();
        int lx0 = builder.getBuildHeatLx();
        int lz0 = builder.getBuildHeatLz();
        if (ly0 == Integer.MIN_VALUE) {
            ly0 = 1; lx0 = 1; lz0 = 1;
        }
        for (int ly = ly0; ly < h - 1; ly++) {
            int lxStart = (ly == ly0) ? lx0 : 1;
            for (int lx = lxStart; lx < w - 1; lx++) {
                int lzStart = (ly == ly0 && lx == lxStart) ? lz0 : 1;
                for (int lz = lzStart; lz < d - 1; lz++) {
                    builder.setBuildHeatCursor(lx, ly, lz + 1);
                    if (isInteriorCellRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) continue;
                    if (pattern != RodPatternLogic.PATTERN_CUSTOM) {
                        if (patternMode == RodPatternLogic.MODE_SUPER_ECONOMY) {
                            if (!isInRodSpace(lx, ly, lz, w, h, d, insetXZ) || !isRodSpaceCellAdjacentToRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) continue;
                        } else if (patternMode == RodPatternLogic.MODE_ECONOMY && !isInteriorCellAdjacentToRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) continue;
                    }
                    BlockPos pos = new BlockPos(minX + lx, minY + ly, minZ + lz);
                    if (!canReplaceForSolidBlock(level, pos)) continue;
                    ItemStack heatSink = findHeatSinkBlock(builder);
                    if (heatSink.isEmpty()) {
                        builder.setBuildHeatCursor(lx, ly, lz);
                        return true;
                    }
                    if (tryPlaceHeatSink(builder, level, pos, heatSink)) return true;
                }
                builder.setBuildHeatCursor(lx + 1, ly, 1);
            }
            builder.setBuildHeatCursor(1, ly + 1, 1);
        }
        return false;
    }

    /** True if (lx, ly, lz) lies inside rod space (the -2 X/Z area). */
    private static boolean isInRodSpace(int lx, int ly, int lz, int w, int h, int d, int insetXZ) {
        return lx >= insetXZ && lx < w - insetXZ && ly >= 1 && ly < h - 1 && lz >= insetXZ && lz < d - insetXZ;
    }

    /** True if (lx, ly, lz) lies inside rod space and is a rod position. Used to skip rod cells when placing coolant in full interior. */
    private static boolean isInteriorCellRod(int lx, int ly, int lz, int w, int h, int d, int insetXZ, int rw, int rh, int rd, int pattern, boolean expansionRodAtCenter) {
        if (!isInRodSpace(lx, ly, lz, w, h, d, insetXZ)) return false;
        int rx = lx - insetXZ, ry = ly - 1, rz = lz - insetXZ;
        return RodPatternLogic.isRodForPreview(rx, ry, rz, rw, rh, rd, pattern, expansionRodAtCenter);
    }

    /** True if cell (lx, ly, lz) has at least one neighbor in rod space (6 directions) that is a rod. Used for Super Economy (coolant only in rod space, only adjacent to rods). */
    private static boolean isRodSpaceCellAdjacentToRod(int lx, int ly, int lz, int w, int h, int d, int insetXZ, int rw, int rh, int rd, int pattern, boolean expansionRodAtCenter) {
        for (int dx = -1; dx <= 1; dx += 2) {
            int nx = lx + dx;
            if (nx >= insetXZ && nx < w - insetXZ && isInteriorCellRod(nx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) return true;
        }
        for (int dy = -1; dy <= 1; dy += 2) {
            int ny = ly + dy;
            if (ny >= 1 && ny < h - 1 && isInteriorCellRod(lx, ny, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) return true;
        }
        for (int dz = -1; dz <= 1; dz += 2) {
            int nz = lz + dz;
            if (nz >= insetXZ && nz < d - insetXZ && isInteriorCellRod(lx, ly, nz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) return true;
        }
        return false;
    }

    /** True if interior cell (lx, ly, lz) has at least one neighbor (6 directions) that is a rod. Used for Economy mode (coolant only on sides of rods). Neighbors evaluated in full interior. */
    private static boolean isInteriorCellAdjacentToRod(int lx, int ly, int lz, int w, int h, int d, int insetXZ, int rw, int rh, int rd, int pattern, boolean expansionRodAtCenter) {
        for (int dx = -1; dx <= 1; dx += 2) {
            int nx = lx + dx;
            if (nx >= 1 && nx < w - 1 && isInteriorCellRod(nx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) return true;
        }
        for (int dy = -1; dy <= 1; dy += 2) {
            int ny = ly + dy;
            if (ny >= 1 && ny < h - 1 && isInteriorCellRod(lx, ny, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) return true;
        }
        for (int dz = -1; dz <= 1; dz += 2) {
            int nz = lz + dz;
            if (nz >= 1 && nz < d - 1 && isInteriorCellRod(lx, ly, nz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) return true;
        }
        return false;
    }

    private static boolean canReplace(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    /**
     * True if we can place a solid (non-liquid) block at pos: air, or replaceable block that is NOT a liquid source.
     * Prevents overwriting already-placed coolant sources with heat sink blocks.
     */
    private static boolean canReplaceForSolidBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (!state.canBeReplaced()) return false;
        FluidState fluidState = state.getFluidState();
        if (fluidState.isSource()) return false;
        return true;
    }

    /**
     * True if we can place a liquid block at pos: air or replaceable non-liquid. Do not replace existing liquid source blocks.
     */
    private static boolean canReplaceForLiquid(ServerLevel level, BlockPos pos, Fluid fluidToPlace) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (!state.canBeReplaced()) return false;
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty()) return true;
        if (fluidState.isSource()) return false;
        return true;
    }

    /** True if position is on frame/cornice (edge or corner: at least 2 dimensions on boundary). */
    private static boolean isEdgeOrCorner(int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int onBoundary = 0;
        if (x == minX || x == maxX) onBoundary++;
        if (y == minY || y == maxY) onBoundary++;
        if (z == minZ || z == maxZ) onBoundary++;
        return onBoundary >= 2;
    }

    /**
     * Casing faces: casing only (wait if none in buffer). Glass faces: glass first, then casing fallback.
     */
    private static ItemStack resolveFrameStack(ReactorBuilderBlockEntity builder, boolean preferCasing) {
        if (preferCasing) {
            return findCasingBlock(builder);
        }
        ItemStack glass = findGlassBlock(builder);
        if (!glass.isEmpty()) {
            return glass;
        }
        return findCasingBlock(builder);
    }

    private static ItemStack findCasingBlock(ReactorBuilderBlockEntity builder) {
        for (int i = 0; i < builder.getBufferHandler().getSlots(); i++) {
            ItemStack stack = builder.getBufferHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                Block block = Block.byItem(stack.getItem());
                if (block != null && block.defaultBlockState().is(ModBlockTags.REACTOR_SHELL_CASINGS)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findGlassBlock(ReactorBuilderBlockEntity builder) {
        for (int i = 0; i < builder.getBufferHandler().getSlots(); i++) {
            ItemStack stack = builder.getBufferHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                Block block = Block.byItem(stack.getItem());
                if (block != null && block.defaultBlockState().is(ModBlockTags.REACTOR_SHELL_GLASSES)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean tryPlaceFrame(ReactorBuilderBlockEntity builder, ServerLevel level, BlockPos pos, ItemStack stack) {
        Block block = Block.byItem(stack.getItem());
        if (block == Blocks.AIR || !ReactorValidation.isShellBlock(block.defaultBlockState())) {
            return false;
        }
        level.setBlock(pos, block.defaultBlockState(), 3);
        consumeOne(builder, stack);
        return true;
    }

    private static ItemStack findRodController(ReactorBuilderBlockEntity builder) {
        for (int i = 0; i < builder.getBufferHandler().getSlots(); i++) {
            ItemStack stack = builder.getBufferHandler().getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == ModBlocks.ROD_CONTROLLER.get().asItem()) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean tryPlaceRodController(ReactorBuilderBlockEntity builder, ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.getItem() != ModBlocks.ROD_CONTROLLER.get().asItem()) return false;
        level.setBlock(pos, ModBlocks.ROD_CONTROLLER.get().defaultBlockState(), 3);
        consumeOne(builder, stack);
        return true;
    }

    private static ItemStack findRod(ReactorBuilderBlockEntity builder) {
        for (int i = 0; i < builder.getBufferHandler().getSlots(); i++) {
            ItemStack stack = builder.getBufferHandler().getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == ModBlocks.REACTOR_ROD.get().asItem()) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean tryPlaceRod(ReactorBuilderBlockEntity builder, ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.getItem() != ModBlocks.REACTOR_ROD.get().asItem()) return false;
        level.setBlock(pos, ModBlocks.REACTOR_ROD.get().defaultBlockState(), 3);
        consumeOne(builder, stack);
        return true;
    }

    private static ItemStack findHeatSinkBlock(ReactorBuilderBlockEntity builder) {
        int idx = builder.getSelectedHeatSinkIndex();
        if (idx <= 0) return ItemStack.EMPTY;
        if (builder.getLevel() == null) return ItemStack.EMPTY;
        var defs = HeatSinkLoader.getAllDefinitions();
        int defIdx = idx - 1;
        if (defIdx >= defs.size()) return ItemStack.EMPTY;
        var registryAccess = builder.getLevel().registryAccess();
        for (int i = 0; i < builder.getBufferHandler().getSlots(); i++) {
            ItemStack stack = builder.getBufferHandler().getStackInSlot(i);
            if (stack.isEmpty()) continue;
            Block block = Block.byItem(stack.getItem());
            if (block != Blocks.AIR && HeatSinkLoader.isBlockMatchingSelectedHeatSink(block.defaultBlockState(), idx, registryAccess))
                return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean tryPlaceHeatSink(ReactorBuilderBlockEntity builder, ServerLevel level, BlockPos pos, ItemStack stack) {
        int idx = builder.getSelectedHeatSinkIndex();
        Block block = Block.byItem(stack.getItem());
        if (block == Blocks.AIR) return false;
        if (!HeatSinkLoader.isBlockMatchingSelectedHeatSink(block.defaultBlockState(), idx, level.registryAccess())) return false;
        level.setBlock(pos, block.defaultBlockState(), 3);
        consumeOne(builder, stack);
        return true;
    }

    private static void consumeOne(ReactorBuilderBlockEntity builder, ItemStack stack) {
        stack.shrink(1);
        builder.setChanged();
    }

    /**
     * Overall build completion (0–100) from blocks already placed vs {@link ReactorBuildMaterialCounter} totals.
     * Cumulative across all stages (not per-stage cursor / bounding-box scan).
     */
    public static int computeBuildProgressPercent(ServerLevel level, ReactorBuilderBlockEntity builder) {
        if (!builder.isBuildProgressVisible()) {
            return 0;
        }
        if (builder.getBuildStage() >= STAGE_DONE) {
            return 100;
        }
        long total = estimateTotalWork(builder);
        if (total <= 0) {
            return 0;
        }
        long placed = countPlacedWork(level, builder);
        return (int) Math.min(100, (placed * 100L) / total);
    }

    private static long estimateTotalWork(ReactorBuilderBlockEntity builder) {
        ReactorBuildMaterialCounter.BuildMaterialCounts counts = ReactorBuildMaterialCounter.estimate(
                builder.getSizeLeft(), builder.getSizeRight(), builder.getSizeHeight(), builder.getSizeDepth(),
                builder.getRodPattern(), builder.getPatternMode(), builder.getSelectedHeatSinkIndex(), builder.isOpenTop());
        long total = counts.frameCasings() + counts.faceCasings() + counts.rodControllers() + counts.rods();
        int heatSinkIndex = builder.getSelectedHeatSinkIndex();
        if (HeatSinkLoader.requiresLiquidPlacement(heatSinkIndex)) {
            total += counts.heatSinkCells();
        }
        if (!HeatSinkLoader.shouldSkipSolidHeatSinkAutoPlacement(heatSinkIndex)) {
            total += counts.heatSinkCells();
        }
        return total;
    }

    private static long countPlacedWork(ServerLevel level, ReactorBuilderBlockEntity builder) {
        BlockState builderState = level.getBlockState(builder.getBlockPos());
        if (!(builderState.getBlock() instanceof ReactorBuilderBlock)) {
            return 0;
        }
        Direction facing = builderState.getValue(ReactorBuilderBlock.FACING);
        var aabb = ReactorBuilderBlockEntity.getReactorVolumeAABB(
                builder.getBlockPos(), facing,
                builder.getSizeLeft(), builder.getSizeRight(),
                builder.getSizeHeight(), builder.getSizeDepth());
        int minX = (int) Math.floor(aabb.minX);
        int minY = (int) Math.floor(aabb.minY);
        int minZ = (int) Math.floor(aabb.minZ);
        int maxX = (int) Math.floor(aabb.maxX - 1e-6);
        int maxY = (int) Math.floor(aabb.maxY - 1e-6);
        int maxZ = (int) Math.floor(aabb.maxZ - 1e-6);
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;
        int patternMode = builder.getPatternMode();
        int pattern = builder.getRodPattern();
        int rw = RodPatternLogic.rodSpaceWidth(w, patternMode);
        int rh = RodPatternLogic.rodSpaceHeight(h, patternMode);
        int rd = RodPatternLogic.rodSpaceDepth(d, patternMode);
        int insetXZ = RodPatternLogic.rodSpaceInsetXZ(patternMode);
        boolean expansionRodAtCenter = (pattern == RodPatternLogic.PATTERN_EXPANSION)
                ? RodPatternLogic.getExpansionRodAtCenterForPreview(rw, rd)
                : false;
        boolean openTop = builder.isOpenTop();
        RegistryAccess registryAccess = level.registryAccess();
        int heatSinkIndex = builder.getSelectedHeatSinkIndex();

        long placed = 0;
        int maxYLocal = h - 1;
        for (int lx = 0; lx < w; lx++) {
            for (int ly = 0; ly < h; ly++) {
                for (int lz = 0; lz < d; lz++) {
                    if (ly == maxYLocal && openTop) {
                        continue;
                    }
                    boolean onBorder = (lx == 0 || lx == w - 1 || ly == 0 || ly == h - 1 || lz == 0 || lz == d - 1);
                    if (!onBorder) {
                        continue;
                    }
                    if (ly == maxYLocal && isRodControllerLocal(lx, lz, insetXZ, rw, rd, pattern, expansionRodAtCenter)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(new BlockPos(minX + lx, minY + ly, minZ + lz));
                    if (ReactorValidation.isShellBlock(state)) {
                        placed++;
                    }
                }
            }
        }

        int rodControllerY = maxY;
        for (int rx = 0; rx < rw; rx++) {
            for (int rz = 0; rz < rd; rz++) {
                if (!RodPatternLogic.isRodColumnForPreview(rx, rz, rw, rd, pattern, expansionRodAtCenter)) {
                    continue;
                }
                BlockPos pos = new BlockPos(minX + insetXZ + rx, rodControllerY, minZ + insetXZ + rz);
                if (level.getBlockState(pos).is(ModBlocks.ROD_CONTROLLER.get())) {
                    placed++;
                }
            }
        }

        for (int ly = 1; ly < h - 1; ly++) {
            for (int lx = insetXZ; lx < w - insetXZ; lx++) {
                for (int lz = insetXZ; lz < d - insetXZ; lz++) {
                    int rx = lx - insetXZ;
                    int ry = ly - 1;
                    int rz = lz - insetXZ;
                    if (!RodPatternLogic.isRodForPreview(rx, ry, rz, rw, rh, rd, pattern, expansionRodAtCenter)) {
                        continue;
                    }
                    if (level.getBlockState(new BlockPos(minX + lx, minY + ly, minZ + lz)).is(ModBlocks.REACTOR_ROD.get())) {
                        placed++;
                    }
                }
            }
        }

        if (heatSinkIndex > 0 && pattern != RodPatternLogic.PATTERN_NONE) {
            boolean countLiquid = HeatSinkLoader.requiresLiquidPlacement(heatSinkIndex);
            boolean countSolid = !HeatSinkLoader.shouldSkipSolidHeatSinkAutoPlacement(heatSinkIndex);
            for (int ly = 1; ly < h - 1; ly++) {
                for (int lx = 1; lx < w - 1; lx++) {
                    for (int lz = 1; lz < d - 1; lz++) {
                        if (isInteriorCellRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) {
                            continue;
                        }
                        if (pattern != RodPatternLogic.PATTERN_CUSTOM) {
                            if (patternMode == RodPatternLogic.MODE_SUPER_ECONOMY) {
                                if (!isInRodSpace(lx, ly, lz, w, h, d, insetXZ)
                                        || !isRodSpaceCellAdjacentToRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) {
                                    continue;
                                }
                            } else if (patternMode == RodPatternLogic.MODE_ECONOMY
                                    && !isInteriorCellAdjacentToRod(lx, ly, lz, w, h, d, insetXZ, rw, rh, rd, pattern, expansionRodAtCenter)) {
                                continue;
                            }
                        }
                        BlockState state = level.getBlockState(new BlockPos(minX + lx, minY + ly, minZ + lz));
                        if (countLiquid && isPlacedLiquidHeatSink(state, registryAccess, heatSinkIndex)) {
                            placed++;
                        }
                        if (countSolid && isPlacedSolidHeatSink(state, registryAccess, heatSinkIndex)) {
                            placed++;
                        }
                    }
                }
            }
        }
        return placed;
    }

    private static boolean isPlacedLiquidHeatSink(BlockState state, RegistryAccess registryAccess, int heatSinkIndex) {
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty()) {
            return false;
        }
        Fluid fluid = fluidState.getType();
        return HeatSinkLoader.isFluidMatchingSelectedHeatSink(registryAccess, heatSinkIndex, fluid);
    }

    private static boolean isPlacedSolidHeatSink(BlockState state, RegistryAccess registryAccess, int heatSinkIndex) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return HeatSinkLoader.isBlockMatchingSelectedHeatSink(state, heatSinkIndex, registryAccess);
    }

    private static boolean isRodControllerLocal(int lx, int lz, int insetXZ, int rw, int rd, int pattern, boolean expansionRodAtCenter) {
        int rx = lx - insetXZ;
        int rz = lz - insetXZ;
        if (rx < 0 || rx >= rw || rz < 0 || rz >= rd) {
            return false;
        }
        return RodPatternLogic.isRodColumnForPreview(rx, rz, rw, rd, pattern, expansionRodAtCenter);
    }
}
