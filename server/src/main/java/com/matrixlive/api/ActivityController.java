package com.matrixlive.api;

import static com.matrixlive.api.ApiModels.*;

import com.matrixlive.service.ActivityService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for activity-scoped public and staff workflows. Authorization is applied by the security layer. */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {
  private final ActivityService service;

  public ActivityController(ActivityService service) { this.service = service; }

  @GetMapping
  public List<ActivityResponse> list() { return service.listActivities(); }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ActivityResponse create(@Valid @RequestBody CreateActivityRequest request) { return service.createActivity(request); }

  @GetMapping("/{activityId}")
  public ActivityResponse activity(@PathVariable UUID activityId) { return service.activity(activityId); }

  @PatchMapping("/{activityId}")
  public ActivityResponse update(@PathVariable UUID activityId, @Valid @RequestBody UpdateActivityRequest request) {
    return service.updateActivity(activityId, request);
  }

  @PostMapping("/{activityId}/status")
  public ActivityResponse changeStatus(@PathVariable UUID activityId,
      @Valid @RequestBody ChangeActivityStatusRequest request) {
    return service.changeActivityStatus(activityId, request);
  }

  @DeleteMapping("/{activityId}")
  public ActivityResponse terminate(@PathVariable UUID activityId) { return service.terminateActivity(activityId); }

  @GetMapping("/{activityId}/venues")
  public List<VenueResponse> venues(@PathVariable UUID activityId) { return service.listVenues(activityId); }

  @PostMapping("/{activityId}/venues")
  @ResponseStatus(HttpStatus.CREATED)
  public VenueResponse createVenue(@PathVariable UUID activityId, @Valid @RequestBody VenueRequest request) {
    return service.createVenue(activityId, request);
  }

  @PatchMapping("/{activityId}/venues/{venueId}")
  public VenueResponse updateVenue(@PathVariable UUID activityId, @PathVariable UUID venueId,
      @Valid @RequestBody UpdateVenueRequest request) {
    return service.updateVenue(activityId, venueId, request);
  }

  @DeleteMapping("/{activityId}/venues/{venueId}")
  public ResponseEntity<Void> deleteVenue(@PathVariable UUID activityId, @PathVariable UUID venueId) {
    service.deleteVenue(activityId, venueId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{activityId}/registration-fields")
  public List<RegistrationFieldResponse> registrationFields(@PathVariable UUID activityId) {
    return service.registrationFields(activityId);
  }

  @PostMapping("/{activityId}/registration-fields")
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationFieldResponse createRegistrationField(@PathVariable UUID activityId,
      @Valid @RequestBody RegistrationFieldRequest request) {
    return service.createRegistrationField(activityId, request);
  }

  @PatchMapping("/{activityId}/registration-fields/{fieldId}")
  public RegistrationFieldResponse updateRegistrationField(@PathVariable UUID activityId, @PathVariable UUID fieldId,
      @Valid @RequestBody UpdateRegistrationFieldRequest request) {
    return service.updateRegistrationField(activityId, fieldId, request);
  }

  @DeleteMapping("/{activityId}/registration-fields/{fieldId}")
  public ResponseEntity<Void> deleteRegistrationField(@PathVariable UUID activityId, @PathVariable UUID fieldId) {
    service.deleteRegistrationField(activityId, fieldId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{activityId}/participants")
  public List<ParticipantResponse> participants(@PathVariable UUID activityId,
      @RequestParam(required = false) String venue, @RequestParam(required = false) String query) {
    return service.listParticipants(activityId, venue, query);
  }

  @GetMapping("/{activityId}/venues/{venue}/participants")
  public List<ParticipantResponse> venueParticipants(@PathVariable UUID activityId, @PathVariable String venue) {
    return service.listParticipants(activityId, venue, null);
  }

  @PostMapping("/{activityId}/venues/{venue}/registrations")
  @ResponseStatus(HttpStatus.CREATED)
  public ParticipantResponse register(@PathVariable UUID activityId, @PathVariable String venue,
      @Valid @RequestBody RegisterParticipantRequest request) {
    return service.register(activityId, venue, request);
  }

  @GetMapping("/{activityId}/participants/{participantId}")
  public ParticipantDetailResponse participant(@PathVariable UUID activityId, @PathVariable UUID participantId) {
    return service.participant(activityId, participantId);
  }

  @PatchMapping("/{activityId}/participants/{participantId}")
  public ParticipantDetailResponse updateParticipant(@PathVariable UUID activityId, @PathVariable UUID participantId,
      @Valid @RequestBody UpdateParticipantRequest request) {
    return service.updateParticipant(activityId, participantId, request);
  }

  @GetMapping("/{activityId}/questions")
  public List<QuestionResponse> questions(@PathVariable UUID activityId) { return service.listQuestions(activityId); }

  @GetMapping("/{activityId}/questions/admin")
  public List<QuestionAdminResponse> administrationQuestions(@PathVariable UUID activityId) {
    return service.listQuestionAdministration(activityId);
  }

  @PostMapping("/{activityId}/questions")
  @ResponseStatus(HttpStatus.CREATED)
  public QuestionAdminResponse createQuestion(@PathVariable UUID activityId,
      @Valid @RequestBody QuestionWriteRequest request) {
    return service.createQuestion(activityId, request);
  }

  @PutMapping("/{activityId}/questions/{questionId}")
  public QuestionAdminResponse updateQuestion(@PathVariable UUID activityId, @PathVariable UUID questionId,
      @Valid @RequestBody QuestionWriteRequest request) {
    return service.updateQuestion(activityId, questionId, request);
  }

  @DeleteMapping("/{activityId}/questions/{questionId}")
  public ResponseEntity<Void> deleteQuestion(@PathVariable UUID activityId, @PathVariable UUID questionId) {
    service.deleteQuestion(activityId, questionId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{activityId}/answers")
  public AnswerResult answer(@PathVariable UUID activityId, @Valid @RequestBody SubmitAnswerRequest request) {
    return service.submitAnswer(activityId, request);
  }

  @GetMapping("/{activityId}/participants/{participantId}/submissions")
  public List<SubmissionResponse> submissions(@PathVariable UUID activityId, @PathVariable UUID participantId) {
    return service.submissions(activityId, participantId);
  }

  @PostMapping("/{activityId}/submissions/{submissionId}/grade")
  public SubmissionResponse gradeSubmission(@PathVariable UUID activityId, @PathVariable UUID submissionId,
      @Valid @RequestBody GradeSubmissionRequest request) {
    return service.gradeSubmission(activityId, submissionId, request);
  }

  @PostMapping("/{activityId}/scores/adjustments")
  public ScoreLedgerResponse adjustScore(@PathVariable UUID activityId,
      @Valid @RequestBody ManualScoreRequest request) {
    return service.adjustScore(activityId, request);
  }

  @GetMapping("/{activityId}/participants/{participantId}/score-ledger")
  public List<ScoreLedgerResponse> scoreLedger(@PathVariable UUID activityId, @PathVariable UUID participantId) {
    return service.scoreLedger(activityId, participantId);
  }

  @GetMapping("/{activityId}/scoreboard")
  public List<ScoreboardEntry> scoreboard(@PathVariable UUID activityId) { return service.scoreboard(activityId); }

  @GetMapping("/{activityId}/control")
  public ControlState controlState(@PathVariable UUID activityId) { return service.controlState(activityId); }

  @PostMapping("/{activityId}/control")
  public ControlState control(@PathVariable UUID activityId, @Valid @RequestBody ControlRequest request) {
    return service.control(activityId, request);
  }

  @GetMapping("/{activityId}/prize-pools")
  public List<PrizePoolResponse> prizePools(@PathVariable UUID activityId) { return service.listPrizePools(activityId); }

  @PostMapping("/{activityId}/prize-pools")
  @ResponseStatus(HttpStatus.CREATED)
  public PrizePoolResponse createPrizePool(@PathVariable UUID activityId,
      @Valid @RequestBody PrizePoolRequest request) {
    return service.createPrizePool(activityId, request);
  }

  @PatchMapping("/{activityId}/prize-pools/{poolId}")
  public PrizePoolResponse updatePrizePool(@PathVariable UUID activityId, @PathVariable UUID poolId,
      @Valid @RequestBody UpdatePrizePoolRequest request) {
    return service.updatePrizePool(activityId, poolId, request);
  }

  @DeleteMapping("/{activityId}/prize-pools/{poolId}")
  public ResponseEntity<Void> deletePrizePool(@PathVariable UUID activityId, @PathVariable UUID poolId) {
    service.deletePrizePool(activityId, poolId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{activityId}/awards")
  public List<AwardResponse> awards(@PathVariable UUID activityId, @RequestParam UUID participantId) {
    return service.awards(activityId, participantId);
  }

  @GetMapping("/{activityId}/awards/admin")
  public List<AwardDetailResponse> allAwards(@PathVariable UUID activityId,
      @RequestParam(required = false) String status) {
    return service.allAwards(activityId, status);
  }

  @PostMapping("/{activityId}/awards")
  @ResponseStatus(HttpStatus.CREATED)
  public AwardDetailResponse issueAward(@PathVariable UUID activityId, @Valid @RequestBody IssueAwardRequest request) {
    return service.issueAward(activityId, request);
  }

  @PostMapping("/{activityId}/prize-pools/{poolId}/ranking-awards")
  public List<AwardDetailResponse> issueRankingAwards(@PathVariable UUID activityId, @PathVariable UUID poolId) {
    return service.issueRankingAwards(activityId, poolId);
  }

  @PostMapping("/{activityId}/participants/{participantId}/lottery-chances")
  public LotteryChanceResponse grantLotteryChances(@PathVariable UUID activityId, @PathVariable UUID participantId,
      @Valid @RequestBody GrantLotteryChancesRequest request) {
    return service.grantLotteryChances(activityId, participantId, request);
  }

  @GetMapping("/{activityId}/participants/{participantId}/lottery-chances")
  public LotteryChanceResponse lotteryChance(@PathVariable UUID activityId, @PathVariable UUID participantId) {
    return service.lotteryChance(activityId, participantId);
  }

  @PostMapping("/{activityId}/draws")
  public DrawResult draw(@PathVariable UUID activityId, @Valid @RequestBody DrawRequest request) {
    return service.draw(activityId, request);
  }

  @PostMapping("/{activityId}/awards/{awardId}/redeem")
  public AwardResponse redeem(@PathVariable UUID activityId, @PathVariable UUID awardId,
      @RequestBody(required = false) RedeemAwardRequest request) {
    return service.redeem(activityId, awardId, request == null ? "system" : request.operator());
  }

  @PostMapping("/{activityId}/awards/{awardId}/reverse-redemption")
  public AwardDetailResponse reverseRedemption(@PathVariable UUID activityId, @PathVariable UUID awardId) {
    return service.reverseRedemption(activityId, awardId);
  }

  @PostMapping("/{activityId}/awards/{awardId}/void")
  public AwardDetailResponse voidAward(@PathVariable UUID activityId, @PathVariable UUID awardId,
      @Valid @RequestBody VoidAwardRequest request) {
    return service.voidAward(activityId, awardId, request);
  }
}
