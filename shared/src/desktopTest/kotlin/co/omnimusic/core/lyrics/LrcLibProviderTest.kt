package co.omnimusic.core.lyrics

import co.omnimusic.core.model.Load
import co.omnimusic.core.testing.FixtureHttpFetcher
import co.omnimusic.core.testing.testTrack
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs against the recorded LRCLIB response in `lrclib_synced.json`. */
class LrcLibProviderTest {

    private fun provider(fixture: String) = LrcLibProvider(FixtureHttpFetcher().route("/api/get", fixture))

    fun testParsesTheRecordedSyncedResponse() {
        val fetcher = FixtureHttpFetcher().route("/api/get", "lrclib_synced.json")
        val lyrics = LrcLibProvider(fetcher).fetch("Daft Punk", "One More Time", "Discovery", 320)

        assertNotNull(lyrics)
        assertEquals("One More Time", lyrics.trackName)
        assertEquals("Daft Punk", lyrics.artistName)
        assertEquals("Discovery", lyrics.albumName)
        assertEquals(320_000L, lyrics.durationMillis)
        assertFalse(lyrics.instrumental)
        assertTrue(lyrics.isSynced)

        // Timestamps come straight out of the recorded LRC body.
        assertEquals(30_750L, lyrics.lines.first().startMillis)
        assertEquals("One more time", lyrics.lines.first().text)
        assertEquals(64_920L, lyrics.lines[5].startMillis)
        assertEquals("We're gonna celebrate", lyrics.lines[5].text)
        // LRCLIB marks instrumental gaps with empty lines; they survive the round trip.
        assertTrue(lyrics.lines.any { it.isBlankLine })
        assertEquals("Celebration tonight", lyrics.lineAt(88_690)?.text)

        assertContains(fetcher.lastRequest(), "artist_name=Daft%20Punk")
        assertContains(fetcher.lastRequest(), "track_name=One%20More%20Time")
        assertContains(fetcher.lastRequest(), "album_name=Discovery")
        assertContains(fetcher.lastRequest(), "duration=320")
    }

    fun testPlainOnlyLyricsAreStillUsable() {
        val lyrics = provider("lrclib_plain_only.json").fetch("Daft Punk", "Aerodynamic")!!
        assertFalse(lyrics.isSynced)
        assertEquals(listOf("Instrumental break", "Second verse"), lyrics.plainLines)
    }

    fun testAMissingTrackIsNullNotAnError() {
        val fetcher = FixtureHttpFetcher().route("/api/get", "lrclib_not_found.json")
        fetcher.statusCodeOverride = 404
        assertNull(LrcLibProvider(fetcher).fetch("Nobody", "Nothing"))
    }

    fun testAServerErrorIsReportedNotSwallowed() {
        val fetcher = FixtureHttpFetcher().route("/api/get", "lrclib_not_found.json")
        fetcher.statusCodeOverride = 503
        val error = runCatching { LrcLibProvider(fetcher).fetch("Nobody", "Nothing") }.exceptionOrNull()
        assertNotNull(error)
        assertContains(error.message!!, "503")
    }

    fun testBlankAlbumAndZeroDurationAreLeftOutOfTheQuery() {
        val fetcher = FixtureHttpFetcher().route("/api/get", "lrclib_plain_only.json")
        LrcLibProvider(fetcher).fetch("Daft Punk", "Aerodynamic", album = "  ", durationSeconds = 0)
        val url = fetcher.lastRequest()
        assertFalse(url.contains("album_name"), url)
        assertFalse(url.contains("duration="), url)
    }

    fun testTheLibraryCachesHitsSoLyricsAreFetchedOncePerTrack() {
        val fetcher = FixtureHttpFetcher().route("/api/get", "lrclib_synced.json")
        val library = LyricsLibrary(LrcLibProvider(fetcher))
        val track = testTrack("3135553", title = "One More Time", artistName = "Daft Punk")

        val first = assertIs<Load.Success<*>>(library.forTrack(track))
        val second = assertIs<Load.Success<*>>(library.forTrack(track))

        assertEquals(1, fetcher.requested.size)
        assertSame(first.value, second.value)
    }

    fun testTheLibraryAlsoCachesMisses() {
        val fetcher = FixtureHttpFetcher().route("/api/get", "lrclib_not_found.json")
        fetcher.statusCodeOverride = 404
        val library = LyricsLibrary(LrcLibProvider(fetcher))
        val track = testTrack("x", title = "Nothing", artistName = "Nobody")

        assertNull((library.forTrack(track) as Load.Success).value)
        assertNull((library.forTrack(track) as Load.Success).value)
        assertEquals(1, fetcher.requested.size, "a track with no lyrics should not be looked up twice")
    }

    fun testTheLibraryReportsTransportFailures() {
        val fetcher = FixtureHttpFetcher().route("/api/get", "lrclib_not_found.json")
        fetcher.statusCodeOverride = 500
        val library = LyricsLibrary(LrcLibProvider(fetcher))
        assertIs<Load.Failure>(library.forTrack(testTrack("y")))
    }

    private fun assertSame(expected: Any?, actual: Any?) =
        assertTrue(expected === actual, "expected the cached instance to be reused")
}
