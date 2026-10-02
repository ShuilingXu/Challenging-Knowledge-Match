package com.matrixlive.service;

import com.matrixlive.api.ApiModels.ChangeActivityStatusRequest;
import java.util.UUID;

/** Existing answer tests must arrange a live event before opening a question. */
public final class QuizTestSupport {
  private QuizTestSupport() { }
  public static com.matrixlive.api.ApiModels.ParticipantResponse register(ActivityService service, UUID activityId,
      String venue, com.matrixlive.api.ApiModels.RegisterParticipantRequest request) {
    var activity = service.activity(activityId);
    UUID scopeId = activity.participantActivityId() == null ? activityId : activity.participantActivityId();
    if ("DRAFT".equals(service.activity(scopeId).status())) {
      service.changeActivityStatus(scopeId, new ChangeActivityStatusRequest("REGISTRATION_OPEN"));
    }
    return service.register(activityId, venue, request);
  }
  public static void start(ActivityService service, UUID activityId) {
    var activity = service.activity(activityId);
    if (activity.parentActivityId() != null) {
      var parent = service.activity(activity.parentActivityId());
      if ("DRAFT".equals(parent.status()) || "REGISTRATION_OPEN".equals(parent.status())) {
        service.changeActivityStatus(parent.id(), new ChangeActivityStatusRequest("LIVE"));
      }
    }
    if ("DRAFT".equals(activity.status()) || "REGISTRATION_OPEN".equals(activity.status())) {
      service.changeActivityStatus(activityId, new ChangeActivityStatusRequest("LIVE"));
    }
  }
}
