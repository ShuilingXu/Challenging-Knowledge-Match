package com.matrixlive.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class ObjectStorageServiceTest {
  @Test
  void redirectsOnlyToConfiguredPublicStorageAndRejectsTraversal() throws Exception {
    var settings = mock(com.matrixlive.service.SiteSettingsService.class);
    when(settings.storageConfiguration()).thenReturn(new com.matrixlive.service.SiteSettingsService.StorageConfiguration(
        true, "http://minio:9000", "us-east-1", "media", "test-access", "test-secret", null, "https://cdn.example.test/media", "PATH"));
    var storage = new ObjectStorageService(new StorageProperties(), settings);
    var activityId = java.util.UUID.randomUUID();
    String fileName = java.util.UUID.randomUUID() + "-poster.png";
    assertEquals("https://cdn.example.test/media/" + activityId + "/branding/" + fileName,
        storage.access(activityId, "branding", fileName, null).location());
    assertThrows(com.matrixlive.service.DomainException.class, () -> storage.access(activityId, "..", fileName, null));
    assertThrows(com.matrixlive.service.DomainException.class, () -> storage.access(activityId, "branding", "../other.png", null));
  }

  @Test
  void uploadReturnsAStableUrlAndAccessDoesNotCacheTheSignedRedirect() throws Exception {
    var storage = mock(ObjectStorageService.class);
    var activityId = java.util.UUID.randomUUID();
    String fileName = java.util.UUID.randomUUID() + "-poster.png";
    String path = "/api/activities/" + activityId + "/media/branding/" + fileName;
    var file = new org.springframework.mock.web.MockMultipartFile("file", "poster.png", "image/png", new byte[]{1});
    when(storage.upload(activityId, "branding", file)).thenReturn(new ObjectStorageService.StoredObject(
        activityId + "/branding/" + fileName, path, "image/png", 1));
    when(storage.access(activityId, "branding", fileName, null)).thenReturn(new ObjectStorageService.MediaContent(
        "https://storage.example.test/poster.png", null, null, 0, null, false));
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new MediaController(storage)).build();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/activities/" + activityId + "/media")
            .file(file).param("category", "branding"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.url").value("http://localhost" + path));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isFound())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("https://storage.example.test/poster.png"));
  }

  @Test
  void proxiesInternalStorageWithByteRangesWithoutRedirectingTheBrowser() throws Exception {
    byte[] bytes = "0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var upstream = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    var requestedRange = new java.util.concurrent.atomic.AtomicReference<String>();
    upstream.createContext("/media/", exchange -> {
      exchange.getResponseHeaders().set("Content-Type", "video/mp4");
      exchange.getResponseHeaders().set("ETag", "\"test-etag\"");
      exchange.getResponseHeaders().set("Last-Modified", "Thu, 01 Oct 2026 00:00:00 GMT");
      if ("HEAD".equals(exchange.getRequestMethod())) {
        exchange.getResponseHeaders().set("Content-Length", "10");
        exchange.sendResponseHeaders(200, -1);
      } else {
        String range = exchange.getRequestHeaders().getFirst("Range");
        requestedRange.set(range);
        byte[] body = range == null ? bytes : java.util.Arrays.copyOfRange(bytes, 2, 5);
        if (range != null) exchange.getResponseHeaders().set("Content-Range", "bytes 2-4/10");
        exchange.sendResponseHeaders(range == null ? 200 : 206, body.length);
        exchange.getResponseBody().write(body);
      }
      exchange.close();
    });
    upstream.start();
    try {
      var settings = mock(com.matrixlive.service.SiteSettingsService.class);
      when(settings.storageConfiguration()).thenReturn(new com.matrixlive.service.SiteSettingsService.StorageConfiguration(
          true, "http://127.0.0.1:" + upstream.getAddress().getPort(), "us-east-1", "media",
          "test-access", "test-secret", null, null, "PATH"));
      var storage = new ObjectStorageService(new StorageProperties(), settings);
      var activityId = java.util.UUID.randomUUID();
      String fileName = java.util.UUID.randomUUID() + "-clip.mp4";
      String path = "/api/activities/" + activityId + "/media/questions/" + fileName;
      var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new MediaController(storage)).build();
      mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(bytes))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Length", "10"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Security-Policy", "sandbox"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().doesNotExist("Location"));
      mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Range", "bytes=2-4"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isPartialContent())
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("234"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Range", "bytes 2-4/10"));
      assertEquals("bytes=2-4", requestedRange.get());
      for (String invalid : java.util.List.of("bytes=20-", "bytes=4-2", "bytes=0-1,3-4", "nonsense")) {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Range", invalid))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isRequestedRangeNotSatisfiable())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Range", "bytes */10"));
      }
    } finally {
      upstream.stop(0);
    }
  }

  @Test
  void closesProxyStreamAfterWritingTheResponse() throws Exception {
    var storage = mock(ObjectStorageService.class);
    var activityId = java.util.UUID.randomUUID();
    String fileName = java.util.UUID.randomUUID() + "-poster.png";
    var closed = new java.util.concurrent.atomic.AtomicBoolean();
    var stream = new java.io.ByteArrayInputStream(new byte[]{1, 2}) {
      @Override public void close() { closed.set(true); }
    };
    when(storage.access(activityId, "branding", fileName, null)).thenReturn(
        new ObjectStorageService.MediaContent(null, stream, "image/png", 2, null, false));
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new MediaController(storage)).build();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
        "/api/activities/" + activityId + "/media/branding/" + fileName))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(new byte[]{1, 2}));
    assertTrue(closed.get());
  }
  @Test
  void derivesRegionalAwsEndpointsWhenNoCustomEndpointIsConfigured() {
    assertEquals("https://s3.us-east-1.amazonaws.com", ObjectStorageService.endpoint(null, "us-east-1"));
    assertEquals("https://s3.eu-west-1.amazonaws.com", ObjectStorageService.endpoint("", "eu-west-1"));
    assertEquals("https://s3.cn-north-1.amazonaws.com.cn", ObjectStorageService.endpoint(null, "cn-north-1"));
  }

  @Test
  void preservesExplicitS3CompatibleEndpointsWithoutTrailingSlash() {
    assertEquals("http://minio:9000", ObjectStorageService.endpoint("http://minio:9000///", "us-east-1"));
  }
}
