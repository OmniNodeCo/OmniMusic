@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName")

// Compile-only stubs — see tools/stubs/ComposeRuntime.kt for what this is and is not.

package androidx.compose.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Deliberately matches the real signature: `clickable` has **no** `onLongClick`. Press-and-hold
 * needs `combinedClickable`, which is exactly the kind of mistake this stub exists to catch.
 */
fun Modifier.clickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = this

fun Modifier.combinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = this

fun Modifier.background(color: Color): Modifier = this

fun Modifier.background(brush: Brush): Modifier = this

@androidx.compose.runtime.Composable
fun isSystemInDarkTheme(): Boolean = true
