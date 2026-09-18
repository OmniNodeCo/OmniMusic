package co.omnimusic.core.desktop

import co.omnimusic.core.net.ApiError
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The desktop network seam, against a real HTTP server on the loopback interface.
 *
 * Every other test in the project replaces [co.omnimusic.core.net.HttpFetcher] with fixtures, which
 * is right for provider and repository logic but leaves this class — the thing that actually talks
 * to Deezer and LRCLIB — completely unexecuted. `com.sun.net.httpserver` ships with the JRE, so
 * there is no dependency cost to covering it, and no network access needed.
 */
class JdkHttpFetcherTest {

    private var server: HttpServer? = null

    /** Request headers of the last call, so tests can assert on what we send, not just receive. */
    private var lastHeaders: Map<String, List<String>> = emptyMap()
    private var lastMethod: String = ""

    private fun serve(vararg routes: Pair<String, Int>): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        for ((path, status) in routes) {
            http.createContext(path) { exchange -> respond(exchange, status, """{"path":"$path"}""") }
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}"
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        lastHeaders = exchange.requestHeaders.toMap()
        lastMethod = exchange.requestMethod
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun shutdown() {
        server?.stop(0)
        server = null
    }

    fun testReturnsTheStatusAndBodyOfASuccessfulCall() {
        val base = serve("/search" to 200)
        try {
            val response = JdkHttpFetcher().fetch("$base/search")
            assertEquals(200, response.statusCode)
            assertTrue(response.isSuccess)
            assertEquals("""{"path":"/search"}""", response.body)
        } finally {
            shutdown()
        }
    }

    fun testAnErrorStatusIsReturnedRatherThanThrown() {
        val base = serve("/rate-limited" to 503)
        try {
            // Providers decide what an error status means; the fetcher must not pre-empt them by
            // throwing here, or a 503 on one facet would sink the whole screen.
            val response = JdkHttpFetcher().fetch("$base/rate-limited")
            assertEquals(503, response.statusCode)
            assertEquals(false, response.isSuccess)
            assertContains(response.body, "rate-limited")
        } finally {
            shutdown()
        }
    }

    fun testSendsTheJsonAcceptHeaderAndItsOwnUserAgent() {
        val base = serve("/probe" to 200)
        try {
            JdkHttpFetcher().fetch("$base/probe")
            assertEquals("GET", lastMethod)
            assertEquals(listOf("application/json"), lastHeaders["Accept"])
            assertEquals(listOf(JdkHttpFetcher.USER_AGENT), lastHeaders["User-agent"])
        } finally {
            shutdown()
        }
    }

    fun testTheUserAgentIsConfigurable() {
        val base = serve("/probe" to 200)
        try {
            JdkHttpFetcher(userAgent = "OmniMusic-test/1.0").fetch("$base/probe")
            assertEquals(listOf("OmniMusic-test/1.0"), lastHeaders["User-agent"])
        } finally {
            shutdown()
        }
    }

    fun testFollowsRedirects() {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/old") { exchange ->
            exchange.responseHeaders.add("Location", "/new")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        http.createContext("/new") { exchange -> respond(exchange, 200, """{"arrived":true}""") }
        http.start()
        server = http
        try {
            val response = JdkHttpFetcher().fetch("http://127.0.0.1:${http.address.port}/old")
            assertEquals(200, response.statusCode)
            assertContains(response.body, "arrived")
        } finally {
            shutdown()
        }
    }

    fun testAnEmptyBodyIsAnEmptyStringNotNull() {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/empty") { exchange ->
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        http.start()
        server = http
        try {
            val response = JdkHttpFetcher().fetch("http://127.0.0.1:${http.address.port}/empty")
            assertEquals(204, response.statusCode)
            assertEquals("", response.body)
        } finally {
            shutdown()
        }
    }

    fun testARefusedConnectionBecomesAnApiErrorNamingTheUrl() {
        // Take a port that was free a moment ago: nothing is listening on it now.
        val base = serve("/never" to 200)
        shutdown()
        val error = assertFailsWith<ApiError> { JdkHttpFetcher(timeout = Duration.ofSeconds(2)).fetch("$base/never") }
        assertContains(error.message.orEmpty(), base)
        assertEquals("$base/never", error.url)
    }

    fun testASlowServerHitsTheTimeoutInsteadOfHanging() {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/slow") { exchange ->
            Thread.sleep(3_000)
            respond(exchange, 200, """{"too":"late"}""")
        }
        http.start()
        server = http
        try {
            val started = System.nanoTime()
            val error = assertFailsWith<ApiError> {
                JdkHttpFetcher(timeout = Duration.ofMillis(250)).fetch("http://127.0.0.1:${http.address.port}/slow")
            }
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            assertNotNull(error.message)
            // Generous ceiling: the point is that it gives up, not that it gives up precisely.
            assertTrue(elapsedMillis < 2_500, "timeout took ${elapsedMillis}ms")
        } finally {
            shutdown()
        }
    }

    fun testAMalformedUrlIsReportedAsAnError() {
        assertFailsWith<Exception> { JdkHttpFetcher().fetch("http://not a valid url/") }
    }
}
