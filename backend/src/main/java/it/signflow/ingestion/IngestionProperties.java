package it.signflow.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("signflow.ingestion")
public record IngestionProperties(
        int maxMessageBytes,
        int rawRetentionDays,
        boolean mllpEnabled,
        String mllpBindAddress,
        int mllpPort,
        boolean demoEnabled) {

    public IngestionProperties {
        if (maxMessageBytes < 1024 || maxMessageBytes > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("HL7 maximum message size must be between 1 KiB and 20 MiB");
        }
        if (rawRetentionDays < 1 || rawRetentionDays > 3650) {
            throw new IllegalArgumentException("HL7 raw retention must be between 1 and 3650 days");
        }
        if (mllpPort < 0 || mllpPort > 65535) {
            throw new IllegalArgumentException("MLLP port must be between 0 and 65535");
        }
        if (mllpBindAddress == null || mllpBindAddress.isBlank()) {
            throw new IllegalArgumentException("MLLP bind address is required");
        }
    }
}
