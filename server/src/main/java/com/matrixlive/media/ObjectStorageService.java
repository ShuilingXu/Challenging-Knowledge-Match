package com.matrixlive.media;

import com.matrixlive.service.DomainException;
import com.matrixlive.service.SiteSettingsService;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.credentials.StaticProvider;
import io.minio.http.Method;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
    validateContent(file, contentType);
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

  private void validateContent(MultipartFile file, String type) {
    try (InputStream stream = file.getInputStream()) {
      byte[] header = stream.readNBytes(32);
      String ascii = new String(header, java.nio.charset.StandardCharsets.ISO_8859_1);
      boolean valid = switch (type) {
        case "image/png" -> header.length >= 8 && java.util.Arrays.equals(java.util.Arrays.copyOf(header, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
        case "image/jpeg" -> header.length >= 3 && header[0] == (byte)255 && header[1] == (byte)216 && header[2] == (byte)255;
        case "image/gif" -> ascii.startsWith("GIF87a") || ascii.startsWith("GIF89a");
        case "image/webp" -> header.length >= 12 && ascii.startsWith("RIFF") && ascii.substring(8).startsWith("WEBP");
        case "audio/wav", "audio/x-wav" -> header.length >= 12 && ascii.startsWith("RIFF") && ascii.substring(8).startsWith("WAVE");
        case "audio/mpeg" -> ascii.startsWith("ID3") || header.length >= 2 && header[0] == (byte)255 && (header[1] & 0xe0) == 0xe0;
        case "audio/ogg", "video/ogg" -> ascii.startsWith("OggS");
        case "audio/flac", "audio/x-flac" -> ascii.startsWith("fLaC");
        case "video/mp4", "audio/mp4", "video/quicktime" -> header.length >= 12 && ascii.substring(4).startsWith("ftyp");
        case "video/webm", "audio/webm" -> header.length >= 4 && header[0] == 0x1a && header[1] == 0x45 && header[2] == (byte)0xdf && header[3] == (byte)0xa3;
        default -> false;
      };
      if (!valid) throw new DomainException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件内容与媒体类型不符，或格式不受支持");
    } catch (java.io.IOException exception) { throw new DomainException(HttpStatus.BAD_REQUEST, "无法读取上传文件"); }
  }

  /** Serve private objects through the same-origin API; browsers never see an internal S3 hostname. */
  public org.springframework.http.ResponseEntity<org.springframework.core.io.InputStreamResource> download(
      UUID activityId, String category, String fileName, String range) throws Exception {
    if (!category.matches("[a-z0-9_-][a-z0-9._-]{0,119}") || !fileName.matches("[0-9a-f-]{36}-[a-z0-9._-]+"))
      throw new DomainException(HttpStatus.BAD_REQUEST, "Invalid media path");
    var target = target(); validateTarget(target);
    var storage = client(target);
    String key = activityId + "/" + category + "/" + fileName;
    var stat = storage.statObject(io.minio.StatObjectArgs.builder().bucket(target.bucket()).object(key).build());
    long start = 0, end = stat.size() - 1;
    if (range != null) {
      if (!range.matches("bytes=[0-9]*-[0-9]*") || range.equals("bytes=-"))
        return org.springframework.http.ResponseEntity.status(416).header("Content-Range", "bytes */" + stat.size()).build();
      try {
        String[] parts = range.substring(6).split("-", -1);
        if (parts[0].isEmpty()) start = Math.max(0, stat.size() - Long.parseLong(parts[1]));
        else { start = Long.parseLong(parts[0]); if (!parts[1].isEmpty()) end = Math.min(end, Long.parseLong(parts[1])); }
      } catch (NumberFormatException exception) { start = -1; }
      if (start < 0 || start > end || start >= stat.size())
        return org.springframework.http.ResponseEntity.status(416).header("Content-Range", "bytes */" + stat.size()).build();
    }
    var builder = org.springframework.http.ResponseEntity.status(range == null ? 200 : 206)
        .cacheControl(org.springframework.http.CacheControl.noStore()).header("Accept-Ranges", "bytes")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox; default-src 'none'")
        .contentType(org.springframework.http.MediaType.parseMediaType(stat.contentType()))
        .contentLength(end - start + 1);
    if (range != null) builder.header("Content-Range", "bytes " + start + "-" + end + "/" + stat.size());
    InputStream stream = storage.getObject(io.minio.GetObjectArgs.builder().bucket(target.bucket()).object(key).offset(start).length(end - start + 1).build());
    return builder.body(new org.springframework.core.io.InputStreamResource(stream));
  }

  private StorageTarget target() {
    var settings = siteSettings.storageConfiguration();
    String region = blank(settings.region()) ? "us-east-1" : settings.region().trim();
    return new StorageTarget(settings.enabled(), endpoint(settings.endpoint(), region), region, settings.bucket(), settings.accessKey(),
        settings.secretKey(), settings.sessionToken(), settings.publicBaseUrl(), normalizeAddressingStyle(settings.addressingStyle()));
  }

  public String accessUrl(UUID activityId, String category, String fileName) throws Exception {
    if (!category.matches("[a-z0-9_-][a-z0-9._-]{0,119}")
        || !fileName.matches("[0-9a-f-]{36}-[a-z0-9._-]+")) {
      throw new DomainException(HttpStatus.BAD_REQUEST, "Invalid media path");
    }
    StorageTarget target = target();
    validateTarget(target);
    return publicUrl(client(target), target, activityId + "/" + category + "/" + fileName);
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

  private String publicUrl(MinioClient storage, StorageTarget target, String objectKey) throws Exception {
    if (target.publicBaseUrl() != null && !target.publicBaseUrl().isBlank()) {
      String base = target.publicBaseUrl().trim().replaceAll("/+$", "");
      String bucketSuffix = "/" + target.bucket();
      if (base.endsWith(bucketSuffix)) return base + "/" + objectKey;
      return base + bucketSuffix + "/" + objectKey;
    }
    return storage.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(target.bucket())
        .object(objectKey).expiry((int) Duration.ofHours(1).toSeconds()).build());
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

  private record StorageTarget(boolean enabled, String endpoint, String region, String bucket, String accessKey,
      String secretKey, String sessionToken, String publicBaseUrl, String addressingStyle) { }
}
