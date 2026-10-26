package net.unfamily.colossal_reactors.turbine;

import java.util.List;

/**
 * One elec coil entry from datapack: valid blocks, RF efficiency and steam-flow coefficients.
 */
public record ElecCoilDefinition(
        List<String> validBlocks,
        double effCoe,
        double effMax,
        double flowCoe,
        double flowMax
) {
    public ElecCoilDefinition {
        validBlocks = validBlocks != null ? List.copyOf(validBlocks) : List.of();
    }

    /** Legacy 3-arg constructor: flow defaults to 1.0. */
    public ElecCoilDefinition(List<String> validBlocks, double effCoe, double effMax) {
        this(validBlocks, effCoe, effMax, 1.0, 1.0);
    }
}
