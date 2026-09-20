package co.omnimusic.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.omnimusic.core.AppEnvironment
import co.omnimusic.core.lyrics.Lyrics
import co.omnimusic.core.model.*
import co.omnimusic.core.store.ListeningHistory
import co.omnimusic.core.util.LocalFilter
import co.omnimusic.core.player.PlayState
import co.omnimusic.core.player.PlaybackListener

/** Where the app currently is. Hand-rolled instead of a navigation library to keep the graph visible. */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data object History : Screen
    data class Album(val id: String, val title: String) : Screen
    data class Artist(val id: String, val name: String) : Screen
}

/**
 * The things a keyboard or media remote can ask for.
 *
 * Deliberately not Compose `Key` values: the mapping from a physical key lives in the UI layer, so
 * this stays testable and a second input method (a TV remote, headset buttons) only has to produce
 * the same enum.
 */
enum class KeyAction {
    TOGGLE_PLAY_PAUSE,
    NEXT_TRACK,
    PREVIOUS_TRACK,
    SEEK_FORWARD,
    SEEK_BACKWARD,

    /** Escape / system back: close the topmost thing. */
    DISMISS,
}

/**
 * The one state holder the UI reads from.
 *
 * Repository calls are blocking, so they are dispatched on the environment's [co.omnimusic.core.Executor]
 * and published into Compose state when they land. Screens therefore only ever read `var`s.
 */
class AppModel(private val environment: AppEnvironment) {

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    var playState by mutableStateOf(environment.engine.state())
        private set

    var home by mutableStateOf<Load<HomeFeed>?>(null)
        private set

    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<Load<SearchResult>?>(null)
        private set

    /** Whether the current search has another page of tracks behind it. */
    var canLoadMoreResults by mutableStateOf(false)
        private set

    /** Opaque cursor for that next page, as handed back by the provider. */
    private var searchCursor: String? = null

    var album by mutableStateOf<Load<Pair<Album, List<Track>>>?>(null)
        private set

    var artist by mutableStateOf<Load<Pair<Artist, List<Track>>>?>(null)
        private set

    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set

    /** Lyrics for the current track, or null while loading / when there are none. */
    var lyrics by mutableStateOf<Lyrics?>(null)
        private set

    var libraryFilter by mutableStateOf("")

    /** Draft name in the library screen's "new playlist" field. */
    var newPlaylistName by mutableStateOf("")

    /**
     * Recently played entries, newest first.
     *
     * The history store is not observable, so this is a snapshot refreshed when the tab is opened
     * and whenever a track starts — otherwise the list would sit stale on screen while playing.
     */
    var historyRecent by mutableStateOf<List<ListeningHistory.Entry>>(emptyList())
        private set

    var nowPlayingExpanded by mutableStateOf(false)

    var notice by mutableStateOf<String?>(null)

    private val history = environment.history

    /** Screens the back action returns to, newest last. Bounded: a long browse should not grow it. */
    private val backStack = ArrayDeque<Screen>()

    /** Set while [restoreSession] is running, so a restored track is not counted as a play. */
    private var restoring = false

    /**
     * Wall-clock reads live in the platform layer; this indirection keeps AppModel testable.
     *
     * Declared above [init] on purpose: property initializers run in declaration order, and `init`
     * restores the previous session, which starts a track, which calls this. Declared any lower and
     * the field is still null during construction — the restore NPEs inside the engine, the engine
     * reports the track as unplayable and skips past it, and the app opens on the wrong song.
     */
    var currentTimeMillis: () -> Long = { 0L }

    init {
        environment.engine.listener = object : PlaybackListener {
            override fun onStateChanged(state: PlayState) {
                playState = state
            }

            override fun onTrackStarted(track: Track) {
                loadLyricsFor(track)
                // Loading the track a previous session left open is not the user pressing play: it
                // must not inflate its play count, reorder "recently played", or overwrite the
                // saved position with 0 before the seek lands.
                if (!restoring) {
                    history.recordPlay(track, playedAt = currentTimeMillis())
                    refreshHistory()
                    persistSession()
                }
            }

            override fun onTrackFailed(track: Track, reason: String) {
                notice = "Could not play “${track.title}”: $reason"
            }

            override fun onQueueFinished() {
                notice = "Queue finished"
            }
        }
        loadHome()
        refreshPlaylists()
        restoreSession()
    }

