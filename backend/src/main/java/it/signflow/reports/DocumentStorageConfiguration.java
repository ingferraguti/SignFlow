package it.signflow.reports;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocumentStorageProperties.class)
public class DocumentStorageConfiguration {
    @Bean
    MinioObjectStorage minioObjectStorage(DocumentStorageProperties properties) {
        MinioClient internalClient = MinioClient.builder()
                .endpoint(properties.endpoint())
                .region(properties.region())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
        MinioClient publicClient = MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                .region(properties.region())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
        return new MinioObjectStorage(internalClient, publicClient, properties.bucket());
    }
}
