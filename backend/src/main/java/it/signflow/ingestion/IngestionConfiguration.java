package it.signflow.ingestion;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IngestionProperties.class)
public class IngestionConfiguration {
    @Bean(destroyMethod = "close")
    HapiContext ingestionHapiContext() {
        return new DefaultHapiContext();
    }
}
