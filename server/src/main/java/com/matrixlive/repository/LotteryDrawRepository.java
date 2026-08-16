package com.matrixlive.repository;

import com.matrixlive.domain.LotteryDraw;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotteryDrawRepository extends JpaRepository<LotteryDraw, UUID> {
  Optional<LotteryDraw> findByActivityIdAndIdempotencyKey(UUID activityId, String idempotencyKey);
}
