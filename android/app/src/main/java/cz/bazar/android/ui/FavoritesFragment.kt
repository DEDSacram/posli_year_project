package cz.bazar.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui

/** Oblíbené: přihlášený má v profilu (backend), host vidí jen hlášku. */
class FavoritesFragment : Fragment() {

    private lateinit var client: BazarClient
    private lateinit var adapter: ListingAdapter
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_list, container, false)
        client = BazarClient(requireContext())
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)
        adapter = ListingAdapter(requireContext(), client.baseUrl()) { item ->
            DetailActivity.open(requireContext(), J.lng(item, "id"))
        }
        view.findViewById<ListView>(R.id.list).adapter = adapter
        return view
    }

    override fun onResume() {
        super.onResume()
        client = BazarClient(requireContext())
        load()
    }

    private fun load() {
        if (!client.isLoggedIn()) {
            adapter.set(null)
            empty.setText(R.string.login_required)
            empty.visibility = View.VISIBLE
            return
        }
        empty.setText(R.string.no_results)
        progress.visibility = View.VISIBLE
        client.api().favorites().enqueue(
            object : Ui<JsonObject>(requireActivity(), "Načtení selhalo") {
                override fun settle() {
                    if (isAdded) progress.visibility = View.GONE
                }

                override fun ok(body: JsonObject) {
                    if (!isAdded) return
                    adapter.set(J.data(body))
                    empty.visibility = if (adapter.count == 0) View.VISIBLE else View.GONE
                }
            },
        )
    }
}