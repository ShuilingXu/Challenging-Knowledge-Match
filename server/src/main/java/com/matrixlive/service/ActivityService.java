package com.matrixlive.service;

import static com.matrixlive.api.ApiModels.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matrixlive.domain.Activity;
import com.matrixlive.domain.AnswerSubmission;
import com.matrixlive.domain.LotteryChance;
import com.matrixlive.domain.LotteryDraw;
import com.matrixlive.domain.Participant;
import com.matrixlive.domain.PrizeAward;
import com.matrixlive.domain.PrizePool;
import com.matrixlive.domain.Question;
import com.matrixlive.domain.QuestionSet;
import com.matrixlive.domain.QuestionSetItem;
import com.matrixlive.domain.RegistrationField;
import com.matrixlive.domain.ScoreLedger;
import com.matrixlive.domain.Venue;
import com.matrixlive.repository.ActivityRepository;
import com.matrixlive.repository.AnswerSubmissionRepository;
import com.matrixlive.repository.LotteryChanceRepository;
import com.matrixlive.repository.LotteryDrawRepository;
import com.matrixlive.repository.ParticipantRepository;
import com.matrixlive.repository.PrizeAwardRepository;
import com.matrixlive.repository.PrizePoolRepository;
import com.matrixlive.repository.QuestionRepository;
import com.matrixlive.repository.QuestionSetItemRepository;
import com.matrixlive.repository.QuestionSetRepository;
import com.matrixlive.repository.RegistrationFieldRepository;
import com.matrixlive.repository.ScoreLedgerRepository;
import com.matrixlive.repository.VenueRepository;
import com.matrixlive.realtime.RealtimeEventBus;
import com.matrixlive.screen.ScreenDisplayMode;
import com.matrixlive.screen.ScreenService;
import com.matrixlive.security.AuthenticatedPrincipal;
import com.matrixlive.security.auth.ActivityMembership;
import com.matrixlive.security.auth.ActivityMembershipRepository;
import com.matrixlive.security.auth.UserRole;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction boundary for all activity-scoped writes. The service never accepts an entity id without
 * checking its activity id first, which prevents cross-event reads and mutations from public APIs.
 */
@Service
public class ActivityService {
  private static final Set<String> ACTIVITY_STATUSES = Set.of(
      "DRAFT", "REGISTRATION_OPEN", "LIVE", "PAUSED", "FINISHED", "CANCELLED");
  private static final Set<String> ACTIVITY_TYPES = Set.of("EVENT", "QUIZ", "LOTTERY", "OTHER");
  private static final Set<String> QUESTION_TYPES = Set.of("SINGLE", "MULTIPLE", "TEXT");
  private static final Set<String> TEXT_MATCH_MODES = Set.of("FUZZY", "REGEX", "MANUAL");
  private static final Set<String> FIELD_TYPES = Set.of(
      "TEXT", "TEXTAREA", "EMAIL", "PHONE", "NUMBER", "SELECT", "RADIO", "CHECKBOX");
  private static final Set<String> POOL_PURPOSES = Set.of("LOTTERY", "RANKING", "MANUAL");
  private static final Set<String> DELIVERY_TYPES = Set.of("PHYSICAL", "DIGITAL", "VOUCHER");
  private static final Set<String> RESERVED_FIELD_KEYS = Set.of("name", "contact", "organization", "venue");
  private static final SecureRandom LOTTERY_RANDOM = new SecureRandom();

  private final ActivityRepository activities;
  private final ActivityMembershipRepository memberships;
  private final VenueRepository venues;
  private final RegistrationFieldRepository registrationFields;
  private final ParticipantRepository participants;
  private final QuestionRepository questions;
  private final QuestionSetRepository questionSets;
  private final QuestionSetItemRepository questionSetItems;
  private final AnswerSubmissionRepository submissions;
  private final ScoreLedgerRepository scoreLedgers;
  private final PrizePoolRepository prizePools;
  private final PrizeAwardRepository awards;
  private final LotteryDrawRepository lotteryDraws;
  private final LotteryChanceRepository lotteryChances;
  private final RealtimeEventBus realtime;
  private final ObjectMapper objectMapper;
  private final ScreenService screens;
  private final Map<UUID, ControlState> controls = new ConcurrentHashMap<>();

  public ActivityService(ActivityRepository activities, ActivityMembershipRepository memberships, VenueRepository venues,
      RegistrationFieldRepository registrationFields, ParticipantRepository participants,
      QuestionRepository questions, QuestionSetRepository questionSets, QuestionSetItemRepository questionSetItems,
      AnswerSubmissionRepository submissions, ScoreLedgerRepository scoreLedgers,
      PrizePoolRepository prizePools, PrizeAwardRepository awards, LotteryDrawRepository lotteryDraws,
      LotteryChanceRepository lotteryChances, RealtimeEventBus realtime, ObjectMapper objectMapper, ScreenService screens) {
    this.activities = activities;
    this.memberships = memberships;
    this.venues = venues;
    this.registrationFields = registrationFields;
    this.participants = participants;
    this.questions = questions;
    this.questionSets = questionSets;
    this.questionSetItems = questionSetItems;
    this.submissions = submissions;
    this.scoreLedgers = scoreLedgers;
    this.prizePools = prizePools;
    this.awards = awards;
    this.lotteryDraws = lotteryDraws;
    this.lotteryChances = lotteryChances;
    this.realtime = realtime;
    this.objectMapper = objectMapper;
    this.screens = screens;
  }

