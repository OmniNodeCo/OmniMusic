package co.omnimusic.core.player

import co.omnimusic.core.audio.AudioOutput
import co.omnimusic.core.audio.AudioSource
import co.omnimusic.core.model.Track
import co.omnimusic.core.util.Guard
import co.omnimusic.core.util.NoGuard
import kotlin.random.Random

/**
 * The queue and transport state machine.
 *
 * It owns *what* plays and *when*; the platform's [AudioOutput] owns *how*. Deliberately free of
 * timers and threads: the host ticks it with [advance] (a UI frame callback on desktop, a coroutine
 * loop on Android), which makes the whole thing deterministic and unit-testable.
 *
 * Shuffle is modelled as an explicit play order — a permutation of the queue's indices that the
 * cursor walks. That is what makes "previous" work while shuffling and keeps the behaviour easy to
 * reason about.
 */
class PlaybackEngine(
    private val audio: AudioOutput,
    private val random: Random = Random.Default,
    private val guard: Guard = NoGuard,
) {

    var listener: PlaybackListener? = null

    /**
     * Optional hook for tracks that are not HTTP streams (a local file the platform decoded itself).
     * Return `null` to fall back to [Track.streamUrl].
     */
    var sourceResolver: ((Track) -> AudioSource?)? = null

    private val queue = mutableListOf<Track>()
    private var order = mutableListOf<Int>()
    private var cursor = -1

    private var shuffleEnabled = false
    private var repeatMode = RepeatMode.OFF
    private var volume = 1f
    private var positionMillis = 0L
    private var playing = false

    val currentTrack: Track?
        get() = exclusive { if (cursor < 0) null else queue.getOrNull(order.getOrNull(cursor) ?: -1) }

    val isPlaying: Boolean get() = exclusive { playing }

    val durationMillis: Long get() = (currentTrack?.durationSeconds ?: 0) * 1000L

    fun queueSnapshot(): List<Track> = exclusive { queue.toList() }

    /** Tracks in the order they will actually be played. */
    fun playOrderSnapshot(): List<Track> = exclusive { order.mapNotNull { queue.getOrNull(it) } }

    fun state(): PlayState = exclusive {
        PlayState(
            track = currentTrack,
            isPlaying = playing,
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            queueIndex = order.getOrNull(cursor) ?: -1,
            queueSize = queue.size,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            volume = volume,
        )
    }

    /**
     * Runs [block] with the engine's state locked.
     *
     * Every public entry point goes through here, because the engine is reached from more than one
     * thread in the real apps: `AppModel.startRadio` starts a queue on the IO executor while the UI
     * thread reads `queueSnapshot()` to draw the queue. Without this, `queue.toList()` can iterate
     * while another thread adds to it — a `ConcurrentModificationException` in the middle of a
     * recomposition.
     *
     * The platform guards are reentrant, so the listener callbacks fired from inside a locked
     * section may call back into the engine, and the private helpers below need no locking of their
     * own. [NoGuard] makes all of this a no-op, which is what keeps the engine deterministic under
     * test.
     */
    private fun <T> exclusive(block: () -> T): T = guard.exclusive(block)

    // ----------------------------------------------------------------------------------------
    // Loading
    // ----------------------------------------------------------------------------------------

    /** Replaces the queue and starts playing at [startIndex]. */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0): Boolean = exclusive {
        if (tracks.isEmpty()) return@exclusive false
        audio.stop()
        queue.clear()
        queue += tracks
        rebuildOrder(startIndex.coerceIn(0, tracks.size - 1))
        startCurrent(attemptsLeft = queue.size)
    }

    /** Replaces the queue with a single track. */
    fun play(track: Track): Boolean = playQueue(listOf(track))

    /** Appends to the end of the queue; starts playback if the engine was idle. */
    fun enqueue(tracks: List<Track>) = exclusive {
        if (tracks.isEmpty()) return@exclusive
        val firstNew = queue.size
        queue += tracks
        if (cursor < 0) {
            rebuildOrder(firstNew)
            startCurrent(attemptsLeft = queue.size)
            return@exclusive
        }
        val added = (firstNew until queue.size).toList()
        order += if (shuffleEnabled) added.shuffled(random) else added
        notifyStateChanged()
    }

    /**
     * Drops a queue entry. Removing anything before the current track shifts the play order, so the
     * current index has to be remapped through the same transform or the cursor silently lands on a
     * different song. Removing the *current* track leaves the cursor at the front of the order.
     */
    fun removeFromQueue(index: Int): Boolean = exclusive {
        if (index !in queue.indices) return@exclusive false
        val currentIndex = order.getOrNull(cursor)
        val remappedCurrent = currentIndex?.let {
            when {
                it == index -> null
                it > index -> it - 1
                else -> it
            }
        }
        queue.removeAt(index)
        order = order.asSequence().filter { it != index }.map { if (it > index) it - 1 else it }.toMutableList()
        if (queue.isEmpty()) {
            cursor = -1
            positionMillis = 0
            playing = false
            audio.stop()
        } else {
            cursor = remappedCurrent?.let { order.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        }
        notifyStateChanged()
        true
    }

    /**
     * Moves a queue entry from [from] to [to], without touching what is playing.
     *
     * The queue and the play order are separate structures: [order] is a permutation of queue
     * indices that the cursor walks. Moving an entry therefore has to rewrite every index in the
     * range that shifted, or the cursor and everything after it silently point at the wrong song —
     * the same trap [removeFromQueue] has to handle when an entry disappears.
     *
     * [cursor] itself is left alone on purpose: it indexes [order], and [order] still holds the
     * same track at that position once the indices are remapped. Rewriting it here would be the
     * bug.
     */
    fun moveInQueue(from: Int, to: Int): Boolean = exclusive {
        if (from == to) return@exclusive false
        if (from !in queue.indices || to !in queue.indices) return@exclusive false
        val track = queue.removeAt(from)
        queue.add(to, track)
        order = order.map { index ->
            when {
                index == from -> to
                // Moving down: the entries the track passed shift up one slot.
                from < to && index in (from + 1)..to -> index - 1
                // Moving up: the entries it passed shift down one slot.
                from > to && index in to until from -> index + 1
                else -> index
            }
        }.toMutableList()
        notifyStateChanged()
        true
    }

    fun clearQueue() = exclusive {
        audio.stop()
        queue.clear()
        order.clear()
        cursor = -1
        positionMillis = 0
        playing = false
        notifyStateChanged()
    }

    // ----------------------------------------------------------------------------------------
    // Transport
    // ----------------------------------------------------------------------------------------

    fun togglePlayPause() = exclusive {
        if (currentTrack == null) return@exclusive
        if (playing) pause() else resume()
    }

    fun resume() = exclusive {
        if (currentTrack == null) return@exclusive
        if (!audio.isReady()) startCurrent(attemptsLeft = queue.size) else {
            playing = true
            audio.play()
            notifyStateChanged()
        }
    }

    fun pause() = exclusive {
        if (!playing) return@exclusive
        playing = false
        audio.pause()
        notifyStateChanged()
    }

    fun stop() = exclusive {
        playing = false
        positionMillis = 0
        audio.stop()
        notifyStateChanged()
    }

    fun seekTo(positionMillis: Long) = exclusive {
        val target = positionMillis.coerceIn(0L, durationMillis.coerceAtLeast(positionMillis))
        this.positionMillis = if (durationMillis > 0) target.coerceAtMost(durationMillis) else target
        audio.seekTo(this.positionMillis)
        notifyStateChanged()
    }

    /** Seeks by a signed offset; clamped to the track. */
    fun seekBy(deltaMillis: Long) = exclusive { seekTo(positionMillis + deltaMillis) }

    fun next(): Boolean = exclusive { goTo(cursor + 1) }

    /** Rewinds to the start if we are more than [REWIND_THRESHOLD_MILLIS] in, else goes back a track. */
    fun previous(): Boolean = exclusive {
        if (positionMillis > REWIND_THRESHOLD_MILLIS && currentTrack != null) {
            seekTo(0)
            return@exclusive true
        }
        goTo(cursor - 1)
    }

    fun setShuffle(enabled: Boolean) = exclusive {
        if (shuffleEnabled == enabled) return@exclusive
        shuffleEnabled = enabled
        val current = order.getOrNull(cursor) ?: 0
        if (queue.isNotEmpty()) rebuildOrder(current.coerceIn(0, queue.size - 1))
        notifyStateChanged()
    }

    fun toggleShuffle() = exclusive { setShuffle(!shuffleEnabled) }

    fun cycleRepeatMode(): RepeatMode = exclusive {
        repeatMode = repeatMode.next()
        notifyStateChanged()
        repeatMode
    }

    fun setRepeatMode(mode: RepeatMode) = exclusive {
        if (repeatMode == mode) return@exclusive
        repeatMode = mode
        notifyStateChanged()
    }

    fun setVolume(value: Float) = exclusive {
        volume = value.coerceIn(0f, 1f)
        audio.setVolume(volume)
        notifyStateChanged()
    }

    /**
     * Restores a persisted session: queue, cursor, position and transport settings.
     *
     * With [autoplay] false the player loads the track and sits at the saved position, which is what
     * a cold start should do — resume silently blasting audio the moment the app opens is not a
     * feature.
     */
    fun restore(
        tracks: List<Track>,
        index: Int = 0,
        positionMillis: Long = 0,
        shuffleEnabled: Boolean = false,
        repeatMode: RepeatMode = RepeatMode.OFF,
        volume: Float = 1f,
        autoplay: Boolean = true,
    ): Boolean = exclusive {
        if (tracks.isEmpty()) return@exclusive false
        audio.stop()
        queue.clear()
        queue += tracks
        this.shuffleEnabled = shuffleEnabled
        this.repeatMode = repeatMode
        this.volume = volume.coerceIn(0f, 1f)
        audio.setVolume(this.volume)
        rebuildOrder(index.coerceIn(0, tracks.size - 1))
        val started = startCurrent(attemptsLeft = queue.size)
        if (started) {
            if (positionMillis > 0) seekTo(positionMillis)
            if (!autoplay) pause()
        }
        started
    }

    /** Everything needed to persist this session. */
    fun snapshotForPersistence(): PersistedPlayback = exclusive {
        PersistedPlayback(
            tracks = queue.toList(),
            index = order.getOrNull(cursor) ?: -1,
            positionMillis = positionMillis,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            volume = volume,
        )
    }

    // ----------------------------------------------------------------------------------------
    // Clock
    // ----------------------------------------------------------------------------------------

    /**
     * Advances the transport by [deltaMillis].
     *
     * Called once per frame (or per 250 ms, whatever the host prefers). When the platform output can
     * report its own position that value wins, so the UI never drifts away from the audio.
     */
    fun advance(deltaMillis: Long) = exclusive {
        if (!playing || deltaMillis <= 0) return@exclusive
        val reported = audio.positionMillis()
        positionMillis = if (reported >= 0) reported else positionMillis + deltaMillis
        val duration = durationMillis
        if (duration > 0 && positionMillis >= duration) {
            handleTrackEnd()
        } else {
            notifyStateChanged()
        }
    }

    private fun handleTrackEnd() {
        if (repeatMode == RepeatMode.ONE) {
            positionMillis = 0
            audio.seekTo(0)
            notifyStateChanged()
            return
        }
        val hasNext = cursor + 1 < order.size
        when {
            hasNext -> {
                cursor++
                startCurrent(attemptsLeft = queue.size)
            }

            repeatMode == RepeatMode.ALL && order.isNotEmpty() -> {
                cursor = 0
                startCurrent(attemptsLeft = queue.size)
            }

            else -> finishQueue()
        }
    }

    private fun finishQueue() {
        playing = false
        positionMillis = durationMillis
        audio.pause()
        listener?.onQueueFinished()
        notifyStateChanged()
    }

    // ----------------------------------------------------------------------------------------
    // Internals
    // ----------------------------------------------------------------------------------------

    private fun goTo(target: Int): Boolean {
        if (queue.isEmpty() || order.isEmpty()) return false
        val resolved = when {
            target in order.indices -> target
            repeatMode == RepeatMode.ALL -> if (target < 0) order.size - 1 else 0
            else -> return false
        }
        cursor = resolved
        return startCurrent(attemptsLeft = queue.size)
    }

    /**
     * Loads and starts the track under the cursor.
     *
     * [attemptsLeft] bounds the skip loop: a queue full of dead stream URLs must not spin forever,
     * it should report each failure and then stop.
     */
    private fun startCurrent(attemptsLeft: Int): Boolean {
        val track = currentTrack
        if (track == null) {
            playing = false
            notifyStateChanged()
            return false
        }
        positionMillis = 0
        val source = sourceFor(track)
        if (source == null) {
            listener?.onTrackFailed(track, "No playable stream for this track")
            return skipForward(attemptsLeft)
        }
        return try {
            audio.prepare(source)
            audio.setVolume(volume)
            audio.play()
            playing = true
            listener?.onTrackStarted(track)
            notifyStateChanged()
            true
        } catch (e: Exception) {
            playing = false
            listener?.onTrackFailed(track, e.message ?: e::class.simpleName ?: "playback error")
            skipForward(attemptsLeft)
        }
    }

    private fun skipForward(attemptsLeft: Int): Boolean {
        if (attemptsLeft <= 1) {
            playing = false
            notifyStateChanged()
            return false
        }
        val target = if (cursor + 1 < order.size) cursor + 1 else if (repeatMode == RepeatMode.ALL) 0 else -1
        if (target < 0) {
            playing = false
            notifyStateChanged()
            return false
        }
        cursor = target
        return startCurrent(attemptsLeft - 1)
    }

    private fun sourceFor(track: Track): AudioSource? =
        sourceResolver?.invoke(track) ?: track.streamUrl?.takeIf { it.isNotBlank() }?.let(AudioSource::Remote)

    private fun rebuildOrder(preferIndex: Int) {
        val indices = (0 until queue.size).toList()
        if (shuffleEnabled) {
            val rest = indices.filter { it != preferIndex }.shuffled(random).toMutableList()
            rest.add(0, preferIndex)
            order = rest
            cursor = 0
        } else {
            order = indices.toMutableList()
            cursor = preferIndex
        }
    }

    /**
     * Fires the state callback. Every caller is already inside [exclusive], so there is no locking
     * here — and a block body, so the expression-bodied transport methods infer `Unit` rather than
     * the `Unit?` that `listener?.` would otherwise leak into their signatures.
     */
    private fun notifyStateChanged() {
        listener?.onStateChanged(state())
    }

    companion object {
        /** "Previous" restarts the track instead of changing it while we are past this point. */
        const val REWIND_THRESHOLD_MILLIS = 5_000L
    }
}
