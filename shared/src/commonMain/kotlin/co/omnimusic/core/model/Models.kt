package co.omnimusic.core.model

import co.omnimusic.core.util.formatDuration

/** Provider-neutral view of a music catalog entry. Every backend maps onto these types. */

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
)

data class Album(
    val id: String,
    val title: String,
    val artistName: String = "",
    val imageUrl: String? = null,
    val trackCount: Int = 0,
    val releaseDate: String? = null,
)

data class Track(
    val id: String,
    val title: String,
    val artist: Artist,
    val album: Album? = null,
    val durationSeconds: Int = 0,
    /** Direct, playable URL (a preview clip for most free APIs). Null means "metadata only". */
    val streamUrl: String? = null,
    val explicit: Boolean = false,
    val popularity: Long = 0,
) {
    val artistName: String get() = artist.name
    val durationLabel: String get() = formatDuration(durationSeconds)
}

data class Genre(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
)

/** A user playlist. Remote playlists are materialized into this shape too. */
data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList(),
    val description: String? = null,
    val imageUrl: String? = null,
) {
    val totalSeconds: Int get() = tracks.sumOf { it.durationSeconds }
    val durationLabel: String get() = formatDuration(totalSeconds)
}

/** A slice of a server-side result set. [nextCursor] is opaque to callers. */
data class Page<T>(
    val items: List<T>,
    val total: Int = items.size,
    val nextCursor: String? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val hasMore: Boolean get() = nextCursor != null
}

/** Combined, de-duplicated result of a catalog search. */
data class SearchResult(
    val query: String,
    val tracks: Page<Track>,
    val albums: Page<Album>,
    val artists: Page<Artist>,
) {
    val isEmpty: Boolean get() = tracks.isEmpty && albums.isEmpty && artists.isEmpty
}

/** The "For you" style landing page: charts plus browsable genres. */
data class HomeFeed(
    val trendingTracks: List<Track>,
    val newAlbums: List<Album>,
    val genres: List<Genre>,
)

/** Outcome wrapper used by the repository so the UI never has to catch raw exceptions. */
sealed interface Load<out T> {
    data class Success<T>(val value: T) : Load<T>
    data class Failure(val error: String) : Load<Nothing>
}

inline fun <T, R> Load<T>.map(transform: (T) -> R): Load<R> = when (this) {
    is Load.Success -> Load.Success(transform(value))
    is Load.Failure -> this
}
