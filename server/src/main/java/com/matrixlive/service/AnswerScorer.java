package com.matrixlive.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
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

  public static boolean matchesText(String submitted, List<String> acceptedAnswers, String matchMode) {
    if (submitted == null || acceptedAnswers == null || acceptedAnswers.isEmpty()) return false;
    return acceptedAnswers.stream().anyMatch(expected -> matchesTextAnswer(submitted, expected, matchMode));
  }

  private static boolean matchesTextAnswer(String submitted, String expected, String matchMode) {
    if (expected == null || expected.isBlank()) return false;
    if ("REGEX".equals(matchMode)) {
      try {
        return Pattern.compile(expected, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            .matcher(submitted.trim()).matches();
      } catch (PatternSyntaxException exception) {
        return false;
      }
    }
    String normalizedSubmitted = normalizeText(submitted);
    String normalizedExpected = normalizeText(expected);
    return !normalizedSubmitted.isEmpty() && !normalizedExpected.isEmpty()
        && (normalizedSubmitted.contains(normalizedExpected) || normalizedExpected.contains(normalizedSubmitted));
  }

  private static String normalizeText(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static Set<String> normalize(Set<String> answers) {
    if (answers == null) return Set.of();
    return answers.stream().filter(value -> value != null && !value.isBlank())
        .map(value -> value.trim().toUpperCase()).collect(Collectors.toUnmodifiableSet());
  }
}
