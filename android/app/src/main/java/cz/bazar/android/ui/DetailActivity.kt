package cz.bazar.android.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.ImageLoader
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui

/**
 * Detail inzerátu: galerie, akce (koupit / zájem / nabídka / oblíbené),
 * informace o spotřebitelských právech, recenze a možnost se ohradit proti hodnocení.
 */
class DetailActivity : AppCompatActivity() {

    private lateinit var client: BazarClient
    private var listingId = -1L
    private var item: JsonObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        client = BazarClient(this)
        listingId = intent.getLongExtra("id", -1)
        load()
    }

    override fun onResume() {
        super.onResume()
        client = BazarClient(this)
    }

    private fun load() {
        client.api().listing(listingId).enqueue(
            object : Ui<JsonObject>(this, "Inzerát nenačten") {
                override fun ok(data: JsonObject) {
                    item = data
                    render()
                }
            },
        )
    }

    private fun render() {
        val item = item ?: return
        title = J.str(item, "title")
        findViewById<TextView>(R.id.title).text = J.str(item, "title")
        findViewById<TextView>(R.id.price).text = J.price(J.lng(item, "priceCzk"))
        findViewById<TextView>(R.id.meta).text =
            J.str(item, "condition") + " · " + J.str(item, "category") + " · " + J.str(item, "city")
        findViewById<TextView>(R.id.desc).text = J.str(item, "description")

        val seller = J.str(item, "sellerName").ifEmpty { "#${J.lng(item, "sellerId")}" }
        val sellerView = findViewById<TextView>(R.id.seller)
        sellerView.text = getString(R.string.seller_label, seller)
        val sellerId = J.lng(item, "sellerId")
        sellerView.setOnClickListener { ProfileActivity.open(this, sellerId) }

        renderGallery(item)
        renderRights(item)

        val me = client.userId()
        val owner = me != null && me == sellerId
        findViewById<View>(R.id.owner_box).visibility = if (owner) View.VISIBLE else View.GONE
        listOf(R.id.btn_buy, R.id.btn_interest, R.id.btn_fav, R.id.btn_contact, R.id.btn_offer_row)
            .forEach { findViewById<View>(it).visibility = if (owner) View.GONE else View.VISIBLE }

        findViewById<Button>(R.id.btn_buy).setOnClickListener { buy() }
        findViewById<Button>(R.id.btn_interest).setOnClickListener {
            openChatWith(getString(R.string.msg_interest))
        }
        findViewById<Button>(R.id.btn_contact).setOnClickListener { openChatWith(null) }
        findViewById<Button>(R.id.btn_fav).setOnClickListener { toggleFav() }
        findViewById<Button>(R.id.btn_offer).setOnClickListener { offer() }
        findViewById<Button>(R.id.btn_edit).setOnClickListener { EditActivity.open(this, listingId) }
        findViewById<Button>(R.id.btn_delete).setOnClickListener { confirmDelete() }
        findViewById<Button>(R.id.btn_report).setOnClickListener { sendReport() }

        refreshFavLabel()
        loadRating(sellerId)
        loadReviews(sellerId)
    }

    private fun renderGallery(item: JsonObject) {
        val gallery = findViewById<LinearLayout>(R.id.gallery)
        gallery.removeAllViews()
        val images = J.arr(item, "images")
        if (images.isEmpty()) {
            gallery.addView(TextView(this).apply { setText(R.string.no_photos) })
        }
        for (img in images) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(600, 450)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(0, 0, 8, 0)
            }
            ImageLoader.load(iv, ImageLoader.url(client.baseUrl(), listingId, J.lng(img, "id")))
            gallery.addView(iv)
        }
    }

    /** Upozornění na právo odstoupení (u obchodníka 14 dní dle § 1829 OZ). */
    private fun renderRights(item: JsonObject) {
        client.api().rightsInfo(listingId).enqueue(
            object : Ui<JsonObject>(this, "") {
                override fun ok(info: JsonObject) {
                    findViewById<TextView>(R.id.rights).text = J.str(info, "explanationCs")
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )
    }

    private fun needLogin(): Boolean {
        if (client.isLoggedIn()) return false
        Toast.makeText(this, R.string.login_required, Toast.LENGTH_LONG).show()
        AuthActivity.open(this)
        return true
    }

    private fun buy() {
        if (needLogin()) return
        client.api().reserve(mapOf("listingId" to listingId)).enqueue(
            object : Ui<JsonObject>(this, "Rezervace selhala") {
                override fun ok(tx: JsonObject) {
                    val item = item ?: return
                    openChatWith(
                        getString(
                            R.string.msg_reserve,
                            J.str(item, "title"),
                            J.price(J.lng(item, "priceCzk")),
                            J.lng(tx, "id"),
                        ),
                    )
                }
            },
        )
    }

    /** Otevře konverzaci a případně pošle předvyplněnou zprávu. */
    private fun openChatWith(message: String?) {
        if (needLogin()) return
        client.api().conversation(mapOf("listingId" to listingId)).enqueue(
            object : Ui<JsonObject>(this, "Chat selhal") {
                override fun ok(conv: JsonObject) {
                    val convId = J.lng(conv, "id")
                    if (message.isNullOrEmpty()) {
                        ChatActivity.openThread(this@DetailActivity, convId)
                        return
                    }
                    client.api().message(
                        mapOf("conversationId" to convId, "bodyText" to message),
                    ).enqueue(
                        object : Ui<JsonObject>(this@DetailActivity, "Zpráva se neposlala") {
                            override fun ok(body: JsonObject) =
                                ChatActivity.openThread(this@DetailActivity, convId)
                        },
                    )
                }
            },
        )
    }

    private fun offer() {
        val value = findViewById<EditText>(R.id.offer).text.toString().trim()
        if (value.isEmpty()) {
            Toast.makeText(this, R.string.offer_required, Toast.LENGTH_LONG).show()
            return
        }
        val item = item ?: return
        openChatWith(getString(R.string.msg_offer, value, J.str(item, "title")))
    }

    private fun toggleFav() {
        if (needLogin()) return
        val button = findViewById<Button>(R.id.btn_fav)
        val has = button.tag as? Boolean ?: false
        val callback = object : Ui<JsonObject>(this, "Akce selhala") {
            override fun ok(body: JsonObject) = refreshFavLabel()
        }
        if (has) client.api().removeFavorite(listingId).enqueue(callback)
        else client.api().addFavorite(listingId).enqueue(callback)
    }

    private fun refreshFavLabel() {
        if (!client.isLoggedIn()) return
        client.api().favoriteIds().enqueue(
            object : Ui<JsonObject>(this, "") {
                override fun ok(body: JsonObject) {
                    val has = J.longs(body, "ids").contains(listingId)
                    findViewById<Button>(R.id.btn_fav).apply {
                        tag = has
                        setText(if (has) R.string.in_fav else R.string.add_fav)
                    }
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )
    }

    private fun loadRating(sellerId: Long) {
        client.api().rating(sellerId).enqueue(
            object : Ui<JsonObject>(this, "") {
                override fun ok(body: JsonObject) {
                    val view = findViewById<TextView>(R.id.seller)
                    view.text = "${view.text}  ★ ${J.num1(body, "average")} (${J.lng(body, "count")})"
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )
    }

    private fun loadReviews(sellerId: Long) {
        client.api().reviews(sellerId, null, 10).enqueue(
            object : Ui<JsonObject>(this, "") {
                override fun ok(page: JsonObject) {
                    val box = findViewById<LinearLayout>(R.id.reviews)
                    box.removeAllViews()
                    val reviews = J.data(page)
                    val me = client.userId()
                    if (reviews.isEmpty()) {
                        box.addView(TextView(this@DetailActivity).apply { setText(R.string.no_reviews) })
                        return
                    }
                    for (r in reviews) {
                        box.addView(reviewRow(r))
                        // Prodejce může hodnocení napadnout a odpovědět na ně.
                        if (me != null && J.lng(r, "revieweeId") == me) {
                            box.addView(reviewActions(r))
                        }
                    }
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )
    }

    private fun reviewRow(r: JsonObject): TextView {
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
        return TextView(this).apply {
            this.text = text
            setPadding(0, 8, 0, 8)
        }
    }

    private fun reviewActions(r: JsonObject): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val reviewId = J.lng(r, "id")

        if (J.str(r, "responseText").isEmpty()) {
            box.addView(
                Button(this).apply {
                    setText(R.string.reply)
                    setOnClickListener { replyTo(reviewId) }
                },
            )
        }
        if (J.str(r, "disputeStatus").isEmpty()) {
            box.addView(
                Button(this).apply {
                    setText(R.string.dispute)
                    setOnClickListener { dispute(reviewId) }
                },
            )
        }
        return box
    }

    private fun replyTo(reviewId: Long) {
        val input = EditText(this).apply { hint = getString(R.string.reply_hint) }
        AlertDialog.Builder(this)
            .setTitle(R.string.reply)
            .setView(input)
            .setPositiveButton(R.string.send) { _, _ ->
                client.api().respond(reviewId, mapOf("text" to input.text.toString().trim()))
                    .enqueue(
                        object : Ui<JsonObject>(this, "Odpověď se neuložila") {
                            override fun ok(body: JsonObject) {
                                loadRating(J.lng(item ?: JsonObject(), "sellerId"))
                                load()
                            }
                        },
                    )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Námitka proti hodnocení — rozhodne administrátor. */
    private fun dispute(reviewId: Long) {
        val input = EditText(this).apply { hint = getString(R.string.dispute_hint) }
        AlertDialog.Builder(this)
            .setTitle(R.string.dispute)
            .setMessage(R.string.dispute_explained)
            .setView(input)
            .setPositiveButton(R.string.dispute) { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.length < 10) {
                    Toast.makeText(this, R.string.dispute_too_short, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                client.api().disputeReview(reviewId, mapOf("reason" to reason)).enqueue(
                    object : Ui<JsonObject>(this, "Námitka se neposlala") {
                        override fun ok(body: JsonObject) {
                            Toast.makeText(this@DetailActivity, R.string.dispute_sent, Toast.LENGTH_LONG).show()
                            load()
                        }
                    },
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_listing)
            .setPositiveButton(R.string.delete_listing) { _, _ ->
                client.api().deleteListing(listingId).enqueue(
                    object : Ui<JsonObject>(this, "Smazání selhalo") {
                        override fun ok(body: JsonObject) = finish()
                    },
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sendReport() {
        if (needLogin()) return
        val reason = findViewById<EditText>(R.id.report).text.toString().trim()
        if (reason.isEmpty()) {
            Toast.makeText(this, R.string.report_reason_required, Toast.LENGTH_LONG).show()
            return
        }
        client.api().report(
            mapOf("targetType" to "listing", "targetId" to listingId, "reason" to reason),
        ).enqueue(
            object : Ui<JsonObject>(this, "Hlášení selhalo") {
                override fun ok(body: JsonObject) {
                    findViewById<EditText>(R.id.report).setText("")
                    Toast.makeText(this@DetailActivity, R.string.report_sent, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    companion object {
        fun open(ctx: Context, id: Long) {
            ctx.startActivity(
                Intent(ctx, DetailActivity::class.java).putExtra("id", id),
            )
        }
    }
}