  @Transactional(readOnly = true)
  public List<ActivityResponse> listActivities() {
    return activities.findAll().stream()
        .sorted(Comparator.comparing(Activity::getStartsAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .map(this::toActivity).toList();
  }

  @Transactional(readOnly = true)
  public ActivityResponse activity(UUID activityId) {
    return toActivity(requireActivity(activityId));
  }

  @Transactional
  public ActivityResponse createActivity(CreateActivityRequest request) {
    validateTimeRange(request.startsAt(), request.endsAt());
    Activity activity = new Activity(cleanRequired(request.name(), "Activity name"),
        cleanRequired(request.city(), "City"), "DRAFT", request.startsAt() == null ? Instant.now() : request.startsAt(),
        request.endsAt(), cleanOptional(request.description()));
    activity.updateClientBrand(cleanOptional(request.clientDisplayName()), normalizeThemeColor(request.clientThemeColor()),
        cleanOptional(request.clientHeroImageUrl()), cleanOptional(request.clientBackgroundImageUrl()));
    configureActivityHierarchy(activity, request.parentActivityId(), request.activityType());
    activities.save(activity);
    createInitiatorMembership(activity.getId());
    return toActivity(activity);
  }

  @Transactional
  public ActivityResponse updateActivity(UUID activityId, UpdateActivityRequest request) {
    Activity activity = requireActivity(activityId);
    Instant startsAt = request.startsAt() == null ? activity.getStartsAt() : request.startsAt();
    Instant endsAt = request.endsAt() == null ? activity.getEndsAt() : request.endsAt();
    validateTimeRange(startsAt, endsAt);
    activity.update(cleanOptional(request.name()), cleanOptional(request.city()), startsAt, endsAt,
        request.description() == null ? activity.getDescription() : cleanOptional(request.description()));
    activity.updateClientBrand(
        request.clientDisplayName() == null ? activity.getClientDisplayName() : cleanOptional(request.clientDisplayName()),
        request.clientThemeColor() == null ? activity.getClientThemeColor() : normalizeThemeColor(request.clientThemeColor()),
        request.clientHeroImageUrl() == null ? activity.getClientHeroImageUrl() : cleanOptional(request.clientHeroImageUrl()),
        request.clientBackgroundImageUrl() == null ? activity.getClientBackgroundImageUrl() : cleanOptional(request.clientBackgroundImageUrl()));
    configureActivityHierarchy(activity,
        request.parentActivityId() == null ? activity.getParentActivityId() : request.parentActivityId(),
        request.activityType() == null ? activity.getActivityType() : request.activityType());
    return toActivity(activity);
  }

  @Transactional
  public ActivityResponse changeActivityStatus(UUID activityId, ChangeActivityStatusRequest request) {
    Activity activity = requireActivity(activityId);
    String next = normalizeEnum(request.status(), ACTIVITY_STATUSES, "activity status");
    ensureStatusTransition(activity.getStatus(), next);
    activity.changeStatus(next);
    broadcast(activityId, "activity.status_changed", toActivity(activity));
    return toActivity(activity);
  }

  /** Logical deletion preserves score and award audit records. */
  @Transactional
  public ActivityResponse terminateActivity(UUID activityId) {
    Activity activity = requireActivity(activityId);
    if (!"CANCELLED".equals(activity.getStatus())) {
      activity.changeStatus("CANCELLED");
      broadcast(activityId, "activity.status_changed", toActivity(activity));
    }
    return toActivity(activity);
  }

  @Transactional(readOnly = true)
  public List<VenueResponse> listVenues(UUID activityId) {
    requireActivity(activityId);
    return venues.findByActivityIdOrderByNameAsc(activityId).stream().map(this::toVenue).toList();
  }

  @Transactional
  public VenueResponse createVenue(UUID activityId, VenueRequest request) {
    requireActivity(activityId);
    String code = normalizeVenue(request.code());
    if (venues.findByActivityIdAndCode(activityId, code).isPresent()) {
      throw conflict("Venue code already exists in this activity");
    }
    Venue venue = venues.save(new Venue(activityId, code, cleanRequired(request.name(), "Venue name"), request.capacity()));
    venue.update(null, null, request.enabled());
    return toVenue(venue);
  }

  @Transactional
  public VenueResponse updateVenue(UUID activityId, UUID venueId, UpdateVenueRequest request) {
    Venue venue = requireVenue(activityId, venueId);
    venue.update(cleanOptional(request.name()), request.capacity(), request.enabled());
    return toVenue(venue);
  }

  @Transactional
  public void deleteVenue(UUID activityId, UUID venueId) {
    Venue venue = requireVenue(activityId, venueId);
    if (participants.countByActivityIdAndVenue(activityId, venue.getCode()) > 0) {
      throw conflict("Venue with participant records cannot be deleted; disable it instead");
    }
    venues.delete(venue);
  }

  @Transactional(readOnly = true)
  public List<RegistrationFieldResponse> registrationFields(UUID activityId) {
    requireActivity(activityId);
    return registrationFields.findByActivityIdOrderByDisplayOrderAsc(activityId).stream()
        .map(this::toRegistrationField).toList();
  }

  @Transactional
  public RegistrationFieldResponse createRegistrationField(UUID activityId, RegistrationFieldRequest request) {
    requireActivity(activityId);
    String fieldKey = normalizeFieldKey(request.fieldKey());
    if (RESERVED_FIELD_KEYS.contains(fieldKey)) throw badRequest("Registration field key is reserved");
    if (registrationFields.findByActivityIdAndFieldKey(activityId, fieldKey).isPresent()) {
      throw conflict("Registration field key already exists");
    }
    FieldValues values = fieldValues(request.type(), request.options());
    RegistrationField field = registrationFields.save(new RegistrationField(activityId, fieldKey,
        cleanRequired(request.label(), "Field label"), values.type(), joinPipe(values.options()), request.required(),
        request.displayOrder()));
    return toRegistrationField(field);
  }

  @Transactional
  public RegistrationFieldResponse updateRegistrationField(UUID activityId, UUID fieldId,
      UpdateRegistrationFieldRequest request) {
    RegistrationField field = requireRegistrationField(activityId, fieldId);
    FieldValues values = fieldValues(request.type() == null ? field.getType() : request.type(),
        request.options() == null ? splitPipe(field.getOptions()) : request.options());
    field.update(cleanOptional(request.label()), values.type(), joinPipe(values.options()), request.required(),
        request.displayOrder(), request.enabled());
    return toRegistrationField(field);
  }

  @Transactional
  public void deleteRegistrationField(UUID activityId, UUID fieldId) {
    RegistrationField field = requireRegistrationField(activityId, fieldId);
    registrationFields.delete(field);
  }

  @Transactional
  public ParticipantResponse register(UUID activityId, String venueCode, RegisterParticipantRequest request) {
    Activity activity = requireActivity(activityId);
    ensureRegistrationAllowed(activity);
    String venue = normalizeVenue(venueCode);
    Venue venueEntity = venues.findByActivityIdAndCode(activityId, venue)
        .orElseThrow(() -> badRequest("Venue is not configured in this activity"));
    if (!venueEntity.isEnabled()) throw conflict("Venue is disabled");
    if (venueEntity.getCapacity() != null
        && participants.countByActivityIdAndVenue(activityId, venue) >= venueEntity.getCapacity()) {
      throw conflict("Venue registration capacity has been reached");
    }
    String contact = normalizeContact(request.contact());
    participants.findByActivityIdAndVenueAndContact(activityId, venue, contact).ifPresent(existing -> {
      throw conflict("This contact is already registered in the selected venue");
    });
    Map<String, String> customFields = validateCustomFields(activityId, request.customFields());
    Participant participant = participants.save(new Participant(activityId, venue, contact,
        cleanRequired(request.name(), "Participant name"), cleanOptional(request.organization()),
        writeFields(customFields)));
    broadcast(activityId, "participant.registered", toParticipant(participant));
    return toParticipant(participant);
  }

  @Transactional(readOnly = true)
  public List<ParticipantResponse> listParticipants(UUID activityId, String venue) {
    return listParticipants(activityId, venue, null);
  }

  @Transactional(readOnly = true)
  public List<ParticipantResponse> listParticipants(UUID activityId, String venue, String query) {
    requireActivity(activityId);
    List<Participant> found = venue == null || venue.isBlank()
        ? participants.findByActivityId(activityId)
        : participants.findByActivityIdAndVenue(activityId, normalizeVenue(venue));
    String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    return found.stream().filter(participant -> term.isBlank()
            || participant.getName().toLowerCase(Locale.ROOT).contains(term)
            || participant.getContact().toLowerCase(Locale.ROOT).contains(term)
            || participant.getId().toString().toLowerCase(Locale.ROOT).contains(term))
        .sorted(participantOrder()).map(this::toParticipant).toList();
  }

  @Transactional(readOnly = true)
  public ParticipantDetailResponse participant(UUID activityId, UUID participantId) {
    return toParticipantDetail(requireParticipant(activityId, participantId));
  }

  @Transactional
  public ParticipantDetailResponse updateParticipant(UUID activityId, UUID participantId,
      UpdateParticipantRequest request) {
    Participant participant = requireParticipantForUpdate(activityId, participantId);
    String venue = request.venue() == null ? participant.getVenue() : normalizeVenue(request.venue());
    String contact = request.contact() == null ? participant.getContact() : normalizeContact(request.contact());
    if (!venue.equals(participant.getVenue()) || !contact.equals(participant.getContact())) {
      participants.findByActivityIdAndVenueAndContact(activityId, venue, contact)
          .filter(existing -> !existing.getId().equals(participantId))
          .ifPresent(existing -> { throw conflict("This contact is already registered in the selected venue"); });
    }
    if (!venue.equals(participant.getVenue())) {
      Venue venueEntity = venues.findByActivityIdAndCode(activityId, venue)
          .orElseThrow(() -> notFound("Venue does not exist in this activity"));
      if (!venueEntity.isEnabled()) throw conflict("Venue is disabled");
      if (venueEntity.getCapacity() != null
          && participants.countByActivityIdAndVenue(activityId, venue) >= venueEntity.getCapacity()) {
        throw conflict("Venue registration capacity has been reached");
      }
    }
    Map<String, String> fields = request.customFields() == null ? readFields(participant.getRegistrationData())
        : validateCustomFields(activityId, request.customFields());
    participant.updateProfile(cleanOptional(request.name()), contact, cleanOptional(request.organization()), writeFields(fields), venue);
    if (request.status() != null) {
      String status = normalizeEnum(request.status(), Set.of("ACTIVE", "DISABLED"), "participant status");
      participant.changeStatus(status);
    }
    return toParticipantDetail(participant);
  }

  @Transactional(readOnly = true)
  public List<QuestionResponse> listQuestions(UUID activityId) {
    requireActivity(activityId);
    return orderedQuestionsForActivity(activityId).stream().map(this::toQuestion).toList();
  }

  @Transactional(readOnly = true)
  public List<QuestionAdminResponse> listQuestionAdministration(UUID activityId) {
    requireActivity(activityId);
    return questions.findByActivityIdOrderByDisplayOrderAsc(activityId).stream().map(this::toQuestionAdmin).toList();
  }

  @Transactional(readOnly = true)
  public List<QuestionControlResponse> listQuestionControl(UUID activityId) {
    requireActivity(activityId);
    return orderedQuestionsForActivity(activityId).stream().map(this::toQuestionControl).toList();
  }

  @Transactional(readOnly = true)
  public List<QuestionSetResponse> listQuestionSets(UUID activityId) {
    requireActivity(activityId);
    return questionSets.findByActivityIdOrderByUpdatedAtDesc(activityId).stream().map(this::toQuestionSet).toList();
  }

  @Transactional
  public QuestionSetResponse createQuestionSet(UUID activityId, QuestionSetRequest request) {
    requireActivity(activityId);
    String name = cleanRequired(request.name(), "Question set name is required");
    if (questionSets.existsByActivityIdAndName(activityId, name)) {
      throw conflict("A question set with this name already exists");
    }
    List<UUID> questionIds = validateQuestionSetItems(activityId, request.questionIds());
    boolean active = Boolean.TRUE.equals(request.active());
    if (active && questionIds.isEmpty()) throw badRequest("An active question set must contain at least one question");
    QuestionSet set = questionSets.save(new QuestionSet(activityId, name, cleanOptional(request.description()), active));
    replaceQuestionSetItems(set, questionIds);
    if (active) activateQuestionSet(set);
    QuestionSetResponse response = toQuestionSet(set);
    broadcast(activityId, "question_set.created", response);
    return response;
  }

  @Transactional
  public QuestionSetResponse updateQuestionSet(UUID activityId, UUID questionSetId, QuestionSetRequest request) {
    QuestionSet set = requireQuestionSet(activityId, questionSetId);
    String name = request.name() == null ? set.getName() : cleanRequired(request.name(), "Question set name is required");
    if (!name.equals(set.getName()) && questionSets.existsByActivityIdAndName(activityId, name)) {
      throw conflict("A question set with this name already exists");
    }
    List<UUID> questionIds = request.questionIds() == null
        ? questionSetItems.findByQuestionSetIdOrderByDisplayOrderAsc(questionSetId).stream().map(QuestionSetItem::getQuestionId).toList()
        : validateQuestionSetItems(activityId, request.questionIds());
    if (Boolean.TRUE.equals(request.active()) && questionIds.isEmpty()) {
      throw badRequest("An active question set must contain at least one question");
    }
    boolean wasActive = set.isActive();
    set.update(name, request.description() == null ? set.getDescription() : cleanOptional(request.description()), request.active());
    replaceQuestionSetItems(set, questionIds);
    if (Boolean.TRUE.equals(request.active())) activateQuestionSet(set);
    else if (Boolean.FALSE.equals(request.active()) && wasActive) {
      activities.findById(activityId).filter(activity -> questionSetId.equals(activity.getActiveQuestionSetId()))
          .ifPresent(activity -> activity.activateQuestionSet(null));
    }
    QuestionSetResponse response = toQuestionSet(set);
    broadcast(activityId, "question_set.updated", response);
    return response;
  }

  @Transactional
  public QuestionSetResponse activateQuestionSet(UUID activityId, UUID questionSetId) {
    QuestionSet set = requireQuestionSet(activityId, questionSetId);
    if (questionSetItems.findByQuestionSetIdOrderByDisplayOrderAsc(questionSetId).isEmpty()) {
      throw badRequest("An active question set must contain at least one question");
    }
    activateQuestionSet(set);
    QuestionSetResponse response = toQuestionSet(set);
    broadcast(activityId, "question_set.activated", response);
    return response;
  }

  @Transactional
  public void deleteQuestionSet(UUID activityId, UUID questionSetId) {
    QuestionSet set = requireQuestionSet(activityId, questionSetId);
    questionSetItems.deleteByQuestionSetId(set.getId());
    questionSets.delete(set);
    activities.findById(activityId).filter(activity -> questionSetId.equals(activity.getActiveQuestionSetId()))
        .ifPresent(activity -> activity.activateQuestionSet(null));
    broadcast(activityId, "question_set.deleted", Map.of("questionSetId", questionSetId));
  }

  @Transactional
  public QuestionAdminResponse createQuestion(UUID activityId, QuestionWriteRequest request) {
    requireActivity(activityId);
    QuestionValues values = questionValues(request.type(), request.title(), request.options(), request.answers(),
        request.fullScore(), request.displayOrder(), request.mediaUrl(), request.partialCreditPercent(),
        request.textAcceptedAnswers(), request.textMatchMode(), request.enabled(),
        (int) questions.countByActivityId(activityId));
    Question question = questions.save(new Question(activityId, values.type(), values.title(), joinPipe(values.options()),
        joinComma(values.answers()), values.fullScore(), values.displayOrder(), values.mediaUrl(),
        values.partialCreditPercent(), writeTextAnswers(values.textAcceptedAnswers()), values.textMatchMode()));
    question.update(null, null, null, null, null, null, null, null, null, null, values.enabled());
    return toQuestionAdmin(question);
  }

  @Transactional
  public QuestionAdminResponse updateQuestion(UUID activityId, UUID questionId, QuestionWriteRequest request) {
    Question question = requireQuestion(activityId, questionId);
    QuestionValues values = questionValues(request.type() == null ? question.getType() : request.type(),
        request.title() == null ? question.getTitle() : request.title(),
        request.options() == null ? splitPipe(question.getOptions()) : request.options(),
        request.answers() == null ? new HashSet<>(splitComma(question.getAnswers())) : request.answers(),
        request.fullScore() == null ? question.getFullScore() : request.fullScore(),
        request.displayOrder() == null ? question.getDisplayOrder() : request.displayOrder(),
        // PUT receives an empty string when the editor explicitly removes a
        // media asset; null keeps the existing value for partial API clients.
        request.mediaUrl() == null ? question.getMediaUrl() : request.mediaUrl(),
        request.partialCreditPercent() == null ? question.getPartialCreditPercent() : request.partialCreditPercent(),
        request.textAcceptedAnswers() == null ? readTextAnswers(question.getTextAcceptedAnswers()) : request.textAcceptedAnswers(),
        request.textMatchMode() == null ? question.getTextMatchMode() : request.textMatchMode(),
        request.enabled() == null ? question.isEnabled() : request.enabled(), question.getDisplayOrder());
    String mediaUrl = request.mediaUrl() != null && request.mediaUrl().isBlank() ? "" : values.mediaUrl();
    question.update(values.type(), values.title(), joinPipe(values.options()), joinComma(values.answers()), values.fullScore(),
        values.displayOrder(), mediaUrl, values.partialCreditPercent(), writeTextAnswers(values.textAcceptedAnswers()),
        values.textMatchMode(), values.enabled());
    return toQuestionAdmin(question);
  }

  @Transactional
  public void deleteQuestion(UUID activityId, UUID questionId) {
    Question question = requireQuestion(activityId, questionId);
    if (submissions.existsByActivityIdAndQuestionId(activityId, questionId)) throw conflict("Question has submissions and cannot be deleted");
    questions.delete(question);
  }

  @Transactional
  public AnswerResult submitAnswer(UUID activityId, SubmitAnswerRequest request) {
    Activity activity = requireActivity(activityId);
    ensureAnsweringAllowed(activity);
    String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
    AnswerSubmission previous = submissions.findByActivityIdAndIdempotencyKey(activityId, idempotencyKey)
        .orElse(null);
    if (previous != null) {
      ensureMatchingReplay(previous, request);
      Participant replayParticipant = requireParticipant(activityId, previous.getParticipantId());
      return toAnswerResult(previous, replayParticipant.getScore(), true, 0);
    }
    Participant participant = requireParticipantForUpdate(activityId, request.participantId());
    if (!"ACTIVE".equals(participant.getStatus())) throw conflict("Participant is disabled");
    Question question = requireQuestion(activityId, request.questionId());
    if (!question.isEnabled()) throw conflict("Question is disabled");
    ControlState state = controls.get(activityId);
    if (state == null || !"QUESTION_OPEN".equals(state.stage()) || !question.getId().equals(state.questionId())) {
      throw conflict("This question is not currently open for answers");
    }
    if (remainingSeconds(state) <= 0) throw conflict("The answer window has closed");
    if (submissions.findByActivityIdAndParticipantIdAndQuestionId(activityId, participant.getId(), question.getId()).isPresent()) {
      throw conflict("Participant has already answered this question");
    }
    QuestionSet activeSet = questionSets.findByActivityIdAndActiveTrue(activityId).orElse(null);
    if (activeSet != null && questionSetItems.findByQuestionSetIdOrderByDisplayOrderAsc(activeSet.getId()).stream()
        .noneMatch(item -> item.getQuestionId().equals(question.getId()))) {
      throw conflict("This question is not part of the active question set");
    }
    Set<String> answers = normalizeAnswers(question, request.answers());
    validateSubmittedAnswers(question, answers);
    int responseRank = Math.toIntExact(submissions.countByActivityIdAndQuestionId(activityId, question.getId()) + 1);
    boolean autoTextCorrect = "TEXT".equals(question.getType()) && !"MANUAL".equals(question.getTextMatchMode())
        && AnswerScorer.matchesText(answers.iterator().next(), readTextAnswers(question.getTextAcceptedAnswers()),
            question.getTextMatchMode());
    boolean requiresManualReview = "TEXT".equals(question.getType()) && !autoTextCorrect;
    int points = "TEXT".equals(question.getType()) ? (autoTextCorrect ? question.getFullScore() : 0)
        : AnswerScorer.score(question.getType(), answers, new HashSet<>(splitComma(question.getAnswers())),
            question.getFullScore(), question.getPartialCreditPercent());
    String outcome = requiresManualReview ? "PENDING_REVIEW" : points >= question.getFullScore() ? "CORRECT"
        : points > 0 ? "PARTIAL" : "INCORRECT";
    AnswerSubmission submission = submissions.save(new AnswerSubmission(activityId, participant.getId(), question.getId(),
        idempotencyKey, encodeSubmittedAnswers(question, answers), points, outcome, null));
    if (requiresManualReview) {
      broadcast(activityId, "answer.submitted", Map.of("participantId", participant.getId(), "questionId", question.getId(),
          "submissionId", submission.getId(), "status", submission.getStatus()));
    } else {
      participant.addScore(points);
      scoreLedgers.save(new ScoreLedger(activityId, participant.getId(), question.getId(), submission.getId(), points,
          "ANSWER", "Auto-scored answer"));
      broadcast(activityId, "answer.scored", Map.of("participantId", participant.getId(), "questionId", question.getId(),
          "points", points, "score", participant.getScore(), "status", outcome, "responseRank", responseRank));
    }
    return toAnswerResult(submission, participant.getScore(), false, responseRank);
  }

  @Transactional
  public SubmissionResponse gradeSubmission(UUID activityId, UUID submissionId, GradeSubmissionRequest request) {
    AnswerSubmission submission = requireSubmission(activityId, submissionId);
    Question question = requireQuestion(activityId, submission.getQuestionId());
    if (request.awardedPoints() > question.getFullScore()) throw badRequest("Awarded points exceed question score");
    Participant participant = requireParticipantForUpdate(activityId, submission.getParticipantId());
    int delta = request.awardedPoints() - submission.getAwardedPoints();
    submission.grade(request.awardedPoints(), cleanOptional(request.feedback()));
    if (delta != 0) {
      participant.addScore(delta);
      scoreLedgers.save(new ScoreLedger(activityId, participant.getId(), question.getId(), submission.getId(), delta,
          "GRADE_ADJUSTMENT", "Manual grading adjustment"));
    }
    broadcast(activityId, "answer.graded", Map.of("participantId", participant.getId(), "submissionId", submission.getId(),
        "points", submission.getAwardedPoints(), "score", participant.getScore()));
    return toSubmission(submission);
  }

  @Transactional(readOnly = true)
  public List<SubmissionResponse> submissions(UUID activityId, UUID participantId) {
    requireParticipant(activityId, participantId);
    return submissions.findByActivityIdAndParticipantIdOrderBySubmittedAtDesc(activityId, participantId).stream()
        .map(this::toSubmission).toList();
  }

  @Transactional
  public ScoreLedgerResponse adjustScore(UUID activityId, ManualScoreRequest request) {
    requireActivity(activityId);
    Participant participant = requireParticipantForUpdate(activityId, request.participantId());
    participant.addScore(request.points());
    ScoreLedger entry = scoreLedgers.save(new ScoreLedger(activityId, participant.getId(), null, null, request.points(),
        "MANUAL_ADJUSTMENT", cleanOptional(request.note())));
    broadcast(activityId, "score.adjusted", Map.of("participantId", participant.getId(), "points", request.points(),
        "score", participant.getScore()));
    return toScoreLedger(entry);
  }

  @Transactional(readOnly = true)
  public List<ScoreLedgerResponse> scoreLedger(UUID activityId, UUID participantId) {
    requireParticipant(activityId, participantId);
    return scoreLedgers.findByActivityIdAndParticipantIdOrderByCreatedAtAsc(activityId, participantId).stream()
        .map(this::toScoreLedger).toList();
  }

  @Transactional(readOnly = true)
  public List<ScoreboardEntry> scoreboard(UUID activityId) {
    requireActivity(activityId);
    List<Participant> ordered = participants.findByActivityId(activityId).stream().sorted(participantOrder()).toList();
    return IntStream.range(0, ordered.size()).mapToObj(index -> {
      Participant participant = ordered.get(index);
      return new ScoreboardEntry(index + 1, participant.getId(), participant.getName(), participant.getVenue(),
          participant.getScore());
    }).toList();
  }

  @Transactional(readOnly = true)
  public QuestionResponseStats questionResponseStats(UUID activityId, UUID questionId) {
    requireQuestion(activityId, questionId);
    List<Participant> activityParticipants = participants.findByActivityId(activityId);
    Map<UUID, Participant> participantById = new HashMap<>();
    int eligibleParticipantCount = 0;
    for (Participant participant : activityParticipants) {
      participantById.put(participant.getId(), participant);
      if ("ACTIVE".equals(participant.getStatus())) eligibleParticipantCount++;
    }
    List<AnswerSubmission> answered = submissions.findByActivityIdAndQuestionIdOrderBySubmittedAtAsc(activityId, questionId);
    int correctCount = (int) answered.stream().filter(item -> "CORRECT".equals(item.getStatus())).count();
    int partialCount = (int) answered.stream().filter(item -> "PARTIAL".equals(item.getStatus())).count();
    int incorrectCount = (int) answered.stream().filter(item -> "INCORRECT".equals(item.getStatus())).count();
    int pendingReviewCount = (int) answered.stream().filter(item -> "PENDING_REVIEW".equals(item.getStatus())).count();
    List<QuestionSubmissionEntry> entries = IntStream.range(0, answered.size()).mapToObj(index -> {
      AnswerSubmission submission = answered.get(index);
      Participant participant = participantById.get(submission.getParticipantId());
      return new QuestionSubmissionEntry(submission.getParticipantId(),
          participant == null ? "已移除参与者" : participant.getName(),
          participant == null ? "--" : participant.getVenue(), readSubmittedAnswers(submission.getSubmittedAnswers()),
          submission.getAwardedPoints(), submission.getStatus(), index + 1, submission.getSubmittedAt());
    }).toList();
    return new QuestionResponseStats(questionId, eligibleParticipantCount, answered.size(),
        Math.max(0, eligibleParticipantCount - answered.size()), pendingReviewCount, correctCount, partialCount,
        incorrectCount, entries);
  }

  @Transactional
  public ControlState control(UUID activityId, ControlRequest request) {
    requireActivity(activityId);
    if (request.questionId() != null) requireQuestion(activityId, request.questionId());
    int seconds = request.seconds() == null ? 30 : request.seconds();
    if (seconds < 0 || seconds > 86_400) throw badRequest("Control timer must be between 0 and 86400 seconds");
    ControlState state = new ControlState(request.stage().trim().toUpperCase(Locale.ROOT), request.questionId(), seconds,
        Instant.now());
    controls.put(activityId, state);
    broadcast(activityId, "control.updated", state);
    synchronizeControlledScreens(activityId, state);
    return state;
  }

  @Transactional(readOnly = true)
  public ControlState controlState(UUID activityId) {
    requireActivity(activityId);
    return liveControlState(controls.getOrDefault(activityId, new ControlState("LOBBY", null, 0, Instant.now())));
  }

  private void synchronizeControlledScreens(UUID activityId, ControlState state) {
    String stage = state.stage().trim().toUpperCase(Locale.ROOT);
    Map<String, Object> payload = new HashMap<>();
    ScreenDisplayMode mode = ScreenDisplayMode.LOBBY;

    if ("QUESTION_OPEN".equals(stage) || "ANSWER_REVEALED".equals(stage)) {
      Question question = state.questionId() == null ? null : requireQuestion(activityId, state.questionId());
      if (question == null) {
        payload.put("headline", "等待工作人员选择题目");
        payload.put("message", "当前活动尚未下发可展示的题目。");
      } else {
        payload.put("title", question.getTitle());
        payload.put("options", splitPipe(question.getOptions()));
        payload.put("mediaUrl", question.getMediaUrl());
        payload.put("seconds", remainingSeconds(state));
        payload.put("updatedAt", state.updatedAt());
        payload.put("questionType", question.getType());
        if ("ANSWER_REVEALED".equals(stage)) {
          payload.put("answers", displayAnswers(question));
          List<Map<String, Object>> responses = submissions
              .findByActivityIdAndQuestionIdOrderBySubmittedAtAsc(activityId, question.getId()).stream()
              .map(item -> {
                Map<String, Object> response = new HashMap<>();
                Participant participant = participants.findById(item.getParticipantId()).orElse(null);
                response.put("participantName", participant == null ? "参与者" : participant.getName());
                response.put("answers", readSubmittedAnswers(item.getSubmittedAnswers()));
                response.put("awardedPoints", item.getAwardedPoints());
                response.put("status", item.getStatus());
                response.put("submittedAt", item.getSubmittedAt());
                response.put("elapsedSeconds", Math.max(0L,
                    Duration.between(state.updatedAt(), item.getSubmittedAt()).getSeconds()));
                return response;
              }).toList();
          payload.put("responses", responses);
        }
      }
      mode = "ANSWER_REVEALED".equals(stage) ? ScreenDisplayMode.RESULT : ScreenDisplayMode.QUESTION;
    } else if ("SCOREBOARD".equals(stage)) {
      payload.put("rows", scoreboard(activityId));
      payload.put("headline", "实时积分排行榜");
      mode = ScreenDisplayMode.SCOREBOARD;
    } else if ("ENDED".equals(stage) || "WINNERS".equals(stage)) {
      List<Map<String, Object>> winners = new ArrayList<>();
      for (PrizeAward award : awards.findByActivityIdOrderByAwardedAtDesc(activityId)) {
        if ("VOID".equals(award.getStatus())) continue;
        Participant participant = requireParticipant(activityId, award.getParticipantId());
        Map<String, Object> winner = new HashMap<>();
        winner.put("name", participant.getName());
        winner.put("venue", participant.getVenue());
        winner.put("prizeName", award.getPrizeName());
        winner.put("deliveryType", award.getDeliveryType());
        winners.add(winner);
      }
      payload.put("rows", winners);
      payload.put("headline", "获奖名单");
      mode = ScreenDisplayMode.WINNERS;
    } else {
      payload.put("headline", "现场内容即将开始");
      payload.put("message", "工作人员将在控场台下发下一步内容。");
    }
    screens.publishActivityDisplay(activityId, mode, payload);
  }

  private ControlState liveControlState(ControlState state) {
    if (!"QUESTION_OPEN".equals(state.stage()) || state.seconds() <= 0) return state;
    return new ControlState(state.stage(), state.questionId(), remainingSeconds(state), state.updatedAt());
  }

  private int remainingSeconds(ControlState state) {
    if (state.seconds() <= 0 || state.updatedAt() == null) return Math.max(0, state.seconds());
    long elapsed = Duration.between(state.updatedAt(), Instant.now()).getSeconds();
    return Math.max(0, state.seconds() - Math.toIntExact(Math.min(Integer.MAX_VALUE, elapsed)));
  }

  @Transactional(readOnly = true)
  public List<PrizePoolResponse> listPrizePools(UUID activityId) {
    requireActivity(activityId);
    return prizePools.findByActivityIdOrderByCreatedAtAsc(activityId).stream().map(this::toPrizePool).toList();
  }

  @Transactional
  public PrizePoolResponse createPrizePool(UUID activityId, PrizePoolRequest request) {
    requireActivity(activityId);
    String code = normalizeCode(request.code(), "prize pool code");
    if (prizePools.findByActivityIdAndCode(activityId, code).isPresent()) throw conflict("Prize pool code already exists");
    PrizeValues values = prizeValues(request.purpose(), request.deliveryType(), request.totalQuantity(), request.minScore(),
        request.drawWeight(), request.rankFrom(), request.rankTo());
    PrizePool pool = prizePools.save(new PrizePool(activityId, code, cleanRequired(request.name(), "Prize pool name"),
        values.purpose(), values.deliveryType(), cleanOptional(request.description()), cleanOptional(request.redemptionUrl()),
        values.totalQuantity(), values.minScore(), values.drawWeight(), values.rankFrom(), values.rankTo()));
    pool.update(null, null, null, null, null, null, null, null, null, null, request.enabled());
    return toPrizePool(pool);
  }

  @Transactional
  public PrizePoolResponse updatePrizePool(UUID activityId, UUID poolId, UpdatePrizePoolRequest request) {
    PrizePool pool = requirePrizePool(activityId, poolId);
    PrizeValues values = prizeValues(request.purpose() == null ? pool.getPurpose() : request.purpose(),
        request.deliveryType() == null ? pool.getDeliveryType() : request.deliveryType(),
        request.totalQuantity() == null ? pool.getTotalQuantity() : request.totalQuantity(),
        request.minScore() == null ? pool.getMinScore() : request.minScore(),
        request.drawWeight() == null ? pool.getDrawWeight() : request.drawWeight(),
        request.rankFrom() == null ? pool.getRankFrom() : request.rankFrom(),
        request.rankTo() == null ? pool.getRankTo() : request.rankTo());
    if (values.totalQuantity() < pool.getClaimedQuantity()) throw conflict("Prize stock cannot be below claimed stock");
    pool.update(cleanOptional(request.name()), values.purpose(), values.deliveryType(), cleanOptional(request.description()),
        cleanOptional(request.redemptionUrl()), values.totalQuantity(), values.minScore(), values.drawWeight(), values.rankFrom(),
        values.rankTo(), request.enabled());
    return toPrizePool(pool);
  }

  @Transactional
  public void deletePrizePool(UUID activityId, UUID poolId) {
    PrizePool pool = requirePrizePool(activityId, poolId);
    if (pool.getClaimedQuantity() > 0) throw conflict("Claimed prize pool cannot be deleted");
    prizePools.delete(pool);
  }

  @Transactional(readOnly = true)
  public List<AwardResponse> awards(UUID activityId, UUID participantId) {
    requireParticipant(activityId, participantId);
    return awards.findByActivityIdAndParticipantId(activityId, participantId).stream().map(this::toAward).toList();
  }

  @Transactional(readOnly = true)
  public List<AwardDetailResponse> allAwards(UUID activityId, String status) {
    requireActivity(activityId);
    List<PrizeAward> found = status == null || status.isBlank()
        ? awards.findByActivityIdOrderByAwardedAtDesc(activityId)
        : awards.findByActivityIdAndStatusOrderByAwardedAtDesc(activityId, status.trim().toUpperCase(Locale.ROOT));
    return found.stream().map(this::toAwardDetail).toList();
  }

  @Transactional
  public AwardDetailResponse issueAward(UUID activityId, IssueAwardRequest request) {
    requireActivity(activityId);
    Participant participant = requireParticipantForUpdate(activityId, request.participantId());
    PrizePool pool = requirePrizePoolForUpdate(activityId, request.prizePoolId());
    PrizeAward award = issuePoolAward(activityId, participant, pool, cleanOptional(request.fulfillmentNote()));
    broadcast(activityId, "award.issued", toAwardDetail(award));
    return toAwardDetail(award);
  }

  @Transactional
  public List<AwardDetailResponse> issueRankingAwards(UUID activityId, UUID poolId) {
    Activity activity = requireActivity(activityId);
    ControlState controlState = controls.get(activityId);
    if (!"FINISHED".equals(activity.getStatus())
        && (controlState == null || !"WINNERS".equals(controlState.stage()))) {
      throw conflict("Ranking awards can only be issued after the activity ends or during winner confirmation");
    }
    PrizePool pool = requirePrizePoolForUpdate(activityId, poolId);
    if (!"RANKING".equals(pool.getPurpose())) throw badRequest("Prize pool is not a ranking pool");
    if (pool.getRankFrom() == null || pool.getRankTo() == null) throw badRequest("Ranking pool requires rank range");
    List<Participant> ranked = participants.findByActivityId(activityId).stream().sorted(participantOrder()).toList();
    List<AwardDetailResponse> result = new ArrayList<>();
    for (int index = pool.getRankFrom() - 1; index < ranked.size() && index < pool.getRankTo(); index++) {
      Participant participant = ranked.get(index);
      if (!pool.isAvailableFor(participant.getScore())) break;
      if (awards.existsByActivityIdAndPrizePoolIdAndParticipantId(activityId, pool.getId(), participant.getId())) continue;
      PrizeAward award = issuePoolAward(activityId, participant, pool, "Ranking #" + (index + 1));
      result.add(toAwardDetail(award));
    }
    if (!result.isEmpty()) broadcast(activityId, "awards.ranking_issued", result);
    return result;
  }

  @Transactional
  public LotteryChanceResponse grantLotteryChances(UUID activityId, UUID participantId,
      GrantLotteryChancesRequest request) {
    requireActivity(activityId);
    requireParticipant(activityId, participantId);
    LotteryChance chance = lotteryChances.findByActivityIdAndParticipantIdForUpdate(activityId, participantId)
        .orElseGet(() -> lotteryChances.save(new LotteryChance(activityId, participantId, 0, null)));
    chance.grant(request.draws(), cleanOptional(request.reason()));
    LotteryChanceResponse response = toLotteryChance(chance);
    broadcast(activityId, "lottery.chance_granted", response);
    return response;
  }

  @Transactional(readOnly = true)
  public LotteryChanceResponse lotteryChance(UUID activityId, UUID participantId) {
    requireParticipant(activityId, participantId);
    LotteryChance chance = lotteryChances.findByActivityIdAndParticipantId(activityId, participantId)
        .orElse(new LotteryChance(activityId, participantId, 0, null));
    return toLotteryChance(chance);
  }

  @Transactional
  public DrawResult draw(UUID activityId, DrawRequest request) {
    requireActivity(activityId);
    String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
    LotteryDraw previous = lotteryDraws.findByActivityIdAndIdempotencyKey(activityId, idempotencyKey).orElse(null);
    if (previous != null) {
      if (!previous.getParticipantId().equals(request.participantId())) throw conflict("Idempotency key belongs to another participant");
      return drawResult(requireAward(activityId, previous.getPrizeAwardId()), true);
    }
    Participant participant = requireParticipantForUpdate(activityId, request.participantId());
    if (!"ACTIVE".equals(participant.getStatus())) throw conflict("Participant is disabled");
    if (request.venue() != null && !request.venue().isBlank()
        && !participant.getVenue().equals(normalizeVenue(request.venue()))) {
      throw conflict("Lottery entry venue does not match the participant venue");
    }
    LotteryChance chance = lotteryChances.findByActivityIdAndParticipantIdForUpdate(activityId, participant.getId())
        .orElseThrow(() -> conflict("No lottery chances have been granted"));
    if (chance.getRemainingDraws() <= 0) throw conflict("No lottery chances remaining");
    PrizePool pool = selectLotteryPool(activityId, request.prizePoolId(), participant.getScore());
    chance.consume();
    PrizeAward award = issuePoolAward(activityId, participant, pool, "Lottery draw");
    lotteryDraws.save(new LotteryDraw(activityId, participant.getId(), pool.getId(), award.getId(), idempotencyKey));
    DrawResult result = drawResult(award, false);
    broadcast(activityId, "lottery.completed", result);
    return result;
  }

  @Transactional
  public AwardResponse redeem(UUID activityId, UUID awardId) {
    return redeem(activityId, awardId, "system");
  }

  @Transactional
  public AwardResponse redeem(UUID activityId, UUID awardId, String operator) {
    requireActivity(activityId);
    PrizeAward award = requireAwardForUpdate(activityId, awardId);
    if ("VOID".equals(award.getStatus())) throw conflict("Voided award cannot be redeemed");
    if (!"REDEEMED".equals(award.getStatus())) award.redeem(cleanOptional(operator) == null ? "system" : cleanOptional(operator));
    AwardResponse response = toAward(award);
    broadcast(activityId, "award.redeemed", response);
    return response;
  }

  @Transactional
  public AwardDetailResponse reverseRedemption(UUID activityId, UUID awardId) {
    requireActivity(activityId);
    PrizeAward award = requireAwardForUpdate(activityId, awardId);
    if (!"REDEEMED".equals(award.getStatus())) throw conflict("Award has not been redeemed");
    award.reverseRedemption();
    AwardDetailResponse response = toAwardDetail(award);
    broadcast(activityId, "award.redemption_reversed", response);
    return response;
  }

  @Transactional
  public AwardDetailResponse voidAward(UUID activityId, UUID awardId, VoidAwardRequest request) {
    requireActivity(activityId);
    PrizeAward award = requireAwardForUpdate(activityId, awardId);
    if ("VOID".equals(award.getStatus())) return toAwardDetail(award);
    if ("REDEEMED".equals(award.getStatus())) throw conflict("Redeemed award cannot be voided");
    award.voidAward(cleanOptional(request.note()));
    if (award.getPrizePoolId() != null) requirePrizePoolForUpdate(activityId, award.getPrizePoolId()).release();
    AwardDetailResponse response = toAwardDetail(award);
    broadcast(activityId, "award.voided", response);
    return response;
  }

  private Activity requireActivity(UUID activityId) {
    return activities.findById(activityId).orElseThrow(() -> notFound("Activity does not exist"));
  }

  private void createInitiatorMembership(UUID activityId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)
        || principal.userId() == null) return;
    memberships.findByUserIdAndActivityId(principal.userId(), activityId)
        .orElseGet(() -> memberships.save(new ActivityMembership(activityId, principal.userId(), UserRole.ACTIVITY_ADMIN)));
  }

