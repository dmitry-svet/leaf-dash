package com.leafdash.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {

    private fun eff(theme: ThemeId, auto: AutoMode, systemDark: Boolean = false, lights: Boolean = false): ThemeId {
        AppTheme.theme = theme
        AppTheme.auto = auto
        AppTheme.systemDark = systemDark
        AppTheme.headlights = lights
        return AppTheme.effective
    }

    @Test fun offUsesChosenTheme() {
        assertEquals(ThemeId.OLED, eff(ThemeId.OLED, AutoMode.OFF, systemDark = false, lights = false))
        assertEquals(ThemeId.LIGHT, eff(ThemeId.LIGHT, AutoMode.OFF, systemDark = true, lights = true))
    }

    @Test fun systemFollowsPhoneNightMode() {
        assertEquals(ThemeId.LIGHT, eff(ThemeId.NIGHT, AutoMode.SYSTEM, systemDark = false))
        assertEquals(ThemeId.NIGHT, eff(ThemeId.NIGHT, AutoMode.SYSTEM, systemDark = true))
        assertEquals(ThemeId.DARK, eff(ThemeId.LIGHT, AutoMode.SYSTEM, systemDark = true))
    }

    @Test fun lightsFollowHeadlightsNotPhone() {
        assertEquals(ThemeId.LIGHT, eff(ThemeId.OLED, AutoMode.LIGHTS, systemDark = true, lights = false))
        assertEquals(ThemeId.OLED, eff(ThemeId.OLED, AutoMode.LIGHTS, systemDark = false, lights = true))
        assertEquals(ThemeId.DARK, eff(ThemeId.LIGHT, AutoMode.LIGHTS, lights = true))
    }
}
