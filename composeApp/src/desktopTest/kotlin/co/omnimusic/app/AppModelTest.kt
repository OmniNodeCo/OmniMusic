package co.omnimusic.app

import co.omnimusic.core.AppEnvironment
import co.omnimusic.core.InlineExecutor
import co.omnimusic.core.audio.AudioOutput
import co.omnimusic.core.audio.AudioSource
import co.omnimusic.core.audio.AudioSourceException
import co.omnimusic.core.lyrics.LyricLine
import co.omnimusic.core.lyrics.Lyrics
import co.omnimusic.core.lyrics.LyricsLibrary
import co.omnimusic.core.lyrics.LyricsProvider
import co.omnimusic.core.model.Album
import co.omnimusic.core.model.Artist
import co.omnimusic.core.model.Genre
import co.omnimusic.core.model.HomeFeed
import co.omnimusic.core.model.Load
import co.omnimusic.core.model.Page
import co.omnimusic.core.model.Playlist
import co.omnimusic.core.model.Track
import co.omnimusic.core.net.ApiError
import co.omnimusic.core.player.PersistedPlayback
import co.omnimusic.core.player.PlaybackEngine
import co.omnimusic.core.player.RepeatMode
import co.omnimusic.core.provider.MusicProvider
import co.omnimusic.core.repo.MusicRepository
import co.omnimusic.core.store.KeyValueStore
import co.omnimusic.core.store.ListeningHistory
import co.omnimusic.core.store.PlaybackStateStore
import co.omnimusic.core.store.PlaylistStore
import co.omnimusic.core.util.NoGuard
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the state holder the whole UI reads from.
 *
 * These run on a plain JVM against the compile-only Compose stubs in `tools/stubs`, which is why
 * they can exist at all: the stub `mutableStateOf` is a real mutable field, so an [AppModel] can be
 * constructed and driven without a composition. Everything it talks to is faked here, so the tests
 * assert what the model *decides* — which screen, which state it publishes, what it persists — not
 * how Compose renders it. The catalog and lyric fakes are in this file rather than in `shared`'s
 * test source sets so this module only ever depends on `:shared`'s main sources.
 */
class AppModelTest {

    // ----------------------------------------------------------------------------------------
    // Harness
    // ----------------------------------------------------------------------------------------

    private val trackOne = track("t1", "One More Time", "Daft Punk")
    private val trackTwo = track("t2", "Starboy", "The Weeknd")
    private val catalog = FakeCatalog(listOf(trackOne, trackTwo))
    private val lyricSource = FakeLyrics()
    private val audio = FakeAudio()
    private val store = MapKeyValueStore()
    private val playlists = PlaylistStore(store)
    private val history = ListeningHistory(store)
    private val playbackState = PlaybackStateStore(store)
    private val engine = PlaybackEngine(audio, guard = NoGuard)

    private fun model(seedPersisted: PersistedPlayback? = null): AppModel {
        seedPersisted?.let(playbackState::save)
        return AppModel(
            AppEnvironment(
                repository = MusicRepository(catalog),
                lyrics = LyricsLibrary(lyricSource),
                playlists = playlists,
                history = history,
                playbackState = playbackState,
                engine = engine,
                executor = InlineExecutor,
                guard = NoGuard,
            )
        ).also { it.currentTimeMillis = { 1_700_000_000_000L } }
    }

    // ----------------------------------------------------------------------------------------
    // Startup and navigation
    // ----------------------------------------------------------------------------------------

    fun testStartupLoadsTheHomeFeedAndPlaylists() {
        val model = model()
        assertIs<Screen.Home>(model.screen)
        val home = assertIs<Load.Success<HomeFeed>>(model.home)
        assertEquals(listOf("One More Time", "Starboy"), home.value.trendingTracks.map { it.title })
        assertEquals(1, catalog.homeFeedCalls)
        assertTrue(model.playlists.isEmpty())
    }

    fun testStartupReportsAHomeFeedFailureInsteadOfThrowing() {
        catalog.failHomeWith = "backend exploded"
        val model = model()
        val failure = assertIs<Load.Failure>(model.home)
        assertContains(failure.error, "backend exploded")
    }

