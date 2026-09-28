package com.leafdash.trip

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** One poll cycle's worth of values for the trip log. */
data class TripSample(
    val tMs: Long,
    /** Poller session id: a new session re-anchors [distKm] on the odometer. */
    val session: Int,
    val odoKm: Double?,
    /** Smooth distance (odometer-anchored), km. */
    val distKm: Double,
    val kwh: Double,
    val soc: Double?,
    val packV: Double?,
    val cellMinV: Double?,
    val cellMaxV: Double?,
    val ah: Double?,
    val soh: Double?,
    val hx: Double?,
    val batTempC: Double?,
    val extTempC: Double?,
)

/**
 * One car-on-to-car-off drive. Values are rounded on the way in so the CSV
 * form round-trips exactly (it is also the storage format).
 */
data class TripRecord(
    val startMs: Long,
    val endMs: Long,
    val startOdoKm: Double?,
    /** App-connected distance, summed per sample within each session. */
    val distKm: Double,
    /** Net battery energy used, summed like [distKm] (idle and climate
     *  included, regen subtracted). */
    val energyWh: Double,
    val startKwh: Double,
    val endKwh: Double,
    val startSoc: Double?,
    val endSoc: Double?,
    val ah: Double?,
    val soh: Double?,
    val hx: Double?,
    val startV: Double?,
    val endV: Double?,
    /** Lowest cell voltage seen during the trip. */
    val cellMinV: Double?,
    /** Widest min-max cell spread seen during the trip. */
    val maxSpreadMv: Double?,
    val batTempC: Double?,
    val extTempC: Double?,
) {
    val durationMs: Long get() = endMs - startMs
    val whPerKm: Double? get() = if (distKm >= 0.1) energyWh / distKm else null

    /** Display/export columns, in [HEADER] order. */
    fun cells(): List<String> {
        val start = Date(startMs)
        return listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(start),
            SimpleDateFormat("HH:mm:ss", Locale.US).format(start),
            f(startOdoKm, 0),
            duration(durationMs),
            f(distKm, 1),
            f(energyWh, 0),
            f(whPerKm, 0),
            f(startSoc, 2), f(endSoc, 2),
            f(startKwh, 3), f(endKwh, 3),
            f(ah, 2), f(soh, 2), f(hx, 2),
            f(startV, 2), f(endV, 2),
            f(cellMinV, 3), f(maxSpreadMv, 0),
            f(batTempC, 1), f(extTempC, 1),
            startMs.toString(), endMs.toString(),
            f(distKm, 3), f(energyWh, 1),
        )
    }

    fun toCsv(): String = cells().joinToString(",")

    companion object {
        val HEADER = listOf(
            "Date", "Time", "Odo km", "Duration", "Dist km", "Energy Wh", "Wh/km",
            "Start SOC", "End SOC", "Start kWh", "End kWh", "AHr", "SOH", "Hx",
            "Start V", "End V", "Cell min V", "Max spread mV", "Bat temp C", "Ext temp C",
            "Start ms", "End ms", "Dist acc km", "Energy acc Wh",
        )

        /** v0.69/0.70 header: last two columns were start/end smooth distance. */
        val LEGACY_HEADER = HEADER.dropLast(2) + listOf("Start dist", "End dist")

        /**
         * Parses a [toCsv] line; the derived columns (date, duration...) are
         * ignored. [legacy] = old format, distance/energy from start/end values.
         */
        fun fromCsv(line: String, legacy: Boolean = false): TripRecord? = runCatching {
            val c = line.split(",")
            if (c.size != HEADER.size) return null
            fun d(i: Int) = c[i].toDoubleOrNull()
            val startKwh = c[9].toDouble()
            val endKwh = c[10].toDouble()
            TripRecord(
                startMs = c[20].toLong(), endMs = c[21].toLong(),
                startOdoKm = d(2),
                // legacy differences rounded to the stored precision so the row
                // re-saves in the new format without drifting
                distKm = if (legacy) round(c[23].toDouble() - c[22].toDouble(), 3) else c[22].toDouble(),
                energyWh = if (legacy) round((startKwh - endKwh) * 1000.0, 1) else c[23].toDouble(),
                startKwh = startKwh, endKwh = endKwh,
                startSoc = d(7), endSoc = d(8),
                ah = d(11), soh = d(12), hx = d(13),
                startV = d(14), endV = d(15),
                cellMinV = d(16), maxSpreadMv = d(17),
                batTempC = d(18), extTempC = d(19),
            )
        }.getOrNull()

        internal fun round(v: Double, digits: Int): Double {
            val m = 10.0.pow(digits)
            return (v * m).roundToLong() / m
        }

        private fun f(v: Double?, digits: Int): String =
            if (v == null) "" else String.format(Locale.US, "%.${digits}f", v)

        private fun duration(ms: Long): String {
            val s = ms / 1000
            return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        }
    }
}

