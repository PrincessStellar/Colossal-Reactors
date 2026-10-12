package net.unfamily.colossal_reactors.turbine;

/**
 * Rod column placement for turbine builder preview/build.
 * Efficient / Productive affect blade ring growth only ({@link #targetBladeRingForLayer}), not which columns get rods.
 */
public final class TurbineRodPatternLogic {

    public static final int PATTERN_EFFICIENT = 0;
    public static final int PATTERN_PRODUCTIVE = 1;
    /** Frame + partition wall + coils only (no rods / blades / rod controller). */
    public static final int PATTERN_CUSTOM = 2;
    /** Frame + partition wall only (no rods / blades / coils / rod controller). */
    public static final int PATTERN_NONE = 3;

    private TurbineRodPatternLogic() {}

    public static int rodSpaceWidth(int turbineWidth) {
        return TurbineRodSpaceLayout.interiorWidth(turbineWidth);
    }

    public static int rodSpaceHeight(int turbineHeight, int coilLayers) {
        int ih = TurbineRodSpaceLayout.interiorHeight(turbineHeight);
        return TurbineRodSpaceLayout.rodLayerCount(ih, coilLayers);
    }

    public static int rodSpaceDepth(int turbineDepth) {
        return TurbineRodSpaceLayout.interiorDepth(turbineDepth);
    }

    /**
     * Single rod column at the primary center (aligned with the one {@link TurbineRodControllerLayout} cell).
     * Efficient / Productive change {@link #targetBladeRingForLayer} only, not which column gets rods.
     */
    public static boolean isRodColumn(int rx, int rz, int rw, int rd, int pattern) {
        if (rw <= 0 || rd <= 0) {
            return false;
        }
        if (pattern == PATTERN_CUSTOM || pattern == PATTERN_NONE) {
            return false;
        }
        TurbineRodControllerLayout.Center center = TurbineRodControllerLayout.bestPrimaryCenter(rw, rd);
        return rx == center.rx() && rz == center.rz();
    }

    /** True when the pattern skips rotor contents (rods / blades / rod controller). */
    public static boolean skipsRotorContents(int pattern) {
        return pattern == PATTERN_CUSTOM || pattern == PATTERN_NONE;
    }

    /** True when the pattern also skips coil placement. */
    public static boolean skipsCoils(int pattern) {
        return pattern == PATTERN_NONE;
    }

    public static boolean isRodForPreview(int rx, int ry, int rz, int rw, int rh, int rd, int pattern) {
        if (ry < 0 || ry >= rh) return false;
        return isRodColumn(rx, rz, rw, rd, pattern);
    }

    /**
     * Target blade ring index for rotor layer {@code ry} (0 = bottom along growth axis).
     * Efficient: +1 ring per layer (4, 8, 12, … blades) up to {@link TurbineBladePlacement#maxRing()}.
     * Productive: full rings on every layer immediately.
     */
    public static int targetBladeRingForLayer(int ry, int rh, int pattern) {
        int maxRing = TurbineBladePlacement.maxRing();
        if (pattern == PATTERN_PRODUCTIVE) {
            return maxRing;
        }
        return Math.min(maxRing, 1 + ry);
    }
}
