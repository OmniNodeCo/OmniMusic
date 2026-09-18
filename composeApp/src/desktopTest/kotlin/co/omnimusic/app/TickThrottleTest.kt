package co.omnimusic.app

import kotlin.test.assertEquals

/**
 * The cadence control between the frame clock and the transport.
 *
 * The property that matters most is the absence of drift: whatever goes in must come back out,
 * because the released total is what the engine adds to the track position. A throttle that
 * returned a fixed interval each time would silently lose or gain time whenever frames arrived
 * unevenly, and the progress bar would wander away from the audio.
 */
class TickThrottleTest {

    fun testNothingIsDueBeforeTheInterval() {
        val throttle = TickThrottle(250)
        assertEquals(0L, throttle.advance(16))
        assertEquals(0L, throttle.advance(16))
        assertEquals(0L, throttle.advance(16))
        assertEquals(48L, throttle.pending)
    }

    fun testReleasesTheWholeAccumulationNotJustTheInterval() {
        val throttle = TickThrottle(250)
        // Build up 200 ms without crossing the interval.
        repeat(10) { assertEquals(0L, throttle.advance(20)) }
        assertEquals(200L, throttle.pending)
        // A 120 ms frame crosses it. The release is the full 320, not 250 with 70 left behind —
        // leaving a remainder here is exactly how a throttled transport drifts behind the audio.
        assertEquals(320L, throttle.advance(120))
        assertEquals(0L, throttle.pending)
    }

    fun testReleaseResetsTheAccumulator() {
        val throttle = TickThrottle(100)
        assertEquals(120L, throttle.advance(120))
        assertEquals(0L, throttle.pending)
        assertEquals(0L, throttle.advance(50))
        assertEquals(100L, throttle.advance(50))
    }

    fun testExactlyTheIntervalIsDue() {
        val throttle = TickThrottle(100)
        assertEquals(100L, throttle.advance(100))
    }

    fun testALateFrameContributesItsRealLength() {
        val throttle = TickThrottle(250)
        assertEquals(0L, throttle.advance(200))
        // A 900 ms stall (a window being dragged, a GC pause) must not be rounded to the interval.
        assertEquals(1_100L, throttle.advance(900))
        assertEquals(0L, throttle.pending)
    }

    fun testNothingIsLostOverManyUnevenFrames() {
        val throttle = TickThrottle(250)
        // A jagged frame pattern: some short, some long, totalling a known amount.
        val frames = listOf(8L, 33L, 16L, 400L, 12L, 17L, 250L, 5L, 900L, 24L)
        var released = 0L
        for (frame in frames) released += throttle.advance(frame)
        released += throttle.pending
        assertEquals(frames.sum(), released)
    }

    fun testZeroAndNegativeDeltasAreIgnored() {
        val throttle = TickThrottle(100)
        assertEquals(0L, throttle.advance(0))
        assertEquals(0L, throttle.advance(-500))
        assertEquals(0L, throttle.pending)
        assertEquals(100L, throttle.advance(100))
    }

    fun testResetDropsTheRemainder() {
        val throttle = TickThrottle(250)
        throttle.advance(200)
        throttle.reset()
        assertEquals(0L, throttle.pending)
        // After a reset the next release starts from zero, so a stale gap is not folded in.
        assertEquals(0L, throttle.advance(100))
        assertEquals(250L, throttle.advance(150))
    }

    fun testAQuarterSecondCadenceOverASimulatedMinute() {
        val throttle = TickThrottle(250)
        var ticks = 0
        var total = 0L
        // 60 s at 60 fps.
        repeat(3_600) {
            val due = throttle.advance(16)
            if (due > 0) {
                ticks++
                total += due
            }
        }
        // ~240 releases rather than 3600, and the whole minute accounted for.
        assertEquals(true, ticks in 220..250, "expected ~240 ticks, got $ticks")
        assertEquals(57_600L, total + throttle.pending)
    }
}
