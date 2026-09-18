@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName", "ClassName")

// Compile-only stubs of Compose's key-input API — see tools/stubs/ComposeRuntime.kt for the
// caveats that apply to every file in here.

package androidx.compose.ui.input.key

import androidx.compose.ui.Modifier

/**
 * The real `Key` is a value class over a key code. Modelled as a named class so equality and
 * `when` branches behave, which is all the mapping code in this project does with it.
 */
class Key private constructor(val name: String) {

    override fun toString(): String = name

    companion object {
        val Spacebar: Key = Key("Spacebar")
        val Escape: Key = Key("Escape")
        val DirectionLeft: Key = Key("DirectionLeft")
        val DirectionRight: Key = Key("DirectionRight")
        val N: Key = Key("N")
        val P: Key = Key("P")
        val MediaPlayPause: Key = Key("MediaPlayPause")
        val MediaNext: Key = Key("MediaNext")
        val MediaPrevious: Key = Key("MediaPrevious")
        val MediaFastForward: Key = Key("MediaFastForward")
        val MediaRewind: Key = Key("MediaRewind")
    }
}

class KeyEventType private constructor() {
    companion object {
        val KeyDown: KeyEventType = KeyEventType()
        val KeyUp: KeyEventType = KeyEventType()
    }
}

/**
 * The real `KeyEvent` is a value class wrapping a platform event, and `key`, `type`,
 * `utf16CodePoint` and the modifier flags are *extension properties* on it — so a caller has to
 * import them explicitly. Modelling them as constructor parameters here instead let `KeyMapping.kt`
 * type-check against the stub while failing against real Compose, which is the one thing these
 * stubs are supposed to prevent. The shape below mirrors KeyEvent.kt in
 * compose-multiplatform-core.
 */
class NativeKeyEvent

@JvmInline
value class KeyEvent(val nativeKeyEvent: NativeKeyEvent)

val KeyEvent.key: Key
    get() = Key.Spacebar

val KeyEvent.type: KeyEventType
    get() = KeyEventType.KeyDown

fun Modifier.onPreviewKeyEvent(onKeyEvent: (KeyEvent) -> Boolean): Modifier = this