    fun testExistingPlaylistsAreLoadedOnStartup() {
        playlists.create("Road Trip")
        val model = model()
        assertEquals(listOf("Road Trip"), model.playlists.map { it.name })
    }

    fun testNavigationSwitchesScreens() {
        val model = model()
        model.goSearch()
        assertIs<Screen.Search>(model.screen)
        model.goLibrary()
        assertIs<Screen.Library>(model.screen)
        model.goHome()
        assertIs<Screen.Home>(model.screen)
    }

    fun testOpenAlbumLoadsAlbumAndTracksAndClearsStaleData() {
        val model = model()
        model.openAlbum("a1", "Discovery")
        val screen = assertIs<Screen.Album>(model.screen)
        assertEquals("a1", screen.id)
        val loaded = assertIs<Load.Success<Pair<Album, List<Track>>>>(model.album)
        assertEquals("Discovery", loaded.value.first.title)
        assertEquals(2, loaded.value.second.size)
    }

    fun testAlbumFailureIsPublishedNotThrown() {
        catalog.failAlbumWith = "no such album"
        val model = model()
        model.openAlbum("a1", "Discovery")
        assertContains((model.album as Load.Failure).error, "no such album")
    }

    fun testOpenArtistLoadsTheArtistAndTopTracks() {
        val model = model()
        model.openArtist("ar1", "Daft Punk")
        assertIs<Screen.Artist>(model.screen)
        val loaded = assertIs<Load.Success<Pair<Artist, List<Track>>>>(model.artist)
        assertEquals("Daft Punk", loaded.value.first.name)
        assertEquals(2, loaded.value.second.size)
    }

    fun testOpenArtistWithoutAnIdWarnsInsteadOfNavigating() {
        val model = model()
        model.openArtist("", "Nobody")
        assertIs<Screen.Home>(model.screen)
        assertContains(model.notice.orEmpty(), "no artist id")
    }

    // ----------------------------------------------------------------------------------------
    // Search
    // ----------------------------------------------------------------------------------------

    fun testSearchPublishesResultsAndRemembersTheQuery() {
        val model = model()
        model.runSearch("starboy")
        assertEquals("starboy", model.searchQuery)
        val results = assertIs<Load.Success<co.omnimusic.core.model.SearchResult>>(model.searchResults)
        assertEquals(listOf("Starboy"), results.value.tracks.items.map { it.title })
    }

    fun testBlankSearchClearsResultsWithoutCallingTheProvider() {
        val model = model()
        model.runSearch("starboy")
        catalog.searchCalls = 0
        model.runSearch("   ")
        assertNull(model.searchResults)
        assertEquals(0, catalog.searchCalls)
    }

    fun testSearchForAlsoLeavesTheCurrentScreen() {
        val model = model()
        assertIs<Screen.Home>(model.screen)
        model.searchFor("Pop")
        assertIs<Screen.Search>(model.screen)
        assertEquals("Pop", model.searchQuery)
        assertIs<Load.Success<*>>(model.searchResults)
    }

    fun testReopeningSearchReRunsTheLastQuery() {
        val model = model()
        model.searchQuery = "starboy"
        assertNull(model.searchResults)
        model.goSearch()
        assertIs<Load.Success<*>>(model.searchResults)
        assertEquals(1, catalog.searchCalls)
    }

    // ----------------------------------------------------------------------------------------
    // Playlists
    // ----------------------------------------------------------------------------------------

    fun testCreatePlaylistRefreshesTheList() {
        val model = model()
        model.createPlaylist("Focus")
        assertEquals(listOf("Focus"), model.playlists.map { it.name })
    }

    fun testAddToPlaylistUpdatesThePlaylistAndAnnouncesIt() {
        val model = model()
        model.createPlaylist("Focus")
        val id = model.playlists.single().id
        model.addToPlaylist(id, trackOne)
        assertEquals(listOf("One More Time"), model.playlists.single().tracks.map { it.title })
        assertContains(model.notice.orEmpty(), "Added to Focus")
    }

