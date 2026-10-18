package com.matrixlive.security.auth;
import com.matrixlive.service.ActivityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ParticipantJoinService {
  private final ActivityService activities;
  private final AuthService auth;
  public ParticipantJoinService(ActivityService activities,AuthService auth) { this.activities=activities; this.auth=auth; }
  @Transactional
  public AuthModels.ParticipantTokenResponse join(ParticipantJoinController.JoinRequest r,HttpServletRequest request) {
    activities.register(r.activityId(),r.venue(),r.registration());
    return auth.verifiedParticipantToken(new AuthModels.ParticipantTokenRequest(r.activityId(),r.venue(),r.registration().contact(),r.challengeId(),r.challengeAnswer()),request);
  }
}
