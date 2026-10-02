package com.matrixlive.security.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByUsernameIgnoreCase(String username);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from UserAccount account where account.id = :id")
  Optional<UserAccount> findByIdForUpdate(UUID id);
}
