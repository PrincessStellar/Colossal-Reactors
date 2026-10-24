package net.unfamily.colossal_reactors.turbine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Rod column placement for turbine builder preview/build.
 * Efficient / Productive affect blade ring growth only ({@link #targetBladeRingForLayer}), not which columns get rods.
 * Hyperflow places a multi-rod grid; Website uses an external design seed (no built-in columns until applied).
 */
public final class TurbineRodPatternLogic {

    public static final int PATTERN_WEBSITE = 0;
    public static final int PATTERN_EFFICIENT = 1;
    public static final int PATTERN_PRODUCTIVE = 2;
    public static final int PATTERN_HYPERFLOW = 3;
    /** Frame + partition wall + coils only (no rods / blades / rod controller). */
    public static final int PATTERN_CUSTOM = 4;
    /** Frame + partition wall only (no rods / blades / coils / rod controller). */
    public static final int PATTERN_NONE = 5;

    public static final int PATTERN_COUNT = 6;

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
     * Built-in rod columns for the pattern (Website has none until a design seed is applied on the builder).
     */
    public static boolean isRodColumn(int rx, int rz, int rw, int rd, int pattern) {
        return isRodColumn(rx, rz, rw, rd, pattern, null);
    }

    /**
     * Like {@link #isRodColumn(int, int, int, int, int)} but when {@code pattern == WEBSITE} and
     * {@code websiteOverride} is non-null, uses the override for column membership.
     */
    public static boolean isRodColumn(int rx, int rz, int rw, int rd, int pattern,
                                      BiPredicate<Integer, Integer> websiteOverride) {
        if (rw <= 0 || rd <= 0) {
            return false;
        }
        if (rx < 0 || rx >= rw || rz < 0 || rz >= rd) {
            return false;
        }
        if (pattern == PATTERN_WEBSITE) {
            return websiteOverride != null && websiteOverride.test(rx, rz);
        }
        return switch (pattern) {
            case PATTERN_EFFICIENT, PATTERN_PRODUCTIVE -> {
                TurbineRodControllerLayout.Center center = TurbineRodControllerLayout.bestPrimaryCenter(rw, rd);
                yield rx == center.rx() && rz == center.rz();
            }
            case PATTERN_HYPERFLOW -> isHyperflowColumn(rx, rz, rw, rd);
            case PATTERN_CUSTOM, PATTERN_NONE -> false;
            default -> false;
        };
    }

    /**
     * Multi-rod grid with a one-cell margin from interior walls (matches moddecoded calculator rod grid).
     */
    private static boolean isHyperflowColumn(int rx, int rz, int rw, int rd) {
        if (rw < 3 || rd < 3) {
            TurbineRodControllerLayout.Center center = TurbineRodControllerLayout.bestPrimaryCenter(rw, rd);
            return rx == center.rx() && rz == center.rz();
        }
        if (rx < 1 || rz < 1 || rx > rw - 2 || rz > rd - 2) {
            return false;
        }
        return ((rx - 1) % 2 == 0) && ((rz - 1) % 2 == 0);
    }

    /** All rod-column centers for a built-in pattern. */
    public static List<TurbineRodControllerLayout.Center> rodColumns(int rw, int rd, int pattern) {
        return rodColumns(rw, rd, pattern, null);
    }

    /** Like {@link #rodColumns(int, int, int)} with an optional Website column override. */
    public static List<TurbineRodControllerLayout.Center> rodColumns(
            int rw, int rd, int pattern, BiPredicate<Integer, Integer> websiteOverride) {
        List<TurbineRodControllerLayout.Center> out = new ArrayList<>();
        for (int rx = 0; rx < rw; rx++) {
            for (int rz = 0; rz < rd; rz++) {
                if (isRodColumn(rx, rz, rw, rd, pattern, websiteOverride)) {
                    out.add(new TurbineRodControllerLayout.Center(rx, rz));
                }
            }
        }
        return out;
    }

    /**
     * True when the pattern skips rotor contents (rods / blades / rod controller).
     * Website stays skipped for material estimates without a design; build/preview paths that have a
     * valid seed should check {@code hasValidWebsiteDesign()} and use the website override instead.
     */
    public static boolean skipsRotorContents(int pattern) {
        return pattern == PATTERN_CUSTOM || pattern == PATTERN_NONE || pattern == PATTERN_WEBSITE;
    }

    /** Blade-ring growth pattern for Website designs: climb → Efficient, full → Productive. */
    public static int bladeGrowthPattern(int rodPattern, boolean websiteClimbProfile) {
        if (rodPattern == PATTERN_WEBSITE) {
            return websiteClimbProfile ? PATTERN_EFFICIENT : PATTERN_PRODUCTIVE;
        }
        return rodPattern;
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
     * Efficient / Hyperflow: +1 ring per layer up to max.
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
