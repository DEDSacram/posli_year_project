package cz.bazar.android.util

/**
 * Kontrola vstupů před odesláním do API — stejná pravidla jako na webu
 * (`frontend/src/components/validation.ts`), aby se chyby hlásily hned
 * a bez zbytečného síťového požadavku. Čisté funkce → snadno testovatelné.
 */
object Validate {

    /** Chybová hláška, nebo prázdný řetězec, když je hodnota v pořádku. */
    fun email(value: String): String {
        val v = value.trim()
        return if (EMAIL.matches(v)) "" else "Zadejte platný e-mail (např. jan.novak@email.cz)."
    }

    fun password(value: String): String =
        if (value.length >= 8) "" else "Heslo musí mít aspoň 8 znaků."

    fun displayName(value: String): String {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Zobrazované jméno je povinné."
            v.length < 2 -> "Jméno je příliš krátké."
            else -> ""
        }
    }

    fun title(value: String): String {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Název je povinné."
            v.length < 3 -> "Název musí mít aspoň 3 znaky."
            else -> ""
        }
    }

    fun description(value: String): String {
        val v = value.trim()
        return when {
            v.isEmpty() -> "Popis je povinný."
            v.length < 10 -> "Popis musí mít aspoň 10 znaků."
            else -> ""
        }
    }

    fun price(value: String): String {
        if (value.isBlank()) return "Cena je povinná."
        val n = value.trim().toIntOrNull() ?: return "Cena musí být celé číslo v Kč."
        if (n < 1) return "Cena musí být alespoň 1 Kč."
        return ""
    }

    fun city(value: String): String =
        if (value.trim().length < 2) "Zadejte město." else ""

    /** PSČ ve tvaru 12345 nebo 123 45. */
    fun postalCode(value: String): String {
        val v = value.trim()
        return when {
            v.isEmpty() -> "PSČ je povinné."
            POSTAL.matches(v) -> ""
            else -> "PSČ má tvar 110 00."
        }
    }

    /** IBAN bez mezer a pomlček, velkými písmeny. */
    fun ibanCompact(value: String): String =
        value.replace(Regex("[\\s-]"), "").uppercase()

    /** Kontrolní součet mod-97 podle normy ISO 13616. */
    fun ibanValid(value: String): Boolean {
        val v = ibanCompact(value)
        if (!IBAN_FORMAT.matches(v)) return false
        val rearranged = v.substring(4) + v.substring(0, 4)
        val digits = StringBuilder()
        for (c in rearranged) {
            digits.append(if (c.isLetter()) (c.code - 'A'.code + 10).toString() else c.toString())
        }
        var rem = 0L
        for (ch in digits) {
            rem = (rem * 10 + (ch - '0')) % 97
        }
        return rem == 1L
    }

    fun iban(value: String): String {
        val c = ibanCompact(value)
        if (c.isEmpty()) return "IBAN je povinný."
        if (!c.startsWith("CZ")) return "Podporujeme český IBAN (CZ…)."
        if (!IBAN_FORMAT.matches(c)) return "Český IBAN má tvar CZ + 22 číslic (celkem 24 znaků)."
        if (!ibanValid(c)) return "IBAN neprošel kontrolním součtem."
        return ""
    }

    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    private val POSTAL = Regex("^\\d{3} ?\\d{2}$")
    private val IBAN_FORMAT = Regex("^[A-Z]{2}\\d{2}[A-Z0-9]{11,30}$")
}