package com.leafdash.log

import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LeafSpy-format data log files, one per logging session (Start log .. Stop
 * log): LOG_FILES/Log_LeafDash_YYMMDD_HHmmss.csv, named by the start time.
 * Blocking file IO - call off the main thread.
 */
class DataLogStore(root: File) {
    private val dir = File(root, "LOG_FILES")

    /** File of the running session, null when stopped. */
    @Volatile var current: File? = null
        private set

    fun start(nowMs: Long) {
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyMMdd_HHmmss", Locale.US).format(Date(nowMs))
        val f = File(dir, "Log_LeafDash_$stamp.csv")
        f.writeText(csvLine(LeafSpyLog.HEADER))
        current = f
    }

    fun stop() {
        current = null
    }

    /** Appends to the running session, starting one if needed. */
    fun append(nowMs: Long, row: List<String>) {
        if (current == null) start(nowMs)
        current!!.appendText(csvLine(row))
    }

    /** Running session's file, else the newest one on disk. */
    fun latest(): File? =
        current ?: dir.listFiles { f -> f.name.endsWith(".csv") }?.maxByOrNull { it.name }

    /** All sessions merged oldest first, header once. */
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
