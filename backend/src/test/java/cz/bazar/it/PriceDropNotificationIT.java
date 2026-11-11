package cz.bazar.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Upozornění na zlevnění přes celý HTTP stack: kupce si inzerát uloží,
 * prodejce sníží cenu, kupce dostane nepřečtené upozornění a po přečtení zmizí.
 */
class PriceDropNotificationIT extends AbstractIntegrationTest {

  @Test
  void priceDropCreatesNotificationForFavoritesOnly() {
    String seller = registerUser("seller-drop@t.cz", "private");
    fillSellerProfile(seller, "private");
    long listingId = post("/listings", Map.of(
        "title", "Vintage kůň", "description", "Pěkný kus", "priceCzk", 5000,
        "category", "Zvířata", "condition", "dobrý", "city", "Praha"), seller)
        .get("id").asLong();

    String watcher = registerUser("watcher-drop@t.cz", "private");
    String bystander = registerUser("bystander-drop@t.cz", "private");
    String stranger = registerUser("stranger-drop@t.cz", "private"); // inzerát neuložil

    post("/favorites/" + listingId, null, watcher);
    post("/favorites/" + listingId, null, bystander);

    ResponseEntity<String> update = exchange(HttpMethod.PUT, "/listings/" + listingId,
        Map.of("priceCzk", 3000), seller);
    assertThat(update.getStatusCode().is2xxSuccessful()).isTrue();

    JsonNode notes = get("/notifications", watcher);
    assertThat(notes.get("data")).hasSize(1);
    JsonNode n = notes.get("data").get(0);
    assertThat(n.get("type").asText()).isEqualTo("price_drop");
    assertThat(n.get("listingId").asLong()).isEqualTo(listingId);
    assertThat(n.get("oldPriceCzk").asInt()).isEqualTo(5000);
    assertThat(n.get("newPriceCzk").asInt()).isEqualTo(3000);
    assertThat(n.get("read").asBoolean()).isFalse();
    assertThat(n.get("title").asText()).contains("Vintage kůň");

    assertThat(get("/notifications/unread-count", watcher).get("unread").asInt()).isEqualTo(1);
    assertThat(get("/notifications", bystander).get("data")).hasSize(1);

    // kdo inzerát neuložil, upozornění nedostane; prodejce se sám neupozorňuje
    assertThat(get("/notifications", stranger).get("data")).isEmpty();
    assertThat(get("/notifications", seller).get("data")).isEmpty();
  }

  @Test
  void priceIncreaseCreatesNoNotification() {
    String seller = registerUser("seller-up@t.cz", "private");
    fillSellerProfile(seller, "private");
    long listingId = post("/listings", Map.of(
        "title", "Stůl", "description", "Masivní", "priceCzk", 1000,
        "category", "Nábytek", "condition", "dobrý", "city", "Brno"), seller)
        .get("id").asLong();

    String buyer = registerUser("buyer-up@t.cz", "private");
    post("/favorites/" + listingId, null, buyer);

    exchange(HttpMethod.PUT, "/listings/" + listingId, Map.of("priceCzk", 2000), seller);

    assertThat(get("/notifications", buyer).get("data")).isEmpty();
  }

  @Test
  void duplicatePriceDropIsNotRepeated() {
    String seller = registerUser("seller-dup@t.cz", "private");
    fillSellerProfile(seller, "private");
    long listingId = post("/listings", Map.of(
        "title", "Lampa", "description", "Stará", "priceCzk", 800,
        "category", "Bydlení", "condition", "dobrý", "city", "Ostrava"), seller)
        .get("id").asLong();

    String buyer = registerUser("buyer-dup@t.cz", "private");
    post("/favorites/" + listingId, null, buyer);

    exchange(HttpMethod.PUT, "/listings/" + listingId, Map.of("priceCzk", 500), seller);
    exchange(HttpMethod.PUT, "/listings/" + listingId, Map.of("priceCzk", 500), seller);

    assertThat(get("/notifications", buyer).get("data")).hasSize(1);
  }

