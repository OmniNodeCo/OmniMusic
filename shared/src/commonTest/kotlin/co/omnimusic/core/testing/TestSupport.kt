package co.omnimusic.core.testing

import co.omnimusic.core.audio.AudioOutput
import co.omnimusic.core.audio.AudioSource
import co.omnimusic.core.audio.AudioSourceException
import co.omnimusic.core.model.Album
import co.omnimusic.core.model.Artist
import co.omnimusic.core.model.Track
import co.omnimusic.core.store.KeyValueStore

/**
 * Shared test doubles.
 *
 * These live in the test source set rather than in production code so the engine's real dependency
 * ([AudioOutput]) stays an interface with exactly one production implementation per platform.
 */
class FakeAudioOutput : AudioOutput {

    /** Every call the engine made, in order, so tests can assert on the sequence. */
    val commands = mutableListOf<String>()

    var prepared: AudioSource? = null
        private set

    var ready: Boolean = false
        private set

    var playing: Boolean = false
        private set

    var volume: Float = 1f
        private set

    /**
     * `-1` means "this output cannot report a position", which is what forces the engine to
     * accumulate [co.omnimusic.core.player.PlaybackEngine.advance] ticks — deterministic for tests.
     */
    var reportedPosition: Long = -1L

    /** When non-null, the next [prepare] throws with this message. */
    var failNextPrepareWith: String? = null

    override fun prepare(source: AudioSource) {
        commands += "prepare"
        failNextPrepareWith?.let { message ->
            failNextPrepareWith = null
            throw AudioSourceException(message)
        }
        prepared = source
        ready = true
        playing = false
    }

    override fun play() {
        commands += "play"
        playing = true
    }

    override fun pause() {
        commands += "pause"
        playing = false
    }

    override fun stop() {
        commands += "stop"
        playing = false
        ready = false
    }

    override fun seekTo(positionMillis: Long) {
        commands += "seek:$positionMillis"
        reportedPosition = if (reportedPosition >= 0) positionMillis else reportedPosition
    }

    override fun positionMillis(): Long = reportedPosition

    override fun setVolume(volume: Float) {
        commands += "volume:$volume"
        this.volume = volume
    }

    override fun isReady(): Boolean = ready

    fun reset() {
        commands.clear()
        prepared = null
        ready = false
        playing = false
    }
}

/** Records every key written; nothing else. */
class RecordingKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {

    val writes = mutableListOf<Pair<String, String>>()
    private val values = LinkedHashMap(initial)

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        writes += key to value
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun keys(prefix: String): Set<String> = values.keys.filter { it.startsWith(prefix) }.toSet()

    fun rawSnapshot(): Map<String, String> = values.toMap()
}

fun testTrack(
    id: String,
    title: String = "Track $id",
    artistName: String = "Artist $id",
    durationSeconds: Int = 180,
    streamUrl: String? = "https://example.test/stream/$id.mp3",
    albumTitle: String? = "Album $id",
): Track = Track(
    id = id,
    title = title,
    artist = Artist(id = "a$id", name = artistName),
    album = albumTitle?.let { Album(id = "al$id", title = it, artistName = artistName) },
    durationSeconds = durationSeconds,
    streamUrl = streamUrl,
)

fun testQueue(count: Int): List<Track> = (1..count).map { testTrack(it.toString()) }