    fun testSubmitNewPlaylistTrimsTheNameAndClearsTheField() {
        val model = model()
        model.newPlaylistName = "  Road Trip  "
        model.submitNewPlaylist()
        assertEquals(listOf("Road Trip"), model.playlists.map { it.name })
        assertEquals("", model.newPlaylistName)
        assertContains(model.notice.orEmpty(), "Created “Road Trip”")
    }

    fun testSubmitNewPlaylistIgnoresBlankInput() {
        val model = model()
        model.newPlaylistName = "   "
        model.submitNewPlaylist()
        assertTrue(model.playlists.isEmpty())
        assertNull(model.notice)
    }

    fun testAddCurrentTrackToPlaylistUsesThePlayingTrack() {
        val model = model()
        model.createPlaylist("Focus")
        model.play(listOf(trackTwo))
        model.addCurrentTrackToPlaylist(model.playlists.single().id)
        assertEquals(listOf("Starboy"), model.playlists.single().tracks.map { it.title })
    }

    fun testAddToPlaylistWithoutAnythingPlayingWarns() {
        val model = model()
        model.createPlaylist("Focus")
        model.addCurrentTrackToPlaylist(model.playlists.single().id)
        assertContains(model.notice.orEmpty(), "Nothing is playing")
        assertTrue(model.playlists.single().tracks.isEmpty())
    }

    fun testDeletePlaylistRemovesItFromTheList() {
        val model = model()
        model.createPlaylist("Focus")
        model.deletePlaylist(model.playlists.single().id)
        assertTrue(model.playlists.isEmpty())
    }

    fun testSaveAsPlaylistCreatesAFilledPlaylist() {
        val model = model()
        model.saveAsPlaylist("Discovery", listOf(trackOne, trackTwo))
        val saved = model.playlists.single()
        assertEquals("Discovery", saved.name)
        assertEquals(2, saved.tracks.size)
        assertContains(model.notice.orEmpty(), "Saved “Discovery”")
    }

    fun testFilterMatchesPlaylistNameAndContents() {
        val model = model()
        model.saveAsPlaylist("Road Trip", listOf(trackTwo))
        model.createPlaylist("Focus")

        model.libraryFilter = "road"
        assertEquals(listOf("Road Trip"), model.filteredPlaylists().map { it.name })

        model.libraryFilter = "starboy"
        assertEquals(listOf("Road Trip"), model.filteredPlaylists().map { it.name })

        model.libraryFilter = "focus"
        assertEquals(listOf("Focus"), model.filteredPlaylists().map { it.name })

        model.libraryFilter = "zzzz"
        assertTrue(model.filteredPlaylists().isEmpty())

        model.libraryFilter = ""
        assertEquals(2, model.filteredPlaylists().size)
    }

    // ----------------------------------------------------------------------------------------
    // Playback
    // ----------------------------------------------------------------------------------------

    fun testPlayPublishesTheEngineStateThroughTheListener() {
        val model = model()
        assertTrue(model.playState.isEmpty)
        model.play(listOf(trackOne, trackTwo), startIndex = 1)
        assertEquals("Starboy", model.playState.track?.title)
        assertEquals(1, model.playState.queueIndex)
        assertEquals(2, model.playState.queueSize)
        assertTrue(model.playState.isPlaying)
        assertEquals(listOf("One More Time", "Starboy"), model.queue().map { it.title })
    }

    fun testPlayPlaylistStartsAtItsFirstTrack() {
        val model = model()
        model.saveAsPlaylist("Mix", listOf(trackTwo, trackOne))
        model.playPlaylist(model.playlists.single())
        assertEquals("Starboy", model.playState.track?.title)
    }

    fun testAnUnplayableTrackIsReportedAsANotice() {
        audio.failNextPrepareWith = "no decoder for this stream"
        val model = model()
        model.play(listOf(trackOne))
        val notice = assertNotNull(model.notice)
        assertContains(notice, "One More Time")
        assertContains(notice, "no decoder for this stream")
        assertTrue(model.playState.track == null || !model.playState.isPlaying)
    }

