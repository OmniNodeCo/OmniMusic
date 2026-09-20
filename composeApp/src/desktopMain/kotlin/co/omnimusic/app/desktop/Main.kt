package co.omnimusic.app.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import co.omnimusic.app.App
import co.omnimusic.app.AppModel
import co.omnimusic.app.ui.keyActionFor
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
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.concurrent.Executors
import javax.swing.JOptionPane

/** Desktop entry point: `./gradlew :composeApp:run` for a debug run, `packageDistributionForCurrentOS` for an installer. */
fun main() {
    // jpackage's native launcher reports "Failed to launch JVM" for everything that goes wrong
    // before the first frame: a JDK module missing from the bundled runtime, a class that fails to
    // initialise, an exception escaping main(). An installed build has no console and no window
    // yet, so that message is all a user ever sees. Capture it instead — to a file, and, when the
    // failure happens on this thread, to a dialog that says what actually threw.
    Thread.setDefaultUncaughtExceptionHandler { thread, error -> logStartupFailure(thread, error) }
    try {
        application {
            val environment = remember { desktopEnvironment() }
            val model = remember { AppModel(environment) }
            Window(
                onCloseRequest = ::exitApplication,
                // Recomposed as the track changes, so the window title (and the taskbar entry) says what
                // is playing without the window having to be focused.
                title = model.playState.track?.let { "${it.title} — ${it.artistName} · OmniMusic" } ?: "OmniMusic",
                state = rememberWindowState(width = 1120.dp, height = 780.dp),
                // Installed at the window rather than on a composable: preview key events are dispatched
                // down the focus chain, and a node that never takes focus would silently get nothing.
                onPreviewKeyEvent = { event ->
                    keyActionFor(event)?.let(model::handleKeyAction) ?: false
                },
            ) {
                App(environment, model)
            }
        }
    } catch (error: Throwable) {
        val log = logStartupFailure(Thread.currentThread(), error)
        runCatching {
            JOptionPane.showMessageDialog(
                null,
                "OmniMusic could not start.\n\n${describe(error)}\n\nDetails: $log",
                "OmniMusic failed to start",
                JOptionPane.ERROR_MESSAGE,
            )
        }
        throw error
    }
}

/**
 * Appends a startup failure to `<config dir>/startup-error.log`, falling back to the temp
 * directory if the config directory cannot be resolved — which is exactly the case where the
 * diagnosis matters most. Returns the path written to, or `null` if writing failed too.
 */
private fun logStartupFailure(thread: Thread, error: Throwable): String? {
    val trace = StringWriter().also { sink ->
        PrintWriter(sink).use { error.printStackTrace(it) }
    }.toString()
    val report = buildString {
        appendLine("--- OmniMusic startup failure ---")
        appendLine("time      = ${LocalDateTime.now()}")
        appendLine("thread    = ${thread.name}")
        appendLine("error     = ${describe(error)}")
        appendLine("os        = ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        appendLine("java      = ${System.getProperty("java.version")} at ${System.getProperty("java.home")}")
        appendLine("installed = ${System.getProperty("jpackage.app-path") ?: "no (not launched by jpackage)"}")
        appendLine(trace)
    }
    val target = runCatching {
        val directory = JvmKeyValueStore.defaultDirectory()
        Files.createDirectories(directory)
        directory.resolve("startup-error.log")
    }.getOrElse {
        Path.of(System.getProperty("java.io.tmpdir"), "omnimusic-startup-error.log")
    }
    return runCatching {
        Files.writeString(target, report, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        target.toString()
    }.getOrNull()
}

private fun describe(error: Throwable): String =
    "${error::class.qualifiedName ?: error::class.simpleName}: ${error.message ?: "no message"}"

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
