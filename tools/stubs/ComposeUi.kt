@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName", "ClassName")

// Compile-only stubs — see tools/stubs/ComposeRuntime.kt for what this is and is not.

package androidx.compose.ui

interface Modifier {
    companion object : Modifier
}

/**
 * Modelled with the real three-way split ([Alignment], [Alignment.Horizontal], [Alignment.Vertical])
 * rather than one flat type, because that split is exactly what stops `Modifier.align(Alignment
 * .CenterVertically)` from being written inside a `Column`. A single flat `Alignment` would let
 * every such mistake through.
 */
class Alignment private constructor() {

    class Horizontal private constructor() {
        companion object {
            val Start: Horizontal = Horizontal()
            val CenterHorizontally: Horizontal = Horizontal()
            val End: Horizontal = Horizontal()
        }
    }

    class Vertical private constructor() {
        companion object {
            val Top: Vertical = Vertical()
            val CenterVertically: Vertical = Vertical()
            val Bottom: Vertical = Vertical()
        }
    }

    companion object {
        val Center: Alignment = Alignment()
        val TopStart: Alignment = Alignment()
        val TopCenter: Alignment = Alignment()
        val BottomStart: Alignment = Alignment()
        val BottomCenter: Alignment = Alignment()
        val BottomEnd: Alignment = Alignment()
        val CenterStart: Alignment = Alignment()
        val CenterEnd: Alignment = Alignment()
        val CenterHorizontally: Horizontal = Horizontal.CenterHorizontally
        val CenterVertically: Vertical = Vertical.CenterVertically
    }
}
