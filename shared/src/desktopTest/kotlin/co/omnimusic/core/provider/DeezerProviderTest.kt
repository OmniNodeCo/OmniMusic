package co.omnimusic.core.provider

import co.omnimusic.core.net.ApiError
import co.omnimusic.core.provider.deezer.DeezerProvider
import co.omnimusic.core.util.StreamLink
import co.omnimusic.core.testing.FixtureHttpFetcher
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * These run against the recorded Deezer responses in `shared/src/jvmTest/fixtures`, so they assert
 * the real wire format rather than a shape invented for the test.
 */
class DeezerProviderTest {

    private val fetcher = FixtureHttpFetcher.fullyRouted()
    private val provider = DeezerProvider(fetcher)

    fun testProviderIdentity() {
        assertEquals("deezer", provider.id)
        assertEquals("Deezer", provider.displayName)
    }

    fun testTrackFetchesOneTrackWithAFreshSignedLink() {
        val track = provider.track("2868828162")

        assertEquals("2868828162", track.id)
        assertEquals("NEVER GET USED TO THIS (feat. JVKE)", track.title)
        assertEquals("Forrest Frank", track.artist.name)
        // The featured artist is in the title, not the artist field: this track is filed under
        // Forrest Frank alone, which is what the search result says too.
        assertEquals("Forrest Frank", track.artistName)
        assertContains(track.title, "feat. JVKE")
        assertEquals("NEVER GET USED TO THIS", track.album?.title)
        assertEquals(145, track.durationSeconds)
        assertEquals(1, fetcher.requestsTo("/track/2868828162"))

        // The fixture keeps its real token, so this reads the expiry out of the actual wire format
        // rather than a URL invented for the test.
        val url = track.streamUrl!!
        assertEquals(1_789_942_732_000L, StreamLink.expiresAtMillis(url))
        assertTrue(StreamLink.isExpired(url, 1_789_942_732_000L + 1))
    }

    fun testATrackTheApiDoesNotKnowIsAnErrorNotAnEmptyTrack() {
        fetcher.route("/track/1", "error_parameter.json")
        // The error body wins over "no data", so the message is Deezer's own.
        val error = assertFailsWith<ApiError> { provider.track("1") }
        assertContains(error.message ?: "", "wrong parameter")
    }

    fun testSearchTracksMapsEveryField() {
        val page = provider.searchTracks("daft punk", limit = 2)

        assertEquals(183, page.total)
        assertEquals("2", page.nextCursor)
        assertEquals(2, page.items.size)

        val starboy = page.items[0]
        assertEquals("136889400", starboy.id)
        assertEquals("Starboy", starboy.title)
        assertEquals("The Weeknd", starboy.artist.name)
        assertEquals("4050205", starboy.artist.id)
        assertEquals("The Weeknd", starboy.artistName)
        assertEquals("Starboy", starboy.album?.title)
        assertEquals("14652356", starboy.album?.id)
        assertEquals(230, starboy.durationSeconds)
        assertEquals("3:50", starboy.durationLabel)
        assertTrue(starboy.explicit)
        assertEquals(975897L, starboy.popularity)
        assertNotNull(starboy.streamUrl)
        assertContains(starboy.streamUrl!!, ".mp3")
        // The UI asks for the 250px artwork variant.
        assertContains(starboy.artist.imageUrl!!, "250x250")
        assertContains(starboy.album!!.imageUrl!!, "250x250")

        val crush = page.items[1]
        assertEquals("Instant Crush (feat. Julian Casablancas)", crush.title)
        assertEquals("Daft Punk", crush.artist.name)
        assertEquals("Random Access Memories", crush.album?.title)
    }

    fun testSearchPutsTheCursorInTheIndexParameter() {
        provider.searchTracks("daft punk", limit = 2)
        val secondPage = provider.searchTracks("daft punk", cursor = "2", limit = 2)

        assertContains(fetcher.lastRequest(), "index=2")
        assertEquals("3135553", secondPage.items[0].id)
        assertEquals("4", secondPage.nextCursor)
    }

