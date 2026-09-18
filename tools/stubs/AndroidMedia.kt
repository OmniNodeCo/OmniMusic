@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName", "ClassName")

// Compile-only stubs — see tools/stubs/ComposeRuntime.kt for what this is and is not.

package android.media

class AudioAttributes private constructor() {

    class Builder {
        fun setUsage(usage: Int): Builder = this
        fun setContentType(contentType: Int): Builder = this
        fun build(): AudioAttributes = AudioAttributes()
    }

    companion object {
        const val USAGE_MEDIA: Int = 1
        const val CONTENT_TYPE_MUSIC: Int = 2
    }
}

class MediaPlayer {

    fun setAudioAttributes(attributes: AudioAttributes) {}

    @Throws(java.io.IOException::class)
    fun setDataSource(path: String) {}

    @Throws(java.io.IOException::class)
    fun prepare() {}

    fun start() {}

    fun pause() {}

    fun stop() {}

    fun release() {}

    fun seekTo(msec: Int) {}

    val currentPosition: Int get() = 0

    val isPlaying: Boolean get() = false

    fun setVolume(leftVolume: Float, rightVolume: Float): Int = 0
}
