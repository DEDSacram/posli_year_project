package cz.bazar.android.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Jednoduchý loader obrázků inzerátů s paměťovou cache.
 * Backend drží fotky jako binární data, proto není co cachovat v HTTP klientovi.
 */
object ImageLoader {

    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    /** URL fotografie inzerátu — stejný tvar jako `imageUrl` ve webu. */
    fun url(base: String, listingId: Long, imgId: Long): String =
        "$base/listings/$listingId/images/$imgId"

    fun load(view: ImageView, url: String) {
        // Značka chrání před přepsáním, když se recycle viewu stihne dočíst jiná fotka.
        view.tag = url
        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageResource(android.R.color.darker_gray)
        pool.execute {
            try {
                val bitmap = download(url)
                if (bitmap != null) cache.put(url, bitmap)
                main.post {
                    if (url == view.tag && bitmap != null) view.setImageBitmap(bitmap)
                }
            } catch (_: Exception) {
                // síť nebo formát obrázku — necháme placeholder
            }
        }
    }

    private fun download(url: String): Bitmap? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        return try {
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }
}