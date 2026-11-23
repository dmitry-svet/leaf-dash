package com.leafdash.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    private val tagsJson = """[{"name":"v0.91","zipball_url":"z","tarball_url":"t",
        "commit":{"sha":"abc","url":"u"},"node_id":"n"},
        {"name":"v0.9","commit":{"sha":"d","url":"u"}},
        {"name":"v0.100","commit":{"sha":"e","url":"u"}},
        {"name":"junk","commit":{"sha":"f","url":"u"}}]"""

    @Test fun newestTagIsNumericallyHighest() {
        assertEquals("0.100", UpdateCheck.newestVersion(tagsJson))
    }

    @Test fun noTagsGivesNull() {
        assertNull(UpdateCheck.newestVersion("[]"))
        assertNull(UpdateCheck.newestVersion("{\"message\":\"Not Found\"}"))
    }

    @Test fun comparesNumerically() {
        assertTrue(UpdateCheck.isNewer("0.91", than = "0.90"))
        assertTrue(UpdateCheck.isNewer("0.100", than = "0.91"))
        assertTrue(UpdateCheck.isNewer("1.0", than = "0.99"))
        assertFalse(UpdateCheck.isNewer("0.90", than = "0.90"))
        assertFalse(UpdateCheck.isNewer("0.89", than = "0.90"))
    }

    @Test fun apkUrlPointsAtTheTag() {
        assertEquals(
            "https://github.com/dmitry-svet/leaf-dash/raw/v0.91/release/leafdash.apk",
            UpdateCheck.apkUrl("0.91"),
        )
    }
}
