package com.matrixlive.media;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
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
    return new MediaUploadResponse(object.objectKey(), object.url(), object.contentType(), object.size());
  }

  public record MediaUploadResponse(String objectKey, String url, String contentType, long size) { }
}
