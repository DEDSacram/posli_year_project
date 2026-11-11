package cz.bazar.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Správa systému administrátorem: audit log, výpis uživatelů a blokace účtů. */
class AdminGovernanceIT extends AbstractIntegrationTest {

  @Test
  void auditTrailIsReadableAndLogsItself() {
    String admin = adminAccount().token();

    // moderace zaloguje čtení fronty i rozhodnutí
    post("/moderation/reports", Map.of(
        "targetType", "listing", "targetId", 1, "reason", "podezřelý obsah"), admin);
    get("/admin/moderation/queue", admin);

    JsonNode audit = get("/admin/audit?limit=50", admin);
    assertThat(audit.get("data")).isNotEmpty();

    boolean sawQueueRead = false;
    boolean sawAuditRead = false;
    for (JsonNode row : audit.get("data")) {
      String action = row.get("action").asText();
      if (action.equals("MODERATION_QUEUE_READ")) sawQueueRead = true;
      if (action.equals("AUDIT_TRAIL_READ")) sawAuditRead = true;
      assertThat(row.has("id")).isTrue();
      assertThat(row.has("action")).isTrue();
      assertThat(row.has("createdAt")).isTrue();
    }
    assertThat(sawQueueRead).isTrue();
    assertThat(sawAuditRead).isTrue();
  }

  @Test
  void auditCanBeFilteredByAction() {
    String admin = adminAccount().token();
    get("/admin/moderation/queue", admin);

    JsonNode filtered = get("/admin/audit?action=MODERATION_QUEUE_READ", admin);
    assertThat(filtered.get("data")).isNotEmpty();
    for (JsonNode row : filtered.get("data")) {
      assertThat(row.get("action").asText()).isEqualTo("MODERATION_QUEUE_READ");
    }
  }

  @Test
  void userListSupportsSearchAndPaging() {
    String admin = adminAccount().token();
    registerUser("hledany-clovek@t.cz", "private");

    JsonNode found = get("/admin/users?q=hledany", admin);
    assertThat(found.get("data")).isNotEmpty();
    assertThat(found.get("data").get(0).get("email").asText()).isEqualTo("hledany-clovek@t.cz");
    assertThat(found.get("nextCursor")).isNotNull();
  }

  @Test
  void blockedUserCannotLogIn() {
    Account victim = registerAccount("blocked-clovek@t.cz", "private");
    String admin = adminAccount().token();

    JsonNode blocked = post("/admin/users/" + victim.id() + "/block",
        Map.of("reason", "opakované podvádění"), admin);
    assertThat(blocked.get("blocked").asBoolean()).isTrue();

    ResponseEntity<String> login = exchange(HttpMethod.POST, "/auth/login",
        Map.of("email", "blocked-clovek@t.cz", "password", "heslo12345"), null);
    assertThat(login.getStatusCode().is2xxSuccessful()).isFalse();

    // po odblokování se přihlásit znovu umí
    post("/admin/users/" + victim.id() + "/unblock", null, admin);
    ResponseEntity<String> retry = exchange(HttpMethod.POST, "/auth/login",
        Map.of("email", "blocked-clovek@t.cz", "password", "heslo12345"), null);
    assertThat(retry.getStatusCode().is2xxSuccessful()).isTrue();
  }

  @Test
  void cannotBlockAdminOrSelf() {
    Account adminAccount = adminAccount();
    Account other = registerAccount("block-target@t.cz", "private");

    assertThat(exchange(HttpMethod.POST, "/admin/users/" + adminAccount.id() + "/block",
        Map.of("reason", "x"), adminAccount.token()).getStatusCode().value()).isEqualTo(403);
    assertThat(exchange(HttpMethod.POST, "/admin/users/" + adminAccount.id() + "/block",
        Map.of("reason", "x"), other.token()).getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void adminEndpointsAreForbiddenForRegularUsers() {
    Account user = registerAccount("not-admin@t.cz", "private");
    for (String path : new String[]{"/admin/audit", "/admin/users", "/admin/verifications", "/admin/disputes"}) {
      assertThat(exchange(HttpMethod.GET, path, null, user.token()).getStatusCode().value())
          .as(path).isEqualTo(403);
      assertThat(exchange(HttpMethod.GET, path, null, null).getStatusCode().value())
          .as(path + " (host)").isEqualTo(401);
    }
  }

  @Test
  void dac7SummaryIsAvailableForYear() {
    String admin = adminAccount().token();
    JsonNode summary = get("/admin/dac7/summary?year=2025", admin);

    assertThat(summary.get("year").asInt()).isEqualTo(2025);
    assertThat(summary.get("countThreshold").asLong()).isEqualTo(30L);
    assertThat(summary.get("amountThresholdCzk").asLong()).isEqualTo(50000L);
    assertThat(summary.has("sellers")).isTrue();
  }

  @Test
  void dac7ExportSupportsCsvAndDetail() {
    String admin = adminAccount().token();

    ResponseEntity<String> csv = exchange(HttpMethod.GET, "/admin/dac7/export?year=2025&format=csv",
        null, admin);
    assertThat(csv.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(csv.getHeaders().getFirst("Content-Disposition")).contains("dac7-2025.csv");
    assertThat(csv.getBody()).startsWith("user_id;email;full_name;tax_id;ico;iban;sales_count");

    ResponseEntity<String> detail = exchange(HttpMethod.GET,
        "/admin/dac7/export?year=2025&format=csv&detail=true", null, admin);
    assertThat(detail.getBody()).startsWith("transaction_id;seller_id;seller_email;listing_id");

    ResponseEntity<String> xml = exchange(HttpMethod.GET, "/admin/dac7/export?year=2025&format=xml",
        null, admin);
    assertThat(xml.getBody()).contains("<dac7 year=\"2025\">");
  }

  @Test
  void dac7ExportRejectsUnknownFormat() {
    String admin = adminAccount().token();
    assertThat(exchange(HttpMethod.GET, "/admin/dac7/export?year=2025&format=pdf", null, admin)
        .getStatusCode().is4xxClientError()).isTrue();
  }
}