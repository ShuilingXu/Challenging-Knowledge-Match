package com.matrixlive.security.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityMembershipRepository extends JpaRepository<ActivityMembership, UUID> {
  Optional<ActivityMembership> findByUserIdAndActivityId(UUID userId, UUID activityId);
  boolean existsByUserIdAndRole(UUID userId, UserRole role);
  List<ActivityMembership> findByActivityIdOrderByCreatedAtAsc(UUID activityId);
  void deleteByUserIdAndActivityId(UUID userId, UUID activityId);
}
