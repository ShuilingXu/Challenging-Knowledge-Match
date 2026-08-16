package com.matrixlive.media;

import com.matrixlive.service.DomainException;
import com.matrixlive.service.SiteSettingsService;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ObjectStorageService {
  private final StorageProperties properties;
  private final SiteSettingsService siteSettings;
  private volatile MinioClient client;
  private volatile String clientEndpoint;

  public ObjectStorageService(StorageProperties properties, SiteSettingsService siteSettings) {
    this.properties = properties;
    this.siteSettings = siteSettings;
  }

  public StoredObject upload(UUID activityId, String category, MultipartFile file) {
    if (!properties.isEnabled()) throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage is not configured");
    if (file == null || file.isEmpty()) throw new DomainException(HttpStatus.BAD_REQUEST, "An upload file is required");
    if (file.getSize() > properties.getMaxFileSize()) throw new DomainException(HttpStatus.PAYLOAD_TOO_LARGE, "Upload exceeds configured file size limit");
    StorageTarget target = target();
    String safeName = sanitize(file.getOriginalFilename());
    String objectKey = activityId + "/" + sanitize(category) + "/" + UUID.randomUUID() + "-" + safeName;
    try (InputStream stream = file.getInputStream()) {
      MinioClient storage = client(target.endpoint());
      ensureBucket(storage, target.bucket());
      storage.putObject(PutObjectArgs.builder().bucket(target.bucket()).object(objectKey).stream(stream, file.getSize(), -1)
          .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType()).build());
      return new StoredObject(objectKey, publicUrl(storage, target.bucket(), objectKey), file.getContentType(), file.getSize());
    } catch (DomainException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "Object storage upload failed");
    }
  }

  private StorageTarget target() {
    var settings = siteSettings.get();
    return new StorageTarget(settings.storageEndpoint(), settings.storageBucket());
  }

  private MinioClient client(String endpoint) {
    if (client == null || !endpoint.equals(clientEndpoint)) synchronized (this) {
      if (client == null || !endpoint.equals(clientEndpoint)) {
        client = MinioClient.builder().endpoint(endpoint).credentials(properties.getAccessKey(), properties.getSecretKey()).build();
        clientEndpoint = endpoint;
      }
    }
    return client;
  }

  private void ensureBucket(MinioClient storage, String bucket) throws Exception {
    if (!storage.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
      storage.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
  }

  private String publicUrl(MinioClient storage, String bucket, String objectKey) throws Exception {
    if (properties.getPublicBaseUrl() != null && !properties.getPublicBaseUrl().isBlank()) {
      return properties.getPublicBaseUrl().replaceAll("/$", "") + "/" + bucket + "/" + objectKey;
    }
    return storage.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(bucket)
        .object(objectKey).expiry((int) Duration.ofHours(1).toSeconds()).build());
  }

  private String sanitize(String value) {
    String cleaned = (value == null ? "file" : value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    return cleaned.isBlank() ? "file" : cleaned.substring(0, Math.min(120, cleaned.length()));
  }

  public record StoredObject(String objectKey, String url, String contentType, long size) { }

  private record StorageTarget(String endpoint, String bucket) { }
}
