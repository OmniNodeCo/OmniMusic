package co.omnimusic.tools.demo

import co.omnimusic.core.audio.AudioOutput
import co.omnimusic.core.audio.AudioSource
import co.omnimusic.core.audio.WavCodec
import co.omnimusic.core.desktop.JavaSoundAudioOutput
import co.omnimusic.core.desktop.JdkHttpFetcher
import co.omnimusic.core.desktop.JvmKeyValueStore
import co.omnimusic.core.lyrics.LrcLibProvider
import co.omnimusic.core.lyrics.LyricsLibrary
import co.omnimusic.core.model.Artist
import co.omnimusic.core.model.Load
import co.omnimusic.core.model.Track
import co.omnimusic.core.player.PlayState
import co.omnimusic.core.player.PlaybackEngine
import co.omnimusic.core.player.PersistedPlayback
import co.omnimusic.core.player.PlaybackListener
import co.omnimusic.core.net.HttpFetcher
import co.omnimusic.core.provider.MusicProvider
import co.omnimusic.core.provider.deezer.DeezerProvider
import co.omnimusic.core.repo.MusicRepository
import co.omnimusic.core.store.ListeningHistory
import co.omnimusic.core.store.PlaybackStateStore
import co.omnimusic.core.store.PlaylistStore
import co.omnimusic.core.testing.FixtureHttpFetcher
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.sin
import kotlin.system.exitProcess

/**
 * A command-line front end for the shared core.
 *
 * Two jobs:
 *  1. prove the core works end to end on a real JVM — network or replayed fixtures, real stores on
 *     disk, the real playback engine driving a real audio output;
 *  2. give a human a way to poke at the API without building the Compose app first.
 *
 * It is a dev tool, not the product: the shipping UIs are the Compose desktop window and the
 * Android activity.
 */
object DemoMain {

    @JvmStatic
    fun main(args: Array<String>) {
        val options = Options.parse(args)
        if (options.command.isEmpty()) {
            printUsage()
            exitProcess(if (args.isEmpty()) 0 else 2)
        }

        val fetcher = options.fetcher()
        val repository = MusicRepository(DeezerProvider(fetcher))
        val lyrics = LyricsLibrary(LrcLibProvider(fetcher))
        val store = JvmKeyValueStore(options.storePath)
        val playlists = PlaylistStore(store)
        val history = ListeningHistory(store)
        val sessions = PlaybackStateStore(store)

        when (options.command[0]) {
            "home" -> home(repository)
            "search" -> search(repository, options.rest())
            "album" -> album(repository, options.rest())
            "artist" -> artist(repository, options.rest())
            "radio" -> radio(repository, options.rest())
            "genres" -> genres(repository)
            "playlists" -> playlists(playlists)
            "lyrics" -> lyrics(lyrics, options.rest())
            "session" -> session(repository, playlists, history, sessions, options)
            else -> {
                System.err.println("unknown command '${options.command[0]}'")
                printUsage()
                exitProcess(2)
            }
        }
    }

    // ----------------------------------------------------------------------------------------
    // Commands
    // ----------------------------------------------------------------------------------------

    private fun home(repository: MusicRepository) {
        heading("Home — ${repository.providerName}")
        when (val result = repository.homeFeed(limit = 8)) {
            is Load.Failure -> fail(result.error)
            is Load.Success -> {
                section("Trending tracks")
                result.value.trendingTracks.forEachIndexed { i, track -> println(trackLine(i + 1, track)) }
                section("Albums")
                result.value.newAlbums.forEachIndexed { i, album ->
                    println("  ${i + 1}. ${album.title} — ${album.artistName} (${album.releaseDate ?: "n/a"})")
                }
                section("Genres")
                println("  " + result.value.genres.joinToString(", ") { it.name })
            }
        }
    }

    private fun search(repository: MusicRepository, args: List<String>) {
        val query = args.joinToString(" ").ifBlank {
            System.err.println("usage: search <query>")
            exitProcess(2)
        }
        heading("Search — \"$query\"")
        when (val result = repository.search(query, limit = 5)) {
            is Load.Failure -> fail(result.error)
            is Load.Success -> {
                val search = result.value
                section("Tracks (${search.tracks.total} total)")
                search.tracks.items.forEachIndexed { i, track -> println(trackLine(i + 1, track)) }
                section("Albums")
                search.albums.items.forEachIndexed { i, album -> println("  ${i + 1}. ${album.title} — ${album.artistName}") }
                section("Artists")
                search.artists.items.forEachIndexed { i, artist -> println("  ${i + 1}. ${artist.name}") }
                if (search.isEmpty) println("  no results")
            }
        }
    }

