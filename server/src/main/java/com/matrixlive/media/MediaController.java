package com.matrixlive.media;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestHeader;
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
    String url = ServletUriComponentsBuilder.fromCurrentContextPath().path(object.url()).build().toUriString();
    return new MediaUploadResponse(object.objectKey(), url, object.contentType(), object.size());
  }

  @GetMapping("/{category}/{fileName}")
  public void access(@PathVariable UUID activityId, @PathVariable String category,
      @PathVariable String fileName, @RequestHeader(name = "Range", required = false) String range,
      HttpServletResponse response) throws Exception {
    response.setHeader("Cache-Control", "no-store");
    ObjectStorageService.MediaContent content;
    try {
      content = storage.access(activityId, category, fileName, range);
    } catch (ObjectStorageService.InvalidMediaRange exception) {
      response.setStatus(416);
      response.setHeader("Content-Range", "bytes */" + exception.size());
      return;
    }
    if (content.location() != null) {
      response.setStatus(302);
      response.setHeader("Location", content.location());
      return;
    }
    try (var stream = content.stream()) {
      response.setStatus(content.partial() ? 206 : 200);
      response.setContentType(content.contentType());
      response.setContentLengthLong(content.size());
      response.setHeader("X-Content-Type-Options", "nosniff");
      // SVG uploads can contain active content; a direct navigation must not run it on the API origin.
      response.setHeader("Content-Security-Policy", "sandbox");
      response.setHeader("Accept-Ranges", "bytes");
      if (content.contentRange() != null) response.setHeader("Content-Range", content.contentRange());
      stream.transferTo(response.getOutputStream());
    }
  }

  public record MediaUploadResponse(String objectKey, String url, String contentType, long size) { }
}
