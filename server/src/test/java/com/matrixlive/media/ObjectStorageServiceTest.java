package com.matrixlive.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ObjectStorageServiceTest {
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
