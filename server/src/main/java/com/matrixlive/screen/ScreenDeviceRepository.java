package com.matrixlive.screen;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenDeviceRepository extends JpaRepository<ScreenDevice, UUID> {
  List<ScreenDevice> findByActivityIdOrderByLastSeenAtDesc(UUID activityId);
  List<ScreenDevice> findByActivityIdAndCurrentTemplateId(UUID activityId, UUID currentTemplateId);
  Optional<ScreenDevice> findByIdAndActivityId(UUID id, UUID activityId);
  Optional<ScreenDevice> findByIdAndDeviceTokenHash(UUID id, String deviceTokenHash);
}
