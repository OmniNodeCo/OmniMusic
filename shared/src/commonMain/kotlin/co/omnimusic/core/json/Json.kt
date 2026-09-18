package co.omnimusic.core.json

/**
 * A minimal, dependency-free JSON model.
 *
 * The `shared` module deliberately avoids third-party serialization libraries so that the exact
 * same source set can be compiled by Gradle for Android/Desktop *and* by a bare `kotlinc` on the
 * command line (see `tools/`). Keeping the core free of external artifacts is what makes that
 * possible, and it keeps the module usable from any Kotlin Multiplatform target.
 */
sealed interface Json {

    data object Null : Json

    data class Bool(val value: Boolean) : Json

    /**
     * JSON numbers are stored as [Double] (the only numeric type JSON guarantees) together with the
     * literal text so that large integral ids such as Deezer's can be recovered exactly.
     */
    data class Num(val value: Double, val raw: String) : Json

    data class Str(val value: String) : Json

    data class Arr(val items: List<Json>) : Json

    data class Obj(val fields: Map<String, Json>) : Json
}

/** Thrown when input is not syntactically valid JSON. Always carries a 1-based position. */
class JsonException(message: String, val position: Int) : Exception("$message (at offset $position)")

// --------------------------------------------------------------------------------------------
// Accessors
// --------------------------------------------------------------------------------------------

val Json?.isNull: Boolean get() = this == null || this is Json.Null

fun Json?.asObjectOrNull(): Json.Obj? = this as? Json.Obj

fun Json?.asArrayOrNull(): Json.Arr? = this as? Json.Arr

fun Json?.asArrayOrEmpty(): List<Json> = (this as? Json.Arr)?.items ?: emptyList()

fun Json?.asStringOrNull(): String? = when (this) {
    is Json.Str -> value
    is Json.Num -> raw
    is Json.Bool -> value.toString()
    else -> null
}

fun Json?.asStringOrDefault(default: String): String = asStringOrNull() ?: default

fun Json?.asLongOrNull(): Long? = when (this) {
    is Json.Num -> raw.toLongOrNull() ?: value.toLong()
    is Json.Str -> value.trim().toLongOrNull()
    is Json.Bool -> if (value) 1L else 0L
    else -> null
}

fun Json?.asLongOrDefault(default: Long): Long = asLongOrNull() ?: default

fun Json?.asIntOrNull(): Int? = asLongOrNull()?.toInt()

fun Json?.asDoubleOrNull(): Double? = when (this) {
    is Json.Num -> value
    is Json.Str -> value.trim().toDoubleOrNull()
    else -> null
}

fun Json?.asIntOrDefault(default: Int): Int = asIntOrNull() ?: default

fun Json?.asBooleanOrDefault(default: Boolean): Boolean = when (this) {
    is Json.Bool -> value
    is Json.Num -> value != 0.0
    is Json.Str -> value.equals("true", ignoreCase = true)
    else -> default
}

/** `obj["artist"]["name"]` style lookup; returns [Json.Null] rather than a Kotlin `null` for a miss. */
operator fun Json?.get(key: String): Json? = (this as? Json.Obj)?.fields?.get(key)

/** `obj["data"][0]` style lookup. */
operator fun Json?.get(index: Int): Json? = (this as? Json.Arr)?.items?.getOrNull(index)