    private fun album(repository: MusicRepository, args: List<String>) {
        val id = args.firstOrNull() ?: missing("album <id>")
        heading("Album $id")
        when (val result = repository.album(id)) {
            is Load.Failure -> fail(result.error)
            is Load.Success -> {
                val (album, tracks) = result.value
                println("  ${album.title} — ${album.artistName}  [${album.trackCount} tracks, ${album.releaseDate ?: "n/a"}]")
                tracks.forEachIndexed { i, track -> println(trackLine(i + 1, track)) }
            }
        }
    }

    private fun artist(repository: MusicRepository, args: List<String>) {
        val id = args.firstOrNull() ?: missing("artist <id>")
        heading("Artist $id")
        when (val result = repository.artist(id)) {
            is Load.Failure -> fail(result.error)
            is Load.Success -> {
                val (artist, top) = result.value
                println("  ${artist.name}")
                top.forEachIndexed { i, track -> println(trackLine(i + 1, track)) }
            }
        }
    }

    private fun radio(repository: MusicRepository, args: List<String>) {
        val id = args.firstOrNull() ?: missing("radio <trackId>")
        heading("Radio from track $id")
        when (val result = repository.radio(id, limit = 8)) {
            is Load.Failure -> fail(result.error)
            is Load.Success -> result.value.forEachIndexed { i, track -> println(trackLine(i + 1, track)) }
        }
    }

    private fun genres(repository: MusicRepository) {
        heading("Genres")
        when (val result = repository.genres()) {
            is Load.Failure -> fail(result.error)
            is Load.Success -> result.value.forEachIndexed { i, genre -> println("  ${i + 1}. ${genre.name}") }
        }
    }

    private fun playlists(store: PlaylistStore) {
        heading("Saved playlists")
        val all = store.all()
        if (all.isEmpty()) println("  (none yet — run the `session` command to create one)")
        all.forEach { println("  ${it.name}: ${it.tracks.size} tracks, ${it.durationLabel}") }
    }

    private fun lyrics(library: LyricsLibrary, args: List<String>) {
        if (args.size < 2) missing("lyrics <artist> <track>")
        val artist = args[0]
        val title = args.drop(1).joinToString(" ")
        heading("Lyrics — $artist / $title")
        val track = Track(id = "lookup", title = title, artist = Artist(id = "", name = artist))
        when (val result = library.forTrack(track)) {
            is Load.Failure -> fail(result.error)
            is Load.Success -> {
                val found = result.value
                if (found == null) {
                    println("  no lyrics for that track")
                    return
                }
                println("  ${found.trackName} — ${found.artistName} [${if (found.isSynced) "synced" else "plain"}]")
                if (found.isSynced) {
                    found.lines.take(15).forEach { line ->
                        val stamp = formatMillis(line.startMillis)
                        println("  $stamp  ${line.text.ifBlank { "·" }}")
                    }
                    if (found.lines.size > 15) println("  … ${found.lines.size - 15} more lines")
                } else {
                    found.plainLines.take(10).forEach { println("    $it") }
                }
            }
        }
    }

