package com.matrixlive.screen;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenActivityStateRepository extends JpaRepository<ScreenActivityState, UUID> { }
