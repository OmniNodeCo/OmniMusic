package co.omnimusic.core.util

/**
 * Mutual exclusion without pulling a platform lock into common code.
 *
 * `commonMain` cannot use `kotlin.jvm.Synchronized` (or a `ReentrantLock`) and stay a valid
 * multiplatform source set, so anything that needs a critical section takes a [Guard]. The desktop
 * and Android layers supply a real one; tests and single-threaded callers use [NoGuard].
 */
interface Guard {
    fun <T> exclusive(block: () -> T): T
}

object NoGuard : Guard {
    override fun <T> exclusive(block: () -> T): T = block()
}
