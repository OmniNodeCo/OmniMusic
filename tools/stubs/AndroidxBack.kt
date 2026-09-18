@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName")

// Compile-only stubs of the AndroidX back-dispatch pieces the Android shell uses.
// Same caveats as tools/stubs/ComposeRuntime.kt.

package androidx.activity

import androidx.lifecycle.LifecycleOwner

abstract class OnBackPressedCallback(var isEnabled: Boolean) {

    abstract fun handleOnBackPressed()
}

class OnBackPressedDispatcher {

    fun addCallback(callback: OnBackPressedCallback) {}

    fun addCallback(owner: LifecycleOwner, callback: OnBackPressedCallback) {}

    fun onBackPressed() {}
}
