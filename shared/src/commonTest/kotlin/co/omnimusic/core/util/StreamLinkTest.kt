package co.omnimusic.core.util

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading the lifetime out of a signed stream link.
 *
 * The URL shape here is not invented: it is Deezer's `hdnea` token, copied from a recorded response.
 * Getting the parse wrong in either direction is a user-visible bug — too strict and every play
 * costs a wasted request, too loose and playback starts on a link that dies a few seconds in.
 */
class StreamLinkTest {

    private val signed =
        "https://cdnt-preview.dzcdn.net/api/1/1/a/2/f/0/a2f1f064d818d52e875bf2f18ec15b51.mp3" +
            "?hdnea=exp=1789942732~acl=/api/1/1/a/2/f/0/a2f1f064d818d52e875bf2f18ec15b51.mp3*" +
            "~data=user_id=0,application_id=42~hmac=9c6bc99feb9da4678f104dfd78eb0871"

    private val expiry = 1_789_942_732_000L

    fun testReadsTheExpiryOutOfASignedLink() {
        assertEquals(expiry, StreamLink.expiresAtMillis(signed))
    }

    fun testAnUnsignedLinkReportsNoExpiry() {
        assertNull(StreamLink.expiresAtMillis("https://example.test/t1.mp3"))
    }

    fun testALinkIsLiveUntilItsTokenRunsOut() {
        assertFalse(StreamLink.isExpired(signed, expiry - 60_000))
        assertTrue(StreamLink.isExpired(signed, expiry))
        assertTrue(StreamLink.isExpired(signed, expiry + 1))
    }

    fun testTheSafetyMarginRetiresALinkBeforeItActuallyDies() {
        // A link with seconds left would play and then cut out; better to ask for a new one now.
        assertTrue(StreamLink.isExpired(signed, expiry - 5_000))
        assertFalse(StreamLink.isExpired(signed, expiry - 5_000, marginMillis = 0))
    }

    fun testAnUnsignedLinkIsNeverTreatedAsExpired() {
        // Not every provider signs, and a plain file URL should not be re-fetched on every play.
        assertFalse(StreamLink.isExpired("https://example.test/t1.mp3", Long.MAX_VALUE / 2))
    }

    fun testSomethingThatMerelyContainsExpIsNotAToken() {
        assertNull(StreamLink.expiresAtMillis("https://example.test/export=1.mp3"))
        assertNull(StreamLink.expiresAtMillis("https://example.test/t1.mp3?expires=never"))
    }
}
