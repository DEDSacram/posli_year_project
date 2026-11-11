package cz.bazar.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Autentizace, role, vlastnictví inzerátů a vyhledávání přes celý HTTP stack. */
class CoreApiIT extends AbstractIntegrationTest {

  @Test
  void registerHashesPasswordAndIssuesBothTokens() {
    ResponseEntity<String> res = exchange(HttpMethod.POST, "/auth/register",
        Map.of("email", "hash@t.cz", "password", "heslo12345", "displayName", "Hash", "sellerType", "private"),
        null);
    JsonNode body = body(res);

    assertThat(res.getStatusCode().value()).isEqualTo(201);
    assertThat(body.get("token").asText()).isNotBlank();
    assertThat(body.get("refreshToken").asText()).isNotBlank();
    // heslo se nikdy nevrací
    assertThat(res.getBody()).doesNotContain("heslo12345");
    assertThat(res.getBody()).doesNotContain("passwordHash");
  }

  @Test
  void duplicateEmailIsConflict() {
    registerUser("dup@t.cz", "private");
    ResponseEntity<String> res = exchange(HttpMethod.POST, "/auth/register",
        Map.of("email", "dup@t.cz", "password", "heslo12345", "displayName", "Dup", "sellerType", "private"),
        null);

    assertThat(res.getStatusCode().value()).isEqualTo(409);
    assertThat(body(res).get("code").asText()).isEqualTo("EMAIL_EXISTS");
  }

