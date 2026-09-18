package co.omnimusic.core.player

import co.omnimusic.core.model.Track
import co.omnimusic.core.testing.FakeAudioOutput
import co.omnimusic.core.testing.testTrack
import co.omnimusic.core.util.Guard
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The engine under a real lock instead of [co.omnimusic.core.util.NoGuard].
 *
 * Every other playback test is single-threaded, which is right for the state machine but blind to
 * the reason the engine takes a [Guard] at all: in the real apps `AppModel.startRadio` starts a
 * queue on the IO executor while the UI thread is drawing the queue from `queueSnapshot()`. These
 * tests use the same reentrant lock the Android and desktop layers build, so they cover the two
 * things that lock has to get right — reentrancy for listener callbacks, and mutual exclusion for
 * readers against writers.
 */
class PlaybackEngineConcurrencyTest {

    /** Shaped like the guard in `androidEnvironment` and the desktop one: reentrant by monitor. */
    private class SynchronizedGuard : Guard {
        private val lock = Any()
        override fun <T> exclusive(block: () -> T): T = synchronized(lock) { block() }
    }

    fun testListenerCanReadTheEngineBackFromInsideACallback() {
        val engine = PlaybackEngine(FakeAudioOutput(), guard = SynchronizedGuard())
        var observedQueueSize = -1
        engine.listener = object : PlaybackListener {
            override fun onStateChanged(state: PlayState) {
                // The callback fires from inside the engine's own critical section. With a
                // non-reentrant lock this deadlocks; with NoGuard it proves nothing at all.
                observedQueueSize = engine.queueSnapshot().size
            }
        }

        engine.playQueue(listOf(testTrack("a"), testTrack("b")))

        assertEquals(2, observedQueueSize)
    }

    fun testAListenerThatDrivesTheTransportDoesNotDeadlock() {
        val engine = PlaybackEngine(FakeAudioOutput(), guard = SynchronizedGuard())
        var pauses = 0
        engine.listener = object : PlaybackListener {
            override fun onTrackStarted(track: Track) {
                // A realistic host reaction: react to a start by touching the transport again.
                if (pauses++ == 0) engine.pause()
            }
        }

        assertTrue(engine.playQueue(listOf(testTrack("a"))))
        assertEquals(1, pauses)
        assertEquals(false, engine.isPlaying)
    }

    fun testReadersNeverSeeAQueueAnotherThreadIsStillBuilding() {
        val engine = PlaybackEngine(FakeAudioOutput(), guard = SynchronizedGuard())
        engine.playQueue(listOf(testTrack("seed")))

        val finished = CountDownLatch(1)
        var failure: Throwable? = null
        val reader = Thread {
            try {
                while (finished.count > 0) {
                    // `queue.toList()` is Arrays.copyOf under the hood, so it does not throw when
                    // another thread is appending — it quietly returns a copy padded with nulls,
                    // which then detonates somewhere far from here as a null in a List<Track>.
                    for (track in engine.queueSnapshot()) {
                        assertNotNull(track, "torn queue snapshot")
                    }
                    assertNotNull(engine.state().track, "torn state")
                }
            } catch (e: Throwable) {
                failure = e
            }
        }.apply { isDaemon = true }

        reader.start()
        repeat(300) { i ->
            // playQueue, not enqueue: it clears and refills, which is the window where the size
            // disagrees with the contents.
            engine.playQueue(listOf(testTrack("seed-$i"), testTrack("second-$i")))
        }
        finished.countDown()
        reader.join(10_000)

        failure?.let { throw AssertionError("reader thread failed: ${it::class.simpleName}: ${it.message}", it) }
        assertEquals(false, reader.isAlive, "reader thread never stopped")
        assertEquals(2, engine.queueSnapshot().size)
    }

    /**
     * The deterministic half of this file.
     *
     * The reader test above is a race, so it can only ever fail *sometimes* without the lock — it
     * is not a regression test on its own. This one is: it asserts the property the engine actually
     * promises, that no public entry point reads or writes state outside the guard. Drop the lock
     * from any one of these methods and this test names it.
     */
    fun testEveryPublicEntryPointTakesTheGuard() {
        val guard = RecordingGuard()
        val engine = PlaybackEngine(FakeAudioOutput(), guard = guard)
        val tracks = listOf(testTrack("a"), testTrack("b"), testTrack("c"))

        fun takesLock(what: String, block: () -> Unit) {
            val before = guard.entered
            block()
            assertTrue(guard.entered > before, "$what touched engine state without taking the guard")
        }

        takesLock("playQueue") { engine.playQueue(tracks) }
        takesLock("play") { engine.play(testTrack("solo")) }
        takesLock("enqueue") { engine.enqueue(listOf(testTrack("later"))) }
        takesLock("queueSnapshot") { engine.queueSnapshot() }
        takesLock("playOrderSnapshot") { engine.playOrderSnapshot() }
        takesLock("state") { engine.state() }
        takesLock("snapshotForPersistence") { engine.snapshotForPersistence() }
        takesLock("currentTrack") { engine.currentTrack }
        takesLock("isPlaying") { engine.isPlaying }
        takesLock("togglePlayPause") { engine.togglePlayPause() }
        takesLock("resume") { engine.resume() }
        takesLock("pause") { engine.pause() }
        takesLock("stop") { engine.stop() }
        takesLock("seekTo") { engine.seekTo(1_000) }
        takesLock("seekBy") { engine.seekBy(500) }
        takesLock("next") { engine.next() }
        takesLock("previous") { engine.previous() }
        takesLock("setShuffle") { engine.setShuffle(true) }
        takesLock("toggleShuffle") { engine.toggleShuffle() }
        takesLock("cycleRepeatMode") { engine.cycleRepeatMode() }
        takesLock("setRepeatMode") { engine.setRepeatMode(RepeatMode.ALL) }
        takesLock("setVolume") { engine.setVolume(0.5f) }
        takesLock("removeFromQueue") { engine.removeFromQueue(0) }
        takesLock("clearQueue") { engine.clearQueue() }
        takesLock("restore") { engine.restore(tracks, autoplay = false) }

        engine.playQueue(tracks)
        takesLock("advance") { engine.advance(1_000) }
    }

    private class RecordingGuard : Guard {
        var entered = 0
        override fun <T> exclusive(block: () -> T): T {
            entered++
            return block()
        }
    }

    fun testStatePublishedByAnotherThreadIsVisibleToTheNextReader() {
        val engine = PlaybackEngine(FakeAudioOutput(), guard = SynchronizedGuard())
        val started = CountDownLatch(1)

        val writer = Thread {
            engine.playQueue(listOf(testTrack("from-writer")))
            started.countDown()
        }
        writer.start()
        started.await()
        writer.join(10_000)

        // Same lock on both sides, so the write happens-before this read.
        assertEquals("from-writer", engine.currentTrack?.id)
        assertEquals(1, engine.queueSnapshot().size)
    }
}
