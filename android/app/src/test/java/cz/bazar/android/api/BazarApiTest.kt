package cz.bazar.android.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder

/**
 * Smoke test rozhraní proti REST API: ověřuje cesty, metody a parsování odpovědi
 * na skutečné tvare JSON, které vrací backend. Běží proti MockWebServeru, takže
 * nepotřebuje síť ani emulátor.
 */
class BazarApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: BazarApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BazarApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    /** Vykoná požadavek, ověří 2xx a vrátí tělo jako JSON objekt. */
    private fun call(execute: () -> retrofit2.Response<JsonObject>): JsonObject {
        val response = execute()
        assertTrue("HTTP ${response.code()}", response.isSuccessful)
        val raw = response.body()?.toString() ?: error("Úspěšná odpověď bez těla")
        return JsonParser.parseString(raw).asJsonObject
    }

    @Test
    fun `login posts to auth login`() {
        enqueue("""{"id":7,"token":"t","refreshToken":"r"}""")

        val body = call { api.login(mapOf("email" to "a@b.cz", "password" to "x")).execute() }

        assertEquals(7, body.get("id").asInt)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/login", request.path)
    }

    @Test
    fun `withdrawal posts to transactions id withdraw`() {
        enqueue("""{"id":9,"status":"withdrawn"}""")

        val body = call { api.withdraw(9, mapOf("reason" to "vada")).execute() }

        assertEquals("withdrawn", body.get("status").asString)
        assertEquals("/transactions/9/withdraw", server.takeRequest().path)
    }

    @Test
    fun `withdrawal rights are read from dedicated endpoint`() {
        enqueue("""{"applies":true,"eligible":true,"days":14}""")

        val body = call { api.withdrawalRights(9).execute() }

        assertTrue(body.get("eligible").asBoolean)
        assertEquals("/transactions/9/withdrawal-rights", server.takeRequest().path)
    }

    @Test
    fun `notifications endpoint is parsed`() {
        enqueue(
            """
            {"data":[{"id":3,"type":"price_drop","listingId":11,"title":"Zlevnění",
            "oldPriceCzk":5000,"newPriceCzk":3000,"read":false}],"nextCursor":null}
            """.trimIndent(),
        )

        val body = call { api.notifications(null).execute() }
        val first = body.getAsJsonArray("data")[0].asJsonObject

        assertEquals("price_drop", first.get("type").asString)
        assertEquals(11, first.get("listingId").asInt)
        assertEquals(5000, first.get("oldPriceCzk").asInt)
        assertTrue(!first.get("read").asBoolean)
        assertEquals("/notifications", server.takeRequest().path)
    }

    @Test
    fun `dispute posts reason to reviews id dispute`() {
        enqueue("""{"id":2,"status":"open"}""")

        val body = call { api.disputeReview(5, mapOf("reason" to "zkreslená")).execute() }

        assertEquals("open", body.get("status").asString)
        val request = server.takeRequest()
        assertEquals("/reviews/5/dispute", request.path)
        assertTrue(request.body.readUtf8().contains("zkreslen"))
    }

    @Test
    fun `verification approval uses approve subresource`() {
        enqueue("""{"status":"verified","verified":true}""")

        val body = call { api.approveVerification(42, mapOf("note" to "ok")).execute() }

        assertEquals("verified", body.get("status").asString)
        assertEquals("/admin/verifications/42/approve", server.takeRequest().path)
    }

    @Test
    fun `search sends query parameters`() {
        enqueue("""{"data":[],"nextCursor":null}""")

        call {
            api.search(mapOf("q" to "kůň", "radiusKm" to "25", "limit" to "20")).execute()
        }

        val path = URLDecoder.decode(server.takeRequest().path.orEmpty(), "UTF-8")
        assertTrue(path.contains("q=kůň"))
        assertTrue(path.contains("radiusKm=25"))
    }

    @Test
    fun `feed passes cursor as query`() {
        enqueue("""{"data":[],"nextCursor":null}""")

        call { api.feed(42L, 20).execute() }

        val request: RecordedRequest = server.takeRequest()
        assertEquals("/listings/feed", request.requestUrl?.encodedPath)
        assertEquals("42", request.requestUrl?.queryParameter("cursor"))
    }

    @Test
    fun `error responses are not treated as success`() {
        enqueue("""{"code":"TRADER_PROFILE_INCOMPLETE","messageCs":"Obchodník musí mít IČO…"}""", 422)

        val response = api.reserve(mapOf("listingId" to 1)).execute()

        assertEquals(422, response.code())
        val error = JsonParser.parseString(response.errorBody()!!.string()).asJsonObject
        assertEquals("TRADER_PROFILE_INCOMPLETE", error.get("code").asString)
    }

    @Test
    fun `blocked account returns forbidden`() {
        enqueue("""{"code":"ACCOUNT_BLOCKED","messageCs":"Účet byl zablokován administrátorem."}""", 403)

        val response = api.myTransactions("all").execute()

        assertEquals(403, response.code())
    }
}