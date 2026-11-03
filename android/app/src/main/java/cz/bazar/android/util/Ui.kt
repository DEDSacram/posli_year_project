package cz.bazar.android.util

import android.app.Activity
import android.widget.Toast
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Retrofit callback, který výsledek vrací na UI vlákně a při chybě ukáže toast.
 * Odpovídá tomu, jak web v `src/api.ts` převádí `{code, messageCs}` na hlášku.
 */
abstract class Ui<T>(
    private val activity: Activity,
    private val errFallback: String,
) : Callback<T> {

    /** Voláno na UI vlákně při úspěšné odpovědi. */
    protected abstract fun ok(body: T)

    /** Voláno před `ok`, užitečné pro schování spinneru. */
    protected open fun settle() = Unit

    override fun onResponse(call: Call<T>, response: Response<T>) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            settle()
            val body = response.body()
            if (response.isSuccessful && body != null) ok(body) else toast(J.errMsg(response, errFallback))
        }
    }

    override fun onFailure(call: Call<T>, t: Throwable) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            settle()
            toast("Síťová chyba: ${t.message}")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
    }
}