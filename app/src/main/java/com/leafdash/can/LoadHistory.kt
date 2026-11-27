package com.leafdash.can

/** One load screen chart sample: motor (- = regen) and the climate split, kW. */
data class LoadPoint(val tMs: Long, val motorKw: Double?, val acKw: Double?, val heaterKw: Double?)

/** Last [windowMs] of load samples for the background charts; in memory only. */
class LoadHistory(private val windowMs: Long = 300_000) {
    private val buf = ArrayDeque<LoadPoint>()

    val points: List<LoadPoint> get() = buf.toList()

    fun add(tMs: Long, load: Load) {
        if (buf.isNotEmpty() && tMs < buf.last().tMs) buf.clear()
        buf.addLast(LoadPoint(tMs, load.motorKw, load.acKw, load.heaterKw))
        while (buf.first().tMs < tMs - windowMs) buf.removeFirst()
    }
}

private const val MIN_SPAN_KW = 15.0

/**
 * Motor chart range (lo to hi, kW) for the window: 0 sits at a third of the
 * height, the scale fits the largest traction (upper two thirds) and regen
 * (lower third), at least -5..10 kW so idle noise is not blown up.
 */
fun motorChartRange(points: List<LoadPoint>): Pair<Double, Double> {
    val vals = points.mapNotNull { it.motorKw }
    val pos = vals.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val neg = vals.minOrNull()?.let { -it }?.coerceAtLeast(0.0) ?: 0.0
    val span = maxOf(pos * 1.5, neg * 3, MIN_SPAN_KW)
    return -span / 3 to span * 2 / 3
}