  private Venue requireVenue(UUID activityId, UUID venueId) {
    return venues.findById(venueId).filter(venue -> venue.getActivityId().equals(activityId))
        .orElseThrow(() -> notFound("Venue does not exist in this activity"));
  }

  private RegistrationField requireRegistrationField(UUID activityId, UUID fieldId) {
    return registrationFields.findById(fieldId).filter(field -> field.getActivityId().equals(activityId))
        .orElseThrow(() -> notFound("Registration field does not exist in this activity"));
  }

  private Participant requireParticipant(UUID activityId, UUID participantId) {
    return participants.findById(participantId).filter(participant -> participant.getActivityId().equals(activityId))
        .orElseThrow(() -> notFound("Participant does not exist in this activity"));
  }

  private Participant requireParticipantForUpdate(UUID activityId, UUID participantId) {
    return participants.findByIdForUpdate(participantId).filter(participant -> participant.getActivityId().equals(activityId))
        .orElseThrow(() -> notFound("Participant does not exist in this activity"));
  }

  private Question requireQuestion(UUID activityId, UUID questionId) {
    return questions.findById(questionId).filter(question -> question.getActivityId().equals(activityId))
        .orElseThrow(() -> notFound("Question does not exist in this activity"));
  }

  private QuestionSet requireQuestionSet(UUID activityId, UUID questionSetId) {
    return questionSets.findByIdAndActivityId(questionSetId, activityId)
        .orElseThrow(() -> notFound("Question set does not exist in this activity"));
  }

