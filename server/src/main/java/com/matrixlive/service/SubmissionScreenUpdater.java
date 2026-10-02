package com.matrixlive.service;

import com.matrixlive.repository.AnswerSubmissionRepository;
import com.matrixlive.screen.ScreenService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** The answer transaction never holds its question lock while updating screen devices. */
@Component
public class SubmissionScreenUpdater {
  private static final Logger LOG = LoggerFactory.getLogger(SubmissionScreenUpdater.class);
  private final AnswerSubmissionRepository submissions;
  private final ScreenService screens;
  private final org.springframework.transaction.support.TransactionTemplate transaction;

  public SubmissionScreenUpdater(
      AnswerSubmissionRepository submissions,
      ScreenService screens,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.submissions = submissions;
    this.screens = screens;
    this.transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
    this.transaction.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @TransactionalEventListener
  public void submitted(Submitted event) {
    try {
      transaction.executeWithoutResult(
          status ->
              screens.refreshQuestionSubmissionCount(
                  event.activityId(),
                  event.questionId(),
                  submissions.countByActivityIdAndQuestionId(
                      event.activityId(), event.questionId())));
    } catch (Exception exception) {
      LOG.warn(
          "Answer committed; screen count update will be recovered on the next display read",
          exception);
    }
  }

  public record Submitted(UUID activityId, UUID questionId) {}
}
