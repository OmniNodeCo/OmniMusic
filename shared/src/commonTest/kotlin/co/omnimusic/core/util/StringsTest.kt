package co.omnimusic.core.util

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringsTest {

    fun testFormatDuration() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:00", formatDuration(-5))
        assertEquals("0:09", formatDuration(9))
        assertEquals("0:10", formatDuration(10))
        assertEquals("3:46", formatDuration(226))
        assertEquals("5:37", formatDuration(337))
        assertEquals("1:02:05", formatDuration(3725))
    }

    fun testUrlEncodeLeavesUnreservedCharactersAlone() {
        assertEquals("Daft_Punk-1.0~x", urlEncode("Daft_Punk-1.0~x"))
    }

    fun testUrlEncodeEscapesReservedAndNonAsciiCharacters() {
        assertEquals("daft%20punk%26co", urlEncode("daft punk&co"))
        // "é" is two UTF-8 bytes, both must be escaped.
        assertEquals("caf%C3%A9", urlEncode("café"))
    }

    fun testBuildUrlAppendsQueryOnlyOnce() {
        assertEquals("https://api.test/search?q=a%20b", buildUrl("https://api.test/search", mapOf("q" to "a b")))
        assertEquals("https://api.test/search?limit=2", buildUrl("https://api.test/search", mapOf("limit" to "2")))
        assertEquals(
            "https://api.test/search?limit=2&index=4",
            buildUrl("https://api.test/search?limit=2", mapOf("index" to "4")),
        )
        assertEquals("https://api.test/search", buildUrl("https://api.test/search", emptyMap()))
    }

    fun testNormalizeForSearchFoldsCaseAndPunctuation() {
        assertEquals("harder better faster stronger", normalizeForSearch("Harder,  Better—Faster  STRONGER"))
        assertEquals("rap hip hop", normalizeForSearch("Rap/Hip Hop"))
    }

    fun testOrPlaceholder() {
        assertEquals("n/a", null.orPlaceholder("n/a"))
        assertEquals("n/a", "   ".orPlaceholder("n/a"))
        assertEquals("x", "x".orPlaceholder("n/a"))
    }

    fun testGuardDefaultIsTransparent() {
        assertTrue(NoGuard.exclusive { true })
        assertFalse(NoGuard.exclusive { false })
    }
}
