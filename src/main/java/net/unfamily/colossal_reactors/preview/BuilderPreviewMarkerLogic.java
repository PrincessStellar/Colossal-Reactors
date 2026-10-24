package net.unfamily.colossal_reactors.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.block.ReactorBuilderBlock;
import net.unfamily.colossal_reactors.block.TurbineBuilderBlock;
import net.unfamily.colossal_reactors.blockentity.ReactorBuilderBlockEntity;
import net.unfamily.colossal_reactors.blockentity.TurbineBuilderBlockEntity;
import net.unfamily.colossal_reactors.heatsink.HeatSinkLoader;
import net.unfamily.colossal_reactors.reactor.ReactorValidation;
import net.unfamily.colossal_reactors.reactor.RodPatternLogic;
import net.unfamily.colossal_reactors.tags.ModBlockTags;
import net.unfamily.colossal_reactors.turbine.ElecCoilLoader;
import net.unfamily.colossal_reactors.turbine.TurbineRodControllerLayout;
import net.unfamily.colossal_reactors.turbine.TurbineRodPatternLogic;
import net.unfamily.colossal_reactors.turbine.TurbineRotorLayout;
import net.unfamily.colossal_reactors.turbine.TurbineValidation;

/**
 * Shared reactor/turbine footprint marker rules for server preview packets and client reconcile.
 * Always emits baseline colors together with occupied when both apply so layer cache can restore
 * purple/cyan/white/yellow after a wrong block is removed.
 */
public final class BuilderPreviewMarkerLogic {

    public static final int COLOR_OCCUPIED = 0xE0FF0000;
    public static final int COLOR_ROD = 0xE0FFFF00;
    public static final int COLOR_ROD_CONTROLLER = 0xE0FFFFFF;
    public static final int COLOR_FRAME_EDGE = 0x80FF00FF;
    public static final int COLOR_CLOSURE_DECK = 0xE070D8FF;

    @FunctionalInterface
    public interface MarkerSink {
        void accept(BlockPos worldPos, int color);
    }

    private BuilderPreviewMarkerLogic() {}

