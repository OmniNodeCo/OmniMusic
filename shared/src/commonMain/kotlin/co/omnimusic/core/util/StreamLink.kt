package co.omnimusic.core.util

/**
 * Reads the lifetime out of a signed stream link.
 *
 * Music APIs do not hand back a plain file: the URL carries a token, and after it expires the CDN
 * refuses the request. Deezer's previews use Akamai-style `hdnea=exp=<epoch seconds>~acl=...~hmac=...`
 * and the window is short — measured on a live search, a preview token was valid for about
 * 15 minutes. That is shorter than a listening session, and far shorter than a saved playlist, so a
 * `streamUrl` read back from disk is essentially always dead and has to be fetched again.
 *
 * This exists so the player can tell "still good, play it" from "ask the provider again" without
 * making a request just to find out.
 */
object StreamLink {

    /** Treat a link that expires within this window as already gone, so playback does not start and then die. */
    const val SAFETY_MARGIN_MILLIS = 30_000L

    /**
     * The token field, not any parameter that happens to contain "exp". Deezer nests it one level
     * down — `hdnea=exp=<seconds>~acl=...` — so `=` is a delimiter too, alongside `?`, `&` and `~`.
     */
    private val EXPIRY = Regex("""[?&~=]exp=(\d+)""")

    /** When [url] stops working, in epoch milliseconds, or null when the link carries no token. */
    fun expiresAtMillis(url: String): Long? =
        EXPIRY.find(url)?.groupValues?.get(1)?.toLongOrNull()?.times(1_000L)

    /**
     * True when [url] is unusable now. A link with no token at all is assumed fine: not every
     * provider signs, and a plain file URL should not be replaced on every play.
     */
    fun isExpired(url: String, nowMillis: Long, marginMillis: Long = SAFETY_MARGIN_MILLIS): Boolean {
        val expiresAt = expiresAtMillis(url) ?: return false
        return nowMillis + marginMillis >= expiresAt
    }
}
