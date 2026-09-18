package co.omnimusic.core.desktop

import co.omnimusic.core.net.ApiError
import co.omnimusic.core.net.HttpFetcher
import co.omnimusic.core.net.HttpResponse
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration

/** Desktop [HttpFetcher] on top of `java.net.http`. No third-party HTTP client needed. */
class JdkHttpFetcher(
    private val timeout: Duration = Duration.ofSeconds(20),
    private val userAgent: String = USER_AGENT,
) : HttpFetcher {

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(timeout)
        .build()

    override fun fetch(url: String): HttpResponse {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .GET()
            .build()
        return try {
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            HttpResponse(response.statusCode(), response.body() ?: "")
        } catch (e: IOException) {
            throw ApiError(e.message ?: "network error", url, cause = e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ApiError("request interrupted", url, cause = e)
        }
    }

    companion object {
        const val USER_AGENT = "OmniMusic/1.0 (+https://github.com/OmniNodeCo/OmniMusic)"
    }
}
