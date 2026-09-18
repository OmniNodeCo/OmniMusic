@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName", "ClassName")

// --------------------------------------------------------------------------------------------
// Compile-only stubs of the Compose Multiplatform API surface this project uses.
//
// Purpose: the sandbox cannot reach Maven Central or Google Maven, so the real Compose artifacts
// cannot be downloaded and `composeApp/` cannot be compiled here. These declarations stand in for
// them so that kotlinc can still check the app's own code — unresolved references, wrong argument
// names, wrong types, receiver-scope mistakes (a `Modifier.align` outside a Box, say), and syntax.
//
// What this does NOT prove: that the signatures below match the real library. They are written to
// match Compose 1.8 / Material3 as closely as the author knows them, and any place where a stub is
// more permissive than the real API is a place where a real build could still fail.
//
// These files are used only by tools/check-ui.sh and are never part of the Gradle build.
// --------------------------------------------------------------------------------------------

package androidx.compose.runtime

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
)
annotation class Composable

@Composable
fun <T> remember(calculation: () -> T): T = calculation()

@Composable
fun <T> remember(key: Any?, calculation: () -> T): T = calculation()

interface State<out T> {
    val value: T
}

interface MutableState<T> : State<T> {
    override var value: T
}

fun <T> mutableStateOf(value: T): MutableState<T> = object : MutableState<T> {
    override var value: T = value
}

operator fun <T> State<T>.getValue(thisObj: Any?, property: Any?): T = value

operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: Any?, newValue: T) {
    value = newValue
}

@Composable
fun LaunchedEffect(key1: Any?, block: suspend () -> Unit) {}

suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R = onFrame(0L)
