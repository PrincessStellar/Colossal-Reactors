package net.unfamily.colossal_reactors.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.unfamily.colossal_reactors.block.ReactorBuilderBlock;
import net.unfamily.colossal_reactors.block.TurbineBuilderBlock;
import net.unfamily.colossal_reactors.blockentity.ReactorBuilderBlockEntity;
import net.unfamily.colossal_reactors.blockentity.TurbineBuilderBlockEntity;
import net.unfamily.colossal_reactors.preview.BuilderPreviewMarkerLogic;
import net.unfamily.iskalib.client.marker.AreaBorderRenderer;
import net.unfamily.iskalib.client.marker.MarkRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

/**
 * Per-player client footprint preview: shell via {@link AreaBorderRenderer},
 * interior markers via depth-tested {@link MarkRenderer} (merged into boxes where possible).
 */
public final class BuilderPreviewTracker {

    public static final int BUILDER_PREVIEW_DURATION_TICKS = 7_200_000;

    private static final int WORLD_POLL_INTERVAL_TICKS = 5;
    private static final int REFRESH_COOLDOWN_TICKS = 20;
    private static final int PERIODIC_RECONCILE_INTERVAL_TICKS = 40;

    private static final int COLOR_OCCUPIED = BuilderPreviewMarkerLogic.COLOR_OCCUPIED;
    private static final int COLOR_ROD = BuilderPreviewMarkerLogic.COLOR_ROD;
    private static final int COLOR_ROD_CONTROLLER = BuilderPreviewMarkerLogic.COLOR_ROD_CONTROLLER;
    private static final int COLOR_FRAME_EDGE = BuilderPreviewMarkerLogic.COLOR_FRAME_EDGE;
    private static final int COLOR_CLOSURE_DECK = BuilderPreviewMarkerLogic.COLOR_CLOSURE_DECK;
    private static final int COLOR_BLADE = BuilderPreviewMarkerLogic.COLOR_BLADE;

    private static final int[] BASELINE_COLOR_PRIORITY = {
            COLOR_ROD_CONTROLLER,
            COLOR_CLOSURE_DECK,
            COLOR_ROD,
            COLOR_BLADE,
    };

    private static final Set<BlockPos> activePreviews = ConcurrentHashMap.newKeySet();
    private static final Map<BlockPos, Integer> lastWorldStateHashByBuilder = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Integer> refreshCooldownByBuilder = new ConcurrentHashMap<>();
    private static final Set<BlockPos> pendingWorldRefreshByBuilder = ConcurrentHashMap.newKeySet();
    private static final Map<BlockPos, Integer> activeFootprintGeneration = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Map<BlockPos, Set<Integer>>> markerLayersByOwner = new ConcurrentHashMap<>();
    private static final Set<BlockPos> pendingLayerResetByOwner = ConcurrentHashMap.newKeySet();

    private static final int INVALID_FOOTPRINT_GENERATION = -1;

    private static long lastPollGameTime = Long.MIN_VALUE;
    private static long lastPeriodicReconcileGameTime = Long.MIN_VALUE;

    private BuilderPreviewTracker() {}

    public static boolean isPreviewActive(BlockPos builderOrigin) {
        return builderOrigin != null && activePreviews.contains(builderOrigin.immutable());
    }

    public static void setPreviewActive(BlockPos builderOrigin, boolean active) {
        if (builderOrigin == null) {
            return;
        }
        BlockPos key = builderOrigin.immutable();
        if (active) {
            activePreviews.add(key);
            Level level = Minecraft.getInstance().level;
            if (level != null) {
                refreshAreaBorder(level, key);
            }
        } else {
            activePreviews.remove(key);
            clearTrackingForBuilder(key);
            clearRendererForBuilder(key);
        }
    }

    public static void clearMarkersForBuilder(BlockPos builderOrigin) {
        if (builderOrigin == null) {
            return;
        }
        BlockPos key = builderOrigin.immutable();
        activeFootprintGeneration.put(key, INVALID_FOOTPRINT_GENERATION);
        clearRendererForBuilder(key);
    }

    public static void applyFootprintClear(BlockPos builderOrigin, int footprintGeneration) {
        if (builderOrigin == null) {
            return;
        }
        BlockPos key = builderOrigin.immutable();
        activeFootprintGeneration.put(key, footprintGeneration);
        pendingLayerResetByOwner.add(key);
        clearRendererForBuilder(key);
        Level level = Minecraft.getInstance().level;
        if (level != null && activePreviews.contains(key)) {
            refreshAreaBorder(level, key);
        }
    }

    public static void deactivateBuilder(BlockPos builderOrigin) {
        setPreviewActive(builderOrigin, false);
    }

