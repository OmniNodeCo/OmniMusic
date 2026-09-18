package co.omnimusic.core.provider

import co.omnimusic.core.model.*

/**
 * A music backend. [DeezerProvider] is the shipped implementation; the interface exists so another
 * service (YouTube Music, Jamendo, a local collection, …) can be dropped in without touching the
 * repository, the player or any UI code.
 *
 * All methods are synchronous and blocking: callers own the threading. On desktop that is a
 * background executor, on Android a coroutine on `Dispatchers.IO`.
 */
interface MusicProvider {

    /** Stable identifier persisted with user data, e.g. `"deezer"`. */
    val id: String

    val displayName: String

    fun searchTracks(query: String, cursor: String? = null, limit: Int = DEFAULT_LIMIT): Page<Track>

    fun searchAlbums(query: String, cursor: String? = null, limit: Int = DEFAULT_LIMIT): Page<Album>

    fun searchArtists(query: String, cursor: String? = null, limit: Int = DEFAULT_LIMIT): Page<Artist>

    /** Everything the landing screen needs, in one call for the caller. */
    fun homeFeed(limit: Int = DEFAULT_LIMIT): HomeFeed

    fun chartTracks(limit: Int = DEFAULT_LIMIT): List<Track>

    fun genres(): List<Genre>

    /** Album metadata plus its full track list. */
    fun albumDetail(albumId: String): Pair<Album, List<Track>>

    fun artistDetail(artistId: String): Artist

    fun artistTopTracks(artistId: String, limit: Int = DEFAULT_LIMIT): List<Track>

    /** "Start a radio from this track" — the closest thing to related content. */
    fun radio(seedTrackId: String, limit: Int = DEFAULT_LIMIT): List<Track>

    fun playlistDetail(playlistId: String): Playlist

    companion object {
        const val DEFAULT_LIMIT = 25
    }
}
