package net.unfamily.colossal_reactors.turbine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.block.TurbineRodBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Blade efficiency from ascending/descending layer blade counts along rod controller axis.
 */
public final class TurbineBladeEfficiency {

    /** Applied when at least one rod column has no rod controller on the closure deck. */
    public static final double MISSING_ROD_CONTROLLER_MULTIPLIER = 0.95;

    private TurbineBladeEfficiency() {}

    /**
     * @param layerBladeCounts blade count per layer along axis (bottom to top)
     */
    public static double computeMultiplier(List<Integer> layerBladeCounts) {
        if (layerBladeCounts == null || layerBladeCounts.isEmpty()) {
            return 1.0;
        }
        double bonus = Config.TURBINE_BLADE_EFFICIENCY_BONUS_PER_ASCENDING_LAYER.get();
        double malus = Config.TURBINE_BLADE_EFFICIENCY_MALUS_PER_DESCENDING_LAYER.get();
        double mult = 1.0;
        boolean bonusBroken = false;
        for (int i = 1; i < layerBladeCounts.size(); i++) {
            int prev = layerBladeCounts.get(i - 1);
            int cur = layerBladeCounts.get(i);
            if (cur > prev) {
                if (!bonusBroken) {
                    mult += bonus;
                }
            } else if (cur < prev) {
                bonusBroken = true;
                mult -= malus;
            }
        }
        return Math.max(0.01, mult);
    }

    public static double computeFromBounds(Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                           Direction axis) {
        List<Integer> layerCounts = collectLayerBladeCounts(level, minX, minY, minZ, maxX, maxY, maxZ, axis);
        return computeMultiplier(layerCounts);
    }

    /**
     * Runtime production: blade-efficiency curve uses only rod-zone layers (not coil-cap layers along full interior height).
     */
    public static double computeFromRotorLayout(Level level, TurbineRotorLayout layout,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        double mult = computeMultiplier(collectRodZoneLayerBladeCounts(level, layout, minX, minY, minZ, maxX, maxY, maxZ));
        if (hasRodColumnMissingController(level, layout)) {
            mult *= MISSING_ROD_CONTROLLER_MULTIPLIER;
        }
        return mult;
    }

    /**
     * True when any rod column (full height under the closure deck) lacks a turbine rod controller on the deck.
     */
    public static boolean hasRodColumnMissingController(Level level, TurbineRotorLayout layout) {
        int layers = layout.closureInteriorIndex();
        if (layers <= 0) {
            return false;
        }
        for (int ca = 0; ca < layout.crossSizeA(); ca++) {
            for (int cb = 0; cb < layout.crossSizeB(); cb++) {
                boolean hasRod = false;
                for (int layer = 0; layer < layers; layer++) {
                    if (level.getBlockState(layout.rodPos(layer, ca, cb)).is(ModBlocks.TURBINE_ROD.get())) {
                        hasRod = true;
                        break;
                    }
                }
                if (!hasRod) {
                    continue;
                }
                BlockPos ctrl = layout.controllerPos(new TurbineRodControllerLayout.Center(ca, cb));
                if (!level.getBlockState(ctrl).is(ModBlocks.TURBINE_ROD_CONTROLLER.get())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<Integer> collectRodZoneLayerBladeCounts(Level level, TurbineRotorLayout layout,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<Integer> counts = new ArrayList<>();
        int rodLayers = layout.closureInteriorIndex();
        for (int layer = 0; layer < rodLayers; layer++) {
            counts.add(maxBalancedBladesOnRodZoneLayer(level, layout, layer, minX, minY, minZ, maxX, maxY, maxZ));
        }
        trimLeadingZeroLayers(counts);
        if (counts.isEmpty()) {
            counts.add(0);
        }
        return counts;
    }

    /**
     * Drops empty layers below the lowest rod segment so partial columns (not reaching the rotor floor)
     * do not penalize the ascending blade-efficiency curve.
     */
    private static void trimLeadingZeroLayers(List<Integer> counts) {
        while (counts.size() > 1 && counts.getFirst() == 0) {
            counts.removeFirst();
        }
    }

    private static int maxBalancedBladesOnRodZoneLayer(Level level, TurbineRotorLayout layout, int layerIndex,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int max = 0;
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = minY + 1; y < maxY; y++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    if (!layout.isInRodZone(x, y, z) || layout.interiorIndexFromWorld(x, y, z) != layerIndex) {
                        continue;
                    }
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.is(ModBlocks.TURBINE_ROD.get()) && state.hasProperty(TurbineRodBlock.FACING)) {
                        Direction rodAxis = state.getValue(TurbineRodBlock.FACING);
                        if (rodAxis.getAxis() != layout.growthAxis().getAxis()) {
                            continue;
                        }
                        max = Math.max(max, countBalancedBlades(level, new BlockPos(x, y, z), rodAxis));
                    }
                }
            }
        }
        return max;
    }

