package co.omnimusic.core.store

import co.omnimusic.core.json.*
import co.omnimusic.core.model.Playlist
import co.omnimusic.core.model.Track

/**
 * Hand-rolled codec for the app's own on-disk format.
 *
 * Playlists have to survive an upgrade, so the schema is written down here explicitly and kept
 * backwards compatible: unknown fields are ignored and missing fields fall
 * back to defaults.
 */
object PlaylistJson {

    private const val SCHEMA_VERSION = 1

    fun encode(playlist: Playlist): String = JsonWriter.write(
        jsonObject(
            "schema" to jsonNumber(SCHEMA_VERSION),
            "id" to jsonString(playlist.id),
            "name" to jsonString(playlist.name),
            "description" to playlist.description?.let(::jsonString),
            "image" to playlist.imageUrl?.let(::jsonString),
            "tracks" to jsonArray(playlist.tracks.map(::trackJson)),
        )
    )

    fun decode(raw: String): Playlist? {
        val root = JsonParser.parseOrNull(raw).asObjectOrNull() ?: return null
        val id = root["id"].asStringOrNull() ?: return null
        val name = root["name"].asStringOrNull() ?: "Playlist"
        return Playlist(
            id = id,
            name = name,
            description = root["description"].asStringOrNull(),
            imageUrl = root["image"].asStringOrNull(),
            tracks = root["tracks"].asArrayOrEmpty().mapNotNull(::trackFrom),
        )
    }

    private fun trackJson(track: Track): Json = TrackJson.encode(track)

    private fun trackFrom(node: Json?): Track? = TrackJson.decode(node)
}
