package com.matrixlive.service;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class VenueUpgradeMigrationTest {
  @Test void preservesHistoricPeopleQuestionsAndScoresAndCreatesMasterCopies() throws Exception {
    String url="jdbc:h2:mem:venue-upgrade-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url,"sa","").target("22").load().migrate();
    UUID main=UUID.randomUUID(),child=UUID.randomUUID(),person=UUID.randomUUID(),question=UUID.randomUUID();
    try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
      s.executeUpdate("insert into activities(id,name,city,status,activity_type) values('"+main+"','Event','City','LIVE','EVENT')");
      s.executeUpdate("insert into activities(id,name,city,status,activity_type,parent_activity_id,shared_participants) values('"+child+"','Quiz','City','LIVE','QUIZ','"+main+"',true)");
      s.executeUpdate("insert into participants(id,activity_id,name,contact,venue,score) values('"+person+"','"+main+"','Player','contact','south',75)");
      s.executeUpdate("insert into questions(id,activity_id,type,title,options,answers,full_score) values('"+question+"','"+main+"','SINGLE','Question','A|B','A',10)");
    }
    Flyway.configure().dataSource(url,"sa","").load().migrate();
    try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
      String venue;
      try(var rows=s.executeQuery("select id,activity_type from activities where parent_activity_id='"+main+"'")) {
        assertTrue(rows.next()); venue=rows.getString(1);assertEquals("VENUE",rows.getString(2));assertFalse(rows.next());
      }
      try(var rows=s.executeQuery("select activity_id,score from participants where id='"+person+"'")) { assertTrue(rows.next());assertEquals(venue,rows.getString(1));assertEquals(75,rows.getInt(2)); }
      try(var rows=s.executeQuery("select parent_activity_id from activities where id='"+child+"'")) { assertTrue(rows.next());assertEquals(venue,rows.getString(1)); }
      String source;
      try(var rows=s.executeQuery("select activity_id,source_question_id from questions where id='"+question+"'")) { assertTrue(rows.next());assertEquals(venue,rows.getString(1));source=rows.getString(2);assertNotNull(source);assertNotEquals(question.toString(),source); }
      try(var rows=s.executeQuery("select activity_id,title from questions where id='"+source+"'")) { assertTrue(rows.next());assertEquals(main.toString(),rows.getString(1));assertEquals("Question",rows.getString(2)); }
    }
  }
}
