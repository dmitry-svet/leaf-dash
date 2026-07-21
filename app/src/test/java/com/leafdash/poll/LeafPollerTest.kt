package com.leafdash.poll

import com.leafdash.transport.MockTransport
import com.leafdash.transport.Transport
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeafPollerTest {

    private fun poller() = LeafPoller(MockTransport(emptyMap()))

    @Test fun distanceLeadsOdoByAtMostOneKm() {
        val p = poller()
        var t = 1_000L
        p.updateDistance(1000.0, 90.0, t)
        t += 240_000                       // 4 min at 90 km/h = 6 km integral
        p.updateDistance(1000.0, 90.0, t)  // odo still 1000 -> integral over-read
        assertEquals(1001.0, p.distanceKm!!, 1e-6)  // capped at odo + 1 (truncation)
    }

    @Test fun firstOdoTickDoesNotJumpDistance() {
        val p = poller()
        var t = 1_000L
        p.updateDistance(1000.0, 30.0, t)
        t += 48_000                        // 0.4 km at 30 km/h
        p.updateDistance(1000.0, 30.0, t)
        assertEquals(1000.4, p.distanceKm!!, 1e-6)
        t += 1_000
        p.updateDistance(1001.0, 30.0, t)  // odo ticks over mid-ride
        assertEquals(1000.41, p.distanceKm!!, 0.01)  // no snap to 1001
    }

    @Test fun backwardsOdoReadingRejected() {
        val p = poller()
        var t = 1_000L
        p.updateDistance(1000.0, 60.0, t)
        t += 60_000
        p.updateDistance(1001.0, 60.0, t)  // 1 km driven
        t += 3_000
        p.updateDistance(995.0, 60.0, t)   // corrupt low read
        assertEquals(1001.0, p.odoDisplayKm!!, 1e-6)  // reading dropped
        assertTrue(p.distanceKm!! >= 1001.0)          // no distance dip
    }

    @Test fun persistentOdoShiftReanchors() {
        val p = poller()
        var t = 1_000L
        p.updateDistance(1000.0, 0.0, t)
        repeat(3) { t += 3_000; p.updateDistance(1200.0, 0.0, t) }
        assertEquals(1200.0, p.odoDisplayKm!!, 1e-6)  // accepted after streak
        assertEquals(1200.0, p.distanceKm!!, 1e-6)    // clean re-anchor
    }

    @Test fun unitToggleReanchors() {
        val p = poller()
        p.setUnitsMiles(true)
        var t = 1_000L
        p.updateDistance(1000.0, 0.0, t)   // anchored at 1609.344 km
        p.setUnitsMiles(false)
        t += 3_000
        p.updateDistance(1000.0, 0.0, t)
        assertEquals(1000.0, p.odoDisplayKm!!, 1e-6)
        assertEquals(1000.0, p.distanceKm!!, 1e-6)    // re-anchored to new scale
    }

    @Test(timeout = 10_000) fun watchdogUnsticksHungTransport() {
        val p = LeafPoller(HangingTransport(), active = true, stallTimeoutMs = 300)
        p.runBlocking()                    // must return once watchdog fires
        assertFalse(p.state.value.connected)
    }

    /** Blocks every read until close() — models a hung dongle/BT link. */
    private class HangingTransport : Transport {
        private val closed = CountDownLatch(1)
        override fun open() {}
        override fun write(bytes: ByteArray) {}
        override fun read(buffer: ByteArray, max: Int): Int {
            closed.await()
            return -1
        }
        override fun close() = closed.countDown()
    }

    @Test fun passiveDecodesStreamAndReportsDisconnectAtEnd() {
        val ok = "OK"
        val mock = MockTransport(
            mapOf(
                "ATZ" to ok, "ATE0" to ok, "ATL0" to ok,
                "ATS0" to ok, "ATH1" to ok, "ATSP6" to ok,
                "ATMA" to "55B8AC0\r1DB0C806400",
            )
        )
        val poller = LeafPoller(mock, active = false)
        poller.runBlocking()

        val s = poller.state.value
        assertEquals(55.5, s.leaf.socPercent!!, 1e-9)
        assertEquals(200.0, s.leaf.packVolts!!, 1e-9)
        assertEquals(50.0, s.leaf.packAmps!!, 1e-9)
        assertFalse(s.connected)   // stream ended
    }
}
