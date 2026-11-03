package cz.bazar.android.api

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Retrofit klient a úložiště tokenů (SharedPreferences).
 * Obdoba webového `src/api.ts` — stejná adresa, stejný `Authorization` hlavička.
 */
class BazarClient(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val api: BazarApi

    init {
        val http = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(prefs))
            .build()
        val base = baseUrl().let { if (it.endsWith("/")) it else "$it/" }
        api = Retrofit.Builder()
            .baseUrl(base)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BazarApi::class.java)
    }

    fun api(): BazarApi = api

    fun baseUrl(): String = prefs.getString(KEY_BASE, DEFAULT_BASE) ?: DEFAULT_BASE

    fun setBaseUrl(url: String?) {
        val cleaned = url.orEmpty().trim().trimEnd('/')
        prefs.edit().putString(KEY_BASE, cleaned.ifEmpty { DEFAULT_BASE }).apply()
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)

    fun userId(): Long? = prefs.getLong(KEY_USER_ID, -1).takeIf { it >= 0 }

    fun saveAuth(userId: Long, token: String, refreshToken: String) {
        prefs.edit()
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_TOKEN, token)
            .putString(KEY_REFRESH, refreshToken)
            .apply()
    }

    fun clearAuth() {
        prefs.edit().remove(KEY_USER_ID).remove(KEY_TOKEN).remove(KEY_REFRESH).apply()
    }

    fun isLoggedIn(): Boolean = token() != null

    /** Přidá `Authorization: Bearer …` ke každému požadavku s uloženým tokenem. */
    private class AuthInterceptor(private val prefs: SharedPreferences) : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val token = prefs.getString(KEY_TOKEN, null)
            val request: Request = chain.request()
            if (token.isNullOrBlank()) return chain.proceed(request)
            return chain.proceed(
                request.newBuilder().header("Authorization", "Bearer $token").build(),
            )
        }
    }

    companion object {
        /** 10.0.2.2 je adresa hostitelského počítače z emulátoru Androidu. */
        const val DEFAULT_BASE = "http://10.0.2.2:8081"

        private const val PREFS = "bazar-prefs"
        private const val KEY_BASE = "api_base"
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_USER_ID = "user_id"
    }
}