    // ----------------------------------------------------------------------------------------
    // Navigation
    // ----------------------------------------------------------------------------------------

    fun goHome() {
        screen = Screen.Home
        backStack.clear()
    }

    fun goSearch() {
        screen = Screen.Search
        backStack.clear()
        if (searchResults == null && searchQuery.isNotBlank()) runSearch(searchQuery)
    }

    fun goLibrary() {
        screen = Screen.Library
        backStack.clear()
        refreshPlaylists()
    }

    fun goHistory() {
        screen = Screen.History
        backStack.clear()
        refreshHistory()
    }

    fun openAlbum(id: String, title: String) {
        pushScreen()
        screen = Screen.Album(id, title)
        album = null
        background { album = environment.repository.album(id) }
    }

    fun openArtist(id: String, name: String) {
        if (id.isBlank()) {
            notice = "This entry has no artist id"
            return
        }
        pushScreen()
        screen = Screen.Artist(id, name)
        artist = null
        background { artist = environment.repository.artist(id) }
    }

    private fun pushScreen() {
        // Detail screens are the only ones worth going back to; tab switches reset the stack.
        if (backStack.size < MAX_BACK_DEPTH) backStack.addLast(screen)
    }

    /**
     * Closes the topmost thing: the now-playing panel, then the last detail screen.
     *
     * Returns false when there is nothing left to close, which is the caller's cue to let the
     * platform do its default (exit on Android). Without this the system back button leaves the app
     * from an album page, and desktop has no way out of the full-screen player but the mouse.
     */
    fun navigateBack(): Boolean {
        if (nowPlayingExpanded) {
            nowPlayingExpanded = false
            return true
        }
        val previous = backStack.removeLastOrNull() ?: return false
        screen = previous
        // Re-fetch rather than trust the old state: the repository caches, so this is normally free,
        // and a screen whose data was dropped would otherwise sit on its spinner forever.
        when (previous) {
            is Screen.Album -> background { album = environment.repository.album(previous.id) }
            is Screen.Artist -> background { artist = environment.repository.artist(previous.id) }
            Screen.Library -> refreshPlaylists()
            Screen.History -> refreshHistory()
            else -> Unit
        }
        return true
    }

    /** Keyboard, media keys and remote buttons, already translated out of platform key codes. */
    fun handleKeyAction(action: KeyAction): Boolean {
        if (action == KeyAction.DISMISS) return navigateBack()
        if (playState.isEmpty) return false
        return when (action) {
            KeyAction.TOGGLE_PLAY_PAUSE -> {
                togglePlayPause()
                true
            }

            KeyAction.NEXT_TRACK -> next()
            KeyAction.PREVIOUS_TRACK -> previous()
            KeyAction.SEEK_FORWARD -> {
                seekBy(SEEK_STEP_MILLIS)
                true
            }

            KeyAction.SEEK_BACKWARD -> {
                seekBy(-SEEK_STEP_MILLIS)
                true
            }

            KeyAction.DISMISS -> false
        }
    }

    // ----------------------------------------------------------------------------------------
    // Data
    // ----------------------------------------------------------------------------------------

    fun loadHome() {
        home = null
        background { home = environment.repository.homeFeed(limit = 10) }
    }

    fun runSearch(query: String) {
        searchQuery = query
        searchCursor = null
        canLoadMoreResults = false
        if (query.isBlank()) {
            searchResults = null
            return
        }
        background {
            val result = environment.repository.search(query, limit = SEARCH_PAGE_SIZE)
            searchResults = result
            searchCursor = (result as? Load.Success)?.value?.tracks?.nextCursor
            canLoadMoreResults = searchCursor != null
        }
    }

