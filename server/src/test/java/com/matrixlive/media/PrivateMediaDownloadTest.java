package com.matrixlive.media;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.matrixlive.service.SiteSettingsService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrivateMediaDownloadTest {
  @Test
  void servesPrivateStorageBytesAndByteRangesWithoutRedirectingTheBrowser() throws Exception {
    byte[] bytes = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "image/png");
          exchange.getResponseHeaders().add("ETag", "\"test-etag\"");
          exchange.getResponseHeaders().add("Last-Modified", "Thu, 01 Oct 2026 00:00:00 GMT");
          if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.getResponseHeaders().add("Content-Length", String.valueOf(bytes.length));
            exchange.sendResponseHeaders(200, -1);
          } else {
            int start = 0, end = bytes.length - 1;
            String range = exchange.getRequestHeaders().getFirst("Range");
            if (range != null) {
              String[] pieces = range.substring(6).split("-");
              start = Integer.parseInt(pieces[0]);
              if (pieces.length > 1) end = Integer.parseInt(pieces[1]);
              exchange
                  .getResponseHeaders()
                  .add("Content-Range", "bytes " + start + "-" + end + "/" + bytes.length);
            }
            byte[] body = Arrays.copyOfRange(bytes, start, end + 1);
            exchange.sendResponseHeaders(range == null ? 200 : 206, body.length);
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    server.start();
    try {
      var settings = mock(SiteSettingsService.class);
      when(settings.storageConfiguration())
          .thenReturn(
              new SiteSettingsService.StorageConfiguration(
                  true,
                  "http://127.0.0.1:" + server.getAddress().getPort(),
                  "us-east-1",
                  "media",
                  "test-access",
                  "test-secret",
                  null,
                  null,
                  "PATH"));
      var storage = new ObjectStorageService(new StorageProperties(), settings);
      UUID activity = UUID.randomUUID();
      String file = UUID.randomUUID() + "-poster.png";
      var full = storage.download(activity, "questions", file, null);
      assertEquals(200, full.getStatusCode().value());
      assertEquals("no-store", full.getHeaders().getCacheControl());
      assertNull(full.getHeaders().getLocation());
      try (var body = full.getBody().getInputStream()) {
        assertArrayEquals(bytes, body.readAllBytes());
      }
      var partial = storage.download(activity, "questions", file, "bytes=2-4");
      assertEquals(206, partial.getStatusCode().value());
      assertEquals("bytes 2-4/8", partial.getHeaders().getFirst("Content-Range"));
      try (var body = partial.getBody().getInputStream()) {
        assertArrayEquals(new byte[] {3, 4, 5}, body.readAllBytes());
      }
      assertEquals(
          416, storage.download(activity, "questions", file, "bytes=99-").getStatusCode().value());
      assertEquals(
          416,
          storage.download(activity, "questions", file, "bytes=1-2,4-5").getStatusCode().value());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectsSvgAndForgedMediaContentBeforeContactingStorage() {
    var storage =
        new ObjectStorageService(new StorageProperties(), mock(SiteSettingsService.class));
    UUID activity = UUID.randomUUID();
    for (String type : new String[] {"image/svg+xml", "image/png", "image/webp"}) {
      var file =
          new org.springframework.mock.web.MockMultipartFile(
              "file", "fake.png", type, "<script>alert(1)</script>".getBytes());
      var error =
          assertThrows(
              com.matrixlive.service.DomainException.class,
              () -> storage.upload(activity, "questions", file));
      assertEquals(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.getStatus());
    }
  }
}
