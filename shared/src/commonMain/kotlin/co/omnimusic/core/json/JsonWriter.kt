package co.omnimusic.core.json

/** Serializes [Json] back to compact text. Used to persist playlists and preferences. */
object JsonWriter {

    fun write(value: Json, builder: StringBuilder = StringBuilder()): String {
        when (value) {
            Json.Null -> builder.append("null")
            is Json.Bool -> builder.append(value.value.toString())
            is Json.Num -> builder.append(value.raw)
            is Json.Str -> writeString(value.value, builder)
            is Json.Arr -> {
                builder.append('[')
                value.items.forEachIndexed { i, item ->
                    if (i > 0) builder.append(',')
                    write(item, builder)
                }
                builder.append(']')
            }

            is Json.Obj -> {
                builder.append('{')
                var first = true
                for ((key, item) in value.fields) {
                    if (!first) builder.append(',')
                    first = false
                    writeString(key, builder)
                    builder.append(':')
                    write(item, builder)
                }
                builder.append('}')
            }
        }
        return builder.toString()
    }
}

// --------------------------------------------------------------------------------------------
// Builders — small helpers so the persistence layer does not have to hand-assemble maps.
// --------------------------------------------------------------------------------------------

fun jsonObject(vararg pairs: Pair<String, Json?>): Json.Obj =
    Json.Obj(pairs.filter { it.second != null }.associate { (k, v) -> k to v!! })

fun jsonArray(items: List<Json>): Json.Arr = Json.Arr(items)

fun jsonArrayOf(vararg items: Json): Json.Arr = Json.Arr(items.toList())

fun writeString(value: String, builder: StringBuilder) {
    builder.append('"')
    for (c in value) {
        when (c) {
            '"' -> builder.append("\\\"")
            '\\' -> builder.append("\\\\")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            '\b' -> builder.append("\\b")
            '\u000C' -> builder.append("\\f")
            else -> if (c.code < 0x20) {
                builder.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            } else {
                builder.append(c)
            }
        }
    }
    builder.append('"')
}

// Constructors kept as top-level helpers: Kotlin does not allow extension functions on the
// companion of an interface unless the companion is explicitly named, and `jsonString("x")` reads
// just as well as `Json.string("x")`.
fun jsonString(value: String): Json.Str = Json.Str(value)

fun jsonNumber(value: Int): Json.Num = Json.Num(value.toDouble(), value.toString())

fun jsonNumber(value: Long): Json.Num = Json.Num(value.toDouble(), value.toString())

fun jsonNumber(value: Double): Json.Num = Json.Num(value, value.toString())

fun jsonBoolean(value: Boolean): Json.Bool = Json.Bool(value)