    /**
     * Appends the next page of tracks to the results on screen.
     *
     * A catalog search is paged server-side; without this the app shows the first twelve tracks and
     * silently pretends that is everything the service has.
     */
    fun loadMoreResults() {
        val cursor = searchCursor ?: return
        val query = searchQuery
        val current = (searchResults as? Load.Success)?.value ?: return
        background {
            when (val page = environment.repository.searchTracks(query, cursor = cursor, limit = SEARCH_PAGE_SIZE)) {
                is Load.Failure -> notice = "Could not load more results: ${page.error}"
                is Load.Success -> {
                    val merged = current.tracks.items + page.value.items
                    searchCursor = page.value.nextCursor
                    canLoadMoreResults = searchCursor != null
                    searchResults = Load.Success(
                        current.copy(
                            tracks = Page(
                                items = merged,
                                total = maxOf(current.tracks.total, merged.size),
                                nextCursor = page.value.nextCursor,
                            )
                        )
                    )
                }
            }
        }
    }

    /**
     * Search *and* show the results.
     *
     * [runSearch] alone only fills state, which is right for the search box (it is already on that
     * screen) and wrong for every other entry point: a genre chip on the home screen that calls it
     * appears to do nothing, because the app never leaves the screen it was on.
     */
    fun searchFor(query: String) {
        screen = Screen.Search
        runSearch(query)
    }

    fun startRadio(seedTrackId: String) {
        background {
            when (val result = environment.repository.radio(seedTrackId, limit = 20)) {
                is Load.Failure -> notice = "Radio unavailable: ${result.error}"
                is Load.Success -> {
                    environment.engine.playQueue(result.value)
                    notice = "Radio started from this track"
                }
            }
        }
    }

    fun createPlaylist(name: String) {
        environment.playlists.create(name)
        refreshPlaylists()
    }

    /**
     * Creates a playlist from the library screen's input field and clears it. Blank input is
     * ignored rather than producing a playlist called "".
     */
    fun submitNewPlaylist() {
        val name = newPlaylistName.trim()
        if (name.isEmpty()) return
        val created = environment.playlists.create(name)
        refreshPlaylists()
        newPlaylistName = ""
        notice = "Created “${created.name}”"
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        environment.playlists.addTrack(playlistId, track)
        refreshPlaylists()
        notice = "Added to ${environment.playlists.byId(playlistId)?.name ?: "playlist"}"
    }

    /** "Add to playlist" for whatever is on screen in the player, without making the UI pass a track. */
    fun addCurrentTrackToPlaylist(playlistId: String) {
        val track = playState.track
        if (track == null) {
            notice = "Nothing is playing"
            return
        }
        addToPlaylist(playlistId, track)
    }

    fun deletePlaylist(id: String) {
        environment.playlists.delete(id)
        refreshPlaylists()
    }

    private fun refreshPlaylists() {
        playlists = environment.playlists.all()
    }

    // ----------------------------------------------------------------------------------------
    // History
    // ----------------------------------------------------------------------------------------

    /** Re-reads the history store into observable state. */
    fun refreshHistory() {
        historyRecent = environment.history.recent(HISTORY_LIMIT)
    }

    fun clearHistory() {
        environment.history.clear()
        refreshHistory()
        notice = "Listening history cleared"
    }

    // ----------------------------------------------------------------------------------------
    // Transport
    // ----------------------------------------------------------------------------------------

    fun play(tracks: List<Track>, startIndex: Int = 0) {
        environment.engine.playQueue(tracks, startIndex)
    }

    fun playPlaylist(playlist: Playlist) {
        play(playlist.tracks)
    }

    fun togglePlayPause() {
        environment.engine.togglePlayPause()
        if (!environment.engine.isPlaying) persistSession()
    }

    fun next(): Boolean = environment.engine.next().also { persistSession() }

    fun previous(): Boolean = environment.engine.previous().also { persistSession() }

    /**
     * Seeks absolutely. A seek can fail — a decoder that cannot rewind, a stream that is gone — and
     * that failure used to escape through the key handler into composition. It belongs in the same
     * notice channel as every other error, not in the crash handler.
     */
    fun seekTo(millis: Long) {
        seek { environment.engine.seekTo(millis) }
    }

    fun seekBy(deltaMillis: Long) {
        seek { environment.engine.seekBy(deltaMillis) }
    }

