package com.leafdash.stats

import com.leafdash.trip.TripRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One bar: a day (week/month views) or a Monday-based week (year view). */
data class Bucket(val label: String, val km: Double, val wh: Double)

data class Period(val buckets: List<Bucket>, val trips: Int) {
    val totalKm: Double get() = buckets.sumOf { it.km }
    val totalWh: Double get() = buckets.sumOf { it.wh }
    val whPerKm: Double? get() = if (totalKm >= 1.0) totalWh / totalKm else null
}

/**
 * Trip-log aggregation for the statistics screen. Trips are bucketed by the
 * local date of their start; windows end today (inclusive).
 */
object Stats {
    /** Last 7 days, one bucket per day, oldest first. */
    fun week(trips: List<TripRecord>, today: LocalDate, zone: ZoneId) = daily(trips, today, zone, 7)

    /** Last 30 days, one bucket per day, oldest first. */
    fun month(trips: List<TripRecord>, today: LocalDate, zone: ZoneId) = daily(trips, today, zone, 30)

    /** Last 52 weeks (Monday-based), one bucket per week, oldest first; the last one is the current week. */
    fun year(trips: List<TripRecord>, today: LocalDate, zone: ZoneId): Period {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val first = thisMonday.minusWeeks(51)
        val inRange = trips.filter { !date(it, zone).isBefore(first) }
        val byWeek = inRange.groupBy { date(it, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        val buckets = (0 until 52).map { i ->
            val monday = first.plusWeeks(i.toLong())
            bucket("%02d.%02d".format(monday.dayOfMonth, monday.monthValue), byWeek[monday])
        }
        return Period(buckets, inRange.size)
    }

    private fun daily(trips: List<TripRecord>, today: LocalDate, zone: ZoneId, days: Int): Period {
        val first = today.minusDays(days - 1L)
        val inRange = trips.filter { !date(it, zone).isBefore(first) && !date(it, zone).isAfter(today) }
        val byDay = inRange.groupBy { date(it, zone) }
        val buckets = (0 until days).map { i ->
            val d = first.plusDays(i.toLong())
            bucket("%02d".format(d.dayOfMonth), byDay[d])
        }
        return Period(buckets, inRange.size)
    }

    private fun bucket(label: String, trips: List<TripRecord>?) =
        Bucket(label, trips?.sumOf { it.distKm } ?: 0.0, trips?.sumOf { it.energyWh } ?: 0.0)

    private fun date(t: TripRecord, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(t.startMs).atZone(zone).toLocalDate()
}
