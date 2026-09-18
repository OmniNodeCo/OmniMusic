package co.omnimusic.core.provider.deezer

import co.omnimusic.core.json.*
import co.omnimusic.core.model.Album
import co.omnimusic.core.model.Artist
import co.omnimusic.core.model.Genre
import co.omnimusic.core.model.Playlist
import co.omnimusic.core.model.Track

/**
 * Maps raw Deezer JSON onto the provider-neutral model.
 *
 * Deezer is forgiving about which sub-objects it includes — a track inside an album track list has
 * no `artist`, an artist inside a track has no `picture` — so every mapper degrades gracefully
 * instead of throwing on a missing field.
 */
internal object DeezerJson {

    fun track(node: Json?, fallbackArtist: Artist? = null): Track? {
        val obj = node.asObjectOrNull() ?: return null
        val id = obj["id"].asLongOrNull()?.toString() ?: return null
        val title = obj["title"].asStringOrNull()
            ?: obj["title_short"].asStringOrNull()
            ?: "Unknown title"
        val album = album(obj["album"])
        val artist = artist(obj["artist"])
            ?: obj["contributors"][0]?.let(::artist)
            ?: fallbackArtist
            ?: album?.artistName?.takeIf { it.isNotBlank() }?.let { Artist(id = "", name = it) }
            ?: Artist(id = "", name = "Unknown artist")
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = obj["duration"].asIntOrDefault(0),
            streamUrl = obj["preview"].asStringOrNull(),
            explicit = obj["explicit_lyrics"].asBooleanOrDefault(false),
            popularity = obj["rank"].asLongOrDefault(0),
        )
    }

    fun trackList(node: Json?, fallbackArtist: Artist? = null): List<Track> =
        (node?.get("data") ?: node).asArrayOrEmpty().mapNotNull { track(it, fallbackArtist) }

    fun artist(node: Json?): Artist? {
        val obj = node.asObjectOrNull() ?: return null
        val id = obj["id"].asLongOrNull()?.toString() ?: return null
        val name = obj["name"].asStringOrNull() ?: return null
        return Artist(id = id, name = name, imageUrl = image(obj, "picture"))
    }

    fun artistList(node: Json?): List<Artist> =
        (node?.get("data") ?: node).asArrayOrEmpty().mapNotNull(::artist)

    fun album(node: Json?): Album? {
        val obj = node.asObjectOrNull() ?: return null
        val id = obj["id"].asLongOrNull()?.toString() ?: return null
        val title = obj["title"].asStringOrNull() ?: "Unknown album"
        val artistName = obj["artist"]["name"].asStringOrNull() ?: ""
        return Album(
            id = id,
            title = title,
            artistName = artistName,
            imageUrl = image(obj, "cover"),
            trackCount = obj["nb_tracks"].asIntOrDefault(0),
            releaseDate = obj["release_date"].asStringOrNull(),
        )
    }

    fun albumList(node: Json?): List<Album> =
        (node?.get("data") ?: node).asArrayOrEmpty().mapNotNull(::album)

    fun genre(node: Json?): Genre? {
        val obj = node.asObjectOrNull() ?: return null
        val id = obj["id"].asLongOrNull()?.toString() ?: return null
        val name = obj["name"].asStringOrNull() ?: return null
        return Genre(id = id, name = name, imageUrl = image(obj, "picture"))
    }

    fun genreList(node: Json?): List<Genre> =
        (node?.get("data") ?: node).asArrayOrEmpty().mapNotNull(::genre)

    fun playlist(node: Json?): Playlist? {
        val obj = node.asObjectOrNull() ?: return null
        val id = obj["id"].asLongOrNull()?.toString() ?: return null
        return Playlist(
            id = id,
            name = obj["title"].asStringOrNull() ?: "Playlist",
            tracks = trackList(obj["tracks"]),
            description = obj["description"].asStringOrNull(),
            imageUrl = image(obj, "picture"),
        )
    }

    /** `data` arrays in Deezer responses, tolerant of a bare array being passed in. */
    fun dataArray(node: Json?): List<Json> = (node?.get("data") ?: node).asArrayOrEmpty()

    fun total(node: Json?): Int = node["total"].asIntOrDefault(dataArray(node).size)

    /**
     * Deezer hands back a `next` URL for pagination. We only need the offset it carries, so the
     * cursor that leaves this class is just a number and stays stable across provider versions.
     */
    fun nextCursor(node: Json?, requestedLimit: Int): String? {
        val next = node["next"].asStringOrNull() ?: return null
        val index = Regex("[?&]index=(\\d+)").find(next)?.groupValues?.get(1)?.toIntOrNull()
        val cursor = index?.toString() ?: (requestedLimit.toString())
        return if (dataArray(node).isEmpty()) null else cursor
    }

    fun errorOf(node: Json?): String? {
        val err = node["error"].asObjectOrNull() ?: return null
        val type = err["type"].asStringOrNull() ?: "API error"
        val message = err["message"].asStringOrNull() ?: "no message"
        return "$type: $message"
    }

    private fun image(obj: Json.Obj, prefix: String): String? =
        obj["${prefix}_medium"].asStringOrNull()
            ?: obj["${prefix}_big"].asStringOrNull()
            ?: obj["${prefix}_small"].asStringOrNull()
            ?: obj["${prefix}_xl"].asStringOrNull()
            ?: obj[prefix].asStringOrNull()
}
