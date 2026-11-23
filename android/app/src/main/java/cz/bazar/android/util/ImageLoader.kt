package cz.bazar.android.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Jednoduchý loader obrázků inzerátů s paměťovou cache.
 * Backend drží fotky jako binární data, proto není co cachovat v HTTP klientovi.
 */
object ImageLoader {

    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024
    private const val MAX_IMAGE_SIZE = 512

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    private val lock = Any()
    private val inFlight =
        mutableMapOf<String, MutableList<WeakReference<ImageView>>>()

    fun url(base: String, listingId: Long, imgId: Long): String =
        "$base/listings/$listingId/images/$imgId"

    fun load(view: ImageView, url: String) {
        view.tag = url

        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }

        view.setImageResource(android.R.color.darker_gray)

        synchronized(lock) {
            val existing = inFlight[url]

            if (existing != null) {
                if (existing.none { it.get() === view }) {
                    existing.add(WeakReference(view))
                }
                return
            }

            inFlight[url] = mutableListOf(WeakReference(view))
        }

        pool.execute {
            var bitmap: Bitmap? = null

            try {
                bitmap = download(url)
                if (bitmap != null) {
                    cache.put(url, bitmap)
                }
            } catch (_: Exception) {
                // Při chybě zůstane placeholder.
            }

            val targets = synchronized(lock) {
                inFlight.remove(url)?.toList() ?: emptyList()
            }

            if (bitmap != null) {
                main.post {
                    targets.forEach { reference ->
                        val imageView = reference.get()
                        if (imageView != null && imageView.tag == url) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        }
    }

    private fun download(url: String): Bitmap? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000

        return try {
            val bytes = connection.inputStream.use { it.readBytes() }

            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size, bounds
            )

            var sampleSize = 1

            while (
                bounds.outWidth / sampleSize > MAX_IMAGE_SIZE ||
                bounds.outHeight / sampleSize > MAX_IMAGE_SIZE
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size, options
            )
        } finally {
            connection.disconnect()
        }
    }
}
