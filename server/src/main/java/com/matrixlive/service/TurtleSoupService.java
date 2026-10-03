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
  public record Content(@NotBlank @Size(max=160) String title,
      @NotBlank @Size(max=8000) String surface,
      @NotNull @Size(max=50) List<@NotBlank @Size(max=2000) String> clues,
      @NotBlank @Size(max=8000) String solution) { }
  public record State(Content content, String stage, int revealedClues, long revision, List<Integer> revealedClueIndexes) {
    public State {
      // Old persisted states represented a consecutive prefix. Preserve that public selection.
      if (revealedClueIndexes == null) {
        int count = Math.min(Math.max(0, revealedClues), content == null ? 0 : content.clues().size());
        revealedClueIndexes = java.util.stream.IntStream.range(0, count).boxed().toList();
      }
      revealedClueIndexes = List.copyOf(revealedClueIndexes);
      revealedClues = revealedClueIndexes.size();
    }
    public State(Content content, String stage, int revealedClues, long revision) {
      this(content, stage, revealedClues, revision, null);
    }
  }
  public record Command(@NotBlank String action, @Min(0) long revision,
      @Min(0) Integer clueIndex, @Size(max=2000) String clue, Boolean publishNow) {
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
  @Transactional
  public State save(UUID id, Content content) {
    Activity activity=require(id, true);
    State previous=read(activity);
    if (!"LOBBY".equals(previous.stage())) throw conflict("请先回到候场，再修改汤稿");
    if (Set.of("FINISHED", "CANCELLED").contains(activity.getStatus())) throw conflict("已结束的活动不能修改汤稿");
    State next=new State(new Content(content.title().trim(), content.surface().trim(),
        content.clues().stream().map(String::trim).toList(), content.solution().trim()), "LOBBY", 0, previous.revision()+1);
    store(activity, next);
    return next;
  }
  @Transactional
  public State control(UUID id, Command command) {
    Activity activity=require(id, true);
    State current=read(activity);
    if (command.revision()!=current.revision()) throw conflict("其他主持人已更新展示，请刷新后重试");
    String action=command.action();
    if (!"RESET".equals(action)) {
      Activity parent=activities.findById(activity.getParentActivityId()).orElseThrow(() -> conflict("母活动不存在"));
      if (!"LIVE".equals(activity.getStatus()) || !Set.of("LIVE", "REGISTRATION_OPEN").contains(parent.getStatus()))
        throw conflict("请先启用母活动并开始海龟汤环节");
      if (current.content()==null) throw conflict("请先保存汤稿");
    }
    String stage=current.stage();
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
      case "SYNC" -> { }
      case "RESET" -> { stage="LOBBY"; selected.clear(); }
      default -> throw new DomainException(HttpStatus.BAD_REQUEST, "未知海龟汤操作");
    }
    State next=new State(content, stage, selected.size(), current.revision()+1, selected);
    store(activity, next);
    Map<String,Object> payload=display(next);
    screens.publishActivityDisplay(id, ScreenDisplayMode.TURTLE_SOUP, payload);
    screens.publishActivityDisplay(activity.getParentActivityId(), ScreenDisplayMode.TURTLE_SOUP, payload);
    return next;
  }
  /** Only explicitly revealed content may reach device state or public topics. */
  public static Map<String,Object> display(State state) {
    Map<String,Object> data=new HashMap<>();
    data.put("stage", state.stage());
    if (state.content()!=null && !"LOBBY".equals(state.stage())) {
      data.put("title", state.content().title()); data.put("surface", state.content().surface());
      data.put("clues", state.revealedClueIndexes().stream().map(i -> state.content().clues().get(i)).toList());
      data.put("clueNumbers", state.revealedClueIndexes().stream().map(i -> i+1).toList());
      if ("SOLUTION".equals(state.stage())) data.put("solution", state.content().solution());
    }
    return data;
  }
  private Activity require(UUID id, boolean lock) {
    Activity activity=(lock ? activities.findForUpdate(id) : activities.findById(id))
        .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "活动不存在"));
    if (!"TURTLE_SOUP".equals(activity.getActivityType())) throw conflict("请选择海龟汤子活动");
    return activity;
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
  }
  private DomainException conflict(String message) { return new DomainException(HttpStatus.CONFLICT, message); }
}
