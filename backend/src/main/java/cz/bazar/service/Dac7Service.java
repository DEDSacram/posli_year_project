package cz.bazar.service;

import cz.bazar.crypto.AesGcmService;
import cz.bazar.domain.Listing;
import cz.bazar.domain.SaleTransaction;
import cz.bazar.domain.SellerTaxProfile;
import cz.bazar.domain.User;
import cz.bazar.repository.ListingRepository;
import cz.bazar.repository.SaleTransactionRepository;
import cz.bazar.repository.SellerTaxProfileRepository;
import cz.bazar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Daňový výkaz pro platformu (DAC7 — směrnice 2011/16/EU, implementovaná v ČR
 * zákonným opatřením a vyhláškou k DZ). Výkaz obsahuje za každého prodejce
 * identifikační údaje (jméno, DIČ/IČO), počet a hodnotu prodejů a příznak,
 * zda prodejce překročil reportingový práh.
 */
@Service
public class Dac7Service {
  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private final SaleTransactionRepository transactions;
  private final UserRepository users;
  private final SellerTaxProfileRepository profiles;
  private final ListingRepository listings;
  private final AesGcmService crypto;

  public Dac7Service(SaleTransactionRepository transactions, UserRepository users,
                     SellerTaxProfileRepository profiles, ListingRepository listings,
                     AesGcmService crypto) {
    this.transactions = transactions; this.users = users;
    this.profiles = profiles; this.listings = listings; this.crypto = crypto;
  }

  /** Řádek výkazu — jedna obchodující osoba. */
  public record Row(Long userId, String email, String fullName, String taxId, String ico,
                    String iban, long salesCount, long totalAmountCzk, boolean reportable) {}

  /** Detail jednoho prodeje pro rozšířený výkaz (`?detail=true`). */
  public record SaleLine(Long transactionId, Long sellerId, String sellerEmail, Long listingId,
                         String listingTitle, int priceCzk, String completedAt, String sellerType) {}

  public List<Row> collect(int year) {
    List<Object[]> agg = transactions.aggregateBySellerAndYear(year);
    List<Row> out = new ArrayList<>();
    for (Object[] a : agg) {
      Long sellerId = ((Number) a[0]).longValue();
      long count = ((Number) a[1]).longValue();
      long total = a[2] == null ? 0 : ((Number) a[2]).longValue();
      boolean reportable = count >= SellerService.DAC7_COUNT_THRESHOLD
          || total >= SellerService.DAC7_AMOUNT_THRESHOLD_CZK;
      if (!reportable) continue; // jen prodejci nad prahem
      out.add(rowFor(sellerId, count, total, true));
    }
    return out;
  }

  /** Všichni prodejci daného roku včetně těch pod prahem (pro kontrolní souhrn). */
  public List<Row> collectAll(int year) {
    List<Object[]> agg = transactions.aggregateBySellerAndYear(year);
    List<Row> out = new ArrayList<>();
    for (Object[] a : agg) {
      Long sellerId = ((Number) a[0]).longValue();
      long count = ((Number) a[1]).longValue();
      long total = a[2] == null ? 0 : ((Number) a[2]).longValue();
      boolean reportable = count >= SellerService.DAC7_COUNT_THRESHOLD
          || total >= SellerService.DAC7_AMOUNT_THRESHOLD_CZK;
      out.add(rowFor(sellerId, count, total, reportable));
    }
    return out;
  }

  private Row rowFor(Long sellerId, long count, long total, boolean reportable) {
    User u = users.findById(sellerId).orElse(null);
    String email = u == null ? "" : u.getEmail();
    String name = "", taxId = "", ico = "", iban = "";
    SellerTaxProfile p = profiles.findById(sellerId).orElse(null);
    if (p != null) {
      name = dec(p.getFullName());
      taxId = dec(p.getTaxId());
      ico = dec(p.getIco());
      iban = dec(p.getIban());
    }
    return new Row(sellerId, email, name, taxId, ico, iban, count, total, reportable);
  }

  /** Rozšířený výkaz: agregace po prodejcích i jednotlivé prodeje. */
  @Transactional(readOnly = true)
  public List<SaleLine> saleLines(int year) {
    List<SaleLine> out = new ArrayList<>();
    for (SaleTransaction t : transactions.findAllCompletedByYear(year)) {
      String email = users.findById(t.getSellerId()).map(User::getEmail).orElse("");
      String title = listings.findById(t.getListingId()).map(Listing::getTitle).orElse("");
      out.add(new SaleLine(t.getId(), t.getSellerId(), email, t.getListingId(), title,
          t.getPriceCzk(),
          t.getCompletedAt() == null ? null : TS.format(t.getCompletedAt()),
          t.getSellerTypeSnapshot()));
    }
    return out;
  }

