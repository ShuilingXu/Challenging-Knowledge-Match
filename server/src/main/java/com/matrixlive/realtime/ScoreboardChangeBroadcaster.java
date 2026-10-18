package com.matrixlive.realtime;

import com.matrixlive.domain.Activity;
import com.matrixlive.repository.ActivityRepository;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Collapses the per-answer score fan-out into at most one notification per activity per window.
 *
 * <p>Every answer used to emit its own message to the activity topic plus one to the parent and to
 * every shared sibling, so fifty simultaneous answers on a venue produced hundreds of messages.
 * Each recipient client then reloads on every message, which is what turned a burst of answers into
 * a burst of HTTP requests. One trailing notification per window carries the same information: the
 * clients only need to know that the scores changed.
 *
 * <p>A window of zero (the test default) bypasses the scheduler and publishes inline so tests stay
 * deterministic.
 */
@Component
public class ScoreboardChangeBroadcaster {
  private static final Logger LOG = LoggerFactory.getLogger(ScoreboardChangeBroadcaster.class);
  private final RealtimeEventBus realtime;
  private final ActivityRepository activities;
  private final long windowMillis;
  private final ScheduledExecutorService scheduler;
  private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

  public ScoreboardChangeBroadcaster(RealtimeEventBus realtime, ActivityRepository activities,
      @Value("${app.realtime.score-broadcast-window-ms:250}") long windowMillis) {
    this.realtime = realtime;
    this.activities = activities;
    this.windowMillis = windowMillis;
    this.scheduler = windowMillis > 0
        ? Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "score-broadcast");
            thread.setDaemon(true);
            return thread;
          })
        : null;
  }

  /** Event types whose only meaning for other clients is "the scores moved". */
  public static boolean isScoreboardEvent(String type) {
    return type.startsWith("answer.") || type.startsWith("score.")
        || type.equals("participant.registered") || type.startsWith("buzz.");
  }

  public void scoreboardChanged(UUID activityId) {
    if (activityId == null) return;
    if (scheduler == null) {
      flush(activityId);
      return;
    }
    if (!pending.add(activityId)) return;
    try {
      scheduler.schedule(() -> flush(activityId), windowMillis, TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException exception) {
      // Shutting down: deliver once inline rather than dropping the notification.
      flush(activityId);
    }
  }

  private void flush(UUID activityId) {
    pending.remove(activityId);
    try {
      Map<String, String> payload = Map.of("type", "score.updated", "sentAt", Instant.now().toString());
      for (UUID target : fanOutTargets(activityId)) {
        realtime.send("/topic/activities/" + target, payload);
      }
    } catch (Exception exception) {
      LOG.warn("Scoreboard broadcast failed for activity {}", activityId, exception);
    }
  }

  /** Keep the original fan-out rule: self, the venue root, and every shared sibling. */
  private Set<UUID> fanOutTargets(UUID activityId) {
    Set<UUID> targets = new LinkedHashSet<>();
    targets.add(activityId);
    Activity source = activities.findById(activityId).orElse(null);
    if (source == null) return targets;
    UUID root = source.isSharedParticipants() ? source.getParentActivityId()
        : "VENUE".equals(source.getActivityType()) ? activityId : null;
    if (root == null) return targets;
    targets.add(root);
    for (Activity child : activities.findByParentActivityId(root)) {
      if (child.isSharedParticipants()) targets.add(child.getId());
    }
    return targets;
  }

  @PreDestroy
  void shutdown() {
    if (scheduler == null) return;
    scheduler.shutdownNow();
  }
}
