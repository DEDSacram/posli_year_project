package cz.bazar.android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui

/** Chat: seznam konverzací a vlákno s odesíláním zpráv. */
class ChatActivity : AppCompatActivity() {

    private lateinit var client: BazarClient
    private lateinit var list: ListView
    private lateinit var messages: ListView
    private lateinit var threadBox: View
    private lateinit var title: TextView
    private lateinit var input: EditText
    private var openConv = -1L

    private val convs = ArrayList<JsonObject>()
    private val msgLines = ArrayList<String>()
    private val convLines = ArrayList<String>()
    private lateinit var convAdapter: ArrayAdapter<String>
    private lateinit var msgAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        client = BazarClient(this)
        setTitle(R.string.chats)

        if (!client.isLoggedIn()) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_LONG).show()
            AuthActivity.open(this)
            finish()
            return
        }

        list = findViewById(R.id.list)
        messages = findViewById(R.id.msgs)
        threadBox = findViewById(R.id.thread_box)
        title = findViewById(R.id.conv_title)
        input = findViewById(R.id.input)

        convAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, convLines)
        msgAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, msgLines)
        list.adapter = convAdapter
        messages.adapter = msgAdapter
        list.setOnItemClickListener { _, _, position, _ ->
            convs.getOrNull(position)?.let { openConversation(J.lng(it, "id")) }
        }

        // Zpět: nejprve zavře vlákno, pak ukončí obrazovku.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (threadBox.visibility == View.VISIBLE) {
                        threadBox.visibility = View.GONE
                        list.visibility = View.VISIBLE
                        openConv = -1
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )

        findViewById<Button>(R.id.send).setOnClickListener { send() }
        loadList()

        val conv = intent.getLongExtra("conv", -1)
        if (conv >= 0) openConversation(conv)
    }

    private fun loadList() {
        client.api().conversations().enqueue(
            object : Ui<JsonObject>(this, "Konverzace nenačteny") {
                override fun ok(body: JsonObject) {
                    convs.clear()
                    convs.addAll(J.data(body))
                    convLines.clear()
                    convs.forEach { c ->
                        val listingId = J.lng(c, "listingId")
                        convLines.add(
                            getString(R.string.conversation_placeholder, listingId, J.lng(c, "id")),
                        )
                    }
                    convAdapter.notifyDataSetChanged()

                    // Nadpis doplníme z katalogu; do té doby ukazujeme ID inzerátu.
                    convs.forEachIndexed { index, c ->
                        val listingId = J.lng(c, "listingId")
                        client.api().listing(listingId).enqueue(
                            object : Ui<JsonObject>(this@ChatActivity, "") {
                                override fun ok(listing: JsonObject) {
                                    if (index >= convLines.size) return
                                    convLines[index] = getString(
                                        R.string.conversation_placeholder,
                                        listingId,
                                        J.lng(c, "id"),
                                    ) + " · " + J.str(listing, "title")
                                    convAdapter.notifyDataSetChanged()
                                }

                                override fun onFailure(
                                    call: retrofit2.Call<JsonObject>,
                                    t: Throwable,
                                ) = Unit
                            },
                        )
                    }
                }
            },
        )
    }

    private fun openConversation(convId: Long) {
        openConv = convId
        list.visibility = View.GONE
        threadBox.visibility = View.VISIBLE
        title.text = getString(R.string.conversation_title, convId)
        loadMessages()
    }

    private fun loadMessages() {
        client.api().messages(openConv, null, 20).enqueue(
            object : Ui<JsonObject>(this, "Zprávy nenačteny") {
                override fun ok(page: JsonObject) {
                    val me = client.userId()
                    msgLines.clear()
                    // API vrací nejnovější zprávu první — otočíme pro čtení.
                    J.data(page).asReversed().forEach { m ->
                        val mine = me != null && me == J.lng(m, "senderId")
                        msgLines.add((if (mine) "Já: " else "Oni: ") + J.str(m, "bodyText"))
                    }
                    msgAdapter.notifyDataSetChanged()
                }
            },
        )
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || openConv < 0) return
        client.api().message(mapOf("conversationId" to openConv, "bodyText" to text)).enqueue(
            object : Ui<JsonObject>(this, "Zpráva se neposlala") {
                override fun ok(body: JsonObject) {
                    input.setText("")
                    loadMessages()
                }
            },
        )
    }

    companion object {
        fun openThread(ctx: Context, convId: Long) {
            ctx.startActivity(
                Intent(ctx, ChatActivity::class.java).putExtra("conv", convId),
            )
        }
    }
}