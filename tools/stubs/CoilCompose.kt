@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName", "ClassName")

// Compile-only stubs of the Coil image loader — see tools/stubs/ComposeRuntime.kt for the caveats
// that apply to every file in here.

package coil3.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * The one entry point this app uses from `io.coil-kt.coil3:coil-compose`.
 *
 * Parameters are kept in the real declaration's order — `model`, `contentDescription`, `modifier`,
 * then the rest — because a positional argument in the wrong slot would otherwise compile here and
 * fail in CI. The `placeholder` / `error` / `fallback` painters and the state callbacks are
 * omitted; if the app starts using them, add them here with their real `Painter?` and
 * `AsyncImagePainter.State.*` types rather than passing anything.
 *
 * Nothing in here loads an image. The real fetch comes from `coil-network-okhttp`, which is a
 * ServiceLoader-registered dependency of the Android and desktop targets — omit it and `AsyncImage`
 * renders the placeholder forever without reporting an error, which is the failure mode this stub
 * cannot catch.
 */
@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
) {
}
