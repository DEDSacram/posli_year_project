package cz.bazar.android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui

/**
 * Veřejný profil prodejce: odznaky (včetně zásluh odvozených z historie),
 * hodnocení a recenze.
 */
class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        val client = BazarClient(this)
        val id = intent.getLongExtra("id", -1)

        client.api().user(id).enqueue(
            object : Ui<JsonObject>(this, "Profil nenačten") {
                override fun ok(u: JsonObject) {
                    findViewById<TextView>(R.id.name).text = J.str(u, "displayName")
                }
            },
        )

        client.api().badges(id).enqueue(
            object : Ui<JsonObject>(this, "") {
                override fun ok(o: JsonObject) {
                    findViewById<TextView>(R.id.badges).text = badges(o)
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )

        client.api().rating(id).enqueue(
            object : Ui<JsonObject>(this, "") {
                override fun ok(o: JsonObject) {
                    val count = J.lng(o, "count")
                    findViewById<TextView>(R.id.rating).text = if (count > 0) {
                        "★ ${J.num1(o, "average")} ($count)"
                    } else {
                        getString(R.string.no_rating_yet)
                    }
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )

        client.api().reviews(id, null, 20).enqueue(
            object : Ui<JsonObject>(this, "") {
                override fun ok(page: JsonObject) {
                    val box = findViewById<LinearLayout>(R.id.reviews)
                    val reviews = J.data(page)
                    if (reviews.isEmpty()) {
                        box.addView(TextView(this@ProfileActivity).apply { setText(R.string.no_reviews) })
                        return
                    }
                    for (r in reviews) {
                        val text = buildString {
                            append("★ ").append(J.str(r, "rating")).append(" — ").append(J.str(r, "text"))
                            val response = J.str(r, "responseText")
                            if (response.isNotEmpty()) append("\n↳ ").append(response)
                            when (J.str(r, "disputeStatus")) {
                                "open" -> append("\n(námitka se řeší)")
                                "rejected" -> append("\n(námitka zamítnuta)")
                                else -> Unit
                            }
                        }
                        box.addView(TextView(this@ProfileActivity).apply {
                            this.text = text
                            setPadding(0, 8, 0, 8)
                        })
                    }
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )
    }

    /** Odznaky: typ prodejce, ověření administrátorem a zásluhy z historie obchodů. */
    private fun badges(o: JsonObject): String = buildList {
        add(if (J.bool(o, "trader")) "Obchodník" else "Soukromník")
        if (J.bool(o, "verifiedTrader")) add("✓ Ověřený obchodník")
        if (J.bool(o, "trustedSeller")) add("Důvěryhodný prodejce")
        if (J.bool(o, "highlyRated")) add("Vysoké hodnocení")
        if (J.bool(o, "experienced")) add("Zkušený prodejce")
        if (J.bool(o, "cleanRecord")) add("Bez stížností")
    }.joinToString(" · ")

    companion object {
        fun open(ctx: Context, userId: Long) {
            ctx.startActivity(
                Intent(ctx, ProfileActivity::class.java).putExtra("id", userId),
            )
        }
    }
}