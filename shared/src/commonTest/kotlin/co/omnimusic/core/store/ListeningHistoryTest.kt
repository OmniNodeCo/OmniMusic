package co.omnimusic.core.store

import co.omnimusic.core.testing.RecordingKeyValueStore
import co.omnimusic.core.testing.testTrack
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListeningHistoryTest {

    private val store = RecordingKeyValueStore()

    fun testPlaysAccumulateAndRecencyIsPreserved() {
        val history = ListeningHistory(store)
        history.recordPlay(testTrack("1", artistName = "Daft Punk"), playedAt = 1000)
        history.recordPlay(testTrack("2", artistName = "Daft Punk"), playedAt = 2000)
        history.recordPlay(testTrack("1", artistName = "Daft Punk"), playedAt = 3000)

        val snapshot = history.snapshot()
        assertEquals(3, snapshot.totalPlays)
        assertEquals(2, snapshot.distinctTracks)
        assertEquals(listOf("1", "2"), snapshot.recent.map { it.trackId })
        assertEquals(2, history.playsFor("1"))
        assertEquals(1, history.playsFor("2"))
        assertEquals(0, history.playsFor("never"))
    }

    fun testTopTracksAndArtists() {
        val history = ListeningHistory(store)
        repeat(3) { history.recordPlay(testTrack("1", artistName = "Daft Punk"), playedAt = it.toLong()) }
        repeat(2) { history.recordPlay(testTrack("2", artistName = "The Strokes"), playedAt = 10L + it) }
        history.recordPlay(testTrack("3", artistName = "Daft Punk"), playedAt = 99)

        assertEquals(listOf("1", "2", "3"), history.topTracks().map { it.trackId })
        assertEquals(listOf("Daft Punk" to 4, "The Strokes" to 2), history.topArtists())
    }

    fun testRecentIsBoundedByTheLimit() {
        val history = ListeningHistory(store)
        repeat(30) { history.recordPlay(testTrack("t$it"), playedAt = it.toLong()) }
        assertEquals(5, history.recent(limit = 5).size)
        assertEquals(30, history.snapshot().distinctTracks)
    }

    fun testOldEntriesAreEvicted() {
        val history = ListeningHistory(store, maxEntries = 3)
        repeat(5) { history.recordPlay(testTrack("t$it"), playedAt = it.toLong()) }
        val snapshot = history.snapshot()
        assertEquals(3, snapshot.distinctTracks)
        assertEquals(listOf("t4", "t3", "t2"), snapshot.recent.map { it.trackId })
    }

    fun testHistorySurvivesARestart() {
        ListeningHistory(store).recordPlay(testTrack("1", title = "One More Time"), playedAt = 42)
        val reloaded = ListeningHistory(RecordingKeyValueStore(store.rawSnapshot()))
        assertEquals(1, reloaded.playsFor("1"))
        assertEquals("One More Time", reloaded.recent()[0].title)
    }

    fun testClearEmptiesEverything() {
        val history = ListeningHistory(store)
        history.recordPlay(testTrack("1"), playedAt = 1)
        history.clear()
        assertEquals(0, history.snapshot().totalPlays)
        assertTrue(store.rawSnapshot().isEmpty())
    }

    fun testCorruptHistoryStartsEmptyInsteadOfCrashing() {
        val seeded = mapOf(ListeningHistory.STORE_KEY to "{not json")
        val history = ListeningHistory(RecordingKeyValueStore(seeded))
        assertEquals(0, history.snapshot().totalPlays)
        history.recordPlay(testTrack("1"), playedAt = 1)
        assertEquals(1, history.snapshot().totalPlays)
    }
}
