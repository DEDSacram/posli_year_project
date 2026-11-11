package cz.bazar.service;

import cz.bazar.crypto.AesGcmService;
import cz.bazar.domain.SellerTaxProfile;
import cz.bazar.domain.User;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.SaleTransactionRepository;
import cz.bazar.repository.SellerTaxProfileRepository;
import cz.bazar.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Ověřování obchodníků (DSA): rozhodnutí administrátora a jeho důsledky. */
class SellerServiceTest {

  private SellerTaxProfileRepository profiles;
  private UserRepository users;
  private CurrentUserService current;
  private AuditService audit;
  private SellerService service;

  @BeforeEach
  void setUp() {
    profiles = mock(SellerTaxProfileRepository.class);
    users = mock(UserRepository.class);
    current = mock(CurrentUserService.class);
    audit = mock(AuditService.class);
    byte[] key = new byte[32];
    AesGcmService crypto = new AesGcmService(Base64.getEncoder().encodeToString(key));
    service = new SellerService(profiles, users, mock(SaleTransactionRepository.class), crypto, current, audit);
  }

  private User trader(Long id) {
    User u = new User();
    u.setId(id);
    u.setEmail("trader@test.cz");
    u.setDisplayName("Obchodník");
    u.setSellerType("trader");
    return u;
  }

  /** Profil obchodníka s dokladem, který čeká na rozhodnutí. */
  private SellerTaxProfile pendingProfile(Long userId) {
    SellerTaxProfile p = new SellerTaxProfile();
    p.setUserId(userId);
    p.setFullName("Obchodník".getBytes(StandardCharsets.UTF_8));
    p.setIban("iban".getBytes(StandardCharsets.UTF_8));
    p.setIco("12345678".getBytes(StandardCharsets.UTF_8));
    p.setSelfCert(true);
    p.setIdDoc("doklad".getBytes(StandardCharsets.UTF_8));
    p.applyVerificationStatus(SellerTaxProfile.STATUS_PENDING);
    return p;
  }

  @Test
  void adminApprovesTraderDocument() {
    when(current.require()).thenReturn(new User());
    when(users.findById(5L)).thenReturn(Optional.of(trader(5L)));
    SellerTaxProfile p = pendingProfile(5L);
    when(profiles.findById(5L)).thenReturn(Optional.of(p));

    var out = service.decideVerification(5L, true, "doklad v pořádku");

    assertThat(out).containsEntry("status", SellerTaxProfile.STATUS_VERIFIED)
        .containsEntry("verified", true);
    assertThat(p.isVerified()).isTrue();
    assertThat(p.getVerifiedAt()).isNotNull();
    verify(audit).log(any(), eq("TRADER_VERIFICATION_APPROVED"), eq("user"), eq(5L));
  }

  @Test
  void adminRejectsTraderDocument() {
    when(current.require()).thenReturn(new User());
    when(users.findById(5L)).thenReturn(Optional.of(trader(5L)));
    SellerTaxProfile p = pendingProfile(5L);
    when(profiles.findById(5L)).thenReturn(Optional.of(p));

    var out = service.decideVerification(5L, false, "nečitelné doklad");

    assertThat(out).containsEntry("status", SellerTaxProfile.STATUS_REJECTED)
        .containsEntry("verified", false);
    assertThat(p.getVerificationNote()).isEqualTo("nečitelné doklad");
    verify(audit).log(any(), eq("TRADER_VERIFICATION_REJECTED"), eq("user"), eq(5L));
  }

