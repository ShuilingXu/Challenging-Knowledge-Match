package com.matrixlive.service;

import com.matrixlive.repository.AnswerSubmissionRepository;
import com.matrixlive.screen.ScreenService;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The answer transaction never holds its question lock while updating screen devices.
 *
 * <p>Refreshes are also coalesced per activity: a full scoreboard recompute (participants plus one
 * submission query per answered participant replaced by a batch load) ran once per answer, so fifty
 * answers in a few seconds meant fifty full recomputes on the request threads. A trailing refresh
 * per window produces the same final display state and lets the answer response return immediately.
 * Screen reads still recover the submission count themselves, so a dropped window is self-healing.
 */
@Component
public class SubmissionScreenUpdater {
  private static final Logger LOG = LoggerFactory.getLogger(SubmissionScreenUpdater.class);
  private final AnswerSubmissionRepository submissions;
  private final ScreenService screens;
  private final org.springframework.beans.factory.ObjectProvider<ActivityService> activities;
  private final org.springframework.transaction.support.TransactionTemplate transaction;
  private final long windowMillis;
  private final ScheduledExecutorService scheduler;
  private final ConcurrentHashMap<UUID, Pending> pending = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

  public SubmissionScreenUpdater(
      AnswerSubmissionRepository submissions,
      ScreenService screens,
      org.springframework.beans.factory.ObjectProvider<ActivityService> activities,
      org.springframework.transaction.PlatformTransactionManager manager,
      @Value("${app.screens.refresh-window-ms:300}") long windowMillis) {
    this.submissions = submissions;
    this.screens = screens;
    this.activities = activities;
    this.windowMillis = windowMillis;
    this.transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
    this.transaction.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.scheduler = windowMillis > 0
        ? Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "screen-refresh");
            thread.setDaemon(true);
            return thread;
          })
        : null;
  }

  @TransactionalEventListener
  public void submitted(Submitted event) {
    schedule(event.activityId(), event.questionId(), true);
  }

  @TransactionalEventListener
  public void changed(LiveDataChanged event) {
    schedule(event.activityId(), event.questionId(), false);
  }

  private void schedule(UUID activityId, UUID questionId, boolean countChanged) {
    if (activityId == null) return;
    // Merging keeps the newest question while remembering that a count refresh is still owed.
    pending.merge(activityId, new Pending(questionId, countChanged),
        (existing, incoming) -> new Pending(incoming.questionId(), existing.countChanged() || incoming.countChanged()));
    if (scheduler == null) {
      flush(activityId);
      return;
    }
    try {
      ScheduledFuture<?> next = scheduler.schedule(() -> flush(activityId), windowMillis, TimeUnit.MILLISECONDS);
      ScheduledFuture<?> previous = scheduled.put(activityId, next);
      // Debounce: the last answer in the burst decides when the screen is actually refreshed.
      if (previous != null) previous.cancel(false);
    } catch (RejectedExecutionException exception) {
      flush(activityId);
    }
  }

  private void flush(UUID activityId) {
    scheduled.remove(activityId);
    Pending state = pending.remove(activityId);
    if (state == null) return;
    try {
      transaction.executeWithoutResult(
          status -> {
            if (state.countChanged()) {
              screens.refreshQuestionSubmissionCount(
                  activityId, state.questionId(),
                  submissions.countByActivityIdAndQuestionId(activityId, state.questionId()));
            }
            ActivityService service = activities.getObject();
            screens.refreshLiveData(activityId, () -> service.scoreboard(activityId), state.questionId(),
                () -> service.screenResponses(activityId, state.questionId()));
          });
    } catch (Exception exception) {
      LOG.warn("Screen refresh failed for activity {}; the next display read recovers the count", activityId, exception);
    }
  }

  @PreDestroy
  void shutdown() {
    if (scheduler == null) return;
    scheduler.shutdownNow();
  }

  private record Pending(UUID questionId, boolean countChanged) { }

  public record Submitted(UUID activityId, UUID questionId) {}
  public record LiveDataChanged(UUID activityId, UUID questionId) {}
}
