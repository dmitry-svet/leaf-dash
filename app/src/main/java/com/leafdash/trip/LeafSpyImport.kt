package com.leafdash.trip

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Imports drives from a LeafSpy Pro Trip/Charge log (`TripChrgLog_*.csv`):
 * one row per drive or charge, written when the car is turned off.
 *
 * Columns: Date, Time (drive start, local), odo, Duration, dist, elv, Energy
 * (net Wh of the drive), Gids (delta), SGids, EGids, AHr, SOH ("67.54%"), Hx,
 * SHVolt, EHVolt, Drive, Regen, Charge (cumulative counters), L1/L2, QC.
 * Charge rows have dist 0 and negative Energy. Distance/odometer are km or
 * miles per the LeafSpy units setting; the date is MM/DD/YYYY or DD/MM/YYYY
 * per its date setting (detected from the data).
 */
object LeafSpyImport {
    private const val MILE_KM = 1.609344
    private const val GID_WH = 77.5

    fun parse(text: String, zone: ZoneId): List<TripRecord> {
        val lines = text.lines().filter { it.isNotBlank() }
        val head = lines.firstOrNull()?.split(",")?.map { it.trim() } ?: return emptyList()
        val col = head.withIndex().associate { (i, h) -> h to i }
        val odoCol = col["odo km"] ?: col["odo mi"] ?: return emptyList()
        val distCol = col["dist km"] ?: col["dist mi"] ?: return emptyList()
        val toKm = if ("odo mi" in col) MILE_KM else 1.0
        val need = listOf("Date", "Time", "Duration", "Energy", "SGids", "EGids", "AHr", "SOH", "Hx", "SHVolt", "EHVolt")
        if (need.any { it !in col }) return emptyList()
        val rows = lines.drop(1).map { it.split(",") }.filter { it.size >= head.size }
        // US format unless some row has a first field > 12
        val dayFirst = rows.any { (it[col.getValue("Date")].substringBefore("/").toIntOrNull() ?: 0) > 12 }
        return rows.mapNotNull { c ->
            runCatching {
                fun s(name: String) = c[col.getValue(name)].trim()
                fun d(name: String) = s(name).toDouble()
                val dist = d("dist km".takeIf { distCol == col["dist km"] } ?: "dist mi") * toKm
                val energy = d("Energy")
                // charge / idle rows and corrupt rows (odo and dist swapped)
                if (dist <= 0 && energy <= 0) return@runCatching null
                if (dist > 500) return@runCatching null
                val (a, b, y) = s("Date").split("/").map { it.toInt() }
                val date = if (dayFirst) LocalDate.of(y, b, a) else LocalDate.of(y, a, b)
                val (hh, mm, ss) = s("Time").split(":").map { it.toInt() }
                val startMs = date.atTime(LocalTime.of(hh, mm, ss)).atZone(zone).toInstant().toEpochMilli()
                val (dh, dm, ds) = s("Duration").split(":").map { it.toInt() }
                val odo = c[odoCol].trim().toDouble() * toKm
                TripRecord(
                    startMs = startMs,
                    endMs = startMs + ((dh * 60 + dm) * 60 + ds) * 1000L,
                    startOdoKm = odo.takeIf { it > 0 },
                    distKm = dist,
                    energyWh = energy,
                    startKwh = d("SGids").takeIf { it > 0 }?.let { it * GID_WH / 1000 } ?: 0.0,
                    endKwh = d("EGids").takeIf { it > 0 }?.let { it * GID_WH / 1000 } ?: 0.0,
                    startSoc = null, endSoc = null,
                    ah = d("AHr").takeIf { it > 0 },
                    soh = s("SOH").removeSuffix("%").toDouble().takeIf { it > 0 },
                    hx = d("Hx").takeIf { it > 0 },
                    startV = d("SHVolt").takeIf { it > 0 },
                    endV = d("EHVolt").takeIf { it > 0 },
                    cellMinV = null, maxSpreadMv = null, batTempC = null, extTempC = null,
                )
            }.getOrNull()
        }
    }

    /**
     * [existing] plus those of [imported] whose time span does not overlap an
     * already kept trip; result oldest first. Re-importing the same file is a
     * no-op.
     */
    fun merge(existing: List<TripRecord>, imported: List<TripRecord>): List<TripRecord> {
        val kept = existing.toMutableList()
        for (t in imported.sortedBy { it.startMs }) {
            if (kept.none { it.startMs <= t.endMs && t.startMs <= it.endMs }) kept += t
        }
        return kept.sortedBy { it.startMs }
    }
}
