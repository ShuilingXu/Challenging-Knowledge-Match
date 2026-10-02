package com.matrixlive.security.auth;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientCredentialRepository extends JpaRepository<ClientCredential, UUID> {
  Optional<ClientCredential> findByTokenHash(String tokenHash);

  void deleteByExpiresAtBefore(Instant instant);
}
