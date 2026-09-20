package co.omnimusic.core.desktop

import co.omnimusic.core.audio.AudioSourceException
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.net.InetSocketAddress
import java.net.URI
import javax.sound.sampled.AudioSystem
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The remote-audio buffering that makes seeking possible.
 *
 * This is the one place the project can test audio plumbing for real: `javax.sound.sampled` decodes
 * WAV without an audio device, so the mark/reset behaviour the whole seek path depends on can be
 * exercised here rather than reasoned about. The bytes come from a `com.sun.net.httpserver` on the
 * loopback interface, so nothing needs network access.
 */
class RemoteAudioBufferTest {

    private var server: HttpServer? = null

    private fun serve(body: ByteArray, status: Int = 200): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/preview") { exchange ->
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}/preview"
    }

    private fun shutdown() {
        server?.stop(0)
        server = null
    }

    fun testReadsTheWholeBodyFromTheNetwork() {
        val wav = pcmWav(frames = 200)
        val url = serve(wav)
        try {
            assertContentEquals(wav, RemoteAudioBuffer.read(URI.create(url).toURL()))
        } finally {
            shutdown()
        }
    }

    fun testAStreamWhoseMarkIsExhaustedCannotBeRewound() {
        // The condition openRemote used to create, and it is not "no mark at all". Measured with a
        // probe against this JRE: SunFileReader marks 200 bytes to parse the header and resets, and
        // the JDK wraps a URL stream in an 8 KB buffer - enough for that header reset, so the stream
        // opens fine. But once playback has read past the buffer, reset() - the only way
        // JavaSoundAudioOutput seeks - throws "Resetting to invalid mark". That is why seeking died
        // part-way into a track instead of immediately. 8 KB of 16-bit mono is a fifth of a second.
        val stream = AudioSystem.getAudioInputStream(
            ExhaustibleMarkStream(ByteArrayInputStream(pcmWav(frames = 8_000)), bufferBytes = 8_192),
        )
        repeat(4) { assertTrue(stream.read(ByteArray(4_096)) > 0) }

        val outcome = runCatching { stream.reset() }
        assertTrue(outcome.isFailure, "expected reset() past the mark to throw, got $outcome")
    }

    fun testAStreamOpenedFromTheNetworkCanBeRewoundPastTheBuffer() {
        // The fix, asserted end to end: what open() hands back must survive being read well past
        // the 8 KB a socket-backed buffer holds, and still rewind. Decoding the URL directly -
        // which is what openRemote used to do - passes the first rewind and fails every later one.
        val url = serve(pcmWav(frames = 8_000))
        try {
            val stream = RemoteAudioBuffer.open(URI.create(url).toURL())
            repeat(4) { assertTrue(stream.read(ByteArray(4_096)) > 0, "expected audio data on pass $it") }

            stream.reset()
            assertTrue(stream.skip(50) > 0L, "expected to skip frames after a rewind")

            // Seeking back and forth is the real usage, so rewind twice more.
            repeat(2) {
                assertTrue(stream.read(ByteArray(4_096)) > 0)
                stream.reset()
            }
        } finally {
            shutdown()
        }
    }

    fun testAnExpiredLinkIsReportedAsSuchWithoutQuotingTheToken() {
        // What the CDN does with a signed preview URL past its `exp`: 403. The old notice pasted the
        // whole 300-character link into a snackbar, which told the user nothing they could act on.
        val base = serve(ByteArray(16), status = 403)
        val url = "$base?hdnea=exp=1789911086~acl=/preview.mp3*~hmac=deadbeefdeadbeef"
        try {
            val error = assertFailsWith<AudioSourceException> {
                RemoteAudioBuffer.read(URI.create(url).toURL())
            }
            val message = error.message!!
            assertContains(message, "403")
            assertContains(message, "expired")
            assertFalse(message.contains("hdnea"), "the notice quoted the signed link: $message")
            assertFalse(message.contains("deadbeef"), "the notice quoted the signature: $message")
        } finally {
            shutdown()
        }
    }

    fun testABodyLargerThanTheLimitIsRefusedRatherThanBuffered() {
        val url = serve(ByteArray(4_000))
        try {
            val error = assertFailsWith<AudioSourceException> {
                RemoteAudioBuffer.read(URI.create(url).toURL(), limitBytes = 1_000)
            }
            assertTrue(error.message!!.contains("larger than"), "unhelpful message: ${error.message}")
        } finally {
            shutdown()
        }
    }

    fun testABodyExactlyAtTheLimitIsAccepted() {
        val body = ByteArray(1_000)
        val url = serve(body)
        try {
            assertEquals(1_000, RemoteAudioBuffer.read(URI.create(url).toURL(), limitBytes = 1_000).size)
        } finally {
            shutdown()
        }
    }

    // ----------------------------------------------------------------------------------------

    /**
     * A stream that supports `mark` but only remembers as much as its buffer holds — the rule a
     * `BufferedInputStream` actually follows, and the reason the failure showed up mid-track: the
     * header parse fits inside the buffer, playback eventually does not.
     */
    private class ExhaustibleMarkStream(
        private val source: ByteArrayInputStream,
        private val bufferBytes: Int,
    ) : FilterInputStream(source) {

        private var markLimit = -1
        private var readSinceMark = 0L

        override fun markSupported(): Boolean = true

        override fun mark(readlimit: Int) {
            // The reader asks for 200 bytes; the buffer decides how long the mark really survives.
            markLimit = bufferBytes
            readSinceMark = 0
            source.mark(markLimit)
        }

        override fun reset() {
            if (markLimit < 0 || readSinceMark > markLimit) {
                throw java.io.IOException("Resetting to invalid mark")
            }
            source.reset()
            readSinceMark = 0
        }

        override fun read(): Int = tally(source.read())

        override fun read(b: ByteArray, off: Int, len: Int): Int = tally(source.read(b, off, len))

        override fun skip(n: Long): Long = source.skip(n).also { readSinceMark += it }

        private fun tally(read: Int): Int {
            if (read > 0) readSinceMark += read
            return read
        }
    }

    /** A minimal canonical RIFF/WAVE container: 44-byte header plus silent 16-bit mono PCM. */
    private fun pcmWav(frames: Int, sampleRate: Int = 8_000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val bytesPerFrame = channels * bitsPerSample / 8
        val dataSize = frames * bytesPerFrame
        val out = ByteArray(44 + dataSize)

        fun little(value: Int, at: Int, size: Int) {
            for (i in 0 until size) out[at + i] = ((value ushr (8 * i)) and 0xFF).toByte()
        }

        "RIFF".toByteArray().copyInto(out, 0)
        little(36 + dataSize, 4, 4)
        "WAVE".toByteArray().copyInto(out, 8)
        "fmt ".toByteArray().copyInto(out, 12)
        little(16, 16, 4)
        little(1, 20, 2)
        little(channels, 22, 2)
        little(sampleRate, 24, 4)
        little(sampleRate * bytesPerFrame, 28, 4)
        little(bytesPerFrame, 32, 2)
        little(bitsPerSample, 34, 2)
        "data".toByteArray().copyInto(out, 36)
        little(dataSize, 40, 4)
        return out
    }
}
