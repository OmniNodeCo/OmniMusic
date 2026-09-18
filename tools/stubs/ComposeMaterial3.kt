@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName")

// Compile-only stubs — see tools/stubs/ComposeRuntime.kt for what this is and is not.

package androidx.compose.material3

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

class ColorScheme(
    val primary: Color = Color(),
    val onPrimary: Color = Color(),
    val primaryContainer: Color = Color(),
    val secondary: Color = Color(),
    val surface: Color = Color(),
    val surfaceVariant: Color = Color(),
    val onSurface: Color = Color(),
    val onSurfaceVariant: Color = Color(),
    val background: Color = Color(),
)

fun darkColorScheme(
    primary: Color = Color(),
    onPrimary: Color = Color(),
    primaryContainer: Color = Color(),
    secondary: Color = Color(),
    surface: Color = Color(),
    surfaceVariant: Color = Color(),
    onSurface: Color = Color(),
    onSurfaceVariant: Color = Color(),
    background: Color = Color(),
): ColorScheme = ColorScheme()

fun lightColorScheme(
    primary: Color = Color(),
    onPrimary: Color = Color(),
    primaryContainer: Color = Color(),
    secondary: Color = Color(),
    surface: Color = Color(),
    surfaceVariant: Color = Color(),
    onSurface: Color = Color(),
    onSurfaceVariant: Color = Color(),
    background: Color = Color(),
): ColorScheme = ColorScheme()

class Typography(
    val labelSmall: TextStyle = TextStyle(),
    val labelMedium: TextStyle = TextStyle(),
    val labelLarge: TextStyle = TextStyle(),
    val bodySmall: TextStyle = TextStyle(),
    val bodyMedium: TextStyle = TextStyle(),
    val bodyLarge: TextStyle = TextStyle(),
    val titleSmall: TextStyle = TextStyle(),
    val titleMedium: TextStyle = TextStyle(),
    val titleLarge: TextStyle = TextStyle(),
)

object MaterialTheme {
    val colorScheme: ColorScheme get() = ColorScheme()
    val typography: Typography get() = Typography()
}

@Composable
fun MaterialTheme(
    colorScheme: ColorScheme = ColorScheme(),
    typography: Typography = Typography(),
    content: @Composable () -> Unit,
) {
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(),
    fontSize: TextUnit = TextUnit(),
    fontWeight: FontWeight? = null,
    style: TextStyle = TextStyle(),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
}

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.draw.Shape = androidx.compose.ui.graphics.RectangleShape,
    color: Color = Color(),
    tonalElevation: Dp = Dp(0f),
    content: @Composable () -> Unit,
) {
}

@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Third position, matching Material3: a named `enabled` is the usual way to pass it, but a
    // positional argument would land in the wrong slot if this were declared last.
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
}

@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
}

@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
) {
}

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
}

@Composable
fun CircularProgressIndicator(modifier: Modifier = Modifier) {
}

@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
}

@Composable
fun Snackbar(
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
}
