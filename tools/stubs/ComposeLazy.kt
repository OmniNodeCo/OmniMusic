@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName")

// Compile-only stubs — see tools/stubs/ComposeRuntime.kt for what this is and is not.

package androidx.compose.foundation.lazy

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface LazyListScope {
    fun item(content: @Composable () -> Unit)

    fun items(count: Int, itemContent: @Composable (index: Int) -> Unit)
}

fun <T> LazyListScope.items(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    itemContent: @Composable (item: T) -> Unit,
) {
}

@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues? = null,
    content: LazyListScope.() -> Unit,
) {
}

@Composable
fun LazyRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues? = null,
    content: LazyListScope.() -> Unit,
) {
}
