package com.matrixlive.security.auth;

import com.matrixlive.service.DomainException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HumanVerificationService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  private final HumanChallengeRepository challenges;
  private final AuthRateLimiter limiter;
  private final int challengeIpLimit;
  private final int participantIpLimit;
  private final int contactLimit;

  public HumanVerificationService(
      HumanChallengeRepository challenges,
      AuthRateLimiter limiter,
      @org.springframework.beans.factory.annotation.Value(
              "${app.security.human-verification.challenge-ip-limit:3000}")
          int challengeIpLimit,
      @org.springframework.beans.factory.annotation.Value(
              "${app.security.human-verification.participant-ip-limit:3000}")
          int participantIpLimit,
      @org.springframework.beans.factory.annotation.Value(
              "${app.security.human-verification.contact-limit:10}")
          int contactLimit) {
    this.challenges = challenges;
    this.limiter = limiter;
    if (challengeIpLimit < 1 || participantIpLimit < 1 || contactLimit < 1)
      throw new IllegalArgumentException("Human verification rate limits must be positive");
    this.challengeIpLimit = challengeIpLimit;
    this.participantIpLimit = participantIpLimit;
    this.contactLimit = contactLimit;
  }

  @Transactional
  public ChallengeResponse issue(String ip) {
    limiter.check("challenge-ip", ip, challengeIpLimit);
    StringBuilder answer = new StringBuilder();
    for (int i = 0; i < 5; i++) answer.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    UUID id = UUID.randomUUID();
    challenges.save(new HumanChallenge(id, AuthService.hash(answer.toString()), ip));
    BufferedImage image = new BufferedImage(210, 70, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(new Color(245, 245, 250));
    g.fillRect(0, 0, 210, 70);
    for (int i = 0; i < 20; i++) {
      g.setColor(new Color(RANDOM.nextInt(180), RANDOM.nextInt(180), RANDOM.nextInt(180)));
      g.drawLine(RANDOM.nextInt(210), RANDOM.nextInt(70), RANDOM.nextInt(210), RANDOM.nextInt(70));
    }
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
    for (int i = 0; i < 5; i++) {
      g.setColor(new Color(RANDOM.nextInt(100), RANDOM.nextInt(100), RANDOM.nextInt(100)));
      g.drawString(answer.substring(i, i + 1), 12 + i * 38, 43 + RANDOM.nextInt(15));
    }
    g.dispose();
    try {
      var out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return new ChallengeResponse(
          id, "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray()));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to render human challenge", e);
    }
  }

  @Transactional(noRollbackFor = DomainException.class)
  public void verify(UUID id, String answer, String ip, String contactHash) {
    limiter.check("participant-ip", ip, participantIpLimit);
    limiter.check("participant-contact", contactHash, contactLimit);
    var challenge =
        challenges
            .findForUpdate(id)
            .orElseThrow(() -> new DomainException(HttpStatus.UNAUTHORIZED, "人机验证失败，请刷新重试"));
    boolean valid = challenge.matches(ip, answer.trim().toUpperCase(Locale.ROOT));
    challenge.consume(contactHash);
    if (!valid) throw new DomainException(HttpStatus.UNAUTHORIZED, "人机验证失败，请刷新重试");
  }

  public record ChallengeResponse(UUID challengeId, String image) {}
}
