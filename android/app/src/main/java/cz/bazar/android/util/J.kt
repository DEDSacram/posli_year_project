package cz.bazar.android.util

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.NumberFormat
import java.util.Locale

/**
 * Bezpečné čtení Gson [JsonObject] a formátování pro UI.
 * Backend posílá některá pole jako `null`, takže tu nic nespadne —
 * chybějící hodnota je prázdný řetězec / nula.
 */
object J {

    private val CS_CZK: NumberFormat = NumberFormat.getIntegerInstance(Locale("cs", "CZ"))

    /** Text hodnoty; prázdný řetězec, pokud chybí nebo je null. */
    fun str(o: JsonObject?, key: String): String {
        val e = element(o, key) ?: return ""
        return try {
            e.asString
        } catch (_: Exception) {
            ""
        }
    }

    /** Text hodnoty, ale jako číslo s jedním desetinným místem (hodnocení, souřadnice). */
    fun num1(o: JsonObject?, key: String): String {
        val d = dbl(o, key, Double.NaN)
        return if (d.isNaN()) "" else String.format(Locale("cs", "CZ"), "%.1f", d)
    }

    fun lng(o: JsonObject?, key: String): Long = num(o, key)?.toLong() ?: 0L

    fun integer(o: JsonObject?, key: String): Int = num(o, key)?.toInt() ?: 0

    fun dbl(o: JsonObject?, key: String, def: Double): Double = num(o, key)?.toDouble() ?: def

    fun bool(o: JsonObject?, key: String): Boolean {
        val e = element(o, key) ?: return false
        return try {
            e.asBoolean
        } catch (_: Exception) {
            false
        }
    }

    fun obj(o: JsonObject?, key: String): JsonObject {
        val e = element(o, key) ?: return JsonObject()
        return if (e.isJsonObject) e.asJsonObject else JsonObject()
    }

    /** Obsah stránkovaného výpisu `{data: [...]}` jako seznam objektů. */
    fun data(page: JsonObject?): List<JsonObject> = arr(page, "data")

    fun arr(o: JsonObject?, key: String): List<JsonObject> {
        val e = element(o, key) ?: return emptyList()
        if (!e.isJsonArray) return emptyList()
        val out = ArrayList<JsonObject>()
        for (item in e.asJsonArray) {
            if (item.isJsonObject) out.add(item.asJsonObject)
        }
        return out
    }

    /** Pole čísel jako `List<Long>` (např. `ids` u oblíbených). */
    fun longs(o: JsonObject?, key: String): List<Long> {
        val e = element(o, key) ?: return emptyList()
        if (!e.isJsonArray) return emptyList()
        val out = ArrayList<Long>()
        for (item in e.asJsonArray) {
            try {
                out.add(item.asLong)
            } catch (_: Exception) {
                // nečíselný prvek přeskočíme
            }
        }
        return out
    }

    fun nextCursor(page: JsonObject?): String? {
        val e = element(page, "nextCursor") ?: return null
        return try {
            e.asString
        } catch (_: Exception) {
            null
        }
    }

    fun price(czk: Long): String = CS_CZK.format(czk) + " Kč"

    /** Česká hláška z těla chyby `{code, messageCs}`; jinak fallback s HTTP stavem. */
    fun errMsg(r: retrofit2.Response<*>, fallback: String): String {
        val tail = "$fallback (${r.code()})"
        return try {
            val body = r.errorBody()?.string() ?: return tail
            val json = JsonParser.parseString(body)
            if (!json.isJsonObject) return tail
            val cs = str(json.asJsonObject, "messageCs")
            cs.ifEmpty { tail }
        } catch (_: Exception) {
            tail
        }
    }

    private fun element(o: JsonObject?, key: String): JsonElement? {
        if (o == null || !o.has(key) || o.get(key).isJsonNull) return null
        return o.get(key)
    }

    private fun num(o: JsonObject?, key: String): Number? {
        val e = element(o, key) ?: return null
        return try {
            e.asNumber
        } catch (_: Exception) {
            null
        }
    }
}