    /**
     * The full pipeline: search, save a playlist, drive the playback engine, persist history.
     *
     * With `--silent` (or on a machine with no sound card) the engine drives a mute output, so the
     * transport state machine still runs and prints, but nothing is sent to a device.
     */
    private fun session(
        repository: MusicRepository,
        playlists: PlaylistStore,
        history: ListeningHistory,
        sessions: PlaybackStateStore,
        options: Options,
    ) {
        val query = options.rest().ifEmpty { listOf("daft punk") }.joinToString(" ")
        heading("Session — searching \"$query\"")

        val tracks = when (val result = repository.search(query, limit = 4)) {
            is Load.Failure -> fail(result.error)
            is Load.Success -> result.value.tracks.items
        }
        if (tracks.isEmpty()) {
            System.err.println("nothing to play")
            exitProcess(1)
        }
        tracks.forEachIndexed { i, track -> println(trackLine(i + 1, track)) }

        // --- library -------------------------------------------------------------------------
        val playlist = playlists.create("Demo mix — $query")
        playlists.addTracks(playlist.id, tracks)
        section("Saved playlist")
        println("  ${playlist.name}: ${playlists.byId(playlist.id)!!.tracks.size} tracks, " +
            playlists.byId(playlist.id)!!.durationLabel)
        println("  stored in ${options.storePath}")

        // --- audio ---------------------------------------------------------------------------
        val tone = synthesize(seconds = 2, frequency = 440.0)
        val decoded = WavCodec.decode(WavCodec.encode(tone, sampleRate = SAMPLE_RATE, channels = 1))
        section("Local audio path")
        println("  generated ${decoded.durationMillis} ms of ${decoded.format.sampleRate} Hz PCM, " +
            "${decoded.frameCount} frames")

        val audio = chooseOutput(options)
        val engine = PlaybackEngine(audio)
        engine.sourceResolver = { AudioSource.Pcm(decoded.format, decoded.samples) }
        engine.listener = object : PlaybackListener {
            override fun onTrackStarted(track: Track) = println("  ▸ started  ${track.title} — ${track.artistName}")
            override fun onTrackFailed(track: Track, reason: String) = println("  ✗ failed   ${track.title}: $reason")
            override fun onQueueFinished() = println("  ■ queue finished")
        }

        section("Transport")
        engine.playQueue(tracks, startIndex = 0)
        printState(engine)

        // The host is responsible for ticking the clock; a UI would do this per frame.
        repeat(6) { engine.advance(1000) }
        printState(engine)

        println("  → next")
        engine.next()
        printState(engine)

        println("  → shuffle on")
        engine.setShuffle(true)
        println("    play order: ${engine.playOrderSnapshot().joinToString(" → ") { it.title }}")

        println("  → seek to 1:30")
        engine.seekTo(90_000)
        printState(engine)

        println("  → repeat ${engine.cycleRepeatMode().label}")
        println("  → volume 0.35")
        engine.setVolume(0.35f)
        printState(engine)

        engine.pause()
        printState(engine)

        // --- history -------------------------------------------------------------------------
        repeat(3) { history.recordPlay(tracks[0], playedAt = 1_000L * it) }
        history.recordPlay(tracks[1], playedAt = 9_999)
        section("Listening history")
        history.topTracks(3).forEach { println("  ${it.plays}×  ${it.title} — ${it.artistName}") }
        println("  top artists: " + history.topArtists(3).joinToString(", ") { "${it.first} (${it.second})" })

        section("Queue")
        engine.queueSnapshot().forEachIndexed { i, track ->
            val marker = if (i == engine.state().queueIndex) "▸" else " "
            println("  $marker ${i + 1}. ${track.title} — ${track.artistName}  [${track.durationLabel}]")
        }

        // Snapshot *before* stopping: stop() rewinds, and the point of persisting is to come back
        // to where the listener actually was.
        val snapshot: PersistedPlayback = engine.snapshotForPersistence()
        engine.stop()
        audio.stop()

        // --- resume --------------------------------------------------------------------------
        sessions.save(snapshot)
        val restored = PlaybackEngine(SilentAudioOutput)
        val saved = sessions.restore()
        if (saved != null) {
            restored.restore(
                tracks = saved.tracks,
                index = saved.index,
                positionMillis = saved.positionMillis,
                shuffleEnabled = saved.shuffleEnabled,
                repeatMode = saved.repeatMode,
                volume = saved.volume,
                autoplay = false,
            )
        }
        section("Resume after restart")
        val resumed = restored.state()
        println("  restored ${resumed.queueSize} tracks at ${resumed.queueIndex + 1}: " +
            "${resumed.track?.title ?: "-"} @ ${resumed.positionLabel} " +
            "(${if (resumed.isPlaying) "playing" else "paused"}, ${resumed.repeatMode.label})")

        println()
        println("done.")
    }

    // ----------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------

    private fun chooseOutput(options: Options): AudioOutput {
        if (options.silent) {
            println("  (silent mode: engine drives a mute output)")
            return SilentAudioOutput
        }
        return try {
            JavaSoundAudioOutput()
        } catch (e: Exception) {
            println("  (no usable audio device: ${e.message})")
            SilentAudioOutput
        }
    }

