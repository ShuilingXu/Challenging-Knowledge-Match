package com.matrixlive.api;

import com.matrixlive.service.TurtleSoupService;
import com.matrixlive.service.TurtleSoupService.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities/{activityId}/turtle-soup")
public class TurtleSoupController {
  private final TurtleSoupService service;
  public TurtleSoupController(TurtleSoupService service) { this.service=service; }
  @GetMapping public State state(@PathVariable UUID activityId) { return service.state(activityId); }
  @PutMapping public State save(@PathVariable UUID activityId, @Valid @RequestBody Content content) { return service.save(activityId, content); }
  @PostMapping("/control") public State control(@PathVariable UUID activityId, @Valid @RequestBody Command command) { return service.control(activityId, command); }
}
