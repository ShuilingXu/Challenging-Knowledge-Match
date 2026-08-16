package com.matrixlive.screen;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenTemplateRepository extends JpaRepository<ScreenTemplate, UUID> {
  List<ScreenTemplate> findByActivityIdOrderByUpdatedAtDesc(UUID activityId);
}
