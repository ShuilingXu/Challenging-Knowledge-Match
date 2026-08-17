package com.matrixlive.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ApiModels {
  private ApiModels() { }

  public record CreateActivityRequest(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 80) String city,
      Instant startsAt,
      Instant endsAt,
      @Size(max = 5000) String description,
      @Size(max = 160) String clientDisplayName,
      @Size(max = 16) String clientThemeColor,
      @Size(max = 1024) String clientHeroImageUrl,
      @Size(max = 1024) String clientBackgroundImageUrl) {
    public CreateActivityRequest(String name, String city, Instant startsAt) {
      this(name, city, startsAt, null, null, null, null, null, null);
    }
  }

  public record UpdateActivityRequest(
      @Size(max = 160) String name,
      @Size(max = 80) String city,
      Instant startsAt,
      Instant endsAt,
      @Size(max = 5000) String description,
      @Size(max = 160) String clientDisplayName,
      @Size(max = 16) String clientThemeColor,
      @Size(max = 1024) String clientHeroImageUrl,
      @Size(max = 1024) String clientBackgroundImageUrl) { }

  public record ChangeActivityStatusRequest(@NotBlank String status) { }

  public record ActivityResponse(UUID id, String name, String city, String status, Instant startsAt,
      Instant endsAt, String description, String clientDisplayName, String clientThemeColor,
      String clientHeroImageUrl, String clientBackgroundImageUrl, Instant createdAt, Instant updatedAt) { }

  /** Settings safe to expose on participant-facing routes. */
  public record SiteSettingsResponse(String domain, String siteName, String logoUrl, String footerCode) { }

  /** Object-storage configuration is only returned from the system-admin endpoint. */
  public record AdminSiteSettingsResponse(String domain, String siteName, String logoUrl, String footerCode,
      boolean storageEnabled, String storageEndpoint, String storageRegion, String storageBucket,
      String storageAccessKey, boolean storageSecretConfigured, boolean storageSessionTokenConfigured,
      String storagePublicBaseUrl, String storageAddressingStyle) { }

  public record UpdateSiteSettingsRequest(
      @Size(max = 255) String domain,
      @Size(max = 160) String siteName,
      @Size(max = 1024) String logoUrl,
      @Size(max = 5000) String footerCode,
      Boolean storageEnabled,
      @Size(max = 1024) String storageEndpoint,
      @Size(max = 80) String storageRegion,
      @Size(max = 160) String storageBucket,
      @Size(max = 512) String storageAccessKey,
      @Size(max = 2048) String storageSecretKey,
      @Size(max = 4096) String storageSessionToken,
      @Size(max = 1024) String storagePublicBaseUrl,
      @Size(max = 16) String storageAddressingStyle,
      Boolean clearStorageCredentials) { }

  public record VenueRequest(@NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 160) String name,
      @Min(1) Integer capacity, Boolean enabled) { }

  public record UpdateVenueRequest(@Size(max = 160) String name, @Min(1) Integer capacity, Boolean enabled) { }

  public record VenueResponse(UUID id, String code, String name, boolean enabled, Integer capacity,
      Instant createdAt) { }

  public record RegistrationFieldRequest(
      @NotBlank @Size(max = 80) String fieldKey,
      @NotBlank @Size(max = 160) String label,
      @NotBlank String type,
      List<@NotBlank String> options,
      boolean required,
      @Min(0) int displayOrder) { }

  public record UpdateRegistrationFieldRequest(
      @Size(max = 160) String label,
      String type,
      List<String> options,
      Boolean required,
      @Min(0) Integer displayOrder,
      Boolean enabled) { }

  public record RegistrationFieldResponse(UUID id, String fieldKey, String label, String type, List<String> options,
      boolean required, boolean enabled, int displayOrder) { }

  public record RegisterParticipantRequest(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 160) String contact,
      @Size(max = 180) String organization,
      Map<String, String> customFields) {
    public RegisterParticipantRequest(String name, String contact, String organization) {
      this(name, contact, organization, Map.of());
    }
  }

  public record UpdateParticipantRequest(
      @Size(max = 120) String name,
      @Size(max = 160) String contact,
      @Size(max = 180) String organization,
      Map<String, String> customFields,
      String venue,
      String status) { }

  public record ParticipantResponse(UUID id, String name, String contact, String venue, String organization, int score,
      String status, Instant registeredAt, Map<String, String> customFields) { }

  public record ParticipantDetailResponse(UUID id, String name, String contact, String venue, String organization,
      int score, String status, Instant registeredAt, Instant lastScoreAt, Map<String, String> customFields,
      List<AwardResponse> awards, LotteryChanceResponse lotteryChance) { }

  public record QuestionWriteRequest(
      @NotBlank String type,
      @NotBlank @Size(max = 8000) String title,
      List<@NotBlank String> options,
      Set<@NotBlank String> answers,
      @Min(1) @Max(100000) Integer fullScore,
      @Min(0) Integer displayOrder,
      @Size(max = 2048) String mediaUrl,
      @Min(0) @Max(100) Integer partialCreditPercent,
      Boolean enabled) { }

  public record QuestionResponse(UUID id, String type, String title, List<String> options, int fullScore,
      int displayOrder, String mediaUrl, boolean enabled) { }

  public record QuestionAdminResponse(UUID id, String type, String title, List<String> options,
      List<String> answers, int fullScore, int displayOrder, String mediaUrl, int partialCreditPercent,
      boolean enabled) { }

  public record SubmitAnswerRequest(@NotNull UUID participantId, @NotNull UUID questionId, Set<String> answers,
      @NotBlank @Size(max = 160) String idempotencyKey) { }

  public record AnswerResult(UUID submissionId, int awardedPoints, int totalScore, boolean replayed,
      String status, String feedback, int responseRank) {
    public AnswerResult(UUID submissionId, int awardedPoints, int totalScore, boolean replayed) {
      this(submissionId, awardedPoints, totalScore, replayed, "SCORED", null, 0);
    }
  }

  public record GradeSubmissionRequest(@Min(0) @NotNull Integer awardedPoints, @Size(max = 1000) String feedback) { }

  public record SubmissionResponse(UUID id, UUID participantId, UUID questionId, List<String> answers,
      int awardedPoints, String status, String feedback, Instant submittedAt, Instant gradedAt) { }

  public record ScoreLedgerResponse(UUID id, UUID participantId, UUID questionId, UUID submissionId, int points,
      String entryType, String note, Instant createdAt) { }

  public record ManualScoreRequest(@NotNull UUID participantId, @NotNull Integer points, @Size(max = 400) String note) { }

  public record ControlRequest(@NotBlank String stage, UUID questionId, Integer seconds) { }
  public record ControlState(String stage, UUID questionId, int seconds, Instant updatedAt) { }
  public record ScoreboardEntry(int rank, UUID participantId, String name, String venue, int score) { }
  public record QuestionSubmissionEntry(UUID participantId, String participantName, String venue, List<String> answers,
      int awardedPoints, String status, int responseRank, Instant submittedAt) { }
  public record QuestionResponseStats(UUID questionId, int eligibleParticipantCount, int submittedCount,
      int unansweredCount, int pendingReviewCount, int correctCount, int partialCount, int incorrectCount,
      List<QuestionSubmissionEntry> submissions) { }

  public record PrizePoolRequest(
      @NotBlank @Size(max = 80) String code,
      @NotBlank @Size(max = 180) String name,
      @NotBlank String purpose,
      @NotBlank String deliveryType,
      @Size(max = 5000) String description,
      @Size(max = 1024) String redemptionUrl,
      @NotNull @Min(1) Integer totalQuantity,
      @Min(0) Integer minScore,
      @Min(1) Integer drawWeight,
      @Min(1) Integer rankFrom,
      @Min(1) Integer rankTo,
      Boolean enabled) { }

  public record UpdatePrizePoolRequest(
      @Size(max = 180) String name,
      String purpose,
      String deliveryType,
      @Size(max = 5000) String description,
      @Size(max = 1024) String redemptionUrl,
      @Min(1) Integer totalQuantity,
      @Min(0) Integer minScore,
      @Min(1) Integer drawWeight,
      @Min(1) Integer rankFrom,
      @Min(1) Integer rankTo,
      Boolean enabled) { }

  public record PrizePoolResponse(UUID id, String code, String name, String purpose, String deliveryType,
      String description, String redemptionUrl, int totalQuantity, int claimedQuantity, int remainingQuantity,
      int minScore, int drawWeight, Integer rankFrom, Integer rankTo, boolean enabled) { }

  public record AwardResponse(UUID id, String prizeName, String deliveryType, String status, String redemptionCode) { }

  public record AwardDetailResponse(UUID id, UUID participantId, UUID prizePoolId, String prizeName,
      String deliveryType, String status, String redemptionCode, String redemptionUrl, String fulfillmentNote,
      Instant awardedAt, Instant redeemedAt, String redeemedBy, String participantName, String participantContact) { }

  public record IssueAwardRequest(@NotNull UUID participantId, @NotNull UUID prizePoolId,
      @Size(max = 600) String fulfillmentNote) { }

  public record RedeemAwardRequest(@Size(max = 120) String operator) { }
  public record VoidAwardRequest(@Size(max = 600) String note) { }
  public record GrantLotteryChancesRequest(@NotNull @Min(1) Integer draws, @Size(max = 200) String reason) { }
  public record LotteryChanceResponse(UUID participantId, int remainingDraws, int grantedDraws,
      String lastGrantReason, Instant updatedAt) { }

  public record DrawRequest(@NotNull UUID participantId, UUID prizePoolId,
      @Size(max = 80) String venue, @NotBlank @Size(max = 160) String idempotencyKey) {
    public DrawRequest(UUID participantId, UUID prizePoolId, String idempotencyKey) {
      this(participantId, prizePoolId, null, idempotencyKey);
    }

    public DrawRequest(UUID participantId, String idempotencyKey) {
      this(participantId, null, null, idempotencyKey);
    }
  }

  public record DrawResult(UUID awardId, String prizeName, String deliveryType, String status,
      String redemptionCode, boolean replayed) { }
}
