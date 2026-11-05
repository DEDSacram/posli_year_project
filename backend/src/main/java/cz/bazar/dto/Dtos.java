package cz.bazar.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public class Dtos {
  public record RegisterRequest(
      @Email @NotBlank String email,
      @Size(min = 8, max = 128) @NotBlank String password,
      @NotBlank String displayName,
      @Pattern(regexp = "private|trader") @NotBlank String sellerType) {}

  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
  public record AuthResponse(Long id, String token, String refreshToken) {}
  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record SellerInfoRequest(
      String sellerType, String fullName, String dateOfBirth,
      String street, String city, String postalCode, String country,
      String taxId, String ico, String iban, Boolean selfCertification) {}

  public record Dac7Status(long salesCount, long totalAmountCzk, boolean isReportable) {}

  public record ListingCreate(
      @NotBlank String title, @NotBlank String description,
      @Min(1) int priceCzk, @NotBlank String category,
      @NotBlank String condition, @NotBlank String city,
      Double lat, Double lon) {}

  public record ListingUpdate(
      @Size(min = 1, max = 200) String title,
      @Size(min = 1, max = 10000) String description,
      @Min(1) Integer priceCzk,
      @Size(min = 1, max = 50) String category,
      @Size(min = 1, max = 50) String condition,
      @Size(min = 1, max = 120) String city,
      Double lat, Double lon) {}

  public record RightsInfo(boolean withdrawalApplies, String sellerType, String explanationCs) {}

  public record TransactionCreate(@NotNull Long listingId) {}
  public record WithdrawalRequest(@Size(max = 500) String reason) {}
  public record ReviewCreate(@Min(1) @Max(5) int rating, @NotBlank String text) {}
  public record ReviewResponseCreate(@NotBlank String text) {}

  /** Námitka prodejce proti hodnocení. */
  public record DisputeCreate(@NotBlank @Size(min = 10, max = 1000) String reason) {}
  /** Rozhodnutí administrátora: uphold = námitka oprávněná, reject = zamítnuta. */
  public record DisputeResolve(@Pattern(regexp = "uphold|reject") @NotBlank String decision,
                               @Size(max = 1000) String note) {}

  public record ConversationCreate(@NotNull Long listingId) {}
  public record MessageCreate(@NotNull Long conversationId, @Size(max = 8192) @NotBlank String bodyText) {}

  public record ReportCreate(
      @Pattern(regexp = "listing|review|chat") @NotBlank String targetType,
      @NotNull Long targetId, @NotBlank String reason) {}

  public record ConsentCreate(@NotBlank String purpose, boolean granted) {}

  public record LocationUpdate(@NotBlank String city, @NotBlank String postalCode) {}

  /** Rozhodnutí administrátora o dokladu obchodníka. */
  public record VerificationDecision(@NotBlank @Size(max = 500) String note) {}

  /** Blokace účtu administrátorem. */
  public record BlockRequest(@Size(max = 500) String reason) {}

  public record Page<T>(List<T> data, String nextCursor) {}
}
