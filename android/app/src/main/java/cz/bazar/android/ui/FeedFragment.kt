package cz.bazar.android.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.ImageLoader
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui

/** Řádek inzerátu v seznamech (feed / hledání / oblíbené). */
class ListingAdapter(
    context: Context,
    private val base: String,
    private val onPick: (JsonObject) -> Unit,
) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val items = ArrayList<JsonObject>()

    fun set(data: List<JsonObject>?) {
        items.clear()
        data?.let { items.addAll(it) }
        notifyDataSetChanged()
    }

    fun addAll(data: List<JsonObject>?) {
        data?.let { items.addAll(it) }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): JsonObject = items[position]

    override fun getItemId(position: Int): Long = J.lng(items[position], "id")

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_listing, parent, false)
        val item = items[position]

        view.findViewById<TextView>(R.id.title).text = J.str(item, "title")
        view.findViewById<TextView>(R.id.price).text = J.price(J.lng(item, "priceCzk"))
        val seller = J.str(item, "sellerName").ifEmpty { "#${J.lng(item, "sellerId")}" }
        view.findViewById<TextView>(R.id.meta).text = "$seller · ${J.str(item, "city")}"

        val thumb = view.findViewById<ImageView>(R.id.thumb)
        val images = J.arr(item, "images")
        if (images.isNotEmpty()) {
            ImageLoader.load(thumb, ImageLoader.url(base, J.lng(item, "id"), J.lng(images[0], "id")))
        } else {
            thumb.setImageResource(android.R.color.darker_gray)
        }

        view.setOnClickListener { onPick(item) }
        return view
    }
}

/** Domovský feed inzerátů s načítáním dalších stránek při scrollu ke konci. */
class FeedFragment : androidx.fragment.app.Fragment() {

    private lateinit var client: BazarClient
    private lateinit var adapter: ListingAdapter
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView
    private var cursor: String? = null
    private var loading = false

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
        view.findViewById<ListView>(R.id.list).apply {
            this.adapter = this@FeedFragment.adapter
            setOnScrollListener(
                object : AbsListView.OnScrollListener {
                    override fun onScrollStateChanged(v: AbsListView?, scrollState: Int) = Unit

                    override fun onScroll(v: AbsListView?, firstVisible: Int, visible: Int, total: Int) {
                        if (total > 0 && firstVisible + visible >= total - 2) loadMore()
                    }
                },
            )
        }

        loadMore()
        return view
    }

    override fun onResume() {
        super.onResume()
        client = BazarClient(requireContext())
    }

    private fun loadMore() {
        if (loading) return
        loading = true
        progress.visibility = View.VISIBLE
        client.api().feed(cursor?.toLong(), 20).enqueue(
            object : Ui<JsonObject>(requireActivity(), "Načtení selhalo") {
                override fun settle() {
                    loading = false
                    if (isAdded) progress.visibility = View.GONE
                }

                override fun ok(body: JsonObject) {
                    if (!isAdded) return
                    val data = J.data(body)
                    if (cursor == null) adapter.set(data) else adapter.addAll(data)
                    cursor = J.nextCursor(body)
                    empty.visibility = if (adapter.count == 0) View.VISIBLE else View.GONE
                }
            },
        )
    }
}