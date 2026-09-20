package co.omnimusic.core.player

import co.omnimusic.core.audio.AudioSource
import co.omnimusic.core.audio.AudioSourceException
import co.omnimusic.core.model.Track
import co.omnimusic.core.testing.FakeAudioOutput
import co.omnimusic.core.testing.testQueue
import co.omnimusic.core.testing.testTrack
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackEngineTest {

    private class Recorder : PlaybackListener {
        val failures = mutableListOf<Pair<Track, String>>()
        var started = mutableListOf<Track>()
        var queueFinishedCount = 0
        var stateChanges = 0

        override fun onTrackStarted(track: Track) {
            started += track
        }

        override fun onTrackFailed(track: Track, reason: String) {
            failures += track to reason
        }

        override fun onQueueFinished() {
            queueFinishedCount++
        }

        override fun onStateChanged(state: PlayState) {
            stateChanges++
        }
    }

    private val audio = FakeAudioOutput()
    private val recorder = Recorder()

    private fun engine(random: Random = Random(42)): PlaybackEngine =
        PlaybackEngine(audio, random).also { it.listener = recorder }

    /** Runs the clock to the end of the current track, which is what triggers a transition. */
    private fun PlaybackEngine.finishCurrentTrack() {
        advance(durationMillis + 1)
    }

    // ----------------------------------------------------------------------------------------

    fun testPlayQueueStartsTheFirstTrack() {
        val engine = engine()
        assertTrue(engine.playQueue(testQueue(3)))
        assertEquals("1", engine.currentTrack?.id)
        assertTrue(engine.isPlaying)

        val state = engine.state()
        assertEquals(0, state.queueIndex)
        assertEquals(3, state.queueSize)
        assertEquals(0L, state.positionMillis)
        assertEquals(180_000L, state.durationMillis)
        assertEquals("3:00", state.durationLabel)
        assertTrue(state.isPlaying)

        // The platform output was handed a real stream URL and told to play.
        assertEquals(AudioSource.Remote("https://example.test/stream/1.mp3"), audio.prepared)
        assertTrue(audio.playing)
        assertEquals(listOf("stop", "prepare", "volume:1.0", "play"), audio.commands)
        assertEquals(listOf("1"), recorder.started.map { it.id })
    }

    fun testEmptyQueueIsRejected() {
        assertFalse(engine().playQueue(emptyList()))
        assertNull(engine().currentTrack)
    }

    fun testPlayQueueAtAnIndex() {
        val engine = engine()
        engine.playQueue(testQueue(5), startIndex = 3)
        assertEquals("4", engine.currentTrack?.id)
        assertEquals(3, engine.state().queueIndex)
    }

    fun testNextWalksTheQueueAndStopsAtTheEnd() {
        val engine = engine()
        engine.playQueue(testQueue(3))
        assertTrue(engine.next())
        assertEquals("2", engine.currentTrack?.id)
        assertTrue(engine.next())
        assertEquals("3", engine.currentTrack?.id)
        // Repeat is off, so there is nowhere left to go and the current track is untouched.
        assertFalse(engine.next())
        assertEquals("3", engine.currentTrack?.id)
        assertTrue(engine.isPlaying)
    }

    fun testPreviousGoesBackATrackFromTheStart() {
        val engine = engine()
        engine.playQueue(testQueue(3), startIndex = 2)
        assertTrue(engine.previous())
        assertEquals("2", engine.currentTrack?.id)
    }

    fun testPreviousRewindsInsteadWhenPastTheThreshold() {
        val engine = engine()
        engine.playQueue(testQueue(3))
        engine.advance(PlaybackEngine.REWIND_THRESHOLD_MILLIS + 1000)
        assertEquals(6000L, engine.state().positionMillis)

        audio.commands.clear()
        assertTrue(engine.previous())
        assertEquals("1", engine.currentTrack?.id)
        assertEquals(0L, engine.state().positionMillis)
        assertEquals(listOf("seek:0"), audio.commands)
    }

    fun testRepeatOffStopsAtTheEndOfTheQueue() {
        val engine = engine()
        engine.playQueue(listOf(testTrack("only")))
        engine.finishCurrentTrack()

        assertFalse(engine.isPlaying)
        assertEquals(180_000L, engine.state().positionMillis)
        assertEquals(1, recorder.queueFinishedCount)
    }

    fun testRepeatAllWrapsToTheFront() {
        val engine = engine()
        engine.playQueue(testQueue(2))
        engine.setRepeatMode(RepeatMode.ALL)

        engine.finishCurrentTrack()
        assertEquals("2", engine.currentTrack?.id)
        engine.finishCurrentTrack()
        assertEquals("1", engine.currentTrack?.id)
        assertTrue(engine.isPlaying)
        assertEquals(0, recorder.queueFinishedCount)
    }

    fun testRepeatOneReplaysTheSameTrack() {
        val engine = engine()
        engine.playQueue(testQueue(2))
        engine.setRepeatMode(RepeatMode.ONE)
        audio.commands.clear()

        engine.finishCurrentTrack()
        assertEquals("1", engine.currentTrack?.id)
        assertEquals(0L, engine.state().positionMillis)
        assertTrue(engine.isPlaying)
        assertTrue(audio.commands.contains("seek:0"))
    }

    fun testRepeatModeCycles() {
        val engine = engine()
        assertEquals(RepeatMode.ALL, engine.cycleRepeatMode())
        assertEquals(RepeatMode.ONE, engine.cycleRepeatMode())
        assertEquals(RepeatMode.OFF, engine.cycleRepeatMode())
    }

    fun testShuffleVisitsEveryTrackExactlyOnce() {
        val engine = engine(Random(1234))
        engine.setShuffle(true)
        engine.playQueue(testQueue(10))

        val visited = mutableListOf(engine.currentTrack!!.id)
        repeat(9) {
            assertTrue(engine.next(), "shuffle order ran out after ${visited.size} tracks")
            visited += engine.currentTrack!!.id
        }
        assertEquals((1..10).map { it.toString() }.sorted(), visited.sorted())
        assertEquals(visited.size, visited.distinct().size)
        assertFalse(engine.next(), "shuffled queue must not repeat without repeat-all")
    }

    fun testShuffleIsReproducibleForTheSameSeed() {
        fun orderFor(seed: Long): List<String> {
            val e = PlaybackEngine(FakeAudioOutput(), Random(seed))
            e.setShuffle(true)
            e.playQueue(testQueue(8))
            return e.playOrderSnapshot().map { it.id }
        }
        assertEquals(orderFor(7), orderFor(7))
    }

    fun testTogglingShuffleKeepsTheCurrentTrack() {
        val engine = engine(Random(99))
        engine.playQueue(testQueue(5))
        engine.next()
        engine.next()
        assertEquals("3", engine.currentTrack?.id)

        engine.toggleShuffle()
        assertEquals("3", engine.currentTrack?.id)
        assertEquals(2, engine.state().queueIndex, "queueIndex must still point at the real queue slot")
        assertTrue(engine.state().shuffleEnabled)
        assertEquals(5, engine.playOrderSnapshot().size)

        engine.toggleShuffle()
        assertEquals("3", engine.currentTrack?.id)
        assertFalse(engine.state().shuffleEnabled)
    }

    fun testUnplayableTracksAreReportedAndSkipped() {
        val engine = engine()
        val dead = testTrack("dead", streamUrl = null)
        val good = testTrack("good")
        assertTrue(engine.playQueue(listOf(dead, good)))

        assertEquals("good", engine.currentTrack?.id)
        assertEquals(listOf("dead"), recorder.failures.map { it.first.id })
        assertTrue(recorder.failures[0].second.contains("No playable stream"))
    }

    fun testAQueueWithNothingPlayableGivesUpInsteadOfSpinning() {
        val engine = engine()
        val queue = listOf(testTrack("a", streamUrl = null), testTrack("b", streamUrl = null))
        assertFalse(engine.playQueue(queue))
        assertFalse(engine.isPlaying)
        assertEquals(2, recorder.failures.size, "each dead track should be reported exactly once")
    }

    fun testAFailingOutputIsReportedAndSkipped() {
        val engine = engine()
        audio.failNextPrepareWith = "codec unavailable"
        assertTrue(engine.playQueue(listOf(testTrack("a"), testTrack("b"))))
        assertEquals("b", engine.currentTrack?.id)
        assertEquals(listOf("a"), recorder.failures.map { it.first.id })
        assertEquals("codec unavailable", recorder.failures[0].second)
    }

    fun testEnqueueKeepsTheCurrentTrackPlaying() {
        val engine = engine()
        engine.playQueue(testQueue(2))
        engine.enqueue(listOf(testTrack("3")))
        assertEquals("1", engine.currentTrack?.id)
        assertEquals(3, engine.state().queueSize)

        engine.next()
        assertEquals("2", engine.currentTrack?.id)
        engine.next()
        assertEquals("3", engine.currentTrack?.id)
    }

    fun testEnqueueIntoAnIdleEngineStartsPlayback() {
        val engine = engine()
        assertFalse(engine.isPlaying)
        engine.enqueue(testQueue(2))
        assertEquals("1", engine.currentTrack?.id)
        assertTrue(engine.isPlaying)
    }

    fun testRemoveFromQueueKeepsTheCursorOnTheCurrentTrack() {
        val engine = engine()
        engine.playQueue(testQueue(3))
        engine.next()
        assertEquals("2", engine.currentTrack?.id)

        assertTrue(engine.removeFromQueue(0))
        assertEquals("2", engine.currentTrack?.id)
        assertEquals(2, engine.state().queueSize)
        assertEquals(listOf("2", "3"), engine.queueSnapshot().map { it.id })
        assertFalse(engine.removeFromQueue(9))
    }

    fun testRemovingTheLastTrackLeavesAnIdleEngine() {
        val engine = engine()
        engine.playQueue(listOf(testTrack("only")))
        assertTrue(engine.removeFromQueue(0))
        assertNull(engine.currentTrack)
        assertFalse(engine.isPlaying)
        assertEquals(-1, engine.state().queueIndex)
    }

    // ----------------------------------------------------------------------------------------
    // Reordering the queue
    // ----------------------------------------------------------------------------------------

    fun testMoveInQueueReordersTheQueue() {
        val engine = engine()
        engine.playQueue(testQueue(4))

        assertTrue(engine.moveInQueue(0, 2))
        assertEquals(listOf("2", "3", "1", "4"), engine.queueSnapshot().map { it.id })

        assertTrue(engine.moveInQueue(3, 0))
        assertEquals(listOf("4", "2", "3", "1"), engine.queueSnapshot().map { it.id })
    }

    fun testMovingTheCurrentTrackKeepsItCurrentAndPlaying() {
        val engine = engine()
        engine.playQueue(testQueue(4))
        assertEquals("1", engine.currentTrack?.id)

        assertTrue(engine.moveInQueue(0, 3))

        // The entry moved to the end of the queue, but nothing about playback changed.
        assertEquals("1", engine.currentTrack?.id)
        assertTrue(engine.isPlaying)
        assertEquals(listOf("2", "3", "4", "1"), engine.queueSnapshot().map { it.id })
        assertEquals(3, engine.state().queueIndex)
    }

    fun testMovingAnotherEntryLeavesTheCursorOnTheSameTrack() {
        val engine = engine()
        engine.playQueue(testQueue(4))
        engine.next()
        assertEquals("2", engine.currentTrack?.id)

        assertTrue(engine.moveInQueue(0, 3))

        assertEquals("2", engine.currentTrack?.id)
        // queueIndex is the current track's position in the queue, not the cursor: "2" now sits at
        // the front, so 0 is the right answer even though the cursor has not moved.
        assertEquals(0, engine.state().queueIndex)
    }

    fun testNextAfterAMoveFollowsTheNewQueueOrder() {
        val engine = engine()
        engine.playQueue(testQueue(3))
        assertEquals("1", engine.currentTrack?.id)

        // Move the last entry to the front: the visible queue changes, the play order must not.
        assertTrue(engine.moveInQueue(2, 0))
        assertEquals(listOf("3", "1", "2"), engine.queueSnapshot().map { it.id })

        engine.next()
        assertEquals("2", engine.currentTrack?.id)
    }

    fun testMovingPreservesTheShuffledPlayOrder() {
        val engine = engine()
        engine.setShuffle(true)
        engine.playQueue(testQueue(6))
        engine.next()
        val before = engine.playOrderSnapshot().map { it.id }
        val current = engine.currentTrack?.id
        assertEquals(6, before.distinct().size)

        // The strongest invariant available: a move rewrites queue indices, and if any one of them
        // is remapped wrongly the sequence of tracks the player will walk changes.
        assertTrue(engine.moveInQueue(1, 4))
        assertEquals(before, engine.playOrderSnapshot().map { it.id })
        assertEquals(current, engine.currentTrack?.id)

        assertTrue(engine.moveInQueue(5, 0))
        assertEquals(before, engine.playOrderSnapshot().map { it.id })
        assertEquals(current, engine.currentTrack?.id)
    }

    fun testMoveInQueueRejectsOutOfRangeAndNoOpMoves() {
        val engine = engine()
        engine.playQueue(testQueue(3))
        val before = engine.queueSnapshot().map { it.id }

        assertFalse(engine.moveInQueue(1, 1))
        assertFalse(engine.moveInQueue(0, 3))
        assertFalse(engine.moveInQueue(3, 0))
        assertFalse(engine.moveInQueue(-1, 0))
        assertFalse(engine.moveInQueue(0, -1))
        assertEquals(before, engine.queueSnapshot().map { it.id })
    }

    fun testMoveInQueueOnAnEmptyOrSingleEntryQueue() {
        val engine = engine()
        assertFalse(engine.moveInQueue(0, 0))
        assertFalse(engine.moveInQueue(0, 1))

        engine.playQueue(listOf(testTrack("only")))
        assertFalse(engine.moveInQueue(0, 1))
        assertEquals(listOf("only"), engine.queueSnapshot().map { it.id })
    }

    fun testMovingEveryEntryInTurnKeepsTheQueueConsistent() {
        val engine = engine()
        engine.playQueue(testQueue(5))

        // Walk one entry all the way to the end and back, asserting the queue is always a
        // permutation of the original rather than losing or duplicating a track.
        val original = engine.queueSnapshot().map { it.id }.sorted()
        for (i in 0 until 4) {
            assertTrue(engine.moveInQueue(i, i + 1))
            assertEquals(original, engine.queueSnapshot().map { it.id }.sorted())
        }
        for (i in 4 downTo 1) {
            assertTrue(engine.moveInQueue(i, i - 1))
            assertEquals(original, engine.queueSnapshot().map { it.id }.sorted())
        }
        assertEquals(listOf("1", "2", "3", "4", "5"), engine.queueSnapshot().map { it.id })
    }

    fun testPauseResumeAndToggle() {
        val engine = engine()
        engine.playQueue(testQueue(2))
        engine.togglePlayPause()
        assertFalse(engine.isPlaying)
        assertFalse(audio.playing)

        // The clock must not move while paused.
        engine.advance(5000)
        assertEquals(0L, engine.state().positionMillis)

        engine.togglePlayPause()
        assertTrue(engine.isPlaying)
        assertTrue(audio.playing)
    }

    fun testSeekClampsToTheTrack() {
        val engine = engine()
        engine.playQueue(testQueue(1))
        engine.seekTo(999_999)
        assertEquals(180_000L, engine.state().positionMillis)
        engine.seekBy(-1000)
        assertEquals(179_000L, engine.state().positionMillis)
        engine.seekTo(-50)
        assertEquals(0L, engine.state().positionMillis)
        assertEquals(0f, engine.state().progress)
    }

    fun testARefusedSeekLeavesTheReportedPositionWhereTheAudioIs() {
        val engine = engine()
        engine.playQueue(testQueue(1))
        engine.seekTo(30_000)
        assertEquals(30_000L, engine.state().positionMillis)

        // A decoder that cannot rewind throws. The position must not move anyway, or the UI shows a
        // time the audio is nowhere near.
        audio.failNextSeekWith = "cannot seek this stream"
        assertFailsWith<AudioSourceException> { engine.seekTo(90_000) }
        assertEquals(30_000L, engine.state().positionMillis)
    }

    fun testProgressTracksTheClock() {
        val engine = engine()
        engine.playQueue(listOf(testTrack("t", durationSeconds = 100)))
        engine.advance(50_000)
        assertEquals(0.5f, engine.state().progress)
        assertEquals("0:50", engine.state().positionLabel)
    }

    fun testThePlatformReportedPositionWins() {
        val engine = engine()
        engine.playQueue(testQueue(2))
        audio.reportedPosition = 42_000
        engine.advance(1000)
        assertEquals(42_000L, engine.state().positionMillis, "a real output position beats accumulated ticks")
    }

    fun testVolumeIsClampedAndForwarded() {
        val engine = engine()
        engine.playQueue(testQueue(1))
        engine.setVolume(2f)
        assertEquals(1f, engine.state().volume)
        assertEquals(1f, audio.volume)

        engine.setVolume(-3f)
        assertEquals(0f, engine.state().volume)
        assertEquals(0f, audio.volume)

        engine.setVolume(0.4f)
        assertEquals(0.4f, engine.state().volume)
    }

    fun testSourceResolverCanOverrideTheStreamUrl() {
        val engine = engine()
        val local = testTrack("local", streamUrl = "https://example.test/never-used.mp3")
        val pcm = AudioSource.Pcm(co.omnimusic.core.audio.PcmFormat(8000, 1, 16), ByteArray(64))
        engine.sourceResolver = { track -> if (track.id == "local") pcm else null }
        engine.playQueue(listOf(local))
        assertEquals(pcm, audio.prepared)
    }

    fun testStopResetsPositionButKeepsTheQueue() {
        val engine = engine()
        engine.playQueue(testQueue(3))
        engine.advance(10_000)
        engine.stop()
        assertEquals(0L, engine.state().positionMillis)
        assertEquals(3, engine.state().queueSize)
        assertNotNull(engine.currentTrack)
    }

    fun testClearQueueLeavesAnEmptyIdleEngine() {
        val engine = engine()
        engine.playQueue(testQueue(3))
        engine.clearQueue()
        assertNull(engine.currentTrack)
        assertEquals(0, engine.state().queueSize)
        assertFalse(engine.isPlaying)
        assertTrue(engine.queueSnapshot().isEmpty())
    }

    fun testEveryStateChangeIsPublished() {
        val engine = engine()
        engine.playQueue(testQueue(2))
        val before = recorder.stateChanges
        assertTrue(before > 0)
        engine.pause()
        assertTrue(recorder.stateChanges > before)
    }

    fun testEmptyStateIsRepresentable() {
        val empty = PlayState()
        assertTrue(empty.isEmpty)
        assertEquals(0f, empty.progress)
        assertEquals("0:00", empty.positionLabel)
        assertFalse(empty.hasNext)
    }
}