    public static void forEachReactorMarker(Level level, ReactorBuilderBlockEntity builder, BlockPos builderPos,
                                            MarkerSink sink) {
        BlockState state = level.getBlockState(builderPos);
        if (!(state.getBlock() instanceof ReactorBuilderBlock)) {
            return;
        }
        Direction facing = state.getValue(ReactorBuilderBlock.FACING);
        AABB aabb = ReactorBuilderBlockEntity.getReactorVolumeAABB(
                builderPos, facing,
                builder.getSizeLeft(), builder.getSizeRight(),
                builder.getSizeHeight(), builder.getSizeDepth());

        int minX = (int) Math.floor(aabb.minX);
        int minY = (int) Math.floor(aabb.minY);
        int minZ = (int) Math.floor(aabb.minZ);
        int maxX = (int) Math.floor(aabb.maxX - 1e-6);
        int maxY = (int) Math.floor(aabb.maxY - 1e-6);
        int maxZ = (int) Math.floor(aabb.maxZ - 1e-6);

        int pattern = builder.getRodPattern();
        int patternMode = builder.getPatternMode();
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;
        int rw = RodPatternLogic.rodSpaceWidth(w, patternMode);
        int rh = RodPatternLogic.rodSpaceHeight(h, patternMode);
        int rd = RodPatternLogic.rodSpaceDepth(d, patternMode);
        int insetXZ = RodPatternLogic.rodSpaceInsetXZ(patternMode);
        boolean expansionRodAtCenter = (pattern == RodPatternLogic.PATTERN_EXPANSION)
                ? RodPatternLogic.getExpansionRodAtCenterForPreview(rw, rd)
                : false;

        for (int lx = insetXZ; lx < w - insetXZ; lx++) {
            for (int ly = 1; ly < h - 1; ly++) {
                for (int lz = insetXZ; lz < d - insetXZ; lz++) {
                    int rx = lx - insetXZ;
                    int ry = ly - 1;
                    int rz = lz - insetXZ;
                    if (isReactorRodColumn(builder, rx, rz, rw, rd, pattern, expansionRodAtCenter)) {
                        sink.accept(new BlockPos(minX + lx, minY + ly, minZ + lz), COLOR_ROD);
                    }
                }
            }
        }

        int rodControllerY = minY + h - 1;
        for (int rx = 0; rx < rw; rx++) {
            for (int rz = 0; rz < rd; rz++) {
                if (isReactorRodColumn(builder, rx, rz, rw, rd, pattern, expansionRodAtCenter)) {
                    BlockPos ctrlPos = new BlockPos(minX + insetXZ + rx, rodControllerY, minZ + insetXZ + rz);
                    emitReactorRodController(level, sink, ctrlPos);
                }
            }
        }

        RegistryAccess registryAccess = level.registryAccess();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState blockState = level.getBlockState(pos);
                    int lx = x - minX;
                    int ly = y - minY;
                    int lz = z - minZ;
                    boolean onBorder = (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ);
                    boolean hasBlock = hasSolidBlock(blockState);
                    boolean onEdge = ((x == minX || x == maxX) && (y == minY || y == maxY))
                            || ((x == minX || x == maxX) && (z == minZ || z == maxZ))
                            || ((y == minY || y == maxY) && (z == minZ || z == maxZ));

                    if (onBorder) {
                        boolean validFrame = ReactorValidation.isShellBlock(blockState)
                                || (blockState.is(ModBlocks.ROD_CONTROLLER.get()) && y == maxY
                                && isReactorRodColumn(builder, x - minX - insetXZ, z - minZ - insetXZ, rw, rd, pattern, expansionRodAtCenter));
                        if (hasBlock && !validFrame) {
                            sink.accept(pos, COLOR_OCCUPIED);
                            if (onEdge) {
                                sink.accept(pos, COLOR_FRAME_EDGE);
                            }
                        } else if (onEdge) {
                            sink.accept(pos, COLOR_FRAME_EDGE);
                        }
                    } else {
                        boolean isRodPos = (lx >= insetXZ && lx < w - insetXZ && ly >= 1 && ly < h - 1 && lz >= insetXZ && lz < d - insetXZ)
                                && isReactorRodColumn(builder, lx - insetXZ, lz - insetXZ, rw, rd, pattern, expansionRodAtCenter);
                        if (isRodPos) {
                            if (hasBlock && !blockState.is(ModBlocks.REACTOR_ROD.get())) {
                                sink.accept(pos, COLOR_OCCUPIED);
                                sink.accept(pos, COLOR_ROD);
                            }
                        } else if (hasBlock && !HeatSinkLoader.isHeatSinkBlock(blockState, registryAccess)) {
                            sink.accept(pos, COLOR_OCCUPIED);
                        }
                    }
                }
            }
        }
    }

    private static void emitReactorRodController(Level level, MarkerSink sink, BlockPos ctrlPos) {
        BlockState blockState = level.getBlockState(ctrlPos);
        if (hasSolidBlock(blockState) && !blockState.is(ModBlocks.ROD_CONTROLLER.get())) {
            sink.accept(ctrlPos, COLOR_OCCUPIED);
        }
        sink.accept(ctrlPos, COLOR_ROD_CONTROLLER);
    }

    public static void forEachTurbineMarker(Level level, TurbineBuilderBlockEntity builder, BlockPos builderPos,
                                            MarkerSink sink) {
        BlockState state = level.getBlockState(builderPos);
        if (!(state.getBlock() instanceof TurbineBuilderBlock)) {
            return;
        }
        Direction facing = state.getValue(TurbineBuilderBlock.FACING);
        AABB aabb = TurbineBuilderBlockEntity.getTurbineVolumeAABB(
                builderPos, facing,
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
        int inset = 1;
        int coilLayers = builder.getAppliedCoilLayerCount();
        var growthAxis = builder.getPlacementAxis();
        TurbineRotorLayout layout = TurbineRotorLayout.from(
                minX, minY, minZ, maxX, maxY, maxZ, w, h, d, coilLayers, growthAxis);
        int rw = layout.crossSizeA();
        int rd = layout.crossSizeB();
        int pattern = builder.getRodPattern();
        RegistryAccess registryAccess = level.registryAccess();
        java.util.function.BiPredicate<Integer, Integer> websiteRodOverride =
                pattern == TurbineRodPatternLogic.PATTERN_WEBSITE && builder.hasValidWebsiteDesign()
                        ? builder::websiteIsRodColumn
                        : null;
        java.util.List<TurbineRodControllerLayout.Center> rodCtrlCenters =
                TurbineRodPatternLogic.rodColumns(rw, rd, pattern, websiteRodOverride);

        for (int lx = inset; lx < w - inset; lx++) {
            for (int ly = inset; ly < h - inset; ly++) {
                for (int lz = inset; lz < d - inset; lz++) {
                    int wx = minX + lx;
                    int wy = minY + ly;
                    int wz = minZ + lz;
                    if (!layout.isInRodZone(wx, wy, wz)) {
                        continue;
                    }
                    int rx = layout.crossAFromWorld(wx, wy, wz);
                    int rz = layout.crossBFromWorld(wx, wy, wz);
                    if (rx < 0 || rx >= rw || rz < 0 || rz >= rd) {
                        continue;
                    }
                    if (isTurbineRodColumn(builder, rx, rz, rw, rd, pattern)) {
                        sink.accept(new BlockPos(wx, wy, wz), COLOR_ROD);
                    }
                }
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isAnyRodControllerCell(layout, rodCtrlCenters, x, y, z)) {
                        continue;
                    }
                    BlockState blockState = level.getBlockState(pos);
                    boolean onBorder = (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ);
                    boolean hasBlock = hasSolidBlock(blockState);
                    boolean onEdge = ((x == minX || x == maxX) && (y == minY || y == maxY))
                            || ((x == minX || x == maxX) && (z == minZ || z == maxZ))
                            || ((y == minY || y == maxY) && (z == minZ || z == maxZ));

                    if (onBorder) {
                        if (layout.isOpenEndCapWorld(x, y, z) && builder.isOpenTop() && blockState.isAir()) {
                            continue;
                        }
                        boolean validFrame = TurbineValidation.isShellBlock(blockState)
                                || (blockState.is(ModBlocks.TURBINE_ROD_CONTROLLER.get())
                                && isAnyRodControllerCell(layout, rodCtrlCenters, x, y, z));
                        if (hasBlock && !validFrame) {
                            sink.accept(pos, COLOR_OCCUPIED);
                            if (onEdge) {
                                int edgeColor = layout.isClosureDeckWorld(x, y, z) ? COLOR_CLOSURE_DECK : COLOR_FRAME_EDGE;
                                sink.accept(pos, edgeColor);
                            }
                        } else if (onEdge) {
                            int edgeColor = layout.isClosureDeckWorld(x, y, z) ? COLOR_CLOSURE_DECK : COLOR_FRAME_EDGE;
                            sink.accept(pos, edgeColor);
                        }
                    } else if (layout.isInRodZone(x, y, z)) {
                        int rx = layout.crossAFromWorld(x, y, z);
                        int rz = layout.crossBFromWorld(x, y, z);
                        boolean isRodCol = rx >= 0 && rx < rw && rz >= 0 && rz < rd
                                && isTurbineRodColumn(builder, rx, rz, rw, rd, pattern);
                        if (isRodCol) {
                            if (hasBlock && !blockState.is(ModBlocks.TURBINE_ROD.get())
                                    && !blockState.is(ModBlocks.TURBINE_BLADE.get())) {
                                sink.accept(pos, COLOR_OCCUPIED);
                                sink.accept(pos, COLOR_ROD);
                            }
                        } else if (hasBlock && !blockState.is(ModBlocks.TURBINE_BLADE.get())
                                && !blockState.is(ModBlockTags.TURBINE_SHELL_CASINGS)
                                && !blockState.is(ModBlockTags.TURBINE_SHELL_GLASSES)) {
                            sink.accept(pos, COLOR_OCCUPIED);
                        }
                    } else if (layout.isClosureDeckWorld(x, y, z)) {
                        if (hasBlock && !blockState.is(ModBlockTags.TURBINE_SHELL_CASINGS)
                                && !blockState.is(ModBlockTags.TURBINE_SHELL_GLASSES)
                                && !blockState.isAir()) {
                            sink.accept(pos, COLOR_OCCUPIED);
                            sink.accept(pos, COLOR_CLOSURE_DECK);
                        } else {
                            sink.accept(pos, COLOR_CLOSURE_DECK);
                        }
                    } else if (layout.isCoilZoneWorld(x, y, z)) {
                        if (hasBlock && !ElecCoilLoader.isCoilBlock(blockState, registryAccess)
                                && !blockState.is(ModBlockTags.TURBINE_SHELL_CASINGS)
                                && !blockState.is(ModBlockTags.TURBINE_SHELL_GLASSES)) {
                            sink.accept(pos, COLOR_OCCUPIED);
                        }
                    } else if (hasBlock && !blockState.is(ModBlockTags.TURBINE_SHELL_CASINGS)
                            && !blockState.is(ModBlockTags.TURBINE_SHELL_GLASSES)) {
                        sink.accept(pos, COLOR_OCCUPIED);
                    }
                }
            }
        }

        for (TurbineRodControllerLayout.Center center : rodCtrlCenters) {
            emitTurbineRodController(level, sink, layout.controllerPos(center));
        }
    }

    private static boolean isAnyRodControllerCell(
            TurbineRotorLayout layout, java.util.List<TurbineRodControllerLayout.Center> centers,
            int x, int y, int z) {
        for (TurbineRodControllerLayout.Center center : centers) {
            if (layout.isRodControllerAt(x, y, z, center)) {
                return true;
            }
        }
        return false;
    }

    private static void emitTurbineRodController(Level level, MarkerSink sink, BlockPos ctrlPos) {
        BlockState blockState = level.getBlockState(ctrlPos);
        if (hasSolidBlock(blockState) && !blockState.is(ModBlocks.TURBINE_ROD_CONTROLLER.get())) {
            sink.accept(ctrlPos, COLOR_OCCUPIED);
        }
        sink.accept(ctrlPos, COLOR_ROD_CONTROLLER);
    }

    private static boolean hasSolidBlock(BlockState state) {
        return !state.isAir() && !state.canBeReplaced();
    }

    private static boolean isReactorRodColumn(ReactorBuilderBlockEntity builder, int rx, int rz, int rw, int rd,
                                              int pattern, boolean expansionRodAtCenter) {
        if (rx < 0 || rz < 0 || rx >= rw || rz >= rd) {
            return false;
        }
        if (pattern == RodPatternLogic.PATTERN_WEBSITE) {
            return builder.hasValidWebsiteDesign() && builder.websiteIsRodColumn(rx, rz);
        }
        return RodPatternLogic.isRodColumnForPreview(rx, rz, rw, rd, pattern, expansionRodAtCenter);
    }

    private static boolean isTurbineRodColumn(TurbineBuilderBlockEntity builder, int rx, int rz, int rw, int rd, int pattern) {
        java.util.function.BiPredicate<Integer, Integer> override = null;
        if (pattern == TurbineRodPatternLogic.PATTERN_WEBSITE && builder.hasValidWebsiteDesign()) {
            override = builder::websiteIsRodColumn;
        }
        return TurbineRodPatternLogic.isRodColumn(rx, rz, rw, rd, pattern, override);
    }
}