  private List<Question> orderedQuestionsForActivity(UUID activityId) {
    QuestionSet activeSet = questionSets.findByActivityIdAndActiveTrue(activityId).orElse(null);
    if (activeSet == null) {
      return questions.findByActivityIdOrderByDisplayOrderAsc(activityId).stream()
          .filter(Question::isEnabled)
          .toList();
    }
    Map<UUID, Question> byId = questions.findByActivityIdOrderByDisplayOrderAsc(activityId).stream()
        .collect(java.util.stream.Collectors.toMap(Question::getId, question -> question));
    return questionSetItems.findByQuestionSetIdOrderByDisplayOrderAsc(activeSet.getId()).stream()
        .map(item -> byId.get(item.getQuestionId()))
        .filter(java.util.Objects::nonNull)
        .filter(Question::isEnabled)
        .toList();
  }

  private List<UUID> validateQuestionSetItems(UUID activityId, List<UUID> questionIds) {
    List<UUID> ids = questionIds == null ? List.of() : List.copyOf(questionIds);
    if (new LinkedHashSet<>(ids).size() != ids.size()) throw badRequest("Question set cannot contain duplicate questions");
    ids.forEach(questionId -> requireQuestion(activityId, questionId));
    return ids;
  }

  private void replaceQuestionSetItems(QuestionSet set, List<UUID> questionIds) {
    questionSetItems.deleteByQuestionSetId(set.getId());
    // Force the bulk delete to reach the database before re-inserting the
    // reordered rows, otherwise the unique constraints can see both versions
    // during Hibernate's transaction flush.
    questionSetItems.flush();
    for (int index = 0; index < questionIds.size(); index++) {
      questionSetItems.save(new QuestionSetItem(set.getId(), questionIds.get(index), index));
    }
  }

