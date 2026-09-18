package co.omnimusic.core.store

import co.omnimusic.core.player.PersistedPlayback
import co.omnimusic.core.player.RepeatMode
import co.omnimusic.core.testing.RecordingKeyValueStore
import co.omnimusic.core.testing.testQueue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackStateStoreTest {

    private val store = RecordingKeyValueStore()
    private val sessions = PlaybackStateStore(store)

    private fun session() = PersistedPlayback(
        tracks = testQueue(3),
        index = 1,
        positionMillis = 42_000,
        shuffleEnabled = true,
        repeatMode = RepeatMode.ALL,
        volume = 0.4f,
    )

    fun testRoundTripsTheWholeSession() {
        sessions.save(session())
        val restored = sessions.restore()!!

        assertEquals(3, restored.tracks.size)
        assertEquals(listOf("1", "2", "3"), restored.tracks.map { it.id })
        assertEquals(1, restored.index)
        assertEquals(42_000L, restored.positionMillis)
        assertTrue(restored.shuffleEnabled)
        assertEquals(RepeatMode.ALL, restored.repeatMode)
        assertEquals(0.4f, restored.volume)
        assertEquals("2", restored.currentTrack?.id)
    }

    fun testSurvivesARestart() {
        sessions.save(session())
        val afterRestart = PlaybackStateStore(RecordingKeyValueStore(store.rawSnapshot()))
        assertEquals(42_000L, afterRestart.restore()?.positionMillis)
    }

    fun testNothingSavedMeansNothingRestored() {
        assertNull(sessions.restore())
    }

    fun testSavingAnEmptyQueueClearsTheStore() {
        sessions.save(session())
        sessions.save(session().copy(tracks = emptyList()))
        assertNull(sessions.restore())
        assertTrue(store.rawSnapshot().isEmpty())
    }

    fun testClear() {
        sessions.save(session())
        sessions.clear()
        assertNull(sessions.restore())
    }

    fun testCorruptDataIsIgnoredRatherThanThrown() {
        store.putString(PlaybackStateStore.KEY, "{not json")
        assertNull(sessions.restore())
    }

    fun testAnUnknownRepeatModeFallsBackToOff() {
        sessions.save(session().copy(repeatMode = RepeatMode.ONE))
        store.putString(
            PlaybackStateStore.KEY,
            store.getString(PlaybackStateStore.KEY)!!.replace("\"ONE\"", "\"SIDE_B\""),
        )
        assertEquals(RepeatMode.OFF, sessions.restore()?.repeatMode)
    }

    fun testAnOutOfRangeIndexIsClamped() {
        sessions.save(session().copy(index = 9))
        assertEquals(2, sessions.restore()?.index)
    }

    fun testAnUnplayableQueueIsNotRestored() {
        store.putString(PlaybackStateStore.KEY, """{"tracks":[],"index":0}""")
        assertNull(sessions.restore())
    }
}
