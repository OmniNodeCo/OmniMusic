@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName")

// Compile-only stubs — see tools/stubs/ComposeRuntime.kt for what this is and is not.

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

class WindowState

interface ApplicationScope {
    fun exitApplication()
}

@Composable
fun rememberWindowState(width: Dp = Dp(800f), height: Dp = Dp(600f)): WindowState = WindowState()

@Composable
fun ApplicationScope.Window(
    onCloseRequest: () -> Unit,
    title: String = "Untitled",
    state: WindowState = WindowState(),
    onPreviewKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
}

fun application(content: @Composable ApplicationScope.() -> Unit) {}