  private void activateQuestionSet(QuestionSet selected) {
    questionSets.findByActivityIdOrderByUpdatedAtDesc(selected.getActivityId()).stream()
        .filter(candidate -> !candidate.getId().equals(selected.getId()) && candidate.isActive())
        .forEach(QuestionSet::deactivate);
    selected.activate();
    activities.findById(selected.getActivityId()).ifPresent(activity -> activity.activateQuestionSet(selected.getId()));
  }

  private AnswerSubmission requireSubmission(UUID activityId, UUID submissionId) {
    return submissions.findById(submissionId).filter(submission -> submission.getActivityId().equals(activityId))
        .orElseThrow(() -> notFound("Answer submission does not exist in this activity"));
  }

  private PrizePool requirePrizePool(UUID activityId, UUID poolId) {
    return prizePools.findById(poolId).filter(pool -> pool.getActivityId().equals(activityId))
        .orElseThrow(() -> notFound("Prize pool does not exist in this activity"));
  }

  private PrizePool requirePrizePoolForUpdate(UUID activityId, UUID poolId) {
    return prizePools.findByIdForUpdate(poolId).filter(pool -> pool.getActivityId().equals(activityId))
        .orElseThrow(() -> notFound("Prize pool does not exist in this activity"));
  }

  private PrizeAward requireAward(UUID activityId, UUID awardId) {
    return awards.findById(awardId).filter(award -> award.getActivityId().equals(activityId))
        .orElseThrow(() -> notFound("Award does not exist in this activity"));
  }

