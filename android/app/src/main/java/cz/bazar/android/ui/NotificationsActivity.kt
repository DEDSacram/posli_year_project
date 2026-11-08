package cz.bazar.android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui

/**
 * Upozornění na zlevnění inzerátů uložených do oblíbených.
 * Backend hlídá, aby se stejné zlevnění neopakovalo; od čtení je odlišujeme
 * jen vizuálně, server si pamatuje stav `read`.
 */
class NotificationsActivity : AppCompatActivity() {

    private lateinit var client: BazarClient
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var count: TextView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        client = BazarClient(this)
        setTitle(R.string.notifications)

        if (!client.isLoggedIn()) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_LONG).show()
            AuthActivity.open(this)
            finish()
            return
        }

        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        count = findViewById(R.id.count)
        progress = findViewById(R.id.progress)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        list.adapter = adapter

        list.setOnItemClickListener { _, _, position, _ ->
            val id = listingIds.getOrNull(position) ?: return@setOnItemClickListener
            notificationIds.getOrNull(position)?.let { markRead(it) }
            DetailActivity.open(this, id)
        }

        findViewById<Button>(R.id.read_all).setOnClickListener {
            client.api().markAllNotificationsRead().enqueue(
                object : Ui<JsonObject>(this, "Označení selhalo") {
                    override fun ok(body: JsonObject) {
                        Toast.makeText(
                            this@NotificationsActivity,
                            getString(R.string.marked_read, J.integer(body, "marked")),
                            Toast.LENGTH_SHORT,
                        ).show()
                        load()
                    }
                },
            )
        }

        load()
    }

    private val listingIds = ArrayList<Long>()
    private val notificationIds = ArrayList<Long>()

    private fun load() {
        progress.visibility = View.VISIBLE
        client.api().notifications(null).enqueue(
            object : Ui<JsonObject>(this, "Upozornění se nenačetla") {
                override fun settle() {
                    progress.visibility = View.GONE
                }

                override fun ok(body: JsonObject) {
                    val data = J.data(body)
                    listingIds.clear()
                    notificationIds.clear()
                    adapter.clear()

                    var unread = 0
                    for (n in data) {
                        val isUnread = !J.bool(n, "read")
                        if (isUnread) unread++
                        listingIds.add(J.lng(n, "listingId"))
                        notificationIds.add(J.lng(n, "id"))

                        val old = J.str(n, "title")
                        val from = J.lng(n, "oldPriceCzk")
                        val to = J.lng(n, "newPriceCzk")
                        val prices = if (from > 0 && to > 0) {
                            " · ${J.price(from)} → ${J.price(to)}"
                        } else {
                            ""
                        }
                        adapter.add((if (isUnread) "● " else "○ ") + old + prices)
                    }

                    count.text = getString(R.string.notifications_count, unread)
                    empty.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
                }
            },
        )
    }

    private fun markRead(id: Long) {
        client.api().markNotificationRead(id).enqueue(
            object : Ui<JsonObject>(this, "") {
                override fun ok(body: JsonObject) = load()

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )
    }

    companion object {
        fun open(ctx: Context) {
            ctx.startActivity(Intent(ctx, NotificationsActivity::class.java))
        }
    }
}