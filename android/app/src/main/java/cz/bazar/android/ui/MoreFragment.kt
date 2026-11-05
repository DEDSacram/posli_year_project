package cz.bazar.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient

/** Více: moje obchody, chaty, účet, administrace, adresa serveru a přihlášení. */
class MoreFragment : Fragment() {

    private lateinit var client: BazarClient
    private lateinit var hello: TextView
    private lateinit var authButton: Button
    private lateinit var adminButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_more, container, false)
        client = BazarClient(requireContext())
        hello = view.findViewById(R.id.hello)
        authButton = view.findViewById(R.id.btn_auth)
        adminButton = view.findViewById(R.id.btn_admin)

        val serverUrl = view.findViewById<EditText>(R.id.server_url)
        serverUrl.setText(client.baseUrl())

        view.findViewById<Button>(R.id.btn_chats).setOnClickListener {
            startActivity(Intent(requireContext(), ChatActivity::class.java))
        }
        view.findViewById<Button>(R.id.btn_deals).setOnClickListener {
            startActivity(Intent(requireContext(), DealsActivity::class.java))
        }
        view.findViewById<Button>(R.id.btn_notifications).setOnClickListener {
            startActivity(Intent(requireContext(), NotificationsActivity::class.java))
        }
        view.findViewById<Button>(R.id.btn_account).setOnClickListener {
            startActivity(Intent(requireContext(), AccountActivity::class.java))
        }
        adminButton.setOnClickListener {
            startActivity(Intent(requireContext(), AdminActivity::class.java))
        }
        view.findViewById<Button>(R.id.btn_save_server).setOnClickListener {
            client.setBaseUrl(serverUrl.text.toString())
            client = BazarClient(requireContext())
            Toast.makeText(requireContext(), "Uloženo: ${client.baseUrl()}", Toast.LENGTH_SHORT).show()
            refresh()
        }
        authButton.setOnClickListener {
            if (client.isLoggedIn()) {
                client.clearAuth()
                refresh()
            } else {
                startActivity(Intent(requireContext(), AuthActivity::class.java))
            }
        }

        refresh()
        return view
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        client = BazarClient(requireContext())
        val loggedIn = client.isLoggedIn()
        hello.text = if (loggedIn) {
            "Přihlášen (${client.userId()})"
        } else {
            "Nepřihlášen (host) — prohlížet lze bez účtu"
        }
        authButton.setText(if (loggedIn) R.string.logout else R.string.login)
        adminButton.visibility = if (loggedIn) View.VISIBLE else View.GONE
        view?.findViewById<Button>(R.id.btn_deals)?.visibility =
            if (loggedIn) View.VISIBLE else View.GONE
        view?.findViewById<Button>(R.id.btn_notifications)?.visibility =
            if (loggedIn) View.VISIBLE else View.GONE
    }
}