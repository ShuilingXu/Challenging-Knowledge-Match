package com.matrixlive.service;

import java.util.Set;
import java.util.stream.Collectors;

public final class AnswerScorer {
  private AnswerScorer() { }

  public static int score(Set<String> submitted, Set<String> expected, int fullScore) {
    return score("MULTIPLE", submitted, expected, fullScore, 40);
  }

  public static int score(String type, Set<String> submitted, Set<String> expected, int fullScore,
      int partialCreditPercent) {
    Set<String> normalizedSubmitted = normalize(submitted);
    Set<String> normalizedExpected = normalize(expected);
    if (normalizedSubmitted.equals(normalizedExpected)) return fullScore;
    if ("MULTIPLE".equals(type) && !normalizedSubmitted.isEmpty() && normalizedExpected.containsAll(normalizedSubmitted)) {
      return Math.round(fullScore * (partialCreditPercent / 100.0f));
    }
    return 0;
  }

  public static Set<String> parse(String answers) {
    if (answers == null || answers.isBlank()) return Set.of();
    return java.util.Arrays.stream(answers.split(",")).filter(answer -> !answer.isBlank()).collect(Collectors.toUnmodifiableSet());
  }

  private static Set<String> normalize(Set<String> answers) {
    if (answers == null) return Set.of();
    return answers.stream().filter(value -> value != null && !value.isBlank())
        .map(value -> value.trim().toUpperCase()).collect(Collectors.toUnmodifiableSet());
  }
}
