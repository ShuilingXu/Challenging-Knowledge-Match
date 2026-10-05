package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve historic ids, scores and references in one explicitly named legacy venue. */
public class V24__move_legacy_operations_to_venue extends BaseJavaMigration {
  @Override public void migrate(Context context) throws Exception {
    Connection c=context.getConnection();
    List<String> roots=new ArrayList<>();
    try (var s=c.createStatement(); var rows=s.executeQuery("select id from activities where activity_type='EVENT' and parent_activity_id is null")) {
      while (rows.next()) roots.add(rows.getString(1));
    }
    for (String root : roots) {
      UUID venue=UUID.randomUUID();
      execute(c, "insert into activities(id,name,city,status,starts_at,ends_at,description,parent_activity_id,activity_type,created_at,updated_at,active_question_set_id,client_display_name,client_theme_color,client_hero_image_url,client_background_image_url,control_stage,control_question_id,control_seconds,control_updated_at,question_opened_at) select ?,substring(name,1,145) || ' - 历史会场',city,status,starts_at,ends_at,description,id,'VENUE',created_at,updated_at,active_question_set_id,client_display_name,client_theme_color,client_hero_image_url,client_background_image_url,control_stage,control_question_id,control_seconds,control_updated_at,question_opened_at from activities where id=?", venue, UUID.fromString(root));
      // Move operational rows without changing question, participant, award or device ids.
      for (String table : List.of("venues", "registration_fields", "participants", "questions", "question_sets", "answer_submissions", "score_ledgers", "prize_pools", "prize_awards", "lottery_chances", "lottery_draws", "buzz_entries", "screen_templates", "screen_devices", "screen_activity_states", "client_credentials")) {
        execute(c, "update " + table + " set activity_id=? where activity_id=?", venue, UUID.fromString(root));
      }
      execute(c, "update activities set parent_activity_id=? where parent_activity_id=? and id<>?", venue, UUID.fromString(root), venue);
      // Old mother memberships become explicit memberships in the historical venue too.
      List<Object[]> members=new ArrayList<>();
      try (var s=c.prepareStatement("select user_id,role from activity_memberships where activity_id=?")) {
        s.setObject(1, UUID.fromString(root));
        try (var rows=s.executeQuery()) { while(rows.next()) members.add(new Object[]{rows.getObject(1),rows.getString(2)}); }
      }
      for (Object[] member : members) execute(c, "insert into activity_memberships(id,activity_id,user_id,role,created_at) values(?,?,?,?,current_timestamp)", UUID.randomUUID(),venue,member[0],member[1]);
      List<Object[]> childMembers=new ArrayList<>();
      try (var s=c.prepareStatement("select m.user_id,m.role from activity_memberships m join activities a on a.id=m.activity_id where a.parent_activity_id=?")) {
        s.setObject(1,venue); try(var rows=s.executeQuery()) { while(rows.next()) childMembers.add(new Object[]{rows.getObject(1),rows.getString(2)}); }
      }
      for(Object[] member : childMembers) {
        boolean exists;
        try(var s=c.prepareStatement("select id from activity_memberships where activity_id=? and user_id=?")) {
          s.setObject(1,venue); s.setObject(2,member[0]); try(var rows=s.executeQuery()) { exists=rows.next(); }
        }
        if (!exists) execute(c,"insert into activity_memberships(id,activity_id,user_id,role,created_at) values(?,?,?,?,current_timestamp)",UUID.randomUUID(),venue,member[0],member[1]);
        else if ("ACTIVITY_ADMIN".equals(member[1])) execute(c,"update activity_memberships set role='ACTIVITY_ADMIN' where activity_id=? and user_id=?",venue,member[0]);
      }
      execute(c, "update activities set active_question_set_id=null,control_stage='LOBBY',control_question_id=null,control_seconds=0 where id=?", UUID.fromString(root));
      // Republish historical questions as independent master copies, preserving operational originals.
      List<UUID> ids=new ArrayList<>();
      try (var s=c.prepareStatement("select id from questions where activity_id=?")) {
        s.setObject(1,venue); try(var rows=s.executeQuery()) { while(rows.next()) ids.add(UUID.fromString(rows.getString(1))); }
      }
      for (UUID id : ids) {
        UUID source=UUID.randomUUID();
        execute(c, "insert into questions(id,activity_id,type,title,options,answers,full_score,display_order,enabled,media_url,media_urls,answer_media_urls,partial_credit_percent,text_accepted_answers,text_match_mode,version) select ?,?,type,title,options,answers,full_score,display_order,enabled,media_url,media_urls,answer_media_urls,partial_credit_percent,text_accepted_answers,text_match_mode,0 from questions where id=?", source,UUID.fromString(root),id);
        execute(c,"update questions set source_question_id=? where id=?",source,id);
      }
    }
  }
  private void execute(Connection c, String sql, Object... values) throws Exception {
    try (PreparedStatement s=c.prepareStatement(sql)) {
      for(int i=0;i<values.length;i++) s.setObject(i+1,values[i]);
      s.executeUpdate();
    }
  }
}