    public static void clearAll() {
        for (BlockPos key : List.copyOf(activePreviews)) {
            clearRendererForBuilder(key);
        }
        activePreviews.clear();
        lastWorldStateHashByBuilder.clear();
        refreshCooldownByBuilder.clear();
        pendingWorldRefreshByBuilder.clear();
        activeFootprintGeneration.clear();
        markerLayersByOwner.clear();
        pendingLayerResetByOwner.clear();
        lastPollGameTime = Long.MIN_VALUE;
        lastPeriodicReconcileGameTime = Long.MIN_VALUE;
    }

    public static void onFootprintRefreshRequested(Level level, BlockPos builderOrigin) {
        if (level == null || builderOrigin == null) {
            return;
        }
        BlockPos key = builderOrigin.immutable();
        clearMarkersForBuilder(key);
        reconcileMarkersForBuilder(key);
        seedWorldHash(level, key);
        noteRefreshRequested(key);
    }

    public static void noteRefreshRequested(BlockPos builderOrigin) {
        if (builderOrigin == null) {
            return;
        }
        refreshCooldownByBuilder.put(builderOrigin.immutable(), REFRESH_COOLDOWN_TICKS);
    }

    public static void seedWorldHash(Level level, BlockPos builderOrigin) {
        if (level == null || builderOrigin == null) {
            return;
        }
        AABB volume = resolveFootprintVolume(level, builderOrigin);
        if (volume != null) {
            lastWorldStateHashByBuilder.put(builderOrigin.immutable(), computeOccupiedBlocksHash(level, volume));
        }
    }

    public static void onBlockInPreviewChanged(Level level, BlockPos worldPos) {
        if (level == null || worldPos == null || activePreviews.isEmpty()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(worldPos);
        for (BlockPos builder : List.copyOf(activePreviews)) {
            AABB volume = resolveFootprintVolume(level, builder);
            if (volume == null || !volume.contains(center)) {
                continue;
            }
            reconcileMarkersForBuilder(builder);
            lastWorldStateHashByBuilder.put(builder, computeOccupiedBlocksHash(level, volume));
        }
    }

    public static void addMarker(BlockPos builderOrigin, BlockPos worldPos, int color, int durationTicks, int footprintGeneration) {
        if (worldPos == null || builderOrigin == null || builderOrigin.equals(BlockPos.ZERO)) {
            return;
        }
        BlockPos owner = builderOrigin.immutable();
        if (footprintGeneration != activeFootprintGeneration.getOrDefault(owner, INVALID_FOOTPRINT_GENERATION)) {
            return;
        }
        if (pendingLayerResetByOwner.remove(owner)) {
            clearLayerCacheForBuilder(owner);
        }
        // Shell edges are AreaBorder; ignore legacy frame-edge packets.
        if (color == COLOR_FRAME_EDGE) {
            return;
        }
        BlockPos cell = worldPos.immutable();
        Level level = Minecraft.getInstance().level;
        if (level != null && color == COLOR_OCCUPIED) {
            BlockState state = level.getBlockState(cell);
            if (state.isAir() || state.canBeReplaced()) {
                return;
            }
        }
        int duration = durationTicks > 0 ? durationTicks : BUILDER_PREVIEW_DURATION_TICKS;
        markerLayersByOwner
                .computeIfAbsent(owner, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(cell, k -> ConcurrentHashMap.newKeySet())
                .add(color);
        syncResolvedMarker(owner, cell, duration);
    }

    public static List<BlockPos> pollBuildersNeedingWorldRefresh(Level level) {
        if (level == null || activePreviews.isEmpty()) {
            return List.of();
        }
        long gameTime = level.getGameTime();
        if (gameTime - lastPollGameTime < WORLD_POLL_INTERVAL_TICKS) {
            return List.of();
        }
        lastPollGameTime = gameTime;

        for (BlockPos key : List.copyOf(refreshCooldownByBuilder.keySet())) {
            int remaining = refreshCooldownByBuilder.getOrDefault(key, 0) - WORLD_POLL_INTERVAL_TICKS;
            if (remaining <= 0) {
                refreshCooldownByBuilder.remove(key);
            } else {
                refreshCooldownByBuilder.put(key, remaining);
            }
        }

        List<BlockPos> needsRefresh = new ArrayList<>();
        for (BlockPos builder : activePreviews) {
            AABB volume = resolveFootprintVolume(level, builder);
            if (volume == null) {
                continue;
            }
            int hash = computeOccupiedBlocksHash(level, volume);
            Integer last = lastWorldStateHashByBuilder.get(builder);
            if (last != null && last != hash) {
                reconcileMarkersForBuilder(builder);
                lastWorldStateHashByBuilder.put(builder, hash);
                if (refreshCooldownByBuilder.containsKey(builder)) {
                    pendingWorldRefreshByBuilder.add(builder);
                } else {
                    scheduleWorldRefresh(builder, needsRefresh);
                }
            } else if (last == null) {
                lastWorldStateHashByBuilder.put(builder, hash);
            }
        }

        for (BlockPos builder : List.copyOf(pendingWorldRefreshByBuilder)) {
            if (!refreshCooldownByBuilder.containsKey(builder)) {
                pendingWorldRefreshByBuilder.remove(builder);
                scheduleWorldRefresh(builder, needsRefresh);
            }
        }
        return needsRefresh;
    }

    public static void tickPeriodicReconcile(Level level) {
        if (level == null || activePreviews.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime - lastPeriodicReconcileGameTime < PERIODIC_RECONCILE_INTERVAL_TICKS) {
            return;
        }
        lastPeriodicReconcileGameTime = gameTime;
        for (BlockPos builder : List.copyOf(activePreviews)) {
            reconcileMarkersForBuilder(builder);
        }
    }

    private static void scheduleWorldRefresh(BlockPos builder, List<BlockPos> needsRefresh) {
        refreshCooldownByBuilder.put(builder, REFRESH_COOLDOWN_TICKS);
        needsRefresh.add(builder);
    }

    private static void clearTrackingForBuilder(BlockPos builderKey) {
        lastWorldStateHashByBuilder.remove(builderKey);
        refreshCooldownByBuilder.remove(builderKey);
        pendingWorldRefreshByBuilder.remove(builderKey);
        activeFootprintGeneration.remove(builderKey);
        pendingLayerResetByOwner.remove(builderKey);
        clearLayerCacheForBuilder(builderKey);
    }

    private static void clearLayerCacheForBuilder(BlockPos owner) {
        markerLayersByOwner.remove(owner);
    }

    private static void clearRendererForBuilder(BlockPos owner) {
        MarkRenderer.getInstance().clearBillboardMarkersForOwner(owner);
        AreaBorderRenderer.getInstance().clearArea(areaBorderKey(owner));
    }

    private static Object areaBorderKey(BlockPos owner) {
        return "cr_builder_area_" + owner.asLong();
    }

    private static void refreshAreaBorder(Level level, BlockPos owner) {
        AABB volume = resolveFootprintVolume(level, owner);
        if (volume == null) {
            AreaBorderRenderer.getInstance().clearArea(areaBorderKey(owner));
            return;
        }
        int minX = (int) Math.floor(volume.minX);
        int minY = (int) Math.floor(volume.minY);
        int minZ = (int) Math.floor(volume.minZ);
        int maxX = (int) Math.floor(volume.maxX - 1e-6);
        int maxY = (int) Math.floor(volume.maxY - 1e-6);
        int maxZ = (int) Math.floor(volume.maxZ - 1e-6);
        AreaBorderRenderer.getInstance().showArea(
                areaBorderKey(owner),
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ),
                AreaBorderRenderer.DEFAULT_MACHINE_COLOR,
                0);
    }

    private static void reconcileMarkersForBuilder(BlockPos owner) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        refreshLayerCacheFromWorld(level, owner);
        refreshAllMarkersForOwner(owner, BUILDER_PREVIEW_DURATION_TICKS);
        if (activePreviews.contains(owner)) {
            refreshAreaBorder(level, owner);
        }
    }

