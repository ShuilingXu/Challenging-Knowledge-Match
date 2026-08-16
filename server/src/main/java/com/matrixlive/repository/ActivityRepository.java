package com.matrixlive.repository;

import com.matrixlive.domain.Activity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
  List<Activity> findByStatusOrderByStartsAtAsc(String status);
}
