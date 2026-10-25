package com.leafdash.can

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeafStateTest {

    @Test fun kwhFromSocNeedsSocAndAh() {
        assertNull(LeafState(gids = 150).kwhFromSoc)
        assertNull(LeafState(socPercent = 80.0, gids = 150).kwhFromSoc)
        assertEquals(12.96, LeafState(socPercent = 80.0, ahCapacity = 45.0).kwhFromSoc!!, 1e-9)
    }

    @Test fun kwhRemainingFallsBackToGidsForDisplayOnly() {
        assertEquals(11.625, LeafState(gids = 150).kwhRemaining!!, 1e-9)
        assertEquals(12.96, LeafState(socPercent = 80.0, ahCapacity = 45.0, gids = 150).kwhRemaining!!, 1e-9)
    }
}
