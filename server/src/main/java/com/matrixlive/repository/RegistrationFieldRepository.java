package com.matrixlive.repository;

import com.matrixlive.domain.RegistrationField;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationFieldRepository extends JpaRepository<RegistrationField, UUID> {
  List<RegistrationField> findByActivityIdOrderByDisplayOrderAsc(UUID activityId);
  Optional<RegistrationField> findByActivityIdAndFieldKey(UUID activityId, String fieldKey);
}
