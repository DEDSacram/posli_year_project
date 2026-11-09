package cz.bazar.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validace vstupů před odesláním do API — musí souhlasit s pravidly backendu. */
class ValidateTest {

    @Test
    fun `email accepts valid addresses`() {
        assertEquals("", Validate.email("jan.novak@email.cz"))
        assertEquals("", Validate.email("  a@b.co  "))
    }

    @Test
    fun `email rejects malformed addresses`() {
        assertTrue(Validate.email("").isNotEmpty())
        assertTrue(Validate.email("jan@").isNotEmpty())
        assertTrue(Validate.email("jan@email").isNotEmpty())
        assertTrue(Validate.email("jan email@email.cz").isNotEmpty())
    }

    @Test
    fun `password requires eight characters`() {
        assertTrue(Validate.password("kratke").isNotEmpty())
        assertEquals("", Validate.password("dlouheheslo"))
    }

    @Test
    fun `displayName requires at least two characters`() {
        assertTrue(Validate.displayName("").isNotEmpty())
        assertTrue(Validate.displayName("J").isNotEmpty())
        assertEquals("", Validate.displayName("Jan"))
    }

    @Test
    fun `title requires at least three characters`() {
        assertTrue(Validate.title("ab").isNotEmpty())
        assertEquals("", Validate.title("Stole"))
    }

    @Test
    fun `description requires at least ten characters`() {
        assertTrue(Validate.description("krátké").isNotEmpty())
        assertEquals("", Validate.description("Dřevěný stůl v dobrém stavu."))
    }

    @Test
    fun `price accepts whole positive numbers only`() {
        assertEquals("", Validate.price("1500"))
        assertEquals("", Validate.price(" 42 "))
        assertTrue(Validate.price("").isNotEmpty())
        assertTrue(Validate.price("0").isNotEmpty())
        assertTrue(Validate.price("-5").isNotEmpty())
        assertTrue(Validate.price("12.50").isNotEmpty())
        assertTrue(Validate.price("abc").isNotEmpty())
    }

    @Test
    fun `city requires two characters`() {
        assertTrue(Validate.city("P").isNotEmpty())
        assertEquals("", Validate.city("Praha"))
    }

    @Test
    fun `postalCode accepts both czech formats`() {
        assertEquals("", Validate.postalCode("11000"))
        assertEquals("", Validate.postalCode("110 00"))
        assertTrue(Validate.postalCode("1100").isNotEmpty())
        assertTrue(Validate.postalCode("").isNotEmpty())
    }

    @Test
    fun `ibanCompact strips separators and uppercases`() {
        assertEquals("CZ6508000000192000145399", Validate.ibanCompact("cz65 0800-0000 1920 0014 5399"))
    }

    @Test
    fun `ibanValid verifies the mod-97 checksum`() {
        // Referenční český IBAN s platným kontrolním součtem.
        assertTrue(Validate.ibanValid("CZ6508000000192000145399"))
        assertTrue(Validate.ibanValid("cz65 0800 0000 1920 0014 5399"))
        // Změna jedné číslice součet zruší.
        assertFalse(Validate.ibanValid("CZ6508000000192000145398"))
    }

    @Test
    fun `iban reports czech specific problems`() {
        assertTrue(Validate.iban("").isNotEmpty())
        assertTrue(Validate.iban("DE89370400440532013000").isNotEmpty())
        assertTrue(Validate.iban("CZ6508").isNotEmpty())
        assertTrue(Validate.iban("CZ6508000000192000145398").contains("součtem"))
        assertEquals("", Validate.iban("CZ6508000000192000145399"))
    }
}