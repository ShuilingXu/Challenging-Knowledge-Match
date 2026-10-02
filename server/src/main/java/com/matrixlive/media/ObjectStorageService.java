package com.matrixlive.media;

import com.matrixlive.service.DomainException;
import com.matrixlive.service.SiteSettingsService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.credentials.StaticProvider;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ObjectStorageService {
  private final StorageProperties properties;
  private final SiteSettingsService siteSettings;
  private volatile MinioClient client;
  private volatile int clientFingerprint;

  public ObjectStorageService(StorageProperties properties, SiteSettingsService siteSettings) {
    this.properties = properties;
    this.siteSettings = siteSettings;
  }

  public StoredObject upload(UUID activityId, String category, MultipartFile file) {
    if (file == null || file.isEmpty()) throw new DomainException(HttpStatus.BAD_REQUEST, "An upload file is required");
    if (file.getSize() > properties.getMaxFileSize()) throw new DomainException(HttpStatus.PAYLOAD_TOO_LARGE, "Upload exceeds configured file size limit");
    String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
    if (!isSupportedMediaType(contentType)) {
      throw new DomainException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only image, audio, or video files are supported");
    }
    StorageTarget target = target();
    validateTarget(target);
    String safeName = sanitize(file.getOriginalFilename());
    if (!hasExtension(safeName)) safeName += extensionFor(contentType);
    String objectKey = activityId + "/" + sanitize(category) + "/" + UUID.randomUUID() + "-" + safeName;
    try (InputStream stream = file.getInputStream()) {
      MinioClient storage = client(target);
      ensureBucket(storage, target.bucket());
      storage.putObject(PutObjectArgs.builder().bucket(target.bucket()).object(objectKey).stream(stream, file.getSize(), -1)
          .contentType(contentType).build());
      return new StoredObject(objectKey, "/api/activities/" + activityId + "/media/" + objectKey.substring(37), contentType, file.getSize());
    } catch (DomainException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage upload failed");
    }
  }

  private StorageTarget target() {
    var settings = siteSettings.storageConfiguration();
    String region = blank(settings.region()) ? "us-east-1" : settings.region().trim();
    return new StorageTarget(settings.enabled(), endpoint(settings.endpoint(), region), region, settings.bucket(), settings.accessKey(),
        settings.secretKey(), settings.sessionToken(), settings.publicBaseUrl(), normalizeAddressingStyle(settings.addressingStyle()));
  }

  public MediaContent access(UUID activityId, String category, String fileName, String range) {
    if (!category.matches("[a-z0-9_-][a-z0-9._-]{0,119}")
        || !fileName.matches("[0-9a-f-]{36}-[a-z0-9._-]+")) {
      throw new DomainException(HttpStatus.BAD_REQUEST, "Invalid media path");
    }
    StorageTarget target = target();
    validateTarget(target);
    String objectKey = activityId + "/" + category + "/" + fileName;
    if (!blank(target.publicBaseUrl())) {
      return new MediaContent(publicUrl(target, objectKey), null, null, 0, null, false);
    }
    try {
      MinioClient storage = client(target);
      var metadata = storage.statObject(StatObjectArgs.builder().bucket(target.bucket()).object(objectKey).build());
      long size = metadata.size();
      long start = 0;
      long end = size - 1;
      boolean partial = range != null;
      if (partial) {
        try {
          var ranges = HttpRange.parseRanges(range);
          if (ranges.size() != 1 || size == 0) throw new IllegalArgumentException();
          start = ranges.getFirst().getRangeStart(size);
          end = ranges.getFirst().getRangeEnd(size);
          if (start >= size || end < start) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
          throw new InvalidMediaRange(size);
        }
      }
      var request = GetObjectArgs.builder().bucket(target.bucket()).object(objectKey);
      if (partial) request.offset(start).length(end - start + 1);
      InputStream stream = storage.getObject(request.build());
      String contentType = blank(metadata.contentType()) ? "application/octet-stream" : metadata.contentType();
      return new MediaContent(null, stream, contentType, partial ? end - start + 1 : size,
          partial ? "bytes " + start + "-" + end + "/" + size : null, partial);
    } catch (DomainException exception) {
      throw exception;
    } catch (io.minio.errors.ErrorResponseException exception) {
      if ("NoSuchKey".equals(exception.errorResponse().code()) || "NoSuchObject".equals(exception.errorResponse().code())) {
        throw new DomainException(HttpStatus.NOT_FOUND, "Media does not exist");
      }
      throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage download failed");
    } catch (Exception exception) {
      throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage download failed");
    }
  }

  private MinioClient client(StorageTarget target) {
    int fingerprint = Objects.hash(target.endpoint(), target.region(), target.accessKey(), target.secretKey(), target.sessionToken(),
        target.addressingStyle());
    if (client == null || fingerprint != clientFingerprint) synchronized (this) {
      if (client == null || fingerprint != clientFingerprint) {
        var builder = MinioClient.builder().endpoint(target.endpoint()).region(target.region());
        if (target.sessionToken() == null || target.sessionToken().isBlank()) {
          builder.credentials(target.accessKey(), target.secretKey());
        } else {
          builder.credentialsProvider(new StaticProvider(target.accessKey(), target.secretKey(), target.sessionToken()));
        }
        client = builder.build();
        if ("PATH".equals(target.addressingStyle())) client.disableVirtualStyleEndpoint();
        if ("VIRTUAL".equals(target.addressingStyle())) client.enableVirtualStyleEndpoint();
        clientFingerprint = fingerprint;
      }
    }
    return client;
  }

  private void validateTarget(StorageTarget target) {
    if (!target.enabled() || blank(target.endpoint()) || blank(target.bucket())) {
      throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage is not configured");
    }
    if (!validEndpoint(target.endpoint())) {
      throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage endpoint is invalid");
    }
    if (blank(target.region())) {
      throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage region is required");
    }
    if (!target.bucket().matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) {
      throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage bucket name is invalid");
    }
    if (blank(target.accessKey()) || blank(target.secretKey())) {
      throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage credentials are not configured");
    }
  }

  private void ensureBucket(MinioClient storage, String bucket) throws Exception {
    if (!storage.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
      storage.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
  }

  private String publicUrl(StorageTarget target, String objectKey) {
    String base = target.publicBaseUrl().trim().replaceAll("/+$", "");
    String bucketSuffix = "/" + target.bucket();
    if (base.endsWith(bucketSuffix)) return base + "/" + objectKey;
    return base + bucketSuffix + "/" + objectKey;
  }

  private String sanitize(String value) {
    String cleaned = (value == null ? "file" : value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    return cleaned.isBlank() ? "file" : cleaned.substring(0, Math.min(120, cleaned.length()));
  }

  private static boolean blank(String value) { return value == null || value.isBlank(); }

  private boolean isSupportedMediaType(String contentType) {
    return contentType.startsWith("image/") || contentType.startsWith("audio/") || contentType.startsWith("video/");
  }

  private boolean hasExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 && dot < name.length() - 1;
  }

  private String extensionFor(String contentType) {
    return switch (contentType) {
      case "image/jpeg" -> ".jpg";
      case "image/svg+xml" -> ".svg";
      case "image/webp" -> ".webp";
      case "image/gif" -> ".gif";
      case "audio/mpeg" -> ".mp3";
      case "audio/wav", "audio/x-wav" -> ".wav";
      case "audio/ogg" -> ".ogg";
      case "video/webm" -> ".webm";
      case "video/quicktime" -> ".mov";
      default -> contentType.startsWith("video/") ? ".mp4" : contentType.startsWith("audio/") ? ".audio" : ".img";
    };
  }

  private String normalizeAddressingStyle(String value) {
    if (blank(value)) return "AUTO";
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "PATH", "VIRTUAL", "AUTO" -> normalized;
      default -> "AUTO";
    };
  }

  /**
   * AWS S3 does not require an endpoint in its configuration. MinIO's client
   * still needs one, so use the regional AWS endpoint when operators leave it
   * blank while preserving explicit endpoints for MinIO and other S3 services.
   */
  static String endpoint(String configured, String region) {
    if (!blank(configured)) return configured.trim().replaceAll("/+$", "");
    String normalizedRegion = blank(region) ? "us-east-1" : region.trim();
    String suffix = normalizedRegion.startsWith("cn-") ? "amazonaws.com.cn" : "amazonaws.com";
    return "https://s3." + normalizedRegion + "." + suffix;
  }

  private boolean validEndpoint(String endpoint) {
    try {
      URI uri = new URI(endpoint);
      return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null && (uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath()))
          && uri.getQuery() == null && uri.getFragment() == null;
    } catch (URISyntaxException exception) {
      return false;
    }
  }

  public record StoredObject(String objectKey, String url, String contentType, long size) { }

  public record MediaContent(String location, InputStream stream, String contentType, long size,
      String contentRange, boolean partial) { }

  public static class InvalidMediaRange extends DomainException {
    private final long size;
    InvalidMediaRange(long size) {
      super(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Invalid media byte range");
      this.size = size;
    }
    public long size() { return size; }
  }

  private record StorageTarget(boolean enabled, String endpoint, String region, String bucket, String accessKey,
      String secretKey, String sessionToken, String publicBaseUrl, String addressingStyle) { }
}
