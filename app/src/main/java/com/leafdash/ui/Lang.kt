package com.leafdash.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * UI language, English or Ukrainian. Global Compose state: every composable
 * that calls [t] recomposes when it flips. Persisted by TripStore, loaded in
 * DashboardViewModel.init.
 */
object Lang {
    var uk by mutableStateOf(false)
}

/** The string for the current UI language. */
fun t(en: String, uk: String): String = if (Lang.uk) uk else en
