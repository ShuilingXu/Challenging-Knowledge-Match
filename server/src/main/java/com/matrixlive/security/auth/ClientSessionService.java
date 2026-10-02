package com.matrixlive.security.auth;

import com.matrixlive.repository.ParticipantRepository;
import com.matrixlive.screen.ScreenDeviceRepository;
import com.matrixlive.security.*;
import com.matrixlive.service.DomainException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientSessionService {
  private final ClientCredentialRepository credentials;
  private final JwtTokenService jwt;
  private final JwtProperties properties;
  private final TokenRevocationService revocations;
  private final ParticipantRepository participants;
  private final ScreenDeviceRepository devices;

  public ClientSessionService(
      ClientCredentialRepository credentials,
      JwtTokenService jwt,
      JwtProperties properties,
      TokenRevocationService revocations,
      ParticipantRepository participants,
      ScreenDeviceRepository devices) {
    this.credentials = credentials;
    this.jwt = jwt;
    this.properties = properties;
    this.revocations = revocations;
    this.participants = participants;
    this.devices = devices;
  }

  @Transactional
  public Session issue(PrincipalKind kind, UUID activityId, UUID subjectId) {
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var credential =
        credentials.save(
            new ClientCredential(
                UUID.randomUUID(),
                AuthService.hash(raw),
                kind.name(),
                activityId,
                subjectId,
                Instant.now().plus(properties.getRefreshTokenTtl())));
    return new Session(access(credential), raw);
  }

  @Transactional(readOnly = true)
  public JwtTokenService.IssuedAccessToken refresh(String raw) {
    var credential =
        credentials
            .findByTokenHash(AuthService.hash(raw))
            .filter(c -> c.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "会话已过期，请重新登录或配对"));
    if (revocations.isAccessTokenRevoked(credential.getId()))
      throw new DomainException(HttpStatus.UNAUTHORIZED, "会话已退出");
    if (PrincipalKind.PARTICIPANT.name().equals(credential.getKind())) {
      if (participants
          .findById(credential.getSubjectId())
          .filter(p -> "ACTIVE".equals(p.getStatus()))
          .isEmpty()) throw new DomainException(HttpStatus.UNAUTHORIZED, "参与者身份已停用");
    } else if (devices
        .findByIdAndActivityId(credential.getSubjectId(), credential.getActivityId())
        .isEmpty()) throw new DomainException(HttpStatus.UNAUTHORIZED, "大屏设备已移除");
    return access(credential);
  }

  @Transactional
  public void logout(String raw) {
    credentials
        .findByTokenHash(AuthService.hash(raw))
        .ifPresent(
            c -> {
              revocations.revokeFamily(
                  c.getId(), null, c.getExpiresAt().plus(properties.getParticipantTokenTtl()));
              credentials.delete(c);
            });
  }

  private JwtTokenService.IssuedAccessToken access(ClientCredential c) {
    return PrincipalKind.PARTICIPANT.name().equals(c.getKind())
        ? jwt.issueParticipantToken(c.getActivityId(), c.getSubjectId(), c.getId())
        : jwt.issueScreenDeviceToken(c.getActivityId(), c.getSubjectId(), c.getId());
  }

  public record Session(JwtTokenService.IssuedAccessToken access, String refreshToken) {}
}
