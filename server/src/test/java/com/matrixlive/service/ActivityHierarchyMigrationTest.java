package com.matrixlive.service;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ActivityHierarchyMigrationTest {
  @Test
  void keepsLegacyQuizRostersAndScoresAndSharesOnlyEmptyQuizzes() throws Exception {
    String url = "jdbc:h2:mem:hierarchy-upgrade-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("20").load().migrate();
    UUID main = UUID.randomUUID(), legacy = UUID.randomUUID(), empty = UUID.randomUUID(), lottery = UUID.randomUUID(), person = UUID.randomUUID();
    try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
      sql.executeUpdate("insert into activities(id,name,city,status,activity_type) values ('" + main + "','Main','City','LIVE','EVENT')");
      for (var id : java.util.List.of(legacy, empty, lottery)) {
        sql.executeUpdate("insert into activities(id,name,city,status,activity_type,parent_activity_id) values ('" + id + "','Child','City','LIVE','" + (id.equals(lottery) ? "LOTTERY" : "QUIZ") + "','" + main + "')");
      }
      sql.executeUpdate("insert into participants(id,activity_id,name,contact,venue,score) values ('" + person + "','" + legacy + "','Legacy','legacy','hall',75)");
    }
    Flyway.configure().dataSource(url, "sa", "").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
      try (var rows = sql.executeQuery("select shared_participants from activities where id='" + legacy + "'")) { assertTrue(rows.next()); assertFalse(rows.getBoolean(1)); }
      for (var id : java.util.List.of(empty, lottery)) {
        try (var rows = sql.executeQuery("select shared_participants from activities where id='" + id + "'")) { assertTrue(rows.next()); assertTrue(rows.getBoolean(1)); }
      }
      try (var rows = sql.executeQuery("select activity_id,score from participants where id='" + person + "'")) { assertTrue(rows.next()); assertEquals(legacy.toString(), rows.getString(1)); assertEquals(75, rows.getInt(2)); }
    }
  }
}
