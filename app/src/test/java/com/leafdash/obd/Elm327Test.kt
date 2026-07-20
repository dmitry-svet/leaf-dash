package com.leafdash.obd

import com.leafdash.can.CanDecoder
import com.leafdash.can.LeafState
import com.leafdash.transport.MockTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Elm327Test {

    @Test fun parsesTightHexLine() {
        val f = Elm327.parseMonitorLine("55B8AC0")!!
        assertEquals(0x55B, f.id)
        assertEquals(0x8A, f.u(0))
        assertEquals(0xC0, f.u(1))
    }

    @Test fun toleratesSpaces() {
        val f = Elm327.parseMonitorLine("  55B 8A C0 ")!!
        assertEquals(0x55B, f.id)
        assertEquals(0xC0, f.u(1))
    }

    @Test fun rejectsNonFrameLines() {
        assertNull(Elm327.parseMonitorLine("OK"))
        assertNull(Elm327.parseMonitorLine("SEARCHING..."))
        assertNull(Elm327.parseMonitorLine(">"))
        assertNull(Elm327.parseMonitorLine(""))
        assertNull(Elm327.parseMonitorLine("1DB0")) // odd byte nibbles
    }

    @Test fun streamsFramesThroughMockThenDecodes() {
        val ok = "OK"
        val mock = MockTransport(
            mapOf(
                "ATZ" to ok, "ATE0" to ok, "ATL0" to ok,
                "ATS0" to ok, "ATH1" to ok, "ATSP6" to ok,
                // SOC frame then pack volts/amps frame
                "ATMA" to "55B8AC0\r1DB0C806400",
            )
        )
        mock.open()
        val elm = Elm327(mock)
        elm.init()
        elm.startMonitor()

        var state = LeafState()
        var f = elm.nextFrame()
        while (f != null) {
            state = CanDecoder.apply(state, f)
            f = elm.nextFrame()
        }

        assertEquals(55.5, state.socPercent!!, 1e-9)
        assertEquals(200.0, state.packVolts!!, 1e-9)
        assertEquals(50.0, state.packAmps!!, 1e-9)
        assertEquals(10.0, state.powerKw!!, 1e-9)
    }
}
