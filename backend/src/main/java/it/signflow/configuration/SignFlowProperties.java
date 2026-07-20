package it.signflow.configuration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signflow.api")
public record SignFlowProperties(List<String> corsAllowedOrigins) {
}
