package com.leafdash.transport

/**
 * Generates a synthetic ELM327 monitor stream so the dashboard can be run and
 * demoed without a car or dongle. Replies "OK" to setup commands; after ATMA
 * it emits a repeating set of frames with slowly varying values so the live
 * tiles and trip counters move.
 */
class DemoTransport : Transport {

    private var streaming = false
    private var tick = 0
    private var pending = ArrayDeque<Byte>()

    override fun open() {}

    override fun write(bytes: ByteArray) {
        val cmd = String(bytes, Charsets.US_ASCII).trim().trimEnd('\r')
        if (cmd == "ATMA") streaming = true else enqueue("OK\r>")
    }

    override fun read(buffer: ByteArray, max: Int): Int {
        if (pending.isEmpty()) {
            if (!streaming) return -1
            Thread.sleep(120)          // ~8 frame-sets/sec, keeps CPU idle
            enqueue(nextFrameSet())
        }
        var n = 0
        while (n < max && pending.isNotEmpty()) buffer[n++] = pending.removeFirst()
        return n
    }

    override fun available(): Int = pending.size

    override fun close() {
        streaming = false
        pending.clear()
    }

    private fun enqueue(s: String) =
        s.toByteArray(Charsets.US_ASCII).forEach { pending.addLast(it) }

    /** One cycle of frames with values drifting by [tick]. */
    private fun nextFrameSet(): String {
        tick++
        val phase = (tick % 100)
        val speed = 40.0 + 20.0 * Math.sin(phase / 8.0)      // ~20..60 km/h
        val power = 8.0 + 12.0 * Math.sin(phase / 8.0)       // ~ -4..20 kW
        val soc = 62.0 - phase * 0.01                        // slowly drops
        val gids = 190 - phase / 5                           // slowly drops
        val tempC = 24.0

        return buildString {
            append(frameSoc(soc)).append('\r')
            append(framePack(volts = 360.0, amps = power * 1000.0 / 360.0)).append('\r')
            append(frameGids(gids)).append('\r')
            append(frameSpeed(speed)).append('\r')
            append(frameTemp(tempC)).append('\r')
        }
    }

    // Encoders mirror CanDecoder's formulas (inverse), producing tight hex.
    private fun frameSoc(soc: Double): String {
        val raw = (soc * 10).toInt().coerceIn(0, 1023)
        return "55B" + hex2(raw shr 2) + hex2((raw and 0x3) shl 6)
    }

    private fun framePack(volts: Double, amps: Double): String {
        val v = (volts * 2).toInt().coerceIn(0, 1023)
        val a = ((amps * 2).toInt()) and 0x7FF               // 11-bit
        val b0 = a shr 3
        val b1 = (a and 0x7) shl 5
        val b2 = v shr 2
        val b3 = (v and 0x3) shl 6
        return "1DB" + hex2(b0) + hex2(b1) + hex2(b2) + hex2(b3)
    }

    private fun frameGids(gids: Int): String {
        val raw = gids.coerceIn(0, 1023)
        return "5BC" + hex2(raw shr 2) + hex2((raw and 0x3) shl 6)
    }

    private fun frameSpeed(speed: Double): String {
        val raw = (speed * 100).toInt().coerceIn(0, 0xFFFF)
        // decoder reads speed from bytes 4..5, so pad bytes 0..3 with zeros
        return "284" + "00000000" + hex2(raw shr 8) + hex2(raw and 0xFF)
    }

    private fun frameTemp(c: Double): String {
        val b2 = ((c + 40.0) * 2).toInt().coerceIn(0, 255)
        return "5C0" + hex2(0x40) + hex2(0) + hex2(b2)       // group 1
    }

    private fun hex2(v: Int): String = "%02X".format(v and 0xFF)
}
