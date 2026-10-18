package com.matrixlive.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.matrixlive.domain.Activity;
import com.matrixlive.repository.ActivityRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Short-lived cache for the activity fields the authorization and routing layers read on every
 * request. Those fields only change through staff configuration, so a two second window keeps a
 * feature switch effectively immediate while removing four to seven identical lookups from the hot
 * path of a single participant request.
 */
@Component
public class ActivityAccessCache {
  /** The subset of an activity that request authorization needs; deliberately not the entity. */
  public record Access(UUID id, boolean featureEnabled, String activityType,
      boolean sharedParticipants, UUID parentActivityId) { }

  private final ActivityRepository activities;
  private final Cache<UUID, Optional<Access>> cache;
  private final boolean cachingEnabled;

  public ActivityAccessCache(ActivityRepository activities,
      @Value("${app.cache.activity-ttl-seconds:2}") long ttlSeconds,
      @Value("${app.cache.activity-max-entries:2000}") long maxEntries) {
    this.activities = activities;
    this.cachingEnabled = ttlSeconds > 0;
    this.cache = Caffeine.newBuilder()
        .maximumSize(Math.max(1, maxEntries))
        .expireAfterWrite(Duration.ofSeconds(Math.max(1, ttlSeconds)))
        .build();
  }

  public Optional<Access> access(UUID activityId) {
    if (activityId == null) return Optional.empty();
    // A zero TTL disables the window entirely so tests observe every write immediately.
    return cachingEnabled ? cache.get(activityId, this::load) : load(activityId);
  }

  private Optional<Access> load(UUID activityId) {
    return activities.findById(activityId).map(ActivityAccessCache::snapshot);
  }

  private static Access snapshot(Activity activity) {
    return new Access(activity.getId(), activity.isFeatureEnabled(), activity.getActivityType(),
        activity.isSharedParticipants(), activity.getParentActivityId());
  }

  /** Mirrors {@link ActivityScopes#membershipActivity} using the cached snapshots. */
  public UUID membershipActivity(UUID activityId) {
    if (activityId == null) return null;
    UUID parentId = access(activityId).map(Access::parentActivityId).orElse(null);
    if (parentId == null) return activityId;
    boolean parentIsVenue = access(parentId)
        .map(parent -> "VENUE".equals(parent.activityType()))
        .orElse(false);
    return ActivityScopes.resolve(activityId, parentId, parentIsVenue);
  }

  public void invalidate(UUID activityId) {
    if (activityId != null) cache.invalidate(activityId);
  }
}
