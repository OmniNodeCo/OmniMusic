package co.omnimusic.core.repo

import co.omnimusic.core.model.*
import co.omnimusic.core.provider.MusicProvider

/**
 * The single entry point the UI talks to.
 *
 * Responsibilities, in order of importance:
 *  1. keep the provider off the main thread's business (callers still own threading, but nothing
 *     here is chatty: results are cached),
 *  2. turn exceptions into [Load.Failure] so screens render an error state instead of crashing,
 *  3. stitch together the pieces a screen needs ([homeFeed], [search]).
 */
class MusicRepository(
    private val provider: MusicProvider,
    private val cache: MemoryCache = MemoryCache(),
) {

    val providerId: String get() = provider.id
    val providerName: String get() = provider.displayName

    fun homeFeed(limit: Int = 10): Load<HomeFeed> = cached("home:$limit") { provider.homeFeed(limit) }

    fun chart(limit: Int = 20): Load<List<Track>> = cached("chart:$limit") { provider.chartTracks(limit) }

    fun genres(): Load<List<Genre>> = cached("genres") { provider.genres() }

    /** One round trip per facet; a failure in a facet degrades that facet, not the whole screen. */
    fun search(query: String, limit: Int = 10): Load<SearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return Load.Success(SearchResult(query, Page(emptyList()), Page(emptyList()), Page(emptyList())))
        }
        return cached("search:$trimmed:$limit") {
            SearchResult(
                query = trimmed,
                tracks = runCatching { provider.searchTracks(trimmed, limit = limit) }
                    .getOrElse { Page(emptyList()) },
                albums = runCatching { provider.searchAlbums(trimmed, limit = limit) }
                    .getOrElse { Page(emptyList()) },
                artists = runCatching { provider.searchArtists(trimmed, limit = limit) }
                    .getOrElse { Page(emptyList()) },
            )
        }
    }

    fun searchTracks(query: String, cursor: String? = null, limit: Int = MusicProvider.DEFAULT_LIMIT): Load<Page<Track>> =
        if (cursor == null) {
            cached("search:t:$query:$limit") { provider.searchTracks(query, limit = limit) }
        } else {
            attempt { provider.searchTracks(query, cursor, limit) }
        }

    fun album(albumId: String): Load<Pair<Album, List<Track>>> = cached("album:$albumId") { provider.albumDetail(albumId) }

    fun artist(artistId: String): Load<Pair<Artist, List<Track>>> = cached("artist:$artistId") {
        provider.artistDetail(artistId) to provider.artistTopTracks(artistId)
    }

    fun radio(seedTrackId: String, limit: Int = 20): Load<List<Track>> = attempt { provider.radio(seedTrackId, limit) }

    fun playlist(playlistId: String): Load<Playlist> = cached("playlist:$playlistId") { provider.playlistDetail(playlistId) }

    /** Drops everything; called when the user switches provider or pulls to refresh. */
    fun invalidate() = cache.clear()

    // ----------------------------------------------------------------------------------------

    private inline fun <T> attempt(crossinline block: () -> T): Load<T> = try {
        Load.Success(block())
    } catch (e: Exception) {
        Load.Failure(e.message ?: e::class.simpleName ?: "Unknown error")
    }

    private inline fun <T : Any> cached(key: String, crossinline block: () -> T): Load<T> {
        @Suppress("UNCHECKED_CAST")
        (cache.get(key) as? T)?.let { return Load.Success(it) }
        return attempt { block() }.also { result ->
            if (result is Load.Success) cache.put(key, result.value)
        }
    }
}
