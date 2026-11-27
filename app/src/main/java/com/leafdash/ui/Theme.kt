package com.leafdash.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Selectable UI themes. Stored by name in TripStore. */
enum class ThemeId { LIGHT, DARK, OLED, NIGHT }

/** App colors that Material's ColorScheme does not cover (chart series, status). */
data class Pal(
    val mot: Color, val reg: Color, val ac: Color, val heat: Color, val aux: Color, val oth: Color,
    val con: Color, val soh: Color, val hx: Color,
    val ok: Color, val warn: Color, val danger: Color,
)

/**
 * Global theme state, like [Lang]: composables reading [pal] or the scheme
 * recompose when it changes. [theme]/[auto] are persisted by TripStore and
 * loaded in DashboardViewModel.init; [systemDark] is set by MainActivity
 * (the activity is recreated when the system night mode flips).
 * Auto: system light -> LIGHT, system dark -> the chosen theme (DARK if LIGHT is chosen).
 */
object AppTheme {
    var theme by mutableStateOf(ThemeId.LIGHT)
    var auto by mutableStateOf(false)
    var systemDark by mutableStateOf(false)

    val effective: ThemeId
        get() = when {
            !auto -> theme
            !systemDark -> ThemeId.LIGHT
            theme == ThemeId.LIGHT -> ThemeId.DARK
            else -> theme
        }

    val pal: Pal get() = PALS.getValue(effective)
    val scheme: ColorScheme get() = SCHEMES.getValue(effective)
}

private fun c(hex: Long) = Color(0xFF000000 or hex)

private val PALS = mapOf(
    ThemeId.LIGHT to Pal(
        mot = c(0x6650A4), reg = c(0x2E8C46), ac = c(0x1E78C8), heat = c(0xD95A1A), aux = c(0x0F8A8A), oth = c(0x827C88),
        con = c(0xC8285A), soh = c(0xD9731A), hx = c(0x0F8A8A),
        ok = c(0x2E7D32), warn = c(0xB58900), danger = c(0xC62828),
    ),
    ThemeId.DARK to Pal(
        mot = c(0xD0BCFF), reg = c(0x7FD48F), ac = c(0x7DB8F0), heat = c(0xFF9A5C), aux = c(0x4FD1C5), oth = c(0xA8A2AE),
        con = c(0xFF7AA2), soh = c(0xFFB15C), hx = c(0x4FD1C5),
        ok = c(0x7FD48F), warn = c(0xFFD54F), danger = c(0xFF6B6B),
    ),
    ThemeId.OLED to Pal(
        mot = c(0xB39DFF), reg = c(0x3DDC6E), ac = c(0x3FA9FF), heat = c(0xFF7A2E), aux = c(0x1FD6C4), oth = c(0x8E8E8E),
        con = c(0xFF4F86), soh = c(0xFFA53D), hx = c(0x1FD6C4),
        ok = c(0x3DDC6E), warn = c(0xFFC400), danger = c(0xFF5252),
    ),
    // night drive: warm amber text, dimmed accents; SOH is not amber (the bars are)
    ThemeId.NIGHT to Pal(
        mot = c(0xE0A458), reg = c(0x8CBF73), ac = c(0x6F9CC0), heat = c(0xD9704A), aux = c(0x5FA89A), oth = c(0x7D7468),
        con = c(0xD9607A), soh = c(0xB08CC8), hx = c(0x5FA89A),
        ok = c(0x8CBF73), warn = c(0xD9A441), danger = c(0xD9534A),
    ),
)

private fun dark(bg: Long, card: Long, text: Long, sub: Long, track: Long, primary: Long, onPrimary: Long, container: Long) =
    darkColorScheme(
        primary = c(primary), onPrimary = c(onPrimary),
        secondaryContainer = c(container), onSecondaryContainer = c(text),
        background = c(bg), onBackground = c(text),
        surface = c(bg), onSurface = c(text),
        surfaceVariant = c(card), onSurfaceVariant = c(sub),
        surfaceContainer = c(card), surfaceContainerHigh = c(card), surfaceContainerHighest = c(card),
        outline = c(sub), outlineVariant = c(track),
    )

private val SCHEMES = mapOf(
    ThemeId.LIGHT to lightColorScheme(),
    ThemeId.DARK to dark(0x141218, 0x2B2930, 0xE6E1E5, 0xCAC4D0, 0x49454F, 0xD0BCFF, 0x381E72, 0x4A4458),
    ThemeId.OLED to dark(0x000000, 0x151515, 0xFFFFFF, 0x9E9E9E, 0x333333, 0xB39DFF, 0x000000, 0x2A2A2A),
    ThemeId.NIGHT to dark(0x000000, 0x0F0E0C, 0xE8C98F, 0x8C7B5E, 0x2A261F, 0xE0A458, 0x000000, 0x2A261F),
)
