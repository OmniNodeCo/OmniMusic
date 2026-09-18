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
import java.util.concurrent.Executors

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
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            try {
                setDataSource(url)
                prepare()
            } catch (e: IOException) {
                throw AudioSourceException("could not open $url", e)
            }
        }
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
}
