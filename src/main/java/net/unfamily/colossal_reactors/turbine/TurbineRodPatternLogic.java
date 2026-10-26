package net.unfamily.colossal_reactors.turbine;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Rod column placement and blade-ring growth for the Turbine Builder.
 * Modes: Website / Flow / HyperFlow / Custom / None.
 * Flow and HyperFlow share an Efficient↔Productive variant (climb vs full rings).
 */
public final class TurbineRodPatternLogic {

    public static final int PATTERN_WEBSITE = 0;
    /** Single center shaft; growth from {@link #VARIANT_EFFICIENT} / {@link #VARIANT_PRODUCTIVE}. */
    public static final int PATTERN_FLOW = 1;
    /** Multi-rod grid from {@link TurbineRodGridLayout#bestLayout}; growth from variant + best ring. */
    public static final int PATTERN_HYPERFLOW = 2;
    /** Frame + partition wall + coils only (no rods / blades / rod controller). */
    public static final int PATTERN_CUSTOM = 3;
    /** Frame + partition wall only (no rods / blades / coils / rod controller). */
    public static final int PATTERN_NONE = 4;

    public static final int PATTERN_COUNT = 5;

    /** Climb rings (+1 per layer). Default for Flow / HyperFlow. */
    public static final int VARIANT_EFFICIENT = 0;
    /** Full rings on every layer. */
    public static final int VARIANT_PRODUCTIVE = 1;
    public static final int VARIANT_COUNT = 2;

    /** Growth id for {@link #targetBladeRingForLayer}: climb. */
    public static final int GROWTH_CLIMB = 0;
    /** Growth id for {@link #targetBladeRingForLayer}: full rings. */
    public static final int GROWTH_FULL = 1;

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

    public static boolean usesBladeVariantToggle(int pattern) {
        return pattern == PATTERN_FLOW || pattern == PATTERN_HYPERFLOW;
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
            case PATTERN_FLOW -> {
                TurbineRodControllerLayout.Center center = TurbineRodControllerLayout.bestPrimaryCenter(rw, rd);
                yield rx == center.rx() && rz == center.rz();
            }
            case PATTERN_HYPERFLOW -> TurbineRodGridLayout.isBestLayoutRod(rx, rz, rw, rd);
            case PATTERN_CUSTOM, PATTERN_NONE -> false;
            default -> false;
        };
    }

    /**
     * Ring cap for HyperFlow (ModDecoded Rod grid {@code best.ring}). Website uses the seed ring;
     * other patterns leave uncapped ({@link Integer#MAX_VALUE}).
     */
    public static int designBladeRingCap(int rodPattern, int rw, int rd, int websiteRingCap) {
        if (rodPattern == PATTERN_WEBSITE) {
            return websiteRingCap < 0 ? Integer.MAX_VALUE : websiteRingCap;
        }
        if (rodPattern == PATTERN_HYPERFLOW) {
            return TurbineRodGridLayout.bestRing(rw, rd);
        }
        return Integer.MAX_VALUE;
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
     * Single rod-controller column for builder/preview: among {@link #rodColumns}, prefer the
     * geometric primary center when it is a rod column, otherwise the closest rod column.
     */
    @Nullable
    public static TurbineRodControllerLayout.Center controllerColumn(
            int rw, int rd, int pattern, BiPredicate<Integer, Integer> websiteOverride) {
        List<TurbineRodControllerLayout.Center> columns = rodColumns(rw, rd, pattern, websiteOverride);
        if (columns.isEmpty()) {
            return null;
        }
        if (columns.size() == 1) {
            return columns.getFirst();
        }
        TurbineRodControllerLayout.Center primary = TurbineRodControllerLayout.bestPrimaryCenter(rw, rd);
        for (TurbineRodControllerLayout.Center c : columns) {
            if (c.rx() == primary.rx() && c.rz() == primary.rz()) {
                return primary;
            }
        }
        float targetX = (rw - 1) / 2.0f;
        float targetZ = (rd - 1) / 2.0f;
        TurbineRodControllerLayout.Center best = columns.getFirst();
        float bestDist = Float.MAX_VALUE;
        for (TurbineRodControllerLayout.Center c : columns) {
            float dx = c.rx() - targetX;
            float dz = c.rz() - targetZ;
            float dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }
        return best;
    }

    /**
     * True when the pattern skips rotor contents (rods / blades / rod controller).
     * Website stays skipped for material estimates without a design; build/preview paths that have a
     * valid seed should check {@code hasValidWebsiteDesign()} and use the website override instead.
     */
    public static boolean skipsRotorContents(int pattern) {
        return pattern == PATTERN_CUSTOM || pattern == PATTERN_NONE || pattern == PATTERN_WEBSITE;
    }

    /**
     * Resolves climb vs full growth for build/sim.
     * Website uses the seed climb profile; Flow / HyperFlow use {@code bladeVariant}.
     */
    public static int resolveBladeGrowth(int rodPattern, int bladeVariant, boolean websiteClimbProfile) {
        if (rodPattern == PATTERN_WEBSITE) {
            return websiteClimbProfile ? GROWTH_CLIMB : GROWTH_FULL;
        }
        if (rodPattern == PATTERN_FLOW || rodPattern == PATTERN_HYPERFLOW) {
            return bladeVariant == VARIANT_PRODUCTIVE ? GROWTH_FULL : GROWTH_CLIMB;
        }
        return GROWTH_FULL;
    }

    /**
     * @deprecated use {@link #resolveBladeGrowth(int, int, boolean)}
     */
    @Deprecated
    public static int bladeGrowthPattern(int rodPattern, boolean websiteClimbProfile) {
        return resolveBladeGrowth(rodPattern, VARIANT_EFFICIENT, websiteClimbProfile);
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
     * {@link #GROWTH_FULL}: max rings every layer; {@link #GROWTH_CLIMB}: {@code 1+ry}.
     */
    public static int targetBladeRingForLayer(int ry, int rh, int growth) {
        int maxRing = TurbineBladePlacement.maxRing();
        if (growth == GROWTH_FULL) {
            return maxRing;
        }
        return Math.min(maxRing, 1 + ry);
    }

    /**
     * Migrates a pre-2.5.1 {@code RodPattern} (6 values: Website/Eff/Prod/Hyper/Custom/None)
     * into mode + variant. Returns {@code int[]{pattern, variant}}.
     */
    public static int[] migrateLegacyRodPattern(int legacyPattern) {
        return switch (legacyPattern) {
            case 1 -> new int[]{PATTERN_FLOW, VARIANT_EFFICIENT};
            case 2 -> new int[]{PATTERN_FLOW, VARIANT_PRODUCTIVE};
            case 3 -> new int[]{PATTERN_HYPERFLOW, VARIANT_PRODUCTIVE};
            case 4 -> new int[]{PATTERN_CUSTOM, VARIANT_EFFICIENT};
            case 5 -> new int[]{PATTERN_NONE, VARIANT_EFFICIENT};
            default -> new int[]{PATTERN_WEBSITE, VARIANT_EFFICIENT};
        };
    }
}