  @Test
  void markingReadClearsUnreadCount() {
    String seller = registerUser("seller-read@t.cz", "private");
    fillSellerProfile(seller, "private");
    long listingId = post("/listings", Map.of(
        "title", "Křeslo", "description", "Dřevěné", "priceCzk", 900,
        "category", "Nábytek", "condition", "dobrý", "city", "Plzeň"), seller)
        .get("id").asLong();

    String buyer = registerUser("buyer-read@t.cz", "private");
    post("/favorites/" + listingId, null, buyer);
    exchange(HttpMethod.PUT, "/listings/" + listingId, Map.of("priceCzk", 400), seller);

    long id = get("/notifications", buyer).get("data").get(0).get("id").asLong();
    post("/notifications/" + id + "/read", null, buyer);

    assertThat(get("/notifications/unread-count", buyer).get("unread").asInt()).isZero();
    assertThat(get("/notifications", buyer).get("data").get(0).get("read").asBoolean()).isTrue();
  }

  @Test
  void markAllReadMarksEverything() {
    String seller = registerUser("seller-all@t.cz", "private");
    fillSellerProfile(seller, "private");
    String buyer = registerUser("buyer-all@t.cz", "private");

    for (int i = 0; i < 2; i++) {
      long id = post("/listings", Map.of(
          "title", "Předmět " + i, "description", "x", "priceCzk", 1000,
          "category", "Jiné", "condition", "dobrý", "city", "Praha"), seller)
          .get("id").asLong();
      post("/favorites/" + id, null, buyer);
      exchange(HttpMethod.PUT, "/listings/" + id, Map.of("priceCzk", 500 + i), seller);
    }

    assertThat(get("/notifications/unread-count", buyer).get("unread").asInt()).isEqualTo(2);
    assertThat(post("/notifications/read-all", null, buyer).get("marked").asInt()).isEqualTo(2);
    assertThat(get("/notifications/unread-count", buyer).get("unread").asInt()).isZero();
  }

  @Test
  void notificationsRequireAuthentication() {
    assertThat(exchange(HttpMethod.GET, "/notifications", null, null).getStatusCode().value())
        .isEqualTo(401);
  }

  @Test
  void cannotReadNotificationOfAnotherUser() {
    String seller = registerUser("seller-own@t.cz", "private");
    fillSellerProfile(seller, "private");
    long listingId = post("/listings", Map.of(
        "title", "Čaj", "description", "x", "priceCzk", 300,
        "category", "Kuchyň", "condition", "nový", "city", "Praha"), seller)
        .get("id").asLong();

    String owner = registerUser("owner-own@t.cz", "private");
    String intruder = registerUser("intruder-own@t.cz", "private");
    post("/favorites/" + listingId, null, owner);
    exchange(HttpMethod.PUT, "/listings/" + listingId, Map.of("priceCzk", 200), seller);
    long id = get("/notifications", owner).get("data").get(0).get("id").asLong();

    // značka 200, ale cizí upozornění se neoznačí (dotaz je omezen na vlastní řádky)
    post("/notifications/" + id + "/read", null, intruder);
    assertThat(get("/notifications/unread-count", owner).get("unread").asInt()).isEqualTo(1);
  }

  @Test
  void favoritePriceDropBadgeStillWorks() {
    String seller = registerUser("seller-badge@t.cz", "private");
    fillSellerProfile(seller, "private");
    long listingId = post("/listings", Map.of(
        "title", "Bicycle", "description", " Horské kolo", "priceCzk", 4000,
        "category", "Sport", "condition", "dobrý", "city", "Praha"), seller)
        .get("id").asLong();

    String buyer = registerUser("buyer-badge@t.cz", "private");
    post("/favorites/" + listingId, null, buyer);
    exchange(HttpMethod.PUT, "/listings/" + listingId, Map.of("priceCzk", 3500), seller);

    JsonNode mine = get("/favorites/mine", buyer).get("data").get(0);
    assertThat(mine.get("savedPriceCzk").asInt()).isEqualTo(4000);
    assertThat(mine.get("priceDrop").asInt()).isEqualTo(500);
  }
}