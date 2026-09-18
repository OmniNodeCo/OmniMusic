package co.omnimusic.core.player

import co.omnimusic.core.testing.FakeAudioOutput
import co.omnimusic.core.testing.testQueue
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cold-start behaviour: a player that reopens on an empty queue is the most annoying thing a player
 * can do, so restoring the session is tested as carefully as playback itself.
 */
class PlaybackEngineRestoreTest {

    private val audio = FakeAudioOutput()

    private fun engine() = PlaybackEngine(audio, Random(7))

    fun testRestoreRebuildsTheQueueWithoutPlaying() {
        val engine = engine()
        val saved = PersistedPlayback(
            tracks = testQueue(3),
            index = 1,
            positionMillis = 42_000,
            shuffleEnabled = false,
            repeatMode = RepeatMode.ALL,
            volume = 0.4f,
        )

        assertTrue(engine.restore(saved.tracks, saved.index, saved.positionMillis, saved.shuffleEnabled, saved.repeatMode, saved.volume, autoplay = false))

        val state = engine.state()
        assertEquals("2", state.track?.id)
        assertEquals(42_000L, state.positionMillis)
        assertEquals(3, state.queueSize)
        assertEquals(1, state.queueIndex)
        assertEquals(RepeatMode.ALL, state.repeatMode)
        assertEquals(0.4f, state.volume)
        assertFalse(state.isPlaying, "a cold start must not surprise the user with audio")
    }

    fun testRestoreCanAutoplayWhenAsked() {
        val engine = engine()
        engine.restore(testQueue(2), index = 0, autoplay = true)
        assertTrue(engine.isPlaying)
    }

    fun testRestoreRefusesAnEmptyQueue() {
        assertFalse(engine().restore(emptyList()))
    }

    fun testSnapshotRoundTripsThroughPersistence() {
        val first = engine()
        first.playQueue(testQueue(4), startIndex = 2)
        first.advance(30_000)
        first.setShuffle(true)
        first.cycleRepeatMode()
        first.setVolume(0.7f)
        val snapshot = first.snapshotForPersistence()

        val second = engine()
        second.restore(
            tracks = snapshot.tracks,
            index = snapshot.index,
            positionMillis = snapshot.positionMillis,
            shuffleEnabled = snapshot.shuffleEnabled,
            repeatMode = snapshot.repeatMode,
            volume = snapshot.volume,
            autoplay = false,
        )

        assertEquals(first.currentTrack?.id, second.currentTrack?.id)
        assertEquals(30_000L, second.state().positionMillis)
        assertEquals(true, second.state().shuffleEnabled)
        assertEquals(RepeatMode.ALL, second.state().repeatMode)
        assertEquals(0.7f, second.state().volume)
    }

    fun testRestoreReplacesWhateverWasPlaying() {
        val engine = engine()
        engine.playQueue(testQueue(2))
        assertEquals("1", engine.currentTrack?.id)

        engine.restore(listOf(co.omnimusic.core.testing.testTrack("x")), index = 0, autoplay = false)
        assertEquals("x", engine.currentTrack?.id)
        assertEquals(1, engine.state().queueSize)
    }
}
