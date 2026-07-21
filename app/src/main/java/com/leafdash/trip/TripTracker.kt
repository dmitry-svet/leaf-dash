package com.leafdash.trip

import kotlin.math.abs
import kotlin.math.exp

/** One economy window: distance (from odometer endpoints) and net energy. */
data class TripWindow(var km: Double = 0.0, var kwh: Double = 0.0) {
    /** Net consumption, kWh per 100 km. Null until enough distance driven. */
    val kwhPer100: Double?
        get() = if (km > MIN_KM) kwh / km * 100.0 else null

    fun reset() {
        km = 0.0
        kwh = 0.0
    }

    private companion object {
        const val MIN_KM = 0.05
    }
}

/**
 * Persistable state. Distance is derived from an odometer baseline per window
 * (start odometer reading), so it survives across sessions without summing.
 */
data class TripSnapshot(
    val lcStartOdo: Double? = null,
    val lcKwh: Double = 0.0,
    val tripStartOdo: Double? = null,
    val tripKwh: Double = 0.0,
    val chargeMinSoc: Double? = null,
    /** Smoothed lifetime efficiency (kWh/100 km) for stable range estimates. */
    val emaEff: Double = 15.0,
    /** All-time totals (never reset). Distance is app-tracked only, not odometer. */
    val lifetimeKm: Double = 0.0,
    val lifetimeKwh: Double = 0.0,
)

/**
 * Accumulates the three economy windows.
 *
 * Distance = current odometer − window's start odometer. The odometer is
 * absolute and monotonic, so this is immune to dropped samples / corrupt
 * reads (only the endpoints matter) — unlike summing per-sample deltas, which
 * loses distance on every gap. Energy is still the summed drop in kWh (SOC is
 * not monotonic: regen and charging move it both ways).
 *
 *  - lastCharge : reset when a charge is detected (SOC climbs >=5% above low)
 *  - carOn      : reset at the start of each session
 *  - trip       : reset manually by the user
 */
class TripTracker(snapshot: TripSnapshot = TripSnapshot()) {

    val lastCharge = TripWindow(kwh = snapshot.lcKwh)
    val carOn = TripWindow()
    val trip = TripWindow(kwh = snapshot.tripKwh)
    val lifetime = TripWindow(km = snapshot.lifetimeKm, kwh = snapshot.lifetimeKwh)

    private var lcStartOdo: Double? = snapshot.lcStartOdo
    private var coStartOdo: Double? = null
    private var tripStartOdo: Double? = snapshot.tripStartOdo
    private var curOdo: Double? = null
    private var prevOdo: Double? = null
    private var prevKwh: Double? = null
    private var chargeMinSoc: Double? = snapshot.chargeMinSoc

    /** Smoothed lifetime efficiency, kWh/100 km. */
    var avgKwhPer100: Double = snapshot.emaEff
        private set

    fun onSessionStart() {
        coStartOdo = null          // rebaselined on next sample
        carOn.reset()
        prevKwh = null
        prevOdo = null
    }

    fun resetTrip() {
        tripStartOdo = curOdo
        trip.reset()
    }

    fun onSample(kwhRemaining: Double?, cumKm: Double, soc: Double?, speedKmh: Double? = null) {
        curOdo = cumKm
        if (lcStartOdo == null) lcStartOdo = cumKm
        if (coStartOdo == null) coStartOdo = cumKm
        if (tripStartOdo == null) tripStartOdo = cumKm

        // distance from odometer endpoints (monotonic, gap-proof)
        lastCharge.km = (cumKm - lcStartOdo!!).coerceAtLeast(0.0)
        carOn.km = (cumKm - coStartOdo!!).coerceAtLeast(0.0)
        trip.km = (cumKm - tripStartOdo!!).coerceAtLeast(0.0)

        // lifetime distance = app-tracked increments only (excludes driving with
        // the app disconnected, and session-boundary odometer jumps)
        prevOdo?.let { pOdo ->
            val dCum = cumKm - pOdo
            if (dCum in 0.0..MAX_JUMP) lifetime.km += dCum
        }

        // energy: summed drop in kWh remaining, counted ONLY while actually
        // moving (speed >= MOVE_SPEED). Skipping stationary samples excludes
        // charging-while-parked (would subtract energy) and parked idle draw.
        val pOdo = prevOdo
        val pKwh = prevKwh
        val moving = speedKmh != null && speedKmh >= MOVE_SPEED
        if (moving && pKwh != null && kwhRemaining != null) {
            val dKwh = pKwh - kwhRemaining
            if (abs(dKwh) < MAX_KWH_STEP) {
                lastCharge.kwh += dKwh
                carOn.kwh += dKwh
                trip.kwh += dKwh
                lifetime.kwh += dKwh
                if (pOdo != null) {
                    val dKm = cumKm - pOdo
                    if (dKm in 0.005..MAX_JUMP) {
                        val instEff = (dKwh / dKm * 100.0).coerceIn(-20.0, 60.0)
                        val f = 1.0 - exp(-dKm / EMA_LEN_KM)
                        avgKwhPer100 = (avgKwhPer100 + f * (instEff - avgKwhPer100)).coerceIn(5.0, 60.0)
                    }
                }
            }
        }
        if (kwhRemaining != null) prevKwh = kwhRemaining
        prevOdo = cumKm

        // charge detection: SOC rising well above its trough means a charge
        if (soc != null) {
            val m = chargeMinSoc
            if (m == null || soc < m) {
                chargeMinSoc = soc
            } else if (soc > m + CHARGE_SOC_RISE) {
                lcStartOdo = cumKm
                lastCharge.reset()
                chargeMinSoc = soc
            }
        }
    }

    fun snapshot() = TripSnapshot(
        lcStartOdo = lcStartOdo,
        lcKwh = lastCharge.kwh,
        tripStartOdo = tripStartOdo,
        tripKwh = trip.kwh,
        chargeMinSoc = chargeMinSoc,
        emaEff = avgKwhPer100,
        lifetimeKm = lifetime.km,
        lifetimeKwh = lifetime.kwh,
    )

    private companion object {
        const val MAX_KWH_STEP = 3.0    // kWh/sample beyond this = corrupt, skip
        const val MOVE_SPEED = 1.0      // km/h; below this = stationary, skip energy
        const val MAX_JUMP = 5.0        // km/sample beyond this = gap, skip EMA
        const val CHARGE_SOC_RISE = 5.0 // % SOC rise above trough = charged
        const val EMA_LEN_KM = 8.0      // smoothing length for lifetime efficiency
    }
}
