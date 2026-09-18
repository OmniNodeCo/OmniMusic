package co.omnimusic.core.repo

import co.omnimusic.core.util.Guard
import co.omnimusic.core.util.NoGuard
import kotlin.time.TimeSource

/**
 * A tiny TTL cache.
 *
 * Music metadata is exactly the kind of data worth holding for a few minutes: it changes rarely,
 * and re-fetching it on every screen transition is both slow and rude. There is no eviction policy
 * beyond "the oldest entry goes once [capacity] is reached", which is plenty for a catalog browser.
 *
 * Time comes from [TimeSource.Monotonic] so the cache never depends on the wall clock (or on a
 * platform API), and tests can inject a fake.
 */
class MemoryCache(
    private val capacity: Int = 128,
    private val ttlMillis: Long = 5 * 60 * 1000L,
    private val clock: () -> Long = realClock(),
    private val guard: Guard = NoGuard,
) {

    private class Entry(val value: Any, val expiresAt: Long)

    private val entries = LinkedHashMap<String, Entry>()

    fun get(key: String): Any? = guard.exclusive {
        val entry = entries[key] ?: return@exclusive null
        if (entry.expiresAt < clock()) {
            entries.remove(key)
            null
        } else {
            entry.value
        }
    }

    fun put(key: String, value: Any) = guard.exclusive {
        entries[key] = Entry(value, clock() + ttlMillis)
        while (entries.size > capacity) {
            entries.remove(entries.keys.iterator().next())
        }
    }

    fun clear() = guard.exclusive { entries.clear() }

    fun size(): Int = guard.exclusive { entries.size }

    companion object {
        fun realClock(): () -> Long {
            val origin = TimeSource.Monotonic.markNow()
            return { origin.elapsedNow().inWholeMilliseconds }
        }
    }
}
