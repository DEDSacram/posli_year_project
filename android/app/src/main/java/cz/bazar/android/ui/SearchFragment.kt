package cz.bazar.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui

/**
 * Hledání: text (i část slova), kategorie, řazení a okruh podle PSČ.
 * PSČ se předvyplní z profilu — jako u webového filtru.
 */
class SearchFragment : Fragment() {

    private lateinit var client: BazarClient
    private lateinit var adapter: ListingAdapter
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView
    private lateinit var query: EditText
    private lateinit var zip: EditText
    private lateinit var radius: EditText
    private lateinit var category: Spinner
    private lateinit var sort: Spinner

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        client = BazarClient(requireContext())
        query = view.findViewById(R.id.q)
        zip = view.findViewById(R.id.zip)
        radius = view.findViewById(R.id.radius)
        category = view.findViewById(R.id.category)
        sort = view.findViewById(R.id.sort)
        progress = view.findViewById(R.id.progress)
        empty = view.findViewById(R.id.empty)

        category.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, CAT_LABELS,
        )
        sort.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, SORT_LABELS,
        )

        adapter = ListingAdapter(requireContext(), client.baseUrl()) { item ->
            DetailActivity.open(requireContext(), J.lng(item, "id"))
        }
        view.findViewById<ListView>(R.id.list).adapter = adapter

        view.findViewById<Button>(R.id.go).setOnClickListener { run() }
        query.setOnEditorActionListener { _, _, _ -> run(); true }

        prefillZip()
        return view
    }

    private fun prefillZip() {
        if (!client.isLoggedIn()) return
        client.api().location().enqueue(
            object : Ui<JsonObject>(requireActivity(), "Profil nenačten") {
                override fun ok(body: JsonObject) {
                    if (J.bool(body, "hasProfile") && zip.text.isNullOrEmpty()) {
                        zip.setText(J.str(body, "postalCode"))
                    }
                }

                // Host nebo chyba: filtr funguje i bez profilu, nic nehlásíme.
                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )
    }

    private fun run() {
        val params = LinkedHashMap<String, String>()
        val text = query.text.toString().trim()
        if (text.isNotEmpty()) params["q"] = text
        params["category"] = CATS[category.selectedItemPosition]
        params["sort"] = SORTS[sort.selectedItemPosition]
        params["limit"] = "20"

        val z = zip.text.toString().replace(Regex("\\s"), "")
        val r = radius.text.toString().trim()
        if (z.isEmpty() || r.isEmpty()) {
            fetch(params)
            return
        }
        // PSČ → souřadnice, pak dotaz s okruhem.
        client.api().zip(z).enqueue(
            object : Ui<JsonObject>(requireActivity(), "PSČ nenalezeno") {
                override fun ok(place: JsonObject) {
                    fetch(
                        params + mapOf(
                            "lat" to J.dbl(place, "lat", 0.0).toString(),
                            "lon" to J.dbl(place, "lon", 0.0).toString(),
                            "radiusKm" to r,
                        ),
                    )
                }
            },
        )
    }

    private fun fetch(params: Map<String, String>) {
        progress.visibility = View.VISIBLE
        client.api().search(params).enqueue(
            object : Ui<JsonObject>(requireActivity(), "Hledání selhalo") {
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

    companion object {
        /** Hodnoty pro API; `CAT_LABELS` jsou popisky ve stejném pořadí. */
        val CATS = arrayOf("", "elektronika", "sport", "nabytok", "moda", "auto", "dum", "ostatni")
        val CAT_LABELS = arrayOf(
            "Vše", "Elektronika", "Sport", "Nábytek", "Móda", "Auto-moto", "Dům a zahrada", "Ostatní",
        )
        val SORTS = arrayOf("relevance", "newest", "priceAsc", "priceDesc")
        val SORT_LABELS = arrayOf("Relevance", "Nejnovější", "Od nejlevnějšího", "Od nejdražšího")
    }
}