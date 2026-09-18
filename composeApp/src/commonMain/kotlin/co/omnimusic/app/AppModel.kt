package co.omnimusic.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.omnimusic.core.AppEnvironment
import co.omnimusic.core.lyrics.Lyrics
import co.omnimusic.core.model.*
import co.omnimusic.core.util.LocalFilter
import co.omnimusic.core.player.PlayState
import co.omnimusic.core.player.PlaybackListener

/** Where the app currently is. Hand-rolled instead of a navigation library to keep the graph visible. */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data class Album(val id: String, val title: String) : Screen
    data class Artist(val id: String, val name: String) : Screen
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

    var nowPlayingExpanded by mutableStateOf(false)

    var notice by mutableStateOf<String?>(null)

    private val history = environment.history

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
    }

    fun goSearch() {
        screen = Screen.Search
        if (searchResults == null && searchQuery.isNotBlank()) runSearch(searchQuery)
    }

    fun goLibrary() {
        screen = Screen.Library
        refreshPlaylists()
    }

    fun openAlbum(id: String, title: String) {
        screen = Screen.Album(id, title)
        album = null
        background { album = environment.repository.album(id) }
    }

    fun openArtist(id: String, name: String) {
        if (id.isBlank()) {
            notice = "This entry has no artist id"
            return
        }
        screen = Screen.Artist(id, name)
        artist = null
        background { artist = environment.repository.artist(id) }
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
        if (query.isBlank()) {
            searchResults = null
            return
        }
        background { searchResults = environment.repository.search(query, limit = 12) }
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

    fun seekTo(millis: Long) = environment.engine.seekTo(millis)

    fun toggleShuffle() = environment.engine.toggleShuffle()

    fun cycleRepeat() = environment.engine.cycleRepeatMode()

    fun setVolume(volume: Float) = environment.engine.setVolume(volume)

    fun queue(): List<Track> = environment.engine.queueSnapshot()

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
}
