package net.unfamily.colossal_reactors.turbine;

import net.unfamily.colossal_reactors.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ModDecoded turbine calculator {@code bestLayout}: pick the rod grid (odd-odd, staggered a/R, or
 * shaft) with the most blades on one layer. HyperFlow uses this membership and {@link #bestRing}.
 */
public final class TurbineRodGridLayout {

    /** Result of {@link #bestLayout(int, int)}. */
    public record BestLayout(List<TurbineRodControllerLayout.Center> columns, int ring, int blades) {
        public boolean isRod(int rx, int rz) {
            for (TurbineRodControllerLayout.Center c : columns) {
                if (c.rx() == rx && c.rz() == rz) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final ConcurrentHashMap<Long, BestLayout> CACHE = new ConcurrentHashMap<>();

    private TurbineRodGridLayout() {}

    public static void clearCache() {
        CACHE.clear();
    }

    public static BestLayout bestLayout(int rw, int rd) {
        if (rw <= 0 || rd <= 0) {
            return new BestLayout(List.of(), 1, 0);
        }
        int maxRing = TurbineBladePlacement.maxRing();
        boolean balanced = Boolean.TRUE.equals(Config.TURBINE_REQUIRE_BALANCED_BLADE_RINGS.get());
        long key = (((long) rw) << 42) | (((long) rd) << 21) | (((long) maxRing) << 1) | (balanced ? 1L : 0L);
        return CACHE.computeIfAbsent(key, k -> computeBestLayout(rw, rd, maxRing, balanced));
    }

    public static int bestRing(int rw, int rd) {
        return bestLayout(rw, rd).ring();
    }

    public static boolean isBestLayoutRod(int rx, int rz, int rw, int rd) {
        if (rx < 0 || rz < 0 || rx >= rw || rz >= rd) {
            return false;
        }
        return bestLayout(rw, rd).isRod(rx, rz);
    }

    private static BestLayout computeBestLayout(int rw, int rd, int maxRing, boolean balanced) {
        List<Candidate> cands = new ArrayList<>();

        List<TurbineRodControllerLayout.Center> odd = new ArrayList<>();
        for (int x = 1; x <= rw - 2; x += 2) {
            for (int z = 1; z <= rd - 2; z += 2) {
                odd.add(new TurbineRodControllerLayout.Center(x, z));
            }
        }
        if (!odd.isEmpty()) {
            cands.add(new Candidate(odd, 1));
        }

        for (int a = 0; a < 4; a++) {
            for (int R = 1; R <= 3; R++) {
                List<TurbineRodControllerLayout.Center> rods = new ArrayList<>();
                int row = 0;
                for (int z = 1; z < rd; z += 2, row++) {
                    int x0 = (a + (row % 2) * 2) % 4;
                    for (int x = x0; x < rw; x += 4) {
                        rods.add(new TurbineRodControllerLayout.Center(x, z));
                    }
                }
                cands.add(new Candidate(rods, R));
            }
        }

        TurbineRodControllerLayout.Center center = TurbineRodControllerLayout.bestPrimaryCenter(rw, rd);
        int shaftRing = Math.min(maxRing, Math.min(
                Math.min(center.rx(), rw - 1 - center.rx()),
                Math.min(center.rz(), rd - 1 - center.rz())));
        cands.add(new Candidate(List.of(center), Math.max(1, shaftRing)));

        BestLayout best = null;
        for (Candidate o : cands) {
            if (o.ring > maxRing) {
                continue;
            }
            LayerScore L = scoreBladeLayer(rw, rd, o.columns, o.ring, maxRing, balanced);
            if (!L.ok) {
                continue;
            }
            if (best == null || L.blades > best.blades()) {
                best = new BestLayout(List.copyOf(o.columns), o.ring, L.blades);
            }
        }
        if (best == null) {
            return new BestLayout(List.of(center), Math.max(1, shaftRing), 0);
        }
        return best;
    }

    private record Candidate(List<TurbineRodControllerLayout.Center> columns, int ring) {}

    private record LayerScore(boolean ok, int blades) {}

    /**
     * Dry-run of one rotor layer matching ModDecoded {@code bladeLayer} (owned 2.5, non-builder).
     * Grid: -1 air, -2 rod, else owner rod index into {@code rods}.
     */
    private static LayerScore scoreBladeLayer(
            int iw, int id,
            List<TurbineRodControllerLayout.Center> columns,
            int target,
            int maxRing,
            boolean balanced) {
        if (columns.isEmpty()) {
            return new LayerScore(false, 0);
        }
        int[] rods = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            TurbineRodControllerLayout.Center c = columns.get(i);
            rods[i] = c.rx() * id + c.rz();
        }
        Arrays.sort(rods);

        int[] grid = new int[iw * id];
        Arrays.fill(grid, -1);
        for (int r : rods) {
            if (r < 0 || r >= grid.length) {
                return new LayerScore(false, 0);
            }
            grid[r] = -2;
        }

        // dirs: N(-z), S(+z), W(-x), E(+x) — same order as ModDecoded
        final int[] dx = {0, 0, -1, 1};
        final int[] dz = {-1, 1, 0, 0};

        for (int r : rods) {
            while (ringOf(grid, iw, id, r, maxRing, dx, dz) <= target) {
                int[] snap = grid.clone();
                int k0 = ringOf(grid, iw, id, r, maxRing, dx, dz);
                boolean ok = true;
                while (ringOf(grid, iw, id, r, maxRing, dx, dz) == k0) {
                    int c = click(grid, iw, id, r, maxRing, dx, dz);
                    if (c < 0) {
                        ok = false;
                        break;
                    }
                    grid[c] = r;
                }
                if (!ok) {
                    System.arraycopy(snap, 0, grid, 0, grid.length);
                    break;
                }
            }
        }

        int blades = 0;
        for (int v : grid) {
            if (v >= 0) {
                blades++;
            }
        }
        if (balanced) {
            for (int r : rods) {
                int tot = total(grid, iw, id, r, maxRing, dx, dz);
                if (tot % 4 != 0) {
                    return new LayerScore(false, blades);
                }
            }
        }
        return new LayerScore(true, blades);
    }

    private static int cellAt(int iw, int id, int r, int d, int k, int[] dx, int[] dz) {
        int x = (r / id) + dx[d] * k;
        int z = (r % id) + dz[d] * k;
        if (x < 0 || z < 0 || x >= iw || z >= id) {
            return -1;
        }
        return x * id + z;
    }

    private static int facing(int[] grid, int id, int c) {
        int o = grid[c];
        int dx = (c / id) - (o / id);
        if (dx != 0) {
            return dx < 0 ? 2 : 3;
        }
        return (c % id) < (o % id) ? 0 : 1;
    }

    private static boolean aligned(int[] grid, int iw, int id, int r, int d, int maxRing, int[] dx, int[] dz) {
        int cap = Math.max(maxRing + 2, 8);
        for (int k = 1; k <= cap; k++) {
            int c = cellAt(iw, id, r, d, k, dx, dz);
            if (c < 0) {
                return false;
            }
            if (grid[c] == -2) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlade(int[] grid, int iw, int id, int c, int r, int d, int maxRing, int[] dx, int[] dz) {
        if (c < 0 || grid[c] < 0) {
            return false;
        }
        int f = facing(grid, id, c);
        return f == d || (f == (d ^ 1) && aligned(grid, iw, id, r, d, maxRing, dx, dz));
    }

    private static int depth(int[] grid, int iw, int id, int r, int d, int maxRing, int[] dx, int[] dz) {
        int n = 0;
        while (n < maxRing && isBlade(grid, iw, id, cellAt(iw, id, r, d, n + 1, dx, dz), r, d, maxRing, dx, dz)) {
            n++;
        }
        return n;
    }

    private static int total(int[] grid, int iw, int id, int r, int maxRing, int[] dx, int[] dz) {
        return depth(grid, iw, id, r, 0, maxRing, dx, dz)
                + depth(grid, iw, id, r, 1, maxRing, dx, dz)
                + depth(grid, iw, id, r, 2, maxRing, dx, dz)
                + depth(grid, iw, id, r, 3, maxRing, dx, dz);
    }

    private static int ringOf(int[] grid, int iw, int id, int r, int maxRing, int[] dx, int[] dz) {
        for (int k = 1; k <= maxRing; k++) {
            for (int d = 0; d < 4; d++) {
                if (!isBlade(grid, iw, id, cellAt(iw, id, r, d, k, dx, dz), r, d, maxRing, dx, dz)) {
                    return k;
                }
            }
        }
        return maxRing + 1;
    }

    private static int choose(int[] grid, int iw, int id, int r, int maxRing, int[] dx, int[] dz) {
        int tot = total(grid, iw, id, r, maxRing, dx, dz);
        if (tot >= maxRing * 4) {
            return -1;
        }
        int k = ringOf(grid, iw, id, r, maxRing, dx, dz);
        if (k > maxRing) {
            return -1;
        }
        List<Integer> missing = new ArrayList<>(4);
        for (int d = 0; d < 4; d++) {
            if (!isBlade(grid, iw, id, cellAt(iw, id, r, d, k, dx, dz), r, d, maxRing, dx, dz)) {
                missing.add(d);
            }
        }
        if (missing.isEmpty()) {
            return -1;
        }
        if (tot == 1) {
            for (int d = 0; d < 4; d++) {
                if (depth(grid, iw, id, r, d, maxRing, dx, dz) == 1) {
                    int o = d ^ 1;
                    if (missing.contains(o)) {
                        return o;
                    }
                    break;
                }
            }
        }
        int min = Integer.MAX_VALUE;
        for (int d : missing) {
            min = Math.min(min, depth(grid, iw, id, r, d, maxRing, dx, dz));
        }
        List<Integer> cand = new ArrayList<>();
        for (int d : missing) {
            if (depth(grid, iw, id, r, d, maxRing, dx, dz) == min) {
                cand.add(d);
            }
        }
        return cand.get(tot % cand.size());
    }

    private static int click(int[] grid, int iw, int id, int r, int maxRing, int[] dx, int[] dz) {
        int k = ringOf(grid, iw, id, r, maxRing, dx, dz);
        if (k > maxRing) {
            return -1;
        }
        int first = choose(grid, iw, id, r, maxRing, dx, dz);
        List<Integer> tries = new ArrayList<>(4);
        if (first >= 0) {
            tries.add(first);
        }
        for (int d = 0; d < 4; d++) {
            if (tries.contains(d)) {
                continue;
            }
            if (!isBlade(grid, iw, id, cellAt(iw, id, r, d, k, dx, dz), r, d, maxRing, dx, dz)
                    && (k == 1 || depth(grid, iw, id, r, d, maxRing, dx, dz) == k - 1)) {
                tries.add(d);
            }
        }
        for (int d : tries) {
            int c = cellAt(iw, id, r, d, k, dx, dz);
            if (c >= 0 && grid[c] == -1) {
                return c;
            }
        }
        return -1;
    }
}
