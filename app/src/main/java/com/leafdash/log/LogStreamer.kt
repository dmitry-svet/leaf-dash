package com.leafdash.log

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.LinkedBlockingDeque

/**
 * Streams diagnostic log lines to an HTTP endpoint (plain-text POST, batched
 * every few seconds). Lines are buffered in memory and re-queued if the
 * endpoint is unreachable; the buffer is capped so an offline drive can't
 * grow it without bound (oldest lines drop first).
 */
class LogStreamer {
    @Volatile var url: String = ""

    private val queue = LinkedBlockingDeque<String>(MAX_LINES)
    @Volatile private var running = true

    private val worker = Thread {
        while (running) {
            try { Thread.sleep(FLUSH_MS) } catch (_: InterruptedException) {}
            if (running) flush()
        }
    }.apply { isDaemon = true; name = "log-streamer"; start() }

    fun add(line: String) {
        if (url.isBlank()) return
        while (!queue.offerLast(line)) queue.pollFirst()
    }

    private fun flush() {
        val u = url
        if (u.isBlank() || queue.isEmpty()) return
        val batch = ArrayList<String>(MAX_BATCH)
        while (batch.size < MAX_BATCH) {
            batch.add(queue.pollFirst() ?: break)
        }
        val body = batch.joinToString("\n").toByteArray()
        val ok = runCatching {
            val conn = URL(u).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 3_000
                conn.readTimeout = 3_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "text/plain")
                // free ngrok tunnels answer with an interstitial page unless
                // this header is present
                conn.setRequestProperty("ngrok-skip-browser-warning", "1")
                conn.outputStream.use { it.write(body) }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
        if (!ok) {
            // put the batch back at the front, keeping order, for the next flush
            for (line in batch.asReversed()) {
                if (!queue.offerFirst(line)) break
            }
        }
    }

    fun stop() {
        running = false
        worker.interrupt()
    }

    private companion object {
        const val FLUSH_MS = 3_000L
        const val MAX_LINES = 5_000   // ~40 min of 2 Hz samples
        const val MAX_BATCH = 200
    }
}
