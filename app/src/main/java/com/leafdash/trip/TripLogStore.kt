package com.leafdash.trip

import java.io.File

/**
 * Trip log on disk, in the export CSV format: finished trips appended to
 * trips.csv, the in-progress trip overwritten in trip_current.csv (survives
 * an app restart mid-drive). Blocking file IO - call off the main thread.
 */
class TripLogStore(dir: File) {
    private val finished = File(dir, "trips.csv")
    private val current = File(dir, "trip_current.csv")

    fun loadFinished(): List<TripRecord> =
        if (!finished.exists()) emptyList()
        else finished.readLines().drop(1).mapNotNull { TripRecord.fromCsv(it) }

    fun append(r: TripRecord) {
        if (!finished.exists() || finished.length() == 0L) {
            finished.writeText(TripRecord.HEADER.joinToString(",") + "\n")
        }
        finished.appendText(r.toCsv() + "\n")
    }

    fun loadCurrent(): TripRecord? =
        if (!current.exists()) null else TripRecord.fromCsv(current.readText().trim())

    fun saveCurrent(r: TripRecord?) {
        if (r == null) current.delete() else current.writeText(r.toCsv() + "\n")
    }

    companion object {
        /** Oldest first, as stored; the in-progress trip last if it has distance. */
        fun toCsv(trips: List<TripRecord>): String =
            (listOf(TripRecord.HEADER.joinToString(",")) + trips.map { it.toCsv() })
                .joinToString("\n", postfix = "\n")
    }
}
