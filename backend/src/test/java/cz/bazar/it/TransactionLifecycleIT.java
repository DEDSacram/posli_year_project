package cz.bazar.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Životní cyklus obchodu: rezervace → dokončení → hodnocení, právo spotřebitele
 * na odstoupení od smlouvy a přehled obchodů prodejce.
 */
class TransactionLifecycleIT extends AbstractIntegrationTest {

  private record Party(Account account, long listingId) {}

  private Party party(String email, String sellerType) {
    Account acc = registerAccount(email, sellerType);
    fillSellerProfile(acc.token(), sellerType);
    long listingId = post("/listings", Map.of(
        "title", "Věc " + email, "description", "Popis", "priceCzk", 2000,
        "category", "Jiné", "condition", "dobrý", "city", "Praha"), acc.token())
        .get("id").asLong();
    return new Party(acc, listingId);
  }

  @Test
  void reserveCompleteAndReviewFlow() {
    Party seller = party("flow-seller@t.cz", "private");
    Account buyer = registerAccount("flow-buyer@t.cz", "private");

    JsonNode tx = post("/transactions", Map.of("listingId", seller.listingId()), buyer.token());
    assertThat(tx.get("status").asText()).isEqualTo("reserved");
    assertThat(tx.get("priceCzk").asInt()).isEqualTo(2000); // snapshot ceny
    assertThat(tx.get("role").asText()).isEqualTo("buyer");
    long txId = tx.get("id").asLong();

    assertThat(post("/transactions/" + txId + "/complete", null, seller.account().token())
        .get("status").asText()).isEqualTo("completed");

    JsonNode review = post("/transactions/" + txId + "/review",
        Map.of("rating", 5, "text", "Skvělý prodej"), buyer.token());
    assertThat(review.get("verifiedPurchase").asBoolean()).isTrue();
    assertThat(review.get("rating").asInt()).isEqualTo(5);

    // druhé hodnocení stejné transakce není možné
    assertThat(exchange(HttpMethod.POST, "/transactions/" + txId + "/review",
        Map.of("rating", 1, "text", "znovu"), buyer.token()).getStatusCode().value())
        .isEqualTo(409);
  }

  @Test
  void reservationIsIdempotent() {
    Party seller = party("idem-seller@t.cz", "private");
    Account buyer = registerAccount("idem-buyer@t.cz", "private");

    long first = post("/transactions", Map.of("listingId", seller.listingId()), buyer.token())
        .get("id").asLong();
    long second = post("/transactions", Map.of("listingId", seller.listingId()), buyer.token())
        .get("id").asLong();

    assertThat(second).isEqualTo(first);
  }

