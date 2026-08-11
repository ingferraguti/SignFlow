package it.signflow.signatures;

public interface SignatureProviderAdapter {
    String adapterType();

    AuthenticationResult authenticate(AuthenticationCommand command);

    SignatureResult sign(SignatureCommand command);

    record AuthenticationCommand(String providerCode, String accountAlias, String authorizationCode) {
    }

    record AuthenticationResult(boolean authenticated, String message) {
    }

    record SignatureCommand(String providerCode, String accountAlias, String reportIdentifier,
                            int retryCount, int failuresBeforeSuccess) {
    }

    record SignatureResult(boolean successful, String providerReference, String artifactName,
                           String artifactContent, String errorCode, String errorMessage) {
        static SignatureResult success(String providerReference, String artifactName, String artifactContent) {
            return new SignatureResult(true, providerReference, artifactName, artifactContent, null, null);
        }

        static SignatureResult failure(String code, String message) {
            return new SignatureResult(false, null, null, null, code, message);
        }
    }
}
