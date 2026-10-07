package com.matrixlive.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matrixlive.domain.Activity;
import com.matrixlive.repository.ActivityRepository;
import com.matrixlive.realtime.RealtimeEventBus;
import com.matrixlive.screen.ScreenService;
import com.matrixlive.screen.ScreenDisplayMode;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TurtleSoupService {
  public record Template(UUID id, Content content) { }
  public record Playlist(List<Template> entries, UUID activeTemplateId) { }
  public record PlaylistRequest(@NotNull @Size(max=100) List<@NotNull UUID> templateIds) { }
  public record SwitchRequest(@NotNull UUID templateId, @Min(0) long revision) { }
  public record SyncTemplate(@NotNull UUID templateId) { }
  public record Content(@NotBlank @Size(max=160) String title,
      @NotBlank @Size(max=8000) String surface,
      @NotNull @Size(max=50) List<@NotBlank @Size(max=2000) String> clues,
      @NotBlank @Size(max=8000) String solution) { }
  public record Playback(boolean paused, long elapsedMs, long startedAt, long step, int paceSeconds, Integer focusClueNumber) {
    public static Playback initial() { return new Playback(false, 0, System.currentTimeMillis(), 0, 15, null); }
    public long position(long now) { return elapsedMs + (paused ? 0 : Math.max(0, now - startedAt)); }
  }
  public record State(Content content, String stage, int revealedClues, long revision, List<Integer> revealedClueIndexes, Playback playback) {
    public State {
      if (playback == null) playback = Playback.initial();
      // Old persisted states represented a consecutive prefix. Preserve that public selection.
      if (revealedClueIndexes == null) {
        int count = Math.min(Math.max(0, revealedClues), content == null ? 0 : content.clues().size());
        revealedClueIndexes = java.util.stream.IntStream.range(0, count).boxed().toList();
      }
      revealedClueIndexes = List.copyOf(revealedClueIndexes);
      revealedClues = revealedClueIndexes.size();
    }
    public State(Content content, String stage, int revealedClues, long revision) {
      this(content, stage, revealedClues, revision, null, null);
    }
    public State(Content content, String stage, int revealedClues, long revision, List<Integer> indexes) {
      this(content, stage, revealedClues, revision, indexes, null);
    }
  }
  public record Command(@NotBlank String action, @Min(0) long revision,
      @Min(0) Integer clueIndex, @Size(max=2000) String clue, Boolean publishNow, @Min(8) @Max(40) Integer paceSeconds) {
    public Command(String action, long revision, Integer index, String clue, Boolean publishNow) {
      this(action, revision, index, clue, publishNow, null);
    }
    public Command(String action, long revision) { this(action, revision, null, null, null); }
  }
  private final ActivityRepository activities;
  private final ScreenService screens;
  private final ObjectMapper mapper;
  private final RealtimeEventBus events;
  public TurtleSoupService(ActivityRepository activities, ScreenService screens, ObjectMapper mapper, RealtimeEventBus events) {
    this.activities=activities; this.screens=screens; this.mapper=mapper; this.events=events;
  }
  @Transactional(readOnly=true)
  public State state(UUID id) { return read(require(id, false)); }
  @Transactional(readOnly=true)
  public Map<String,Object> participantDisplay(UUID id) {
    Activity source=activities.findById(id).orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "活动不存在"));
    UUID venueId="VENUE".equals(source.getActivityType()) ? id : source.getParentActivityId();
    List<Activity> candidates="TURTLE_SOUP".equals(source.getActivityType()) ? List.of(source)
        : venueId==null ? List.of() : activities.findByParentActivityId(venueId);
    for (Activity candidate : candidates) {
      if (!"TURTLE_SOUP".equals(candidate.getActivityType()) || !candidate.isFeatureEnabled()
          || !Set.of("LIVE", "FINISHED").contains(candidate.getStatus())) continue;
      Activity ancestor=candidate;
      boolean visible=true;
      while (ancestor.getParentActivityId()!=null) {
        ancestor=activities.findById(ancestor.getParentActivityId()).orElseThrow();
        if (!ancestor.isFeatureEnabled() || !Set.of("LIVE", "REGISTRATION_OPEN", "FINISHED").contains(ancestor.getStatus())) visible=false;
      }
      State state=read(candidate);
      if (visible && state.content()!=null && !"LOBBY".equals(state.stage())) {
        return display(state);
      }
    }
    return Map.of();
  }
  @Transactional
  public State save(UUID id, Content content) {
    Activity activity=require(id, true);
    State previous=read(activity);
    if (!"LOBBY".equals(previous.stage())) throw conflict("请先回到候场，再修改汤稿");
    if (Set.of("FINISHED", "CANCELLED").contains(activity.getStatus())) throw conflict("已结束的活动不能修改汤稿");
    State next=new State(new Content(content.title().trim(), content.surface().trim(),
        content.clues().stream().map(String::trim).toList(), content.solution().trim()), "LOBBY", 0, previous.revision()+1);
    store(activity, next);
    Playlist playlist=readPlaylist(activity);
    if (playlist.activeTemplateId()!=null) storePlaylist(activity, new Playlist(playlist.entries().stream()
        .map(t -> t.id().equals(playlist.activeTemplateId()) ? new Template(t.id(), next.content()) : t).toList(), playlist.activeTemplateId()));
    return next;
  }
  @Transactional
  public State control(UUID id, Command command) {
    Activity activity=require(id, true);
    State current=read(activity);
    if (command.revision()!=current.revision()) throw conflict("其他主持人已更新展示，请刷新后重试");
    String action=command.action();
    if (!"RESET".equals(action)) {
      if (!"LIVE".equals(activity.getStatus())) throw conflict("请先开始海龟汤环节");
      Activity ancestor=activity;
      while (ancestor.getParentActivityId()!=null) {
        ancestor=activities.findById(ancestor.getParentActivityId()).orElseThrow(() -> conflict("上级活动不存在"));
        if (!Set.of("LIVE", "REGISTRATION_OPEN").contains(ancestor.getStatus())) throw conflict("请先启用所属会场及母活动");
      }
      if (current.content()==null) throw conflict("请先保存汤稿");
    }
    String stage=current.stage();
    Playback playback=current.playback();
    long now=System.currentTimeMillis();
    Content content=current.content();
    List<Integer> selected=new ArrayList<>(current.revealedClueIndexes());
    switch(action) {
      case "SHOW_SURFACE" -> { stage="SURFACE"; selected.clear(); }
      case "SHOW_CLUE", "HIDE_CLUE" -> {
        if (!"SURFACE".equals(stage)) throw conflict("请先展示汤面，在推理过程中选择线索");
        Integer index=command.clueIndex();
        if (index==null || index<0 || index>=content.clues().size())
          throw new DomainException(HttpStatus.BAD_REQUEST, "请选择有效的线索");
        if ("SHOW_CLUE".equals(action)) { if (!selected.contains(index)) selected.add(index); }
        else selected.remove(index);
      }
      // Compatibility for older host tabs: resolve against the actual public selection.
      case "NEXT_CLUE" -> {
        if (!"SURFACE".equals(stage)) throw conflict("请先展示汤面");
        Integer index=java.util.stream.IntStream.range(0, content.clues().size()).boxed()
            .filter(i -> !selected.contains(i)).findFirst().orElseThrow(() -> conflict("已展示全部线索"));
        selected.add(index);
      }
      case "PREVIOUS_CLUE" -> {
        if (!"SURFACE".equals(stage) || selected.isEmpty()) throw conflict("没有可撤回的线索");
        selected.remove(selected.size()-1);
      }
      case "ADD_CLUE" -> {
        if (!Set.of("LOBBY", "SURFACE").contains(stage)) throw conflict("揭晓后请先回到候场，再添加线索");
        if (command.clue()==null || command.clue().isBlank() || command.clue().length()>2000)
          throw new DomainException(HttpStatus.BAD_REQUEST, "临时线索需为 1 至 2000 字");
        if (content.clues().size()>=50) throw conflict("最多保存 50 条线索");
        if (Boolean.TRUE.equals(command.publishNow()) && !"SURFACE".equals(stage)) throw conflict("请先展示汤面，再公开临时线索");
        List<String> all=new ArrayList<>(content.clues()); all.add(command.clue().trim());
        content=new Content(content.title(), content.surface(), List.copyOf(all), content.solution());
        if (Boolean.TRUE.equals(command.publishNow())) selected.add(all.size()-1);
      }
      case "REVEAL_SOLUTION" -> {
        if (!"SURFACE".equals(stage)) throw conflict("请先展示汤面，再揭晓汤底");
        stage="SOLUTION";
      }
      case "PAUSE_PLAYBACK", "RESUME_PLAYBACK", "NEXT_PAGE", "PREVIOUS_PAGE", "RESTART_PLAYBACK", "SET_PLAYBACK_SPEED" -> {
        if (!Set.of("SURFACE", "SOLUTION").contains(stage)) throw conflict("请先展示汤面，再控制播放");
        long elapsed=playback.position(now);
        long step=playback.step();
        boolean paused=playback.paused();
        int pace=playback.paceSeconds();
        Integer focus=playback.focusClueNumber();
        switch(action) {
          case "PAUSE_PLAYBACK" -> paused=true;
          case "RESUME_PLAYBACK" -> paused=false;
          case "NEXT_PAGE" -> { step++; paused=true; }
          case "PREVIOUS_PAGE" -> { step--; paused=true; }
          case "RESTART_PLAYBACK" -> { elapsed=0; step=0; paused=false; focus=null; }
          case "SET_PLAYBACK_SPEED" -> {
            if (command.paceSeconds()==null || command.paceSeconds()<8 || command.paceSeconds()>40)
              throw new DomainException(HttpStatus.BAD_REQUEST,"翻页间隔需为 8 至 40 秒");
            // Preserve the current page and progress when changing its reading interval.
            elapsed=Math.round(elapsed * (double) command.paceSeconds() / pace);
            pace=command.paceSeconds();
          }
          default -> { }
        }
        playback=new Playback(paused,elapsed,now,step,pace,focus);
      }
      case "SYNC" -> { }
      case "RESET" -> { stage="LOBBY"; selected.clear(); }
      default -> throw new DomainException(HttpStatus.BAD_REQUEST, "未知海龟汤操作");
    }
    // New public material starts a full reading interval; private edits do not interrupt playback.
    if (Set.of("SHOW_SURFACE","REVEAL_SOLUTION","RESET").contains(action)
        || (!selected.equals(current.revealedClueIndexes()) && !"HIDE_CLUE".equals(action)))
      playback=new Playback(false,0,now,0,playback.paceSeconds(), !selected.equals(current.revealedClueIndexes()) && "SURFACE".equals(stage) && !selected.isEmpty() ? selected.getLast()+1 : null);
    State next=new State(content, stage, selected.size(), current.revision()+1, selected, playback);
    store(activity, next);
    Playlist playlist=readPlaylist(activity);
    if(playlist.activeTemplateId()!=null) storePlaylist(activity,new Playlist(playlist.entries().stream()
        .map(t -> t.id().equals(playlist.activeTemplateId()) ? new Template(t.id(),next.content()) : t).toList(),playlist.activeTemplateId()));
    Map<String,Object> payload=display(next);
    screens.publishActivityDisplay(id, ScreenDisplayMode.TURTLE_SOUP, payload);
    screens.publishActivityDisplay(activity.getParentActivityId(), ScreenDisplayMode.TURTLE_SOUP, payload);
    return next;
  }
  /** Only explicitly revealed content may reach device state or public topics. */
  public static Map<String,Object> display(State state) {
    Map<String,Object> data=new HashMap<>();
    data.put("stage", state.stage());
    data.put("playback", state.playback());
    if (state.content()!=null && !"LOBBY".equals(state.stage())) {
      data.put("title", state.content().title()); data.put("surface", state.content().surface());
      data.put("clues", state.revealedClueIndexes().stream().map(i -> state.content().clues().get(i)).toList());
      data.put("clueNumbers", state.revealedClueIndexes().stream().map(i -> i+1).toList());
      data.put("solution", state.content().solution());
    }
    return data;
  }
  private Activity require(UUID id, boolean lock) {
    Activity activity=(lock ? activities.findForUpdate(id) : activities.findById(id))
        .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "活动不存在"));
    if (!activity.isFeatureEnabled()) throw conflict("此会场的海龟汤功能已关闭");
    if (!"TURTLE_SOUP".equals(activity.getActivityType())) throw conflict("请选择会场海龟汤功能");
    return activity;
  }

  private Activity libraryOwner(UUID id, boolean lock) {
    Activity owner=activities.findById(id).orElseThrow(() -> conflict("活动不存在"));
    while (owner.getParentActivityId()!=null) owner=activities.findById(owner.getParentActivityId()).orElseThrow();
    if (!"EVENT".equals(owner.getActivityType())) throw conflict("总汤库不存在");
    return lock ? activities.findForUpdate(owner.getId()).orElseThrow() : owner;
  }

  private List<Template> readLibrary(Activity owner) {
    if (owner.getTurtleSoup()==null) return List.of();
    try { return mapper.readValue(owner.getTurtleSoup(), new com.fasterxml.jackson.core.type.TypeReference<List<Template>>() { }); }
    catch (Exception e) { throw new IllegalStateException("Invalid soup library", e); }
  }

  @Transactional(readOnly=true)
  public List<Template> templates(UUID id) { return readLibrary(libraryOwner(id, false)); }

  @Transactional
  public Template saveTemplate(UUID id, UUID templateId, Content content) {
    Activity owner=libraryOwner(id, true);
    if (!owner.getId().equals(id)) throw conflict("请在母活动编辑总汤库");
    if (Set.of("FINISHED", "CANCELLED").contains(owner.getStatus())) throw conflict("母活动已结束");
    List<Template> entries=new ArrayList<>(readLibrary(owner));
    if (templateId!=null && entries.stream().noneMatch(t -> t.id().equals(templateId))) throw conflict("汤稿不存在");
    if (templateId==null && entries.size()>=500) throw conflict("总汤库最多保存 500 份汤稿");
    Template next=new Template(templateId==null ? UUID.randomUUID() : templateId, content);
    entries.removeIf(t -> t.id().equals(next.id())); entries.add(next);
    try { owner.updateTurtleSoup(mapper.writeValueAsString(entries)); } catch(Exception e) { throw new IllegalStateException(e); }
    return next;
  }

  @Transactional
  public State syncTemplate(UUID id, UUID templateId) {
    Activity target=require(id, true);
    if (!"DRAFT".equals(target.getStatus())) throw conflict("仅可在草稿阶段同步总汤库");
    Content content=templates(id).stream().filter(t -> t.id().equals(templateId)).findFirst()
        .orElseThrow(() -> conflict("汤稿不存在")).content();
    return save(id, content);
  }
  private Playlist readPlaylist(Activity activity) {
    if (activity.getSoupPlaylist()==null) return new Playlist(List.of(), null);
    try { return mapper.readValue(activity.getSoupPlaylist(), Playlist.class); }
    catch (Exception e) { throw new IllegalStateException("Invalid soup playlist", e); }
  }
  private void storePlaylist(Activity activity, Playlist playlist) {
    try { activity.updateSoupPlaylist(mapper.writeValueAsString(playlist)); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
  @Transactional(readOnly=true)
  public Playlist playlist(UUID id) { return readPlaylist(require(id, false)); }

  @Transactional
  public Playlist configurePlaylist(UUID id, List<UUID> ids) {
    Activity activity=require(id, true);
    State current=read(activity);
    if (!"LOBBY".equals(current.stage())) throw conflict("请先回到候场，再调整本会场汤稿");
    if (Set.of("FINISHED", "CANCELLED").contains(activity.getStatus())) throw conflict("会场功能已结束");
    Playlist previous=readPlaylist(activity);
    Map<UUID,Template> source=new HashMap<>();
    templates(id).forEach(t -> source.put(t.id(),t));
    // Retain local edits for already selected scripts.
    previous.entries().forEach(t -> source.put(t.id(),t));
    List<Template> entries=ids.stream().distinct().map(key -> {
      Template t=source.get(key); if(t==null) throw conflict("所选汤稿不存在"); return t;
    }).toList();
    UUID active=entries.stream().anyMatch(t -> t.id().equals(previous.activeTemplateId()))
        ? previous.activeTemplateId() : entries.isEmpty() ? null : entries.getFirst().id();
    storePlaylist(activity,new Playlist(entries,active));
    Content content=active==null ? null : entries.stream().filter(t -> t.id().equals(active)).findFirst().orElseThrow().content();
    State next=new State(content,"LOBBY",0,current.revision()+1);
    store(activity,next);
    screens.publishActivityDisplay(activity.getParentActivityId(),ScreenDisplayMode.TURTLE_SOUP,display(next));
    return new Playlist(entries,active);
  }

  @Transactional
  public State switchTemplate(UUID id, SwitchRequest request) {
    Activity activity=require(id,true);
    State current=read(activity);
    if(request.revision()!=current.revision()) throw conflict("其他主持人已更新，请刷新后重试");
    if(!"LOBBY".equals(current.stage())) throw conflict("请先回到候场，再切换汤稿");
    if(Set.of("FINISHED","CANCELLED").contains(activity.getStatus())) throw conflict("会场功能已结束");
    Playlist playlist=readPlaylist(activity);
    Template selected=playlist.entries().stream().filter(t -> t.id().equals(request.templateId())).findFirst()
        .orElseThrow(() -> conflict("请先将汤稿选入本会场"));
    storePlaylist(activity,new Playlist(playlist.entries(),selected.id()));
    State next=new State(selected.content(),"LOBBY",0,current.revision()+1);
    store(activity,next);
    screens.publishActivityDisplay(id,ScreenDisplayMode.TURTLE_SOUP,display(next));
    screens.publishActivityDisplay(activity.getParentActivityId(),ScreenDisplayMode.TURTLE_SOUP,display(next));
    return next;
  }
  private State read(Activity activity) {
    if (activity.getTurtleSoup()==null) return new State(null, "LOBBY", 0, 0);
    try { return mapper.readValue(activity.getTurtleSoup(), State.class); }
    catch(Exception e) { throw new IllegalStateException("Invalid stored turtle soup", e); }
  }
  private void store(Activity activity, State state) {
    try { activity.updateTurtleSoup(mapper.writeValueAsString(state)); }
    catch(Exception e) { throw new IllegalStateException(e); }
    events.send("/topic/activities/"+activity.getId(), Map.of("type", "turtle_soup.updated", "sentAt", Instant.now().toString()));
    if (activity.getParentActivityId()!=null)
      events.send("/topic/activities/"+activity.getParentActivityId(), Map.of("type", "turtle_soup.updated", "sentAt", Instant.now().toString()));
  }
  private DomainException conflict(String message) { return new DomainException(HttpStatus.CONFLICT, message); }
}
