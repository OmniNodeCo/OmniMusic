package co.omnimusic.core.testing

import co.omnimusic.core.net.HttpFetcher
import co.omnimusic.core.net.HttpResponse
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Serves the recorded Deezer responses in `shared/src/desktopTest/fixtures` instead of hitting the
 * network.
 *
 * Routing is by URL path, and a path can be given several fixtures in sequence to exercise
 * pagination. [failPaths] and [statusCodeOverride] simulate the two failure modes the repository
 * has to cope with: a 5xx on one facet, and a hard outage.
 */
class FixtureHttpFetcher(fixtureDir: Path = defaultFixtureDir()) : HttpFetcher {

    private val fixtures: Path = fixtureDir
    private val routes = mutableMapOf<String, MutableList<String>>()
    private val failPaths = mutableSetOf<String>()

    /** Every URL requested, in order. */
    val requested = mutableListOf<String>()

    var statusCodeOverride: Int? = null

    fun route(path: String, vararg fixtureNames: String): FixtureHttpFetcher {
        routes[path] = fixtureNames.toMutableList()
        return this
    }

    fun fail(path: String): FixtureHttpFetcher {
        failPaths += path
        return this
    }

    fun requestsTo(path: String): Int = requested.count { URI.create(it).path == path }

    fun lastRequest(): String = requested.last()

    override fun fetch(url: String): HttpResponse {
        requested += url
        val path = URI.create(url).path
        statusCodeOverride?.let { return HttpResponse(it, "{}") }
        if (path in failPaths) return HttpResponse(503, """{"error":"simulated outage"}""")
        val queue = routes[path] ?: error("no fixture route registered for '$path' (url=$url)")
        val name = if (queue.size == 1) queue.first() else queue.removeAt(0)
        val file = fixtures.resolve(name)
        check(Files.exists(file)) { "missing fixture ${file.toAbsolutePath()}" }
        return HttpResponse(200, Files.readString(file))
    }

    companion object {
        fun defaultFixtureDir(): Path {
            val configured = System.getProperty("omnimusic.fixtures")
            if (configured != null) return Path.of(configured)
            var candidate = Path.of(System.getProperty("user.dir"))
            repeat(5) {
                val found = candidate.resolve("shared/src/desktopTest/fixtures")
                if (Files.isDirectory(found)) return found
                candidate = candidate.parent ?: return Path.of("shared/src/desktopTest/fixtures")
            }
            return Path.of("shared/src/desktopTest/fixtures")
        }

        /** The full set of routes the provider can hit, pointed at the happy-path fixtures. */
        fun fullyRouted(fetcher: FixtureHttpFetcher = FixtureHttpFetcher()): FixtureHttpFetcher = fetcher
            .route("/search", "search_tracks.json", "search_tracks_page2.json")
            .route("/search/album", "search_albums.json")
            .route("/search/artist", "search_artists.json")
            .route("/chart/0/tracks", "chart_tracks.json")
            .route("/chart/0/albums", "chart_albums.json")
            .route("/genre", "genres.json")
            .route("/album/302127", "album_detail.json")
            .route("/album/6575789", "album_no_tracks.json")
            .route("/album/6575789/tracks", "album_tracks.json")
            .route("/artist/27", "artist_detail.json")
            .route("/artist/27/top", "artist_top.json")
            .route("/track/2868828162", "track_detail.json")
            .route("/track/3135553/radio", "radio.json")
            .route("/playlist/908622995", "playlist_detail.json")
            .route("/api/get", "lrclib_synced.json")
    }
}
