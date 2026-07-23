package it.signflow.reports;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("signflow.documents")
public record DocumentStorageProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String region,
        long maxSizeBytes,
        int temporaryUrlSeconds,
        boolean demoEnabled) {
}
