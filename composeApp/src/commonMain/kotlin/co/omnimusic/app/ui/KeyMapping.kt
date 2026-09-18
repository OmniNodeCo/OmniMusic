package co.omnimusic.app.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import co.omnimusic.app.KeyAction

/**
 * Translates a physical key press into a [KeyAction], or null when the key is not ours.
 *
 * Lives in the UI layer so [co.omnimusic.app.AppModel] never has to know what a `Key` is, and so a
 * second input route (headset buttons, a TV remote) can produce the same enum without touching the
 * player. Returning null — rather than false — lets the caller leave unhandled keys alone, which is
 * what keeps typing in the search box working while these shortcuts are installed.
 *
 * Only key-down is acted on; acting on key-up as well would double every press.
 */
fun keyActionFor(event: KeyEvent): KeyAction? {
    if (event.type != KeyEventType.KeyDown) return null
    return when (event.key) {
        Key.Spacebar, Key.MediaPlayPause -> KeyAction.TOGGLE_PLAY_PAUSE
        Key.DirectionRight, Key.MediaFastForward -> KeyAction.SEEK_FORWARD
        Key.DirectionLeft, Key.MediaRewind -> KeyAction.SEEK_BACKWARD
        Key.N, Key.MediaNext -> KeyAction.NEXT_TRACK
        Key.P, Key.MediaPrevious -> KeyAction.PREVIOUS_TRACK
        Key.Escape -> KeyAction.DISMISS
        else -> null
    }
}
