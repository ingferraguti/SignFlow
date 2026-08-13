package it.signflow.reports;

public record AuthenticationAccountResponse(String username, String issuer,
        String authenticationMethod, boolean current) {
}
