package com.matrixlive.security;

import com.matrixlive.repository.ActivityRepository;
import java.util.UUID;

/** Child operations and staff memberships belong to the same venue. Mothers never grant venue access. */
public final class ActivityScopes {
  private ActivityScopes() { }
  public static UUID membershipActivity(ActivityRepository activities, UUID activityId) {
    UUID parent=activities.findById(activityId).map(item -> item.getParentActivityId()).orElse(null);
    return parent!=null && activities.findById(parent).filter(item -> "VENUE".equals(item.getActivityType())).isPresent()
        ? parent : activityId;
  }
}
