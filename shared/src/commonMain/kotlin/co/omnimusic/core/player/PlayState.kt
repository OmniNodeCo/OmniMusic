package co.omnimusic.core.player

import co.omnimusic.core.model.Track
import co.omnimusic.core.util.formatDuration

enum class RepeatMode {
    OFF, ALL, ONE;

    fun next(): RepeatMode = entries[(ordinal + 1) % entries.size]

    val label: String
        get() = when (this) {
            OFF -> "Repeat off"
            ALL -> "Repeat queue"
            ONE -> "Repeat track"
        }
}

/** Immutable snapshot of the engine — this is the only thing the UI is allowed to observe. */
data class PlayState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1f,
    val buffered: Boolean = false,
) {
    val isEmpty: Boolean get() = track == null
    val progress: Float
        get() = if (durationMillis <= 0L) 0f else (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    val positionLabel: String get() = formatDuration((positionMillis / 1000).toInt())
    val durationLabel: String get() = formatDuration((durationMillis / 1000).toInt())
    val hasNext: Boolean get() = queueSize > 0 && queueIndex >= 0
}

/**
 * Everything needed to resurrect a session: the queue, where the cursor was, the position within
 * the track, and the transport settings. Produced by [PlaybackEngine.snapshotForPersistence] and
 * consumed by [PlaybackEngine.restore].
 */
data class PersistedPlayback(
    val tracks: List<Track>,
    val index: Int,
    val positionMillis: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
    val volume: Float,
) {
    val currentTrack: Track? get() = tracks.getOrNull(index)
}

interface PlaybackListener {
    fun onStateChanged(state: PlayState) {}
    fun onTrackStarted(track: Track) {}
    /** A track could not be started (dead stream URL, missing codec, …). */
    fun onTrackFailed(track: Track, reason: String) {}
    /** The queue ran out and repeat is off. */
    fun onQueueFinished() {}
}

/** Convenience base class: implement only what you need. */
abstract class SimplePlaybackListener : PlaybackListener
