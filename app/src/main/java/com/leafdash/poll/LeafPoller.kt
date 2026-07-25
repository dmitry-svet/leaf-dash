package com.leafdash.poll

import com.leafdash.can.CanDecoder
import com.leafdash.can.CanFrame
import com.leafdash.can.GroupDecoder
import com.leafdash.can.IsoTp
import com.leafdash.can.LeafState
import com.leafdash.obd.Elm327
import com.leafdash.transport.Transport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives one dongle session: open -> init -> (monitor | active poll) -> decode,
 * publishing a [DashState] after every update. Trip/economy tracking lives in
 * the ViewModel (it needs GPS), so this only produces the raw vehicle state.
 *
 * [runBlocking] blocks (the ELM327 reads block), so call it on an IO thread.
 */
class LeafPoller(
    private val transport: Transport,
    /** true = active ISO-TP polling (real Leaf); false = passive monitor (demo). */
    private val active: Boolean = false,
    /** No state progress for this long = hung link; watchdog closes it. */
    private val stallTimeoutMs: Long = 30_000,
    /** Diagnostic CSV logger (one line per active cycle). */
    private val logLine: (String) -> Unit = {},
) {
    private val elm = Elm327(transport)

    private val _state = MutableStateFlow(DashState(connecting = true))
    val state: StateFlow<DashState> = _state.asStateFlow()

    @Volatile private var running = false
    private var leaf = LeafState()
    private var raw: Map<String, String> = emptyMap()
    private var debug: List<String> = emptyList()

    private var odometerRaw: Double? = null   // raw 5C5 count (km or mi per car)
    internal var odoDisplayKm: Double? = null // odometer in km (unit applied)
        private set
    internal var distanceKm: Double? = null   // smooth session distance (km, for tracker)
        private set
    private var odoAnchorKm: Double? = null
    private var sessionDist = 0.0            // raw speed-integrated distance since anchor
    private var lastSpeedMs = 0L
    private var speedGain = 1.0              // speed->distance scale, calibrated over long spans
    private var calibOdo: Double? = null     // odo km at first tick (calibration start)
    private var calibSess = 0.0              // raw sessionDist at first tick
    private var lastTickOdo = 0.0
    private var odoRejects = 0               // consecutive implausible odo readings
    private var lastUnitsMiles: Boolean? = null

    @Volatile private var unitsMiles = false
    fun setUnitsMiles(m: Boolean) { unitsMiles = m }

    @Volatile private var lastProgressMs = 0L

    /** Battery-controller diagnostic groups to poll in active mode. */
    private val activeGroups = listOf("2101", "2103", "2104", "2105", "2106")

    /** LBC (battery) diagnostic response id; odometer broadcast id (car-CAN). */
    private val lbcRxAddr = "7BB"
    private val odoBroadcastId = "5C5"
    private val ambientBroadcastId = "510"
    private val speedBroadcastId = "284"

    private fun status(msg: String) {
        lastProgressMs = System.currentTimeMillis()
        _state.value = _state.value.copy(connecting = true, connectMsg = msg)
    }

    fun runBlocking() {
        running = true
        lastProgressMs = System.currentTimeMillis()
        val watchdog = startWatchdog()
        try {
            status("Opening Bluetooth...")
            transport.open()
            status("Bluetooth open")
            if (active) runActive() else runPassive()
            publish(connected = false)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                connected = false, connecting = false, error = e.message ?: "error",
            )
        } finally {
            runCatching { transport.close() }
            running = false
            watchdog.interrupt()
        }
    }

    /**
     * ELM327 reads block with no timeout (BT sockets can hang silently, and the
     * odometer broadcast stops when the car turns off). If the session makes no
     * progress for [stallTimeoutMs], stop it: closing the transport unblocks the
     * read, runBlocking exits, and the UI auto-reconnect takes over.
     */
    private fun startWatchdog() = Thread {
        try {
            while (running) {
                Thread.sleep((stallTimeoutMs / 4).coerceAtLeast(10))
                if (running && System.currentTimeMillis() - lastProgressMs > stallTimeoutMs) {
                    stop()
                    break
                }
            }
        } catch (_: InterruptedException) {
        }
    }.apply { isDaemon = true; start() }

    /** Passive broadcast monitor (used by the demo transport). */
    private fun runPassive() {
        elm.init()
        elm.startMonitor()
        publish(connected = true)
        while (running) {
            val f = elm.nextFrame() ?: break
            leaf = CanDecoder.apply(leaf, f)
            publish(connected = true)
        }
    }

    /** Active ISO-TP polling of the Leaf battery controller. */
    private fun runActive() {
        elm.initActive { cmd -> status("Init ELM: $cmd") }
        status("Reading battery...")
        while (running) {
            val captured = LinkedHashMap<String, String>()
            val status = ArrayList<String>()
            status.add("ELM: ${elm.elmId.ifBlank { "?" }}")
            status.add("Proto: ${elm.protocol.ifBlank { "?" }}")
            for (g in activeGroups) {
                if (!running) break
                val text = elm.queryRaw(g)
                captured[g] = text
                val payload = IsoTp.reassemble(text)
                leaf = GroupDecoder.apply(leaf, payload)
                status.add("$g: ${if (payload.isEmpty()) "no data" else "${payload.size}B"}")
            }

            // odometer: broadcast 0x5C5 on car-CAN via hardware filter (LeafSpy way).
            // Read several frames and take the median to reject corrupt BT reads.
            // Raw count may be km or mi depending on the car; unit applied downstream.
            if (running) {
                val frames = elm.readBroadcastN(odoBroadcastId, 3)
                frames.lastOrNull()?.let {
                    captured["odo $odoBroadcastId"] = "%03X".format(it.id) +
                        it.data.joinToString("") { b -> "%02X".format(b) }
                }
                val readings = frames.mapNotNull { decodeOdometer(it) }.sorted()
                val median = if (readings.isEmpty()) null else readings[readings.size / 2]

                // speed 0x284 -> smooth distance between coarse odometer ticks
                val sf = elm.readBroadcast(speedBroadcastId)
                val speed = sf?.let {
                    (((it.u(4) shl 8) or it.u(5)) / 100.0).takeIf { s -> s in 0.0..300.0 }
                }
                leaf = leaf.copy(speedKmh = speed)
                updateDistance(median, speed, System.currentTimeMillis())
                status.add("odo: ${odoDisplayKm?.let { "%.0f km".format(it) } ?: "no data"}")
                status.add("spd: ${speed?.let { "%.0f km/h".format(it) } ?: "no data"}")

                // ambient/outside temp: broadcast 0x510 byte7, C = b7*0.5 - 40
                val af = elm.readBroadcast(ambientBroadcastId)
                af?.let {
                    captured["ext $ambientBroadcastId"] =
                        it.data.joinToString("") { b -> "%02X".format(b) }
                    val b7 = it.u(7)
                    if (b7 != 0xFF) {
                        val c = b7 * 0.5 - 40.0
                        if (c in -50.0..90.0) leaf = leaf.copy(ambientTempC = c)
                    }
                }
                status.add("ext: ${leaf.ambientTempC?.let { "%.0f C".format(it) } ?: "no data"}")

                // 12V battery: the ELM327 measures OBD pin 16 directly (ATRV)
                val rv = elm.queryRaw("ATRV").trim()          // e.g. "12.4V"
                rv.removeSuffix("V").toDoubleOrNull()?.let { v ->
                    if (v in 5.0..20.0) leaf = leaf.copy(aux12V = v)
                }
                status.add("12V: ${leaf.aux12V?.let { "%.1f V".format(it) } ?: "no data"}")

                // diagnostic CSV: t,odoRaw,odoKm,spd,b6(counter),sessDist,dist
                logLine(
                    "${System.currentTimeMillis()},${odometerRaw ?: ""}," +
                        "${odoDisplayKm ?: ""},${speed ?: ""},${sf?.u(6) ?: ""}," +
                        "${"%.3f".format(sessionDist)},${distanceKm?.let { "%.3f".format(it) } ?: ""}",
                )

                elm.setRxAddr(lbcRxAddr)   // restore filter for battery polling
            }

            raw = captured
            debug = status
            publish(connected = true)
            Thread.sleep(500)
        }
    }

    /**
     * Fold one odometer reading + speed sample into the smooth session distance.
     *
     * Distance is the speed integral (smooth), scaled by a [speedGain] that is
     * self-calibrated from odometer tick-to-tick spans (speedometers read a few
     * percent optimistic). The truncated odometer is NOT used as a hard bound —
     * doing so froze the estimate for most of each mile then jumped at the tick,
     * because the anchor lands mid-mile. Corrupt/backwards odometer reads are
     * dropped unless they persist ([ODO_REJECT_LIMIT] in a row = a real jump,
     * re-anchor). A unit toggle re-anchors too.
     */
    internal fun updateDistance(reading: Double?, speedKmh: Double?, nowMs: Long) {
        if (reading != null) {
            val cur = odometerRaw
            if (cur == null || (reading - cur) in 0.0..MAX_ODO_STEP) {
                odometerRaw = reading
                odoRejects = 0
            } else if (++odoRejects >= ODO_REJECT_LIMIT) {
                odometerRaw = reading
                odoAnchorKm = null
                odoRejects = 0
            }
        }
        val um = unitsMiles
        if (um != lastUnitsMiles) {
            lastUnitsMiles = um
            odoAnchorKm = null
        }
        val odoKmConv = odometerRaw?.let { if (um) it * MI_TO_KM else it }
        odoDisplayKm = odoKmConv

        if (speedKmh != null) {
            if (lastSpeedMs > 0) {
                val dtH = (nowMs - lastSpeedMs) / 3_600_000.0
                if (dtH in 0.0..0.1) sessionDist += speedKmh * dtH
            }
            lastSpeedMs = nowMs
        }
        if (odoKmConv != null) {
            if (odoAnchorKm == null) {
                odoAnchorKm = odoKmConv; sessionDist = 0.0
                speedGain = SEED_GAIN; calibOdo = null; calibSess = 0.0; lastTickOdo = odoKmConv
            }
            // recalibrate gain only when the odometer ticks (stable between ticks);
            // measure from the first tick so the mid-mile anchor offset is excluded
            if (odoKmConv > lastTickOdo + 1e-6) {
                val c0 = calibOdo
                if (c0 == null) { calibOdo = odoKmConv; calibSess = sessionDist }
                else {
                    // only calibrate over a long span: the odometer is integer km/mi
                    // and the anchor lands mid-tick, so short spans overstate it
                    val rawSpan = sessionDist - calibSess
                    if (rawSpan > CALIB_MIN_KM) {
                        speedGain = ((odoKmConv - c0) / rawSpan).coerceIn(0.85, 1.15)
                    }
                }
                lastTickOdo = odoKmConv
            }
            distanceKm = odoAnchorKm!! + sessionDist * speedGain
        }
    }

    /** Decode odometer km from broadcast 0x5C5: (B1<<16 | B2<<8 | B3). */
    private fun decodeOdometer(f: CanFrame?): Double? {
        if (f == null) return null
        val km = (f.u(1) shl 16) or (f.u(2) shl 8) or f.u(3)
        return km.toDouble().takeIf { it in 1.0..2_000_000.0 }
    }

    fun stop() {
        running = false
        runCatching { transport.close() }   // unblock the read
    }

    private fun publish(connected: Boolean) {
        lastProgressMs = System.currentTimeMillis()
        _state.value = DashState(
            leaf = leaf,
            connected = connected,
            connecting = false,
            raw = raw,
            debug = debug,
            odometerKm = distanceKm,   // smooth session distance -> tracker
            odoKm = odoDisplayKm,      // odometer reading -> Odo tile
        )
    }

    private companion object {
        const val MI_TO_KM = 1.609344
        // raw counter units per poll cycle beyond this = corrupt read (a cycle
        // is seconds; even 250 km/h moves it by ~1)
        const val MAX_ODO_STEP = 10.0
        const val ODO_REJECT_LIMIT = 3  // this many in a row = counter really moved
        const val SEED_GAIN = 1.0       // trust raw speed integral (matched car trip in logs)
        const val CALIB_MIN_KM = 16.0   // calibrate gain only past this raw distance
    }
}
