package co.omnimusic.app

/**
 * Turns a per-frame clock into a fixed-cadence one.
 *
 * The engine is tick-driven: the host measures elapsed time and calls [AppModel.tick], which moves
 * the transport and republishes player state. Driven straight off `withFrameNanos` that happens
 * sixty times a second, and because `positionMillis` changes every time, every write invalidates
 * the composables reading it — a progress bar that only needs a few updates a second recomposing
 * the mini player and the now-playing panel at display rate.
 *
 * So the host keeps calling every frame, but only the accumulated time is released here once
 * [intervalMillis] has passed. The released amount is the actual accumulation rather than the
 * interval, which is what keeps the transport from drifting: a frame that arrives late contributes
 * its real length to the next release.
 *
 * Pure and allocation-free so the cadence can be tested without composing anything.
 */
class TickThrottle(private val intervalMillis: Long) {

    private var pendingMillis = 0L

    /**
     * Adds [deltaMillis] to the pending time and returns what is due now, or 0 when nothing is.
     * A non-zero result is always the whole accumulation, never just [intervalMillis].
     */
    fun advance(deltaMillis: Long): Long {
        if (deltaMillis <= 0) return 0
        pendingMillis += deltaMillis
        if (pendingMillis < intervalMillis) return 0
        val due = pendingMillis
        pendingMillis = 0
        return due
    }

    /**
     * Drops any part-frame remainder. Called when the clock restarts — a resumed composition, or a
     * frame timestamp that went backwards — so a stale gap is not added to the next release.
     */
    fun reset() {
        pendingMillis = 0
    }

    /** Time accumulated but not yet released. */
    val pending: Long get() = pendingMillis
}
