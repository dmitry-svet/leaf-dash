package com.leafdash.can

import kotlin.math.roundToLong

/**
 * Tire pressure alert from the TPMS pressures (PSI, FL/FR/RR/RL, null =
 * unknown). Compared in bar rounded to 0.01, as Settings shows them: a tire
 * below the user threshold is [low]; a max-min spread of [UNEVEN_BAR] or more
 * across the known tires is [uneven].
 */
data class TireCheck(val low: Set<Int>, val uneven: Boolean, val spreadBar: Double) {
    companion object {
        const val BAR_PER_PSI = 0.0689476
        const val UNEVEN_BAR = 0.2
        /** Threshold choices in Settings, bar. */
        val THRESHOLDS = (19..25).map { it / 10.0 }
        const val DEFAULT_MIN_BAR = 2.2

        private fun bar(psi: Double) = (psi * BAR_PER_PSI * 100).roundToLong() / 100.0

        /** null = no alert (or no data). */
        fun of(psi: List<Double?>, minBar: Double): TireCheck? {
            val known = psi.withIndex().mapNotNull { (i, p) -> p?.let { i to bar(it) } }
            if (known.isEmpty()) return null
            val low = known.filter { it.second < minBar - 1e-9 }.map { it.first }.toSet()
            val spread = known.maxOf { it.second } - known.minOf { it.second }
            val uneven = known.size >= 2 && spread >= UNEVEN_BAR - 1e-9
            return if (low.isEmpty() && !uneven) null else TireCheck(low, uneven, spread)
        }
    }
}
