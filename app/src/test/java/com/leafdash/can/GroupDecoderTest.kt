package com.leafdash.can

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupDecoderTest {

    // Real captures from a 24 kWh AZE0 (car sitting in READY).
    private val group1 =
        "7BB10296101FFFFF7F6 7BB210286FFFFF9C1FF 7BB22FFFFFF170E2AF8 " +
            "7BB238E9532A0037700 7BB24731412000451CE 7BB250006C28B800005"
    private val group4 =
        "7BB1010610401A11E01 7BB21B11CFFFFFF01B6 7BB221C1C00FFFFFFFF"

    @Test fun reassemblesGroup1ToDeclaredLength() {
        val p = IsoTp.reassemble(group1)
        assertEquals(41, p.size)                 // 0x29
        assertEquals(0x61, p[0].toInt() and 0xFF)
        assertEquals(0x01, p[1].toInt() and 0xFF)
    }

    @Test fun decodesGroup1CapacityHxSohVolts() {
        val s = GroupDecoder.apply(LeafState(), IsoTp.reassemble(group1))
        assertEquals(44.30, s.ahCapacity!!, 0.01)
        assertEquals(51.38, s.hx!!, 0.01)
        assertEquals(67.1, s.sohPercent!!, 0.1)   // 44.30 / 66 * 100
        assertEquals(365.0, s.packVolts!!, 0.1)
        assertEquals(28.31, s.socPercent!!, 0.01) // idx31-33 / 10000
    }

    @Test fun decodesGroup4TempAverage() {
        val s = GroupDecoder.apply(LeafState(), IsoTp.reassemble(group4))
        assertEquals(28.67, s.batteryTempC!!, 0.01) // (30+28+28)/3
    }

    @Test fun consecutiveFramesWithoutFirstFrameRejected() {
        // CF-only garbage (missed first frame): no declared length -> no payload
        assertEquals(0, IsoTp.reassemble("7BB2101020304 7BB2205060708").size)
    }

    @Test fun ignoresNonReplyPayload() {
        val s = GroupDecoder.apply(LeafState(), byteArrayOf(0x7F, 0x21)) // negative resp
        assertEquals(LeafState(), s)
    }
}