    /**
     * A 440 Hz sine, faded at both ends so it does not click. Used to prove the local PCM path —
     * encode, decode, hand to the platform output — without shipping a binary asset.
     */
    private fun synthesize(seconds: Int, frequency: Double): ShortArray {
        val count = SAMPLE_RATE * seconds
        val fade = SAMPLE_RATE / 20
        return ShortArray(count) { index ->
            val envelope = minOf(1.0, minOf(index, count - index).toDouble() / fade).coerceIn(0.0, 1.0)
            val sample = sin(2.0 * PI * frequency * index / SAMPLE_RATE) * envelope * 0.4
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun printState(engine: PlaybackEngine) {
        val state: PlayState = engine.state()
        val track = state.track ?: return
        val playing = if (state.isPlaying) "playing" else "paused "
        println("    [$playing] ${state.positionLabel}/${state.durationLabel} " +
            "(${(state.progress * 100).toInt()}%) ${track.title} — ${track.artistName} " +
            "[${state.queueIndex + 1}/${state.queueSize}] vol=${state.volume}")
    }

    private fun trackLine(index: Int, track: Track): String {
        val playable = if (track.streamUrl != null) "" else "  (no preview)"
        val flag = if (track.explicit) " E" else ""
        return "  $index. ${track.title}$flag — ${track.artistName} [${track.album?.title ?: "?"}] ${track.durationLabel}$playable"
    }

    private fun formatMillis(millis: Long): String {
        val totalSeconds = millis / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private fun heading(text: String) {
        println()
        println(text)
        println("─".repeat(text.length.coerceAtLeast(24)))
    }

    private fun section(text: String) {
        println()
        println("  $text")
    }

    private fun fail(message: String): Nothing {
        System.err.println("  request failed: $message")
        exitProcess(1)
    }

    private fun missing(usage: String): Nothing {
        System.err.println("usage: $usage")
        exitProcess(2)
    }

    private fun printUsage() = printDemoUsage()

    private const val SAMPLE_RATE = 8000

    /** Mute stand-in for machines without a sound card; the engine still runs normally. */
    private object SilentAudioOutput : AudioOutput {
        private var ready = false
        override fun prepare(source: AudioSource) {
            ready = true
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun stop() {
            ready = false
        }

        override fun seekTo(positionMillis: Long) = Unit
        override fun positionMillis(): Long = -1
        override fun setVolume(volume: Float) = Unit
        override fun isReady(): Boolean = ready
    }

    private class Options(
        val command: List<String>,
        val fixtures: Path?,
        val storePath: Path,
        val silent: Boolean,
    ) {
        fun rest(): List<String> = command.drop(1)

        /** One fetcher serves both the catalog and the lyrics lookup. */
        fun fetcher(): HttpFetcher = if (fixtures != null) {
            FixtureHttpFetcher.fullyRouted(FixtureHttpFetcher(fixtures))
        } else {
            JdkHttpFetcher()
        }

        fun provider(): MusicProvider = DeezerProvider(fetcher())

        companion object {
            fun parse(args: Array<String>): Options {
                var fixtures: Path? = null
                var store: Path = Path.of(System.getProperty("java.io.tmpdir"), "omnimusic-demo", "store.json")
                var silent = false
                val rest = mutableListOf<String>()
                val iterator = args.iterator()
                while (iterator.hasNext()) {
                    when (val arg = iterator.next()) {
                        "--fixtures" -> fixtures = Path.of(iterator.next())
                        "--live" -> fixtures = null
                        "--store" -> store = Path.of(iterator.next())
                        "--silent" -> silent = true
                        "--help", "-h" -> {
                            printUsageStatic()
                            exitProcess(0)
                        }

                        else -> rest += arg
                    }
                }
                return Options(
                    command = rest,
                    // Replay mode is opt-in; the default path for a human is the live API.
                    fixtures = fixtures ?: System.getProperty("omnimusic.demo.fixtures")?.let(Path::of),
                    storePath = store,
                    silent = silent,
                )
            }

            private fun printUsageStatic() = printDemoUsage()
        }
    }
}


/** File-private so both the command dispatcher and the option parser can reach it. */
private fun printDemoUsage() {
    println(
        """
        OmniMusic core demo

        usage: run-demo.sh [--fixtures DIR | --live] [--store PATH] [--silent] <command> [args]

        commands:
          home                  trending tracks, albums and genres
          search <query>        search tracks, albums and artists
          album <id>            album metadata and track list
          artist <id>           artist and their top tracks
          radio <trackId>       tracks related to a seed track
          genres                every browsable genre
          playlists             playlists saved on this machine
          session [query]       the whole pipeline: search, playlist, playback, history

        --fixtures DIR   replay recorded API responses from DIR
        --live           hit the real API (the default)
        --store PATH     where playlists and history are written
        --silent         drive a mute audio output instead of the sound card
        """.trimIndent()
    )
}
