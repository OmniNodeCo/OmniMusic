package co.omnimusic.core

import co.omnimusic.core.lyrics.LyricsLibrary
import co.omnimusic.core.player.PlaybackEngine
import co.omnimusic.core.repo.MusicRepository
import co.omnimusic.core.store.ListeningHistory
import co.omnimusic.core.store.PlaybackStateStore
import co.omnimusic.core.store.PlaylistStore
import co.omnimusic.core.util.Guard

/**
 * Everything the UI needs, assembled once per platform and handed to the app.
 *
 * Deliberately a plain holder rather than a DI framework: the whole graph is seven objects, and
 * constructing it in `MainActivity` / `main()` keeps it obvious what each platform provides.
 */
class AppEnvironment(
    val repository: MusicRepository,
    val lyrics: LyricsLibrary,
    val playlists: PlaylistStore,
    val history: ListeningHistory,
    val playbackState: PlaybackStateStore,
    val engine: PlaybackEngine,
    val executor: Executor,
    val guard: Guard,
) {
    val providerName: String get() = repository.providerName
}

/**
 * Background work, without coroutines in the core.
 *
 * The core is intentionally free of `kotlinx.coroutines` so it can be compiled by a bare `kotlinc`
 * and reused from any target. Callers that prefer coroutines can trivially wrap this.
 */
fun interface Executor {
    fun submit(block: () -> Unit)
}

/** Runs work inline. Used in tests and previews. */
object InlineExecutor : Executor {
    override fun submit(block: () -> Unit) = block()
}
