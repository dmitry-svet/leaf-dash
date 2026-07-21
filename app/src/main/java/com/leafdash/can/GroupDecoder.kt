package com.leafdash.can

/**
 * Reassembles ISO-TP multi-frame ELM327 responses and decodes Nissan Leaf
 * AZE0 battery-controller (LBC) group data into [LeafState].
 *
 * Offsets/formulas from OVMS (`vehicle_nissanleaf.cpp`) and dalathegreat's
 * Battery-Emulator, verified against real captures from a 24 kWh car.
 * Payload index 0 = the 0x61 service echo, index 1 = group id.
 */
object GroupDecoder {

    /** New-car capacity for a 24 kWh pack (Ah), for the SOH baseline. */
    const val NEW_CAR_AH = 66.0

    fun apply(state: LeafState, payload: ByteArray): LeafState {
        if (payload.size < 2 || (payload[0].toInt() and 0xFF) != 0x61) return state
        return when (payload[1].toInt() and 0xFF) {
            0x01 -> group1(state, payload)
            0x04 -> group4(state, payload)
            else -> state
        }
    }

    /** Group 1 (2101): capacity Ah, Hx, derived SOH, candidate pack voltage. */
    private fun group1(s: LeafState, p: ByteArray): LeafState {
        if (p.size < 38) return s
        fun u(i: Int) = p[i].toInt() and 0xFF
        val hx = ((u(28) shl 8) or u(29)) / 100.0
        val soc = ((u(31) shl 16) or (u(32) shl 8) or u(33)) / 10000.0
        val ah = ((u(35) shl 16) or (u(36) shl 8) or u(37)) / 10000.0
        val soh = if (ah > 0) ah / NEW_CAR_AH * 100.0 else null
        val packV = ((u(20) shl 8) or u(21)) / 100.0
        return s.copy(
            hx = hx.takeIf { it in 0.0..200.0 },
            socPercent = soc.takeIf { it in 0.0..100.0 } ?: s.socPercent,
            ahCapacity = ah.takeIf { it in 0.0..100.0 },
            sohPercent = soh?.takeIf { it in 0.0..150.0 },
            packVolts = packV.takeIf { it in 100.0..500.0 } ?: s.packVolts,
        )
    }

    /**
     * Group 4 (2104): four temp triplets [ADC_hi, ADC_lo, °C]. The LBC already
     * computes °C in the 3rd byte, so use those directly. Sensor 3 is unused
     * on AZE0 (0xFF). Reports the average of the valid sensors.
     */
    private fun group4(s: LeafState, p: ByteArray): LeafState {
        if (p.size < 14) return s
        val cBytes = listOf(4, 7, 13).mapNotNull { i ->
            val v = p[i].toInt() and 0xFF
            if (v == 0xFF) null else p[i].toInt() // signed byte -> °C
        }
        if (cBytes.isEmpty()) return s
        val temps = cBytes.map { it.toDouble() }
        return s.copy(batteryTempC = temps.average(), batteryTempsC = temps)
    }
}

/** ISO-TP reassembly of ELM327 monitor/response text into the payload bytes. */
object IsoTp {

    /**
     * Join the data bytes of the ELM327 frame lines (id + PCI + data) into the
     * ISO-TP payload. Handles single, first, and consecutive frames; trims to
     * the length declared in the first frame (drops padding).
     */
    fun reassemble(raw: String): ByteArray {
        val out = ArrayList<Int>()
        var declared = -1
        var sawCf = false
        for (tok in raw.trim().split(Regex("\\s+"))) {
            if (tok.length < 5 || !tok.all { it.isHex() }) continue
            val d = tok.substring(3)                 // strip 3-char 11-bit id
            val b = ArrayList<Int>()
            var i = 0
            while (i + 1 < d.length) {
                b.add(d.substring(i, i + 2).toInt(16)); i += 2
            }
            if (b.isEmpty()) continue
            when (b[0] shr 4) {
                0x1 -> {                              // first frame: 2 PCI bytes
                    if (b.size >= 2) declared = ((b[0] and 0xF) shl 8) or b[1]
                    for (j in 2 until b.size) out.add(b[j])
                }
                0x2 -> {                              // consecutive frame: 1 PCI byte
                    sawCf = true
                    for (j in 1 until b.size) out.add(b[j])
                }
                0x0 -> for (j in 1 until b.size) out.add(b[j]) // single frame: 1 PCI byte
            }
        }
        // CFs without their first frame = partial capture; no declared length to
        // trim padding by, so the payload can't be trusted
        if (sawCf && declared < 0) return ByteArray(0)
        val res = if (declared in 0..out.size) out.subList(0, declared) else out
        return ByteArray(res.size) { res[it].toByte() }
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
