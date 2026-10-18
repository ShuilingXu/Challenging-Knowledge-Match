package com.matrixlive.security.auth;

import com.matrixlive.api.ApiModels.RegisterParticipantRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/participant-join")
public class ParticipantJoinController {
  private final HumanVerificationService human;
  private final ParticipantJoinService join;
  public ParticipantJoinController(HumanVerificationService human,ParticipantJoinService join) { this.human=human; this.join=join; }
  public record JoinRequest(@NotNull UUID activityId,@NotBlank @Size(max=80) String venue,
    @NotNull @Valid RegisterParticipantRequest registration,@NotNull UUID challengeId,@NotBlank @Size(max=16) String challengeAnswer) {}
  @PostMapping
  public AuthModels.ParticipantTokenResponse join(@Valid @RequestBody JoinRequest r,HttpServletRequest request) {
    String contact=r.registration().contact().replaceAll("[\\s-]","").trim();
    human.verify(r.challengeId(),r.challengeAnswer(),request.getRemoteAddr(),AuthService.hash(contact));
    return join.join(r,request);
  }
}
