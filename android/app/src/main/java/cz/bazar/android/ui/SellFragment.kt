package cz.bazar.android.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.gson.JsonObject
import cz.bazar.android.R
import cz.bazar.android.api.BazarClient
import cz.bazar.android.util.J
import cz.bazar.android.util.Ui
import cz.bazar.android.util.Validate
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody

/** Prodej: formulář, fotky z galerie a publikace (max. 10 fotografií). */
class SellFragment : Fragment() {

    private lateinit var client: BazarClient
    private lateinit var title: EditText
    private lateinit var description: EditText
    private lateinit var price: EditText
    private lateinit var city: EditText
    private lateinit var zip: EditText
    private lateinit var photosInfo: TextView
    private lateinit var progress: ProgressBar
    private val photos = ArrayList<Uri>()
    private lateinit var pickPhoto: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickPhoto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null && photos.size < MAX_PHOTOS) {
                photos.add(uri)
                updatePhotosInfo()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_sell, container, false)
        client = BazarClient(requireContext())
        title = view.findViewById(R.id.title)
        description = view.findViewById(R.id.desc)
        price = view.findViewById(R.id.price)
        city = view.findViewById(R.id.city)
        zip = view.findViewById(R.id.zip)
        photosInfo = view.findViewById(R.id.photos_info)
        progress = view.findViewById(R.id.progress)

        view.findViewById<Spinner>(R.id.category).adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, SearchFragment.CAT_LABELS,
        )
        view.findViewById<Spinner>(R.id.condition).adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, CONDITIONS,
        )

        view.findViewById<Button>(R.id.add_photo).setOnClickListener {
            pickPhoto.launch(
                Intent.createChooser(
                    Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" },
                    getString(R.string.pick_photo),
                ),
            )
        }
        view.findViewById<Button>(R.id.publish).setOnClickListener { publish() }

        updatePhotosInfo()
        prefillLocation()
        return view
    }

    /** Město a PSČ z profilu jako u webového formuláře. */
    private fun prefillLocation() {
        if (!client.isLoggedIn()) return
        client.api().location().enqueue(
            object : Ui<JsonObject>(requireActivity(), "") {
                override fun ok(body: JsonObject) {
                    if (!J.bool(body, "hasProfile")) return
                    if (city.text.isNullOrEmpty()) city.setText(J.str(body, "city"))
                    if (zip.text.isNullOrEmpty()) zip.setText(J.str(body, "postalCode"))
                }

                override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) = Unit
            },
        )
    }

    private fun updatePhotosInfo() {
        photosInfo.text = getString(R.string.photos_count, photos.size, MAX_PHOTOS)
    }

    private fun publish() {
        if (!client.isLoggedIn()) {
            Toast.makeText(requireContext(), R.string.login_required, Toast.LENGTH_LONG).show()
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            return
        }

        val titleValue = title.text.toString().trim()
        val descValue = description.text.toString().trim()
        val priceValue = price.text.toString().trim()
        val cityValue = city.text.toString().trim()
        val error = listOf(
            Validate.title(titleValue),
            Validate.description(descValue),
            Validate.price(priceValue),
            Validate.city(cityValue),
        ).firstOrNull { it.isNotEmpty() }
        if (error != null) {
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            return
        }

        val body = LinkedHashMap<String, Any>()
        body["title"] = titleValue
        body["description"] = descValue
        body["priceCzk"] = priceValue.toInt()
        body["category"] = SearchFragment.CATS[
            requireNotNull(view?.findViewById<Spinner>(R.id.category)).selectedItemPosition,
        ].ifEmpty { "ostatni" }
        body["condition"] = CONDITIONS[
            requireNotNull(view?.findViewById<Spinner>(R.id.condition)).selectedItemPosition,
        ]
        body["city"] = cityValue

        progress.visibility = View.VISIBLE
        val z = zip.text.toString().replace(Regex("\\s"), "")
        if (z.isEmpty()) {
            client.api().createListing(body).enqueue(publishCallback())
        } else {
            // PSČ → souřadnice, aby šel inzerát hledat v okolí.
            client.api().zip(z).enqueue(
                object : Ui<JsonObject>(requireActivity(), "PSČ nenalezeno") {
                    override fun settle() = Unit

                    override fun ok(place: JsonObject) {
                        body["lat"] = J.dbl(place, "lat", 0.0)
                        body["lon"] = J.dbl(place, "lon", 0.0)
                        client.api().createListing(body).enqueue(publishCallback())
                    }

                    override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) {
                        if (isAdded) progress.visibility = View.GONE
                    }
                },
            )
        }
    }

    private fun publishCallback(): Ui<JsonObject> =
        object : Ui<JsonObject>(requireActivity(), "Publikace selhala") {
            override fun settle() = Unit

            override fun ok(created: JsonObject) {
                uploadPhotos(J.lng(created, "id"), 0)
            }

            override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) {
                if (isAdded) progress.visibility = View.GONE
            }
        }

    /** Fotky nahráváme postupně, aby se nezahltila paměť a šla hlásit chyba u konkrétní fotky. */
    private fun uploadPhotos(listingId: Long, index: Int) {
        if (!isAdded) return
        if (index >= photos.size) {
            progress.visibility = View.GONE
            Toast.makeText(requireContext(), R.string.published, Toast.LENGTH_SHORT).show()
            DetailActivity.open(requireContext(), listingId)
            title.setText("")
            description.setText("")
            price.setText("")
            photos.clear()
            updatePhotosInfo()
            return
        }
        try {
            val resolver = requireContext().contentResolver
            val bytes = resolver.openInputStream(photos[index])?.use { it.readBytes() }
            if (bytes == null) {
                progress.visibility = View.GONE
                Toast.makeText(requireContext(), R.string.photo_read_failed, Toast.LENGTH_LONG).show()
                return
            }
            if (bytes.size > MAX_PHOTO_BYTES) {
                progress.visibility = View.GONE
                Toast.makeText(requireContext(), R.string.photo_too_large, Toast.LENGTH_LONG).show()
                return
            }
            val mime = resolver.getType(photos[index]) ?: "image/jpeg"
            val part = MultipartBody.Part.createFormData(
                "file",
                "photo",
                RequestBody.create(MediaType.parse(mime), bytes),
            )
            client.api().addImage(listingId, part).enqueue(
                object : Ui<JsonObject>(requireActivity(), "Fotka se nenahrála") {
                    override fun settle() = Unit

                    override fun ok(body: JsonObject) = uploadPhotos(listingId, index + 1)

                    override fun onFailure(call: retrofit2.Call<JsonObject>, t: Throwable) {
                        if (isAdded) progress.visibility = View.GONE
                    }
                },
            )
        } catch (e: Exception) {
            progress.visibility = View.GONE
            Toast.makeText(
                requireContext(),
                getString(R.string.photo_read_failed) + ": ${e.message}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    companion object {
        val CONDITIONS = arrayOf("Nové", "Výborný", "Velmi dobrý", "Dobrý")
        private const val MAX_PHOTOS = 10
        private const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
    }
}