package com.leafdash.poll

import com.leafdash.transport.MockTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LeafPollerTest {

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
