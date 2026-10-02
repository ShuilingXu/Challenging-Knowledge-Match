package com.matrixlive.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.matrixlive.security.JwtTokenService;
import com.matrixlive.security.auth.UserAccount;
import com.matrixlive.security.auth.UserAccountRepository;
import com.matrixlive.security.auth.UserRole;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Real HTTP multipart parsing verifies limits before controllers are invoked. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.servlet.multipart.max-file-size=2048B", "spring.servlet.multipart.max-request-size=3072B"})
class MediaUploadLimitIntegrationTest {
  @LocalServerPort private int port;
  @Autowired private UserAccountRepository users;
  @Autowired private JwtTokenService tokens;
  @MockitoBean private ObjectStorageService storage;

  @Test
  void acceptsFilesWithinConfiguredLimitAndReturns413ForBothMultipartLimits() throws Exception {
    var account = users.save(new UserAccount("upload-limit-" + UUID.randomUUID(), "Upload tester", "unused", UserRole.SYSTEM_ADMIN));
    String token = tokens.issueAccountToken(account).value();
    UUID activityId = UUID.randomUUID();
    when(storage.upload(eq(activityId), eq("assets"), any())).thenReturn(new ObjectStorageService.StoredObject(
        "key", "/api/activities/" + activityId + "/media/assets/test.png", "image/png", 1500));
    assertEquals(201, upload(activityId, token, 1500).statusCode());
    var fileLimit = upload(activityId, token, 2049);
    assertEquals(413, fileLimit.statusCode());
    org.junit.jupiter.api.Assertions.assertTrue(fileLimit.body().contains("Upload exceeds"));
    assertEquals(413, upload(activityId, token, 4000).statusCode());
  }

  private HttpResponse<String> upload(UUID activityId, String token, int size) throws Exception {
    byte[] prefix = ("--media-test\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.png\"\r\n"
        + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
    byte[] suffix = "\r\n--media-test--\r\n".getBytes(StandardCharsets.UTF_8);
    var body = new java.io.ByteArrayOutputStream();
    body.write(prefix);
    body.write(new byte[size]);
    body.write(suffix);
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/activities/" + activityId + "/media"))
        .header("Authorization", "Bearer " + token)
        .header("Content-Type", "multipart/form-data; boundary=media-test")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }
}
