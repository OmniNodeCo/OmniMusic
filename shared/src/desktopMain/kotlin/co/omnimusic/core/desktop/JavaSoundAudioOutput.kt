package co.omnimusic.core.desktop

import co.omnimusic.core.audio.AudioOutput
import co.omnimusic.core.audio.AudioSource
import co.omnimusic.core.audio.AudioSourceException
import co.omnimusic.core.audio.PcmFormat
import co.omnimusic.core.audio.UnsupportedAudioException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.BooleanControl
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.UnsupportedAudioFileException

/**
 * Desktop [AudioOutput] built on Java Sound (`javax.sound.sampled`).
 *
 * Playback happens on a dedicated writer thread because `SourceDataLine.write` blocks — that
 * blocking is what paces the stream. The thread is stopped, never suspended, so pausing is a flag
 * check rather than a frozen native buffer.
 *
 * The JDK itself can decode WAV, AIFF and AU. MP3 (which is what music APIs hand back as previews)
 * comes from the mp3spi Java Sound SPI the desktop build depends on; whatever an SPI returns is
 * converted to PCM by [PcmConversion] before the line is opened.
 */
class JavaSoundAudioOutput(
    private val bufferSizeBytes: Int = 8192,
) : AudioOutput {

    private val lock = ReentrantLock()
    private val resumed = lock.newCondition()

    private var line: SourceDataLine? = null
    private var stream: AudioInputStream? = null
    private var writer: Thread? = null

    private var paused = true
    private var stopRequested = true
    private var ready = false
    private var volume = 1f
    private var framesWritten = 0L
    private var format: AudioFormat? = null

    /**
     * Opens the audio this output was prepared with again, positioned at a given time.
     *
     * A seek is a second decoder over audio already in memory, never a rewind of the first one.
     */
    private var reopenAt: ((Long) -> AudioInputStream)? = null

    override fun prepare(source: AudioSource) {
        stop()
        val input = openSource(source)
        stream = input
        format = input.format
        line = openLine(input.format)
        framesWritten = 0
        ready = true
        paused = true
        stopRequested = false
        applyVolume()
        startWriter()
    }

    override fun play() {
        if (!ready) return
        lock.lock()
        try {
            paused = false
            resumed.signalAll()
        } finally {
            lock.unlock()
        }
        line?.start()
    }

    override fun pause() {
        lock.lock()
        try {
            paused = true
        } finally {
            lock.unlock()
        }
        line?.let {
            // Let the buffer drain instead of stopping abruptly: no click at the pause point.
            it.flush()
            it.stop()
        }
    }

    override fun stop() {
        lock.lock()
        try {
            stopRequested = true
            paused = false
            resumed.signalAll()
        } finally {
            lock.unlock()
        }
        writer?.let { thread ->
            runCatching { thread.join(500) }
        }
        writer = null
        runCatching { line?.close() }
        runCatching { stream?.close() }
        line = null
        stream = null
        reopenAt = null
        ready = false
        framesWritten = 0
    }

    /**
     * Moves to [positionMillis] by decoding the audio a second time and dropping the frames before
     * it.
     *
     * The obvious implementation is `reset()` followed by `skip()`, and that is what this used to
     * do — it is also what produced "cannot seek this stream". An MP3 does not arrive as a stream
     * over bytes: it arrives as a *conversion* stream over a decoder with its own frame state and
     * bit reservoir, and rewinding that is not something a Java Sound SPI has to honour, however
     * seekable the bytes underneath are. Starting a fresh decoder cannot fail that way, and since
     * the whole body is already in memory it costs a header parse rather than a network round trip.
     *
     * The cost is that skipping decodes: reaching the end of a long track means decoding all of it.
     * That is worth trading for a seek that always lands.
     */
    override fun seekTo(positionMillis: Long) {
        val again = reopenAt
        val output = line
        if (again == null || output == null) return

        val wasPaused = paused
        // The writer thread is blocked inside read() on the decoder being replaced, so it has to go
        // first; swapping the stream underneath it would leave it reading one nobody owns.
        stopWriter()

        val reopened = try {
            again(positionMillis)
        } catch (e: IOException) {
            throw AudioSourceException("cannot seek this stream", e)
        }
        stream = reopened
        framesWritten = frameAt(reopened.format, positionMillis)

        lock.lock()
        try {
            paused = wasPaused
        } finally {
            lock.unlock()
        }
        if (!wasPaused) output.flush()
        startWriter()
    }

    override fun positionMillis(): Long {
        val current = format ?: return -1
        return if (current.frameRate <= 0f) -1L else framesWritten * 1000L / current.frameRate.toLong()
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        applyVolume()
    }

    override fun isReady(): Boolean = ready

    // ----------------------------------------------------------------------------------------

    /** Stops the reader thread but leaves the line open, so a seek can carry on playing. */
    private fun stopWriter() {
        lock.lock()
        try {
            stopRequested = true
            paused = false
            resumed.signalAll()
        } finally {
            lock.unlock()
        }
        writer?.let { thread -> runCatching { thread.join(1_000) } }
        writer = null
        runCatching { stream?.close() }
    }

    private fun startWriter() {
        val output = line ?: return
        val input = stream ?: return
        stopRequested = false
        val thread = Thread({
            val buffer = ByteArray(bufferSizeBytes)
            try {
                while (!stopRequested) {
                    lock.lock()
                    try {
                        while (paused && !stopRequested) resumed.await(100, TimeUnit.MILLISECONDS)
                    } finally {
                        lock.unlock()
                    }
                    if (stopRequested) break
                    val read = input.read(buffer, 0, buffer.size)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    framesWritten += read / output.format.frameSize
                }
            } catch (e: IOException) {
                if (!stopRequested) {
                    // Surface the failure the same way as a prepare-time failure: stop cleanly.
                    ready = false
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                if (!stopRequested) {
                    // Natural end of stream: drain so the tail of the track is audible.
                    runCatching { output.drain() }
                }
                ready = false
                lock.lock()
                try {
                    paused = true
                } finally {
                    lock.unlock()
                }
            }
        }, "OmniMusic-audio").apply { isDaemon = true }
        writer = thread
        thread.start()
    }

    /**
     * Opens the audio behind [source] and records how to open it again, without touching a device.
     *
     * Split out of [prepare] so the part a seek depends on can be tested on a machine with no sound
     * card — which is every machine CI has.
     */
    fun openSource(source: AudioSource): AudioInputStream = when (source) {
        is AudioSource.Remote -> {
            val url = source.url
            val body = fetch(url)
            reopenAt = { millis -> openAt(body, millis, url) }
            decode(body, url)
        }

        is AudioSource.Pcm -> {
            reopenAt = { millis -> positioned(openPcm(source), millis) }
            openPcm(source)
        }
    }

    /** The stream a seek switches to, or null when this output has not been given any audio. */
    fun reopenedAt(positionMillis: Long): AudioInputStream? = reopenAt?.invoke(positionMillis)

    /** Reads a remote body into memory, which is what makes seeking possible at all. */
    private fun fetch(url: String): ByteArray = try {
        RemoteAudioBuffer.bytes(URI.create(url).toURL())
    } catch (e: IOException) {
        throw AudioSourceException("could not open $url", e)
    }

    /**
     * Decodes an audio body held in memory, asking for PCM if the decoder handed back its own
     * encoding. Opened up so a seek can start a second decoder over the same bytes.
     */
    fun decode(bytes: ByteArray, label: String = "audio"): AudioInputStream {
        val raw = try {
            AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
        } catch (e: UnsupportedAudioFileException) {
            throw UnsupportedAudioException(
                "No decoder for this stream. The JDK can play WAV/AIFF/AU natively; MP3 and AAC need " +
                    "a Java Sound SPI on the classpath (the desktop build ships mp3spi).",
                e,
            )
        } catch (e: IOException) {
            throw AudioSourceException("could not read $label", e)
        }
        return decoded(raw, label)
    }

    /** A fresh decoder over [bytes], already positioned at [positionMillis]. */
    fun openAt(bytes: ByteArray, positionMillis: Long, label: String = "audio"): AudioInputStream =
        positioned(decode(bytes, label), positionMillis)

    private fun positioned(stream: AudioInputStream, positionMillis: Long): AudioInputStream {
        val frames = frameAt(stream.format, positionMillis)
        if (frames > 0) stream.skip(frames)
        return stream
    }

    private fun frameAt(audioFormat: AudioFormat?, positionMillis: Long): Long {
        val rate = audioFormat?.frameRate ?: return 0L
        return if (rate <= 0f || positionMillis <= 0) 0L else rate.toLong() * positionMillis / 1000L
    }

    /**
     * Asks the SPI for PCM when it handed us its own encoding.
     *
     * See [PcmConversion] for why this step is not optional: without it an MP3 stream reaches
     * [openLine] as `MPEG1L3`, no mixer supports that, and Java Sound blames the audio device.
     */
    private fun decoded(source: AudioInputStream, url: String): AudioInputStream {
        if (!PcmConversion.needsConversion(source.format)) return source
        val target = PcmConversion.targetFor(source.format)
            ?: throw UnsupportedAudioException(
                "cannot decode $url: the decoder reports an incomplete format (${source.format})",
            )
        return try {
            AudioSystem.getAudioInputStream(target, source)
        } catch (e: IllegalArgumentException) {
            throw UnsupportedAudioException(
                "Nothing on the classpath can convert ${source.format.encoding} to PCM",
                e,
            )
        } catch (e: UnsupportedOperationException) {
            throw UnsupportedAudioException(
                "Nothing on the classpath can convert ${source.format.encoding} to PCM",
                e,
            )
        }
    }

    private fun openPcm(source: AudioSource.Pcm): AudioInputStream {
        val audioFormat = source.format.toAudioFormat()
        val slice = if (source.offset == 0 && source.length == source.samples.size) {
            source.samples
        } else {
            source.samples.copyOfRange(source.offset, source.offset + source.length)
        }
        val frames = slice.size.toLong() / audioFormat.frameSize
        return AudioInputStream(ByteArrayInputStream(slice), audioFormat, frames)
    }

    private fun openLine(audioFormat: AudioFormat): SourceDataLine {
        val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
        if (!AudioSystem.isLineSupported(info)) {
            throw UnsupportedAudioException(
                "This machine has no audio output for ${audioFormat.encoding} @ " +
                    "${audioFormat.sampleRate.toInt()} Hz / ${audioFormat.channels} ch",
            )
        }
        return try {
            (AudioSystem.getLine(info) as SourceDataLine).apply { open(audioFormat, bufferSizeBytes) }
        } catch (e: LineUnavailableException) {
            throw AudioSourceException("no audio device available", e)
        }
    }

    private fun applyVolume() {
        val target = line ?: return
        if (!target.isControlSupported(FloatControl.Type.MASTER_GAIN)) return
        val control = target.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        // Perceptual volume: 0.5 feels like half volume, which linear gain does not.
        val linear = volume.coerceIn(0.0001f, 1f)
        val decibels = 20.0 * Math.log10(linear.toDouble())
        control.value = decibels.toFloat().coerceIn(control.minimum, control.maximum)
        if (target.isControlSupported(BooleanControl.Type.MUTE)) {
            (target.getControl(BooleanControl.Type.MUTE) as BooleanControl).value = volume <= 0f
        }
    }

    private fun PcmFormat.toAudioFormat(): AudioFormat = AudioFormat(
        if (bitsPerSample == 8) AudioFormat.Encoding.PCM_UNSIGNED else AudioFormat.Encoding.PCM_SIGNED,
        sampleRate.toFloat(),
        bitsPerSample,
        channels,
        frameSize,
        sampleRate.toFloat(),
        false,
    )
}
