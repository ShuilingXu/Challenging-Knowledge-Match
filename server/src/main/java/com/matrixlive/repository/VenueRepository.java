package com.matrixlive.repository;

import com.matrixlive.domain.Venue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
  Optional<Venue> findByActivityIdAndCode(UUID activityId, String code);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from Venue v where v.activityId = :activityId and v.code = :code")
  Optional<Venue> findForRegistration(UUID activityId, String code);
  List<Venue> findByActivityIdOrderByNameAsc(UUID activityId);
}