    fun testTransportControlsDelegateToTheEngineAndPersist() {
        val model = model()
        model.play(listOf(trackOne, trackTwo))

        model.tick(1_500)
        assertEquals(1_500L, model.playState.positionMillis)

        model.next()
        assertEquals("Starboy", model.playState.track?.title)

        model.toggleShuffle()
        assertTrue(model.playState.shuffleEnabled)

        assertEquals(RepeatMode.ALL, model.cycleRepeat())

        model.setVolume(0.4f)
        assertEquals(0.4f, model.playState.volume)

        val restored = assertNotNull(playbackState.restore())
        assertEquals(2, restored.tracks.size)
    }

    fun testTrackStartIsRecordedInHistory() {
        val model = model()
        model.play(listOf(trackOne))
        val entry = history.recent(5).single()
        assertEquals("One More Time", entry.title)
        assertEquals(1, entry.plays)
        assertEquals(1_700_000_000_000L, entry.lastPlayedAt)
    }

    fun testRemoveFromQueueDropsTheEntryAndRepersists() {
        val model = model()
        model.play(listOf(trackOne, trackTwo))
        assertTrue(model.removeFromQueue(1))
        assertEquals(listOf("One More Time"), model.queue().map { it.title })
        assertEquals(1, playbackState.restore()?.tracks?.size)
    }

    fun testRemoveFromQueueRejectsAnOutOfRangeIndex() {
        val model = model()
        model.play(listOf(trackOne))
        assertFalse(model.removeFromQueue(7))
        assertEquals(1, model.queue().size)
    }

    fun testPausingPersistsThePositionForTheNextLaunch() {
        val model = model()
        model.play(listOf(trackOne, trackTwo))
        model.tick(42_000)
        model.togglePlayPause()
        val saved = assertNotNull(playbackState.restore())
        assertEquals(42_000L, saved.positionMillis)
        assertTrue(saved.currentTrack?.id == "t1")
    }

    fun testDismissNoticeClearsIt() {
        catalog.failHomeWith = "backend exploded"
        val model = model()
        model.openArtist("", "Nobody")
        assertNotNull(model.notice)
        model.dismissNotice()
        assertNull(model.notice)
    }

    // ----------------------------------------------------------------------------------------
    // History
    // ----------------------------------------------------------------------------------------

    fun testHistoryTabShowsRecentPlaysNewestFirst() {
        val model = model()
        model.goHistory()
        assertIs<Screen.History>(model.screen)
        assertTrue(model.historyRecent.isEmpty())

        model.play(listOf(trackOne, trackTwo))
        model.next()
        model.goHome()
        model.goHistory()
        assertEquals(listOf("Starboy", "One More Time"), model.historyRecent.map { it.title })
        assertEquals(1, model.historyRecent.first().plays)
    }

    fun testHistoryListRefreshesWhilePlaying() {
        val model = model()
        model.goHistory()
        model.play(listOf(trackOne, trackTwo))
        // No re-navigation: the list has to follow along, or the tab goes stale on screen.
        assertEquals(listOf("One More Time"), model.historyRecent.map { it.title })
    }

    fun testPlayCountsAccumulateInHistory() {
        val model = model()
        model.play(listOf(trackOne))
        model.play(listOf(trackOne))
        model.goHistory()
        assertEquals(2, model.historyRecent.single().plays)
    }

    fun testClearHistoryEmptiesTheListAndAnnouncesIt() {
        val model = model()
        model.play(listOf(trackOne))
        model.goHistory()
        assertEquals(1, model.historyRecent.size)
        model.clearHistory()
        assertTrue(model.historyRecent.isEmpty())
        assertContains(model.notice.orEmpty(), "history cleared")
        // The store is the source of truth, not just the snapshot in memory.
        assertTrue(history.recent(10).isEmpty())
    }

    fun testRestoringASessionDoesNotAddAHistoryEntry() {
        val model = model(
            seedPersisted = PersistedPlayback(
                tracks = listOf(trackOne, trackTwo),
                index = 0,
                positionMillis = 1_000,
                shuffleEnabled = false,
                repeatMode = RepeatMode.OFF,
                volume = 1f,
            )
        )
        model.goHistory()
        assertTrue(model.historyRecent.isEmpty())
    }

    // ----------------------------------------------------------------------------------------
    // Session restore
    // ----------------------------------------------------------------------------------------

