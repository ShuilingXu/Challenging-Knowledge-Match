package com.matrixlive.service;

import com.matrixlive.domain.*;
import com.matrixlive.repository.*;
import com.matrixlive.security.AuthenticatedPrincipal;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinService {
  private final JdbcTemplate db;
  private final ActivityRepository activities;
  private final ParticipantRepository participants;
  private final VenueRepository venues;
  public CheckinService(JdbcTemplate db, ActivityRepository activities, ParticipantRepository participants, VenueRepository venues) {
    this.db=db; this.activities=activities; this.participants=participants; this.venues=venues;
  }
  public record Entry(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=160) String contact,
      @NotBlank String activity, @NotBlank String type, @Size(max=1000) String note) {}
  public record Redeem(@NotBlank @Size(max=64) String token, @NotNull UUID requestId) {}
  public record Ticket(UUID id, UUID activityId, UUID participantId, String name, String contact, String activityName,
      String type, String note, String token, int checkinCount, Instant lastCheckinAt) {}
  private DomainException error(String text) { return new DomainException(HttpStatus.CONFLICT,text); }
  public Activity scope(UUID id) {
    Activity a=activities.findById(id).orElseThrow(()->error("场次不存在"));
    if(a.isSharedParticipants() && a.getParentActivityId()!=null) a=activities.findById(a.getParentActivityId()).orElseThrow();
    if("EVENT".equals(a.getActivityType()) || "TURTLE_SOUP".equals(a.getActivityType())) throw error("请选择接受报名的场次");
    return a;
  }
  private static final String SELECT_TICKETS = "select t.*, p.name, p.contact, a.name as activity_name from checkin_tickets t join participants p on p.id=t.participant_id join activities a on a.id=t.activity_id where t.activity_id=?";
  private Ticket ticketRow(java.sql.ResultSet r, int row) throws java.sql.SQLException {
    return new Ticket(r.getObject("id",UUID.class),r.getObject("activity_id",UUID.class),r.getObject("participant_id",UUID.class),r.getString("name"),r.getString("contact"),r.getString("activity_name"),r.getString("ticket_type"),r.getString("note"),r.getString("token"),r.getInt("checkin_count"),r.getTimestamp("last_checkin_at")==null?null:r.getTimestamp("last_checkin_at").toInstant());
  }
  @Transactional(readOnly=true)
  public List<Ticket> list(UUID id) {
    return db.query(SELECT_TICKETS + " order by t.created_at,t.id", this::ticketRow, scope(id).getId());
  }
  @Transactional(readOnly=true)
  public Ticket ticket(UUID id, String token) {
    return db.query(SELECT_TICKETS + " and t.token=?", this::ticketRow, scope(id).getId(),token).stream().findFirst()
      .orElseThrow(()->new DomainException(HttpStatus.NOT_FOUND,"签到码无效或不属于当前场次"));
  }
  @Transactional
  public List<Ticket> create(UUID id,List<Entry> entries) {
    Activity a=scope(id);
    activities.findForUpdate(a.getId()).orElseThrow();
    if(entries.isEmpty() || entries.size()>5000) throw error("每次请导入 1 至 5000 行");
    String venue=venues.findByActivityIdOrderByNameAsc(a.getId()).stream().filter(Venue::isEnabled).map(Venue::getCode).findFirst().orElseThrow(()->error("请先配置一个启用的报名入口"));
    Set<String> seen=new HashSet<>();
    for(int i=0;i<entries.size();i++) {
      Entry e=entries.get(i);
      String contact=e.contact().replaceAll("[\\s-]","").trim();
      if(e.name().isBlank() || contact.length()<3 || contact.length()>160) throw error("第 "+(i+2)+" 行昵称或联系方式不正确");
      if(!e.activity().trim().equals(a.getName()) && !e.activity().trim().equals(a.getId().toString())) throw error("第 "+(i+2)+" 行场次与所选场次不符（可填写场次 ID）");
      String type=switch(e.type().trim()) { case "单次","SINGLE" -> "SINGLE"; case "多次","MULTI" -> "MULTI"; default -> throw error("第 "+(i+2)+" 行类型必须为单次或多次"); };
      if(!seen.add(contact)) throw error("第 "+(i+2)+" 行联系方式重复，整批未导入");
      Participant p=participants.findByActivityIdAndContact(a.getId(),contact).stream().findFirst().orElse(null);
      if(p==null) p=participants.saveAndFlush(new Participant(a.getId(),venue,contact,e.name().trim(),null));
      else { p.updateProfile(e.name().trim(),null,null,null,null); participants.flush(); }
      if(!db.queryForList("select id from checkin_tickets where activity_id=? and participant_id=?",a.getId(),p.getId()).isEmpty()) throw error("第 "+(i+2)+" 行已存在签到票，整批未导入");
      db.update("insert into checkin_tickets(id,activity_id,participant_id,token,ticket_type,note,created_at) values(?,?,?,?,?,?,?)",UUID.randomUUID(),a.getId(),p.getId(),UUID.randomUUID().toString()+UUID.randomUUID().toString().replace("-","").substring(0,16),type,e.note()==null?"":e.note().trim(),java.sql.Timestamp.from(Instant.now()));
    }
    return list(id);
  }
  @Transactional
  public Ticket redeem(UUID id,Redeem request) {
    UUID activity=scope(id).getId();
    var found=db.queryForList("select id,checkin_count,ticket_type,participant_id from checkin_tickets where activity_id=? and token=? for update",activity,request.token());
    if(found.isEmpty()) throw error("签到码无效或不属于当前场次");
    var t=found.getFirst(); UUID ticket=(UUID)t.get("id");
    if(!db.queryForList("select id from ticket_redemptions where ticket_id=? and request_id=?",ticket,request.requestId()).isEmpty()) return ticket(id,request.token());
    if("SINGLE".equals(t.get("ticket_type")) && ((Number)t.get("checkin_count")).intValue()>0) throw error("此单次票已核销，不能重复入场");
    Participant p=participants.findById((UUID)t.get("participant_id")).orElseThrow();
    if(!"ACTIVE".equals(p.getStatus())) throw error("该参与者已停用");
    var principal=(AuthenticatedPrincipal)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    Instant now=Instant.now();
    db.update("insert into ticket_redemptions(id,ticket_id,request_id,staff_id,redeemed_at) values(?,?,?,?,?)",UUID.randomUUID(),ticket,request.requestId(),principal.userId(),java.sql.Timestamp.from(now));
    db.update("update checkin_tickets set checkin_count=checkin_count+1,last_checkin_at=? where id=?",java.sql.Timestamp.from(now),ticket);
    return ticket(id,request.token());
  }
  @Transactional
  public Map<String,Boolean> registration(UUID id,boolean allowed) {
    Activity a=scope(id); a=activities.findForUpdate(a.getId()).orElseThrow(); a.setRegistrationAllowed(allowed);
    return Map.of("registrationAllowed",allowed);
  }
}
