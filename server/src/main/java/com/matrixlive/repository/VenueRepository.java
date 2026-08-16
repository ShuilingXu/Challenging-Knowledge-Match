package com.matrixlive.repository;

import com.matrixlive.domain.Venue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
  Optional<Venue> findByActivityIdAndCode(UUID activityId, String code);
  List<Venue> findByActivityIdOrderByNameAsc(UUID activityId);
}
