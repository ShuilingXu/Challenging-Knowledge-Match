package com.matrixlive.security;

import com.matrixlive.api.ApiModels.DrawRequest;
import com.matrixlive.api.ApiModels.SubmitAnswerRequest;
import com.matrixlive.repository.ActivityRepository;
import java.util.UUID;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Ensures a participant token cannot be used to submit or draw on another participant's behalf. */
@Aspect
@Component
public class ParticipantActionGuard {
  private final ActivityRepository activities;

  public ParticipantActionGuard(ActivityRepository activities) { this.activities = activities; }
  @Before("execution(* com.matrixlive.service.ActivityService.submitAnswer(..)) && args(activityId, request)")
  public void verifyAnswerOwnership(UUID activityId, SubmitAnswerRequest request) {
    verify(activityId, request.participantId());
  }

  @Before("execution(* com.matrixlive.service.ActivityService.draw(..)) && args(activityId, request)")
  public void verifyDrawOwnership(UUID activityId, DrawRequest request) {
    verify(activityId, request.participantId());
  }

  private void verify(UUID activityId, UUID participantId) {
    Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null
        : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof AuthenticatedPrincipal current && current.isParticipant()) {
      boolean sameScope = activityId.equals(current.activityId()) || activities.findById(activityId)
          .filter(item -> "LOTTERY".equals(item.getActivityType()) && current.activityId().equals(item.getParentActivityId()))
          .isPresent();
      if (!sameScope || !participantId.equals(current.participantId())) {
        throw new AccessDeniedException("Participant token cannot act for another participant");
      }
    }
  }
}