    fun testSessionRestoreResumesPausedAtTheSavedPosition() {
        val model = model(
            seedPersisted = PersistedPlayback(
                tracks = listOf(trackOne, trackTwo),
                index = 1,
                positionMillis = 90_000,
                shuffleEnabled = true,
                repeatMode = RepeatMode.ALL,
                volume = 0.35f,
            )
        )
        assertEquals("Starboy", model.playState.track?.title)
        assertEquals(90_000L, model.playState.positionMillis)
        assertEquals(false, model.playState.isPlaying)
        assertEquals(true, model.playState.shuffleEnabled)
        assertEquals(RepeatMode.ALL, model.playState.repeatMode)
        assertEquals(0.35f, model.playState.volume)
        // Restoring must not count as a play.
        assertTrue(history.recent(5).isEmpty())
    }

    fun testRestoreLeavesTheSavedPositionIntactForTheNextLaunch() {
        model(
            seedPersisted = PersistedPlayback(
                tracks = listOf(trackOne, trackTwo),
                index = 1,
                positionMillis = 90_000,
                shuffleEnabled = false,
                repeatMode = RepeatMode.OFF,
                volume = 1f,
            )
        )
        // The engine loads the restored track at 0 ms and only then seeks; persisting during that
        // window would replace 1:30 with 0:00 and lose the position on every restart.
        assertEquals(90_000L, playbackState.restore()?.positionMillis)
    }

    fun testRestoreWithoutASavedSessionLeavesTheEngineIdle() {
        val model = model()
        assertTrue(model.playState.isEmpty)
        assertNull(playbackState.restore())
    }

    // ----------------------------------------------------------------------------------------
    // Lyrics
    // ----------------------------------------------------------------------------------------

    fun testLyricsAreLoadedForTheTrackThatStarted() {
        val model = model()
        model.play(listOf(trackOne))
        val lyrics = assertNotNull(model.lyrics)
        assertEquals("One More Time", lyrics.trackName)
        assertTrue(model.hasSyncedLyrics())
        assertEquals(listOf("One More Time"), lyricSource.requestedTracks)
    }

    fun testLyricIndexFollowsThePlayhead() {
        val model = model()
        model.play(listOf(trackOne))
        assertEquals(-1, model.currentLyricIndex())
        model.seekTo(65_000)
        assertEquals(1, model.currentLyricIndex())
        model.seekTo(10_000)
        assertEquals(0, model.currentLyricIndex())
    }

    fun testMissingLyricsAreNotAnError() {
        lyricSource.missingTitles += "Starboy"
        val model = model()
        model.play(listOf(trackTwo))
        assertNull(model.lyrics)
        assertNull(model.notice)
        assertFalse(model.hasSyncedLyrics())
    }

    fun testLyricsFailureIsSwallowedToAvoidInterruptingPlayback() {
        lyricSource.failWith = "lyrics service down"
        val model = model()
        model.play(listOf(trackOne))
        assertNull(model.lyrics)
        assertNull(model.notice)
    }

    // ----------------------------------------------------------------------------------------
    // Radio and copy
    // ----------------------------------------------------------------------------------------

    fun testRadioReplacesTheQueue() {
        val model = model()
        model.play(listOf(trackOne))
        catalog.radioResult = listOf(trackTwo, trackOne)
        model.startRadio("t1")
        assertEquals(listOf("Starboy", "One More Time"), model.queue().map { it.title })
        assertContains(model.notice.orEmpty(), "Radio started")
    }

    fun testRadioFailureIsReportedAsANotice() {
        catalog.failRadioWith = "radio unavailable"
        val model = model()
        model.startRadio("t1")
        assertContains(model.notice.orEmpty(), "Radio unavailable")
        assertContains(model.notice.orEmpty(), "radio unavailable")
    }

    fun testPlaybackSourceNameComesFromTheProvider() {
        assertEquals("Fake Catalog", model().playbackSourceName())
    }
}

// ----------------------------------------------------------------------------------------
// Fakes
// ----------------------------------------------------------------------------------------

