package co.omnimusic.core.store

import co.omnimusic.core.model.Playlist
import co.omnimusic.core.model.Track
import co.omnimusic.core.util.Guard
import co.omnimusic.core.util.NoGuard

/**
 * User playlists, persisted through a [KeyValueStore].
 *
 * One key per playlist (`playlist.<id>`), which keeps writes small and makes a corrupt entry cost
 * exactly one playlist rather than the whole library.
 */
class PlaylistStore(
    private val store: KeyValueStore,
    private val idFactory: () -> String = { generateId() },
    private val guard: Guard = NoGuard,
) {

    private val cache = LinkedHashMap<String, Playlist>()
    private var loaded = false

    fun all(): List<Playlist> = guard.exclusive {
        ensureLoaded()
        cache.values.toList()
    }

    fun byId(id: String): Playlist? = guard.exclusive {
        ensureLoaded()
        cache[id]
    }

    fun create(name: String, description: String? = null): Playlist = guard.exclusive {
        ensureLoaded()
        val playlist = Playlist(id = newId(), name = name.trim().ifBlank { "New playlist" }, description = description)
        cache[playlist.id] = playlist
        persist(playlist)
        playlist
    }

    fun rename(id: String, name: String): Playlist? = guard.exclusive {
        val updated = cache[id]?.copy(name = name.trim().ifBlank { "Untitled" }) ?: return@exclusive null
        cache[id] = updated
        persist(updated)
        updated
    }

    fun delete(id: String): Boolean = guard.exclusive {
        ensureLoaded()
        if (cache.remove(id) == null) return@exclusive false
        store.remove(key(id))
        true
    }

    fun addTrack(playlistId: String, track: Track): Playlist? = addTracks(playlistId, listOf(track))

    /** Appends tracks, skipping any that are already present (playlists have no duplicates). */
    fun addTracks(playlistId: String, tracks: List<Track>): Playlist? = guard.exclusive {
        ensureLoaded()
        val playlist = cache[playlistId] ?: return@exclusive null
        val existing = playlist.tracks.mapTo(HashSet()) { it.id }
        val merged = playlist.tracks + tracks.filter { existing.add(it.id) }
        if (merged.size == playlist.tracks.size) return@exclusive playlist
        val updated = playlist.copy(tracks = merged)
        cache[playlistId] = updated
        persist(updated)
        updated
    }

    fun removeTrack(playlistId: String, index: Int): Playlist? = guard.exclusive {
        val playlist = cache[playlistId] ?: return@exclusive null
        if (index !in playlist.tracks.indices) return@exclusive playlist
        val updated = playlist.copy(tracks = playlist.tracks.filterIndexed { i, _ -> i != index })
        cache[playlistId] = updated
        persist(updated)
        updated
    }

    fun moveTrack(playlistId: String, from: Int, to: Int): Playlist? = guard.exclusive {
        val playlist = cache[playlistId] ?: return@exclusive null
        if (from !in playlist.tracks.indices) return@exclusive playlist
        val target = to.coerceIn(0, playlist.tracks.size - 1)
        if (from == target) return@exclusive playlist
        val reordered = playlist.tracks.toMutableList()
        reordered.add(target, reordered.removeAt(from))
        val updated = playlist.copy(tracks = reordered)
        cache[playlistId] = updated
        persist(updated)
        updated
    }

    /** Playlists that already contain [trackId] — drives the "added" state of the add-to menu. */
    fun playlistsContaining(trackId: String): List<Playlist> = guard.exclusive {
        ensureLoaded()
        cache.values.filter { playlist -> playlist.tracks.any { it.id == trackId } }
    }

    /** Every track in every playlist, most recently added playlist first. */
    fun allTracks(): List<Track> = all().flatMap { it.tracks }

    // ----------------------------------------------------------------------------------------

    private fun ensureLoaded() {
        if (loaded) return
        for (key in store.keys(KEY_PREFIX)) {
            val raw = store.getString(key) ?: continue
            val playlist = PlaylistJson.decode(raw) ?: continue
            cache[playlist.id] = playlist
        }
        loaded = true
    }

    private fun persist(playlist: Playlist) {
        store.putString(key(playlist.id), PlaylistJson.encode(playlist))
    }

    private fun key(id: String) = KEY_PREFIX + id

    private fun newId(): String {
        var candidate = idFactory()
        while (cache.containsKey(candidate)) candidate = idFactory()
        return candidate
    }

    companion object {
        const val KEY_PREFIX = "playlist."

        /** 8 hex chars is plenty for a per-device collection and keeps keys readable. */
        fun generateId(): String {
            val value = kotlin.random.Random.Default.nextInt() and Int.MAX_VALUE
            return value.toString(16).padStart(8, '0').takeLast(8)
        }
    }
}