  private PrizeAward requireAwardForUpdate(UUID activityId, UUID awardId) {
    return awards.findByIdForUpdate(awardId).filter(award -> award.getActivityId().equals(activityId))
        .orElseThrow(() -> notFound("Award does not exist in this activity"));
  }

  private PrizeAward issuePoolAward(UUID activityId, Participant participant, PrizePool pool, String note) {
    if (!pool.isAvailableFor(participant.getScore())) throw conflict("Prize is unavailable or participant is ineligible");
    pool.claim();
    String redemptionCode = "PHYSICAL".equals(pool.getDeliveryType()) ? null : "KM-" + UUID.randomUUID().toString()
        .replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    return awards.save(new PrizeAward(activityId, participant.getId(), pool.getId(), pool.getName(), pool.getDeliveryType(),
        "PENDING", redemptionCode, pool.getRedemptionUrl(), note));
  }

  private PrizePool selectLotteryPool(UUID activityId, UUID preferredPoolId, int participantScore) {
    if (preferredPoolId != null) {
      PrizePool pool = requirePrizePoolForUpdate(activityId, preferredPoolId);
      if (!"LOTTERY".equals(pool.getPurpose())) throw badRequest("Requested prize pool is not a lottery pool");
      if (!pool.isAvailableFor(participantScore)) throw conflict("Requested prize pool is unavailable");
      return pool;
    }
    List<UUID> candidateIds = prizePools.findByActivityIdAndPurposeAndEnabledTrue(activityId, "LOTTERY").stream()
        .filter(pool -> pool.isAvailableFor(participantScore)).map(PrizePool::getId).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    while (!candidateIds.isEmpty()) {
      int totalWeight = candidateIds.stream().map(prizePools::findById).flatMap(java.util.Optional::stream)
          .mapToInt(PrizePool::getDrawWeight).sum();
      int ticket = LOTTERY_RANDOM.nextInt(Math.max(1, totalWeight));
      UUID selectedId = candidateIds.get(candidateIds.size() - 1);
      for (UUID candidateId : candidateIds) {
        PrizePool candidate = prizePools.findById(candidateId).orElse(null);
        if (candidate == null) continue;
        ticket -= candidate.getDrawWeight();
        if (ticket < 0) {
          selectedId = candidateId;
          break;
        }
      }
      PrizePool locked = requirePrizePoolForUpdate(activityId, selectedId);
      if (locked.isAvailableFor(participantScore)) return locked;
      candidateIds.remove(selectedId);
    }
    throw conflict("No lottery prizes are available");
  }

  private void ensureStatusTransition(String current, String next) {
    if (current.equals(next)) return;
    if ("CANCELLED".equals(current) || "FINISHED".equals(current)) throw conflict("Closed activities cannot be reopened");
    if ("DRAFT".equals(current) && !("REGISTRATION_OPEN".equals(next) || "LIVE".equals(next) || "CANCELLED".equals(next))) {
      throw conflict("Draft activity can only open registration, go live, or be cancelled");
    }
    if ("REGISTRATION_OPEN".equals(current) && !("LIVE".equals(next) || "PAUSED".equals(next)
        || "FINISHED".equals(next) || "CANCELLED".equals(next))) {
      throw conflict("Invalid registration activity transition");
    }
    if ("LIVE".equals(current) && !("PAUSED".equals(next) || "FINISHED".equals(next) || "CANCELLED".equals(next))) {
      throw conflict("Invalid live activity transition");
    }
    if ("PAUSED".equals(current) && !("LIVE".equals(next) || "FINISHED".equals(next) || "CANCELLED".equals(next))) {
      throw conflict("Invalid paused activity transition");
    }
  }

  private void ensureRegistrationAllowed(Activity activity) {
    if ("FINISHED".equals(activity.getStatus()) || "CANCELLED".equals(activity.getStatus())) {
      throw conflict("Registration is closed for this activity");
    }
  }

  private void ensureAnsweringAllowed(Activity activity) {
    if ("FINISHED".equals(activity.getStatus()) || "CANCELLED".equals(activity.getStatus())) {
      throw conflict("Answering is closed for this activity");
    }
  }

  private void ensureMatchingReplay(AnswerSubmission previous, SubmitAnswerRequest request) {
    if (!previous.getParticipantId().equals(request.participantId()) || !previous.getQuestionId().equals(request.questionId())) {
      throw conflict("Idempotency key belongs to a different answer request");
    }
  }

  private void validateSubmittedAnswers(Question question, Set<String> answers) {
    if (answers.isEmpty()) throw badRequest("At least one answer is required");
    if ("SINGLE".equals(question.getType()) && answers.size() != 1) throw badRequest("Single-choice questions require one answer");
    if ("TEXT".equals(question.getType()) && answers.size() != 1) {
      throw badRequest("Text questions require one answer");
    }
    if (!"TEXT".equals(question.getType())) {
      Set<String> options = new HashSet<>(splitPipe(question.getOptions()));
      if (!options.containsAll(answers)) throw badRequest("Answer contains an unknown option");
    }
  }

  private QuestionValues questionValues(String rawType, String rawTitle, List<String> rawOptions, Set<String> rawAnswers,
      Integer rawFullScore, Integer rawDisplayOrder, String rawMediaUrl, Integer rawPartialCreditPercent,
      List<String> rawTextAcceptedAnswers, String rawTextMatchMode, Boolean rawEnabled, int fallbackDisplayOrder) {
    String type = normalizeEnum(rawType, QUESTION_TYPES, "question type");
    String title = cleanRequired(rawTitle, "Question title");
    List<String> options = cleanValues(rawOptions == null ? List.of() : rawOptions, "option");
    Set<String> answers = new HashSet<>(cleanValues(rawAnswers == null ? Set.of() : rawAnswers, "answer"));
    List<String> textAcceptedAnswers = "TEXT".equals(type)
        ? cleanTextAnswers(rawTextAcceptedAnswers == null ? List.of() : rawTextAcceptedAnswers)
        : List.of();
    String textMatchMode = "TEXT".equals(type)
        ? normalizeEnum(rawTextMatchMode == null ? "MANUAL" : rawTextMatchMode, TEXT_MATCH_MODES, "text match mode")
        : "MANUAL";
    int fullScore = rawFullScore == null ? 100 : rawFullScore;
    int displayOrder = rawDisplayOrder == null ? fallbackDisplayOrder : rawDisplayOrder;
    int partialCreditPercent = rawPartialCreditPercent == null ? 40 : rawPartialCreditPercent;
    if (fullScore < 1 || displayOrder < 0 || partialCreditPercent < 0 || partialCreditPercent > 100) {
      throw badRequest("Question numeric values are out of range");
    }
    if ("TEXT".equals(type)) {
      if (!options.isEmpty()) throw badRequest("Text questions cannot define options");
      answers = Set.of();
      if (!"MANUAL".equals(textMatchMode) && textAcceptedAnswers.isEmpty()) {
        throw badRequest("Automatic text matching requires at least one accepted answer");
      }
      if ("REGEX".equals(textMatchMode)) validateTextPatterns(textAcceptedAnswers);
    } else {
      if (options.size() < 2) throw badRequest("Choice questions require at least two options");
      if (answers.isEmpty() || !new HashSet<>(options).containsAll(answers)) throw badRequest("Correct answer is not in options");
      if ("SINGLE".equals(type) && answers.size() != 1) throw badRequest("Single-choice questions require one correct answer");
    }
    return new QuestionValues(type, title, options, answers, fullScore, displayOrder, cleanOptional(rawMediaUrl),
        partialCreditPercent, textAcceptedAnswers, textMatchMode, rawEnabled == null || rawEnabled);
  }

