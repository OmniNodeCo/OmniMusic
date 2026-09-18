@file:Suppress("unused", "UNUSED_PARAMETER", "FunctionName", "ClassName")

// Compile-only stubs of the handful of Android/AndroidX APIs this project touches, so that
// `composeApp/src/androidMain` can be type-checked without the Android SDK.
// Same caveats as tools/stubs/ComposeRuntime.kt.

package android.content

interface SharedPreferences {

    fun getString(key: String, defValue: String?): String?

    fun edit(): Editor

    val all: MutableMap<String, *>

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun remove(key: String): Editor
        fun apply()
    }

    companion object {
        const val MODE_PRIVATE: Int = 0
    }
}

open class Context {

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = throw UnsupportedOperationException()

    val applicationContext: Context get() = this

    companion object {
        const val MODE_PRIVATE: Int = 0
    }
}
