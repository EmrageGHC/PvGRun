package org.emrage.pvgrun.util;

public class DistanceUtils {
    /**
     * Compute block-based distance north of spawn: floor(spawnZ - playerZ) but not negative.
     */
    public static int blockDistanceNorth(double spawnZ, double playerZ) {
        return Math.max(0, (int) Math.floor(spawnZ - playerZ));
    }
}