  @Test
  void shortPasswordIsRejected() {
    ResponseEntity<String> res = exchange(HttpMethod.POST, "/auth/register",
        Map.of("email", "short@t.cz", "password", "kratke", "displayName", "X", "sellerType", "private"),
        null);
    assertThat(res.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void refreshTokenIssuesNewAccessToken() {
    Account acc = registerAccount("refresh@t.cz", "private");
    ResponseEntity<String> login = exchange(HttpMethod.POST, "/auth/login",
        Map.of("email", "refresh@t.cz", "password", "heslo12345"), null);
    String refresh = body(login).get("refreshToken").asText();

    JsonNode refreshed = post("/auth/refresh", Map.of("refreshToken", refresh), null);

    assertThat(refreshed.get("token").asText()).isNotBlank();
    assertThat(refreshed.get("refreshToken").asText()).isNotBlank();
    assertThat(acc.id()).isPositive();
  }

  @Test
  void wrongPasswordIsUnauthorized() {
    registerUser("pwd@t.cz", "private");
    ResponseEntity<String> res = exchange(HttpMethod.POST, "/auth/login",
        Map.of("email", "pwd@t.cz", "password", "spatneheslo"), null);
    assertThat(res.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void publishingRequiresSellerProfile() {
    Account acc = registerAccount("noprof@t.cz", "private");
    ResponseEntity<String> res = exchange(HttpMethod.POST, "/listings", Map.of(
        "title", "Bez profilu", "description", "x", "priceCzk", 100,
        "category", "Jiné", "condition", "dobrý", "city", "Praha"), acc.token());

    assertThat(res.getStatusCode().value()).isEqualTo(422);
    assertThat(body(res).get("code").asText()).isEqualTo("DAC7_PROFILE_REQUIRED");
  }

  @Test
  void onlyOwnerCanEditOrDeleteListing() {
    Account owner = registerAccount("own-owner@t.cz", "private");
    fillSellerProfile(owner.token(), "private");
    long id = post("/listings", Map.of(
        "title", "Vlastní věc", "description", "x", "priceCzk", 100,
        "category", "Jiné", "condition", "dobrý", "city", "Praha"), owner.token())
        .get("id").asLong();

    Account stranger = registerAccount("own-stranger@t.cz", "private");

    assertThat(exchange(HttpMethod.PUT, "/listings/" + id, Map.of("title", "Přepsáno"),
        stranger.token()).getStatusCode().value()).isEqualTo(403);
    assertThat(exchange(HttpMethod.DELETE, "/listings/" + id, null, stranger.token())
        .getStatusCode().value()).isEqualTo(403);

    assertThat(exchange(HttpMethod.PUT, "/listings/" + id, Map.of("title", "Opravený název"),
        owner.token()).getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(exchange(HttpMethod.DELETE, "/listings/" + id, null, owner.token())
        .getStatusCode().is2xxSuccessful()).isTrue();

    // smazaný inzerát už není veřejně dostupný
    assertThat(exchange(HttpMethod.GET, "/listings/" + id, null, null).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void geoPairMustBeCompleteAndInRange() {
    Account acc = registerAccount("geo@t.cz", "private");
    fillSellerProfile(acc.token(), "private");

    ResponseEntity<String> onlyLat = exchange(HttpMethod.POST, "/listings", Map.of(
        "title", "Jen šířka", "description", "x", "priceCzk", 100,
        "category", "Jiné", "condition", "dobrý", "city", "Praha", "lat", 50.0), acc.token());
    assertThat(onlyLat.getStatusCode().value()).isEqualTo(400);

    ResponseEntity<String> outOfRange = exchange(HttpMethod.POST, "/listings", Map.of(
        "title", "Mimo rozsah", "description", "x", "priceCzk", 100,
        "category", "Jiné", "condition", "dobrý", "city", "Praha",
        "lat", 100.0, "lon", 20.0), acc.token());
    assertThat(outOfRange.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void searchFindsFullWordAndSubstring() {
    Account acc = registerAccount("search@t.cz", "private");
    fillSellerProfile(acc.token(), "private");
    // Unikátní kategorie izoluje test od ostatních IT tříd, které sdílejí databázi.
    long id = post("/listings", Map.of(
        "title", "PlayStation 5", "description", "Herní konzole", "priceCzk", 5000,
        "category", "Elektronika", "condition", "dobrý", "city", "Olomouc",
        "lat", 49.5936, "lon", 17.2511), acc.token()).get("id").asLong();

    // celé slovo (BM25)
    assertThat(idsOf(get("/listings/search?q=PlayStation", null))).contains(id);
    // nesouvisející fráze inzerát nenajde
    assertThat(get("/listings/search?q=ZYXQWN", null).get("data")).isEmpty();
  }

  @Test
  void searchMatchesSubstringOfTitle() {
    Account acc = registerAccount("substring@t.cz", "private");
    fillSellerProfile(acc.token(), "private");
    post("/listings", Map.of(
        "title", "Retro herní konzole", "description", "x", "priceCzk", 900,
        "category", "Retro", "condition", "dobrý", "city", "Olomouc"), acc.token());

    // část slova musí fungovat i bez BM25 shody (ILIKE podřetězec)
    assertThat(get("/listings/search?q=herní", null).get("data")).isNotEmpty();
    assertThat(get("/listings/search?category=Retro", null).get("data")).isNotEmpty();
  }

  @Test
  void searchSupportsCategoryPriceAndRadius() {
    Account acc = registerAccount("geo-search@t.cz", "private");
    fillSellerProfile(acc.token(), "private");
    String cat = "Geo test 9911"; // unikátní kategorie → výsledky patří jen tomuto testu
    long prague = post("/listings", Map.of(
        "title", "Pražské křeslo", "description", "x", "priceCzk", 1000,
        "category", cat, "condition", "dobrý", "city", "Praha",
        "lat", 50.0755, "lon", 14.4378), acc.token()).get("id").asLong();
    long brno = post("/listings", Map.of(
        "title", "Brněnský stůl", "description", "x", "priceCzk", 2000,
        "category", cat, "condition", "dobrý", "city", "Brno",
        "lat", 49.1951, "lon", 16.6068), acc.token()).get("id").asLong();

    JsonNode near = get("/listings/search?category=" + urlencode(cat)
        + "&lat=50.0755&lon=14.4378&radiusKm=20", null);
    assertThat(idsOf(near)).containsExactly(prague);

    JsonNode wide = get("/listings/search?category=" + urlencode(cat)
        + "&lat=50.0755&lon=14.4378&radiusKm=500", null);
    assertThat(idsOf(wide)).containsExactlyInAnyOrder(prague, brno);

    JsonNode byPrice = get("/listings/search?category=" + urlencode(cat) + "&minPrice=1500", null);
    assertThat(idsOf(byPrice)).containsExactly(brno);

    JsonNode byMaxPrice = get("/listings/search?category=" + urlencode(cat) + "&maxPrice=1500", null);
    assertThat(idsOf(byMaxPrice)).containsExactly(prague);
  }

  private static java.util.List<Long> idsOf(JsonNode page) {
    java.util.List<Long> ids = new java.util.ArrayList<>();
    for (JsonNode n : page.get("data")) ids.add(n.get("id").asLong());
    return ids;
  }

  private static String urlencode(String s) {
    return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
  }

  @Test
  void searchValidatesInput() {
    assertThat(exchange(HttpMethod.GET, "/listings/search?lat=50&radiusKm=10", null, null)
        .getStatusCode().value()).isEqualTo(400);
    assertThat(exchange(HttpMethod.GET, "/listings/search?lat=50&lon=14&radiusKm=0", null, null)
        .getStatusCode().value()).isEqualTo(400);
    assertThat(exchange(HttpMethod.GET, "/listings/search?sort=evil", null, null)
        .getStatusCode().value()).isEqualTo(400);
    assertThat(exchange(HttpMethod.GET, "/listings/search?q=" + "x".repeat(201), null, null)
        .getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void feedIsPublicAndPaged() {
    JsonNode feed = get("/listings/feed?limit=5", null);
    assertThat(feed.has("data")).isTrue();
    assertThat(feed.has("nextCursor")).isTrue();
  }

  @Test
  void chatRequiresParticipant() {
    Account seller = registerAccount("chat-seller@t.cz", "private");
    fillSellerProfile(seller.token(), "private");
    long listingId = post("/listings", Map.of(
        "title", "Chatovací předmět", "description", "x", "priceCzk", 100,
        "category", "Jiné", "condition", "dobrý", "city", "Praha"), seller.token())
        .get("id").asLong();

    Account buyer = registerAccount("chat-buyer@t.cz", "private");
    Account stranger = registerAccount("chat-stranger@t.cz", "private");

    long convId = post("/chat/conversations", Map.of("listingId", listingId), buyer.token())
        .get("id").asLong();
    post("/chat/messages", Map.of("conversationId", convId, "bodyText", "Dobrý den"), buyer.token());

    assertThat(exchange(HttpMethod.GET, "/chat/conversations/" + convId + "/messages", null,
        stranger.token()).getStatusCode().value()).isEqualTo(403);
    assertThat(get("/chat/conversations/" + convId + "/messages", buyer.token())
        .get("data")).isNotEmpty();
  }

  @Test
  void moderationReportAppearsInAdminQueue() {
    String admin = adminAccount().token();
    Account reporter = registerAccount("mod-reporter@t.cz", "private");
    post("/moderation/reports", Map.of(
        "targetType", "review", "targetId", 42, "reason", "urážlivý text"), reporter.token());

    JsonNode queue = get("/admin/moderation/queue?limit=50", admin);
    boolean found = false;
    for (JsonNode r : queue.get("data")) {
      if (r.get("targetType").asText().equals("review") && r.get("targetId").asLong() == 42L) {
        found = true;
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  void gdprExportAndSoftDelete() {
    Account acc = registerAccount("gdpr@t.cz", "private");
    fillSellerProfile(acc.token(), "private");
    post("/listings", Map.of(
        "title", "GDPR věc", "description", "x", "priceCzk", 100,
        "category", "Jiné", "condition", "dobrý", "city", "Praha"), acc.token());
    post("/consents", Map.of("purpose", "marketing", "granted", true), acc.token());

    String export = exchange(HttpMethod.GET, "/users/me/export", null, acc.token()).getBody();
    assertThat(export).contains("gdpr@t.cz");
    // export nesmí obsahovat daňové údaje (ani zašifrované)
    assertThat(export).doesNotContain("CZ6508000000192000145399");

    assertThat(exchange(HttpMethod.DELETE, "/users/me", null, acc.token())
        .getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(exchange(HttpMethod.GET, "/users/" + acc.id(), null, null).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void healthEndpointIsPublic() {
    JsonNode health = get("/health", null);
    assertThat(health.get("status").asText()).isEqualTo("UP");
    assertThat(health.get("db").asText()).isEqualTo("UP");
  }
}