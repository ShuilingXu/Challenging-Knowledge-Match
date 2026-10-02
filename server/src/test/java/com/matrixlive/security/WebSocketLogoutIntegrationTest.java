package com.matrixlive.security;

import static org.junit.jupiter.api.Assertions.*;

import com.matrixlive.repository.ActivityRepository;
import com.matrixlive.security.auth.AuthModels;
import com.matrixlive.security.auth.AuthService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketLogoutIntegrationTest {
  @LocalServerPort private int port;
  @Autowired private AuthService auth;
  @Autowired private ActivityRepository activities;
  @Autowired private SimpMessagingTemplate messaging;

  @Test void actualBrokerDeliversAuthorizedMessagesAndStopsAfterLogout() throws Exception {
    var login = auth.login(new AuthModels.LoginRequest("sysadmin", "ChangeMe!2026"), null);
    String topic = "/topic/activities/" + activities.findAll().getFirst().getId();
    var frames = new Frames();
    var client = HttpClient.newHttpClient();
    var socket = client.newWebSocketBuilder().subprotocols("v12.stomp")
        .buildAsync(URI.create("ws://localhost:" + port + "/ws"), frames).get(10, TimeUnit.SECONDS);
    try {
      socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer "
          + login.response().accessToken() + "\n\n\0", true).join();
      assertTrue(frames.control.poll(10, TimeUnit.SECONDS).startsWith("CONNECTED"));
      // SimpleBroker does not acknowledge SUBSCRIBE receipts. Publish until an
      // actual delivery proves that subscription registration has completed.
      socket.sendText("SUBSCRIBE\nid:regression\ndestination:" + topic + "\n\n\0", true).join();
      String baseline = null;
      for (int attempt = 0; attempt < 50 && baseline == null; attempt++) {
        messaging.convertAndSend(topic, Map.of("type", "before.logout"));
        baseline = frames.messages.poll(100, TimeUnit.MILLISECONDS);
      }
      assertNotNull(baseline, "An authorized session must receive broker messages");
      assertTrue(baseline.contains("before.logout"));
      var logout = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/logout"))
          .header("Authorization", "Bearer " + login.response().accessToken())
          .POST(HttpRequest.BodyPublishers.noBody()).build();
      assertEquals(204, client.send(logout, HttpResponse.BodyHandlers.discarding()).statusCode());
      // Ignore any baseline publications already in flight; only the new event
      // is evidence of delivery after revocation has committed.
      frames.messages.clear();
      messaging.convertAndSend(topic, Map.of("type", "after.logout"));
      String next;
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(700);
      while (System.nanoTime() < deadline) {
        next = frames.messages.poll(100, TimeUnit.MILLISECONDS);
        assertTrue(next == null || !next.contains("after.logout"), "Revoked session received a new event");
      }
    } finally { socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join(); }
  }

  private static final class Frames implements WebSocket.Listener {
    private final StringBuilder partial = new StringBuilder();
    private final BlockingQueue<String> control = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

    @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
      partial.append(data);
      int end;
      while ((end = partial.indexOf("\0")) >= 0) {
        String frame = partial.substring(0, end).stripLeading();
        partial.delete(0, end + 1);
        if (frame.startsWith("MESSAGE")) messages.offer(frame);
        else control.offer(frame);
      }
      socket.request(1);
      return null;
    }
  }
}
