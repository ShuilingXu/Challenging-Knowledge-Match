package com.matrixlive.api;
import com.matrixlive.service.CheckinService;
import com.matrixlive.service.CheckinService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/activities/{activityId}/checkin")
public class CheckinController {
  private final CheckinService service;
  public CheckinController(CheckinService service) { this.service=service; }
  public record ImportRequest(@NotEmpty @Size(max=5000) List<@Valid Entry> entries) {}
  public record RegistrationRequest(@NotNull Boolean registrationAllowed) {}
  @GetMapping public List<Ticket> list(@PathVariable UUID activityId) { return service.list(activityId); }
  @GetMapping("/tickets/{token}") public Ticket ticket(@PathVariable UUID activityId,@PathVariable String token) { return service.ticket(activityId,token); }
  @PostMapping public List<Ticket> create(@PathVariable UUID activityId,@Valid @RequestBody ImportRequest request) { return service.create(activityId,request.entries()); }
  @PostMapping("/redeem") public Ticket redeem(@PathVariable UUID activityId,@Valid @RequestBody Redeem request) { return service.redeem(activityId,request); }
  @PutMapping("/registration") public Map<String,Boolean> registration(@PathVariable UUID activityId,@Valid @RequestBody RegistrationRequest request) { return service.registration(activityId,request.registrationAllowed()); }
}