  @Test
  void cannotVerifyNonTrader() {
    when(current.require()).thenReturn(new User());
    User u = trader(5L);
    u.setSellerType("private");
    when(users.findById(5L)).thenReturn(Optional.of(u));

    assertThatThrownBy(() -> service.decideVerification(5L, true, null))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("NOT_A_TRADER"));
  }

  @Test
  void cannotVerifyTraderWithoutDocument() {
    when(current.require()).thenReturn(new User());
    when(users.findById(5L)).thenReturn(Optional.of(trader(5L)));
    SellerTaxProfile p = new SellerTaxProfile();
    p.setUserId(5L);
    when(profiles.findById(5L)).thenReturn(Optional.of(p));

    assertThatThrownBy(() -> service.decideVerification(5L, true, null))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("NO_DOCUMENT"));
  }

  @Test
  void cannotDecideTwice() {
    when(current.require()).thenReturn(new User());
    when(users.findById(5L)).thenReturn(Optional.of(trader(5L)));
    SellerTaxProfile p = pendingProfile(5L);
    p.applyVerificationStatus(SellerTaxProfile.STATUS_REJECTED);
    when(profiles.findById(5L)).thenReturn(Optional.of(p));

    assertThatThrownBy(() -> service.decideVerification(5L, true, null))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("VERIFICATION_ALREADY_DECIDED"));
  }

  @Test
  void reuploadingDocumentSendsTraderBackToPending() {
    when(current.require()).thenReturn(trader(5L));
    SellerTaxProfile p = new SellerTaxProfile();
    p.setUserId(5L);
    p.applyVerificationStatus(SellerTaxProfile.STATUS_VERIFIED);
    when(profiles.findById(5L)).thenReturn(Optional.of(p));

    service.uploadIdDocument(new MockMultipartFile("file", "doc.png", "image/png", "x".getBytes()));

    assertThat(p.getVerificationStatus()).isEqualTo(SellerTaxProfile.STATUS_PENDING);
    assertThat(p.isVerified()).isFalse();
    assertThat(p.getVerifiedAt()).isNull();
    assertThat(p.getVerificationNote()).isNull();
  }

  @Test
  void uploadRejectsNonImageMimeType() {
    when(current.require()).thenReturn(trader(5L));
    MockMultipartFile f = new MockMultipartFile("file", "x.exe", "application/x-msdownload", "x".getBytes());

    assertThatThrownBy(() -> service.uploadIdDocument(f))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_MIME"));
  }

  @Test
  void uploadRejectsEmptyFile() {
    when(current.require()).thenReturn(trader(5L));
    MockMultipartFile f = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);

    assertThatThrownBy(() -> service.uploadIdDocument(f))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("EMPTY_FILE"));
  }

  @Test
  void uploadRejectsFileOverFiveMegabytes() {
    when(current.require()).thenReturn(trader(5L));
    MockMultipartFile f = new MockMultipartFile("file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

    assertThatThrownBy(() -> service.uploadIdDocument(f))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("FILE_TOO_LARGE"));
  }

  @Test
  void uploadAcceptsPdf() {
    when(current.require()).thenReturn(trader(5L));
    SellerTaxProfile p = new SellerTaxProfile();
    p.setUserId(5L);
    p.setFullName("x".getBytes());
    p.setIban("x".getBytes());
    when(profiles.findById(5L)).thenReturn(Optional.of(p));

    service.uploadIdDocument(new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF".getBytes()));

    assertThat(p.getIdDoc()).isNotNull();
    verify(profiles).save(p);
  }

  @Test
  void verificationStatusOfOwnProfileIsVisible() {
    when(current.require()).thenReturn(trader(5L));
    SellerTaxProfile p = pendingProfile(5L);
    when(profiles.findById(5L)).thenReturn(Optional.of(p));

    var m = service.myVerification();

    assertThat(m).containsEntry("status", SellerTaxProfile.STATUS_PENDING)
        .containsEntry("hasDocument", true);
  }

  @Test
  void verificationStatusWithoutProfileIsPending() {
    when(current.require()).thenReturn(trader(5L));
    when(profiles.findById(5L)).thenReturn(Optional.empty());

    assertThat(service.myVerification()).containsEntry("status", SellerTaxProfile.STATUS_PENDING)
        .containsEntry("hasDocument", false);
  }
}