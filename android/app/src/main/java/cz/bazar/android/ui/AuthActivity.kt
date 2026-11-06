package cz.bazar.android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui
import cz.bazar.android.util.Validate
import java.util.LinkedHashMap

/** Přihlášení a registrace. */
class AuthActivity : AppCompatActivity() {

    private lateinit var client: BazarClient
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        client = BazarClient(this)

        val email = findViewById<EditText>(R.id.email)
        val password = findViewById<EditText>(R.id.password)
        val displayName = findViewById<EditText>(R.id.display_name)
        val trader = findViewById<CheckBox>(R.id.trader)
        progress = findViewById(R.id.progress)

        findViewById<Button>(R.id.btn_login).setOnClickListener {
            val err = listOf(Validate.email(email.text.toString()), Validate.password(password.text.toString()))
                .firstOrNull { it.isNotEmpty() }
            if (err != null) {
                toast(err)
                return@setOnClickListener
            }
            busy(true)
            val body = mapOf(
                "email" to email.text.toString().trim(),
                "password" to password.text.toString(),
            )
            client.api().login(body).enqueue(authCallback("Přihlášení selhalo"))
        }

        findViewById<Button>(R.id.btn_register).setOnClickListener {
            val emailValue = email.text.toString()
            val name = displayName.text.toString()
            val err = listOf(
                Validate.email(emailValue),
                Validate.password(password.text.toString()),
                Validate.displayName(name),
            ).firstOrNull { it.isNotEmpty() }
            if (err != null) {
                toast(err)
                return@setOnClickListener
            }
            busy(true)
            val body = mapOf(
                "email" to emailValue.trim(),
                "password" to password.text.toString(),
                "displayName" to name.trim(),
                "sellerType" to if (trader.isChecked) "trader" else "private",
            )
            client.api().register(body).enqueue(authCallback("Registrace selhala"))
        }

        intent.getStringExtra("email")?.let { email.setText(it) }
    }

    private fun authCallback(fallback: String): Ui<JsonObject> =
        object : Ui<JsonObject>(this, fallback) {
            override fun settle() = busy(false)

            override fun ok(body: JsonObject) {
                client.saveAuth(
                    J.lng(body, "id"),
                    J.str(body, "token"),
                    J.str(body, "refreshToken"),
                )
                finish()
            }
        }

    private fun busy(value: Boolean) {
        progress.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    companion object {
        fun open(ctx: Context) {
            ctx.startActivity(Intent(ctx, AuthActivity::class.java))
        }
    }
}