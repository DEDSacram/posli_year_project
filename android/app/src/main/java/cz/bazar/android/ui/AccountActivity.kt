package cz.bazar.android.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui
import cz.bazar.android.util.Validate
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody

/** Můj účet: DAC7 stav, daňový profil, ověření dokladu, export a smazání účtu. */
class AccountActivity : AppCompatActivity() {

    private lateinit var client: BazarClient
    private lateinit var pickDocument: ActivityResultLauncher<String>
    private lateinit var sellerType: Spinner
    private lateinit var selfCert: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)
        client = BazarClient(this)
        setTitle(R.string.account)

        if (!client.isLoggedIn()) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_LONG).show()
            AuthActivity.open(this)
            finish()
            return
        }

        sellerType = findViewById(R.id.seller_type)
        sellerType.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, arrayOf("private", "trader"),
        )
        selfCert = findViewById(R.id.self_cert)

        // Activity Result API místo deprecated onActivityResult.
        pickDocument = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uploadDocument(it) }
        }

        loadDac7Status()
        loadVerification()

        findViewById<Button>(R.id.save_profile).setOnClickListener { saveProfile() }
        findViewById<Button>(R.id.upload_doc).setOnClickListener { pickDocument() }
        findViewById<Button>(R.id.btn_export).setOnClickListener { showExport() }
        findViewById<Button>(R.id.btn_delete).setOnClickListener { confirmDelete() }
    }

    private fun loadDac7Status() {
        client.api().dac7Status().enqueue(
            object : Ui<JsonObject>(this, "") {
                override fun ok(o: JsonObject) {
                    findViewById<TextView>(R.id.dac7).text =
                        "Prodeje: ${J.lng(o, "salesCount")} · celkem ${J.price(J.lng(o, "totalAmountCzk"))}" +
                        if (J.bool(o, "isReportable")) " · REPORTUJE SE (DAC7)" else ""
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )
    }

    /** Stav administrátorového posouzení dokladu — obsah dokladu se nikdy nezobrazuje. */
    private fun loadVerification() {
        client.api().myVerification().enqueue(
            object : Ui<JsonObject>(this, "") {
                override fun ok(o: JsonObject) {
                    val status = J.str(o, "status")
                    val text = when (status) {
                        "verified" -> getString(R.string.verification_verified)
                        "rejected" -> getString(R.string.verification_rejected)
                        else -> getString(R.string.verification_pending)
                    }
                    val note = J.str(o, "note")
                    findViewById<TextView>(R.id.verification).text =
                        if (note.isEmpty()) text else "$text\n$note"
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )
    }

    private fun saveProfile() {
        val fields = mapOf(
            "fullName" to text(R.id.full_name),
            "dateOfBirth" to text(R.id.dob),
            "street" to text(R.id.street),
            "city" to text(R.id.city),
            "postalCode" to text(R.id.zip).replace(Regex("\\s"), ""),
            "country" to text(R.id.country),
            "iban" to text(R.id.iban).replace(Regex("[\\s-]"), ""),
        )
        val ibanError = Validate.iban(fields["iban"]!!)
        if (ibanError.isNotEmpty()) {
            Toast.makeText(this, ibanError, Toast.LENGTH_LONG).show()
            return
        }

        val body = LinkedHashMap<String, Any?>()
        body["sellerType"] = sellerType.selectedItem.toString()
        fields.forEach { (k, v) -> body[k] = v }
        body["taxId"] = text(R.id.tax_id)
        body["ico"] = text(R.id.ico)
        body["selfCertification"] = selfCert.isChecked

        client.api().sellerInfo(body).enqueue(
            object : Ui<JsonObject>(this, "Uložení selhalo") {
                override fun ok(o: JsonObject) {
                    Toast.makeText(this@AccountActivity, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                    loadDac7Status()
                }
            },
        )
    }

    /** Otevře systémový prohlížeč souborů k výběru dokladu. */
    private fun pickDocument() {
        pickDocument.launch("*/*")
    }

    private fun uploadDocument(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return
            // Bezpečnostní limity: 5 MB jako na webu.
            if (bytes.size > 5 * 1024 * 1024) {
                Toast.makeText(this, R.string.doc_too_large, Toast.LENGTH_LONG).show()
                return
            }
            val part = MultipartBody.Part.createFormData(
                "file",
                "doklad",
                RequestBody.create(MediaType.parse("application/octet-stream"), bytes),
            )
            client.api().idDocument(part).enqueue(
                object : Ui<JsonObject>(this, "Nahrání selhalo") {
                    override fun ok(o: JsonObject) {
                        Toast.makeText(
                            this@AccountActivity,
                            R.string.document_uploaded,
                            Toast.LENGTH_LONG,
                        ).show()
                        loadVerification()
                    }
                },
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Čtení souboru selhalo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showExport() {
        client.api().exportMe().enqueue(
            object : Ui<JsonObject>(this, "Export selhal") {
                override fun ok(o: JsonObject) {
                    AlertDialog.Builder(this@AccountActivity)
                        .setTitle(R.string.export_data)
                        .setMessage(o.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            },
        )
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_account)
            .setPositiveButton(R.string.delete_account) { _, _ ->
                client.api().deleteMe().enqueue(
                    object : Ui<JsonObject>(this, "Smazání selhalo") {
                        override fun ok(o: JsonObject) {
                            client.clearAuth()
                            finish()
                        }
                    },
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun text(id: Int): String =
        findViewById<EditText>(id).text.toString().trim()
}