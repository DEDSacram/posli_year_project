package cz.bazar.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

/**
 * Společná základna integračních testů: celý kontext Spring Boot včetně
 * PostgreSQL v Testcontainers (ParadeDB image, aby byly dostupné i extenze
 * pg_search / earthdistance / citext jako v produkci) a Liquibase schéma z V1–V10.
 *
 * <p>Kontejner se spouští ručně v statickém bloku, ne anotací {@code @Container}:
 * Spring kontext se mezi testovacími třídami cachuje, takže kontejner musí zůstat
 * živý po celý JVM. {@code @Testcontainers} by ho po každé třídě zastavil.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

  static final PostgreSQLContainer<?> DB;

  static {
    DB = new PostgreSQLContainer<>(
        // ParadeDB je PostgreSQL + extenze pg_search/cube/earthdistance, které produkce používá.
        DockerImageName.parse("paradedb/paradedb:latest").asCompatibleSubstituteFor("postgres"));
    DB.start();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        DB.stop();
      } catch (RuntimeException ignored) {
        // kontejner umí být ukončen spolu s JVM
      }
    }));
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
    r.add("app.jwt.secret", () -> "it-secret-key-min-32-bytes-long-0123456789");
    r.add("app.tax-key", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    r.add("app.admin-email", () -> "admin@bazar.cz");
    // Testy zakládají desítky účtů za sekundu — limity by je zastavily.
    // Jejich chování je pokryto samostatnými testy, ne náhodnou neshodou pořadí.
    r.add("app.rate-limit.enabled", () -> "false");
  }

  @LocalServerPort
  int port;

  @Autowired
  TestRestTemplate rest;

  protected final ObjectMapper mapper = new ObjectMapper();

  // ---------- pomocné metody pro volání API ----------

  protected String url(String path) {
    return "http://localhost:" + port + path;
  }

  protected JsonNode get(String path, String token) {
    return body(exchange(HttpMethod.GET, path, null, token));
  }

  protected JsonNode post(String path, Object payload, String token) {
    return body(exchange(HttpMethod.POST, path, payload, token));
  }

  protected JsonNode patch(String path, Object payload, String token) {
    return body(exchange(HttpMethod.PATCH, path, payload, token));
  }

  protected ResponseEntity<String> exchange(HttpMethod method, String path, Object payload, String token) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) h.setBearerAuth(token);
    return rest.exchange(url(path), method, new HttpEntity<>(payload, h), String.class);
  }

  protected JsonNode body(ResponseEntity<String> res) {
    try {
      return res.getBody() == null ? mapper.createObjectNode() : mapper.readTree(res.getBody());
    } catch (Exception e) {
      throw new IllegalStateException("Rozmazaná odpověď: " + res.getBody(), e);
    }
  }

  /** Založený účet: ID uživatele a přihlašovací token. */
  public record Account(long id, String token) {}

  private static final String ADMIN_EMAIL = "admin@bazar.cz";
  private static final String PASSWORD = "heslo12345";

  protected Account registerAccount(String email, String sellerType) {
    ResponseEntity<String> res = exchange(HttpMethod.POST, "/auth/register",
        Map.of("email", email, "password", PASSWORD, "displayName", "Uživatel " + sellerType,
            "sellerType", sellerType), null);
    if (!res.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException("Registrace selhala: " + res.getBody());
    }
    JsonNode json = body(res);
    return new Account(json.get("id").asLong(), json.get("token").asText());
  }

  /** Založí uživatele a vrátí jeho přihlašovací token. */
  protected String registerUser(String email, String sellerType) {
    return registerAccount(email, sellerType).token();
  }

  protected Account loginAccount(String email) {
    ResponseEntity<String> res = exchange(HttpMethod.POST, "/auth/login",
        Map.of("email", email, "password", PASSWORD), null);
    if (!res.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException("Přihlášení selhalo: " + res.getBody());
    }
    JsonNode json = body(res);
    return new Account(json.get("id").asLong(), json.get("token").asText());
  }

  /**
   * Administrátor je podle e-mailu z app.admin-email (viz CustomUserDetailsService).
   * Všechny IT třídy sdílejí jednu databázi, proto je volání idempotentní:
   * když účet existuje, přihlásí se.
   */
  protected synchronized Account adminAccount() {
    if (admin == null) {
      ResponseEntity<String> res = exchange(HttpMethod.POST, "/auth/register",
          Map.of("email", ADMIN_EMAIL, "password", PASSWORD, "displayName", "Administrátor",
              "sellerType", "private"), null);
      admin = res.getStatusCode().is2xxSuccessful() ? accountOf(body(res)) : loginAccount(ADMIN_EMAIL);
    }
    return admin;
  }

  private static Account admin;

  private static Account accountOf(JsonNode json) {
    return new Account(json.get("id").asLong(), json.get("token").asText());
  }

  /**
   * Vyplní daňový profil, aby uživatel mohl vystavovat inzeráty (jinak 422).
   * Obchodník musí mít nahraný doklad ještě před uložením profilu, proto se doklad
   * vloží jako první (bez profilu ho endpoint přijme a vytvoří minimální záznam).
   */
  protected void fillSellerProfile(String token, String sellerType) {
    if ("trader".equals(sellerType)) uploadDocument(token);
    Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("sellerType", sellerType);
    payload.put("fullName", "Jan Novák");
    payload.put("dateOfBirth", "1990-05-05");
    payload.put("street", "Dlouhá 12");
    payload.put("city", "Praha");
    payload.put("postalCode", "110 00");
    payload.put("country", "CZ");
    payload.put("iban", "CZ6508000000192000145399");
    if ("trader".equals(sellerType)) {
      payload.put("ico", "12345678");
      payload.put("selfCertification", true);
    }
    ResponseEntity<String> res = exchange(HttpMethod.PATCH, "/users/me/seller-info", payload, token);
    if (!res.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException("seller-info selhalo: " + res.getBody());
    }
  }

  /** Nahraje obrázek dokladu totožnosti (nutné před dokončením profilu obchodníka). */
  protected ResponseEntity<String> uploadDocument(String token) {
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("file", new ByteArrayResource(png) {
      @Override
      public String getFilename() {
        return "doklad.png";
      }
    });
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.MULTIPART_FORM_DATA);
    h.setBearerAuth(token);
    return rest.exchange(url("/users/me/id-document"), HttpMethod.POST,
        new org.springframework.http.HttpEntity<>(form, h), String.class);
  }
}