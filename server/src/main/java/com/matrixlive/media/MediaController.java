package com.matrixlive.media;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/activities/{activityId}/media")
public class MediaController {
  private final ObjectStorageService storage;

  public MediaController(ObjectStorageService storage) { this.storage = storage; }

  @PostMapping(consumes = "multipart/form-data")
  @ResponseStatus(HttpStatus.CREATED)
  public MediaUploadResponse upload(@org.springframework.web.bind.annotation.PathVariable("activityId") UUID activityId,
      @RequestParam(name = "category", defaultValue = "assets") String category,
      @RequestPart("file") MultipartFile file) {
    ObjectStorageService.StoredObject object = storage.upload(activityId, category, file);
    String url = object.url(); // Same-origin API path; never persist the upload request Host.
    return new MediaUploadResponse(object.objectKey(), url, object.contentType(), object.size());
  }

  @GetMapping("/{category}/{fileName}")
  public ResponseEntity<org.springframework.core.io.InputStreamResource> access(@PathVariable UUID activityId, @PathVariable String category,
      @PathVariable String fileName, @org.springframework.web.bind.annotation.RequestHeader(value="Range", required=false) String range) throws Exception {
    return storage.download(activityId, category, fileName, range);
  }

  public record MediaUploadResponse(String objectKey, String url, String contentType, long size) { }
}
