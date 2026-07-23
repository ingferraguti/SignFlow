package it.signflow.reports;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.Http;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MinioObjectStorage {
    private final MinioClient internalClient;
    private final MinioClient publicClient;
    private final String bucket;

    MinioObjectStorage(MinioClient internalClient, MinioClient publicClient, String bucket) {
        this.internalClient = internalClient;
        this.publicClient = publicClient;
        this.bucket = bucket;
    }

    public void put(String objectKey, InputStream input, long size, String contentType) {
        try {
            ensureBucket();
            internalClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(input, size, -1L).contentType(contentType).build());
        } catch (Exception exception) {
            throw unavailable("Object upload failed", exception);
        }
    }

    public byte[] get(String objectKey) {
        try (InputStream input = internalClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw unavailable("Object download failed", exception);
        }
    }

    public String temporaryGetUrl(String objectKey, String disposition, int durationSeconds, String filename) {
        try {
            return publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET).bucket(bucket).object(objectKey).expiry(durationSeconds)
                    .extraQueryParams(Map.of(
                            "response-content-type", "application/pdf",
                            "response-content-disposition", disposition + "; filename=\"" + filename + "\""))
                    .build());
        } catch (Exception exception) {
            throw unavailable("Temporary URL generation failed", exception);
        }
    }

    public boolean exists(String objectKey) {
        try {
            ensureBucket();
            internalClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (io.minio.errors.ErrorResponseException exception) {
            return false;
        } catch (Exception exception) {
            throw unavailable("Object lookup failed", exception);
        }
    }

    public void remove(String objectKey) {
        try {
            internalClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw unavailable("Object cleanup failed", exception);
        }
    }

    private synchronized void ensureBucket() throws Exception {
        if (!internalClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            internalClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private ResponseStatusException unavailable(String message, Exception exception) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message, exception);
    }
}