/**
 * Splits the sample stream into drives: a gap longer than [gapMs] with no
 * samples means the car was off. Short BT dropouts stay inside one trip.
 */
class TripLogger(
    /** In-progress trip restored from storage (app restarted mid-drive). */
    initial: TripRecord? = null,
    private val gapMs: Long = GAP_MS,
) {
    var current: TripRecord? = initial
        private set

    // previous sample, for per-sample deltas; not persisted (a restart is a new
    // session anyway, so the first sample after it only rebaselines)
    private var prev: TripSample? = null

    /** Folds a sample in; returns the trip it just finished, if any. */
    fun onSample(s: TripSample): TripRecord? {
        val cur = current
        val p = prev
        prev = s
        if (cur == null || s.tMs - cur.endMs > gapMs) {
            current = start(s)
            return cur?.takeIf { it.distKm >= MIN_TRIP_KM }
        }
        // deltas only within one poller session: a new session re-anchors the
        // distance on the integer-mile odometer (jumps back up to 1.6 km), and
        // driving while the link was down is excluded from distance and energy
        // alike so Wh/km stays honest - same rule as the economy windows
        val same = p != null && p.session == s.session
        val dKm = if (same) (s.distKm - p!!.distKm).coerceAtLeast(0.0) else 0.0
        val dWh = if (same) (p!!.kwh - s.kwh) * 1000.0 else 0.0
        val spread = spreadMv(s)
        current = cur.copy(
            endMs = s.tMs,
            distKm = r(cur.distKm + dKm, 3),
            energyWh = r(cur.energyWh + dWh, 1),
            endKwh = r(s.kwh, 3),
            endSoc = s.soc?.let { r(it, 2) } ?: cur.endSoc,
            ah = s.ah?.let { r(it, 2) } ?: cur.ah,
            soh = s.soh?.let { r(it, 2) } ?: cur.soh,
            hx = s.hx?.let { r(it, 2) } ?: cur.hx,
            endV = s.packV?.let { r(it, 2) } ?: cur.endV,
            cellMinV = minOf(cur.cellMinV, s.cellMinV?.let { r(it, 3) }),
            maxSpreadMv = maxOf(cur.maxSpreadMv, spread),
            batTempC = s.batTempC?.let { r(it, 1) } ?: cur.batTempC,
            extTempC = s.extTempC?.let { r(it, 1) } ?: cur.extTempC,
        )
        return null
    }

    /** The in-progress trip if it is worth keeping (has real distance). */
    fun finish(): TripRecord? = current?.takeIf { it.distKm >= MIN_TRIP_KM }

    private fun start(s: TripSample) = TripRecord(
        startMs = s.tMs, endMs = s.tMs,
        startOdoKm = s.odoKm?.let { r(it, 0) },
        distKm = 0.0, energyWh = 0.0,
        startKwh = r(s.kwh, 3), endKwh = r(s.kwh, 3),
        startSoc = s.soc?.let { r(it, 2) }, endSoc = s.soc?.let { r(it, 2) },
        ah = s.ah?.let { r(it, 2) }, soh = s.soh?.let { r(it, 2) }, hx = s.hx?.let { r(it, 2) },
        startV = s.packV?.let { r(it, 2) }, endV = s.packV?.let { r(it, 2) },
        cellMinV = s.cellMinV?.let { r(it, 3) }, maxSpreadMv = spreadMv(s),
        batTempC = s.batTempC?.let { r(it, 1) }, extTempC = s.extTempC?.let { r(it, 1) },
    )

    private fun spreadMv(s: TripSample): Double? =
        if (s.cellMinV != null && s.cellMaxV != null) r((s.cellMaxV - s.cellMinV) * 1000.0, 0) else null

    private fun minOf(a: Double?, b: Double?) = if (a == null) b else if (b == null) a else min(a, b)
    private fun maxOf(a: Double?, b: Double?) = if (a == null) b else if (b == null) a else max(a, b)

    private fun r(v: Double, digits: Int) = TripRecord.round(v, digits)

    companion object {
        /** No samples for this long = the car was off (matches the "since car on" window). */
        const val GAP_MS = 30 * 60 * 1000L
        const val MIN_TRIP_KM = 0.1
    }
}
