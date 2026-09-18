@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName", "ClassName")

// Compile-only stubs — see tools/stubs/ComposeRuntime.kt for what this is and is not.

package androidx.compose.ui.unit

class Dp(val value: Float)

val Int.dp: Dp get() = Dp(toFloat())
val Float.dp: Dp get() = Dp(this)
val Double.dp: Dp get() = Dp(toFloat())

class TextUnit

val Int.sp: TextUnit get() = TextUnit()
val Float.sp: TextUnit get() = TextUnit()
