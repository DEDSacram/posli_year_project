package cz.bazar.android.api

import com.google.gson.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * Retrofit rozhraní 1:1 k REST API (Spring Boot) — totéž endpointy jako web.
 * Odpovědi jsou `JsonObject`, protože aplikace je tenká vrstva nad API,
 * které popisuje `frontend/src/types.ts`.
 */
interface BazarApi {

    // ---------- autentizace ----------
    @POST("auth/register")
    fun register(@Body body: Map<String, Any>): Call<JsonObject>

    @POST("auth/login")
    fun login(@Body body: Map<String, String>): Call<JsonObject>

    @POST("auth/refresh")
    fun refresh(@Body body: Map<String, String>): Call<JsonObject>

    // ---------- uživatel ----------
    @GET("users/{id}")
    fun user(@Path("id") id: Long): Call<JsonObject>

    @GET("users/{id}/badges")
    fun badges(@Path("id") id: Long): Call<JsonObject>

    @GET("users/{id}/rating")
    fun rating(@Path("id") id: Long): Call<JsonObject>

    @GET("users/{id}/reviews")
    fun reviews(
        @Path("id") id: Long,
        @Query("cursor") cursor: Long?,
        @Query("limit") limit: Int?,
    ): Call<JsonObject>

    @PATCH("users/me/seller-info")
    fun sellerInfo(@Body body: Map<String, Any?>): Call<JsonObject>

    @Multipart
    @POST("users/me/id-document")
    fun idDocument(@Part file: MultipartBody.Part): Call<JsonObject>

    @GET("seller/me/dac7-status")
    fun dac7Status(): Call<JsonObject>

    /** Stav administrátorového ověření dokladu obchodníka. */
    @GET("seller/me/verification")
    fun myVerification(): Call<JsonObject>

    @GET("seller/me/location")
    fun location(): Call<JsonObject>

    @PATCH("seller/me/location")
    fun saveLocation(@Body body: Map<String, String>): Call<JsonObject>

    @GET("users/me/export")
    fun exportMe(): Call<JsonObject>

    @DELETE("users/me")
    fun deleteMe(): Call<JsonObject>

    @POST("consents")
    fun consent(@Body body: Map<String, Any>): Call<JsonObject>

    // ---------- inzeráty ----------
    @POST("listings")
    fun createListing(@Body body: Map<String, Any>): Call<JsonObject>

    @GET("listings/{id}")
    fun listing(@Path("id") id: Long): Call<JsonObject>

    @PUT("listings/{id}")
    fun updateListing(@Path("id") id: Long, @Body body: Map<String, Any>): Call<JsonObject>

    @DELETE("listings/{id}")
    fun deleteListing(@Path("id") id: Long): Call<JsonObject>

    @Multipart
    @POST("listings/{id}/images")
    fun addImage(@Path("id") id: Long, @Part file: MultipartBody.Part): Call<JsonObject>

    @DELETE("listings/{id}/images/{imgId}")
    fun deleteImage(@Path("id") id: Long, @Path("imgId") imgId: Long): Call<JsonObject>

    @GET("listings/{id}/rights-info")
    fun rightsInfo(@Path("id") id: Long): Call<JsonObject>

    @GET("listings/search")
    fun search(@QueryMap params: Map<String, String>): Call<JsonObject>

    @GET("listings/feed")
    fun feed(@Query("cursor") cursor: Long?, @Query("limit") limit: Int?): Call<JsonObject>

    @GET("listings/suggest")
    fun suggest(@Query("title") title: String?, @Query("category") category: String?): Call<JsonObject>

    // ---------- oblíbené ----------
    @GET("favorites/ids")
    fun favoriteIds(): Call<JsonObject>

    @GET("favorites/mine")
    fun favorites(): Call<JsonObject>

    @POST("favorites/{id}")
    fun addFavorite(@Path("id") id: Long): Call<JsonObject>

    @DELETE("favorites/{id}")
    fun removeFavorite(@Path("id") id: Long): Call<JsonObject>

    // ---------- upozornění na zlevnění ----------
    @GET("notifications")
    fun notifications(@Query("cursor") cursor: Long? = null): Call<JsonObject>

    @GET("notifications/unread-count")
    fun unreadCount(): Call<JsonObject>

    @POST("notifications/{id}/read")
    fun markNotificationRead(@Path("id") id: Long): Call<JsonObject>

    @POST("notifications/read-all")
    fun markAllNotificationsRead(): Call<JsonObject>

