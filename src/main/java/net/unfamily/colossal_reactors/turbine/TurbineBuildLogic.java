package net.unfamily.colossal_reactors.turbine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.block.TurbineBuilderBlock;
import net.unfamily.colossal_reactors.block.TurbineRodBlock;
import net.unfamily.colossal_reactors.block.TurbineRodControllerBlock;
import net.unfamily.colossal_reactors.blockentity.TurbineBuilderBlockEntity;
import net.unfamily.colossal_reactors.item.ModItems;
import net.unfamily.colossal_reactors.tags.ModBlockTags;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-side turbine build: frame, closure deck, rod controller, rods, blades, optional coil blocks.
 */
public final class TurbineBuildLogic {

    public static final int STAGE_FRAME = 0;
    public static final int STAGE_CLOSURE_DECK = 1;
    public static final int STAGE_ROD_CONTROLLERS = 2;
    public static final int STAGE_RODS = 3;
    public static final int STAGE_BLADES = 4;
    public static final int STAGE_COILS = 5;
    public static final int STAGE_DONE = 6;

    private enum PlaceResult { PLACED, WAIT, SKIP }

    private TurbineBuildLogic() {}

    public static boolean hasRedZone(ServerLevel level, TurbineBuilderBlockEntity builder) {
        if (level == null || builder == null) return true;
        BuildBounds b = bounds(level, builder);
        if (b == null) return true;
        TurbineRotorLayout layout = b.layout;
        int coilStart = layout.coilStartInterior();
        BlockPos controllerPos = layout.controllerPos(layout.primaryCenter());
        for (int x = b.minX; x <= b.maxX; x++) {
            for (int y = b.minY; y <= b.maxY; y++) {
                for (int z = b.minZ; z <= b.maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState st = level.getBlockState(p);
                    boolean border = x == b.minX || x == b.maxX || y == b.minY || y == b.maxY || z == b.minZ || z == b.maxZ;
                    if (border) {
                        if (layout.isOpenEndCapWorld(x, y, z) && builder.isOpenTop() && st.isAir()) {
                            continue;
                        }
                        if (!st.isAir() && !TurbineValidation.isShellBlock(st) && !st.is(ModBlocks.TURBINE_ROD_CONTROLLER.get())) {
                            return true;
                        }
                    } else {
                        if (p.equals(controllerPos) && st.is(ModBlocks.TURBINE_ROD_CONTROLLER.get())) {
                            continue;
                        }
                        if (layout.isCoilZoneWorld(x, y, z)) {
                            if (!st.isAir() && !ElecCoilLoader.isCoilBlock(st, level.registryAccess())
                                    && !st.is(ModBlockTags.TURBINE_SHELL_CASINGS)
                                    && !st.is(ModBlockTags.TURBINE_SHELL_GLASSES)) {
                                return true;
                            }
                        } else if (!st.isAir() && !st.is(ModBlocks.TURBINE_ROD.get())
                                && !st.is(ModBlocks.TURBINE_BLADE.get())
                                && !st.is(ModBlockTags.TURBINE_SHELL_CASINGS)
                                && !st.is(ModBlockTags.TURBINE_SHELL_GLASSES)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean tick(TurbineBuilderBlockEntity builder, int steps) {
        for (int i = 0; i < Math.max(1, steps); i++) {
            if (!tickOnce(builder)) {
                return false;
            }
        }
        return builder.getBuildStage() < STAGE_DONE;
    }

    private static boolean tickOnce(TurbineBuilderBlockEntity builder) {
        ServerLevel level = (ServerLevel) builder.getLevel();
        if (level == null) return false;
        BuildBounds b = bounds(level, builder);
        if (b == null) return false;
        if (hasRedZone(level, builder)) {
            builder.stopBuild(false);
            return false;
        }
        boolean openTop = builder.isOpenTop();
        int pattern = builder.getRodPattern();
        return switch (builder.getBuildStage()) {
            case STAGE_FRAME -> tickFrame(level, builder, b, openTop);
            case STAGE_CLOSURE_DECK -> tickClosureDeck(level, builder, b);
            case STAGE_ROD_CONTROLLERS -> {
                if (skipsRotorContents(builder, pattern)) {
                    builder.setBuildStage(TurbineRodPatternLogic.skipsCoils(pattern) ? STAGE_DONE : STAGE_COILS);
                    yield builder.getBuildStage() != STAGE_DONE;
                }
                yield tickRodControllers(level, builder, b);
            }
            case STAGE_RODS -> {
                if (skipsRotorContents(builder, pattern)) {
                    builder.setBuildStage(TurbineRodPatternLogic.skipsCoils(pattern) ? STAGE_DONE : STAGE_COILS);
                    yield builder.getBuildStage() != STAGE_DONE;
                }
                yield tickRods(level, builder, b);
            }
            case STAGE_BLADES -> {
                if (skipsRotorContents(builder, pattern)) {
                    builder.setBuildStage(TurbineRodPatternLogic.skipsCoils(pattern) ? STAGE_DONE : STAGE_COILS);
                    yield builder.getBuildStage() != STAGE_DONE;
                }
                yield tickBlades(level, builder, b);
            }
            case STAGE_COILS -> {
                if (TurbineRodPatternLogic.skipsCoils(pattern)) {
                    builder.setBuildStage(STAGE_DONE);
                    yield false;
                }
                yield tickCoils(level, builder, b);
            }
            default -> {
                builder.setBuildStage(STAGE_DONE);
                yield false;
            }
        };
    }

    /** Frame shell: bottom Y layer first, then upward (same spirit as {@link net.unfamily.colossal_reactors.reactor.ReactorBuildLogic#tickRods}). */
    private static boolean tickFrame(ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b, boolean openTop) {
        int x = builder.getBuildFrameX();
        int y = builder.getBuildFrameY();
        int z = builder.getBuildFrameZ();
        if (y == Integer.MIN_VALUE) {
            y = b.minY;
            x = b.minX;
            z = b.minZ;
        }
        for (int yy = y; yy <= b.maxY; yy++) {
            int xx0 = (yy == y) ? x : b.minX;
            for (int xx = xx0; xx <= b.maxX; xx++) {
                int zz0 = (yy == y && xx == xx0) ? z : b.minZ;
                for (int zz = zz0; zz <= b.maxZ; zz++) {
                    if (b.layout.isOpenEndCapWorld(xx, yy, zz) && openTop) {
                        builder.setBuildFrameCursor(xx, yy, zz + 1);
                        continue;
                    }
                    boolean onBorder = (xx == b.minX || xx == b.maxX || yy == b.minY || yy == b.maxY || zz == b.minZ || zz == b.maxZ);
                    if (!onBorder) {
                        builder.setBuildFrameCursor(xx, yy, zz + 1);
                        continue;
                    }
                    BlockPos pos = new BlockPos(xx, yy, zz);
                    BlockState existing = level.getBlockState(pos);
                    if (!canReplaceForSolidBlock(level, pos)) {
                        builder.setBuildFrameCursor(xx, yy, zz + 1);
                        continue;
                    }
                    boolean edgeOrCorner = isEdgeOrCorner(xx, yy, zz, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
                    boolean preferCasing = edgeOrCorner
                            || b.layout.isPlacementAxisCapWorld(xx, yy, zz)
                            || b.layout.isClosureShellRingWorld(xx, yy, zz);
                    if (resolveFrameStack(builder, preferCasing).isEmpty()) {
                        builder.setBuildFrameCursor(xx, yy, zz);
                        return true;
                    }
                    if (tryPlaceFrame(level, builder, pos, preferCasing)) {
                        builder.setBuildFrameCursor(xx, yy, zz + 1);
                        return true;
                    }
                    builder.setBuildFrameCursor(xx, yy, zz + 1);
                }
                builder.setBuildFrameCursor(xx + 1, yy, b.minZ);
            }
            builder.setBuildFrameCursor(b.minX, yy + 1, b.minZ);
        }
        builder.setBuildFrameCursor(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        builder.setBuildStage(STAGE_CLOSURE_DECK);
        return true;
    }

    /** Casing deck separating rotor from coil zone; rod-controller cells stay air. */
    private static boolean tickClosureDeck(ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b) {
        TurbineRotorLayout layout = b.layout;
        int closure = layout.closureCoord();
        int x0 = builder.getBuildFrameX();
        int y0 = builder.getBuildFrameY();
        int z0 = builder.getBuildFrameZ();
        if (x0 == Integer.MIN_VALUE) {
            x0 = b.minX + 1;
            y0 = b.minY + 1;
            z0 = b.minZ + 1;
        }
        return switch (layout.growthAxis().getAxis()) {
            case Y -> tickClosureDeckY(level, builder, b, layout, closure, x0, z0);
            case Z -> tickClosureDeckZ(level, builder, b, layout, closure, x0, y0);
            case X -> tickClosureDeckX(level, builder, b, layout, closure, y0, z0);
            default -> {
                builder.setBuildStage(STAGE_ROD_CONTROLLERS);
                yield true;
            }
        };
    }

    private static boolean skipsRotorContents(TurbineBuilderBlockEntity builder, int pattern) {
        if (pattern == TurbineRodPatternLogic.PATTERN_WEBSITE) {
            return !builder.hasValidWebsiteDesign();
        }
        return TurbineRodPatternLogic.skipsRotorContents(pattern);
    }

    private static java.util.function.BiPredicate<Integer, Integer> websiteRodOverride(TurbineBuilderBlockEntity builder) {
        if (builder.getRodPattern() == TurbineRodPatternLogic.PATTERN_WEBSITE && builder.hasValidWebsiteDesign()) {
            return builder::websiteIsRodColumn;
        }
        return null;
    }

    private static boolean isPatternRodColumn(TurbineBuilderBlockEntity builder, int ca, int cb, int rw, int rd) {
        return TurbineRodPatternLogic.isRodColumn(ca, cb, rw, rd, builder.getRodPattern(), websiteRodOverride(builder));
    }

    private static boolean isPatternRodControllerCell(
            TurbineBuilderBlockEntity builder, TurbineRotorLayout layout, int pattern, int x, int y, int z) {
        if (skipsRotorContents(builder, pattern)) {
            return false;
        }
        int ca = layout.crossAFromWorld(x, y, z);
        int cb = layout.crossBFromWorld(x, y, z);
        return isPatternRodColumn(builder, ca, cb, layout.crossSizeA(), layout.crossSizeB());
    }

    private static boolean tickClosureDeckY(
            ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b,
            TurbineRotorLayout layout, int closure, int x0, int z0) {
        int pattern = builder.getRodPattern();
        for (int xx = x0; xx < b.maxX; xx++) {
            int zz0 = (xx == x0) ? z0 : b.minZ + 1;
            for (int zz = zz0; zz < b.maxZ; zz++) {
                builder.setBuildFrameCursor(xx, closure, zz + 1);
                if (isPatternRodControllerCell(builder, layout, pattern, xx, closure, zz)) {
                    continue;
                }
                if (tryPlaceClosureCasing(level, builder, new BlockPos(xx, closure, zz))) {
                    return true;
                }
            }
            builder.setBuildFrameCursor(xx + 1, closure, b.minZ + 1);
        }
        finishClosureDeck(builder);
        return true;
    }

    private static boolean tickClosureDeckZ(
            ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b,
            TurbineRotorLayout layout, int closure, int x0, int y0) {
        int pattern = builder.getRodPattern();
        for (int xx = x0; xx < b.maxX; xx++) {
            int yy0 = (xx == x0) ? y0 : b.minY + 1;
            for (int yy = yy0; yy < b.maxY; yy++) {
                builder.setBuildFrameCursor(xx, yy, closure + 1);
                if (isPatternRodControllerCell(builder, layout, pattern, xx, yy, closure)) {
                    continue;
                }
                if (tryPlaceClosureCasing(level, builder, new BlockPos(xx, yy, closure))) {
                    return true;
                }
            }
            builder.setBuildFrameCursor(xx + 1, b.minY + 1, closure);
        }
        finishClosureDeck(builder);
        return true;
    }

    private static boolean tickClosureDeckX(
            ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b,
            TurbineRotorLayout layout, int closure, int y0, int z0) {
        int pattern = builder.getRodPattern();
        for (int yy = y0; yy < b.maxY; yy++) {
            int zz0 = (yy == y0) ? z0 : b.minZ + 1;
            for (int zz = zz0; zz < b.maxZ; zz++) {
                builder.setBuildFrameCursor(closure, yy, zz + 1);
                if (isPatternRodControllerCell(builder, layout, pattern, closure, yy, zz)) {
                    continue;
                }
                if (tryPlaceClosureCasing(level, builder, new BlockPos(closure, yy, zz))) {
                    return true;
                }
            }
            builder.setBuildFrameCursor(closure, yy + 1, b.minZ + 1);
        }
        finishClosureDeck(builder);
        return true;
    }

    private static boolean tryPlaceClosureCasing(ServerLevel level, TurbineBuilderBlockEntity builder, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.is(ModBlockTags.TURBINE_SHELL_CASINGS) || !canReplaceForSolidBlock(level, pos)) {
            return false;
        }
        if (resolveFrameStack(builder, true).isEmpty()) {
            builder.setBuildFrameCursor(pos.getX(), pos.getY(), pos.getZ());
            return true;
        }
        return tryPlaceFrame(level, builder, pos, true);
    }

    private static void finishClosureDeck(TurbineBuilderBlockEntity builder) {
        builder.setBuildFrameCursor(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        int pattern = builder.getRodPattern();
        if (skipsRotorContents(builder, pattern)) {
            builder.setBuildStage(TurbineRodPatternLogic.skipsCoils(pattern) ? STAGE_DONE : STAGE_COILS);
        } else {
            builder.setBuildStage(STAGE_ROD_CONTROLLERS);
        }
    }

    private static boolean tickRodControllers(ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b) {
        TurbineRotorLayout layout = b.layout;
        if (layout.crossSizeA() <= 0 || layout.crossSizeB() <= 0) {
            builder.setBuildStage(STAGE_RODS);
            return true;
        }
        List<TurbineRodControllerLayout.Center> columns = TurbineRodPatternLogic.rodColumns(
                layout.crossSizeA(), layout.crossSizeB(), builder.getRodPattern(), websiteRodOverride(builder));
        if (columns.isEmpty()) {
            builder.setBuildStage(STAGE_RODS);
            return true;
        }
        int startIndex = 0;
        if (builder.getBuildRodCtrlRx() != Integer.MIN_VALUE) {
            for (int i = 0; i < columns.size(); i++) {
                TurbineRodControllerLayout.Center c = columns.get(i);
                if (c.rx() == builder.getBuildRodCtrlRx() && c.rz() == builder.getBuildRodCtrlRz()) {
                    startIndex = i;
                    break;
                }
            }
        }
        Direction axis = layout.growthAxis();
        BlockState desired = ModBlocks.TURBINE_ROD_CONTROLLER.get().defaultBlockState()
                .setValue(TurbineRodControllerBlock.FACING, axis);
        for (int i = startIndex; i < columns.size(); i++) {
            TurbineRodControllerLayout.Center center = columns.get(i);
            BlockPos pos = layout.controllerPos(center);
            builder.setBuildRodCtrlCursor(center.rx(), center.rz());
            BlockState current = level.getBlockState(pos);
            if (current.is(ModBlocks.TURBINE_ROD_CONTROLLER.get())
                    && current.hasProperty(TurbineRodControllerBlock.FACING)
                    && current.getValue(TurbineRodControllerBlock.FACING) == axis) {
                continue;
            }
            PlaceResult result = tryPlaceState(level, builder, pos, desired);
            if (result == PlaceResult.WAIT) {
                return true;
            }
            if (result == PlaceResult.PLACED) {
                return true;
            }
        }
        builder.setBuildRodCtrlCursor(Integer.MIN_VALUE, Integer.MIN_VALUE);
        builder.setBuildStage(STAGE_RODS);
        return true;
    }

    /** Rods along placement axis, then cross-section scan (matches reactor rod tick order). */
    private static boolean tickRods(ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b) {
        TurbineRotorLayout layout = b.layout;
        Direction axis = layout.growthAxis();
        int t0 = builder.getBuildRodLy();
        int ca0 = builder.getBuildRodLx();
        int cb0 = builder.getBuildRodLz();
        if (t0 == Integer.MIN_VALUE) {
            t0 = 0;
            ca0 = 0;
            cb0 = 0;
        }
        for (int t = t0; t < layout.rodExtent(); t++) {
            int caStart = (t == t0) ? ca0 : 0;
            for (int ca = caStart; ca < layout.crossSizeA(); ca++) {
                int cbStart = (t == t0 && ca == caStart) ? cb0 : 0;
                for (int cb = cbStart; cb < layout.crossSizeB(); cb++) {
                    builder.setBuildRodCursor(ca, t, cb + 1);
                    if (!isPatternRodColumn(builder, ca, cb, layout.crossSizeA(), layout.crossSizeB())) {
                        continue;
                    }
                    BlockPos pos = layout.rodPos(t, ca, cb);
                    BlockState existing = level.getBlockState(pos);
                    if (existing.is(ModBlocks.TURBINE_ROD.get())
                            && existing.hasProperty(TurbineRodBlock.FACING)
                            && existing.getValue(TurbineRodBlock.FACING) == axis) {
                        continue;
                    }
                    BlockState rod = ModBlocks.TURBINE_ROD.get().defaultBlockState()
                            .setValue(TurbineRodBlock.FACING, axis);
                    PlaceResult result = tryPlaceState(level, builder, pos, rod);
                    if (result == PlaceResult.WAIT) {
                        builder.setBuildRodCursor(ca, t, cb);
                        return true;
                    }
                    if (result == PlaceResult.PLACED) {
                        return true;
                    }
                }
                builder.setBuildRodCursor(ca + 1, t, 0);
            }
            builder.setBuildRodCursor(0, t + 1, 0);
        }
        builder.setBuildRodCursor(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        builder.setBuildStage(STAGE_BLADES);
        return true;
    }

    private static boolean tickBlades(ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b) {
        TurbineRotorLayout layout = b.layout;
        int t0 = builder.getBuildRodLy();
        int ca0 = builder.getBuildRodLx();
        int cb0 = builder.getBuildRodLz();
        if (t0 == Integer.MIN_VALUE) {
            t0 = 0;
            ca0 = 0;
            cb0 = 0;
        }
        for (int t = t0; t < layout.rodExtent(); t++) {
            int caStart = (t == t0) ? ca0 : 0;
            for (int ca = caStart; ca < layout.crossSizeA(); ca++) {
                int cbStart = (t == t0 && ca == caStart) ? cb0 : 0;
                for (int cb = cbStart; cb < layout.crossSizeB(); cb++) {
                    builder.setBuildRodCursor(ca, t, cb + 1);
                    if (!isPatternRodColumn(builder, ca, cb, layout.crossSizeA(), layout.crossSizeB())) {
                        continue;
                    }
                    BlockPos rodPos = layout.rodPos(t, ca, cb);
                    BlockState rodState = level.getBlockState(rodPos);
                    if (!rodState.is(ModBlocks.TURBINE_ROD.get())) {
                        continue;
                    }
                    Direction axis = rodState.getValue(TurbineRodBlock.FACING);
                    int bladePattern = TurbineRodPatternLogic.bladeGrowthPattern(
                            builder.getRodPattern(), builder.isWebsiteClimbProfile());
                    int targetRing = TurbineBladePlacement.effectiveTargetBladeRing(
                            layout, t, bladePattern, ca, cb);
                    if (placeBladesToRing(level, builder, rodPos, rodState, axis, targetRing)) {
                        builder.setBuildRodCursor(ca, t, cb);
                        return true;
                    }
                }
                builder.setBuildRodCursor(ca + 1, t, 0);
            }
            builder.setBuildRodCursor(0, t + 1, 0);
        }
        builder.setBuildRodCursor(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        builder.setBuildStage(STAGE_COILS);
        return true;
    }

    /** Flat index of coil-zone cells (minX→maxX, minY→maxY, minZ→maxZ). Stored in {@code buildRodLx}. */
    private static int countCoilZoneCells(BuildBounds b) {
        int count = 0;
        TurbineRotorLayout layout = b.layout;
        for (int xx = b.minX + 1; xx < b.maxX; xx++) {
            for (int yy = b.minY + 1; yy < b.maxY; yy++) {
                for (int zz = b.minZ + 1; zz < b.maxZ; zz++) {
                    if (layout.isCoilZoneWorld(xx, yy, zz)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Nullable
    private static BlockPos coilZoneCellAt(BuildBounds b, int cellIndex) {
        int i = 0;
        TurbineRotorLayout layout = b.layout;
        for (int xx = b.minX + 1; xx < b.maxX; xx++) {
            for (int yy = b.minY + 1; yy < b.maxY; yy++) {
                for (int zz = b.minZ + 1; zz < b.maxZ; zz++) {
                    if (!layout.isCoilZoneWorld(xx, yy, zz)) {
                        continue;
                    }
                    if (i == cellIndex) {
                        return new BlockPos(xx, yy, zz);
                    }
                    i++;
                }
            }
        }
        return null;
    }

    /**
     * @return {@code true} to stall one tick (placed a blade); {@code false} when this rod needs no more work or cannot progress
     */
    private static boolean placeBladesToRing(ServerLevel level, TurbineBuilderBlockEntity builder,
                                             BlockPos rodPos, BlockState rodState, Direction axis, int targetRing) {
        if (targetRing <= 0) {
            return false;
        }
        int targetBlades = targetRing * 4;
        int onRod = TurbineBladePlacement.totalBladesOnRod(level, rodPos, axis);
        if (onRod >= targetBlades) {
            return false;
        }
        if (!hasItemInBuffer(builder, ModItems.TURBINE_BLADE.get())) {
            return true;
        }
        if (!TurbineBladePlacement.placeNextBlade(level, rodPos, rodState)) {
            // Obstructed or no valid slot — skip this rod layer without consuming buffer items.
            return false;
        }
        consumeItem(level, builder, ModItems.TURBINE_BLADE.get());
        return true;
    }

    private static boolean hasItemInBuffer(TurbineBuilderBlockEntity builder, net.minecraft.world.item.Item item) {
        var handler = builder.getBufferHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty() && s.is(item)) {
                return true;
            }
        }
        return false;
    }

    /** Coils: last build stage — fill entire coil zone from buffer (supports tag selectors). */
    private static boolean tickCoils(ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b) {
        TurbineRotorLayout layout = b.layout;
        int idx = builder.getSelectedCoilIndex();
        if (ElecCoilLoader.shouldSkipSolidCoilAutoPlacement(idx)) {
            builder.setBuildStage(STAGE_DONE);
            return false;
        }
        if (!hasUnfilledCoilCells(level, b, idx)) {
            builder.setBuildStage(STAGE_DONE);
            return false;
        }
        BlockState coilState = ElecCoilLoader.placementStateForOption(idx, level.registryAccess());
        if (coilState == null) {
            return true;
        }

        int totalCells = countCoilZoneCells(b);
        int cellIndex = builder.getBuildRodLx();
        if (cellIndex == Integer.MIN_VALUE || cellIndex < 0) {
            cellIndex = 0;
        }

        for (; cellIndex < totalCells; cellIndex++) {
            BlockPos pos = coilZoneCellAt(b, cellIndex);
            if (pos == null) {
                break;
            }
            BlockState existing = level.getBlockState(pos);
            if (ElecCoilLoader.isBlockMatchingSelectedCoil(existing, idx, level.registryAccess())) {
                continue;
            }
            if (!canReplaceForSolidBlock(level, pos)) {
                continue;
            }
            if (!consumeCoilFromBuffer(builder, idx, level)) {
                builder.setBuildRodCursor(cellIndex, 0, 0);
                return true;
            }
            level.setBlock(pos, coilState, net.minecraft.world.level.block.Block.UPDATE_ALL);
            builder.setBuildRodCursor(cellIndex + 1, 0, 0);
            return true;
        }

        if (hasUnfilledCoilCells(level, b, idx)) {
            builder.setBuildRodCursor(0, 0, 0);
            return true;
        }
        builder.setBuildStage(STAGE_DONE);
        return false;
    }

    /**
     * Coil zone cells that still need the selected coil (empty or replaceable non-match).
     * Used so the builder waits for buffer items instead of marking 100% after a partial scan.
     */
    public static boolean hasUnfilledCoilCells(ServerLevel level, BuildBounds b, int coilIndex) {
        if (ElecCoilLoader.shouldSkipSolidCoilAutoPlacement(coilIndex)) {
            return false;
        }
        TurbineRotorLayout layout = b.layout;
        for (int xx = b.minX + 1; xx < b.maxX; xx++) {
            for (int yy = b.minY + 1; yy < b.maxY; yy++) {
                for (int zz = b.minZ + 1; zz < b.maxZ; zz++) {
                    if (!layout.isCoilZoneWorld(xx, yy, zz)) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(xx, yy, zz);
                    BlockState existing = level.getBlockState(pos);
                    if (ElecCoilLoader.isBlockMatchingSelectedCoil(existing, coilIndex, level.registryAccess())) {
                        continue;
                    }
                    if (!canReplaceForSolidBlock(level, pos)) {
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** Coil zone cells already matching the selected coil type. */
    public static int countMatchingCoilCells(ServerLevel level, BuildBounds b, int coilIndex) {
        if (ElecCoilLoader.shouldSkipSolidCoilAutoPlacement(coilIndex)) {
            return 0;
        }
        int count = 0;
        TurbineRotorLayout layout = b.layout;
        for (int xx = b.minX + 1; xx < b.maxX; xx++) {
            for (int yy = b.minY + 1; yy < b.maxY; yy++) {
                for (int zz = b.minZ + 1; zz < b.maxZ; zz++) {
                    if (!layout.isCoilZoneWorld(xx, yy, zz)) {
                        continue;
                    }
                    if (ElecCoilLoader.isBlockMatchingSelectedCoil(
                            level.getBlockState(new BlockPos(xx, yy, zz)), coilIndex, level.registryAccess())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static boolean consumeCoilFromBuffer(
            TurbineBuilderBlockEntity builder, int coilIndex, ServerLevel level) {
        var handler = builder.getBufferHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()
                    && ElecCoilLoader.itemMatchesSelectedCoil(stack, coilIndex, level.registryAccess())) {
                handler.extractItem(i, 1, false);
                return true;
            }
        }
        return false;
    }

    private static boolean isOnClosurePlane(int x, int y, int z, TurbineRotorLayout layout) {
        return switch (layout.growthAxis().getAxis()) {
            case Y -> y == layout.closureCoord();
            case Z -> z == layout.closureCoord();
            case X -> x == layout.closureCoord();
            default -> false;
        };
    }

    private static boolean isEdgeOrCorner(int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int onBoundary = 0;
        if (x == minX || x == maxX) onBoundary++;
        if (y == minY || y == maxY) onBoundary++;
        if (z == minZ || z == maxZ) onBoundary++;
        return onBoundary >= 2;
    }

    private static ItemStack findCasingItem(TurbineBuilderBlockEntity builder) {
        for (int i = 0; i < builder.getBufferHandler().getSlots(); i++) {
            ItemStack stack = builder.getBufferHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                Block block = Block.byItem(stack.getItem());
                if (block != null && block.defaultBlockState().is(ModBlockTags.TURBINE_SHELL_CASINGS)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findGlassItem(TurbineBuilderBlockEntity builder) {
        for (int i = 0; i < builder.getBufferHandler().getSlots(); i++) {
            ItemStack stack = builder.getBufferHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                Block block = Block.byItem(stack.getItem());
                if (block != null && block.defaultBlockState().is(ModBlockTags.TURBINE_SHELL_GLASSES)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Casing faces: casing only (wait if none in buffer). Glass faces: glass first, then casing fallback.
     */
    private static ItemStack resolveFrameStack(TurbineBuilderBlockEntity builder, boolean preferCasing) {
        if (preferCasing) {
            return findCasingItem(builder);
        }
        ItemStack glass = findGlassItem(builder);
        if (!glass.isEmpty()) {
            return glass;
        }
        return findCasingItem(builder);
    }

    /** Consumes shell item from buffer and places the resolved block state. */
    private static boolean tryPlaceFrame(ServerLevel level, TurbineBuilderBlockEntity builder, BlockPos pos, boolean preferCasing) {
        ItemStack stack = resolveFrameStack(builder, preferCasing);
        if (stack.isEmpty() || !consumeOne(builder, stack.getItem())) {
            return false;
        }
        Block block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
        return level.setBlock(pos, block.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    private static PlaceResult tryPlaceState(ServerLevel level, TurbineBuilderBlockEntity builder, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        if (!canReplaceForSolidBlock(level, pos)) {
            return PlaceResult.SKIP;
        }
        if (!consumeForBlock(builder, state.getBlock())) {
            return PlaceResult.WAIT;
        }
        level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
        return PlaceResult.PLACED;
    }

    private static boolean canReplaceForSolidBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (!state.canBeReplaced()) {
            return false;
        }
        FluidState fluidState = state.getFluidState();
        if (fluidState.isSource()) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("unused")
    private static boolean canReplaceForLiquid(ServerLevel level, BlockPos pos, Fluid fluidToPlace) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (!state.canBeReplaced()) {
            return false;
        }
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty()) {
            return true;
        }
        if (fluidState.isSource()) {
            return false;
        }
        return true;
    }

    private static boolean consumeForBlock(TurbineBuilderBlockEntity builder, Block block) {
        ServerLevel level = (ServerLevel) builder.getLevel();
        if (level == null) return false;
        if (block == ModBlocks.TURBINE_BLADE.get()) {
            return consumeItem(level, builder, ModItems.TURBINE_BLADE.get());
        }
        ItemStack stack = new ItemStack(block);
        return consumeItem(level, builder, stack.getItem());
    }

    private static boolean consumeOne(TurbineBuilderBlockEntity builder, net.minecraft.world.item.Item item) {
        ServerLevel level = (ServerLevel) builder.getLevel();
        if (level == null) return false;
        return consumeItem(level, builder, item);
    }

    private static boolean consumeItem(ServerLevel level, TurbineBuilderBlockEntity builder, net.minecraft.world.item.Item item) {
        var handler = builder.getBufferHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty() && s.is(item)) {
                handler.extractItem(i, 1, false);
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static BuildBounds bounds(ServerLevel level, TurbineBuilderBlockEntity builder) {
        BlockState st = level.getBlockState(builder.getBlockPos());
        if (!(st.getBlock() instanceof TurbineBuilderBlock)) return null;
        Direction facing = st.getValue(TurbineBuilderBlock.FACING);
        var aabb = TurbineBuilderBlockEntity.getTurbineVolumeAABB(
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
        int interiorH = TurbineRodSpaceLayout.interiorHeight(h);
        int coilLayers = builder.getAppliedCoilLayerCount();
        Direction growthAxis = builder.getPlacementAxis();
        TurbineRotorLayout layout = TurbineRotorLayout.from(
                minX, minY, minZ, maxX, maxY, maxZ, w, h, d, coilLayers, growthAxis);
        int closureWorldY = growthAxis.getAxis() == Direction.Axis.Y
                ? layout.closureCoord()
                : TurbineRodControllerLayout.closureWorldY(minY, interiorH, coilLayers);
        return new BuildBounds(minX, minY, minZ, maxX, maxY, maxZ, w, h, d,
                TurbineRodPatternLogic.rodSpaceWidth(w),
                TurbineRodPatternLogic.rodSpaceHeight(h, coilLayers),
                TurbineRodPatternLogic.rodSpaceDepth(d),
                interiorH, coilLayers, closureWorldY, layout);
    }

    public record BuildBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                               int w, int h, int d, int rw, int rh, int rd, int interiorH,
                               int coilLayers, int closureWorldY, TurbineRotorLayout layout) {}

    /**
     * Overall build completion (0–100) from placed blocks vs {@link TurbineBuildMaterialCounter} totals.
     * Cumulative across all stages (not per-stage cursor / full bounding-box scan).
     */
    public static int computeBuildProgressPercent(ServerLevel level, TurbineBuilderBlockEntity builder) {
        if (!builder.isBuildProgressVisible()) {
            return 0;
        }
        if (builder.getBuildStage() >= STAGE_DONE) {
            return 100;
        }
        BuildBounds bounds = bounds(level, builder);
        if (bounds == null) {
            return 0;
        }
        long total = estimateTotalWork(level, builder);
        if (total <= 0) {
            return 0;
        }
        long placed = countPlacedWork(level, builder, bounds);
        return (int) Math.min(100, (placed * 100L) / total);
    }

    private static long estimateTotalWork(ServerLevel level, TurbineBuilderBlockEntity builder) {
        TurbineBuildMaterialCounter.BuildMaterialCounts counts = TurbineBuildMaterialCounter.estimate(
                level.registryAccess(),
                builder.getSizeLeft(), builder.getSizeRight(), builder.getSizeHeight(), builder.getSizeDepth(),
                builder.getPlacementAxisIndex(),
                builder.getRodPattern(), builder.getSelectedCoilIndex(), builder.getAppliedCoilLayerCount(),
                builder.isOpenTop());
        return counts.frameShellTotal()
                + counts.closureDeckCasings()
                + counts.rodControllers()
                + counts.rods()
                + counts.blades()
                + counts.coilBlocks();
    }

    private static long countPlacedWork(ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b) {
        int pattern = builder.getRodPattern();
        return countPlacedFrameShell(level, builder, b)
                + countPlacedClosureDeck(level, builder, b, b.layout, pattern)
                + countPlacedRodController(level, builder, b, b.layout, pattern)
                + countPlacedRods(level, builder, b)
                + countPlacedBlades(level, builder, b)
                + countMatchingCoilCells(level, b, builder.getSelectedCoilIndex());
    }

    private static int countPlacedFrameShell(ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b) {
        TurbinePlacementAxis placement = TurbinePlacementAxis.fromIndex(builder.getPlacementAxisIndex());
        boolean openTop = builder.isOpenTop();
        int maxY = b.h - 1;
        int count = 0;
        for (int lx = 0; lx < b.w; lx++) {
            for (int ly = 0; ly < b.h; ly++) {
                for (int lz = 0; lz < b.d; lz++) {
                    if (placement.isOpenEndCapLocal(lx, ly, lz, b.w, b.h, b.d) && openTop) {
                        continue;
                    }
                    boolean onBorder = (lx == 0 || lx == b.w - 1 || ly == 0 || ly == maxY || lz == 0 || lz == b.d - 1);
                    if (!onBorder) {
                        continue;
                    }
                    BlockState state = level.getBlockState(new BlockPos(b.minX + lx, b.minY + ly, b.minZ + lz));
                    if (TurbineValidation.isShellBlock(state)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int countPlacedClosureDeck(ServerLevel level, TurbineBuilderBlockEntity builder,
                                               BuildBounds b, TurbineRotorLayout layout, int pattern) {
        int closure = layout.closureCoord();
        int count = 0;
        switch (layout.growthAxis().getAxis()) {
            case Y -> {
                for (int xx = b.minX + 1; xx < b.maxX; xx++) {
                    for (int zz = b.minZ + 1; zz < b.maxZ; zz++) {
                        if (isPatternRodControllerCell(builder, layout, pattern, xx, closure, zz)) {
                            continue;
                        }
                        if (isClosureDeckPlaced(level, new BlockPos(xx, closure, zz))) {
                            count++;
                        }
                    }
                }
            }
            case Z -> {
                for (int xx = b.minX + 1; xx < b.maxX; xx++) {
                    for (int yy = b.minY + 1; yy < b.maxY; yy++) {
                        if (isPatternRodControllerCell(builder, layout, pattern, xx, yy, closure)) {
                            continue;
                        }
                        if (isClosureDeckPlaced(level, new BlockPos(xx, yy, closure))) {
                            count++;
                        }
                    }
                }
            }
            case X -> {
                for (int yy = b.minY + 1; yy < b.maxY; yy++) {
                    for (int zz = b.minZ + 1; zz < b.maxZ; zz++) {
                        if (isPatternRodControllerCell(builder, layout, pattern, closure, yy, zz)) {
                            continue;
                        }
                        if (isClosureDeckPlaced(level, new BlockPos(closure, yy, zz))) {
                            count++;
                        }
                    }
                }
            }
            default -> { }
        }
        return count;
    }

    private static boolean isClosureDeckPlaced(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlockTags.TURBINE_SHELL_CASINGS) || TurbineValidation.isShellBlock(state);
    }

    private static int countPlacedRodController(ServerLevel level, TurbineBuilderBlockEntity builder,
                                                 BuildBounds b, TurbineRotorLayout layout, int pattern) {
        if (layout.crossSizeA() <= 0 || layout.crossSizeB() <= 0) {
            return 0;
        }
        int count = 0;
        Direction axis = layout.growthAxis();
        for (TurbineRodControllerLayout.Center center : TurbineRodPatternLogic.rodColumns(
                layout.crossSizeA(), layout.crossSizeB(), pattern, websiteRodOverride(builder))) {
            BlockPos pos = layout.controllerPos(center);
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModBlocks.TURBINE_ROD_CONTROLLER.get())) {
                continue;
            }
            if (state.hasProperty(TurbineRodControllerBlock.FACING)
                    && state.getValue(TurbineRodControllerBlock.FACING) == axis) {
                count++;
            }
        }
        return count;
    }

    private static int countPlacedRods(ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b) {
        TurbineRotorLayout layout = b.layout;
        int count = 0;
        for (int t = 0; t < layout.rodExtent(); t++) {
            for (int ca = 0; ca < layout.crossSizeA(); ca++) {
                for (int cb = 0; cb < layout.crossSizeB(); cb++) {
                    if (!isPatternRodColumn(builder, ca, cb, layout.crossSizeA(), layout.crossSizeB())) {
                        continue;
                    }
                    if (level.getBlockState(layout.rodPos(t, ca, cb)).is(ModBlocks.TURBINE_ROD.get())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int countPlacedBlades(ServerLevel level, TurbineBuilderBlockEntity builder, BuildBounds b) {
        TurbineRotorLayout layout = b.layout;
        int count = 0;
        for (int t = 0; t < layout.rodExtent(); t++) {
            for (int ca = 0; ca < layout.crossSizeA(); ca++) {
                for (int cb = 0; cb < layout.crossSizeB(); cb++) {
                    if (!isPatternRodColumn(builder, ca, cb, layout.crossSizeA(), layout.crossSizeB())) {
                        continue;
                    }
                    BlockPos rodPos = layout.rodPos(t, ca, cb);
                    BlockState rodState = level.getBlockState(rodPos);
                    if (!rodState.is(ModBlocks.TURBINE_ROD.get()) || !rodState.hasProperty(TurbineRodBlock.FACING)) {
                        continue;
                    }
                    Direction axis = rodState.getValue(TurbineRodBlock.FACING);
                    count += TurbineBladePlacement.totalBladesOnRod(level, rodPos, axis);
                }
            }
        }
        return count;
    }
}
