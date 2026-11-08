package cz.bazar.android.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui
import okhttp3.ResponseBody
import java.util.Calendar

/**
 * Administrace: moderační fronta, námitky na hodnocení, ověření obchodníků,
 * výpis uživatelů, audit log a daňové výkazy (DAC7).
 */
class AdminActivity : AppCompatActivity() {

    private lateinit var client: BazarClient
    private lateinit var list: ListView
    private lateinit var dac7Box: View
    private lateinit var adapter: ArrayAdapter<String>

    /** Aktuálně zobrazená sekce; zároveň určuje chování kliknutí na řádek. */
    private var kind = KIND_QUEUE
    private var sectionTitle = R.string.queue_title
    private val rows = ArrayList<JsonObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)
        client = BazarClient(this)
        setTitle(R.string.admin)

        if (!client.isLoggedIn()) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        list = findViewById(R.id.list)
        dac7Box = findViewById(R.id.dac7_box)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> onRowClick(position) }

        findViewById<Spinner>(R.id.section).apply {
            adapter = ArrayAdapter(
                this@AdminActivity,
                android.R.layout.simple_spinner_dropdown_item,
                SECTIONS.map { getString(it) },
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) = show(KINDS[position], SECTIONS[position])

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

        setUpDac7()
    }

    private fun setUpDac7() {
        val year = findViewById<EditText>(R.id.year)
        year.setText(Calendar.getInstance().get(Calendar.YEAR).toString())
        findViewById<Spinner>(R.id.format).adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, arrayOf("csv", "xml"),
        )
        findViewById<Button>(R.id.btn_summary_export).setOnClickListener { export(false) }
        findViewById<Button>(R.id.btn_detail_export).setOnClickListener { export(true) }
    }

    /** Sekce `dac7` má vlastní panel, ostatní jsou prostý seznam s akcemi na řádek. */
    private fun show(nextKind: String, nextTitle: Int) {
        kind = nextKind
        sectionTitle = nextTitle
        title = getString(nextTitle)

        if (kind == KIND_DAC7) {
            dac7Box.visibility = View.VISIBLE
            list.visibility = View.GONE
            loadDac7Summary()
            return
        }
        dac7Box.visibility = View.GONE
        list.visibility = View.VISIBLE
        loadList()
    }

    private fun loadList() {
        rows.clear()
        adapter.clear()
        val call = when (kind) {
            KIND_DISPUTES -> client.api().disputes(50)
            KIND_VERIFICATIONS -> client.api().verifications()
            KIND_USERS -> client.api().users("", 50)
            KIND_AUDIT -> client.api().audit(50, null)
            else -> client.api().queue(50)
        }
        call.enqueue(
            object : Ui<JsonObject>(this, getString(sectionTitle) + " — načtení selhalo") {
                override fun ok(body: JsonObject) {
                    rows.clear()
                    rows.addAll(J.data(body))
                    refreshAdapter()
                }
            },
        )
    }

    private fun refreshAdapter() {
        adapter.clear()
        rows.forEach { adapter.add(label(it)) }
        adapter.notifyDataSetChanged()
    }

    private fun label(r: JsonObject): String = when (kind) {
        KIND_DISPUTES -> "#${J.lng(r, "id")} · recenze #${J.lng(r, "reviewId")} · ${J.str(r, "reason")}"
        KIND_VERIFICATIONS -> "#${J.lng(r, "userId")} ${J.str(r, "displayName")}" +
            " · IČo: ${yesNo(J.bool(r, "hasIco"))}" +
            " · prohlášení: ${yesNo(J.bool(r, "selfCert"))}"
        KIND_USERS -> "${J.str(r, "displayName")} · ${J.str(r, "email")} · ${J.str(r, "sellerType")}" +
            if (J.bool(r, "blocked")) " · ZABLOKOVÁN" else "" +
                if (J.bool(r, "isAdmin")) " · admin" else ""
        KIND_AUDIT -> "${J.str(r, "createdAt")} · ${J.str(r, "action")}"
        else -> "#${J.lng(r, "id")} · ${J.str(r, "targetType")}:${J.lng(r, "targetId")}" +
            " · ${J.str(r, "reason")}" + if (J.bool(r, "trustedFlagger")) " (důvěryhodný hlásič)" else ""
    }

    private fun yesNo(value: Boolean): String = if (value) "ano" else "ne"

    private fun onRowClick(position: Int) {
        val r = rows.getOrNull(position) ?: return
        when (kind) {
            KIND_DISPUTES -> decideDispute(r)
            KIND_VERIFICATIONS -> decideVerification(r)
            KIND_USERS -> toggleBlock(r)
            else -> decideReport(r)
        }
    }

    private fun decideReport(r: JsonObject) {
        val id = J.lng(r, "id")
        AlertDialog.Builder(this)
            .setTitle("${getString(R.string.queue_title)} #$id")
            .setMessage(J.str(r, "reason"))
            .setPositiveButton(R.string.keep) { _, _ ->
                client.api().approve(id).enqueue(refreshAfter())
            }
            .setNegativeButton(R.string.remove) { _, _ ->
                client.api().remove(id).enqueue(refreshAfter())
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    /** Potvrzení námitky recenzi odstraní, zamítnutí ji ponechá v platnosti. */
    private fun decideDispute(r: JsonObject) {
        val id = J.lng(r, "id")
        val note = EditText(this).apply { hint = getString(R.string.decision_note) }
        AlertDialog.Builder(this)
            .setTitle("${getString(R.string.disputes_title)} #$id")
            .setMessage(J.str(r, "reason"))
            .setView(note)
            .setPositiveButton(R.string.uphold) { _, _ -> resolveDispute(id, "uphold", note.text.toString()) }
            .setNegativeButton(R.string.reject) { _, _ -> resolveDispute(id, "reject", note.text.toString()) }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun resolveDispute(id: Long, decision: String, note: String) {
        client.api().resolveDispute(id, mapOf("decision" to decision, "note" to note))
            .enqueue(refreshAfter())
    }

    private fun decideVerification(r: JsonObject) {
        val userId = J.lng(r, "userId")
        val note = EditText(this).apply { hint = getString(R.string.decision_note) }
        AlertDialog.Builder(this)
            .setTitle("${getString(R.string.verifications_title)} #$userId")
            .setMessage(J.str(r, "displayName"))
            .setView(note)
            .setPositiveButton(R.string.approve_verification) { _, _ ->
                client.api().approveVerification(userId, mapOf("note" to note.text.toString()))
                    .enqueue(refreshAfter())
            }
            .setNegativeButton(R.string.reject_verification) { _, _ ->
                client.api().rejectVerification(userId, mapOf("note" to note.text.toString()))
                    .enqueue(refreshAfter())
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleBlock(r: JsonObject) {
        val id = J.lng(r, "id")
        if (J.bool(r, "isAdmin")) {
            Toast.makeText(this, R.string.cannot_block_admin, Toast.LENGTH_LONG).show()
            return
        }
        if (J.bool(r, "blocked")) {
            client.api().unblockUser(id).enqueue(refreshAfter())
            return
        }
        val reason = EditText(this).apply { hint = getString(R.string.block_reason) }
        AlertDialog.Builder(this)
            .setTitle("${getString(R.string.block_user)} #$id")
            .setView(reason)
            .setPositiveButton(R.string.block_user) { _, _ ->
                client.api().blockUser(id, mapOf("reason" to reason.text.toString()))
                    .enqueue(refreshAfter())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshAfter(): Ui<JsonObject> =
        object : Ui<JsonObject>(this, "Rozhodnutí selhalo") {
            override fun ok(body: JsonObject) = show(kind, sectionTitle)
        }

    private fun loadDac7Summary() {
        val year = yearOrCurrent()
        client.api().dac7Summary(year).enqueue(
            object : Ui<JsonObject>(this, "Souhrn se nenačetl") {
                override fun ok(body: JsonObject) {
                    findViewById<TextView>(R.id.summary).text = buildString {
                        append(getString(R.string.dac7_summary_title)).append(' ').append(year).append('\n')
                        append(getString(R.string.users_title)).append(": ")
                        append(J.lng(body, "sellerCount")).append(" · nad prahem ")
                        append(J.integer(body, "reportableSellerCount")).append('\n')
                        append(J.lng(body, "totalSales")).append(" prodejů · ")
                        append(J.price(J.lng(body, "totalAmountCzk")))
                    }
                }
            },
        )
    }

    private fun export(detail: Boolean) {
        val year = yearOrCurrent()
        val format = findViewById<Spinner>(R.id.format).selectedItem.toString()
        client.api().dac7Export(year, format, detail).enqueue(
            object : Ui<ResponseBody>(this, "Export selhal") {
                override fun ok(body: ResponseBody) {
                    val text = body.string()
                    findViewById<TextView>(R.id.export_out).text =
                        if (text.length > 4000) "${text.substring(0, 4000)}…" else text
                }
            },
        )
    }

    private fun yearOrCurrent(): Int =
        findViewById<EditText>(R.id.year).text.toString().trim().toIntOrNull()
            ?: Calendar.getInstance().get(Calendar.YEAR)

    companion object {
        private const val KIND_QUEUE = "queue"
        private const val KIND_DISPUTES = "disputes"
        private const val KIND_VERIFICATIONS = "verifications"
        private const val KIND_USERS = "users"
        private const val KIND_AUDIT = "audit"
        private const val KIND_DAC7 = "dac7"

        private val KINDS = arrayOf(
            KIND_QUEUE, KIND_DISPUTES, KIND_VERIFICATIONS, KIND_USERS, KIND_AUDIT, KIND_DAC7,
        )

        private val SECTIONS = intArrayOf(
            R.string.queue_title,
            R.string.disputes_title,
            R.string.verifications_title,
            R.string.users_title,
            R.string.audit_title,
            R.string.dac7_title,
        )
    }
}