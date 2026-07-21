package com.leafdash.trip

import kotlin.math.abs
import kotlin.math.exp

/** One economy window: accumulated app-tracked distance and net energy. */
data class TripWindow(var km: Double = 0.0, var kwh: Double = 0.0) {
    /** Net consumption, kWh per 100 km. Null until enough distance driven. */
    val kwhPer100: Double?
        get() = if (km > MIN_KM) kwh / km * 100.0 else null

    fun add(dKm: Double, dKwh: Double) {
        km += dKm
        kwh += dKwh
    }

    fun reset() {
        km = 0.0
        kwh = 0.0
    }

    private companion object {
        const val MIN_KM = 0.05
    }
}

/** Persistable state. All distances are app-tracked accumulations. */
data class TripSnapshot(
    val lcKm: Double = 0.0,
    val lcKwh: Double = 0.0,
    val tripKm: Double = 0.0,
    val tripKwh: Double = 0.0,
    val lifetimeKm: Double = 0.0,
    val lifetimeKwh: Double = 0.0,
    val chargeMinSoc: Double? = null,
    val emaEff: Double = 15.0,
)

/**
 * Accumulates the economy windows from app-tracked samples only.
 *
 * Every window sums per-sample distance and energy increments while the app is
 * connected — driving with the app disconnected (and the odometer jump seen on
 * reconnect) is never counted, because each session rebaselines and only sane
 * per-sample deltas are added. Energy is counted only while actually moving
 * (speed >= MOVE_SPEED), so charging/idle while parked doesn't affect it.
 *
 *  - lastCharge : reset when a charge is detected (SOC climbs >=5% above low)
 *  - carOn      : reset at the start of each session
 *  - trip       : reset manually by the user
 *  - lifetime   : never reset
 */
class TripTracker(snapshot: TripSnapshot = TripSnapshot()) {

    val lastCharge = TripWindow(snapshot.lcKm, snapshot.lcKwh)
    val carOn = TripWindow()
    val trip = TripWindow(snapshot.tripKm, snapshot.tripKwh)
    val lifetime = TripWindow(snapshot.lifetimeKm, snapshot.lifetimeKwh)
    private val windows = listOf(lastCharge, carOn, trip, lifetime)

    private var prevOdo: Double? = null
    private var prevKwh: Double? = null
    private var badOdoStreak = 0
    private var chargeMinSoc: Double? = snapshot.chargeMinSoc

    /** Smoothed lifetime efficiency, kWh/100 km. */
    var avgKwhPer100: Double = snapshot.emaEff
        private set

    fun onSessionStart() {
        carOn.reset()
        prevOdo = null
        prevKwh = null
    }

    fun resetTrip() = trip.reset()

    fun onSample(kwhRemaining: Double?, cumKm: Double, soc: Double?, speedKmh: Double? = null) {
        val pOdo = prevOdo
        val pKwh = prevKwh
        if (pOdo == null) {
            prevOdo = cumKm
        } else {
            val dKm = cumKm - pOdo
            if (dKm in 0.0..MAX_JUMP) {          // sane, connected movement
                prevOdo = cumKm
                badOdoStreak = 0
                for (w in windows) w.km += dKm
                val moving = speedKmh != null && speedKmh >= MOVE_SPEED
                if (pKwh != null && kwhRemaining != null) {
                    val dKwh = pKwh - kwhRemaining
                    if (abs(dKwh) < MAX_KWH_STEP) {
                        // moving: net drain incl. regen. Stationary: only positive
                        // drain (heater/AC is real spend); a rise while parked is
                        // charging, not negative consumption
                        if (moving || dKwh > 0) {
                            for (w in windows) w.kwh += dKwh
                            if (moving && dKm >= 0.005) {
                                val instEff = (dKwh / dKm * 100.0).coerceIn(-20.0, 60.0)
                                val f = 1.0 - exp(-dKm / EMA_LEN_KM)
                                avgKwhPer100 = (avgKwhPer100 + f * (instEff - avgKwhPer100)).coerceIn(5.0, 60.0)
                            }
                        }
                    }
                }
            } else if (++badOdoStreak >= BAD_ODO_LIMIT) {
                // out-of-range deltas persisting means the distance source really
                // moved (e.g. re-anchor after a unit toggle) - rebaseline without
                // counting the gap. A single one is a corrupt read: keep the
                // baseline so the distance around it isn't lost.
                prevOdo = cumKm
                badOdoStreak = 0
            }
        }
        if (kwhRemaining != null) prevKwh = kwhRemaining

        // charge detection: SOC rising well above its trough means a charge
        if (soc != null) {
            val m = chargeMinSoc
            if (m == null || soc < m) {
                chargeMinSoc = soc
            } else if (soc > m + CHARGE_SOC_RISE) {
                lastCharge.reset()
                chargeMinSoc = soc
            }
        }
    }

    fun snapshot() = TripSnapshot(
        lcKm = lastCharge.km,
        lcKwh = lastCharge.kwh,
        tripKm = trip.km,
        tripKwh = trip.kwh,
        lifetimeKm = lifetime.km,
        lifetimeKwh = lifetime.kwh,
        chargeMinSoc = chargeMinSoc,
        emaEff = avgKwhPer100,
    )

    private companion object {
        const val MAX_KWH_STEP = 3.0    // kWh/sample beyond this = corrupt, skip
        // off-app gaps are excluded by the per-session rebaseline (prevOdo reset),
        // so this only guards corrupt single reads - allow within-session catch-up
        const val MAX_JUMP = 200.0      // km/sample beyond this = corrupt, skip
        const val BAD_ODO_LIMIT = 2     // consecutive bad deltas before rebaseline
        const val MOVE_SPEED = 1.0      // km/h; below this = stationary, skip energy
        const val CHARGE_SOC_RISE = 5.0 // % SOC rise above trough = charged
        const val EMA_LEN_KM = 8.0      // smoothing length for lifetime efficiency
    }
}
