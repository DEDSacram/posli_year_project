package cz.bazar.service;

import cz.bazar.crypto.AesGcmService;
import cz.bazar.domain.Listing;
import cz.bazar.domain.SellerTaxProfile;
import cz.bazar.domain.User;
import cz.bazar.repository.ListingRepository;
import cz.bazar.repository.SaleTransactionRepository;
import cz.bazar.repository.SellerTaxProfileRepository;
import cz.bazar.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Daňový výkaz DAC7: práh reportovatelnosti, agregace, oddělení CSV/XML, escapování. */
class Dac7ServiceTest {

  private SaleTransactionRepository transactions;
  private UserRepository users;
  private SellerTaxProfileRepository profiles;
  private ListingRepository listings;
  private Dac7Service service;
  private AesGcmService crypto;

  @BeforeEach
  void setUp() {
    transactions = mock(SaleTransactionRepository.class);
    users = mock(UserRepository.class);
    profiles = mock(SellerTaxProfileRepository.class);
    listings = mock(ListingRepository.class);
    byte[] key = new byte[32];
    crypto = new AesGcmService(Base64.getEncoder().encodeToString(key));
    service = new Dac7Service(transactions, users, profiles, listings, crypto);
  }

  /** Jeden řádek agregace jako List<Object[]> (aggregace vrací List<Object[]>). */
  private static List<Object[]> agg(Object... cells) {
    return java.util.Collections.singletonList(cells);
  }

  private void seller(Long id, String email) {
    User u = new User();
    u.setId(id);
    u.setEmail(email);
    when(users.findById(id)).thenReturn(java.util.Optional.of(u));
  }

  private void taxProfile(Long id, String name, String taxId, String ico) {
    SellerTaxProfile p = new SellerTaxProfile();
    p.setUserId(id);
    p.setFullName(crypto.encrypt(name));
    p.setTaxId(crypto.encrypt(taxId));
    p.setIco(crypto.encrypt(ico));
    p.setIban(crypto.encrypt("CZ6508000000192000145399"));
    when(profiles.findById(id)).thenReturn(java.util.Optional.of(p));
  }

  @Test
  void sellerUnderCountThresholdIsNotReportable() {
    when(transactions.aggregateBySellerAndYear(anyInt()))
        .thenReturn(agg(1L, 29L, 49000L));
    seller(1L, "a@test.cz");
    taxProfile(1L, "Jan Novák", "CZ123", "12345678");

    assertThat(service.collect(2025)).isEmpty();
  }

  @Test
  void sellerUnderAmountThresholdIsNotReportable() {
    when(transactions.aggregateBySellerAndYear(anyInt()))
        .thenReturn(agg(1L, 2L, 49999L));
    seller(1L, "a@test.cz");
    taxProfile(1L, "Jan Novák", "CZ123", "12345678");

    assertThat(service.collect(2025)).isEmpty();
  }

