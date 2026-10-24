package net.unfamily.colossal_reactors.turbine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

/**
 * Which lateral connector sides on a turbine rod should be visible (at least one blade on that side).
 */
public final class TurbineRodConnectorVisibility {

    private TurbineRodConnectorVisibility() {}

    /** Bit set: bit {@link Direction#ordinal()} is set when that lateral side has at least one blade. */
    public static int lateralConnectorMask(BlockGetter level, BlockPos rodPos, Direction rodAxis) {
        int mask = 0;
        // Only the adjacent block is required for connector visibility; avoid walking long rings
        // during client section meshing (RenderChunkRegion has a limited chunk window).
        for (Direction lateral : TurbineBladePlacement.lateralDirections(rodAxis)) {
            if (TurbineBladePlacement.isOwnedBladeAlong(
                    TurbineBladePlacement.safeGetBlockState(level, rodPos.relative(lateral)), lateral)) {
                mask |= 1 << lateral.ordinal();
            }
        }
        return mask;
    }

    public static boolean isSideVisible(int mask, Direction lateral) {
        return (mask & (1 << lateral.ordinal())) != 0;
    }
}
