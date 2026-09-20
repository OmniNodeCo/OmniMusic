package co.omnimusic.core.provider.deezer

import co.omnimusic.core.json.Json
import co.omnimusic.core.json.JsonException
import co.omnimusic.core.json.JsonParser
import co.omnimusic.core.json.get
import co.omnimusic.core.model.*
import co.omnimusic.core.net.ApiError
import co.omnimusic.core.net.HttpFetcher
import co.omnimusic.core.provider.MusicProvider
import co.omnimusic.core.util.buildUrl

/**
 * Deezer's public REST API: no API key, no OAuth, JSON everywhere.
 *
 * Free endpoints expose a 30-second `preview` MP3 per track, which is what the player streams.
 * Everything here is a plain blocking call so the core stays platform agnostic.
 */
class DeezerProvider(
    private val fetcher: HttpFetcher,
    private val baseUrl: String = "https://api.deezer.com",
) : MusicProvider {

    override val id: String = "deezer"

    override val displayName: String = "Deezer"

    override fun searchTracks(query: String, cursor: String?, limit: Int): Page<Track> {
        val node = get("/search", searchParams(query, cursor, limit))
        return Page(
            items = DeezerJson.trackList(node),
            total = DeezerJson.total(node),
            nextCursor = DeezerJson.nextCursor(node, limit),
        )
    }

    override fun searchAlbums(query: String, cursor: String?, limit: Int): Page<Album> {
        val node = get("/search/album", searchParams(query, cursor, limit))
        return Page(
            items = DeezerJson.albumList(node),
            total = DeezerJson.total(node),
            nextCursor = DeezerJson.nextCursor(node, limit),
        )
    }

    override fun searchArtists(query: String, cursor: String?, limit: Int): Page<Artist> {
        val node = get("/search/artist", searchParams(query, cursor, limit))
        return Page(
            items = DeezerJson.artistList(node),
            total = DeezerJson.total(node),
            nextCursor = DeezerJson.nextCursor(node, limit),
        )
    }

    override fun homeFeed(limit: Int): HomeFeed {
        val trending = chartTracks(limit)
        val albums = DeezerJson.albumList(get("/chart/0/albums", mapOf("limit" to limit.toString())))
        val allGenres = genres().filter { it.id != ALL_GENRES_ID }
        return HomeFeed(
            trendingTracks = trending,
            newAlbums = albums,
            genres = allGenres,
        )
    }

    override fun chartTracks(limit: Int): List<Track> =
        DeezerJson.trackList(get("/chart/0/tracks", mapOf("limit" to limit.toString()))).take(limit)

    override fun genres(): List<Genre> = DeezerJson.genreList(get("/genre"))

    override fun track(trackId: String): Track {
        val path = "/track/$trackId"
        return DeezerJson.track(get(path))
            ?: throw ApiError("Track $trackId returned no data", path)
    }

    override fun albumDetail(albumId: String): Pair<Album, List<Track>> {
        val node = get("/album/$albumId")
        val album = DeezerJson.album(node) ?: throw ApiError("Album $albumId returned no data", "/album/$albumId")
        val inline = DeezerJson.trackList(node["tracks"], fallbackArtist = fallbackArtistOf(album))
        val tracks = inline.ifEmpty {
            DeezerJson.trackList(get("/album/$albumId/tracks"), fallbackArtistOf(album))
        }
        val withCount = if (album.trackCount == 0) album.copy(trackCount = tracks.size) else album
        return withCount to tracks
    }

    override fun artistDetail(artistId: String): Artist =
        DeezerJson.artist(get("/artist/$artistId"))
            ?: throw ApiError("Artist $artistId returned no data", "/artist/$artistId")

    override fun artistTopTracks(artistId: String, limit: Int): List<Track> =
        DeezerJson.trackList(get("/artist/$artistId/top", mapOf("limit" to limit.toString()))).take(limit)

    override fun radio(seedTrackId: String, limit: Int): List<Track> =
        DeezerJson.trackList(get("/track/$seedTrackId/radio", mapOf("limit" to limit.toString()))).take(limit)

    override fun playlistDetail(playlistId: String): Playlist =
        DeezerJson.playlist(get("/playlist/$playlistId"))
            ?: throw ApiError("Playlist $playlistId returned no data", "/playlist/$playlistId")

    // ----------------------------------------------------------------------------------------

    private fun searchParams(query: String, cursor: String?, limit: Int): Map<String, String> =
        buildMap {
            put("q", query)
            put("limit", limit.toString())
            cursor?.toIntOrNull()?.let { put("index", it.toString()) }
        }

    private fun fallbackArtistOf(album: Album): Artist? =
        album.artistName.takeIf { it.isNotBlank() }?.let { Artist(id = "", name = it) }

    private fun get(path: String, params: Map<String, String> = emptyMap()): Json {
        val url = buildUrl(baseUrl + path, params)
        val response = try {
            fetcher.fetch(url)
        } catch (e: RuntimeException) {
            throw ApiError("Network request failed: ${e.message}", url, cause = e)
        }
        if (!response.isSuccess) {
            throw ApiError("Request failed", url, response.statusCode)
        }
        val node = try {
            JsonParser.parse(response.body)
        } catch (e: JsonException) {
            throw ApiError("Unreadable response from $displayName: ${e.message}", url, cause = e)
        }
        DeezerJson.errorOf(node)?.let { throw ApiError(it, url, response.statusCode) }
        return node
    }

    private companion object {
        /** Deezer returns an "All" pseudo-genre with id 0 that is not browsable. */
        const val ALL_GENRES_ID = "0"
    }
}
