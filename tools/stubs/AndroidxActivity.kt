@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName")

// Compile-only stubs of the AndroidX activity pieces the Android shell uses.
// Same caveats as tools/stubs/ComposeRuntime.kt.

package androidx.activity

import android.content.ContextWrapper
import android.os.Bundle

open class ComponentActivity : ContextWrapper(), androidx.lifecycle.LifecycleOwner {

    val onBackPressedDispatcher: OnBackPressedDispatcher = OnBackPressedDispatcher()

    protected open fun onCreate(savedInstanceState: Bundle?) {}
}

fun ComponentActivity.enableEdgeToEdge() {}
