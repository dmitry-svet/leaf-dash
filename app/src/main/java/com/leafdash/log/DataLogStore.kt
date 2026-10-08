package com.leafdash.log

import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LeafSpy-format data log files, one per day: LOG_FILES/Log_LeafDash_YYMMDD.csv.
 * Like LeafSpy, the day is the drive's start day, so a drive through midnight
 * stays in one file. Blocking file IO - call off the main thread.
 */
class DataLogStore(root: File) {
    private val dir = File(root, "LOG_FILES")

    fun append(dayMs: Long, row: List<String>) {
        dir.mkdirs()
        val day = SimpleDateFormat("yyMMdd", Locale.US).format(Date(dayMs))
        val f = File(dir, "Log_LeafDash_$day.csv")
        if (!f.exists() || f.length() == 0L) f.writeText(csvLine(LeafSpyLog.HEADER))
        f.appendText(csvLine(row))
    }

    /** All days merged oldest first, header once. */
    fun exportAll(out: OutputStream) {
        val files = dir.listFiles { f -> f.name.endsWith(".csv") }?.sortedBy { it.name } ?: emptyList()
        out.bufferedWriter().use { w ->
            w.write(csvLine(LeafSpyLog.HEADER))
            for (f in files) {
                f.bufferedReader().useLines { lines -> lines.drop(1).forEach { w.write(it); w.write("\n") } }
            }
        }
    }

    val path: String get() = dir.absolutePath

    private fun csvLine(cells: List<String>) = cells.joinToString(",") + "\n"
}
