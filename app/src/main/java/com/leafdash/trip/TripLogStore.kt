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

    fun loadFinished(): List<TripRecord> = parse(finished)

    fun append(r: TripRecord) {
        if (!finished.exists() || finished.length() == 0L) {
            finished.writeText(HEADER_LINE + "\n")
        } else if (isLegacy(finished)) {
            // rewrite old-format rows in the current format before appending
            val old = parse(finished)
            finished.writeText(toCsv(old))
        }
        finished.appendText(r.toCsv() + "\n")
    }

    fun loadCurrent(): TripRecord? = parse(current).lastOrNull()

    fun saveCurrent(r: TripRecord?) {
        if (r == null) current.delete() else current.writeText(toCsv(listOf(r)))
    }

    private fun isLegacy(f: File) =
        f.bufferedReader().use { it.readLine() } == TripRecord.LEGACY_HEADER.joinToString(",")

    private fun parse(f: File): List<TripRecord> {
        if (!f.exists()) return emptyList()
        val lines = f.readLines().filter { it.isNotBlank() }
        val head = lines.firstOrNull() ?: return emptyList()
        val legacy = head == TripRecord.LEGACY_HEADER.joinToString(",")
        // v0.69 trip_current.csv had no header line: unknown format, drop it
        if (!legacy && head != HEADER_LINE) return emptyList()
        return lines.drop(1).mapNotNull { TripRecord.fromCsv(it, legacy) }
    }

    companion object {
        private val HEADER_LINE = TripRecord.HEADER.joinToString(",")

        /** Oldest first, as stored; the in-progress trip last if it has distance. */
        fun toCsv(trips: List<TripRecord>): String =
            (listOf(HEADER_LINE) + trips.map { it.toCsv() })
                .joinToString("\n", postfix = "\n")
    }
}
