package cz.bazar.android.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui

/**
 * Přehled obchodů: souhrn prodejů včetně získaného hodnocení, stav rezervací
 * a nákupů, potvrzení obchodu, hodnocení prodejce a právo spotřebitele
 * na odstoupení od smlouvy (§ 1829 OZ).
 */
class DealsActivity : AppCompatActivity() {

    private lateinit var client: BazarClient
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private lateinit var progress: ProgressBar
    private var role = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deals)
        client = BazarClient(this)
        setTitle(R.string.deals)

        if (!client.isLoggedIn()) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_LONG).show()
            AuthActivity.open(this)
            finish()
            return
        }

        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)

        findViewById<Spinner>(R.id.role).adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.all), getString(R.string.buys), getString(R.string.sells)),
        )
        findViewById<Spinner>(R.id.role).onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    role = ROLES[position]
                    load()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }

        load()
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        client.api().myTransactions(role).enqueue(
            object : Ui<JsonObject>(this, "Načtení obchodů selhalo") {
                override fun settle() {
                    progress.visibility = View.GONE
                }

                override fun ok(body: JsonObject) {
                    render(J.data(body))
                    loadOverview()
                }
            },
        )
    }

    /** Souhrn prodejů a získaného hodnocení — stejně jako čísla na webu. */
    private fun loadOverview() {
        client.api().dealOverview().enqueue(
            object : Ui<JsonObject>(this, "") {
                override fun ok(body: JsonObject) {
                    val seller = J.obj(body, "asSeller")
                    findViewById<TextView>(R.id.summary).text = buildString {
                        append(getString(R.string.summary_sales))
                        append(" ${J.lng(seller, "completed")}")
                        append(" · ${getString(R.string.turnover)} ${J.price(J.lng(seller, "revenueCzk"))}\n")
                        val ratingCount = J.lng(seller, "ratingCount")
                        append(getString(R.string.earned_rating))
                        append(if (ratingCount > 0) {
                            " ${J.num1(seller, "ratingAverage")} ★ ($ratingCount×)"
                        } else {
                            " —"
                        })
                        val waiting = J.lng(seller, "reserved")
                        if (waiting > 0) append("\n${getString(R.string.pending_sales)} $waiting")
                    }
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )
    }

    private fun render(items: List<JsonObject>) {
        list.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if (items.isEmpty()) {
            empty.text = getString(R.string.no_deals)
            return
        }

        for (tx in items) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
            }
            val title = J.str(tx, "listingTitle").ifEmpty { getString(R.string.listing_fallback, J.lng(tx, "listingId")) }
            box.addView(
                TextView(this).apply {
                    text = title
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setOnClickListener { DetailActivity.open(this@DealsActivity, J.lng(tx, "listingId")) }
                },
            )
            box.addView(
                TextView(this).apply {
                    val side = if (J.str(tx, "role") == "buyer") getString(R.string.buy) else getString(R.string.sell)
                    text = "$side · ${J.price(J.lng(tx, "priceCzk"))} · ${statusLabel(tx)}"
                    textSize = 13f
                },
            )

            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            when {
                J.str(tx, "status") == "reserved" -> {
                    actions.addView(
                        action(R.string.confirm_deal) {
                            act(client.api().complete(J.lng(tx, "id")), getString(R.string.deal_done))
                        },
                    )
                    actions.addView(
                        action(R.string.cancel_reservation) {
                            act(client.api().cancel(J.lng(tx, "id")), getString(R.string.reservation_cancelled))
                        },
                    )
                }
                J.str(tx, "status") == "completed" && J.str(tx, "role") == "buyer" && !J.bool(tx, "hasReview") -> {
                    actions.addView(action(R.string.review_seller) { askForReview(J.lng(tx, "id")) })
                }
            }
            // Právo odstoupení kupujícího u obchodníka do 14 dnů od dokončení.
            if (J.str(tx, "status") == "completed" && J.str(tx, "role") == "buyer" && J.str(tx, "sellerType") == "trader") {
                actions.addView(action(R.string.withdraw) { confirmWithdrawal(J.lng(tx, "id")) })
            }
            if (actions.childCount > 0) box.addView(actions)
            list.addView(box)
        }
    }

    private fun statusLabel(tx: JsonObject): String {
        val status = J.str(tx, "status")
        return getString(
            when (status) {
                "reserved" -> R.string.st_reserved
                "completed" -> R.string.st_completed
                "cancelled" -> R.string.st_cancelled
                "withdrawn" -> R.string.st_withdrawn
                else -> R.string.st_reserved
            },
        )
    }

    private fun action(label: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = getString(label)
            setOnClickListener { onClick() }
        }

    private fun act(call: retrofit2.Call<JsonObject>, success: String) {
        call.enqueue(
            object : Ui<JsonObject>(this, "Akce selhala") {
                override fun ok(body: JsonObject) {
                    Toast.makeText(this@DealsActivity, success, Toast.LENGTH_SHORT).show()
                    load()
                }
            },
        )
    }

    private fun askForReview(txId: Long) {
        val input = EditText(this).apply {
            hint = getString(R.string.review_hint)
            minLines = 3
        }
        val stars = EditText(this).apply {
            hint = getString(R.string.stars_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 0, 32, 0)
            addView(stars)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.review_seller)
            .setView(box)
            .setPositiveButton(R.string.send) { _, _ ->
                val rating = stars.text.toString().trim().toIntOrNull()
                val text = input.text.toString().trim()
                when {
                    rating == null || rating !in 1..5 ->
                        Toast.makeText(this, R.string.stars_invalid, Toast.LENGTH_LONG).show()
                    text.isEmpty() ->
                        Toast.makeText(this, R.string.review_text_required, Toast.LENGTH_LONG).show()
                    else -> act(
                        client.api().review(txId, mapOf("rating" to rating, "text" to text)),
                        getString(R.string.review_sent),
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Nejdřív ověříme, zda je právo skutečně uplatnitelné, pak se zeptáme na důvod. */
    private fun confirmWithdrawal(txId: Long) {
        client.api().withdrawalRights(txId).enqueue(
            object : Ui<JsonObject>(this, "Právo na odstoupení se nepodařilo ověřit") {
                override fun ok(body: JsonObject) {
                    if (!J.bool(body, "eligible")) {
                        Toast.makeText(
                            this@DealsActivity,
                            J.str(body, "explanationCs").ifEmpty { getString(R.string.withdrawal_not_applies) },
                            Toast.LENGTH_LONG,
                        ).show()
                        return
                    }
                    val reason = EditText(this@DealsActivity).apply {
                        hint = getString(R.string.withdrawal_reason_hint)
                    }
                    AlertDialog.Builder(this@DealsActivity)
                        .setTitle(getString(R.string.withdraw))
                        .setMessage(J.str(body, "explanationCs"))
                        .setView(reason)
                        .setPositiveButton(R.string.confirm_withdrawal) { _, _ ->
                            act(
                                client.api().withdraw(txId, mapOf("reason" to reason.text.toString())),
                                getString(R.string.withdrawn_done),
                            )
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            },
        )
    }

    companion object {
        private val ROLES = arrayOf("all", "buyer", "seller")

        fun open(ctx: Context) {
            ctx.startActivity(Intent(ctx, DealsActivity::class.java))
        }
    }
}