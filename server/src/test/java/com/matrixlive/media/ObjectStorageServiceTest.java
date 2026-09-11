package com.matrixlive.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class ObjectStorageServiceTest {
  @Test
  void generatesSignedAccessForTheRequestedActivityAssetAndRejectsTraversal() throws Exception {
    var settings = mock(com.matrixlive.service.SiteSettingsService.class);
    when(settings.storageConfiguration()).thenReturn(new com.matrixlive.service.SiteSettingsService.StorageConfiguration(
        true, "http://127.0.0.1:9000", "us-east-1", "media", "test-access", "test-secret", null, null, "PATH"));
    var storage = new ObjectStorageService(new StorageProperties(), settings);
    var activityId = java.util.UUID.randomUUID();
    String fileName = java.util.UUID.randomUUID() + "-poster.png";
    var signed = java.net.URI.create(storage.accessUrl(activityId, "branding", fileName));
    assertEquals("/media/" + activityId + "/branding/" + fileName, signed.getPath());
    assertTrue(signed.getRawQuery().contains("X-Amz-Signature="));
    assertThrows(com.matrixlive.service.DomainException.class, () -> storage.accessUrl(activityId, "..", fileName));
    assertThrows(com.matrixlive.service.DomainException.class, () -> storage.accessUrl(activityId, "branding", "../other.png"));
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
    when(storage.accessUrl(activityId, "branding", fileName)).thenReturn("https://storage.example.test/poster.png?signature=fresh");
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new MediaController(storage)).build();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/activities/" + activityId + "/media")
            .file(file).param("category", "branding"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.url").value("http://localhost" + path));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isFound())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("https://storage.example.test/poster.png?signature=fresh"));
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
