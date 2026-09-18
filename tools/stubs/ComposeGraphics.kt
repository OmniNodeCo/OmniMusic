@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName", "ClassName")

// Compile-only stubs — see tools/stubs/ComposeRuntime.kt for what this is and is not.

package androidx.compose.ui.graphics

class Color(val value: ULong = 0u) {
    fun copy(alpha: Float = 1f): Color = Color()

    companion object {
        val White: Color = Color()
        val Black: Color = Color()
        fun hsl(hue: Float, saturation: Float, lightness: Float, alpha: Float = 1f): Color = Color()
    }
}

/** The `Color(0xFFAABBCC)` form everyone actually writes. */
fun Color(color: Long): Color = Color()

/** `androidx.compose.ui.graphics.RectangleShape` — the default `Surface` shape. */
object RectangleShape : androidx.compose.ui.draw.Shape

class Brush {
    companion object {
        fun linearGradient(colors: List<Color>): Brush = Brush()
    }
}
