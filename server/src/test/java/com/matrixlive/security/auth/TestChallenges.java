package com.matrixlive.security.auth;

import java.util.UUID;

public final class TestChallenges {
  public static UUID create(HumanChallengeRepository repository) {
    UUID id = UUID.randomUUID();
    repository.saveAndFlush(new HumanChallenge(id, AuthService.hash("ABCDE"), "127.0.0.1"));
    return id;
  }
}
