package co.omnimusic.core.lyrics

import co.omnimusic.core.model.Load
import co.omnimusic.core.model.Track
import co.omnimusic.core.repo.MemoryCache

/**
 * Lyrics for a track, cached.
 *
 * A miss is a perfectly ordinary result — most instrumentals and plenty of catalogue tracks have no
 * transcript — so it is represented as `Load.Success(null)`, never as an error. Because the cache
 * stores non-null values, a miss is remembered under a sentinel so a track without lyrics is only
 * looked up once per session.
 */
class LyricsLibrary(
    private val provider: LyricsProvider,
    private val cache: MemoryCache = MemoryCache(),
) {

    private object NoLyrics

    fun forTrack(track: Track): Load<Lyrics?> {
        val key = keyFor(track)
        return when (val hit = cache.get(key)) {
            is Lyrics -> Load.Success(hit)
            NoLyrics -> Load.Success(null)
            else -> fetchAndCache(key, track)
        }
    }

    fun invalidate() = cache.clear()

    private fun fetchAndCache(key: String, track: Track): Load<Lyrics?> = try {
        val lyrics = provider.fetch(
            artist = track.artistName,
            track = track.title,
            album = track.album?.title,
            durationSeconds = track.durationSeconds,
        )
        cache.put(key, lyrics ?: NoLyrics)
        Load.Success(lyrics)
    } catch (e: Exception) {
        Load.Failure(e.message ?: e::class.simpleName ?: "Unknown error")
    }

    private fun keyFor(track: Track) = "lyrics:${track.artistName}:${track.title}"
}
