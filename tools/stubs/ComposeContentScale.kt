@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName", "ClassName")

// Compile-only stubs of Compose's layout-scale API — see tools/stubs/ComposeRuntime.kt for the
// caveats that apply to every file in here.

package androidx.compose.ui.layout

/**
 * The real `ContentScale` is a fun interface that measures and scales a source into a destination.
 * Modelled as a named class with the constants the app actually names, which is all a type check
// needs.
 */
class ContentScale private constructor(val name: String) {

    override fun toString(): String = name

    companion object {
        val Crop: ContentScale = ContentScale("Crop")
        val Fit: ContentScale = ContentScale("Fit")
        val FillBounds: ContentScale = ContentScale("FillBounds")
        val Inside: ContentScale = ContentScale("Inside")
        val None: ContentScale = ContentScale("None")
    }
}
