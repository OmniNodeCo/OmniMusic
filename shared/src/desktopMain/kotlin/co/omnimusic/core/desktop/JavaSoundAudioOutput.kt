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
 * needs a Java Sound SPI on the classpath — see `composeApp/build.gradle.kts`, which adds one for
 * the desktop target.
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

    override fun prepare(source: AudioSource) {
        stop()
        val input = when (source) {
            is AudioSource.Remote -> openRemote(source.url)
            is AudioSource.Pcm -> openPcm(source)
        }
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
        ready = false
        framesWritten = 0
    }

    override fun seekTo(positionMillis: Long) {
        val current = stream ?: return
        val target = current.format.frameRate.toLong() * positionMillis / 1000L
        try {
            current.reset()
            val skipped = current.skip(target)
            framesWritten = skipped
        } catch (e: IOException) {
            throw AudioSourceException("cannot seek this stream", e)
        }
        if (!paused) {
            line?.flush()
        }
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

    private fun startWriter() {
        val output = line ?: return
        val input = stream ?: return
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

    private fun openRemote(url: String): AudioInputStream {
        return try {
            AudioSystem.getAudioInputStream(URI.create(url).toURL())
        } catch (e: UnsupportedAudioFileException) {
            throw UnsupportedAudioException(
                "No decoder for this stream. The JDK can play WAV/AIFF/AU natively; MP3 and AAC need " +
                    "a Java Sound SPI on the classpath (see composeApp/build.gradle.kts).",
                e,
            )
        } catch (e: IOException) {
            throw AudioSourceException("could not open $url", e)
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
