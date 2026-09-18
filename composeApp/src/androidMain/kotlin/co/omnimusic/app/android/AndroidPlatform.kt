package co.omnimusic.app.android

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import co.omnimusic.core.AppEnvironment
import co.omnimusic.core.Executor
import co.omnimusic.core.audio.AudioOutput
import co.omnimusic.core.audio.AudioSource
import co.omnimusic.core.audio.AudioSourceException
import co.omnimusic.core.audio.UnsupportedAudioException
import co.omnimusic.core.lyrics.LrcLibProvider
import co.omnimusic.core.lyrics.LyricsLibrary
import co.omnimusic.core.net.HttpFetcher
import co.omnimusic.core.net.HttpResponse
import co.omnimusic.core.player.PlaybackEngine
import co.omnimusic.core.provider.deezer.DeezerProvider
import co.omnimusic.core.repo.MusicRepository
import co.omnimusic.core.store.KeyValueStore
import co.omnimusic.core.store.ListeningHistory
import co.omnimusic.core.store.PlaybackStateStore
import co.omnimusic.core.store.PlaylistStore
import co.omnimusic.core.util.Guard
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Builds the graph Android needs. Called once from `MainActivity.onCreate`. */
fun androidEnvironment(context: Context): AppEnvironment {
    val appContext = context.applicationContext
    val guard = object : Guard {
        private val lock = Any()
        override fun <T> exclusive(block: () -> T): T = synchronized(lock) { block() }
    }
    val store = SharedPrefsKeyValueStore(appContext)
    val executor = object : Executor {
        private val pool = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "OmniMusic-io").apply { priority = Thread.NORM_PRIORITY - 1 }
        }

        override fun submit(block: () -> Unit) {
            pool.execute(block)
        }
    }
    return AppEnvironment(
        repository = MusicRepository(DeezerProvider(UrlConnectionFetcher())),
        lyrics = LyricsLibrary(LrcLibProvider(UrlConnectionFetcher())),
        playlists = PlaylistStore(store, guard = guard),
        history = ListeningHistory(store, guard = guard),
        playbackState = PlaybackStateStore(store, guard = guard),
        engine = PlaybackEngine(MediaPlayerAudioOutput(), guard = guard),
        executor = executor,
        guard = guard,
    )
}

/** [HttpFetcher] on `HttpURLConnection` — no OkHttp dependency for a two-line GET. */
class UrlConnectionFetcher(
    private val userAgent: String = "OmniMusic/1.0 (Android)",
) : HttpFetcher {

    override fun fetch(url: String): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            HttpResponse(status, body)
        } catch (e: IOException) {
            throw AudioSourceException("request to $url failed: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }
}

class SharedPrefsKeyValueStore(context: Context, name: String = "omnimusic") : KeyValueStore {

    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun keys(prefix: String): Set<String> =
        prefs.all.keys.filter { it.startsWith(prefix) }.toSet()
}

/**
 * [AudioOutput] on [MediaPlayer], which is what Android gives you for streaming compressed audio
 * without pulling in ExoPlayer. Swapping in Media3 is a matter of re-implementing this class.
 */
class MediaPlayerAudioOutput : AudioOutput {

    private var player: MediaPlayer? = null
    private var prepared = false

    override fun prepare(source: AudioSource) {
        stop()
        val url = (source as? AudioSource.Remote)?.url
            ?: throw UnsupportedAudioException("Android output streams URLs only; got ${source::class.simpleName}")
        val candidate = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
        }

        // `setDataSource(url)` opens the connection and `prepare()` blocks reading from it. Both are
        // network I/O, and the engine calls this straight from the click handler that started
        // playback — so on Android's main thread it throws NetworkOnMainThreadException before a
        // single sample is decoded. Every route into playback hits it: play, next, previous, resume
        // after a stall, and auto-advance when a track ends. Doing the blocking work on this thread
        // and having the caller wait for it fixes all of them at the boundary, and keeps the
        // engine's synchronous prepare/play contract — and therefore its error handling, which
        // reports the failure and skips to the next track — exactly as it is.
        //
        // The caller still blocks, so a stalled network can ANR. The alternative is prepareAsync()
        // with an auto-start listener, which needs an error channel back into the engine that
        // AudioOutput does not have; Media3 handles all of this properly and is the real fix.
        val finished = CountDownLatch(1)
        var failure: Throwable? = null
        val worker = Thread({
            try {
                candidate.setDataSource(url)
                candidate.prepare()
            } catch (e: Exception) {
                failure = e
            } finally {
                finished.countDown()
            }
        }, "OmniMusic-media")
        worker.start()

        if (!finished.await(PREPARE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            candidate.runCatching { release() }
            throw AudioSourceException("timed out opening $url after ${PREPARE_TIMEOUT_MILLIS}ms")
        }
        val error = failure
        if (error != null) {
            candidate.runCatching { release() }
            throw AudioSourceException("could not open $url: ${error.message}", error)
        }
        player = candidate
        prepared = true
    }

    override fun play() {
        player?.start()
    }

    override fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    override fun stop() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
        prepared = false
    }

    override fun seekTo(positionMillis: Long) {
        player?.seekTo(positionMillis.toInt())
    }

    override fun positionMillis(): Long = player?.currentPosition?.toLong() ?: -1L

    override fun setVolume(volume: Float) {
        val level = volume.coerceIn(0f, 1f)
        player?.setVolume(level, level)
    }

    override fun isReady(): Boolean = prepared

    private companion object {
        /**
         * How long to wait for a stream to open before giving up on it. Long enough for a cold CDN
         * connection, short enough that a dead URL fails over to the next track rather than hanging
         * the player indefinitely.
         */
        const val PREPARE_TIMEOUT_MILLIS = 15_000L
    }
}
