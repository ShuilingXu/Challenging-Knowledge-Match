package com.matrixlive.repository;

import com.matrixlive.domain.Participant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {
  Optional<Participant> findByActivityIdAndVenueAndContact(UUID activityId, String venue, String contact);
  List<Participant> findByActivityId(UUID activityId);
  List<Participant> findByActivityIdAndVenue(UUID activityId, String venue);
  long countByActivityIdAndVenue(UUID activityId, String venue);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select participant from Participant participant where participant.id = :id")
  Optional<Participant> findByIdForUpdate(@Param("id") UUID id);
}