    private inline fun seek(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            notice = e.message ?: "Could not seek"
        }
    }

    fun toggleShuffle() = environment.engine.toggleShuffle()

    fun cycleRepeat() = environment.engine.cycleRepeatMode()

    fun setVolume(volume: Float) = environment.engine.setVolume(volume)

    fun queue(): List<Track> = environment.engine.queueSnapshot()

    /**
     * Drops a queue entry. The engine remaps its cursor when something ahead of the current track
     * disappears, and the saved session has to follow or a restart would resurrect the old queue.
     */
    fun removeFromQueue(index: Int): Boolean =
        environment.engine.removeFromQueue(index).also { removed -> if (removed) persistSession() }

    /**
     * Reorders the queue without disturbing playback.
     *
     * The engine keeps the play order separate from the visible queue, so the cursor does not move
     * and the current track keeps playing; only what comes next changes. Persisted for the same
     * reason a removal is — a restart should show the queue the user arranged.
     */
    fun moveInQueue(from: Int, to: Int): Boolean =
        environment.engine.moveInQueue(from, to).also { moved -> if (moved) persistSession() }

    fun dismissNotice() {
        notice = null
    }

    /** Driven once per frame by the host; the engine ignores it while paused. */
    fun tick(deltaMillis: Long) = environment.engine.advance(deltaMillis)

    fun saveAsPlaylist(name: String, tracks: List<Track>) {
        val created = environment.playlists.create(name)
        environment.playlists.addTracks(created.id, tracks)
        refreshPlaylists()
        notice = "Saved “${created.name}”"
    }

    /** Name of the backend, for copy that mentions where the catalog comes from. */
    fun playbackSourceName(): String = environment.providerName

    // ----------------------------------------------------------------------------------------
    // Lyrics and session restore
    // ----------------------------------------------------------------------------------------

    private fun loadLyricsFor(track: Track) {
        lyrics = null
        background {
            when (val result = environment.lyrics.forTrack(track)) {
                // A miss is not an error worth interrupting playback for.
                is Load.Failure -> lyrics = null
                is Load.Success -> lyrics = result.value
            }
        }
    }

    /** Index of the lyric line on screen right now; -1 during the intro or when unsynced. */
    fun currentLyricIndex(): Int = lyrics?.lineIndexAt(playState.positionMillis) ?: -1

    fun hasSyncedLyrics(): Boolean = lyrics?.isSynced == true

    /** Restores the queue and position from the last session, without starting playback. */
    private fun restoreSession() {
        val saved = environment.playbackState.restore() ?: return
        restoring = true
        try {
            environment.engine.restore(
                tracks = saved.tracks,
                index = saved.index,
                positionMillis = saved.positionMillis,
                shuffleEnabled = saved.shuffleEnabled,
                repeatMode = saved.repeatMode,
                volume = saved.volume,
                autoplay = false,
            )
        } finally {
            restoring = false
        }
        playState = environment.engine.state()
        saved.currentTrack?.let(::loadLyricsFor)
    }

    private fun persistSession() {
        environment.playbackState.save(environment.engine.snapshotForPersistence())
    }

    /**
     * Playlists matching the library filter box, matched on name and contents. The selector is
     * passed positionally: a vararg of function types cannot take a trailing lambda.
     */
    fun filteredPlaylists(): List<Playlist> = LocalFilter.filter(
        playlists,
        libraryFilter,
        { playlist ->
            buildString {
                append(playlist.name)
                playlist.tracks.forEach { track ->
                    append(' ').append(track.title).append(' ').append(track.artistName)
                }
            }
        },
    )

    // ----------------------------------------------------------------------------------------

    private fun background(block: () -> Unit) {
        environment.executor.submit {
            try {
                block()
            } catch (e: Exception) {
                notice = e.message ?: "Unexpected error"
            }
        }
    }

    companion object {
        /** How many recent plays the history tab shows. */
        const val HISTORY_LIMIT = 30

        /** Tracks per search page; "load more" fetches the next one. */
        const val SEARCH_PAGE_SIZE = 12

        /** How far the seek keys jump. */
        const val SEEK_STEP_MILLIS = 10_000L

        /** Detail screens remembered for the back action. */
        const val MAX_BACK_DEPTH = 20
    }
}