  private FieldValues fieldValues(String rawType, List<String> rawOptions) {
    String type = normalizeEnum(rawType, FIELD_TYPES, "registration field type");
    List<String> options = cleanValues(rawOptions == null ? List.of() : rawOptions, "field option");
    if (("SELECT".equals(type) || "RADIO".equals(type) || "CHECKBOX".equals(type)) && options.isEmpty()) {
      throw badRequest("Choice registration fields require options");
    }
    if (!("SELECT".equals(type) || "RADIO".equals(type) || "CHECKBOX".equals(type)) && !options.isEmpty()) {
      throw badRequest("Only choice registration fields can define options");
    }
    return new FieldValues(type, options);
  }

  private PrizeValues prizeValues(String rawPurpose, String rawDeliveryType, Integer rawTotalQuantity,
      Integer rawMinScore, Integer rawDrawWeight, Integer rawRankFrom, Integer rawRankTo) {
    String purpose = normalizeEnum(rawPurpose, POOL_PURPOSES, "prize pool purpose");
    String deliveryType = normalizeEnum(rawDeliveryType, DELIVERY_TYPES, "delivery type");
    int totalQuantity = rawTotalQuantity == null ? 1 : rawTotalQuantity;
    int minScore = rawMinScore == null ? 0 : rawMinScore;
    int drawWeight = rawDrawWeight == null ? 1 : rawDrawWeight;
    if (totalQuantity < 1 || minScore < 0 || drawWeight < 1) throw badRequest("Prize pool numeric values are out of range");
    if ("RANKING".equals(purpose)) {
      if (rawRankFrom == null || rawRankTo == null || rawRankFrom < 1 || rawRankTo < rawRankFrom) {
        throw badRequest("Ranking prize pools require a valid rank range");
      }
      if (totalQuantity < rawRankTo - rawRankFrom + 1) throw badRequest("Ranking prize pool stock is below rank range");
    } else if (rawRankFrom != null || rawRankTo != null) {
      throw badRequest("Only ranking prize pools can define rank range");
    }
    return new PrizeValues(purpose, deliveryType, totalQuantity, minScore, drawWeight, rawRankFrom, rawRankTo);
  }

  private Map<String, String> validateCustomFields(UUID activityId, Map<String, String> rawFields) {
    Map<String, String> supplied = new HashMap<>();
    if (rawFields != null) {
      rawFields.forEach((key, value) -> {
        String normalizedKey = normalizeFieldKey(key);
        if (RESERVED_FIELD_KEYS.contains(normalizedKey)) throw badRequest("Custom field key is reserved");
        if (value != null && !value.isBlank()) supplied.put(normalizedKey, value.trim());
      });
    }
    List<RegistrationField> configured = registrationFields.findByActivityIdOrderByDisplayOrderAsc(activityId);
    Map<String, RegistrationField> configuredByKey = configured.stream().filter(RegistrationField::isEnabled)
        .collect(java.util.stream.Collectors.toMap(RegistrationField::getFieldKey, field -> field));
    for (String key : supplied.keySet()) {
      if (!configuredByKey.containsKey(key)) throw badRequest("Unknown registration field: " + key);
    }
    for (RegistrationField field : configuredByKey.values()) {
      String value = supplied.get(field.getFieldKey());
      if (field.isRequired() && (value == null || value.isBlank())) throw badRequest("Required registration field is missing: " + field.getFieldKey());
      if (value != null && ("SELECT".equals(field.getType()) || "RADIO".equals(field.getType())
          || "CHECKBOX".equals(field.getType())) && !splitPipe(field.getOptions()).contains(value)) {
        throw badRequest("Invalid value for registration field: " + field.getFieldKey());
      }
    }
    return Map.copyOf(supplied);
  }

  private Comparator<Participant> participantOrder() {
    return Comparator.comparingInt(Participant::getScore).reversed()
        .thenComparing(Participant::getLastScoreAt, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(Participant::getRegisteredAt);
  }

  private String writeFields(Map<String, String> fields) {
    try {
      return objectMapper.writeValueAsString(fields == null ? Map.of() : fields);
    } catch (JsonProcessingException exception) {
      throw badRequest("Unable to save registration fields");
    }
  }

  private Map<String, String> readFields(String value) {
    if (value == null || value.isBlank()) return Map.of();
    try {
      Map<String, String> parsed = objectMapper.readValue(value, new TypeReference<Map<String, String>>() { });
      return parsed == null ? Map.of() : Map.copyOf(parsed);
    } catch (JsonProcessingException exception) {
      return Map.of();
    }
  }

  private List<String> cleanValues(Collection<String> values, String label) {
    List<String> cleaned = values.stream().map(value -> cleanRequired(value, label)).toList();
    if (cleaned.stream().anyMatch(value -> value.contains("|") || value.contains(","))) {
      throw badRequest("" + label + " cannot contain ',' or '|'");
    }
    if (new HashSet<>(cleaned).size() != cleaned.size()) throw badRequest("Duplicate " + label + " values are not allowed");
    return cleaned;
  }

  private Set<String> normalizeAnswers(Question question, Set<String> rawAnswers) {
    if ("TEXT".equals(question.getType())) {
      return Set.copyOf(cleanTextAnswers(rawAnswers == null ? Set.of() : rawAnswers));
    }
    Set<String> values = new HashSet<>(cleanValues(rawAnswers == null ? Set.of() : rawAnswers, "answer"));
    return Set.copyOf(values);
  }

  private List<String> cleanTextAnswers(Collection<String> values) {
    List<String> cleaned = values.stream().map(value -> cleanRequired(value, "text answer")).toList();
    if (new HashSet<>(cleaned).size() != cleaned.size()) throw badRequest("Duplicate text answers are not allowed");
    return cleaned;
  }

  private void validateTextPatterns(List<String> patterns) {
    for (String pattern : patterns) {
      try {
        Pattern.compile(pattern);
      } catch (PatternSyntaxException exception) {
        throw badRequest("Invalid text answer regular expression: " + exception.getDescription());
      }
    }
  }

  private String normalizeVenue(String value) {
    String code = normalizeCode(value, "venue code");
    if (code.length() > 80) throw badRequest("Venue code is too long");
    return code;
  }

  private String normalizeFieldKey(String value) {
    String key = cleanRequired(value, "Registration field key").toLowerCase(Locale.ROOT);
    if (!key.matches("[a-z][a-z0-9_]{0,79}")) throw badRequest("Registration field key must use lowercase letters, digits, and underscores");
    return key;
  }

  private String normalizeCode(String value, String label) {
    String code = cleanRequired(value, label).toLowerCase(Locale.ROOT);
    if (!code.matches("[a-z0-9][a-z0-9_-]{0,79}")) throw badRequest(label + " contains unsupported characters");
    return code;
  }

  private String normalizeContact(String contact) {
    String normalized = cleanRequired(contact, "Contact").replaceAll("[\\s-]", "");
    if (normalized.length() < 3 || normalized.length() > 160) throw badRequest("Contact length is invalid");
    return normalized;
  }

  private String normalizeIdempotencyKey(String value) {
    String key = cleanRequired(value, "Idempotency key");
    if (key.length() > 160) throw badRequest("Idempotency key is too long");
    return key;
  }

  private String normalizeThemeColor(String value) {
    String color = cleanOptional(value);
    if (color == null) return null;
    if (!color.matches("#[0-9a-fA-F]{6}")) throw badRequest("Client theme color must be a six-digit hex color");
    return color.toUpperCase(Locale.ROOT);
  }

  private void configureActivityHierarchy(Activity activity, UUID parentActivityId, String activityType) {
    String type = normalizeEnum(activityType == null ? "EVENT" : activityType, ACTIVITY_TYPES, "activity type");
    if (parentActivityId != null && parentActivityId.equals(activity.getId())) {
      throw badRequest("An activity cannot be its own parent");
    }
    if ("EVENT".equals(type) && parentActivityId != null) {
      throw badRequest("A parent activity is only valid for a sub-activity");
    }
    if (!"EVENT".equals(type) && parentActivityId == null) {
      throw badRequest("Sub-activities require a parent activity");
    }
    if (parentActivityId != null) {
      Activity parent = requireActivity(parentActivityId);
      if (parent.getParentActivityId() != null) {
        throw badRequest("Only a top-level activity can own sub-activities");
      }
    }
    activity.configureHierarchy(parentActivityId, type);
  }

  private String normalizeEnum(String value, Set<String> allowed, String label) {
    String normalized = cleanRequired(value, label).toUpperCase(Locale.ROOT);
    if (!allowed.contains(normalized)) throw badRequest("Unsupported " + label);
    return normalized;
  }

  private void validateTimeRange(Instant startsAt, Instant endsAt) {
    if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
      throw badRequest("Activity end time must be after start time");
    }
  }

