package net.unfamily.colossal_reactors.turbine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.item.ModItems;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.block.TurbineBladeBlock;
import net.unfamily.colossal_reactors.block.TurbineRodBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Balanced ring growth of turbine blades on a rod; max radius from {@link Config#MAX_TURBINE_BLADE_RING}. */
public final class TurbineBladePlacement {

    private TurbineBladePlacement() {}

    public static int maxRing() {
        return Config.MAX_TURBINE_BLADE_RING.get();
    }

    public static int maxBladesPerRod() {
        return maxRing() * 4;
    }

    /**
     * Max 1-based blade ring index that fits in the rod cross-section before the interior shell.
     * Does not shrink the interior; caps radial extent from this rod column index.
     */
    public static int maxRingForRodColumn(int crossA, int crossB, int crossSizeA, int crossSizeB) {
        return maxRingForRodColumn(crossA, crossB, crossSizeA, crossSizeB, Collections.emptyList());
    }

    /**
     * Shell clearance, further capped so blade rings stop one cell before neighboring rod columns
     * (Chebyshev distance − 1). Matches ModDecoded multi-rod blade packing.
     */
    public static int maxRingForRodColumn(
            int crossA, int crossB, int crossSizeA, int crossSizeB,
            Collection<TurbineRodControllerLayout.Center> allColumns) {
        if (crossSizeA <= 0 || crossSizeB <= 0) {
            return 0;
        }
        int clampedA = Math.max(0, Math.min(crossA, crossSizeA - 1));
        int clampedB = Math.max(0, Math.min(crossB, crossSizeB - 1));
        int shell = Math.max(0, Math.min(
                Math.min(clampedA, crossSizeA - 1 - clampedA),
                Math.min(clampedB, crossSizeB - 1 - clampedB)));
        if (allColumns == null || allColumns.isEmpty()) {
            return shell;
        }
        int toNeighbor = Integer.MAX_VALUE;
        for (TurbineRodControllerLayout.Center other : allColumns) {
            if (other.rx() == crossA && other.rz() == crossB) {
                continue;
            }
            int cheb = Math.max(Math.abs(other.rx() - crossA), Math.abs(other.rz() - crossB));
            toNeighbor = Math.min(toNeighbor, cheb - 1);
        }
        if (toNeighbor == Integer.MAX_VALUE) {
            return shell;
        }
        return Math.min(shell, Math.max(0, toNeighbor));
    }

    /**
     * Pattern/config target ring clamped to what fits for the primary rod column.
     */
    public static int effectiveTargetBladeRing(TurbineRotorLayout layout, int layerIndex, int rodPattern) {
        TurbineRodControllerLayout.Center center = layout.primaryCenter();
        return effectiveTargetBladeRing(
                layout, layerIndex, rodPattern, center.rx(), center.rz(),
                Collections.emptyList(), Integer.MAX_VALUE);
    }

    /** Pattern/config target ring clamped to clearance around column {@code (crossA, crossB)}. */
    public static int effectiveTargetBladeRing(
            TurbineRotorLayout layout, int layerIndex, int rodPattern, int crossA, int crossB) {
        return effectiveTargetBladeRing(
                layout, layerIndex, rodPattern, crossA, crossB,
                Collections.emptyList(), Integer.MAX_VALUE);
    }

    /**
     * Pattern/config target ring clamped by shell + neighbor-rod clearance and an optional design ring cap
     * ({@code mdct1} {@code ring} field; pass {@link Integer#MAX_VALUE} when unused).
     */
    public static int effectiveTargetBladeRing(
            TurbineRotorLayout layout, int layerIndex, int rodPattern, int crossA, int crossB,
            Collection<TurbineRodControllerLayout.Center> allColumns, int designRingCap) {
        int structural = maxRingForRodColumn(
                crossA, crossB, layout.crossSizeA(), layout.crossSizeB(), allColumns);
        int patternRing = TurbineRodPatternLogic.targetBladeRingForLayer(
                layerIndex, layout.rodExtent(), rodPattern);
        int ringCap = designRingCap < 0 ? maxRing() : designRingCap;
        return Math.min(patternRing, Math.min(maxRing(), Math.min(structural, ringCap)));
    }

    /** Four directions perpendicular to the rod axis (stable order). */
    public static List<Direction> lateralDirections(Direction rodAxis) {
        List<Direction> dirs = new ArrayList<>(4);
        for (Direction d : Direction.values()) {
            if (d.getAxis() != rodAxis.getAxis()) {
                dirs.add(d);
            }
        }
        return dirs;
    }

    /**
     * Safe {@link BlockGetter#getBlockState} for client section meshing.
     * {@code RenderChunkRegion} only covers a 3x3 chunk window; long blade rings can walk outside and
     * throw {@link ArrayIndexOutOfBoundsException}. Treat out-of-window as empty air.
     */
    public static BlockState safeGetBlockState(BlockGetter level, BlockPos pos) {
        try {
            return level.getBlockState(pos);
        } catch (IndexOutOfBoundsException ignored) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    /**
     * True when {@code state} is a blade that belongs to a rod along {@code outwardFromRod}
     * (same facing set when placing by clicking the rod: blade points away from the rod).
     */
    public static boolean isOwnedBladeAlong(BlockState state, Direction outwardFromRod) {
        return state.is(ModBlocks.TURBINE_BLADE.get())
                && state.hasProperty(TurbineBladeBlock.FACING)
                && state.getValue(TurbineBladeBlock.FACING) == outwardFromRod;
    }

    /** Blade count on one lateral axis from the rod outward (capped by config). */
    public static int depthAlong(BlockGetter level, BlockPos rodPos, Direction lateralDir) {
        int depth = 0;
        BlockPos.MutableBlockPos pos = rodPos.mutable().move(lateralDir);
        int cap = maxRing();
        while (depth < cap && isOwnedBladeAlong(safeGetBlockState(level, pos), lateralDir)) {
            depth++;
            pos.move(lateralDir);
        }
        return depth;
    }

    public static int totalBladesOnRod(Level level, BlockPos rodPos, Direction rodAxis) {
        int total = 0;
        for (Direction dir : lateralDirections(rodAxis)) {
            total += depthAlong(level, rodPos, dir);
        }
        return total;
    }

    /** Smallest ring index (1-based) that still has a free lateral slot for this rod. */
    public static int currentRing(Level level, BlockPos rodPos, Direction rodAxis) {
        int limit = maxRing();
        List<Direction> lateral = lateralDirections(rodAxis);
        for (int ring = 1; ring <= limit; ring++) {
            for (Direction dir : lateral) {
                if (!isOwnedBladeAlong(safeGetBlockState(level, rodPos.relative(dir, ring)), dir)) {
                    return ring;
                }
            }
        }
        return limit + 1;
    }

    public static Optional<Direction> chooseLateralDirection(Level level, BlockPos rodPos, Direction rodAxis) {
        int total = totalBladesOnRod(level, rodPos, rodAxis);
        if (total >= maxBladesPerRod()) {
            return Optional.empty();
        }
        int ring = currentRing(level, rodPos, rodAxis);
        if (ring > maxRing()) {
            return Optional.empty();
        }

        List<Direction> lateral = lateralDirections(rodAxis);
        List<Direction> missing = new ArrayList<>();
        int minDepth = Integer.MAX_VALUE;
        for (Direction dir : lateral) {
            if (!isOwnedBladeAlong(safeGetBlockState(level, rodPos.relative(dir, ring)), dir)) {
                missing.add(dir);
                minDepth = Math.min(minDepth, depthAlong(level, rodPos, dir));
            }
        }
        if (missing.isEmpty()) {
            return Optional.empty();
        }

        // Second blade: opposite side of the first for balance
        if (total == 1) {
            for (Direction direction : lateral) {
                if (depthAlong(level, rodPos, direction) == 1) {
                    Direction opposite = direction.getOpposite();
                    if (missing.contains(opposite)) {
                        return Optional.of(opposite);
                    }
                    break;
                }
            }
        }

        // Prefer sides with fewer blades; tie-break round-robin
        List<Direction> candidates = new ArrayList<>();
        for (Direction dir : missing) {
            if (depthAlong(level, rodPos, dir) == minDepth) {
                candidates.add(dir);
            }
        }
        if (candidates.isEmpty()) {
            candidates = missing;
        }
        return Optional.of(candidates.get(total % candidates.size()));
    }

    /**
     * Blade positions owned by this rod: contiguous outward chain on each lateral axis,
     * only blades whose facing matches the placement direction (away from the rod).
     */
    public static List<BlockPos> collectBladePositions(Level level, BlockPos rodPos, Direction rodAxis) {
        List<BlockPos> blades = new ArrayList<>();
        int scanCap = Math.max(maxRing(), 64);
        for (Direction dir : lateralDirections(rodAxis)) {
            BlockPos.MutableBlockPos pos = rodPos.mutable().move(dir);
            int distance = 0;
            while (distance < scanCap && isOwnedBladeAlong(safeGetBlockState(level, pos), dir)) {
                blades.add(pos.immutable());
                pos.move(dir);
                distance++;
            }
        }
        return blades;
    }

    public static void dropBladesOnRod(Level level, BlockPos rodPos, BlockState rodState) {
        if (!(rodState.getBlock() instanceof TurbineRodBlock)) {
            return;
        }
        Direction rodAxis = rodState.getValue(TurbineRodBlock.FACING);
        List<BlockPos> blades = collectBladePositions(level, rodPos, rodAxis);
        if (blades.isEmpty()) {
            return;
        }
        for (BlockPos bladePos : blades) {
            level.removeBlock(bladePos, false);
        }
        refreshRodConnectorRender(level, rodPos);
        int remaining = blades.size();
        int maxStack = new ItemStack(ModItems.TURBINE_BLADE.get()).getMaxStackSize();
        while (remaining > 0) {
            int count = Math.min(remaining, maxStack);
            Block.popResource(level, rodPos, new ItemStack(ModItems.TURBINE_BLADE.get(), count));
            remaining -= count;
        }
    }

    /**
     * Places the next blade on the current ring (balanced lateral pick). Returns true if placed.
     * Tries alternate lateral directions on the same ring when the preferred cell is blocked
     * (common with multi-rod layouts).
     */
    public static boolean placeNextBlade(Level level, BlockPos rodPos, BlockState rodState) {
        if (!(rodState.getBlock() instanceof TurbineRodBlock) || !rodState.hasProperty(TurbineRodBlock.FACING)) {
            return false;
        }
        Direction rodAxis = rodState.getValue(TurbineRodBlock.FACING);
        int ring = currentRing(level, rodPos, rodAxis);
        if (ring > maxRing()) {
            return false;
        }
        List<Direction> tryOrder = new ArrayList<>();
        chooseLateralDirection(level, rodPos, rodAxis).ifPresent(tryOrder::add);
        for (Direction dir : lateralDirections(rodAxis)) {
            if (tryOrder.contains(dir)) {
                continue;
            }
            if (isOwnedBladeAlong(safeGetBlockState(level, rodPos.relative(dir, ring)), dir)) {
                continue;
            }
            if (ring > 1 && depthAlong(level, rodPos, dir) != ring - 1) {
                continue;
            }
            tryOrder.add(dir);
        }
        for (Direction lateral : tryOrder) {
            BlockPos placePos = rodPos.relative(lateral, ring);
            if (!level.getBlockState(placePos).canBeReplaced()) {
                continue;
            }
            BlockState bladeState = ModBlocks.TURBINE_BLADE.get().defaultBlockState()
                    .setValue(TurbineBladeBlock.FACING, lateral);
            if (level.setBlock(placePos, bladeState, Block.UPDATE_ALL)) {
                refreshRodConnectorRender(level, rodPos);
                return true;
            }
        }
        return false;
    }

    /**
     * Walks from a blade toward its rod along the blade {@link TurbineBladeBlock#FACING} axis.
     * Intermediate blades must share the same outward facing (same ownership chain).
     */
    public static Optional<BlockPos> findRodFromBlade(Level level, BlockPos bladePos, BlockState bladeState) {
        if (!bladeState.is(ModBlocks.TURBINE_BLADE.get()) || !bladeState.hasProperty(TurbineBladeBlock.FACING)) {
            return Optional.empty();
        }
        Direction outward = bladeState.getValue(TurbineBladeBlock.FACING);
        Direction towardRod = outward.getOpposite();
        BlockPos.MutableBlockPos scan = bladePos.mutable().move(towardRod);
        int cap = maxRing() + 1;
        for (int step = 0; step <= cap; step++) {
            BlockState at = safeGetBlockState(level, scan);
            if (at.is(ModBlocks.TURBINE_ROD.get())) {
                return Optional.of(scan.immutable());
            }
            if (!isOwnedBladeAlong(at, outward)) {
                return Optional.empty();
            }
            scan.move(towardRod);
        }
        return Optional.empty();
    }

    /** Rebuilds client rod meshes after blade layout on a lateral side changes. */
    public static void refreshRodConnectorRender(Level level, BlockPos rodPos) {
        BlockState rod = level.getBlockState(rodPos);
        if (!rod.is(ModBlocks.TURBINE_ROD.get())) {
            return;
        }
        level.sendBlockUpdated(rodPos, rod, rod, Block.UPDATE_ALL);
    }
}
