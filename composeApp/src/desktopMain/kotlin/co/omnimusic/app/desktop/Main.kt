package co.omnimusic.app.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import co.omnimusic.app.App
import co.omnimusic.core.AppEnvironment
import co.omnimusic.core.Executor
import co.omnimusic.core.desktop.JavaSoundAudioOutput
import co.omnimusic.core.desktop.JdkHttpFetcher
import co.omnimusic.core.desktop.JvmKeyValueStore
import co.omnimusic.core.lyrics.LrcLibProvider
import co.omnimusic.core.lyrics.LyricsLibrary
import co.omnimusic.core.player.PlaybackEngine
import co.omnimusic.core.provider.deezer.DeezerProvider
import co.omnimusic.core.repo.MusicRepository
import co.omnimusic.core.store.ListeningHistory
import co.omnimusic.core.store.PlaybackStateStore
import co.omnimusic.core.store.PlaylistStore
import co.omnimusic.core.util.Guard
import java.util.concurrent.Executors

/** Desktop entry point: `./gradlew :composeApp:run` for a debug run, `packageDistributionForCurrentOS` for an installer. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "OmniMusic",
        state = rememberWindowState(width = 1120.dp, height = 780.dp),
    ) {
        val environment = remember { desktopEnvironment() }
        App(environment)
    }
}

/** The desktop wiring: JDK HTTP, Java Sound, a JSON store in the user's config directory. */
fun desktopEnvironment(): AppEnvironment {
    val guard = object : Guard {
        private val lock = Any()
        override fun <T> exclusive(block: () -> T): T = synchronized(lock) { block() }
    }
    val store = JvmKeyValueStore.default()
    val executor = object : Executor {
        private val pool = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "OmniMusic-io").apply { isDaemon = true }
        }

        override fun submit(block: () -> Unit) {
            pool.execute(block)
        }
    }
    return AppEnvironment(
        repository = MusicRepository(DeezerProvider(JdkHttpFetcher())),
        lyrics = LyricsLibrary(LrcLibProvider(JdkHttpFetcher())),
        playlists = PlaylistStore(store, guard = guard),
        history = ListeningHistory(store, guard = guard),
        playbackState = PlaybackStateStore(store, guard = guard),
        engine = PlaybackEngine(JavaSoundAudioOutput(), guard = guard),
        executor = executor,
        guard = guard,
    )
}