private fun track(id: String, title: String, artist: String): Track = Track(
    id = id,
    title = title,
    artist = Artist(id = "ar$id", name = artist),
    album = Album(id = "al$id", title = "$title — the album", artistName = artist),
    durationSeconds = 240,
    streamUrl = "https://example.test/$id.mp3",
)

private class FakeCatalog(private val tracks: List<Track>) : MusicProvider {

    var homeFeedCalls = 0
    var searchCalls = 0
    var radioCalls = 0
    var failHomeWith: String? = null
    var failAlbumWith: String? = null
    var failRadioWith: String? = null
    var radioResult: List<Track> = emptyList()

    override val id: String = "fake"
    override val displayName: String = "Fake Catalog"

    override fun searchTracks(query: String, cursor: String?, limit: Int): Page<Track> {
        searchCalls++
        return Page(tracks.filter { it.title.contains(query, ignoreCase = true) })
    }

    override fun searchAlbums(query: String, cursor: String?, limit: Int): Page<Album> =
        Page(tracks.mapNotNull { it.album }.distinctBy { it.id })

    override fun searchArtists(query: String, cursor: String?, limit: Int): Page<Artist> =
        Page(tracks.map { it.artist }.distinctBy { it.id })

    override fun homeFeed(limit: Int): HomeFeed {
        homeFeedCalls++
        failHomeWith?.let { throw ApiError(it) }
        return HomeFeed(
            trendingTracks = tracks,
            newAlbums = tracks.mapNotNull { it.album },
            genres = listOf(Genre(id = "g1", name = "Pop")),
        )
    }

    override fun chartTracks(limit: Int): List<Track> = tracks

    override fun genres(): List<Genre> = listOf(Genre(id = "g1", name = "Pop"))

    override fun albumDetail(albumId: String): Pair<Album, List<Track>> {
        failAlbumWith?.let { throw ApiError(it) }
        return Album(id = albumId, title = "Discovery", artistName = "Daft Punk") to tracks
    }

    override fun artistDetail(artistId: String): Artist = tracks.first().artist

    override fun artistTopTracks(artistId: String, limit: Int): List<Track> = tracks

    override fun radio(seedTrackId: String, limit: Int): List<Track> {
        radioCalls++
        failRadioWith?.let { throw ApiError(it) }
        return radioResult
    }

    override fun playlistDetail(playlistId: String): Playlist = Playlist(id = playlistId, name = "Remote")
}

private class FakeLyrics : LyricsProvider {

    val requestedTracks = mutableListOf<String>()
    val missingTitles = mutableSetOf<String>()
    var failWith: String? = null

    override val id: String = "fake-lyrics"

    override fun fetch(artist: String, track: String, album: String?, durationSeconds: Int?): Lyrics? {
        failWith?.let { throw ApiError(it) }
        if (track in missingTitles) return null
        requestedTracks += track
        return Lyrics(
            trackName = track,
            artistName = artist,
            albumName = album,
            durationMillis = 240_000,
            lines = listOf(
                // First line at 5 s, not 0, so the intro (-1) is a distinguishable case.
                LyricLine(startMillis = 5_000, text = "first line"),
                LyricLine(startMillis = 60_000, text = "second line"),
            ),
        )
    }
}

private class FakeAudio : AudioOutput {

    private var ready = false
    private var playing = false
    var failNextPrepareWith: String? = null

    override fun prepare(source: AudioSource) {
        failNextPrepareWith?.let { message ->
            failNextPrepareWith = null
            throw AudioSourceException(message)
        }
        ready = true
        playing = false
    }

    override fun play() {
        playing = true
    }

    override fun pause() {
        playing = false
    }

    override fun stop() {
        ready = false
        playing = false
    }

    override fun seekTo(positionMillis: Long) = Unit

    /** Always -1, so the engine accumulates [PlaybackEngine.advance] ticks deterministically. */
    override fun positionMillis(): Long = -1L

    override fun setVolume(volume: Float) = Unit

    override fun isReady(): Boolean = ready
}

private class MapKeyValueStore : KeyValueStore {

    private val values = LinkedHashMap<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun keys(prefix: String): Set<String> = values.keys.filter { it.startsWith(prefix) }.toSet()
}