    private static void refreshLayerCacheFromWorld(Level level, BlockPos owner) {
        BlockEntity be = level.getBlockEntity(owner);
        if (be == null) {
            return;
        }
        Map<BlockPos, Set<Integer>> fresh = new ConcurrentHashMap<>();
        BuilderPreviewMarkerLogic.MarkerSink sink = (worldPos, color) -> {
            if (color == COLOR_FRAME_EDGE) {
                return;
            }
            fresh.computeIfAbsent(worldPos.immutable(), k -> ConcurrentHashMap.newKeySet()).add(color);
        };
        if (be instanceof ReactorBuilderBlockEntity reactor) {
            BuilderPreviewMarkerLogic.forEachReactorMarker(level, reactor, owner, sink);
        } else if (be instanceof TurbineBuilderBlockEntity turbine) {
            BuilderPreviewMarkerLogic.forEachTurbineMarker(level, turbine, owner, sink);
        } else {
            return;
        }
        markerLayersByOwner.put(owner, fresh);
    }

    private static void syncResolvedMarker(BlockPos owner, BlockPos worldPos, int durationTicks) {
        // Full refresh keeps box merges coherent when a single cell arrives mid-batch.
        refreshAllMarkersForOwner(owner, durationTicks);
    }

    private static void refreshAllMarkersForOwner(BlockPos owner, int durationTicks) {
        Map<BlockPos, Set<Integer>> layers = markerLayersByOwner.get(owner);
        if (layers == null || layers.isEmpty()) {
            clearRendererForBuilder(owner);
            Level level = Minecraft.getInstance().level;
            if (level != null && activePreviews.contains(owner)) {
                refreshAreaBorder(level, owner);
            }
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        MarkRenderer.getInstance().clearBillboardMarkersForOwner(owner);
        Map<BlockPos, Integer> resolved = new HashMap<>();
        for (Map.Entry<BlockPos, Set<Integer>> entry : layers.entrySet()) {
            Integer color = resolveDisplayColor(level, entry.getKey(), entry.getValue());
            if (color != null) {
                resolved.put(entry.getKey(), color);
            }
        }
        emitMergedBoxes(owner, resolved, durationTicks);
        if (activePreviews.contains(owner)) {
            refreshAreaBorder(level, owner);
        }
    }

    /** Greedy merge of same-color cells into inclusive AABBs (fewer draw calls). */
    private static void emitMergedBoxes(BlockPos owner, Map<BlockPos, Integer> resolved, int durationTicks) {
        Set<BlockPos> remaining = new HashSet<>(resolved.keySet());
        MarkRenderer renderer = MarkRenderer.getInstance();
        while (!remaining.isEmpty()) {
            BlockPos start = remaining.iterator().next();
            int color = resolved.get(start);
            int x0 = start.getX();
            int y0 = start.getY();
            int z0 = start.getZ();
            int x1 = x0;
            int y1 = y0;
            int z1 = z0;

            while (sameColor(resolved, remaining, x1 + 1, y0, z0, x1 + 1, y1, z1, color)) {
                x1++;
            }
            while (sameColor(resolved, remaining, x0, y1 + 1, z0, x1, y1 + 1, z1, color)) {
                y1++;
            }
            while (sameColor(resolved, remaining, x0, y0, z1 + 1, x1, y1, z1 + 1, color)) {
                z1++;
            }

            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        remaining.remove(new BlockPos(x, y, z));
                    }
                }
            }
            renderer.addOwnedBox(owner, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1),
                    color, durationTicks, false);
        }
    }

    private static boolean sameColor(Map<BlockPos, Integer> resolved, Set<BlockPos> remaining,
                                     int x0, int y0, int z0, int x1, int y1, int z1, int color) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!remaining.contains(p) || resolved.getOrDefault(p, Integer.MIN_VALUE) != color) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Nullable
    private static Integer resolveDisplayColor(Level level, BlockPos worldPos, Set<Integer> layers) {
        if (layers.contains(COLOR_OCCUPIED)) {
            BlockState state = level.getBlockState(worldPos);
            if (!state.isAir() && !state.canBeReplaced()) {
                return COLOR_OCCUPIED;
            }
        }
        for (int priority : BASELINE_COLOR_PRIORITY) {
            if (layers.contains(priority)) {
                return priority;
            }
        }
        for (int color : layers) {
            if (color != COLOR_OCCUPIED && color != COLOR_FRAME_EDGE) {
                return color;
            }
        }
        return null;
    }

    private static AABB resolveFootprintVolume(Level level, BlockPos builderPos) {
        BlockEntity be = level.getBlockEntity(builderPos);
        BlockState builderState = level.getBlockState(builderPos);
        if (be instanceof ReactorBuilderBlockEntity reactor && builderState.getBlock() instanceof ReactorBuilderBlock) {
            Direction facing = builderState.getValue(ReactorBuilderBlock.FACING);
            return ReactorBuilderBlockEntity.getReactorVolumeAABB(
                    builderPos, facing,
                    reactor.getSizeLeft(), reactor.getSizeRight(),
                    reactor.getSizeHeight(), reactor.getSizeDepth());
        }
        if (be instanceof TurbineBuilderBlockEntity turbine && builderState.getBlock() instanceof TurbineBuilderBlock) {
            Direction facing = builderState.getValue(TurbineBuilderBlock.FACING);
            return TurbineBuilderBlockEntity.getTurbineVolumeAABB(
                    builderPos, facing,
                    turbine.getSizeLeft(), turbine.getSizeRight(),
                    turbine.getSizeHeight(), turbine.getSizeDepth());
        }
        return null;
    }

    private static int computeOccupiedBlocksHash(Level level, AABB volume) {
        int minX = (int) Math.floor(volume.minX);
        int minY = (int) Math.floor(volume.minY);
        int minZ = (int) Math.floor(volume.minZ);
        int maxX = (int) Math.floor(volume.maxX - 1e-6);
        int maxY = (int) Math.floor(volume.maxY - 1e-6);
        int maxZ = (int) Math.floor(volume.maxZ - 1e-6);

        int hash = 1;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (!state.isAir() && !state.canBeReplaced()) {
                        hash = 31 * hash + x;
                        hash = 31 * hash + y;
                        hash = 31 * hash + z;
                        hash = 31 * hash + Block.getId(state);
                    }
                }
            }
        }
        return hash;
    }
}