    public static List<Integer> collectLayerBladeCounts(Level level, int minX, int minY, int minZ,
                                                        int maxX, int maxY, int maxZ, Direction axis) {
        List<Integer> counts = new ArrayList<>();
        if (axis.getAxis() == Direction.Axis.Y) {
            for (int y = minY + 1; y < maxY; y++) {
                counts.add(maxBladesOnLayer(level, minX, y, minZ, maxX, maxY, maxZ, axis));
            }
        } else if (axis.getAxis() == Direction.Axis.X) {
            for (int x = minX + 1; x < maxX; x++) {
                counts.add(maxBladesOnLayerX(level, x, minY, minZ, maxX, maxY, maxZ, axis));
            }
        } else {
            for (int z = minZ + 1; z < maxZ; z++) {
                counts.add(maxBladesOnLayerZ(level, minX, minY, z, maxX, maxY, maxZ, axis));
            }
        }
        if (counts.isEmpty()) {
            counts.add(0);
        }
        return counts;
    }

    private static int maxBladesOnLayer(Level level, int minX, int y, int minZ, int maxX, int maxY, int maxZ, Direction axis) {
        int max = 0;
        for (int x = minX + 1; x < maxX; x++) {
            for (int z = minZ + 1; z < maxZ; z++) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(p);
                if (state.is(ModBlocks.TURBINE_ROD.get()) && state.hasProperty(TurbineRodBlock.FACING)) {
                    Direction rodAxis = state.getValue(TurbineRodBlock.FACING);
                    if (rodAxis.getAxis() != axis.getAxis()) {
                        continue;
                    }
                    int blades = countBalancedBlades(level, p, rodAxis);
                    max = Math.max(max, blades);
                }
            }
        }
        return max;
    }

    private static int maxBladesOnLayerX(Level level, int x, int minY, int minZ, int maxX, int maxY, int maxZ, Direction axis) {
        int max = 0;
        for (int y = minY + 1; y < maxY; y++) {
            for (int z = minZ + 1; z < maxZ; z++) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(p);
                if (state.is(ModBlocks.TURBINE_ROD.get()) && state.hasProperty(TurbineRodBlock.FACING)) {
                    int blades = countBalancedBlades(level, p, state.getValue(TurbineRodBlock.FACING));
                    max = Math.max(max, blades);
                }
            }
        }
        return max;
    }

    private static int maxBladesOnLayerZ(Level level, int minX, int minY, int z, int maxX, int maxY, int maxZ, Direction axis) {
        int max = 0;
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = minY + 1; y < maxY; y++) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(p);
                if (state.is(ModBlocks.TURBINE_ROD.get()) && state.hasProperty(TurbineRodBlock.FACING)) {
                    int blades = countBalancedBlades(level, p, state.getValue(TurbineRodBlock.FACING));
                    max = Math.max(max, blades);
                }
            }
        }
        return max;
    }

    private static int countBalancedBlades(Level level, BlockPos rodPos, Direction rodAxis) {
        int total = TurbineBladePlacement.totalBladesOnRod(level, rodPos, rodAxis);
        if (!Boolean.TRUE.equals(Config.TURBINE_REQUIRE_BALANCED_BLADE_RINGS.get())) {
            return total;
        }
        if (total % 4 != 0) return 0;
        return total;
    }
}
