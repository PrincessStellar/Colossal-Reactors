package net.unfamily.colossal_reactors.turbine;

import net.unfamily.colossal_reactors.Config;

/**
 * Interior layout for turbine builder: rod space below, coil layers at top of interior.
 */
public final class TurbineRodSpaceLayout {

    private static final int FRAME_INSET = 1;

    private TurbineRodSpaceLayout() {}

    public static int interiorWidth(int turbineWidth) {
        return Math.max(0, turbineWidth - 2 * FRAME_INSET);
    }

    public static int interiorHeight(int turbineHeight) {
        return Math.max(0, turbineHeight - 2 * FRAME_INSET);
    }

    public static int interiorDepth(int turbineDepth) {
        return Math.max(0, turbineDepth - 2 * FRAME_INSET);
    }

    public static int defaultCoilLayerCount() {
        return Config.TURBINE_DEFAULT_COIL_LAYER_COUNT.get();
    }

    /** Maximum coil layers that fit (at least one interior layer remains for rods). */
    public static int maxCoilLayersForInterior(int interiorAlong) {
        if (interiorAlong <= 1) {
            return 1;
        }
        return interiorAlong - 1;
    }

    /**
     * Maximum value for the builder/GUI coil-layer setting (same as {@link #maxCoilLayersForInterior}).
     */
    public static int maxCoilLayerSettingForInterior(int interiorAlong) {
        return Math.max(1, maxCoilLayersForInterior(interiorAlong));
    }

    /**
     * Coil layers used in code (build, layout, simulation) from the stored GUI setting, clamped to interior.
     */
    public static int appliedCoilLayerCount(int interiorAlong, int storedSetting) {
        int maxSetting = maxCoilLayerSettingForInterior(interiorAlong);
        int stored = storedSetting >= 0
                ? storedSetting
                : Math.min(defaultCoilLayerCount(), maxSetting);
        stored = Math.min(Math.max(1, stored), maxSetting);
        return coilLayerCount(interiorAlong, stored);
    }

    /** Clamps an already-resolved layer count to the interior (used when the value is not a GUI setting). */
    public static int coilLayerCount(int interiorAlong, int layerCount) {
        return Math.min(Math.max(1, layerCount), maxCoilLayersForInterior(interiorAlong));
    }

    /** Interior index of the closure deck for a resolved coil layer count. */
    public static int closureInteriorIndexForCoilCount(int interiorAlong, int resolvedCoilLayers) {
        return Math.max(0, interiorAlong - resolvedCoilLayers - 1);
    }

    /** First interior index for coil fill for a resolved coil layer count. */
    public static int coilFillStartInteriorForCoilCount(int interiorAlong, int resolvedCoilLayers) {
        return closureInteriorIndexForCoilCount(interiorAlong, resolvedCoilLayers) + 1;
    }

    /** Interior index of the closure deck (between rod space and coil fill). */
    public static int closureInteriorIndex(int interiorAlong, int appliedLayers) {
        return closureInteriorIndexForCoilCount(interiorAlong, coilLayerCount(interiorAlong, appliedLayers));
    }

    /** First interior index for coil fill (air/coil blocks), directly above closure along growth. */
    public static int coilFillStartInterior(int interiorAlong, int appliedLayers) {
        return coilFillStartInteriorForCoilCount(interiorAlong, coilLayerCount(interiorAlong, appliedLayers));
    }

    /** Rod layer count (indices {@code 0 .. closureInteriorIndex - 1}). */
    public static int rodLayerCount(int interiorAlong, int appliedLayers) {
        return closureInteriorIndex(interiorAlong, appliedLayers);
    }

    /** @deprecated Use {@link #coilFillStartInterior}. */
    public static int coilZoneStartY(int interiorHeight, int appliedLayers) {
        return coilFillStartInterior(interiorHeight, appliedLayers);
    }

    /** True if interior index is in the coil fill zone (not the closure deck). */
    public static boolean isCoilLayerY(int interiorY, int interiorHeight, int coilLayers) {
        return interiorY >= coilFillStartInterior(interiorHeight, coilLayers);
    }

    /**
     * Extra inset inside the interior for rod-space indices. Zero: {@link #interiorWidth} already excludes the shell.
     */
    public static int rodSpaceInset() {
        return 0;
    }
}
