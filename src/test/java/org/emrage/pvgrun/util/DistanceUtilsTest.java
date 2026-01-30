package org.emrage.pvgrun.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DistanceUtilsTest {

    @Test
    public void testBlockDistanceNorth_basic() {
        assertEquals(0, DistanceUtils.blockDistanceNorth(0.5, 0.7)); // player south of spawn
        assertEquals(0, DistanceUtils.blockDistanceNorth(0.5, 0.5)); // same Z
        assertEquals(1, DistanceUtils.blockDistanceNorth(0.5, -0.5)); // one block north
        assertEquals(5, DistanceUtils.blockDistanceNorth(0.5, -4.6)); // floor(0.5 - (-4.6)) = floor(5.1) = 5
    }
}

