package com.matrixlive.security;

import com.matrixlive.domain.Activity;
import com.matrixlive.repository.ActivityRepository;
import java.util.UUID;

/** Child operations and staff memberships belong to the same venue. Mothers never grant venue access. */
public final class ActivityScopes {
  private ActivityScopes() { }

  public static UUID membershipActivity(ActivityRepository activities, UUID activityId) {
    UUID parent = activities.findById(activityId).map(Activity::getParentActivityId).orElse(null);
    boolean parentIsVenue = parent != null
        && activities.findById(parent).filter(item -> "VENUE".equals(item.getActivityType())).isPresent();
    return resolve(activityId, parent, parentIsVenue);
  }

  /** The single source of truth for the venue-scope rule, shared with the cached lookup. */
  public static UUID resolve(UUID activityId, UUID parentId, boolean parentIsVenue) {
    return parentId != null && parentIsVenue ? parentId : activityId;
  }
}
