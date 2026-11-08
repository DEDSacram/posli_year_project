package cz.bazar.service;

import cz.bazar.crypto.AesGcmService;
import cz.bazar.domain.SellerTaxProfile;
import cz.bazar.domain.User;
import cz.bazar.dto.Dtos.*;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.SaleTransactionRepository;
import cz.bazar.repository.SellerTaxProfileRepository;
import cz.bazar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SellerService {
  /** 2000 EUR ~ 50 000 CZK (orientacni kurz 25 CZK/EUR dle design_doc). */
  public static final long DAC7_AMOUNT_THRESHOLD_CZK = 50_000L;
  public static final long DAC7_COUNT_THRESHOLD = 30L;

  private final SellerTaxProfileRepository profiles;
  private final UserRepository users;
  private final SaleTransactionRepository transactions;
  private final AesGcmService crypto;
  private final CurrentUserService current;
  private final AuditService audit;

  public SellerService(SellerTaxProfileRepository profiles, UserRepository users,
                       SaleTransactionRepository transactions, AesGcmService crypto,
                       CurrentUserService current, AuditService audit) {
    this.profiles = profiles; this.users = users;
    this.transactions = transactions; this.crypto = crypto; this.current = current;
    this.audit = audit;
  }

  @Transactional
  public void saveSellerInfo(SellerInfoRequest r) {
    User me = current.require();
    if (r.sellerType() != null && !Set.of("private", "trader").contains(r.sellerType())) {
      throw ApiException.badRequest("INVALID_SELLER_TYPE", "Neplatný typ prodejce.");
    }
    String newType = r.sellerType() != null ? r.sellerType() : me.getSellerType();
    me.setSellerType(newType);
    users.save(me);

    SellerTaxProfile p = profiles.findById(me.getId()).orElseGet(() -> {
      SellerTaxProfile np = new SellerTaxProfile();
      np.setUserId(me.getId());
      return np;
    });
    if (r.fullName() != null) p.setFullName(crypto.encrypt(r.fullName()));
    if (r.dateOfBirth() != null) p.setDateOfBirth(crypto.encrypt(r.dateOfBirth()));
    if (r.street() != null) p.setStreet(crypto.encrypt(r.street()));
    if (r.city() != null) p.setCity(crypto.encrypt(r.city()));
    if (r.postalCode() != null) p.setPostalCode(crypto.encrypt(r.postalCode()));
    if (r.country() != null) p.setCountry(crypto.encrypt(r.country()));
    if (r.taxId() != null) p.setTaxId(crypto.encrypt(r.taxId()));
    if (r.ico() != null) p.setIco(crypto.encrypt(r.ico()));
    if (r.iban() != null) p.setIban(crypto.encrypt(r.iban()));
    if (r.selfCertification() != null) p.setSelfCert(r.selfCertification());
    p.setUpdatedAt(OffsetDateTime.now());

    validateForType(newType, p);
    profiles.save(p);
  }

  private void validateForType(String type, SellerTaxProfile p) {
    boolean hasIco = p.getIco() != null && crypto.decrypt(p.getIco()) != null
        && !crypto.decrypt(p.getIco()).isBlank();
    boolean hasDoc = p.getIdDoc() != null && p.getIdDoc().length > 0;
    if ("trader".equals(type)) {
      if (!hasIco || !hasDoc || !p.isSelfCert()) {
        throw ApiException.unprocessable("TRADER_PROFILE_INCOMPLETE",
            "Obchodník musí mít IČO, kopii dokladu a čestné prohlášení.");
      }
    }
    if (p.getFullName() == null || p.getIban() == null) {
      throw ApiException.unprocessable("DAC7_PROFILE_REQUIRED",
          "Doplňte daňový profil (jméno, datum narození, adresa, IBAN).");
    }
  }

  @Transactional
  public void uploadIdDocument(MultipartFile file) {
    User me = current.require();
    if (file == null || file.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "Soubor je prázdný.");
    if (file.getSize() > 5 * 1024 * 1024) throw ApiException.badRequest("FILE_TOO_LARGE", "Soubor je větší než 5 MB.");
    String ct = file.getContentType() == null ? "" : file.getContentType();
    if (!ct.startsWith("image/") && !ct.equals("application/pdf")) {
      throw ApiException.badRequest("INVALID_MIME", "Povolen je pouze obrázek nebo PDF.");
    }
    try {
      SellerTaxProfile p = profiles.findById(me.getId()).orElseGet(() -> {
        SellerTaxProfile np = new SellerTaxProfile();
        np.setUserId(me.getId());
        return np;
      });
      // minimalni profil kdyz jeste neexistuje (sifrovane placeholdery se doplni pres seller-info)
      if (p.getFullName() == null) p.setFullName(crypto.encrypt("—"));
      if (p.getDateOfBirth() == null) p.setDateOfBirth(crypto.encrypt("—"));
      if (p.getStreet() == null) p.setStreet(crypto.encrypt("—"));
      if (p.getCity() == null) p.setCity(crypto.encrypt("—"));
      if (p.getPostalCode() == null) p.setPostalCode(crypto.encrypt("—"));
      if (p.getCountry() == null) p.setCountry(crypto.encrypt("—"));
      if (p.getIban() == null) p.setIban(crypto.encrypt("—"));
      p.setIdDoc(file.getBytes());
      // Nový doklad vždy znovu putuje k ověření administrátorem (DSA).
      p.applyVerificationStatus(SellerTaxProfile.STATUS_PENDING);
      p.setVerificationNote(null);
      p.setVerifiedAt(null);
      p.setVerifiedBy(null);
      p.setUpdatedAt(OffsetDateTime.now());
      profiles.save(p);
    } catch (ApiException e) { throw e; }
    catch (Exception e) { throw ApiException.badRequest("UPLOAD_FAILED", "Nahrání dokladu selhalo."); }
  }

  public Dac7Status dac7Status() {
    User me = current.require();
    int year = LocalDate.now().getYear();
    long count = transactions.countCompletedBySellerAndYear(me.getId(), year);
    long total = transactions.sumCompletedBySellerAndYear(me.getId(), year);
    boolean reportable = count >= DAC7_COUNT_THRESHOLD || total >= DAC7_AMOUNT_THRESHOLD_CZK;
    return new Dac7Status(count, total, reportable);
  }

  /** Poloha z daňového profilu pro předvyplnění inzerátu (jen vlastní údaje). */
  public Map<String, Object> myLocation() {
    User me = current.require();
    SellerTaxProfile p = profiles.findById(me.getId()).orElse(null);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("hasProfile", p != null && p.getCity() != null);
    m.put("city", p == null ? null : crypto.decrypt(p.getCity()));
    m.put("postalCode", p == null ? null : crypto.decrypt(p.getPostalCode()));
    m.put("country", p == null ? null : crypto.decrypt(p.getCountry()));
    return m;
  }

  /** Uložení polohy z inzerátu do profilu (jen město + PSČ, bez validace celého DAC7). */
  @Transactional
  public Map<String, Object> saveLocation(LocationUpdate r) {
    User me = current.require();
    String city = r.city() == null ? "" : r.city().strip();
    String zip = r.postalCode() == null ? "" : r.postalCode().replaceAll("\\s", "");
    if (city.length() < 2) {
      throw ApiException.badRequest("INVALID_CITY", "Zadejte město.");
    }
    if (!zip.matches("\\d{3,10}")) {
      throw ApiException.badRequest("INVALID_ZIP", "PSČ má obsahovat číslice (např. 110 00).");
    }
    SellerTaxProfile p = profiles.findById(me.getId()).orElseThrow(() ->
        ApiException.unprocessable("DAC7_PROFILE_REQUIRED",
            "Nejdřív vyplňte daňový profil v sekci Můj účet, pak si do něj můžete ukládat polohu."));
    p.setCity(crypto.encrypt(city));
    p.setPostalCode(crypto.encrypt(zip));
    p.setUpdatedAt(OffsetDateTime.now());
    profiles.save(p);
    return myLocation();
  }

  public Dac7Status dac7StatusFor(Long userId, int year) {
    long count = transactions.countCompletedBySellerAndYear(userId, year);
    long total = transactions.sumCompletedBySellerAndYear(userId, year);
    boolean reportable = count >= DAC7_COUNT_THRESHOLD || total >= DAC7_AMOUNT_THRESHOLD_CZK;
    return new Dac7Status(count, total, reportable);
  }

  /** Blokace publikovani dle design_doc: bez DAC7 profilu nelze publikovat. */
  public void requirePublishable(User seller) {
    SellerTaxProfile p = profiles.findById(seller.getId()).orElse(null);
    if (p == null || p.getFullName() == null || p.getIban() == null) {
      throw ApiException.unprocessable("DAC7_PROFILE_REQUIRED",
          "Pro publikaci doplňte daňový profil (jméno, datum narození, adresa, IBAN).");
    }
    if ("trader".equals(seller.getSellerType())) {
      String ico = p.getIco() == null ? "" : String.valueOf(crypto.decrypt(p.getIco()));
      if (ico.isBlank() || p.getIdDoc() == null || !p.isSelfCert()) {
        throw ApiException.unprocessable("DAC7_PROFILE_REQUIRED",
            "Obchodník musí mít IČO, doklad totožnosti a čestné prohlášení.");
      }
    }
  }

  // ---------- Ověřování obchodníků (DSA) ----------

  /** Stav ověření vlastního profilu — vidí ho prodejce, ne daňové údaje. */
  public Map<String, Object> myVerification() {
    User me = current.require();
    SellerTaxProfile p = profiles.findById(me.getId()).orElse(null);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("status", p == null ? SellerTaxProfile.STATUS_PENDING : p.getVerificationStatus());
    m.put("hasDocument", p != null && p.getIdDoc() != null && p.getIdDoc().length > 0);
    m.put("verifiedAt", p == null ? null : p.getVerifiedAt());
    m.put("note", p == null ? null : p.getVerificationNote());
    return m;
  }

  /** Obchodníci s nahráným dokladem, kteří čekají na rozhodnutí administrátora. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> pendingVerifications() {
    List<Map<String, Object>> out = new java.util.ArrayList<>();
    for (SellerTaxProfile p : profiles.findPendingTraderVerifications()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("userId", p.getUserId());
      m.put("status", p.getVerificationStatus());
      m.put("hasIco", hasText(p.getIco()));
      m.put("selfCert", p.isSelfCert());
      m.put("submittedAt", p.getUpdatedAt());
      m.put("verified", p.isVerified());
      users.findById(p.getUserId()).ifPresent(u -> m.put("displayName", u.getDisplayName()));
      out.add(m);
    }
    return out;
  }

  /**
   * Rozhodnutí administrátora o dokladu obchodníka. Po schválení zůstává doklad
   * platný i pro budoucí profilové změny; zamítnutí lze zdůvodnit v poznámce.
   */
  @Transactional
  public Map<String, Object> decideVerification(Long userId, boolean approve, String note) {
    User admin = current.require();
    User target = users.findById(userId)
        .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Uživatel nenalezen."));
    if (!"trader".equals(target.getSellerType()))
      throw ApiException.badRequest("NOT_A_TRADER", "Uživatel není obchodník.");
    SellerTaxProfile p = profiles.findById(userId).orElseThrow(() ->
        ApiException.unprocessable("NO_DOCUMENT", "Obchodník nemá nahrán žádný doklad k ověření."));
    if (p.getIdDoc() == null || p.getIdDoc().length == 0)
      throw ApiException.unprocessable("NO_DOCUMENT", "Obchodník nemá nahrán žádný doklad k ověření.");
    // Rozhodnutí lze vydat jen nad dokladem, který dosud nikdo neposoudil.
    if (!SellerTaxProfile.STATUS_PENDING.equals(p.getVerificationStatus()))
      throw ApiException.conflict("VERIFICATION_ALREADY_DECIDED",
          "O tomto dokladu už bylo rozhodnuto. Prodejce musí nahrát doklad znovu.");
    p.applyVerificationStatus(approve
        ? SellerTaxProfile.STATUS_VERIFIED : SellerTaxProfile.STATUS_REJECTED);
    p.setVerificationNote(note);
    p.setVerifiedAt(OffsetDateTime.now());
    p.setVerifiedBy(admin.getId());
    p.setUpdatedAt(OffsetDateTime.now());
    profiles.save(p);
    audit.log(admin.getId(), approve ? "TRADER_VERIFICATION_APPROVED" : "TRADER_VERIFICATION_REJECTED",
        "user", userId);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("userId", userId);
    m.put("status", p.getVerificationStatus());
    m.put("verified", p.isVerified());
    m.put("note", p.getVerificationNote());
    m.put("verifiedAt", p.getVerifiedAt());
    return m;
  }

  private boolean hasText(byte[] encrypted) {
    if (encrypted == null || encrypted.length == 0) return false;
    String v = crypto.decrypt(encrypted);
    return v != null && !v.isBlank();
  }
}
