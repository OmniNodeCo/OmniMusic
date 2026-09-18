package co.omnimusic.core.lyrics

import co.omnimusic.core.json.asBooleanOrDefault
import co.omnimusic.core.json.asDoubleOrNull
import co.omnimusic.core.json.asObjectOrNull
import co.omnimusic.core.json.asStringOrNull
import co.omnimusic.core.json.get
import co.omnimusic.core.json.JsonParser
import co.omnimusic.core.net.ApiError
import co.omnimusic.core.net.HttpFetcher
import co.omnimusic.core.util.buildUrl

/** A source of lyrics. Implementations are blocking, like [co.omnimusic.core.provider.MusicProvider]. */
interface LyricsProvider {

    val id: String

    /** Returns null when the track has no lyrics; throws only on a genuine transport failure. */
    fun fetch(artist: String, track: String, album: String? = null, durationSeconds: Int? = null): Lyrics?
}

/**
 * [LRCLIB](https://lrclib.net) — a free, keyless, community lyric database.
 *
 * It answers 404 when it has nothing for a track, so "not found" is a normal outcome here and is
 * reported as `null` rather than an error.
 */
class LrcLibProvider(
    private val fetcher: HttpFetcher,
    private val baseUrl: String = "https://lrclib.net",
) : LyricsProvider {

    override val id: String = "lrclib"

    override fun fetch(artist: String, track: String, album: String?, durationSeconds: Int?): Lyrics? {
        val params = buildMap {
            put("artist_name", artist)
            put("track_name", track)
            album?.takeIf { it.isNotBlank() }?.let { put("album_name", it) }
            durationSeconds?.takeIf { it > 0 }?.let { put("duration", it.toString()) }
        }
        val url = buildUrl("$baseUrl/api/get", params)
        val response = try {
            fetcher.fetch(url)
        } catch (e: RuntimeException) {
            throw ApiError("Lyrics request failed: ${e.message}", url, cause = e)
        }
        if (response.statusCode == 404) return null
        if (!response.isSuccess) throw ApiError("Lyrics request failed", url, response.statusCode)

        val root = JsonParser.parseOrNull(response.body).asObjectOrNull() ?: return null
        val synced = root["syncedLyrics"].asStringOrNull()
        val plain = root["plainLyrics"].asStringOrNull()
        if (synced.isNullOrBlank() && plain.isNullOrBlank()) return null

        return Lyrics(
            trackName = root["trackName"].asStringOrNull() ?: track,
            artistName = root["artistName"].asStringOrNull() ?: artist,
            albumName = root["albumName"].asStringOrNull(),
            durationMillis = ((root["duration"].asDoubleOrNull() ?: 0.0) * 1000).toLong(),
            instrumental = root["instrumental"].asBooleanOrDefault(false),
            lines = synced?.let(LrcParser::parse).orEmpty(),
            plainText = plain,
        )
    }
}
