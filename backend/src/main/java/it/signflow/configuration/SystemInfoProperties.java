package it.signflow.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signflow.system")
public record SystemInfoProperties(String version) {
}
