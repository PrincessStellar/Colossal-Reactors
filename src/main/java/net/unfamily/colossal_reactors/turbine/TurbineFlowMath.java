package net.unfamily.colossal_reactors.turbine;

import net.unfamily.colossal_reactors.Config;

/**
 * Steam-flow depth bonus from useful coil layers (height + footprint target, blade gate).
 * Material flow (RC-column average of flow_coe/flow_max) is applied separately by callers.
 */
public final class TurbineFlowMath {

    private TurbineFlowMath() {}

    /**
     * Useful coil-layer target for the given interior height and cross-section.
     * Height uses exterior along ({@code interiorAlong + 2} frame) so defaults match
     * config examples (16→2, 32→3, 64→6). Footprint still uses interior cross area.
     * Stacking coil layers above this does not increase the depth bonus.
     */
    public static int targetCoilLayers(int interiorAlong, int crossA, int crossB) {
        int div = Math.max(1, Config.TURBINE_FLOW_LAYER_HEIGHT_DIV.get());
        int targetMax = Math.max(1, Config.TURBINE_FLOW_LAYER_TARGET_MAX.get());
        int footprintRef = Math.max(1, Config.TURBINE_FLOW_LAYER_FOOTPRINT_REF.get());
        // Exterior along the rotor: interior + top/bottom (or end) casing.
        int exteriorAlong = Math.max(0, interiorAlong) + 2;
        int area = Math.max(0, crossA) * Math.max(0, crossB);

        int rawTarget = (int) Math.round(exteriorAlong / (double) div);
        double footFactor = Math.min(1.0, area / (double) footprintRef);
        int scaled = (int) Math.round(rawTarget * footFactor);
        return Math.max(1, Math.min(targetMax, scaled));
    }

    /**
     * Depth bonus fraction in {@code [0, MAX]} from useful layers (before blade gate).
     * {@code usefulL == 1} → 0; asymptote {@code MAX} as usefulL grows.
     */
    public static double layerDepthBonus(int actualCoilLayers, int targetLayers) {
        double max = Math.max(0.0, Config.TURBINE_FLOW_LAYER_BONUS_MAX.get());
        if (max <= 0.0) {
            return 0.0;
        }
        int useful = Math.max(1, Math.min(Math.max(1, actualCoilLayers), Math.max(1, targetLayers)));
        return max * (1.0 - 1.0 / useful);
    }

    public static double bladeGate(int validBladeCount) {
        int gate = Math.max(1, Config.TURBINE_FLOW_LAYER_BLADE_GATE.get());
        return Math.min(1.0, Math.max(0, validBladeCount) / (double) gate);
    }

    /**
     * Final flow efficiency: {@code materialFlow * (1 + layerBonus * bladeGate)}.
     */
    public static double effectiveFlowEfficiency(
            double materialFlow,
            int actualCoilLayers,
            int interiorAlong,
            int crossA,
            int crossB,
            int validBladeCount) {
        int target = targetCoilLayers(interiorAlong, crossA, crossB);
        double bonus = layerDepthBonus(actualCoilLayers, target) * bladeGate(validBladeCount);
        return Math.max(0.0, materialFlow) * (1.0 + bonus);
    }
}
