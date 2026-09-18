package co.omnimusic.core.json

/**
 * A small recursive-descent JSON parser.
 *
 * It is intentionally strict about structure (it rejects trailing garbage and unterminated
 * literals) but lenient about whitespace, which is what the public music APIs emit.
 */
object JsonParser {

    fun parse(input: String): Json {
        val p = Scanner(input)
        p.skipWhitespace()
        val value = p.parseValue()
        p.skipWhitespace()
        if (!p.eof()) throw JsonException("Unexpected trailing content '${p.peek()}'", p.position)
        return value
    }

    /** Convenience for call sites that would rather get `null` than an exception. */
    fun parseOrNull(input: String): Json? = try {
        parse(input)
    } catch (e: JsonException) {
        null
    }

    private class Scanner(private val src: String) {
        var position: Int = 0
            private set

        fun eof(): Boolean = position >= src.length

        fun peek(): Char = if (eof()) throw JsonException("Unexpected end of input", position) else src[position]

        fun next(): Char {
            val c = peek()
            position++
            return c
        }

        fun skipWhitespace() {
            while (!eof()) {
                when (src[position]) {
                    ' ', '\t', '\n', '\r' -> position++
                    else -> return
                }
            }
        }

        fun parseValue(): Json {
            skipWhitespace()
            return when (val c = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> Json.Str(parseString())
                't' -> parseLiteral("true", Json.Bool(true))
                'f' -> parseLiteral("false", Json.Bool(false))
                'n' -> parseLiteral("null", Json.Null)
                else -> if (c == '-' || c in '0'..'9') parseNumber() else throw JsonException("Unexpected character '$c'", position)
            }
        }

        private fun parseLiteral(word: String, value: Json): Json {
            if (src.startsWith(word, position)) {
                position += word.length
                return value
            }
            throw JsonException("Expected '$word'", position)
        }

        private fun parseObject(): Json {
            expect('{')
            val fields = LinkedHashMap<String, Json>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return Json.Obj(fields)
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw JsonException("Expected a quoted object key", position)
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                // Later duplicate keys win, matching what most JSON consumers do.
                fields[key] = value
                skipWhitespace()
                when (val c = next()) {
                    ',' -> Unit
                    '}' -> return Json.Obj(fields)
                    else -> throw JsonException("Expected ',' or '}' but found '$c'", position - 1)
                }
            }
        }

        private fun parseArray(): Json {
            expect('[')
            val items = ArrayList<Json>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return Json.Arr(items)
            }
            while (true) {
                items += parseValue()
                skipWhitespace()
                when (val c = next()) {
                    ',' -> Unit
                    ']' -> return Json.Arr(items)
                    else -> throw JsonException("Expected ',' or ']' but found '$c'", position - 1)
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (eof()) throw JsonException("Unterminated string", position)
                val c = next()
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> out.append(parseEscape())
                    else -> out.append(c)
                }
            }
        }

        private fun parseEscape(): Char {
            val start = position
            val c = next()
            return when (c) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (position + 4 > src.length) throw JsonException("Truncated unicode escape", start)
                    val hex = src.substring(position, position + 4)
                    val code = hex.toIntOrNull(16) ?: throw JsonException("Invalid unicode escape '\\u$hex'", start)
                    position += 4
                    code.toChar()
                }

                else -> throw JsonException("Invalid escape '\\$c'", start - 1)
            }
        }

        private fun parseNumber(): Json {
            val start = position
            if (peek() == '-') position++
            while (!eof() && src[position] in '0'..'9') position++
            if (!eof() && src[position] == '.') {
                position++
                while (!eof() && src[position] in '0'..'9') position++
            }
            if (!eof() && (src[position] == 'e' || src[position] == 'E')) {
                position++
                if (!eof() && (src[position] == '+' || src[position] == '-')) position++
                while (!eof() && src[position] in '0'..'9') position++
            }
            val raw = src.substring(start, position)
            val value = raw.toDoubleOrNull() ?: throw JsonException("Malformed number '$raw'", start)
            return Json.Num(value, raw)
        }

        private fun expect(c: Char) {
            if (eof() || src[position] != c) {
                throw JsonException("Expected '$c'", position)
            }
            position++
        }
    }
}