    // ---------- transakce ----------
    @POST("transactions")
    fun reserve(@Body body: Map<String, Long>): Call<JsonObject>

    @GET("transactions/mine")
    fun myTransactions(@Query("role") role: String = "all"): Call<JsonObject>

    /** Přehled obchodů včetně získaného hodnocení. */
    @GET("transactions/overview")
    fun dealOverview(): Call<JsonObject>

    @POST("transactions/{id}/complete")
    fun complete(@Path("id") id: Long): Call<JsonObject>

    @POST("transactions/{id}/cancel")
    fun cancel(@Path("id") id: Long): Call<JsonObject>

    /** Odstoupení spotřebitele od smlouvy (§ 1829 OZ). */
    @POST("transactions/{id}/withdraw")
    fun withdraw(@Path("id") id: Long, @Body body: Map<String, String>): Call<JsonObject>

    @GET("transactions/{id}/withdrawal-rights")
    fun withdrawalRights(@Path("id") id: Long): Call<JsonObject>

    @POST("transactions/{id}/review")
    fun review(@Path("id") id: Long, @Body body: Map<String, Any>): Call<JsonObject>

    @POST("reviews/{id}/response")
    fun respond(@Path("id") id: Long, @Body body: Map<String, String>): Call<JsonObject>

    /** Námitka prodejce proti hodnocení. */
    @POST("reviews/{id}/dispute")
    fun disputeReview(@Path("id") id: Long, @Body body: Map<String, String>): Call<JsonObject>

    @GET("reviews/disputes/mine")
    fun myDisputes(): Call<JsonObject>

    // ---------- chat a moderace ----------
    @POST("chat/conversations")
    fun conversation(@Body body: Map<String, Long>): Call<JsonObject>

    @GET("chat/conversations")
    fun conversations(): Call<JsonObject>

    @POST("chat/messages")
    fun message(@Body body: Map<String, Any>): Call<JsonObject>

    @GET("chat/conversations/{id}/messages")
    fun messages(
        @Path("id") id: Long,
        @Query("cursor") cursor: Long?,
        @Query("limit") limit: Int?,
    ): Call<JsonObject>

    @GET("chat/interest")
    fun interest(@Query("listingId") listingId: Long): Call<JsonObject>

    @POST("moderation/reports")
    fun report(@Body body: Map<String, Any>): Call<JsonObject>

    @GET("geo/zip")
    fun zip(@Query("zip") zip: String): Call<JsonObject>

    // ---------- administrace ----------
    @GET("admin/moderation/queue")
    fun queue(@Query("limit") limit: Int): Call<JsonObject>

    @POST("admin/moderation/{id}/approve")
    fun approve(@Path("id") id: Long): Call<JsonObject>

    @POST("admin/moderation/{id}/remove")
    fun remove(@Path("id") id: Long): Call<JsonObject>

    @GET("admin/disputes")
    fun disputes(@Query("limit") limit: Int): Call<JsonObject>

    @POST("admin/disputes/{id}/resolve")
    fun resolveDispute(
        @Path("id") id: Long,
        @Body body: Map<String, String>,
    ): Call<JsonObject>

    @GET("admin/verifications")
    fun verifications(): Call<JsonObject>

    @POST("admin/verifications/{userId}/approve")
    fun approveVerification(
        @Path("userId") userId: Long,
        @Body body: Map<String, String>,
    ): Call<JsonObject>

    @POST("admin/verifications/{userId}/reject")
    fun rejectVerification(
        @Path("userId") userId: Long,
        @Body body: Map<String, String>,
    ): Call<JsonObject>

    @GET("admin/audit")
    fun audit(@Query("limit") limit: Int, @Query("action") action: String?): Call<JsonObject>

    @GET("admin/users")
    fun users(@Query("q") q: String, @Query("limit") limit: Int): Call<JsonObject>

    @POST("admin/users/{id}/block")
    fun blockUser(@Path("id") id: Long, @Body body: Map<String, String>): Call<JsonObject>

    @POST("admin/users/{id}/unblock")
    fun unblockUser(@Path("id") id: Long): Call<JsonObject>

    @GET("admin/dac7/summary")
    fun dac7Summary(@Query("year") year: Int): Call<JsonObject>

    @GET("admin/dac7/export")
    fun dac7Export(
        @Query("year") year: Int,
        @Query("format") format: String,
        @Query("detail") detail: Boolean,
    ): Call<ResponseBody>

    @GET("health")
    fun health(): Call<JsonObject>
}