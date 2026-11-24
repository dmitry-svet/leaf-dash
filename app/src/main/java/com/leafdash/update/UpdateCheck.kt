package com.leafdash.update

import java.net.HttpURLConnection
import java.net.URL

/**
 * Update check against the GitHub repo's tags (`vX.Y`, pushed with each
 * version bump). Public API, no token. The APK for a tag is the committed
 * `release/leafdash.apk` at that tag. Pure parsing here; [fetchNewest] does
 * the network call and must run off the main thread.
 */
object UpdateCheck {
    const val REPO = "dmitry-svet/leaf-dash"
    private const val TAGS_URL = "https://api.github.com/repos/$REPO/tags?per_page=100"
    private val TAG = Regex(""""name"\s*:\s*"v(\d+(?:\.\d+)*)"""")

    /** Highest `vX.Y` tag in a GitHub tags JSON array, or null if none. */
    fun newestVersion(tagsJson: String): String? =
        TAG.findAll(tagsJson).map { it.groupValues[1] }.maxWithOrNull(::compare)

    private const val DAY_MS = 24 * 3600 * 1000L

    /** Automatic check is due a day after the last successful one (or if the clock went back). */
    fun due(lastMs: Long?, nowMs: Long): Boolean = lastMs == null || nowMs - lastMs >= DAY_MS || nowMs < lastMs

    fun isNewer(version: String, than: String): Boolean = compare(version, than) > 0

    fun apkUrl(version: String) = "https://github.com/$REPO/raw/v$version/release/leafdash.apk"

    /** Newest tagged version on GitHub; null on any network/parse failure. */
    fun fetchNewest(): String? = runCatching {
        val conn = URL(TAGS_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) return null
            newestVersion(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrNull(i) ?: 0) - (y.getOrNull(i) ?: 0)
            if (d != 0) return d
        }
        return 0
    }
}
