package co.omnimusic.core.store

import co.omnimusic.core.json.Json
import co.omnimusic.core.json.asBooleanOrDefault
import co.omnimusic.core.json.asIntOrDefault
import co.omnimusic.core.json.asLongOrDefault
import co.omnimusic.core.json.asObjectOrNull
import co.omnimusic.core.json.asStringOrDefault
import co.omnimusic.core.json.asStringOrNull
import co.omnimusic.core.json.get
import co.omnimusic.core.json.jsonBoolean
import co.omnimusic.core.json.jsonNumber
import co.omnimusic.core.json.jsonObject
import co.omnimusic.core.json.jsonString
import co.omnimusic.core.model.Album
import co.omnimusic.core.model.Artist
import co.omnimusic.core.model.Track

/**
 * The on-disk encoding of a single track.
 *
 * Shared by [PlaylistJson] and [PlaybackStateStore] so a track means the same thing wherever the
 * app stores one. Unknown fields are ignored and missing ones fall back to defaults, which is what
 * keeps an upgrade from invalidating the user's library.
 */
object TrackJson {

    fun encode(track: Track): Json = jsonObject(
        "id" to jsonString(track.id),
        "title" to jsonString(track.title),
        "duration" to jsonNumber(track.durationSeconds),
        "stream" to track.streamUrl?.let(::jsonString),
        "explicit" to jsonBoolean(track.explicit),
        "artistId" to jsonString(track.artist.id),
        "artist" to jsonString(track.artist.name),
        "artistImage" to track.artist.imageUrl?.let(::jsonString),
        "albumId" to track.album?.id?.let(::jsonString),
        "album" to track.album?.title?.let(::jsonString),
        "albumImage" to track.album?.imageUrl?.let(::jsonString),
        "albumTracks" to jsonNumber(track.album?.trackCount ?: 0),
        "albumReleased" to track.album?.releaseDate?.let(::jsonString),
        "popularity" to jsonNumber(track.popularity),
    )

    fun decode(node: Json?): Track? {
        val obj = node.asObjectOrNull() ?: return null
        val id = obj["id"].asStringOrNull() ?: return null
        val albumId = obj["albumId"].asStringOrNull()
        val albumTitle = obj["album"].asStringOrNull()
        val album = if (albumId != null || albumTitle != null) {
            Album(
                id = albumId ?: "",
                title = albumTitle ?: "",
                artistName = obj["artist"].asStringOrDefault(""),
                imageUrl = obj["albumImage"].asStringOrNull(),
                trackCount = obj["albumTracks"].asIntOrDefault(0),
                releaseDate = obj["albumReleased"].asStringOrNull(),
            )
        } else {
            null
        }
        return Track(
            id = id,
            title = obj["title"].asStringOrNull() ?: "Unknown title",
            artist = Artist(
                id = obj["artistId"].asStringOrDefault(""),
                name = obj["artist"].asStringOrDefault("Unknown artist"),
                imageUrl = obj["artistImage"].asStringOrNull(),
            ),
            album = album,
            durationSeconds = obj["duration"].asIntOrDefault(0),
            streamUrl = obj["stream"].asStringOrNull(),
            explicit = obj["explicit"].asBooleanOrDefault(false),
            popularity = obj["popularity"].asLongOrDefault(0),
        )
    }

    fun encodeAll(tracks: List<Track>): List<Json> = tracks.map(::encode)

    fun decodeAll(node: Json?): List<Track> =
        (node as? Json.Arr)?.items.orEmpty().mapNotNull(::decode)
}
