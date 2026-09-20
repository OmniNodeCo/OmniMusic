package co.omnimusic.core.repo

import co.omnimusic.core.model.Load
import co.omnimusic.core.model.Track
import co.omnimusic.core.testing.testTrack
import co.omnimusic.core.provider.deezer.DeezerProvider
import co.omnimusic.core.testing.FixtureHttpFetcher
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MusicRepositoryTest {

    private val fetcher = FixtureHttpFetcher.fullyRouted()
    private val repository = MusicRepository(DeezerProvider(fetcher))

    fun testSearchAggregatesEveryFacet() {
        val result = assertIs<Load.Success<*>>(repository.search("daft punk"))
        val search = result.value as co.omnimusic.core.model.SearchResult
        assertEquals("daft punk", search.query)
        assertEquals(2, search.tracks.items.size)
        assertEquals(2, search.albums.items.size)
        assertEquals(2, search.artists.items.size)
        assertEquals("Starboy", search.tracks.items[0].title)
        assertEquals("Discovery", search.albums.items[0].title)
        assertEquals("Daft Punk", search.artists.items[0].name)
        assertEquals(3, fetcher.requested.size)
    }

    fun testBlankSearchShortCircuitsWithoutNetworkTraffic() {
        val result = assertIs<Load.Success<*>>(repository.search("   "))
        assertTrue((result.value as co.omnimusic.core.model.SearchResult).isEmpty)
        assertTrue(fetcher.requested.isEmpty())
    }

    fun testOneFailingFacetDoesNotSinkTheWholeScreen() {
        fetcher.fail("/search/album")
        val result = assertIs<Load.Success<*>>(repository.search("daft punk"))
        val search = result.value as co.omnimusic.core.model.SearchResult
        assertEquals(2, search.tracks.items.size)
        assertTrue(search.albums.isEmpty)
        assertEquals(2, search.artists.items.size)
    }

    fun testFreshTrackBypassesTheCacheBecauseANewLinkIsTheWholePoint() {
        val track = testTrack("2868828162")

        val first = assertIs<Load.Success<*>>(repository.freshTrack(track))
        val second = assertIs<Load.Success<*>>(repository.freshTrack(track))

        assertEquals("NEVER GET USED TO THIS (feat. JVKE)", (first.value as Track).title)
        assertEquals("2868828162", (second.value as Track).id)
        // A cached answer would hand back the expired link we asked to be rid of.
        assertEquals(2, fetcher.requestsTo("/track/2868828162"))
    }

    fun testAFailedRefreshComesBackAsAFailureTheCallerCanReport() {
        fetcher.fail("/track/2868828162")
        val result = assertIs<Load.Failure>(repository.freshTrack(testTrack("2868828162")))
        assertTrue(result.error.isNotBlank())
    }

    fun testResultsAreCachedForRepeatNavigation() {
        repository.chart()
        repository.chart()
        repository.chart()
        assertEquals(1, fetcher.requestsTo("/chart/0/tracks"))
    }

    fun testInvalidateForcesAFreshFetch() {
        repository.chart()
        repository.invalidate()
        repository.chart()
        assertEquals(2, fetcher.requestsTo("/chart/0/tracks"))
    }

    fun testRadioIsDeliberatelyNeverCached() {
        repository.radio("3135553")
        repository.radio("3135553")
        assertEquals(2, fetcher.requestsTo("/track/3135553/radio"))
    }

    fun testAlbumAndArtistAreCached() {
        val album = assertIs<Load.Success<*>>(repository.album("302127"))
        @Suppress("UNCHECKED_CAST")
        val (metadata, tracks) = album.value as Pair<co.omnimusic.core.model.Album, List<co.omnimusic.core.model.Track>>
        assertEquals("Discovery", metadata.title)
        assertEquals(4, tracks.size)

        val artist = assertIs<Load.Success<*>>(repository.artist("27"))
        @Suppress("UNCHECKED_CAST")
        val (who, top) = artist.value as Pair<co.omnimusic.core.model.Artist, List<co.omnimusic.core.model.Track>>
        assertEquals("Daft Punk", who.name)
        assertEquals(3, top.size)

        repository.album("302127")
        repository.artist("27")
        assertEquals(1, fetcher.requestsTo("/album/302127"))
        assertEquals(1, fetcher.requestsTo("/artist/27"))
    }

    fun testOutagesAreReportedAsFailuresNotExceptions() {
        fetcher.statusCodeOverride = 500
        val result = assertIs<Load.Failure>(repository.chart())
        assertContains(result.error, "500")
    }

    fun testHomeFeedIsAssembledFromThreeEndpoints() {
        val result = assertIs<Load.Success<*>>(repository.homeFeed(limit = 3))
        val feed = result.value as co.omnimusic.core.model.HomeFeed
        assertEquals(3, feed.trendingTracks.size)
        assertEquals(1, feed.newAlbums.size)
        assertEquals(3, feed.genres.size)
        assertEquals(3, fetcher.requested.size)
    }

    fun testSearchPaginationIsNotCached() {
        val first = assertIs<Load.Success<*>>(repository.searchTracks("daft punk", limit = 2))
        @Suppress("UNCHECKED_CAST")
        val page = first.value as co.omnimusic.core.model.Page<co.omnimusic.core.model.Track>
        assertEquals("2", page.nextCursor)
        assertTrue(page.hasMore)

        val second = assertIs<Load.Success<*>>(repository.searchTracks("daft punk", cursor = "2", limit = 2))
        @Suppress("UNCHECKED_CAST")
        val nextPage = second.value as co.omnimusic.core.model.Page<co.omnimusic.core.model.Track>
        assertEquals("3135553", nextPage.items[0].id)
        assertEquals(2, fetcher.requestsTo("/search"))
    }

    fun testGenresAndPlaylist() {
        val genres = assertIs<Load.Success<*>>(repository.genres())
        assertEquals(4, (genres.value as List<*>).size)

        val playlist = assertIs<Load.Success<*>>(repository.playlist("908622995"))
        assertEquals("Daft Punk Essentials", (playlist.value as co.omnimusic.core.model.Playlist).name)
    }

    fun testProviderIsExposedToTheUi() {
        assertEquals("deezer", repository.providerId)
        assertEquals("Deezer", repository.providerName)
    }
}
