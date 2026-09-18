package co.omnimusic.core.repo

import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoryCacheTest {

    /** A clock the test drives by hand. */
    private class FakeClock(var now: Long = 0L) {
        fun read(): Long = now
    }

    fun testValuesComeBackUntilTheyExpire() {
        val clock = FakeClock()
        val cache = MemoryCache(ttlMillis = 1000, clock = clock::read)
        cache.put("a", "value")
        assertEquals("value", cache.get("a"))

        clock.now = 999
        assertEquals("value", cache.get("a"))

        clock.now = 1000
        assertEquals("value", cache.get("a"), "an entry is valid through its TTL")

        clock.now = 1001
        assertNull(cache.get("a"), "and gone one millisecond after it")
        assertEquals(0, cache.size())
    }

    fun testGettingAnExpiredEntryRemovesIt() {
        val clock = FakeClock()
        val cache = MemoryCache(ttlMillis = 10, clock = clock::read)
        cache.put("a", 1)
        clock.now = 11
        assertNull(cache.get("a"))
        assertEquals(0, cache.size())
    }

    fun testCapacityEvictsTheOldestEntry() {
        val cache = MemoryCache(capacity = 2, clock = { 0L })
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertEquals(3, cache.get("c"))
        assertEquals(2, cache.size())
    }

    fun testClearDropsEverything() {
        val cache = MemoryCache(clock = { 0L })
        cache.put("a", 1)
        cache.clear()
        assertNull(cache.get("a"))
    }

    fun testRealClockIsMonotonicAndNonNegative() {
        val clock = MemoryCache.realClock()
        val first = clock()
        val second = clock()
        check(second >= first) { "monotonic clock went backwards: $first -> $second" }
        check(first >= 0)
    }
}
