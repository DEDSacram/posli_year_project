package cz.bazar.android.util

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Čtení JSON odpovědí backendu. Klíčové je, že chybějící nebo `null` hodnota
 * nikdy nevyhazuje výjimku — rozhraní pak zůstane použitelné i při částečném
 * rozjezdu API nebo u starší verze serveru.
 */
class JTest {

    private fun json(raw: String) = JsonParser.parseString(raw).asJsonObject

    @Test
    fun `str reads text`() {
        assertEquals("Praha", J.str(json("""{"city":"Praha"}"""), "city"))
    }

    @Test
    fun `str returns empty for missing key`() {
        assertEquals("", J.str(json("""{"city":"Praha"}"""), "missing"))
    }

    @Test
    fun `str returns empty for null value`() {
        assertEquals("", J.str(json("""{"city":null}"""), "city"))
    }

    @Test
    fun `str tolerates null object`() {
        assertEquals("", J.str(null, "city"))
    }

    @Test
    fun `numeric accessors read numbers as text`() {
        val o = json("""{"a":5,"b":2.5,"c":7}""")
        assertEquals(5L, J.lng(o, "a"))
        assertEquals(5, J.integer(o, "a"))
        assertEquals(2.5, J.dbl(o, "b", 0.0), 1e-9)
        assertEquals(7L, J.lng(o, "c"))
    }

    @Test
    fun `numeric accessors fall back when key is missing`() {
        val o = json("""{"a":5}""")
        assertEquals(0L, J.lng(o, "zzz"))
        assertEquals(0, J.integer(o, "zzz"))
        assertEquals(1.5, J.dbl(o, "zzz", 1.5), 1e-9)
    }

    @Test
    fun `numeric accessors treat null as missing`() {
        val o = json("""{"a":null}""")
        assertEquals(0L, J.lng(o, "a"))
        assertEquals(9.0, J.dbl(o, "a", 9.0), 1e-9)
    }

    @Test
    fun `num1 formats with one decimal for rating`() {
        assertEquals("4,8", J.num1(json("""{"average":4.75}"""), "average"))
        assertEquals("", J.num1(json("""{}"""), "average"))
    }

    @Test
    fun `bool reads booleans and defaults to false`() {
        val o = json("""{"a":true,"b":false,"c":null}""")
        assertTrue(J.bool(o, "a"))
        assertFalse(J.bool(o, "b"))
        assertFalse(J.bool(o, "c"))
        assertFalse(J.bool(o, "missing"))
    }

    @Test
    fun `obj returns empty object for non object`() {
        val o = json("""{"nested":{"a":1},"scalar":"x"}""")
        assertEquals(1L, J.lng(J.obj(o, "nested"), "a"))
        assertEquals(0, J.obj(o, "scalar").size())
        assertEquals(0, J.obj(o, "missing").size())
    }

    @Test
    fun `data reads paged payload`() {
        val page = json("""{"data":[{"id":1},{"id":2}],"nextCursor":"2"}""")
        val rows = J.data(page)
        assertEquals(2, rows.size)
        assertEquals(2L, J.lng(rows[1], "id"))
        assertEquals("2", J.nextCursor(page))
    }

    @Test
    fun `data returns empty list when payload is not array`() {
        assertTrue(J.data(json("""{"data":null}""")).isEmpty())
        assertTrue(J.data(json("""{}""")).isEmpty())
        assertTrue(J.data(null).isEmpty())
    }

    @Test
    fun `arr reads array field`() {
        val o = json("""{"ids":[1,2,3]}""")
        assertEquals(3, J.longs(o, "ids").size)
        assertEquals(3L, J.longs(o, "ids")[2])
    }

    @Test
    fun `longs ignores non numeric entries`() {
        assertEquals(listOf(1L, 3L), J.longs(json("""{"ids":[1,"x",3]}"""), "ids"))
    }

    @Test
    fun `nextCursor is null on last page`() {
        assertEquals(null, J.nextCursor(json("""{"data":[],"nextCursor":null}""")))
    }

    @Test
    fun `price formats thousands with czech conventions`() {
        // Formátovač vkládá nedělitelnou mezeru (U+00A0 nebo U+202F podle JDK/CLDR),
        // proto skládáme očekávaný řetězec z jednotlivých částí místo literálu.
        fun normalize(s: String): String =
            s.replace('\u00A0', ' ').replace('\u202F', ' ')

        val thousand = "1" + ' ' + "234" + ' ' + "Kč"
        assertEquals(thousand, normalize(J.price(1234)))
        assertEquals("0" + ' ' + "Kč", normalize(J.price(0)))
        assertTrue(J.price(1234).endsWith("Kč"))
    }
}