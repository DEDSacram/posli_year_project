package cz.bazar.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Námitky proti hodnocení: prodejce podá námitku, administrátor ji rozhodne
 * a podle výsledku se recenze zobrazí, nebo odstraní.
 */
class ReviewDisputeIT extends AbstractIntegrationTest {

  /** Prodejce s profilem a vystaveným inzerátem. */
  private record Seller(long id, String token, long listingId) {}

  private Seller sellerWithListing(String email, String type) {
    Account acc = registerAccount(email, type);
    fillSellerProfile(acc.token(), type);
    long listingId = post("/listings", Map.of(
        "title", "Testovací předmět " + email,
        "description", "Popis",
        "priceCzk", 1000,
        "category", "Jiné",
        "condition", "dobrý",
        "city", "Praha"), acc.token()).get("id").asLong();
    return new Seller(acc.id(), acc.token(), listingId);
  }

  /** Dokončený obchod + hodnocení od kupce; vrací ID hodnocení. */
  private long completedPurchaseWithReview(String sellerToken, long listingId, String buyerEmail, int rating) {
    String buyer = registerUser(buyerEmail, "private");
    post("/transactions", Map.of("listingId", listingId), buyer);
    JsonNode tx = get("/transactions/mine?role=buyer", buyer).get("data").get(0);
    post("/transactions/" + tx.get("id").asLong() + "/complete", null, sellerToken);
    return post("/transactions/" + tx.get("id").asLong() + "/review",
        Map.of("rating", rating, "text", "Vše v pořádku"), buyer).get("id").asLong();
  }

  @Test
  void sellerCanDisputeOwnReviewAndAdminCanUpholdIt() {
    Seller seller = sellerWithListing("dispute-seller@t.cz", "private");
    long reviewId = completedPurchaseWithReview(seller.token(), seller.listingId(), "dispute-buyer@t.cz", 1);
    String admin = adminAccount().token();

    long disputeId = post("/reviews/" + reviewId + "/dispute",
        Map.of("reason", "Reakce neodpovídá skutečnosti, zboží bylo v pořádku."), seller.token())
        .get("id").asLong();

    // recenze je do rozhodnutí stále veřejná, ale nese stav otevřené námitky
    String revieweeId = String.valueOf(seller.id());
    assertThat(get("/users/" + revieweeId + "/reviews", null).get("data").get(0)
        .get("disputeStatus").asText()).isEqualTo("open");

    // fronta námitek pro admina
    JsonNode queue = get("/admin/disputes", admin);
    assertThat(queue.get("openCount").asLong()).isGreaterThanOrEqualTo(1);

    JsonNode resolved = post("/admin/disputes/" + disputeId + "/resolve",
        Map.of("decision", "uphold", "note", "reakce zkreslená"), admin);
    assertThat(resolved.get("status").asText()).isEqualTo("upheld");
    assertThat(resolved.get("resolutionNote").asText()).isEqualTo("reakce zkreslená");

    // po potvrzení námitky se recenze nezobrazuje veřejně
    assertThat(get("/users/" + revieweeId + "/reviews", null).get("data")).isEmpty();
  }

  @Test
  void rejectedDisputeKeepsReviewVisible() {
    Seller seller = sellerWithListing("dispute-seller2@t.cz", "private");
    long reviewId = completedPurchaseWithReview(seller.token(), seller.listingId(), "dispute-buyer2@t.cz", 2);
    String admin = adminAccount().token();

    long disputeId = post("/reviews/" + reviewId + "/dispute",
        Map.of("reason", "Recenze mi připadá nespravedlivá vůči skutečnosti."), seller.token())
        .get("id").asLong();
    post("/admin/disputes/" + disputeId + "/resolve",
        Map.of("decision", "reject", "note", "námitka neodůvodněná"), admin);

    String revieweeId = String.valueOf(seller.id());
    JsonNode reviews = get("/users/" + revieweeId + "/reviews", null).get("data");
    assertThat(reviews).hasSize(1);
    assertThat(reviews.get(0).get("disputeStatus").asText()).isEqualTo("rejected");
  }

