package net.unfamily.colossal_reactors.turbine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.colossal_reactors.ColossalReactors;
import net.unfamily.colossal_reactors.Config;
import net.unfamily.colossal_reactors.block.ModBlocks;
import net.unfamily.colossal_reactors.block.TurbineRodBlock;
import net.unfamily.colossal_reactors.block.TurbineRodControllerBlock;
import net.unfamily.colossal_reactors.network.ModPayloads;
import net.unfamily.colossal_reactors.tags.ModBlockTags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates turbine multiblock structure (shell, rods, blades, coil zone).
 */
public final class TurbineValidation {

    public enum FailureCode {
        BAD_START,
        CONTROLLER_FACE_Y,
        TOO_LARGE,
        NO_ROD_CONTROLLER,
        /** @deprecated Multiple aligned controllers are allowed; kept for old clients/messages. */
        MULTIPLE_ROD_CONTROLLERS,
        ROD_CONTROLLER_NO_COLUMN,
        ROD_CONTROLLER_BORDER,
        ROD_CONTROLLER_FACING,
        ROD_CONTROLLER_AXIS_MISMATCH,
        SHELL_GAP,
        ROD_IN_COIL_ZONE,
        COIL_ZONE_BLOCK,
        ROD_NOT_TO_FLOOR,
        UNBALANCED_BLADES,
        EXTERIOR_CONTROLLER_COUNT,
        INTERIOR_UNKNOWN,
        REDSTONE_PORT_COUNT
    }

    public record ValidationReport(
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            int width, int height, int length,
            @Nullable BlockPos faceCenterNegX,
            @Nullable BlockPos faceCenterPosX,
            @Nullable BlockPos faceCenterNegY,
            @Nullable BlockPos faceCenterPosY,
            @Nullable BlockPos faceCenterNegZ,
            @Nullable BlockPos faceCenterPosZ,
            int exteriorControllers,
            @Nullable Direction growthAxis,
            int closureCoord,
            int closureInteriorIndex,
            int coilZoneStartInterior,
            int coilLayersUsed,
            int rodControllersFound,
            int redstonePortCount,
            long[] redstonePortPositions
    ) {
        public static ValidationReport empty() {
            return new ValidationReport(0, 0, 0, 0, 0, 0, 0, 0, 0,
                    null, null, null, null, null, null, 0, null, 0, 0, 0, 0, 0, 0, new long[0]);
        }
    }

    public record Result(
            boolean valid,
            @Nullable FailureCode failure,
            @Nullable BlockPos failurePos,
            ValidationReport report,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            int bladeCount, int validBladeCount, int coilBlockCount,
            double coilEfficiency, double bladeEfficiency,
            double maxSteamMbPerTick, double estimatedRfPerTick
    ) {
        public static Result invalid() {
            return invalid(FailureCode.BAD_START, null, ValidationReport.empty());
        }

        public static Result invalid(FailureCode failure, @Nullable BlockPos failurePos, ValidationReport report) {
            return new Result(false, failure, failurePos, report,
                    report.minX(), report.minY(), report.minZ(),
                    report.maxX(), report.maxY(), report.maxZ(),
                    0, 0, 0, 0, 0, 0, 0);
        }
    }

    private TurbineValidation() {}

    public static Result validate(Level level, BlockPos start, Direction intoTurbine) {
        return validateWithRodAlignment(level, start, intoTurbine, -1);
    }

    public static Result validate(Level level, BlockPos start, Direction intoTurbine, int coilLayerCount) {
        return validateInternal(level, start, intoTurbine, coilLayerCount, false);
    }

