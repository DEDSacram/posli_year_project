package cz.bazar.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Ověřování obchodníků (DSA): prodejce nahraje doklad, administrátor ho schválí
 * nebo zamítne a v profilu se objeví odznak „ověřený obchodník“.
 */
class TraderVerificationIT extends AbstractIntegrationTest {

  /** fillSellerProfile u obchodníka už nahraje doklad (nutné před uložením profilu). */
  private Account traderWithDocument(String email) {
    Account acc = registerAccount(email, "trader");
    fillSellerProfile(acc.token(), "trader");
    return acc;
  }

  @Test
  void traderUploadsDocumentAndAdminSeesItInQueue() {
    Account trader = traderWithDocument("verif-trader@t.cz");
    String admin = adminAccount().token();

    JsonNode queue = get("/admin/verifications", admin);
    assertThat(queue.get("data")).isNotEmpty();
    JsonNode row = null;
    for (JsonNode r : queue.get("data")) {
      if (r.get("userId").asLong() == trader.id()) row = r;
    }
    assertThat(row).isNotNull();
    assertThat(row.get("status").asText()).isEqualTo("pending");
    assertThat(row.get("hasIco").asBoolean()).isTrue();
    assertThat(row.get("selfCert").asBoolean()).isTrue();
  }

  @Test
  void adminApprovalGrantsVerifiedTraderBadge() {
    Account trader = traderWithDocument("verif-approve@t.cz");
    String admin = adminAccount().token();

    JsonNode before = get("/users/" + trader.id() + "/badges", null);
    assertThat(before.get("verifiedTrader").asBoolean()).isFalse();
    assertThat(before.get("trader").asBoolean()).isTrue();

    ResponseEntity<String> res = exchange(HttpMethod.POST,
        "/admin/verifications/" + trader.id() + "/approve",
        Map.of("note", "doklad v pořádku"), admin);
    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(body(res).get("status").asText()).isEqualTo("verified");

    JsonNode after = get("/users/" + trader.id() + "/badges", null);
    assertThat(after.get("verifiedTrader").asBoolean()).isTrue();

    // prodejce uvidí stav ve svém účtu
    JsonNode mine = get("/seller/me/verification", trader.token());
    assertThat(mine.get("status").asText()).isEqualTo("verified");
    assertThat(mine.get("hasDocument").asBoolean()).isTrue();
  }

  @Test
  void adminRejectionIsExplainedToTrader() {
    Account trader = traderWithDocument("verif-reject@t.cz");
    String admin = adminAccount().token();

    post("/admin/verifications/" + trader.id() + "/reject",
        Map.of("note", "doklad je nečitelný"), admin);

    JsonNode mine = get("/seller/me/verification", trader.token());
    assertThat(mine.get("status").asText()).isEqualTo("rejected");
    assertThat(mine.get("note").asText()).isEqualTo("doklad je nečitelný");
    assertThat(get("/users/" + trader.id() + "/badges", null).get("verifiedTrader").asBoolean()).isFalse();
  }

  @Test
  void cannotDecideVerificationTwice() {
    Account trader = traderWithDocument("verif-twice@t.cz");
    String admin = adminAccount().token();

    post("/admin/verifications/" + trader.id() + "/approve", Map.of("note", "ok"), admin);
    ResponseEntity<String> res = exchange(HttpMethod.POST,
        "/admin/verifications/" + trader.id() + "/approve", Map.of("note", "znovu"), admin);

    assertThat(res.getStatusCode().value()).isEqualTo(409);
    assertThat(body(res).get("code").asText()).isEqualTo("VERIFICATION_ALREADY_DECIDED");
  }

  @Test
  void reuploadingDocumentRequiresNewApproval() {
    Account trader = traderWithDocument("verif-reupload@t.cz");
    String admin = adminAccount().token();

    post("/admin/verifications/" + trader.id() + "/approve", Map.of("note", "ok"), admin);
    assertThat(get("/users/" + trader.id() + "/badges", null).get("verifiedTrader").asBoolean()).isTrue();

    assertThat(uploadDocument(trader.token()).getStatusCode().is2xxSuccessful()).isTrue();

    // po nahrání nového dokladu je obchodník znovu neověřený
    assertThat(get("/users/" + trader.id() + "/badges", null).get("verifiedTrader").asBoolean()).isFalse();
    assertThat(get("/seller/me/verification", trader.token()).get("status").asText()).isEqualTo("pending");
  }

  @Test
  void privateSellerCannotBeVerified() {
    Account priv = registerAccount("verif-private@t.cz", "private");
    fillSellerProfile(priv.token(), "private");
    uploadDocument(priv.token()); // soukromík mohl doklad nahrát, jen ho nelze „ověřit“ jako obchodníka
    String admin = adminAccount().token();

    ResponseEntity<String> res = exchange(HttpMethod.POST,
        "/admin/verifications/" + priv.id() + "/approve", Map.of("note", "x"), admin);

    assertThat(res.getStatusCode().value()).isEqualTo(400);
    assertThat(body(res).get("code").asText()).isEqualTo("NOT_A_TRADER");
  }

  @Test
  void verificationEndpointsRequireAdmin() {
    Account trader = traderWithDocument("verif-guard@t.cz");
    String buyer = registerUser("verif-buyer@t.cz", "private");

    assertThat(exchange(HttpMethod.GET, "/admin/verifications", null, null).getStatusCode().value())
        .isEqualTo(401);
    assertThat(exchange(HttpMethod.GET, "/admin/verifications", null, buyer).getStatusCode().value())
        .isEqualTo(403);
    assertThat(exchange(HttpMethod.POST, "/admin/verifications/" + trader.id() + "/approve",
        Map.of("note", "x"), buyer).getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void traderWithoutIcoCannotPublishListing() {
    // obchodník bez IČO/dokladu je odmítnut už při uložení profilu
    Account acc = registerAccount("verif-noico@t.cz", "trader");
    Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("sellerType", "trader");
    payload.put("fullName", "Jan Novák");
    payload.put("dateOfBirth", "1990-05-05");
    payload.put("street", "Dlouhá 12");
    payload.put("city", "Praha");
    payload.put("postalCode", "110 00");
    payload.put("country", "CZ");
    payload.put("iban", "CZ6508000000192000145399");
    payload.put("selfCertification", true); // IČO chybí

    ResponseEntity<String> res = exchange(HttpMethod.PATCH, "/users/me/seller-info", payload, acc.token());

    assertThat(res.getStatusCode().value()).isEqualTo(422);
    assertThat(body(res).get("code").asText()).isEqualTo("TRADER_PROFILE_INCOMPLETE");
  }
}