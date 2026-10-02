package com.matrixlive.repository;

import com.matrixlive.domain.Activity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select a from Activity a where a.id = :id")
  java.util.Optional<Activity> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
  List<Activity> findByStatusOrderByStartsAtAsc(String status);
  List<Activity> findByParentActivityId(UUID parentActivityId);
}