    /** Infers coil layers from structure when {@code coilLayerCount < 0}. */
    public static Result validateWithRodAlignment(Level level, BlockPos start, Direction intoTurbine, int coilLayerCount) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return validateInternal(level, start, intoTurbine, coilLayerCount, false);
        }
        Result probe = validateInternal(level, start, intoTurbine, coilLayerCount, false);
        if (!probe.valid()) {
            return probe;
        }
        Direction axis = probe.report().growthAxis();
        if (axis == null) {
            return Result.invalid(FailureCode.NO_ROD_CONTROLLER, null, probe.report());
        }
        int w = probe.maxX() - probe.minX() + 1;
        int h = probe.maxY() - probe.minY() + 1;
        int d = probe.maxZ() - probe.minZ() + 1;
        TurbineRotorLayout layout = TurbineRotorLayout.from(
                probe.minX(), probe.minY(), probe.minZ(), probe.maxX(), probe.maxY(), probe.maxZ(),
                w, h, d, probe.report().coilLayersUsed(), axis);
        TurbineRodAlignment.correctOppositeRods(server,
                probe.minX(), probe.minY(), probe.minZ(), probe.maxX(), probe.maxY(), probe.maxZ(),
                layout);
        return validateInternal(level, start, intoTurbine, coilLayerCount, false);
    }

    private static Result validateInternal(
            Level level, BlockPos start, Direction intoTurbine, int requestedCoilLayers, boolean unused) {
        ValidationReportBuilder report = new ValidationReportBuilder();

        if (start == null || level == null || !level.isLoaded(start)) {
            return fail(FailureCode.BAD_START, start, report.build());
        }
        if (!isShellBlock(level.getBlockState(start))) {
            return fail(FailureCode.BAD_START, start, report.build());
        }
        if (intoTurbine.getAxis() == Direction.Axis.Y) {
            return fail(FailureCode.CONTROLLER_FACE_Y, start, report.build());
        }

        Direction left = intoTurbine.getCounterClockWise();
        Direction right = intoTurbine.getClockWise();
        int maxW = Config.MAX_TURBINE_WIDTH.get();
        int maxL = Config.MAX_TURBINE_LENGTH.get();
        int maxH = Config.MAX_TURBINE_HEIGHT.get();
        int maxHorizontal = Math.max(maxW, maxL);

        BlockPos pos = start;
        int steps = 0;
        while (steps < maxH && isShellOrRodController(level.getBlockState(pos.below()))) {
            pos = pos.below();
            steps++;
        }
        steps = 0;
        while (steps < maxHorizontal && isShellOrRodController(level.getBlockState(pos.relative(left)))) {
            pos = pos.relative(left);
            steps++;
        }
        BlockPos minCorner = pos;
        steps = 0;
        while (steps < maxH && isShellOrRodController(level.getBlockState(pos.above()))) {
            pos = pos.above();
            steps++;
        }
        steps = 0;
        while (steps < maxHorizontal && isShellOrRodController(level.getBlockState(pos.relative(right)))) {
            pos = pos.relative(right);
            steps++;
        }
        steps = 0;
        while (steps < maxHorizontal && canStepDepth(level, pos, intoTurbine)) {
            pos = pos.relative(intoTurbine);
            steps++;
        }
        BlockPos maxCorner = pos;

        int minX = Math.min(minCorner.getX(), maxCorner.getX());
        int minY = Math.min(minCorner.getY(), maxCorner.getY());
        int minZ = Math.min(minCorner.getZ(), maxCorner.getZ());
        int maxX = Math.max(minCorner.getX(), maxCorner.getX());
        int maxY = Math.max(minCorner.getY(), maxCorner.getY());
        int maxZ = Math.max(minCorner.getZ(), maxCorner.getZ());
        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;
        int height = maxY - minY + 1;

        report.bounds(minX, minY, minZ, maxX, maxY, maxZ, width, height, length);
        report.faceCenters(computeFaceCenters(minX, minY, minZ, maxX, maxY, maxZ));

        if (width > maxW || length > maxL || height > maxH) {
            return fail(FailureCode.TOO_LARGE, start, report.build());
        }

        RegistryAccess registry = level.registryAccess();
        RodControllerScan rodScan = scanRodControllers(level, minX, minY, minZ, maxX, maxY, maxZ);
        report.rodControllersFound(rodScan.count);

        if (rodScan.count == 0) {
            return fail(FailureCode.NO_ROD_CONTROLLER, null, report.build());
        }
        if (rodScan.onBorder) {
            return fail(FailureCode.ROD_CONTROLLER_BORDER, rodScan.lastPos, report.build());
        }

        Direction provisionalAxis = rodScan.axis;
        if (provisionalAxis == null) {
            return fail(FailureCode.ROD_CONTROLLER_FACING, rodScan.lastPos, report.build());
        }

        int coilLayersUsed = requestedCoilLayers >= 0
                ? TurbineRodSpaceLayout.appliedCoilLayerCount(
                interiorExtentAlong(provisionalAxis, width, height, length), requestedCoilLayers)
                : inferCoilLayersFromStructure(level, minX, minY, minZ, maxX, maxY, maxZ, provisionalAxis, registry);

        TurbineRotorLayout layout = TurbineRotorLayout.from(
                minX, minY, minZ, maxX, maxY, maxZ, width, height, length, coilLayersUsed, provisionalAxis);
        report.plane(layout.closureCoord(), layout.coilStartInterior(), layout.closureInteriorIndex(), coilLayersUsed);

        List<BlockPos> rodControllers = listRodControllers(level, minX, minY, minZ, maxX, maxY, maxZ);
        report.rodControllersFound(rodControllers.size());
        if (rodControllers.isEmpty()) {
            return fail(FailureCode.NO_ROD_CONTROLLER, null, report.build());
        }
        Direction rotorAxis = provisionalAxis;
        for (BlockPos ctrlPos : rodControllers) {
            if (xOnBorder(ctrlPos, minX, maxX, minY, maxY, minZ, maxZ)) {
                return fail(FailureCode.ROD_CONTROLLER_BORDER, ctrlPos, report.build());
            }
            BlockState ctrlState = level.getBlockState(ctrlPos);
            if (!ctrlState.hasProperty(TurbineRodControllerBlock.FACING)) {
                return fail(FailureCode.ROD_CONTROLLER_FACING, ctrlPos, report.build());
            }
            Direction facing = ctrlState.getValue(TurbineRodControllerBlock.FACING);
            if (facing.getAxis() != rotorAxis.getAxis()) {
                return fail(FailureCode.ROD_CONTROLLER_AXIS_MISMATCH, ctrlPos, report.build());
            }
            if (!layout.isClosureDeckWorld(ctrlPos.getX(), ctrlPos.getY(), ctrlPos.getZ())) {
                return fail(FailureCode.ROD_CONTROLLER_NO_COLUMN, ctrlPos, report.build());
            }
            int ca = layout.crossAFromWorld(ctrlPos.getX(), ctrlPos.getY(), ctrlPos.getZ());
            int cb = layout.crossBFromWorld(ctrlPos.getX(), ctrlPos.getY(), ctrlPos.getZ());
            if (ca < 0 || cb < 0 || ca >= layout.crossSizeA() || cb >= layout.crossSizeB()) {
                return fail(FailureCode.ROD_CONTROLLER_NO_COLUMN, ctrlPos, report.build());
            }
            boolean hasRodBelow = false;
            for (int layer = 0; layer < layout.closureInteriorIndex(); layer++) {
                if (level.getBlockState(layout.rodPos(layer, ca, cb)).is(ModBlocks.TURBINE_ROD.get())) {
                    hasRodBelow = true;
                    break;
                }
            }
            if (!hasRodBelow) {
                return fail(FailureCode.ROD_CONTROLLER_NO_COLUMN, ctrlPos, report.build());
            }
        }
        report.growthAxis(rotorAxis);

        int bladeCount = 0;
        int coilBlockCount = 0;
        double sumCoe = 0;
        double sumMax = 0;
        List<BlockPos> redstonePorts = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    boolean onBorder = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                    BlockState state = level.getBlockState(p);
                    if (onBorder) {
                        if (state.is(ModBlocks.TURBINE_REDSTONE_PORT.get())) {
                            redstonePorts.add(p.immutable());
                        }
                        if (isRodController(state)) {
                            return fail(FailureCode.ROD_CONTROLLER_BORDER, p, report.build());
                        }
                        if (!isShellBlock(state)) {
                            return fail(FailureCode.SHELL_GAP, p, report.build());
                        }
                    } else {
                        boolean rodCtrlHere = isRodController(state);
                        if (state.is(ModBlocks.TURBINE_ROD.get())) {
                            if (layout.isCoilZoneWorld(x, y, z)) {
                                return fail(FailureCode.ROD_IN_COIL_ZONE, p, report.build());
                            }
                        } else if (state.is(ModBlocks.TURBINE_BLADE.get())) {
                            bladeCount++;
                        } else if (layout.isClosureDeckWorld(x, y, z)) {
                            if (!rodCtrlHere && !isShellBlock(state) && !state.isAir()) {
                                return fail(FailureCode.INTERIOR_UNKNOWN, p, report.build());
                            }
                        } else if (layout.isCoilZoneWorld(x, y, z)) {
                            if (!ElecCoilLoader.isCoilBlock(state, registry) && !state.isAir()) {
                                return fail(FailureCode.COIL_ZONE_BLOCK, p, report.build());
                            }
                            boolean countInAverage = !state.isAir()
                                    || !Boolean.TRUE.equals(Config.TURBINE_LEGACY_IGNORE_AIR_IN_COIL_AVERAGE.get());
                            if (countInAverage) {
                                coilBlockCount++;
                                var m = ElecCoilLoader.getModifiersForBlockOrDefault(state, registry);
                                sumCoe += m.effCoe();
                                sumMax += m.effMax();
                            }
                        } else if (!state.isAir()) {
                            return fail(FailureCode.INTERIOR_UNKNOWN, p, report.build());
                        }
                    }
                }
            }
        }

        if (!Boolean.TRUE.equals(Config.ALLOW_MULTIPLE_TURBINE_CONTROLLERS.get())) {
            int controllers = countControllersOnFaces(level, minX, minY, minZ, maxX, maxY, maxZ);
            report.exteriorControllers(controllers);
            if (controllers != 1) {
                return fail(FailureCode.EXTERIOR_CONTROLLER_COUNT, null, report.build());
            }
        }

        BlockPos rodFloorFailure = findRodNotReachingFloor(level, layout);
        if (rodFloorFailure != null) {
            return fail(FailureCode.ROD_NOT_TO_FLOOR, rodFloorFailure, report.build());
        }
        if (Boolean.TRUE.equals(Config.TURBINE_REQUIRE_BALANCED_BLADE_RINGS.get())) {
            BlockPos unbalanced = findUnbalancedBladesOnRod(level, layout);
            if (unbalanced != null) {
                return fail(FailureCode.UNBALANCED_BLADES, unbalanced, report.build());
            }
        }

        report.redstonePorts(redstonePorts);
        if (redstonePorts.size() >= 2) {
            return fail(FailureCode.REDSTONE_PORT_COUNT, null, report.build());
        }

        double coilEff = coilBlockCount > 0
                ? Math.min(sumCoe / coilBlockCount, sumMax / coilBlockCount)
                : Config.TURBINE_EMPTY_COIL_EFFICIENCY.get();
        double bladeEff = TurbineBladeEfficiency.computeFromRotorLayout(
                level, layout, minX, minY, minZ, maxX, maxY, maxZ);

        int validBladeCount = bladeCount;
        if (Boolean.TRUE.equals(Config.TURBINE_REQUIRE_BALANCED_BLADE_RINGS.get())) {
            validBladeCount = countValidBalancedBlades(level, layout, minX, minY, minZ, maxX, maxY, maxZ);
        }

        TurbineProductionMath.ProductionEstimate production = TurbineProductionMath.compute(
                bladeCount, validBladeCount, coilBlockCount, coilEff, bladeEff,
                TurbineGenerationLoader.getDefault());

        ValidationReport finalReport = report.build();
        logDebug(finalReport, null, true);

        return new Result(true, null, null, finalReport,
                minX, minY, minZ, maxX, maxY, maxZ,
                bladeCount, validBladeCount, coilBlockCount, coilEff, bladeEff,
                production.maxSteamMbPerTick(), production.estimatedRfPerTick());
    }

    private static int interiorExtentAlong(Direction axis, int width, int height, int length) {
        return switch (axis.getAxis()) {
            case Y -> TurbineRodSpaceLayout.interiorHeight(height);
            case Z -> TurbineRodSpaceLayout.interiorDepth(length);
            case X -> TurbineRodSpaceLayout.interiorWidth(width);
            default -> TurbineRodSpaceLayout.interiorHeight(height);
        };
    }

    /**
     * Infers coil layer count from blocks in the coil-cap region along the rotor axis.
     */
    public static int inferCoilLayersFromStructure(
            Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            Direction rotorAxis, RegistryAccess registry) {
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;
        int interiorAlong = interiorExtentAlong(rotorAxis, w, h, d);
        int maxLayers = TurbineRodSpaceLayout.maxCoilLayersForInterior(interiorAlong);
        if (maxLayers <= 0) {
            return 1;
        }

        TurbineRotorLayout probeLayout = TurbineRotorLayout.from(
                minX, minY, minZ, maxX, maxY, maxZ, w, h, d, maxLayers, rotorAxis);
        int closureIdx = probeLayout.closureInteriorIndex();
        int layersWithCoil = 0;
        for (int layer = interiorAlong - 1; layer > closureIdx; layer--) {
            if (crossSectionHasCoilBlock(level, minX, minY, minZ, maxX, maxY, maxZ, probeLayout, layer, registry)) {
                layersWithCoil++;
            } else if (layersWithCoil > 0) {
                break;
            }
        }
        if (layersWithCoil > 0) {
            return Math.min(layersWithCoil, maxLayers);
        }
        return TurbineRodSpaceLayout.appliedCoilLayerCount(
                interiorAlong, Config.TURBINE_DEFAULT_COIL_LAYER_COUNT.get());
    }

    private static boolean crossSectionHasCoilBlock(
            Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            TurbineRotorLayout layout, int layerIndex, RegistryAccess registry) {
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = minY + 1; y < maxY; y++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    if (layout.interiorIndexFromWorld(x, y, z) != layerIndex) {
                        continue;
                    }
                    BlockState st = level.getBlockState(new BlockPos(x, y, z));
                    if (ElecCoilLoader.isCoilBlock(st, registry)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static FaceCenters computeFaceCenters(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int midX = (minX + maxX) / 2;
        int midY = (minY + maxY) / 2;
        int midZ = (minZ + maxZ) / 2;
        return new FaceCenters(
                new BlockPos(minX, midY, midZ),
                new BlockPos(maxX, midY, midZ),
                new BlockPos(midX, minY, midZ),
                new BlockPos(midX, maxY, midZ),
                new BlockPos(midX, midY, minZ),
                new BlockPos(midX, midY, maxZ));
    }

    private record FaceCenters(
            BlockPos negX, BlockPos posX, BlockPos negY, BlockPos posY, BlockPos negZ, BlockPos posZ) {}

    private static final class RodControllerScan {
        int count;
        boolean onBorder;
        @Nullable Direction axis;
        @Nullable BlockPos lastPos;

    }

    private static RodControllerScan scanRodControllers(
            Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        RodControllerScan scan = new RodControllerScan();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState s = level.getBlockState(new BlockPos(x, y, z));
                    if (!s.is(ModBlocks.TURBINE_ROD_CONTROLLER.get())) {
                        continue;
                    }
                    scan.count++;
                    scan.lastPos = new BlockPos(x, y, z);
                    if (xOnBorder(scan.lastPos, minX, maxX, minY, maxY, minZ, maxZ)) {
                        scan.onBorder = true;
                    }
                    if (s.hasProperty(TurbineRodControllerBlock.FACING)) {
                        scan.axis = s.getValue(TurbineRodControllerBlock.FACING);
                    }
                }
            }
        }
        return scan;
    }

    private static boolean xOnBorder(BlockPos p, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return p.getX() == minX || p.getX() == maxX || p.getY() == minY || p.getY() == maxY
                || p.getZ() == minZ || p.getZ() == maxZ;
    }

    private static List<BlockPos> listRodControllers(
            Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<BlockPos> list = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).is(ModBlocks.TURBINE_ROD_CONTROLLER.get())) {
                        list.add(p.immutable());
                    }
                }
            }
        }
        return list;
    }

    private static int countRodControllersExcept(
            Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockPos except) {
        int count = 0;
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = minY + 1; y < maxY; y++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (p.equals(except)) {
                        continue;
                    }
                    if (level.getBlockState(p).is(ModBlocks.TURBINE_ROD_CONTROLLER.get())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Nullable
    private static BlockPos findExtraRodController(
            Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockPos except) {
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = minY + 1; y < maxY; y++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!p.equals(except) && level.getBlockState(p).is(ModBlocks.TURBINE_ROD_CONTROLLER.get())) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private static Result fail(FailureCode failure, @Nullable BlockPos pos, ValidationReport report) {
        logDebug(report, failure, false);
        return Result.invalid(failure, pos, report);
    }

    private static final int MARKER_COLOR_ERROR = 0xE0FF0000;
    private static final int MARKER_COLOR_COIL_ZONE = 0x80FF8800;
    private static final int MARKER_DURATION_TICKS = 400;

    /**
     * Client preview markers for validation failures (red = error block, orange = computed coil cap).
     */
    public static void sendFailureMarkers(ServerPlayer player, Level level, Result result) {
        if (result.valid() || result.failure() == null) {
            return;
        }
        if (result.failure() == FailureCode.REDSTONE_PORT_COUNT) {
            for (long packed : result.report().redstonePortPositions()) {
                ModPayloads.sendEphemeralPreviewMarker(player, BlockPos.of(packed), MARKER_COLOR_ERROR, MARKER_DURATION_TICKS);
            }
            return;
        }
        if (result.failurePos() != null) {
            ModPayloads.sendEphemeralPreviewMarker(player, result.failurePos(), MARKER_COLOR_ERROR, MARKER_DURATION_TICKS);
        }
        FailureCode code = result.failure();
        if (code != FailureCode.COIL_ZONE_BLOCK && code != FailureCode.ROD_IN_COIL_ZONE) {
            return;
        }
        ValidationReport r = result.report();
        Direction axis = r.growthAxis();
        if (axis == null || r.width() <= 0) {
            return;
        }
        TurbineRotorLayout layout = TurbineRotorLayout.from(
                r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ(),
                r.width(), r.height(), r.length(), r.coilLayersUsed(), axis);
        for (int x = r.minX() + 1; x < r.maxX(); x++) {
            for (int y = r.minY() + 1; y < r.maxY(); y++) {
                for (int z = r.minZ() + 1; z < r.maxZ(); z++) {
                    if (!layout.isCoilZoneWorld(x, y, z)) {
                        continue;
                    }
                    BlockPos p = new BlockPos(x, y, z);
                    if (result.failurePos() != null && result.failurePos().equals(p)) {
                        continue;
                    }
                    ModPayloads.sendEphemeralPreviewMarker(player, p, MARKER_COLOR_COIL_ZONE, MARKER_DURATION_TICKS);
                }
            }
        }
    }

    public static net.minecraft.network.chat.Component failureMessage(TurbineValidation.Result result) {
        if (result.valid() || result.failure() == null) {
            return net.minecraft.network.chat.Component.translatable("message.colossal_reactors.turbine_valid");
        }
        return failureMessage(result.failure(), result.report());
    }

    public static net.minecraft.network.chat.Component failureMessage(
            FailureCode failure, ValidationReport r) {
        return switch (failure) {
            case BAD_START -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.bad_start");
            case CONTROLLER_FACE_Y -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.controller_face_y");
            case TOO_LARGE -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.too_large",
                    r.width(), r.height(), r.length());
            case NO_ROD_CONTROLLER -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.no_rod_controller");
            case MULTIPLE_ROD_CONTROLLERS -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.multiple_rod_controllers", r.rodControllersFound());
            case ROD_CONTROLLER_NO_COLUMN -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.rod_controller_no_column");
            case ROD_CONTROLLER_BORDER -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.rod_controller_border");
            case ROD_CONTROLLER_FACING -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.rod_controller_facing");
            case ROD_CONTROLLER_AXIS_MISMATCH -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.rod_controller_axis_mismatch");
            case SHELL_GAP -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.shell_gap");
            case ROD_IN_COIL_ZONE -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.rod_in_coil_zone");
            case COIL_ZONE_BLOCK -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.coil_zone_block");
            case ROD_NOT_TO_FLOOR -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.rod_not_to_floor");
            case UNBALANCED_BLADES -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.unbalanced_blades");
            case EXTERIOR_CONTROLLER_COUNT -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.exterior_controller_count", r.exteriorControllers());
            case INTERIOR_UNKNOWN -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.interior_unknown");
            case REDSTONE_PORT_COUNT -> net.minecraft.network.chat.Component.translatable(
                    "message.colossal_reactors.turbine_invalid.redstone_port_count");
        };
    }

    public static net.minecraft.network.chat.Component failureMessage(int failureOrdinal) {
        if (failureOrdinal < 0 || failureOrdinal >= FailureCode.values().length) {
            return net.minecraft.network.chat.Component.translatable("message.colossal_reactors.turbine_invalid");
        }
        return failureMessage(FailureCode.values()[failureOrdinal], ValidationReport.empty());
    }

    private static void logDebug(ValidationReport report, @Nullable FailureCode failure, boolean valid) {
        if (!Boolean.TRUE.equals(Config.TURBINE_VALIDATION_DEBUG.get())) {
            return;
        }
        ColossalReactors.LOGGER.info(
                "[TurbineValidation] valid={} failure={} bounds=[{} {} {}]..[{} {} {}] size={}x{}x{} "
                        + "growthAxis={} closureWorld={} closureIdx={} coilFillStart={} coilLayers={} rodCtrl={} extCtrl={}",
                valid, failure,
                report.minX(), report.minY(), report.minZ(), report.maxX(), report.maxY(), report.maxZ(),
                report.width(), report.height(), report.length(),
                report.growthAxis(), report.closureCoord(), report.closureInteriorIndex(),
                report.coilZoneStartInterior(), report.coilLayersUsed(), report.rodControllersFound(),
                report.exteriorControllers());
    }

    /**
     * Each rod column along the growth axis must be continuous from the rotor floor (layer 0).
     */
    @Nullable
    private static BlockPos findRodNotReachingFloor(Level level, TurbineRotorLayout layout) {
        int layers = layout.closureInteriorIndex();
        for (int ca = 0; ca < layout.crossSizeA(); ca++) {
            for (int cb = 0; cb < layout.crossSizeB(); cb++) {
                for (int layer = 1; layer < layers; layer++) {
                    BlockPos pos = layout.rodPos(layer, ca, cb);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(ModBlocks.TURBINE_ROD.get())) {
                        continue;
                    }
                    BlockPos below = layout.rodPos(layer - 1, ca, cb);
                    if (!level.getBlockState(below).is(ModBlocks.TURBINE_ROD.get())) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Fails when any rod carries a blade count that is not a multiple of four (complete rings).
     */
    @Nullable
    private static BlockPos findUnbalancedBladesOnRod(Level level, TurbineRotorLayout layout) {
        Direction growthAxis = layout.growthAxis();
        int layers = layout.closureInteriorIndex();
        for (int layer = 0; layer < layers; layer++) {
            for (int ca = 0; ca < layout.crossSizeA(); ca++) {
                for (int cb = 0; cb < layout.crossSizeB(); cb++) {
                    BlockPos pos = layout.rodPos(layer, ca, cb);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(ModBlocks.TURBINE_ROD.get())
                            || !state.hasProperty(TurbineRodBlock.FACING)) {
                        continue;
                    }
                    Direction rodAxis = state.getValue(TurbineRodBlock.FACING);
                    if (rodAxis.getAxis() != growthAxis.getAxis()) {
                        continue;
                    }
                    int blades = TurbineBladePlacement.totalBladesOnRod(level, pos, rodAxis);
                    if (blades > 0 && blades % 4 != 0) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Production blade count in the rotor zone (matches builder simulation / manual counts).
     * Uses placed {@link ModBlocks#TURBINE_BLADE} blocks in the rod zone, not {@link TurbineBladePlacement#totalBladesOnRod}
     * per rod — contiguous-depth counting can under-count full rings (e.g. 156 vs 180).
     * Balance is already enforced by {@link #findUnbalancedBladesOnRod} before this runs.
     */
    private static int countValidBalancedBlades(
            Level level, TurbineRotorLayout layout,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return countBladeBlocksInRodZone(level, layout, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static int countBladeBlocksInRodZone(
            Level level, TurbineRotorLayout layout,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int total = 0;
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = minY + 1; y < maxY; y++) {
                for (int z = minZ + 1; z < maxZ; z++) {
                    if (!layout.isInRodZone(x, y, z)) {
                        continue;
                    }
                    if (level.getBlockState(new BlockPos(x, y, z)).is(ModBlocks.TURBINE_BLADE.get())) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    @Nullable
    public static Direction findRodControllerAxis(
            Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        RodControllerScan scan = scanRodControllers(level, minX, minY, minZ, maxX, maxY, maxZ);
        return scan.count == 1 ? scan.axis : null;
    }

    /** @deprecated Use {@link #findRodControllerAxis(Level, int, int, int, int, int, int)}. */
    @Deprecated
    @Nullable
    public static Direction findRodControllerAxis(
            Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int coilLayerCount) {
        return findRodControllerAxis(level, minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Shell border: any casing, glass, or port in {@link ModBlockTags#TURBINE_SHELL}. */
    public static boolean isShellBlock(BlockState state) {
        return state.is(ModBlockTags.TURBINE_SHELL);
    }

    public static boolean isTurbinePowerPort(BlockState state) {
        return state.is(ModBlocks.TURBINE_POWER_PORT.get()) || state.is(ModBlocks.TURBINE_HIGH_COND_POWER_PORT.get());
    }

    private static boolean isShellOrRodController(BlockState state) {
        return isShellBlock(state) || isRodController(state);
    }

    private static boolean isRodController(BlockState state) {
        return state.is(ModBlocks.TURBINE_ROD_CONTROLLER.get());
    }

    private static boolean canStepDepth(Level level, BlockPos pos, Direction into) {
        BlockPos next = pos.relative(into);
        BlockState nextState = level.getBlockState(next);
        BlockState current = level.getBlockState(pos);
        if (isShellOrRodController(nextState)) {
            return true;
        }
        if (nextState.is(ModBlocks.TURBINE_ROD.get()) || nextState.is(ModBlocks.TURBINE_BLADE.get())) {
            return true;
        }
        return !isShellOrRodController(current)
                && (nextState.isAir() || ElecCoilLoader.isCoilBlock(nextState, level.registryAccess()));
    }

    private static int countControllersOnFaces(Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int count = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (level.getBlockState(new BlockPos(minX - 1, y, z)).is(ModBlocks.TURBINE_CONTROLLER.get())) {
                    count++;
                }
                if (level.getBlockState(new BlockPos(maxX + 1, y, z)).is(ModBlocks.TURBINE_CONTROLLER.get())) {
                    count++;
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (level.getBlockState(new BlockPos(x, minY - 1, z)).is(ModBlocks.TURBINE_CONTROLLER.get())) {
                    count++;
                }
                if (level.getBlockState(new BlockPos(x, maxY + 1, z)).is(ModBlocks.TURBINE_CONTROLLER.get())) {
                    count++;
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (level.getBlockState(new BlockPos(x, y, minZ - 1)).is(ModBlocks.TURBINE_CONTROLLER.get())) {
                    count++;
                }
                if (level.getBlockState(new BlockPos(x, y, maxZ + 1)).is(ModBlocks.TURBINE_CONTROLLER.get())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static final class ValidationReportBuilder {
            private int minX, minY, minZ, maxX, maxY, maxZ;
            private int width, height, length;
            private BlockPos faceCenterNegX, faceCenterPosX, faceCenterNegY, faceCenterPosY, faceCenterNegZ, faceCenterPosZ;
            private int exteriorControllers;
            private Direction growthAxis;
            private int closureCoord;
            private int closureInteriorIndex;
            private int coilZoneStartInterior;
            private int coilLayersUsed;
            private int rodControllersFound;
            private int redstonePortCount;
            private long[] redstonePortPositions = new long[0];

            ValidationReportBuilder redstonePorts(List<BlockPos> positions) {
                this.redstonePortCount = positions.size();
                this.redstonePortPositions = new long[positions.size()];
                for (int i = 0; i < positions.size(); i++) {
                    this.redstonePortPositions[i] = positions.get(i).asLong();
                }
                return this;
            }

            ValidationReportBuilder bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int w, int h, int l) {
                this.minX = minX;
                this.minY = minY;
                this.minZ = minZ;
                this.maxX = maxX;
                this.maxY = maxY;
                this.maxZ = maxZ;
                this.width = w;
                this.height = h;
                this.length = l;
                return this;
            }

            ValidationReportBuilder faceCenters(FaceCenters centers) {
                this.faceCenterNegX = centers.negX();
                this.faceCenterPosX = centers.posX();
                this.faceCenterNegY = centers.negY();
                this.faceCenterPosY = centers.posY();
                this.faceCenterNegZ = centers.negZ();
                this.faceCenterPosZ = centers.posZ();
                return this;
            }

            ValidationReportBuilder exteriorControllers(int count) {
                this.exteriorControllers = count;
                return this;
            }

            ValidationReportBuilder growthAxis(Direction axis) {
                this.growthAxis = axis;
                return this;
            }

            ValidationReportBuilder plane(int closureWorld, int coilFillStart, int closureInterior, int layers) {
                this.closureCoord = closureWorld;
                this.closureInteriorIndex = closureInterior;
                this.coilZoneStartInterior = coilFillStart;
                this.coilLayersUsed = layers;
                return this;
            }

            ValidationReportBuilder rodControllersFound(int count) {
                this.rodControllersFound = count;
                return this;
            }

            ValidationReport build() {
                return new ValidationReport(
                        minX, minY, minZ, maxX, maxY, maxZ, width, height, length,
                        faceCenterNegX, faceCenterPosX, faceCenterNegY, faceCenterPosY, faceCenterNegZ, faceCenterPosZ,
                        exteriorControllers, growthAxis, closureCoord, closureInteriorIndex,
                        coilZoneStartInterior, coilLayersUsed, rodControllersFound,
                        redstonePortCount, redstonePortPositions);
            }
    }
}
