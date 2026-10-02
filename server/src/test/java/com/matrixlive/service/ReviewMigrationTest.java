package com.matrixlive.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ReviewMigrationTest {
  @Test
  void upgradeRepairsOldScoresAndCanonicalizesOnlyApplicationMediaUrls() throws Exception {
    String url =
        "jdbc:h2:mem:review-upgrade-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("14").load().migrate();
    UUID activity = UUID.randomUUID(),
        participant = UUID.randomUUID(),
        question = UUID.randomUUID();
    UUID graded = UUID.randomUUID(), pending = UUID.randomUUID();
    String media =
        "http://localhost:8080/api/activities/"
            + activity
            + "/media/questions/"
            + UUID.randomUUID()
            + "-poster.png";
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var sql = connection.createStatement()) {
      sql.executeUpdate(
          "insert into activities(id,name,city,status,starts_at) values ('"
              + activity
              + "','Upgrade','City','LIVE',current_timestamp)");
      sql.executeUpdate(
          "insert into participants(id,activity_id,venue,contact,name,score,registered_at) values"
              + " ('"
              + participant
              + "','"
              + activity
              + "','a','contact','Player',150,current_timestamp)");
      sql.executeUpdate(
          "insert into"
              + " questions(id,activity_id,type,title,options,answers,full_score,media_url,media_urls,answer_media_urls)"
              + " values ('"
              + question
              + "','"
              + activity
              + "','TEXT','Question','','',100,'"
              + media
              + "','[\""
              + media
              + "\"]','[\"https://external.example/image.png\"]')");
      sql.executeUpdate(
          "insert into"
              + " answer_submissions(id,activity_id,participant_id,question_id,idempotency_key,submitted_answers,awarded_points,submitted_at,status)"
              + " values ('"
              + graded
              + "','"
              + activity
              + "','"
              + participant
              + "','"
              + question
              + "','graded','[\"answer\"]',100,current_timestamp,'SCORED')");
      sql.executeUpdate(
          "insert into"
              + " answer_submissions(id,activity_id,participant_id,question_id,idempotency_key,submitted_answers,awarded_points,submitted_at,status)"
              + " values ('"
              + pending
              + "','"
              + activity
              + "','"
              + participant
              + "','"
              + question
              + "','pending','[\"pending\"]',-50,current_timestamp,'PENDING_REVIEW')");
      sql.executeUpdate(
          "insert into"
              + " score_ledgers(id,activity_id,participant_id,question_id,submission_id,points,entry_type,created_at)"
              + " values ('"
              + UUID.randomUUID()
              + "','"
              + activity
              + "','"
              + participant
              + "','"
              + question
              + "','"
              + graded
              + "',150,'GRADE_ADJUSTMENT',current_timestamp)");
    }
    Flyway.configure().dataSource(url, "sa", "").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var sql = connection.createStatement()) {
      try (var rows =
          sql.executeQuery("select score from participants where id='" + participant + "'")) {
        assertTrue(rows.next());
        assertEquals(100, rows.getInt(1));
      }
      try (var rows =
          sql.executeQuery(
              "select points from score_ledgers where entry_type='REVIEW_CORRECTION'")) {
        assertTrue(rows.next());
        assertEquals(-50, rows.getInt(1));
      }
      try (var rows =
          sql.executeQuery(
              "select awarded_points from answer_submissions where id='" + pending + "'")) {
        assertTrue(rows.next());
        assertEquals(0, rows.getInt(1));
      }
      try (var rows =
          sql.executeQuery(
              "select media_url,media_urls,answer_media_urls from questions where id='"
                  + question
                  + "'")) {
        assertTrue(rows.next());
        assertTrue(rows.getString(1).startsWith("/api/activities/"));
        assertFalse(rows.getString(2).contains("localhost"));
        assertTrue(rows.getString(3).contains("external.example"));
      }
    }
    assertEquals(
        0, Flyway.configure().dataSource(url, "sa", "").load().migrate().migrationsExecuted);
  }
}
