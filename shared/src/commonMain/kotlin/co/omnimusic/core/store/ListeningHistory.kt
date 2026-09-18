package co.omnimusic.core.store

import co.omnimusic.core.json.*
import co.omnimusic.core.model.Track
import co.omnimusic.core.util.Guard
import co.omnimusic.core.util.NoGuard

/**
 * On-device listening history.
 *
 * Everything stays on the device: it is what powers "Recently played" and the top-artists /
 * top-tracks stats without a single byte leaving the app. Only the aggregate counts are kept, so
 * the file stays small no matter how long the app is used.
 */
class ListeningHistory(
    private val store: KeyValueStore,
    private val maxEntries: Int = 200,
    private val guard: Guard = NoGuard,
) {

    data class Entry(
        val trackId: String,
        val title: String,
        val artistName: String,
        val imageUrl: String?,
        val plays: Int,
        val lastPlayedAt: Long,
    )

    data class Snapshot(
        val recent: List<Entry>,
        val totalPlays: Int,
        val distinctTracks: Int,
    )

    private var entries: LinkedHashMap<String, Entry>? = null

    /** Records a completed (or deliberately skipped-forward) play of [track]. */
    fun recordPlay(track: Track, playedAt: Long): Entry = guard.exclusive {
        val map = ensureLoaded()
        val previous = map[track.id]
        val entry = Entry(
            trackId = track.id,
            title = track.title,
            artistName = track.artistName,
            imageUrl = track.album?.imageUrl ?: track.artist.imageUrl,
            plays = (previous?.plays ?: 0) + 1,
            lastPlayedAt = playedAt,
        )
        // Re-insert so iteration order is "most recently played first".
        map.remove(track.id)
        map[track.id] = entry
        while (map.size > maxEntries) map.remove(map.keys.iterator().next())
        store.putString(STORE_KEY, encode(map.values.toList()))
        entry
    }

    fun recent(limit: Int = 20): List<Entry> = guard.exclusive {
        ensureLoaded().values.toList().asReversed().take(limit)
    }

    fun snapshot(limit: Int = 20): Snapshot = guard.exclusive {
        val map = ensureLoaded()
        Snapshot(
            recent = map.values.toList().asReversed().take(limit),
            totalPlays = map.values.sumOf { it.plays },
            distinctTracks = map.size,
        )
    }

    /** Tracks ordered by play count, the "your top tracks" list. */
    fun topTracks(limit: Int = 10): List<Entry> = guard.exclusive {
        ensureLoaded().values.sortedWith(compareByDescending<Entry> { it.plays }.thenByDescending { it.lastPlayedAt }).take(limit)
    }

    fun topArtists(limit: Int = 10): List<Pair<String, Int>> = guard.exclusive {
        ensureLoaded().values
            .groupBy { it.artistName }
            .map { (artist, entries) -> artist to entries.sumOf { it.plays } }
            .sortedByDescending { it.second }
            .take(limit)
    }

    fun playsFor(trackId: String): Int = guard.exclusive { ensureLoaded()[trackId]?.plays ?: 0 }

    fun clear() = guard.exclusive {
        entries = LinkedHashMap()
        store.remove(STORE_KEY)
    }

    // ----------------------------------------------------------------------------------------

    private fun ensureLoaded(): LinkedHashMap<String, Entry> {
        entries?.let { return it }
        val map = LinkedHashMap<String, Entry>()
        val raw = store.getString(STORE_KEY)
        if (raw != null) {
            val root = JsonParser.parseOrNull(raw).asObjectOrNull()
            for (node in root["entries"].asArrayOrEmpty()) {
                val obj = node.asObjectOrNull() ?: continue
                val id = obj["trackId"].asStringOrNull() ?: continue
                map[id] = Entry(
                    trackId = id,
                    title = obj["title"].asStringOrDefault("Unknown title"),
                    artistName = obj["artist"].asStringOrDefault("Unknown artist"),
                    imageUrl = obj["image"].asStringOrNull(),
                    plays = obj["plays"].asIntOrDefault(1),
                    lastPlayedAt = obj["playedAt"].asLongOrDefault(0),
                )
            }
        }
        entries = map
        return map
    }

    private fun encode(entries: List<Entry>): String = JsonWriter.write(
        jsonObject(
            "schema" to jsonNumber(1),
            "entries" to jsonArray(entries.map { entry ->
                jsonObject(
                    "trackId" to jsonString(entry.trackId),
                    "title" to jsonString(entry.title),
                    "artist" to jsonString(entry.artistName),
                    "image" to entry.imageUrl?.let(::jsonString),
                    "plays" to jsonNumber(entry.plays),
                    "playedAt" to jsonNumber(entry.lastPlayedAt),
                )
            }),
        )
    )

    companion object {
        const val STORE_KEY = "history.v1"
    }
}
