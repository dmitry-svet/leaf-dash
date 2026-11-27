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
