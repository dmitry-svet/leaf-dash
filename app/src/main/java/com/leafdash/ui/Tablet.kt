package com.leafdash.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Tablet held in landscape (shortest side >= 600 dp, e.g. a 10" 1280x800
 * tab): the dashboard, load and statistics screens switch to layouts that
 * fill the screen without scrolling. Phones, also in landscape, are never
 * affected.
 */
@Composable
fun tabletLandscape(): Boolean {
    val c = LocalConfiguration.current
    return c.smallestScreenWidthDp >= 600 && c.orientation == Configuration.ORIENTATION_LANDSCAPE
}
