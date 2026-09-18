package co.omnimusic.core.store

import co.omnimusic.core.json.JsonParser
import co.omnimusic.core.json.JsonWriter
import co.omnimusic.core.json.asBooleanOrDefault
import co.omnimusic.core.json.asDoubleOrNull
import co.omnimusic.core.json.asIntOrDefault
import co.omnimusic.core.json.asLongOrDefault
import co.omnimusic.core.json.asObjectOrNull
import co.omnimusic.core.json.asStringOrNull
import co.omnimusic.core.json.get
import co.omnimusic.core.json.jsonArray
import co.omnimusic.core.json.jsonBoolean
import co.omnimusic.core.json.jsonNumber
import co.omnimusic.core.json.jsonObject
import co.omnimusic.core.json.jsonString
import co.omnimusic.core.model.Track
import co.omnimusic.core.player.PersistedPlayback
import co.omnimusic.core.player.RepeatMode
import co.omnimusic.core.util.Guard
import co.omnimusic.core.util.NoGuard

/**
 * The queue, persisted so the app resumes where it stopped.
 *
 * Reopening a music app to an empty player is the single most annoying thing a player can do, and
 * it costs one small JSON document to avoid. Only the last session is kept.
 */
class PlaybackStateStore(
    private val store: KeyValueStore,
    private val guard: Guard = NoGuard,
) {

    /** Persists one session, replacing whatever was there before. */
    fun save(state: PersistedPlayback) = save(
        tracks = state.tracks,
        index = state.index,
        positionMillis = state.positionMillis,
        shuffleEnabled = state.shuffleEnabled,
        repeatMode = state.repeatMode,
        volume = state.volume,
    )

    fun save(
        tracks: List<Track>,
        index: Int,
        positionMillis: Long,
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode,
        volume: Float,
    ) = guard.exclusive {
        if (tracks.isEmpty()) {
            store.remove(KEY)
            return@exclusive
        }
        store.putString(
            KEY,
            JsonWriter.write(
                jsonObject(
                    "schema" to jsonNumber(SCHEMA_VERSION),
                    "index" to jsonNumber(index),
                    "position" to jsonNumber(positionMillis),
                    "shuffle" to jsonBoolean(shuffleEnabled),
                    "repeat" to jsonString(repeatMode.name),
                    "volume" to jsonNumber(volume.toDouble()),
                    "tracks" to jsonArray(TrackJson.encodeAll(tracks)),
                )
            )
        )
    }

    fun restore(): PersistedPlayback? = guard.exclusive {
        val raw = store.getString(KEY) ?: return@exclusive null
        val root = JsonParser.parseOrNull(raw).asObjectOrNull() ?: return@exclusive null
        val tracks = TrackJson.decodeAll(root["tracks"])
        if (tracks.isEmpty()) return@exclusive null
        PersistedPlayback(
            tracks = tracks,
            index = root["index"].asIntOrDefault(0).coerceIn(0, tracks.size - 1),
            positionMillis = root["position"].asLongOrDefault(0).coerceAtLeast(0),
            shuffleEnabled = root["shuffle"].asBooleanOrDefault(false),
            repeatMode = runCatching { RepeatMode.valueOf(root["repeat"].asStringOrNull() ?: "") }
                .getOrDefault(RepeatMode.OFF),
            volume = (root["volume"].asDoubleOrNull()?.toFloat() ?: 1f).coerceIn(0f, 1f),
        )
    }

    fun clear() = guard.exclusive { store.remove(KEY) }

    companion object {
        const val KEY = "playback.v1"
        private const val SCHEMA_VERSION = 1
    }
}