  private String cleanRequired(String value, String label) {
    String cleaned = cleanOptional(value);
    if (cleaned == null) throw badRequest(label + " is required");
    return cleaned;
  }

  private String cleanOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private List<String> splitPipe(String value) {
    if (value == null || value.isBlank()) return List.of();
    return List.of(value.split("\\|"));
  }

  private List<String> splitComma(String value) {
    if (value == null || value.isBlank()) return List.of();
    return List.of(value.split(","));
  }

  private List<String> readTextAnswers(String value) {
    if (value == null || value.isBlank()) return List.of();
    try {
      List<String> parsed = objectMapper.readValue(value, new TypeReference<List<String>>() { });
      return parsed == null ? List.of() : List.copyOf(parsed);
    } catch (JsonProcessingException exception) {
      return List.of();
    }
  }

  private String writeTextAnswers(Collection<String> values) {
    try {
      return objectMapper.writeValueAsString(values == null ? List.of() : values);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to store text answers", exception);
    }
  }

  private String encodeSubmittedAnswers(Question question, Set<String> answers) {
    return "TEXT".equals(question.getType()) ? writeTextAnswers(answers) : joinComma(answers);
  }

  private List<String> readSubmittedAnswers(String value) {
    if (value != null && value.stripLeading().startsWith("[")) return readTextAnswers(value);
    return splitComma(value);
  }

  private List<String> displayAnswers(Question question) {
    return "TEXT".equals(question.getType()) ? readTextAnswers(question.getTextAcceptedAnswers())
        : splitComma(question.getAnswers());
  }

  private String joinPipe(Collection<String> values) { return String.join("|", values); }
  private String joinComma(Collection<String> values) { return String.join(",", values.stream().sorted().toList()); }

  private DomainException notFound(String message) { return new DomainException(HttpStatus.NOT_FOUND, message); }
  private DomainException conflict(String message) { return new DomainException(HttpStatus.CONFLICT, message); }
  private DomainException badRequest(String message) { return new DomainException(HttpStatus.BAD_REQUEST, message); }

  private void broadcast(UUID activityId, String type, Object payload) {
    realtime.send("/topic/activities/" + activityId,
        Map.of("type", type, "payload", payload, "sentAt", Instant.now().toString()));
  }

  private ActivityResponse toActivity(Activity activity) {
    return new ActivityResponse(activity.getId(), activity.getName(), activity.getCity(), activity.getStatus(),
        activity.getStartsAt(), activity.getEndsAt(), activity.getDescription(), activity.getClientDisplayName(),
        activity.getClientThemeColor(), activity.getClientHeroImageUrl(), activity.getClientBackgroundImageUrl(),
        activity.getCreatedAt(), activity.getUpdatedAt(), activity.getParentActivityId(), activity.getActivityType(),
        activity.getActiveQuestionSetId());
  }

  private VenueResponse toVenue(Venue venue) {
    return new VenueResponse(venue.getId(), venue.getCode(), venue.getName(), venue.isEnabled(), venue.getCapacity(),
        venue.getCreatedAt());
  }

  private RegistrationFieldResponse toRegistrationField(RegistrationField field) {
    return new RegistrationFieldResponse(field.getId(), field.getFieldKey(), field.getLabel(), field.getType(),
        splitPipe(field.getOptions()), field.isRequired(), field.isEnabled(), field.getDisplayOrder());
  }

  private ParticipantResponse toParticipant(Participant participant) {
    return new ParticipantResponse(participant.getId(), participant.getName(), participant.getContact(), participant.getVenue(),
        participant.getOrganization(), participant.getScore(), participant.getStatus(), participant.getRegisteredAt(),
        readFields(participant.getRegistrationData()));
  }

  private ParticipantDetailResponse toParticipantDetail(Participant participant) {
    return new ParticipantDetailResponse(participant.getId(), participant.getName(), participant.getContact(),
        participant.getVenue(), participant.getOrganization(), participant.getScore(), participant.getStatus(),
        participant.getRegisteredAt(), participant.getLastScoreAt(), readFields(participant.getRegistrationData()),
        awards.findByActivityIdAndParticipantId(participant.getActivityId(), participant.getId()).stream()
            .map(this::toAward).toList(),
        toLotteryChance(lotteryChances.findByActivityIdAndParticipantId(participant.getActivityId(), participant.getId())
            .orElse(new LotteryChance(participant.getActivityId(), participant.getId(), 0, null))));
  }

  private QuestionResponse toQuestion(Question question) {
    return new QuestionResponse(question.getId(), question.getType(), question.getTitle(), splitPipe(question.getOptions()),
        question.getFullScore(), question.getDisplayOrder(), question.getMediaUrl(), question.isEnabled());
  }

  private QuestionAdminResponse toQuestionAdmin(Question question) {
    return new QuestionAdminResponse(question.getId(), question.getType(), question.getTitle(), splitPipe(question.getOptions()),
        splitComma(question.getAnswers()), question.getFullScore(), question.getDisplayOrder(), question.getMediaUrl(),
        question.getPartialCreditPercent(), readTextAnswers(question.getTextAcceptedAnswers()), question.getTextMatchMode(),
        question.isEnabled());
  }

  private QuestionSetResponse toQuestionSet(QuestionSet set) {
    Map<UUID, Question> byId = questions.findByActivityIdOrderByDisplayOrderAsc(set.getActivityId()).stream()
        .collect(java.util.stream.Collectors.toMap(Question::getId, question -> question));
    List<QuestionSetItemResponse> items = questionSetItems.findByQuestionSetIdOrderByDisplayOrderAsc(set.getId()).stream()
        .map(item -> {
          Question question = byId.get(item.getQuestionId());
          return new QuestionSetItemResponse(item.getQuestionId(), question == null ? "已删除题目" : question.getTitle(),
              question == null ? "UNKNOWN" : question.getType(), item.getDisplayOrder());
        }).toList();
    return new QuestionSetResponse(set.getId(), set.getActivityId(), set.getName(), set.getDescription(), set.isActive(),
        items, set.getCreatedAt(), set.getUpdatedAt());
  }

  private QuestionControlResponse toQuestionControl(Question question) {
    return new QuestionControlResponse(question.getId(), question.getType(), question.getTitle(), splitPipe(question.getOptions()),
        question.getFullScore(), question.getDisplayOrder(), question.getMediaUrl(),
        "TEXT".equals(question.getType()) ? readTextAnswers(question.getTextAcceptedAnswers()) : List.of(),
        question.getTextMatchMode(), question.isEnabled());
  }

  private AnswerResult toAnswerResult(AnswerSubmission submission, int totalScore, boolean replayed, int responseRank) {
    return new AnswerResult(submission.getId(), submission.getAwardedPoints(), totalScore, replayed,
        submission.getStatus(), submission.getFeedback(), responseRank);
  }

  private SubmissionResponse toSubmission(AnswerSubmission submission) {
    return new SubmissionResponse(submission.getId(), submission.getParticipantId(), submission.getQuestionId(),
        readSubmittedAnswers(submission.getSubmittedAnswers()), submission.getAwardedPoints(), submission.getStatus(),
        submission.getFeedback(), submission.getSubmittedAt(), submission.getGradedAt());
  }

  private ScoreLedgerResponse toScoreLedger(ScoreLedger entry) {
    return new ScoreLedgerResponse(entry.getId(), entry.getParticipantId(), entry.getQuestionId(), entry.getSubmissionId(),
        entry.getPoints(), entry.getEntryType(), entry.getNote(), entry.getCreatedAt());
  }

  private PrizePoolResponse toPrizePool(PrizePool pool) {
    return new PrizePoolResponse(pool.getId(), pool.getCode(), pool.getName(), pool.getPurpose(), pool.getDeliveryType(),
        pool.getDescription(), pool.getRedemptionUrl(), pool.getTotalQuantity(), pool.getClaimedQuantity(),
        pool.getRemainingQuantity(), pool.getMinScore(), pool.getDrawWeight(), pool.getRankFrom(), pool.getRankTo(),
        pool.isEnabled());
  }

  private AwardResponse toAward(PrizeAward award) {
    return new AwardResponse(award.getId(), award.getPrizeName(), award.getDeliveryType(), award.getStatus(),
        award.getRedemptionCode());
  }

  private AwardDetailResponse toAwardDetail(PrizeAward award) {
    Participant participant = participants.findById(award.getParticipantId()).orElse(null);
    return new AwardDetailResponse(award.getId(), award.getParticipantId(), award.getPrizePoolId(), award.getPrizeName(),
        award.getDeliveryType(), award.getStatus(), award.getRedemptionCode(), award.getRedemptionUrl(),
        award.getFulfillmentNote(), award.getAwardedAt(), award.getRedeemedAt(), award.getRedeemedBy(),
        participant == null ? null : participant.getName(), participant == null ? null : participant.getContact());
  }

  private DrawResult drawResult(PrizeAward award, boolean replayed) {
    return new DrawResult(award.getId(), award.getPrizeName(), award.getDeliveryType(), award.getStatus(),
        award.getRedemptionCode(), replayed);
  }

  private LotteryChanceResponse toLotteryChance(LotteryChance chance) {
    return new LotteryChanceResponse(chance.getParticipantId(), chance.getRemainingDraws(), chance.getGrantedDraws(),
        chance.getLastGrantReason(), chance.getUpdatedAt());
  }

  private record FieldValues(String type, List<String> options) { }
  private record QuestionValues(String type, String title, List<String> options, Set<String> answers, int fullScore,
      int displayOrder, String mediaUrl, int partialCreditPercent, List<String> textAcceptedAnswers, String textMatchMode,
      boolean enabled) { }
  private record PrizeValues(String purpose, String deliveryType, int totalQuantity, int minScore, int drawWeight,
      Integer rankFrom, Integer rankTo) { }
}
