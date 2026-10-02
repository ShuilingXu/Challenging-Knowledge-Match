package com.matrixlive.screen;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface ScreenDeviceRepository extends JpaRepository<ScreenDevice, UUID> {
  List<ScreenDevice> findByActivityIdOrderByLastSeenAtDesc(UUID activityId);
  List<ScreenDevice> findByActivityIdAndCurrentTemplateId(UUID activityId, UUID currentTemplateId);
  Optional<ScreenDevice> findByIdAndActivityId(UUID id, UUID activityId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<ScreenDevice> findByIdAndDeviceTokenHash(UUID id, String deviceTokenHash);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from ScreenDevice d where d.activityId = :activityId order by d.id")
  List<ScreenDevice> findForDisplayUpdate(UUID activityId);
}