  @Test
  void onlyBuyerCanReviewCompletedPurchase() {
    Party seller = party("only-seller@t.cz", "private");
    Account buyer = registerAccount("only-buyer@t.cz", "private");
    long txId = post("/transactions", Map.of("listingId", seller.listingId()), buyer.token())
        .get("id").asLong();
    post("/transactions/" + txId + "/complete", null, seller.account().token());

    ResponseEntity<String> res = exchange(HttpMethod.POST, "/transactions/" + txId + "/review",
        Map.of("rating", 5, "text", "já nejsem kupce"), seller.account().token());

    assertThat(res.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void buyerCanWithdrawFromTraderWithin14Days() {
    Party seller = party("wd-seller@t.cz", "trader");
    Account buyer = registerAccount("wd-buyer@t.cz", "private");
    long txId = post("/transactions", Map.of("listingId", seller.listingId()), buyer.token())
        .get("id").asLong();
    post("/transactions/" + txId + "/complete", null, seller.account().token());

    JsonNode rights = get("/transactions/" + txId + "/withdrawal-rights", buyer.token());
    assertThat(rights.get("applies").asBoolean()).isTrue();
    assertThat(rights.get("eligible").asBoolean()).isTrue();
    assertThat(rights.get("days").asInt()).isEqualTo(14);

    JsonNode out = post("/transactions/" + txId + "/withdraw",
        Map.of("reason", "zboží neodpovídalo popisu"), buyer.token());
    assertThat(out.get("status").asText()).isEqualTo("withdrawn");

    // odstoupený obchod už není dokončený pro účely hodnocení
    assertThat(exchange(HttpMethod.POST, "/transactions/" + txId + "/review",
        Map.of("rating", 5, "text", "hodnocení"), buyer.token()).getStatusCode().value())
        .isEqualTo(400);
  }

  @Test
  void privateSellerPurchaseHasNoWithdrawalRight() {
    Party seller = party("wd-private@t.cz", "private");
    Account buyer = registerAccount("wd-buyer2@t.cz", "private");
    long txId = post("/transactions", Map.of("listingId", seller.listingId()), buyer.token())
        .get("id").asLong();
    post("/transactions/" + txId + "/complete", null, seller.account().token());

    ResponseEntity<String> res = exchange(HttpMethod.POST, "/transactions/" + txId + "/withdraw",
        Map.of("reason", "chci zpět"), buyer.token());

    assertThat(res.getStatusCode().value()).isEqualTo(400);
    assertThat(body(res).get("code").asText()).isEqualTo("WITHDRAWAL_NOT_APPLIES");
  }

  @Test
  void sellerCannotWithdrawInsteadOfBuyer() {
    Party seller = party("wd-seller2@t.cz", "trader");
    Account buyer = registerAccount("wd-buyer3@t.cz", "private");
    long txId = post("/transactions", Map.of("listingId", seller.listingId()), buyer.token())
        .get("id").asLong();
    post("/transactions/" + txId + "/complete", null, seller.account().token());

    ResponseEntity<String> res = exchange(HttpMethod.POST, "/transactions/" + txId + "/withdraw",
        Map.of("reason", "nechci to"), seller.account().token());

    assertThat(res.getStatusCode().value()).isEqualTo(403);
    assertThat(body(res).get("code").asText()).isEqualTo("ONLY_BUYER");
  }

  @Test
  void outsiderCannotSeeTransaction() {
    Party seller = party("out-seller@t.cz", "private");
    Account buyer = registerAccount("out-buyer@t.cz", "private");
    Account stranger = registerAccount("out-stranger@t.cz", "private");
    long txId = post("/transactions", Map.of("listingId", seller.listingId()), buyer.token())
        .get("id").asLong();

    assertThat(exchange(HttpMethod.GET, "/transactions/" + txId + "/withdrawal-rights", null,
        stranger.token()).getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void dealOverviewAggregatesSellerAndIncludesEarnedRating() {
    Party seller = party("ov-seller@t.cz", "private");

    // dva dokončené obchody, jeden zrušený
    for (int i = 0; i < 2; i++) {
      Account buyer = registerAccount("ov-buyer" + i + "@t.cz", "private");
      long txId = post("/transactions", Map.of("listingId", seller.listingId()), buyer.token())
          .get("id").asLong();
      post("/transactions/" + txId + "/complete", null, seller.account().token());
      post("/transactions/" + txId + "/review",
          Map.of("rating", i == 0 ? 5 : 4, "text", "dobrý obchod"), buyer.token());
    }
    Account canceller = registerAccount("ov-buyer-x@t.cz", "private");
    long cancelledTx = post("/transactions",
        Map.of("listingId", seller.listingId()), canceller.token()).get("id").asLong();
    post("/transactions/" + cancelledTx + "/cancel", null, seller.account().token());

    JsonNode overview = get("/transactions/overview", seller.account().token());
    JsonNode asSeller = overview.get("asSeller");
    assertThat(asSeller.get("completed").asLong()).isEqualTo(2);
    assertThat(asSeller.get("cancelled").asLong()).isEqualTo(1);
    assertThat(asSeller.get("revenueCzk").asLong()).isEqualTo(4000);
    assertThat(asSeller.get("ratingCount").asLong()).isEqualTo(2);
    assertThat(asSeller.get("ratingAverage").asDouble()).isEqualTo(4.5);

    // pohled kupce neobsahuje prodejní agregáty
    assertThat(overview.get("asBuyer").has("revenueCzk")).isFalse();
  }

  @Test
  void mineCanBeFilteredByRole() {
    Party seller = party("role-seller@t.cz", "private");
    Account buyer = registerAccount("role-buyer@t.cz", "private");
    post("/transactions", Map.of("listingId", seller.listingId()), buyer.token());

    assertThat(get("/transactions/mine?role=buyer", buyer.token()).get("data")).hasSize(1);
    assertThat(get("/transactions/mine?role=seller", buyer.token()).get("data")).isEmpty();
    assertThat(get("/transactions/mine?role=seller", seller.account().token()).get("data")).hasSize(1);
    assertThat(get("/transactions/mine", seller.account().token()).get("data")).hasSize(1);
  }

  @Test
  void invalidRoleIsRejected() {
    String any = registerUser("role-invalid@t.cz", "private");
    ResponseEntity<String> res = exchange(HttpMethod.GET, "/transactions/mine?role=root", null, any);
    assertThat(res.getStatusCode().value()).isEqualTo(400);
    assertThat(body(res).get("code").asText()).isEqualTo("INVALID_ROLE");
  }

  @Test
  void listingRightsInfoReflectsSellerTypeSnapshot() {
    Party trader = party("rights-trader@t.cz", "trader");
    JsonNode info = get("/listings/" + trader.listingId() + "/rights-info", null);
    assertThat(info.get("withdrawalApplies").asBoolean()).isTrue();
    assertThat(info.get("explanationCs").asText()).contains("1829");

    Party priv = party("rights-private@t.cz", "private");
    assertThat(get("/listings/" + priv.listingId() + "/rights-info", null)
        .get("withdrawalApplies").asBoolean()).isFalse();
  }
}