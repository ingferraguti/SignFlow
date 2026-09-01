package it.signflow.signatures;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ArubaSignatureProperties.class)
class ArubaSignatureConfiguration {
}
