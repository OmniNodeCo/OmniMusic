package co.omnimusic.core.json

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonParserTest {

    fun testParsesNestedDocument() {
        val json = JsonParser.parse(
            """{"data":[{"id":27,"name":"Daft Punk","fans":4300000}],"total":1,"next":null}"""
        )
        assertEquals("Daft Punk", json["data"][0]["name"].asStringOrNull())
        assertEquals(27L, json["data"][0]["id"].asLongOrNull())
        assertEquals(1, json["total"].asIntOrNull())
        assertTrue(json["next"].isNull)
    }

    fun testHandlesEveryEscapeAndUnicode() {
        val json = JsonParser.parse("""{"s":"a\"b\\c\/d\b\f\n\r\t\u00e9\u2603"}""")
        assertEquals("a\"b\\c/d\b\u000C\n\r\té☃", json["s"].asStringOrNull())
    }

    fun testAcceptsWhitespaceAndEmptyContainers() {
        val json = JsonParser.parse("  { \"a\" : [ ] , \"b\" : { } }  ")
        assertTrue(json["a"].asArrayOrEmpty().isEmpty())
        assertTrue(json["b"].asObjectOrNull()!!.fields.isEmpty())
    }

    fun testKeepsLargeIntegralLiteralsExact() {
        // Deezer track ids exceed what a Double can represent precisely for some ranges.
        val json = JsonParser.parse("""{"id":136889400,"big":9007199254740993}""")
        assertEquals(136889400L, json["id"].asLongOrNull())
        assertEquals(9007199254740993L, json["big"].asLongOrNull())
    }

    fun testParsesNumbersOfEveryShape() {
        val json = JsonParser.parse("""{"a":-1,"b":2.5,"c":1e3,"d":-2E-2}""")
        assertEquals(-1L, json["a"].asLongOrNull())
        assertEquals(2.5, (json["b"] as Json.Num).value)
        assertEquals(1000.0, (json["c"] as Json.Num).value)
        assertEquals(-0.02, (json["d"] as Json.Num).value)
    }

    fun testRejectsTrailingGarbage() {
        val error = assertFailsWith<JsonException> { JsonParser.parse("{}{}") }
        assertTrue(error.position > 0)
    }

    fun testRejectsUnterminatedString() {
        assertFailsWith<JsonException> { JsonParser.parse("""{"a":"oops""") }
    }

    fun testRejectsMissingComma() {
        assertFailsWith<JsonException> { JsonParser.parse("""{"a":1 "b":2}""") }
    }

    fun testParseOrNullSwallowsBadInput() {
        assertNull(JsonParser.parseOrNull("{not json"))
        assertNull(JsonParser.parseOrNull(""))
    }

    fun testAccessorsTolerateMissingAndWrongTypedKeys() {
        val json = JsonParser.parse("""{"n":42,"s":"x","b":true}""")
        assertEquals("default", json["missing"].asStringOrDefault("default"))
        assertEquals(7, json["missing"].asIntOrDefault(7))
        assertEquals("42", json["n"].asStringOrNull())
        assertTrue(json["b"].asBooleanOrDefault(false))
        assertFalse(json["s"].asBooleanOrDefault(false))
        assertTrue(json["missing"].asArrayOrEmpty().isEmpty())
    }

    fun testWriterRoundTripsEscapesAndStructure() {
        val original = """{"a":[1,2],"b":"q\"z","c":{"d":null}}"""
        val parsed = JsonParser.parse(original)
        val rewritten = JsonParser.parse(JsonWriter.write(parsed))
        assertEquals(parsed, rewritten)
    }

    fun testWriterEscapesControlCharacters() {
        val written = JsonWriter.write(jsonObject("s" to jsonString("line\nbreak\u0001")))
        assertEquals("""{"s":"line\nbreak\u0001"}""", written)
    }

    fun testDuplicateKeysLastWins() {
        val json = JsonParser.parse("""{"a":1,"a":2}""")
        assertEquals(2L, json["a"].asLongOrNull())
    }
}