  @Test
  void buyerCannotDisputeReviewAboutSeller() {
    Seller seller = sellerWithListing("dispute-seller3@t.cz", "private");
    String buyerEmail = "dispute-buyer3@t.cz";
    long reviewId = completedPurchaseWithReview(seller.token(), seller.listingId(), buyerEmail, 5);

    // kupce (autor hodnocení) námitku podat nesmí
    String buyer = registerUser(buyerEmail + "-x", "private");
    ResponseEntity<String> res = exchange(HttpMethod.POST, "/reviews/" + reviewId + "/dispute",
        Map.of("reason", "zbytečně dlouhý důvod námitky"), buyer);
    assertThat(res.getStatusCode().value()).isEqualTo(403);
    assertThat(body(res).get("code").asText()).isEqualTo("NOT_REVIEWEE");
  }

  @Test
  void cannotFileSecondOpenDispute() {
    Seller seller = sellerWithListing("dispute-seller4@t.cz", "private");
    long reviewId = completedPurchaseWithReview(seller.token(), seller.listingId(), "dispute-buyer4@t.cz", 1);

    post("/reviews/" + reviewId + "/dispute",
        Map.of("reason", "první námitka s dostatečně dlouhým textem"), seller.token());
    ResponseEntity<String> res = exchange(HttpMethod.POST, "/reviews/" + reviewId + "/dispute",
        Map.of("reason", "druhá námitka s dostatečně dlouhým textem"), seller.token());

    assertThat(res.getStatusCode().value()).isEqualTo(409);
    assertThat(body(res).get("code").asText()).isEqualTo("DISPUTE_OPEN");
  }

  @Test
  void disputeRequiresLongEnoughReason() {
    Seller seller = sellerWithListing("dispute-seller5@t.cz", "private");
    long reviewId = completedPurchaseWithReview(seller.token(), seller.listingId(), "dispute-buyer5@t.cz", 3);

    ResponseEntity<String> res = exchange(HttpMethod.POST, "/reviews/" + reviewId + "/dispute",
        Map.of("reason", "krátce"), seller.token());
    assertThat(res.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void sellerSeesOwnDisputes() {
    Seller seller = sellerWithListing("dispute-seller6@t.cz", "private");
    long reviewId = completedPurchaseWithReview(seller.token(), seller.listingId(), "dispute-buyer6@t.cz", 1);
    post("/reviews/" + reviewId + "/dispute",
        Map.of("reason", "hodnocení neodpovídá realitě obchodu"), seller.token());

    JsonNode mine = get("/reviews/disputes/mine", seller.token()).get("data");
    assertThat(mine).hasSize(1);
    assertThat(mine.get(0).get("reviewId").asLong()).isEqualTo(reviewId);
    assertThat(mine.get(0).get("status").asText()).isEqualTo("open");
  }

  @Test
  void disputeEndpointsAreNotPublic() {
    assertThat(exchange(HttpMethod.GET, "/reviews/disputes/mine", null, null).getStatusCode().value())
        .isEqualTo(401);
    assertThat(exchange(HttpMethod.GET, "/admin/disputes", null, null).getStatusCode().value())
        .isEqualTo(401);
  }

  @Test
  void upheldDisputeRemovesBadgeOfTrust() {
    // prodejce s výborným hodnocím, ale s potvrzenou námitkou proti němu
    Seller seller = sellerWithListing("badge-seller@t.cz", "private");
    long reviewId = completedPurchaseWithReview(seller.token(), seller.listingId(), "badge-buyer@t.cz", 5);
    String admin = adminAccount().token();

    long disputeId = post("/reviews/" + reviewId + "/dispute",
        Map.of("reason", "hodnocení je podle mě zkreslené a neodůvodněné"), seller.token())
        .get("id").asLong();
    post("/admin/disputes/" + disputeId + "/resolve",
        Map.of("decision", "uphold", "note", "potvrzeno"), admin);

    JsonNode badges = get("/users/" + seller.id() + "/badges", null);
    assertThat(badges.get("cleanRecord").asBoolean()).isFalse();
    assertThat(badges.get("trustedSeller").asBoolean()).isFalse();
  }
}