@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName")

// Compile-only stubs — see tools/stubs/ComposeRuntime.kt for what this is and is not.

package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

class PaddingValues

fun PaddingValues(horizontal: Dp = Dp(0f), vertical: Dp = Dp(0f)): PaddingValues = PaddingValues()

/** Scopes carry the alignment types the real API uses, so a wrong axis is a compile error here too. */
interface ColumnScope {
    fun Modifier.align(alignment: Alignment.Horizontal): Modifier
    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
}

interface RowScope {
    fun Modifier.align(alignment: Alignment.Vertical): Modifier
    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
}

interface BoxScope {
    fun Modifier.align(alignment: Alignment): Modifier
}

@Composable
fun Column(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Horizontal.Start,
    verticalArrangement: VerticalArrangement = VerticalArrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
}

@Composable
fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: HorizontalArrangement = HorizontalArrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
}

@Composable
fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
}

@Composable
fun Spacer(modifier: Modifier) {
}

class VerticalArrangement {
    companion object {
        val Top: VerticalArrangement = VerticalArrangement()
        val Center: VerticalArrangement = VerticalArrangement()
        val Bottom: VerticalArrangement = VerticalArrangement()
        fun spacedBy(space: Dp): VerticalArrangement = VerticalArrangement()
    }
}

class HorizontalArrangement {
    companion object {
        val Start: HorizontalArrangement = HorizontalArrangement()
        val Center: HorizontalArrangement = HorizontalArrangement()
        val End: HorizontalArrangement = HorizontalArrangement()
        fun spacedBy(space: Dp): HorizontalArrangement = HorizontalArrangement()
    }
}

fun Modifier.fillMaxSize(): Modifier = this

fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier = this

fun Modifier.height(height: Dp): Modifier = this

fun Modifier.width(width: Dp): Modifier = this

fun Modifier.size(size: Dp): Modifier = this

fun Modifier.padding(all: Dp): Modifier = this

fun Modifier.padding(horizontal: Dp = Dp(0f), vertical: Dp = Dp(0f)): Modifier = this

fun Modifier.padding(
    start: Dp = Dp(0f),
    top: Dp = Dp(0f),
    end: Dp = Dp(0f),
    bottom: Dp = Dp(0f),
): Modifier = this

fun Modifier.padding(paddingValues: PaddingValues): Modifier = this
