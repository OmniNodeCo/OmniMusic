package co.omnimusic.core.net

/** Raised for any transport or protocol problem talking to a music backend. */
class ApiError(
    message: String,
    val url: String? = null,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(buildString {
    append(message)
    statusCode?.let { append(" (HTTP $it)") }
    url?.let { append(" — $it") }
}, cause)

/**
 * The single seam between the core and the outside world.
 *
 * Every platform supplies one implementation (`java.net.http` on desktop, `HttpURLConnection` on
 * Android) and tests supply a fixture-backed one. Nothing else in `shared` touches the network.
 */
fun interface HttpFetcher {
    fun fetch(url: String): HttpResponse
}

data class HttpResponse(
    val statusCode: Int,
    val body: String,
) {
    val isSuccess: Boolean get() = statusCode in 200..299
}
