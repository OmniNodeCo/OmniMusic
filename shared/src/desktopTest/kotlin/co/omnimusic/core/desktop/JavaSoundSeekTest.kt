package co.omnimusic.core.desktop

import co.omnimusic.core.audio.AudioSource
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Seeking, tested at the level where it can be tested.
 *
 * A seek is now "decode the buffered body again and drop the frames before the target", which these
 * tests exercise directly: no audio device is involved, so they run anywhere. What they cannot cover
 * is the MP3 decoder itself — the JDK decodes WAV natively, and the mp3spi jar is only on the Gradle
 * classpath — so the codec-specific half of the old failure is argued from the mechanism, not
 * demonstrated here.
 */
class JavaSoundSeekTest {

    private val output = JavaSoundAudioOutput()

    /** 0.25 s of 8 kHz mono 16-bit PCM, filled with a pattern so a wrong offset is visible. */
    private val body = pcmWav(frames = 2_000) { index -> (index * 7 + 3).toByte() }

    fun testPreparingARemoteTrackRecordsHowToReopenIt() {
        // Through the same path prepare() takes, minus the line, so the wiring from a remote source
        // to the reopen hook is covered too and not only the helper underneath it.
        val url = serve(body)
        try {
            val prepared = JavaSoundAudioOutput()
            prepared.openSource(AudioSource.Remote(url))

            val bySkipping = prepared.reopenedAt(0)!!
            assertEquals(200L, bySkipping.skip(200))
            val expected = readBytes(bySkipping, 64)

            assertContentEquals(expected, readBytes(prepared.reopenedAt(25)!!, 64))
        } finally {
            shutdown()
        }
    }

    fun testOpeningAtAPositionLandsOnTheSameAudioAsSkippingThere() {
        // 25 ms at 8 kHz is 200 frames, which is what openAt has to drop.
        val bySkipping = output.openAt(body, 0)
        assertEquals(200L, bySkipping.skip(200))
        val expected = readBytes(bySkipping, 64)

        val byReopening = output.openAt(body, 25)
        assertContentEquals(expected, readBytes(byReopening, 64))
    }

    fun testSeekingBackwardsIsRepeatable() {
        // The failure this replaces was a rewind that could not be done twice; re-decoding has no
        // such limit, so forward and back land on the same audio every time.
        val late = readBytes(output.openAt(body, 100), 64)
        val early = readBytes(output.openAt(body, 10), 64)
        assertContentEquals(late, readBytes(output.openAt(body, 100), 64))
        assertContentEquals(early, readBytes(output.openAt(body, 10), 64))

        // And the two positions are genuinely different audio, or the comparison above proves nothing.
        assertTrue(!late.contentEquals(early), "two different positions returned identical audio")
    }

    fun testEachDecodeIsIndependentOfTheLast() {
        // A seek replaces the stream the writer is reading; if two decodes shared state, the old one
        // would be left somewhere unpredictable.
        val first = output.decode(body)
        val second = output.decode(body)
        readBytes(first, 128)

        val fresh = readBytes(output.decode(body), 64)
        assertContentEquals(fresh, readBytes(second, 64))
    }

    fun testASeekBeforeTheStartLandsAtTheStart() {
        assertContentEquals(readBytes(output.openAt(body, 0), 64), readBytes(output.openAt(body, -500), 64))
    }

    // ----------------------------------------------------------------------------------------

    private var server: HttpServer? = null

    private fun serve(bytes: ByteArray): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/preview") { exchange ->
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}/preview"
    }

    private fun shutdown() {
        server?.stop(0)
        server = null
    }

    private fun readBytes(stream: AudioInputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = stream.read(out, read, count - read)
            if (n <= 0) break
            read += n
        }
        assertEquals(count, read, "stream ran out after $read of $count bytes")
        return out
    }

    /** A canonical RIFF/WAVE container: 44-byte header plus 16-bit mono PCM built by [sample]. */
    private fun pcmWav(frames: Int, sample: (Int) -> Byte): ByteArray {
        val dataSize = frames * 2
        val out = ByteArray(44 + dataSize)

        fun little(value: Int, at: Int, size: Int) {
            for (i in 0 until size) out[at + i] = ((value ushr (8 * i)) and 0xFF).toByte()
        }

        for (i in 0 until dataSize) out[44 + i] = sample(i)
        "RIFF".toByteArray().copyInto(out, 0)
        little(36 + dataSize, 4, 4)
        "WAVE".toByteArray().copyInto(out, 8)
        "fmt ".toByteArray().copyInto(out, 12)
        little(16, 16, 4)
        little(1, 20, 2)
        little(1, 22, 2)
        little(8_000, 24, 4)
        little(16_000, 28, 4)
        little(2, 32, 2)
        little(16, 34, 2)
        "data".toByteArray().copyInto(out, 36)
        little(dataSize, 40, 4)

        // Guard against the test asserting on a container the JDK refuses to read.
        assertTrue(AudioSystem.getAudioInputStream(ByteArrayInputStream(out)).format.frameRate == 8_000f)
        return out
    }
}