  /** Souhrn pro dashboard administrace (JSON, bez exportu do souboru). */
  @Transactional(readOnly = true)
  public Map<String, Object> summary(int year) {
    List<Row> all = collectAll(year);
    long reportable = all.stream().filter(Row::reportable).count();
    long totalSales = all.stream().mapToLong(Row::salesCount).sum();
    long totalAmount = all.stream().mapToLong(Row::totalAmountCzk).sum();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("year", year);
    m.put("sellerCount", all.size());
    m.put("reportableSellerCount", reportable);
    m.put("totalSales", totalSales);
    m.put("totalAmountCzk", totalAmount);
    m.put("countThreshold", SellerService.DAC7_COUNT_THRESHOLD);
    m.put("amountThresholdCzk", SellerService.DAC7_AMOUNT_THRESHOLD_CZK);
    m.put("sellers", all);
    return m;
  }

  public String exportCsv(int year) {
    StringBuilder sb = new StringBuilder(
        "user_id;email;full_name;tax_id;ico;iban;sales_count;total_amount_czk;reportable\n");
    for (Row r : collect(year)) {
      sb.append(r.userId()).append(';').append(esc(r.email())).append(';')
        .append(esc(r.fullName())).append(';').append(esc(r.taxId())).append(';')
        .append(esc(r.ico())).append(';').append(esc(r.iban())).append(';')
        .append(r.salesCount()).append(';').append(r.totalAmountCzk()).append(';')
        .append(r.reportable()).append('\n');
    }
    return sb.toString();
  }

  public String exportSalesCsv(int year) {
    StringBuilder sb = new StringBuilder(
        "transaction_id;seller_id;seller_email;listing_id;price_czk;completed_at;seller_type\n");
    for (SaleLine s : saleLines(year)) {
      sb.append(s.transactionId()).append(';').append(s.sellerId()).append(';')
        .append(esc(s.sellerEmail())).append(';').append(s.listingId()).append(';')
        .append(s.priceCzk()).append(';').append(esc(s.completedAt())).append(';')
        .append(esc(s.sellerType())).append('\n');
    }
    return sb.toString();
  }

  public String exportXml(int year) {
    StringBuilder sb = new StringBuilder(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dac7 year=\"" + year + "\">\n");
    for (Row r : collect(year)) {
      sb.append("  <seller id=\"").append(r.userId()).append("\">\n")
        .append("    <email>").append(xml(r.email())).append("</email>\n")
        .append("    <fullName>").append(xml(r.fullName())).append("</fullName>\n")
        .append("    <taxId>").append(xml(r.taxId())).append("</taxId>\n")
        .append("    <ico>").append(xml(r.ico())).append("</ico>\n")
        .append("    <salesCount>").append(r.salesCount()).append("</salesCount>\n")
        .append("    <totalAmountCzk>").append(r.totalAmountCzk()).append("</totalAmountCzk>\n")
        .append("    <reportable>").append(r.reportable()).append("</reportable>\n")
        .append("  </seller>\n");
    }
    sb.append("</dac7>\n");
    return sb.toString();
  }

  public String exportSalesXml(int year) {
    StringBuilder sb = new StringBuilder(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dac7-sales year=\"" + year + "\">\n");
    for (SaleLine s : saleLines(year)) {
      sb.append("  <sale id=\"").append(s.transactionId()).append("\">\n")
        .append("    <sellerId>").append(s.sellerId()).append("</sellerId>\n")
        .append("    <sellerEmail>").append(xml(s.sellerEmail())).append("</sellerEmail>\n")
        .append("    <listingId>").append(s.listingId()).append("</listingId>\n")
        .append("    <priceCzk>").append(s.priceCzk()).append("</priceCzk>\n")
        .append("    <completedAt>").append(xml(s.completedAt())).append("</completedAt>\n")
        .append("    <sellerType>").append(xml(s.sellerType())).append("</sellerType>\n")
        .append("  </sale>\n");
    }
    sb.append("</dac7-sales>\n");
    return sb.toString();
  }

  private String dec(byte[] v) {
    if (v == null || v.length == 0) return "";
    try {
      String s = crypto.decrypt(v);
      return s == null ? "" : s;
    } catch (Exception e) {
      return ""; // starší šifra po rotaci klíče nesmí shodit celý výkaz
    }
  }

  private String esc(String s) {
    if (s == null) return "";
    return s.replace(";", ",").replace("\r", " ").replace("\n", " ");
  }

  private String xml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}