  @Test
  void thirtySalesIsReportable() {
    when(transactions.aggregateBySellerAndYear(anyInt()))
        .thenReturn(agg(1L, 30L, 1000L));
    seller(1L, "a@test.cz");
    taxProfile(1L, "Jan Novák", "CZ123", "12345678");

    List<Dac7Service.Row> rows = service.collect(2025);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).salesCount()).isEqualTo(30L);
    assertThat(rows.get(0).reportable()).isTrue();
  }

  @Test
  void fiftyThousandCZKIsReportable() {
    when(transactions.aggregateBySellerAndYear(anyInt()))
        .thenReturn(agg(1L, 1L, 50000L));
    seller(1L, "a@test.cz");
    taxProfile(1L, "Jan Novák", "CZ123", "12345678");

    assertThat(service.collect(2025)).hasSize(1);
  }

  @Test
  void collectAllKeepsSellersBelowThreshold() {
    when(transactions.aggregateBySellerAndYear(anyInt()))
        .thenReturn(List.<Object[]>of(new Object[]{1L, 1L, 100L}, new Object[]{2L, 30L, 100L}));
    seller(1L, "a@test.cz");
    seller(2L, "b@test.cz");

    assertThat(service.collectAll(2025)).hasSize(2);
    assertThat(service.collectAll(2025).get(0).reportable()).isFalse();
    assertThat(service.collectAll(2025).get(1).reportable()).isTrue();
  }

  @Test
  void summaryAggregatesWholeYear() {
    when(transactions.aggregateBySellerAndYear(anyInt())).thenReturn(List.<Object[]>of(new Object[]{1L, 30L, 60000L}, new Object[]{2L, 5L, 5000L}));
    seller(1L, "a@test.cz");
    seller(2L, "b@test.cz");

    var s = service.summary(2025);

    assertThat(s).containsEntry("year", 2025).containsEntry("sellerCount", 2)
        .containsEntry("reportableSellerCount", 1L)
        .containsEntry("totalSales", 35L)
        .containsEntry("totalAmountCzk", 65000L)
        .containsEntry("countThreshold", SellerService.DAC7_COUNT_THRESHOLD);
  }

  @Test
  void csvContainsTaxIdentifiersAndEscapesSeparators() {
    when(transactions.aggregateBySellerAndYear(anyInt()))
        .thenReturn(agg(1L, 30L, 1000L));
    seller(1L, "a@test.cz");
    taxProfile(1L, "Jan; Novák", "CZ123", "12345678");

    String csv = service.exportCsv(2025);

    assertThat(csv).startsWith("user_id;email;full_name;tax_id;ico;iban;sales_count");
    // středník v jméně se nahradí čárkou, aby se ne rozbil sloupec
    assertThat(csv).contains("Jan, Novák").contains("CZ123").contains("12345678");
    assertThat(csv).contains("CZ6508000000192000145399");
  }

  @Test
  void xmlEscapesMarkupCharacters() {
    when(transactions.aggregateBySellerAndYear(anyInt()))
        .thenReturn(agg(1L, 30L, 1000L));
    seller(1L, "a<b>&\"c\".test.cz");
    taxProfile(1L, "Jan <Novák>", "CZ1", "1");

    String xml = service.exportXml(2025);

    assertThat(xml).contains("<dac7 year=\"2025\">");
    assertThat(xml).contains("a&lt;b&gt;&amp;&quot;c&quot;.test.cz");
    assertThat(xml).contains("Jan &lt;Novák&gt;");
    // surové '<' nesmí prosáknout do výstupu
    assertThat(xml).doesNotContain("a<b>");
  }

  @Test
  void detailExportListsIndividualSales() {
    var t = new cz.bazar.domain.SaleTransaction();
    t.setId(1L);
    t.setSellerId(2L);
    t.setListingId(3L);
    t.setPriceCzk(1500);
    t.setSellerTypeSnapshot("trader");
    t.setCompletedAt(OffsetDateTime.parse("2025-06-01T10:00:00+02:00"));
    when(transactions.findAllCompletedByYear(2025)).thenReturn(List.of(t));
    seller(2L, "seller@test.cz");
    when(listings.findById(3L)).thenReturn(Optional.of(listing("Původní kočárek")));

    String csv = service.exportSalesCsv(2025);

    assertThat(csv).startsWith("transaction_id;seller_id;seller_email;listing_id;price_czk");
    assertThat(csv).contains(";2;seller@test.cz;3;1500;");
    assertThat(csv).contains("trader");
  }

  @Test
  void missingTaxProfileStillProducesRow() {
    when(transactions.aggregateBySellerAndYear(anyInt()))
        .thenReturn(agg(1L, 30L, 1000L));
    seller(1L, "a@test.cz");
    when(profiles.findById(1L)).thenReturn(java.util.Optional.empty());

    var row = service.collect(2025).get(0);

    assertThat(row.fullName()).isEmpty();
    assertThat(row.email()).isEqualTo("a@test.cz");
  }

  @Test
  void undecryptableProfileDoesNotBreakTheWholeExport() {
    // Data zašifrovaná jiným klíčem nesmí shodit celý výkaz.
    when(transactions.aggregateBySellerAndYear(anyInt()))
        .thenReturn(agg(1L, 30L, 1000L));
    seller(1L, "a@test.cz");
    SellerTaxProfile broken = new SellerTaxProfile();
    broken.setUserId(1L);
    broken.setFullName("not-encrypted".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    when(profiles.findById(1L)).thenReturn(java.util.Optional.of(broken));

    assertThat(service.collect(2025).get(0).fullName()).isEmpty();
  }

  private static Listing listing(String title) {
    Listing l = new Listing();
    l.setId(3L);
    l.setTitle(title);
    return l;
  }
}