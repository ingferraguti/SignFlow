package it.signflow.signatures;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Secret values are injected at deployment time and must never be persisted in the technical configuration.
 */
@ConfigurationProperties("signflow.signature-providers.aruba")
public record ArubaSignatureProperties(String serviceUrl, String username, String password,
                                       String otpAuthenticationType, String certificateId) {
    static final String DEMO_SERVICE_URL =
            "https://arss.demo.firma-automatica.it/ArubaSignService/ArubaSignService";

    public ArubaSignatureProperties {
        serviceUrl = blankToDefault(serviceUrl, DEMO_SERVICE_URL);
        otpAuthenticationType = blankToDefault(otpAuthenticationType, "demoprod");
        certificateId = blankToDefault(certificateId, "AS0");
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
