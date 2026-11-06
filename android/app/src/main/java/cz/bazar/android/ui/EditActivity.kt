package cz.bazar.android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui
import cz.bazar.android.util.Validate

/** Úprava vlastního inzerátu. Snížení ceny rozešle upozornění jeho sledujícím. */
class EditActivity : AppCompatActivity() {

    private lateinit var client: BazarClient
    private var listingId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        client = BazarClient(this)
        listingId = intent.getLongExtra("id", -1)
        setTitle(R.string.edit_listing)

        client.api().listing(listingId).enqueue(
            object : Ui<JsonObject>(this, "Inzerát nenačten") {
                override fun ok(item: JsonObject) {
                    field(R.id.title).setText(J.str(item, "title"))
                    field(R.id.desc).setText(J.str(item, "description"))
                    field(R.id.price).setText(J.lng(item, "priceCzk").toString())
                    field(R.id.city).setText(J.str(item, "city"))
                }
            },
        )

        findViewById<android.widget.Button>(R.id.save).setOnClickListener { save() }
    }

    private fun save() {
        val title = field(R.id.title).text.toString().trim()
        val description = field(R.id.desc).text.toString().trim()
        val city = field(R.id.city).text.toString().trim()
        val price = field(R.id.price).text.toString().trim()

        val error = listOf(
            Validate.title(title),
            Validate.description(description),
            Validate.price(price),
            Validate.city(city),
        ).firstOrNull { it.isNotEmpty() }
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }

        client.api().updateListing(
            listingId,
            mapOf(
                "title" to title,
                "description" to description,
                "priceCzk" to price.toInt(),
                "city" to city,
            ),
        ).enqueue(
            object : Ui<JsonObject>(this, "Uložení selhalo") {
                override fun ok(body: JsonObject) {
                    Toast.makeText(this@EditActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
        )
    }

    private fun field(id: Int): EditText = findViewById(id)

    companion object {
        fun open(ctx: Context, id: Long) {
            ctx.startActivity(Intent(ctx, EditActivity::class.java).putExtra("id", id))
        }
    }
}