    fun testAlbumDetailCarriesMetadataAndInlineTracks() {
        val (album, tracks) = provider.albumDetail("302127")

        assertEquals("Discovery", album.title)
        assertEquals("Daft Punk", album.artistName)
        assertEquals(14, album.trackCount)
        assertEquals("2001-03-07", album.releaseDate)
        assertEquals(4, tracks.size)
        assertEquals("One More Time", tracks[0].title)
        assertEquals("Daft Punk", tracks[0].artist.name)
        assertEquals("Discovery", tracks[0].album?.title)
        assertEquals(320, tracks[0].durationSeconds)
    }

    fun testAlbumFallsBackToTheTracksEndpointWhenTracksAreNotInlined() {
        val (album, tracks) = provider.albumDetail("6575789")

        assertEquals("Discovery", album.title)
        assertEquals(4, tracks.size)
        assertEquals(1, fetcher.requestsTo("/album/6575789"))
        assertEquals(1, fetcher.requestsTo("/album/6575789/tracks"))
    }

    fun testArtistTopTracks() {
        val tracks = provider.artistTopTracks("27", limit = 3)
        assertEquals(3, tracks.size)
        assertEquals(listOf("One More Time", "Instant Crush (feat. Julian Casablancas)", "Harder, Better, Faster, Stronger"),
            tracks.map { it.title })
        assertEquals("Discovery", tracks[0].album?.title)
    }

    fun testArtistDetail() {
        val artist = provider.artistDetail("27")
        assertEquals("27", artist.id)
        assertEquals("Daft Punk", artist.name)
        assertContains(artist.imageUrl!!, "250x250")
    }

    fun testGenresKeepTheAllEntry() {
        val genres = provider.genres()
        assertEquals(listOf("All", "Pop", "Rap/Hip Hop", "Electro"), genres.map { it.name })
    }

    fun testHomeFeedDropsTheNonBrowsableAllGenre() {
        val feed = provider.homeFeed(limit = 3)
        assertEquals(3, feed.trendingTracks.size)
        assertEquals(1, feed.newAlbums.size)
        assertEquals(listOf("Pop", "Rap/Hip Hop", "Electro"), feed.genres.map { it.name })
    }

    fun testRadioStartsFromASeedTrack() {
        val radio = provider.radio("3135553", limit = 3)
        assertEquals(listOf("3135554", "3135555", "67238732"), radio.map { it.id })
    }

    fun testPlaylistDetail() {
        val playlist = provider.playlistDetail("908622995")
        assertEquals("Daft Punk Essentials", playlist.name)
        assertEquals("Best of the robots", playlist.description)
        assertEquals(3, playlist.tracks.size)
        assertEquals(883, playlist.totalSeconds)
        assertEquals("14:43", playlist.durationLabel)
    }

    fun testTracksWithoutAPreviewHaveNoStreamUrl() {
        val unplayable = DeezerProvider(FixtureHttpFetcher().route("/chart/0/tracks", "unplayable.json"))
        val tracks = unplayable.chartTracks()
        assertEquals(2, tracks.size)
        assertTrue(tracks.all { it.streamUrl == null })
        // Everything else still maps, so the UI can show the row and disable the play button.
        assertEquals("Aerodynamic", tracks[0].title)
    }

    fun testAnApiErrorBodyBecomesAnApiError() {
        val failing = DeezerProvider(FixtureHttpFetcher().route("/search", "error_parameter.json"))
        val error = assertFailsWith<ApiError> { failing.searchTracks("nope") }
        assertContains(error.message!!, "ParameterException")
        assertContains(error.message!!, "wrong parameter")
    }

    fun testAnUnreadableBodyBecomesAnApiError() {
        val failing = DeezerProvider(FixtureHttpFetcher().route("/search", "malformed.json"))
        val error = assertFailsWith<ApiError> { failing.searchTracks("nope") }
        assertContains(error.message!!, "Unreadable response")
        assertContains(error.message!!, "/search")
    }

    fun testAnHttpFailureCarriesTheStatusCode() {
        val failing = DeezerProvider(FixtureHttpFetcher().also { it.statusCodeOverride = 503 })
        val error = assertFailsWith<ApiError> { failing.chartTracks() }
        assertEquals(503, error.statusCode)
    }

    fun testAnUnknownAlbumIsAnErrorNotACrash() {
        val failing = DeezerProvider(FixtureHttpFetcher().route("/album/1", "malformed.json"))
        assertNull(runCatching { failing.albumDetail("1") }.getOrNull())
    }
}
